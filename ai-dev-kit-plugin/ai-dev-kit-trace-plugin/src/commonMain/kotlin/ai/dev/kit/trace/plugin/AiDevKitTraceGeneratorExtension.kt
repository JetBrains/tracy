package ai.dev.kit.trace.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class AiDevKitTraceGeneratorExtension : IrGenerationExtension {
    private val traceAnnotationFqName = FqName("ai.dev.kit.tracing.fluent.KotlinFlowTrace")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val withTraceSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.dev.kit.tracing.fluent.processor"), Name.identifier("withTrace"))
        ).findMultiplatformSymbol()
        val withTraceSuspendedSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.dev.kit.tracing.fluent.processor"), Name.identifier("withTraceSuspended"))
        ).findMultiplatformSymbol()

        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                // Try to get this function's own @KotlinFlowTrace annotation,
                // or fall back to a propagated one from an overridden function.
                val traceAnnotation = declaration.findTraceAnnotation()
                    ?: declaration.findOverriddenAnnotationWithPropagation()
                if (traceAnnotation != null && declaration.body != null) {
                    processFunction(
                        traceAnnotation,
                        declaration,
                        pluginContext,
                        withTraceSymbol,
                        withTraceSuspendedSymbol
                    )
                }
                super.visitFunction(declaration)
            }
        }, null)
    }

    /**
     * Recursively find the first matching @KotlinFlowTrace annotation
     * in any overridden function in the hierarchy.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrFunction.findOverriddenAnnotationWithPropagation(): IrConstructorCall? =
        (this as? IrSimpleFunction)?.let { fn ->
            val visited = mutableSetOf<IrSimpleFunction>()
            fun search(f: IrSimpleFunction): IrConstructorCall? {
                if (!visited.add(f)) return null
                f.findTraceAnnotation()?.let { return it }
                return f.overriddenSymbols
                    .map { it.owner }.firstNotNullOfOrNull { search(it) }
            }
            search(fn)
        }


    /**
     * Returns the @KotlinFlowTrace annotation applied directly to this function,
     * or `null` if not present.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrFunction.findTraceAnnotation() = annotations.find {
        it.symbol.owner.parentAsClass.fqNameWhenAvailable == traceAnnotationFqName
    }


    /**
     * Returns the actual function symbol if present, otherwise the expect symbol,
     * ensuring proper resolution in multiplatform projects.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun Collection<IrSimpleFunctionSymbol>.findMultiplatformSymbol(): IrSimpleFunctionSymbol {
        return this.singleOrNull { !it.owner.isExpect }   // prefer actual in platform modules
            ?: this.singleOrNull { it.owner.isExpect }  // fallback to expect in commonMain
            ?: error("`Expect/actual declaration for `withTrace` not found. Found: $this")
    }

    /**
     * Wraps the original function body in a call to [withTrace] or [withTraceSuspended],
     * passing the annotation, a function reference, arguments, and a lambda with the original body.
     */
    private fun processFunction(
        traceAnnotation: IrConstructorCall,
        function: IrFunction,
        pluginContext: IrPluginContext,
        withTraceSymbol: IrSimpleFunctionSymbol,
        withTraceSuspendedSymbol: IrSimpleFunctionSymbol
    ) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        val functionRefType = pluginContext.irBuiltIns
            .functionN(function.valueParameters.size)
            .typeWith(function.valueParameters.map { it.type } + function.returnType)

        // Reference to original function
        val functionReference = builder.irFunctionReference(
            type = functionRefType,
            symbol = function.symbol
        ).apply {
            function.typeParameters.forEachIndexed { index, typeParameter ->
                putTypeArgument(index, typeParameter.symbol.defaultType)
            }
            function.dispatchReceiverParameter?.let {
                dispatchReceiver = builder.irGet(it)
            }
            function.extensionReceiverParameter?.let {
                extensionReceiver = builder.irGet(it)
            }
        }

        // Function arguments
        val argsArray = builder.irVararg(
            pluginContext.irBuiltIns.anyNType,
            function.valueParameters.map { param ->
                builder.irGet(param.type, param.symbol)
            }
        )

        // Lambda with function, which covered in withTrace logic
        val lambdaFunction = pluginContext.irFactory.buildFun {
            name = Name.identifier("OriginalFunctionExpressionLambda")
            returnType = function.returnType
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            isSuspend = function.isSuspend
            visibility = DescriptorVisibilities.LOCAL
        }.apply {
            parent = function
            body = function.moveBodyTo(this, emptyMap())?.patchDeclarationParents(this)
        }

        val lambdaExpression = IrFunctionExpressionImpl(
            startOffset = lambdaFunction.startOffset,
            endOffset = lambdaFunction.endOffset,
            type = pluginContext.irBuiltIns
                .functionN(0)
                .typeWith(function.returnType),
            function = lambdaFunction,
            origin = IrStatementOrigin.LAMBDA
        )

        val withTraceCall =
            builder.irCall(if (function.isSuspend) withTraceSuspendedSymbol else withTraceSymbol).apply {
                putValueArgument(0, functionReference)
                putValueArgument(1, argsArray)
                putValueArgument(2, traceAnnotation)
                putValueArgument(3, lambdaExpression)
            }

        function.body = builder.irBlockBody {
            +builder.irReturn(withTraceCall)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
class AiDevKitTracePluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(AiDevKitTraceGeneratorExtension())
    }
}

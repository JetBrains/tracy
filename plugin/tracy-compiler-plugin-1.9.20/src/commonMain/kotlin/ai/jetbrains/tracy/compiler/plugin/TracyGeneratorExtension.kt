package ai.jetbrains.tracy.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.isInlineParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class TracyGeneratorExtension : IrGenerationExtension {
    private val traceAnnotationFqName = FqName("ai.jetbrains.tracy.core.fluent.Trace")

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val withTraceSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.jetbrains.tracy.core.fluent.processor"), Name.identifier("withTrace"))
        ).findMultiplatformSymbol()
        val withTraceSuspendedSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.jetbrains.tracy.core.fluent.processor"), Name.identifier("withTraceSuspended"))
        ).findMultiplatformSymbol()

        moduleFragment.accept(object : IrElementTransformerVoid() {
            override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
                // Try to get this function's own @Trace annotation
                // or fall back to a propagated one from an overridden function.
                val traceAnnotation = declaration.findOverriddenAnnotationWithPropagation()
                if (traceAnnotation != null && declaration.body != null) {
                    processFunction(
                        traceAnnotation,
                        declaration,
                        pluginContext,
                        withTraceSymbol,
                        withTraceSuspendedSymbol
                    )
                }
                return super.visitFunction(declaration)
            }
        }, null)
    }

    /**
     * Recursively find the first matching @Trace annotation
     * in any overridden function in the hierarchy.
     */
    private fun IrSimpleFunction.findOverriddenAnnotationWithPropagation(): IrConstructorCall? =
        this.allOverridden(true).firstNotNullOfOrNull {
            it.annotations.findAnnotation(traceAnnotationFqName)
        }

    // Returns the actual function symbol if present ensuring proper resolution in multiplatform projects.
    private fun Collection<IrSimpleFunctionSymbol>.findMultiplatformSymbol(): IrSimpleFunctionSymbol {
        return this.firstOrNull { !it.owner.isExpect }
            ?: error("`Expect/actual declaration for `withTrace` not found. Found: $this")
    }

    /**
     * Wraps the original function body in a call to `withTrace` or `withTraceSuspended`,
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

        // Reference to the original function
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
                if (function.isInline && param.isInlineParameter()) {
                    // Inline lambdas can't be captured
                    builder.irNull()
                } else {
                    builder.irGet(param.type, param.symbol)
                }
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

        val withTraceCall = run {
            val symbol = if (function.isSuspend) withTraceSuspendedSymbol else withTraceSymbol
            builder.irCall(symbol).apply {
                putValueArgument(0, functionReference)
                putValueArgument(1, argsArray)
                putValueArgument(2, traceAnnotation.deepCopyWithSymbols())
                putValueArgument(3, lambdaExpression)
            }
        }

        function.body = builder.irBlockBody {
            +builder.irReturn(withTraceCall)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
class TracyPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(TracyGeneratorExtension())
    }
}

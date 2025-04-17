package ai.dev.kit.trace.plugin

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
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
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class AiDevKitTraceGeneratorExtension : IrGenerationExtension {
    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val printArgsSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.dev.kit.core.fluent.processor"), Name.identifier("withTrace"))
        ).single()
        val printArgsSuspendedSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.dev.kit.core.fluent.processor"), Name.identifier("withTraceSuspended"))
        ).single()
        val traceAnnotationFqName = FqName("ai.dev.kit.core.fluent.KotlinFlowTrace")
        moduleFragment.files.forEach { file ->
            file.declarations.forEach { declaration ->
                processDeclaration(
                    declaration,
                    pluginContext,
                    printArgsSymbol,
                    printArgsSuspendedSymbol,
                    traceAnnotationFqName
                )
            }
        }
    }

    private fun processDeclaration(
        declaration: IrDeclaration,
        pluginContext: IrPluginContext,
        printArgsSymbol: IrSimpleFunctionSymbol,
        printArgsSuspendedSymbol: IrSimpleFunctionSymbol,
        traceAnnotationFqName: FqName
    ) {
        when (declaration) {
            is IrFunction -> {
                if (declaration.hasAnnotation(traceAnnotationFqName)) {
                    processFunction(declaration, pluginContext, printArgsSymbol, printArgsSuspendedSymbol)
                }
            }
            is IrClass -> {
                declaration.declarations.forEach {
                    processDeclaration(it, pluginContext, printArgsSymbol, printArgsSuspendedSymbol, traceAnnotationFqName)
                }
            }
        }
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    private fun processFunction(
        function: IrFunction,
        pluginContext: IrPluginContext,
        printArgsSymbol: IrSimpleFunctionSymbol,
        printArgsSuspendedSymbol: IrSimpleFunctionSymbol
    ) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        val functionRefType = pluginContext.irBuiltIns
            .functionN(function.valueParameters.size)
            .typeWith(function.valueParameters.map { it.type } + function.returnType)

        val functionReference = builder.irFunctionReference(
            type = functionRefType,
            symbol = function.symbol
        ).apply {
            function.dispatchReceiverParameter?.let {
                dispatchReceiver = builder.irGet(it)
            }
        }


        val argsArray = builder.irVararg(
            pluginContext.irBuiltIns.anyNType,
            function.valueParameters.map { param ->
                builder.irGet(param.type, param.symbol)
            }
        )

        val lambdaFunction = pluginContext.irFactory.buildFun {
            name = Name.identifier("lambda")
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

        val printArgsCall = builder.irCall(if (function.isSuspend) printArgsSuspendedSymbol else printArgsSymbol).apply {
            putValueArgument(0, functionReference)
            putValueArgument(1, argsArray)
            putValueArgument(2, lambdaExpression)
        }

        function.body = builder.irBlockBody {
            +builder.irReturn(printArgsCall)
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

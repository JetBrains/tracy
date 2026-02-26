/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TracyGeneratorExtension : IrGenerationExtension {
    private val traceAnnotationFqName = FqName("ai.jetbrains.tracy.core.instrumentation.Trace")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val withTraceSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.jetbrains.tracy.core.instrumentation.processor"), Name.identifier("withTrace"))
        ).findMultiplatformSymbol()
        val withTraceSuspendedSymbol = pluginContext.referenceFunctions(
            CallableId(FqName("ai.jetbrains.tracy.core.instrumentation.processor"), Name.identifier("withTraceSuspended"))
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
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrSimpleFunction.findOverriddenAnnotationWithPropagation(): IrConstructorCall? =
        this.allOverridden(true).firstNotNullOfOrNull {
            it.annotations.findAnnotation(traceAnnotationFqName)
        }

    // Returns the actual function symbol if present ensuring proper resolution in multiplatform projects.
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun Collection<IrSimpleFunctionSymbol>.findMultiplatformSymbol(): IrSimpleFunctionSymbol {
        return this.firstOrNull { !it.owner.isExpect }
            ?: error("`Expect/actual declaration for `withTrace` not found. Found: $this")
    }

    @Suppress()
    /**
     * Wraps the original function body in a call to `withTrace` or `withTraceSuspended`,
     * passing the annotation, a function reference, arguments, and a lambda with the original body.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun processFunction(
        traceAnnotation: IrConstructorCall,
        function: IrFunction,
        pluginContext: IrPluginContext,
        withTraceSymbol: IrSimpleFunctionSymbol,
        withTraceSuspendedSymbol: IrSimpleFunctionSymbol
    ) {
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        val functionRefType = pluginContext.irBuiltIns
            .functionN(function.parameters.size)
            .typeWith(function.parameters.map { it.type } + function.returnType)

        // Reference to the original function
        val functionReference: IrFunctionReference = IrFunctionReferenceImpl(
            startOffset = builder.startOffset,
            endOffset = builder.endOffset,
            type = functionRefType,
            symbol = function.symbol,
            typeArgumentsCount = function.typeParameters.size
        ).also { ref ->
            function.typeParameters.forEachIndexed { index, typeParameter ->
                ref.typeArguments[index] = typeParameter.symbol.defaultType
            }
        }

        // Function arguments
        val argsArray = builder.irVararg(
            pluginContext.irBuiltIns.anyNType,
            function.parameters
                .filter { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
                .map { param ->
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
                arguments[0] = functionReference
                arguments[1] = argsArray
                arguments[2] = traceAnnotation.deepCopyWithSymbols()
                arguments[3] = lambdaExpression
            }
        }

        function.body = builder.irBlockBody {
            +builder.irReturn(withTraceCall)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
class TracyPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "org.jetbrains.ai.tracy"
    override val supportsK2: Boolean = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(TracyGeneratorExtension())
    }
}

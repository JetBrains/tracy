package org.example.ai.mlflow.fluent.processor

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.com.intellij.mock.MockProject

// 1️⃣ Define Annotation for Functions Only
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LogSuspend

// 2️⃣ IR Transformation to Modify Annotated Suspend Functions
class LogSuspendTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        // Check if function has @LogSuspend annotation and is suspend
        if (declaration.annotations.any { it.symbol.owner.name.asString() == "LogSuspend" } && declaration.isSuspend) {
            injectPrintStatement(declaration)
        }
        return super.visitFunction(declaration)
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    private fun injectPrintStatement(function: IrFunction) {
        val irBuiltIns = pluginContext.irBuiltIns
        val irFactory = pluginContext.irFactory

        // Get reference to `kotlin.io.println`
        val printlnSymbol = pluginContext.referenceFunctions(FqName("kotlin.io.println"))
            .firstOrNull { it.owner.valueParameters.size == 1 && it.owner.valueParameters[0].type == irBuiltIns.stringType }
            ?: error("Cannot find kotlin.io.println function")

        val irBuilder = DeclarationIrBuilder(pluginContext, function.symbol)

        function.body = irFactory.createBlockBody(function.startOffset, function.endOffset) {
            statements += irBuilder.irCall(printlnSymbol).apply {
                putValueArgument(0, irBuilder.irString("I am suspend function ${function.name}"))
            }
            function.body?.statements?.let { statements += it } // Preserve existing statements
        }
    }
}

// 3️⃣ Register Plugin with IrGenerationExtension
class LogSuspendPluginRegistrar : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transform(LogSuspendTransformer(pluginContext), null)
    }
}

// 4️⃣ Implement the CompilerPluginRegistrar
//@OptIn(ExperimentalCompilerApi::class)
//class LogSuspendCompilerPluginRegistrar : CompilerPluginRegistrar() {
//
//    override val supportsK2: Boolean= false
//
//    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
//        val pluginRegistrar: IrGenerationExtension = LogSuspendPluginRegistrar()
//        this.registeredExtensions[P]
//    }
//}

// 5️⃣ Test Code (Verify Plugin Works)
class MyClass {
    @LogSuspend
    suspend fun doSomething() {
        println("Executing doSomething")
    }

    fun normalFunction() {
        println("Normal function")
    }
}

suspend fun main() {
    val myClass = MyClass()
    myClass.doSomething() // Should print: "I am suspend function doSomething" before actual execution
}

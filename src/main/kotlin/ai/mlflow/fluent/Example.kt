package org.example.ai.mlflow.fluent

import com.google.inject.Guice
import com.google.inject.Injector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class MyClass {
    @KotlinFlowTrace
    open fun someFunction(x: Int, y: Int, z: Int = 2): Int {
        Thread.sleep(500)
        return a(x) + b(y - z)
    }

    @KotlinFlowTrace
    open fun a(x: Int): Int {
        return x - 1
    }

    @KotlinFlowTrace
    open fun b(x: Int): Int {
        return x + 1
    }
}

fun main() {
    runBlocking {
        coroutineScope {
            setupTracing()
            val injector: Injector = Guice.createInjector(KotlinFlowTraceModule())
            val myClass = injector.getInstance(MyClass::class.java)

            launch {
                myClass.someFunction(2, 4)
            }
//            launch {
//                myClass.someFunction(3, 5)
//            }
        }
    }
}
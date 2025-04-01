package org.example.ai.mlflow.fluent.processor

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Pointcut
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

// Define the custom annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
annotation class MyAnnotation

// Define the aspect
@Aspect
class MyAspect {
    // Pointcut to match methods annotated with @MyAnnotation
    @Pointcut("@annotation(MyAnnotation)")
    fun annotatedMethod() {}

    // Before advice: executed before the annotated method
    @Before("annotatedMethod()")
    fun beforeMethod() {
        println("hi")
    }

    // After advice: executed after the annotated method
    @After("annotatedMethod()")
    fun afterMethod(joinPoint: JoinPoint) {
        val methodName = joinPoint.signature.name
        println("bye $methodName")
    }
}

class A() {
    @MyAnnotation
    fun a() {
        println("a")
    }
}

fun main() {
    A().a()
}
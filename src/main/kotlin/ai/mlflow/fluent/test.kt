import kotlinx.coroutines.delay
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy
import net.bytebuddy.asm.Advice
import net.bytebuddy.matcher.ElementMatchers
import java.lang.instrument.Instrumentation
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

@Retention(AnnotationRetention.RUNTIME)
annotation class TrackExecution2

object ExecutionTimeAgent {
    @JvmStatic
    fun premain(agentArgs: String?, inst: Instrumentation) {
        AgentBuilder.Default()
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .with(RedefinitionStrategy.Listener.StreamWriting.toSystemError())
            .type(ElementMatchers.nameEndsWith("TargetClass"))
            .transform { builder, _, _, _, _ ->
                builder.visit(Advice.to(Demo.Advice1::class.java).on(ElementMatchers.isAnnotatedWith(TrackExecution2::class.java)))
            }
            .installOn(inst)
    }
}

class TargetClass {
    @TrackExecution2
    suspend fun testMethod(s: String, f: String): Int {
        delay(20)
        println("In method testMethod: $s")
        return 4
    }
}

val argsProcessor: ArgsProcessor = object : ArgsProcessor {
    override fun process(args: Array<Any?>) {
        val lastArg = args.lastOrNull()
        if (lastArg is Continuation<*>) {
            println("I am in arg processor, processing coroutine with context: ${lastArg.context}")
            if (lastArg.context[MyContext] == null) {
                args[args.size - 1] = lastArg.withContext(MyContext())
            }
        }
        args[0] = "${args[0]}-suffixe"
        args[1] = "replaced"
    }
}

interface ArgsProcessor {
    fun process(args: Array<Any?>)
}


class MyContext : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<MyContext>
    override val key: CoroutineContext.Key<*> = Key
}

fun <T> Continuation<T>.withContext(context: CoroutineContext): Continuation<T> {
    return object : Continuation<T> {
        override val context: CoroutineContext = this@withContext.context + context

        override fun resumeWith(result: Result<T>) {
            this@withContext.resumeWith(result)
        }
    }
}


suspend fun main() {
    ExecutionTimeAgent.premain(null, ByteBuddyAgent.install())
    val target = TargetClass()
    target.testMethod("Sample Argument", "HIHI")
}

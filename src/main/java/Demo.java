import static net.bytebuddy.matcher.ElementMatchers.is;

import kotlin.jvm.JvmStatic;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Demo {

    public static ArgsProcessor argsProcessor = args -> {
        args[0] = args[0] + "-suffix";
        args[1] = "replaced";
    };

    public static void main(String[] args) {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        Demo demo = new Demo();
        try {
            demo.run(instrumentation, Advice1.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(Instrumentation instrumentation, Class<?> adviceClass) throws Exception {
        FooService fooService = new FooService();
        ByteBuddy byteBuddy = new ByteBuddy();
        ResettableClassFileTransformer transformer = new AgentBuilder.Default()
            .with(byteBuddy)
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .disableClassFormatChanges()
            .type(is(FooService.class))
            .transform((builder, typeDescription, classloader, module, trash) -> {
            AsmVisitorWrapper.ForDeclaredMethods advice = Advice.to(adviceClass)
                .on(ElementMatchers.named("join"));
            return builder.visit(advice);
        }).installOnByteBuddyAgent();
        String c = fooService.join("a", "b");
        System.out.printf("%s %s\n", adviceClass.getSimpleName(), c);
        transformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
    }

    public static interface ArgsProcessor {
        void process(Object[] args);
    }

    public static class Advice1 {
        @Advice.OnMethodEnter
        public static long onEnter(
                @Advice.Origin Method method,
                @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args
        ) {
            Object[] newArgs = Arrays.copyOf(args, args.length);
            Demo.argsProcessor.process(newArgs);
            args = newArgs;
            return System.nanoTime();
        }

        @JvmStatic
        @Advice.OnMethodExit
        public static void exit(
                @Advice.Origin Method method,
                @Advice.Enter long startTime
        ) {
            long elapsedTime = System.nanoTime() - startTime;
            System.out.printf("Method %s executed in %s ms\n", method.getName(), TimeUnit.NANOSECONDS.toMillis(elapsedTime));
        }
    }

    public static class FooService {
        public String join(String message, String message1) {
            return message + " " + message1;
        }
    }
}
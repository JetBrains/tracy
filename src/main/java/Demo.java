import static net.bytebuddy.matcher.ElementMatchers.is;

import kotlin.jvm.JvmStatic;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static class Advice1 {
        @Advice.OnMethodEnter
        public static long onEnter(
                @Advice.Origin Method method,
                @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args
        ) {
            Object[] newArgs = Arrays.copyOf(args, args.length);
            TestKt.getArgsProcessor().process(newArgs);
            args = newArgs;
            System.out.println("Here");
            return System.nanoTime();
        }

        @JvmStatic
        @Advice.OnMethodExit
        public static void exit(
                @Advice.Origin Method method,
                @Advice.Enter long startTime
        ) {
            System.out.println("Here2");
            long elapsedTime = System.nanoTime() - startTime;
            System.out.printf("Method %s executed in %s ms\n", method.getName(), TimeUnit.NANOSECONDS.toMillis(elapsedTime));
        }
    }
}
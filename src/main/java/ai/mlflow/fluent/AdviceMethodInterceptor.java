package ai.mlflow.fluent;

import kotlin.Pair;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.JvmStatic;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.example.ai.mlflow.fluent.KotlinFlowTrace;
import org.example.ai.mlflow.fluent.processor.TraceInfo;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.example.ai.mlflow.fluent.processor.TracedMethodInterceptorKt.argsProcessor;

public class AdviceMethodInterceptor {
    @Advice.OnMethodEnter
    public static TraceInfo onEnter(
            @Advice.Origin Method method,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args
    ) {
        KotlinFlowTrace traceAnnotation = method.getAnnotation(KotlinFlowTrace.class);
        Object[] newArgs = Arrays.copyOf(args, args.length);
        Pair<Object[], TraceInfo> result = argsProcessor(traceAnnotation, method, newArgs);
        Object[] proceedArgs = result.getFirst();
        TraceInfo traceInfo = result.getSecond();
        args = proceedArgs;
        System.out.printf("I just changed arguments of %s\n", method.getName());
        return traceInfo;
    }

    @JvmStatic
    @Advice.OnMethodExit
    public static void exit(
            @Advice.Origin Method method,
            @Advice.Enter TraceInfo traceInfo,
            @Advice.Return Object result
    ) {
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            KotlinFlowTrace traceAnnotation = method.getAnnotation(KotlinFlowTrace.class);
            traceInfo.addOutputAttribute(traceAnnotation, result);
            System.out.printf("I am in method exit with result %s\n", result);
            traceInfo.close();
        } else {
            System.out.println("I am in method exit with suspended result\n");
        }
    }
}

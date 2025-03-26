package ai.mlflow.fluent;

import kotlin.Pair;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.JvmStatic;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.example.ai.mlflow.fluent.KotlinFlowTrace;
import org.example.ai.mlflow.fluent.processor.TraceInfo;
import org.example.ai.mlflow.fluent.processor.TracedMethodInterceptor;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.example.ai.mlflow.fluent.processor.TracedMethodInterceptorKt.*;

public class AdviceMethodInterceptor {
    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Origin Method method,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args
    ) {
        KotlinFlowTrace traceAnnotation = method.getAnnotation(KotlinFlowTrace.class);
        Object[] newArgs = Arrays.copyOf(args, args.length);
        args = argsProcessor(traceAnnotation, method, newArgs);
        System.out.printf("I just changed arguments of %s\n", method.getName());
    }

    @JvmStatic
    @Advice.OnMethodExit
    public static void exit(
            @Advice.Origin Method method,
            @Advice.Return Object result,
            @Advice.AllArguments Object[] args
    ) {
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            KotlinFlowTrace traceAnnotation = method.getAnnotation(KotlinFlowTrace.class);
            TraceInfo traceInfo = deleteContinuation(method);
            System.out.printf("I am in method exit with result %s\n", result);
            traceInfo.addOutputAttribute(traceAnnotation, result);
            traceInfo.close();
        } else {
            System.out.println("I am in method exit with suspended result\n");
        }
    }
}

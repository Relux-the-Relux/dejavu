package com.jayway.dejavu.core;

import com.jayway.dejavu.core.annotation.Impure;
import com.jayway.dejavu.core.chainer.ChainBuilder;
import com.jayway.dejavu.core.repository.TraceCallback;
import com.jayway.dejavu.core.typeinference.DefaultInference;
import com.jayway.dejavu.core.typeinference.ExceptionInference;
import com.jayway.dejavu.core.typeinference.TypeInference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.*;
import java.util.concurrent.Callable;


@Aspect
public class DejaVuAspect {

    private static TraceCallback callback;
    private static boolean traceMode = true;
    private static int timeout = 10 * 60 * 1000; // ten minutes
    private static int exceptionThreshold = 10;

    private static ThreadLocal<String> traceId = new ThreadLocal<String>();
    private static ThreadLocal<String> threadId = new ThreadLocal<String>();
    private static ThreadLocal<Boolean> threadLocalInImpure = new ThreadLocal<Boolean>();
    private static ThreadLocal<Boolean> threadLocalIgnore = new ThreadLocal<Boolean>();

    private static Map<String, RunningTrace> runningTraces = new HashMap<String, RunningTrace>();
    private static Map<String, CircuitBreaker> circuitBreakers;
    private static List<TypeInference> typeHelpers = new ArrayList<TypeInference>();
    private static TypeInference typeInference;

    public static void initialize( TraceCallback cb ) {
        callback = cb;
        circuitBreakers = new HashMap<String, CircuitBreaker>();
        ChainBuilder<TypeInference> builder = ChainBuilder.chain(TypeInference.class).add(new ExceptionInference());
        for (TypeInference typeHelper : typeHelpers) {
            builder.add( typeHelper );
        }
        typeInference = builder.add( new DefaultInference() ).build();
    }

    protected static void addTypeHelper( TypeInference typeInference ) {
        typeHelpers.add( typeInference );
    }

    public static void destroy() {
        callback = null;
        circuitBreakers = null;
    }

    protected static void setTraceMode( boolean mode ) {
        traceMode = mode;
    }
    public static void addCircuitBreaker( CircuitBreaker handler ) {
        circuitBreakers.put(handler.getName(), handler);
    }
    public static void addCircuitBreaker( String name, int timeout, int exceptionThreshold ) {
        circuitBreakers.put(name, new CircuitBreaker(name, timeout, exceptionThreshold));
    }

    public static void setDefaultCircuitBreakerSettings( int timeoutMillis, int exceptionThreshold) {
        timeout = timeoutMillis;
        DejaVuAspect.exceptionThreshold = exceptionThreshold;
    }

    @Around("execution(@com.jayway.dejavu.core.annotation.Traced * *(..))")
    public Object traced( ProceedingJoinPoint proceed ) throws Throwable {
        if (traceId.get() != null) {
            // setup already done just proceed
            return proceed.proceed();
        }

        Trace trace = trace( proceed );
        traceId.set( trace.getId() );
        threadId.set( trace.getId() );
        runningTraces.put(trace.getId(), new RunningTrace(trace));

        threadLocalInImpure.set(false);
        try {
            Object result = proceed.proceed();
            callbackIfFinished(trace, null);
            return result;
        } catch ( Throwable t) {
            runningTraces.get( trace.getId() ).setThrowable( t );
            callbackIfFinished(trace, t);
            throw t;
        }
    }

    private static Trace trace( ProceedingJoinPoint proceed ) {
        if ( traceMode ) {
            MethodSignature signature = (MethodSignature) proceed.getSignature();
            setIgnore(true);
            String id = UUID.randomUUID().toString();
            setIgnore(false);
            return new Trace(id, signature.getMethod(), proceed.getArgs() );
        } else {
            return DejaVuTrace.getTrace();
        }
    }

    @Around("execution(@com.jayway.dejavu.core.annotation.Impure * *(..)) && @annotation(impure)")
    public Object integrationPoint( ProceedingJoinPoint proceed, Impure impure) throws Throwable {
        if ( fallThrough() ) {
            return proceed.proceed();
        }
        return handle(proceed, impure.integrationPoint() );
    }

    public static Object handle(ProceedingJoinPoint proceed, String integrationPoint) throws Throwable {
        if (!traceMode) {
            return DejaVuTrace.nextValue(threadId.get());
        }

        CircuitBreakerWrapper circuitBreaker = new CircuitBreakerWrapper( integrationPoint );
        Object result = null;
        try {
            threadLocalInImpure.set(true);
            circuitBreaker.verify();
            result = proceed.proceed();
            return result;
        } catch ( Throwable t ) {
            result = new ThrownThrowable( t );
            throw t;
        } finally {
            Trace trace = runningTraces.get( traceId.get() ).getTrace();
            add( trace, result, typeInference.inferType( result, (MethodSignature) proceed.getSignature() ) );
            circuitBreaker.result( result );
            threadLocalInImpure.set(false);
        }
    }

    protected static boolean isTraceMode() {
        return traceMode;
    }

    protected static boolean fallThrough() throws Throwable {
        if ( traceMode && traceId.get() == null ) {
            // we are outside of a trace so call the method normally
            return true;
        }
        boolean inIntegratonPoint = threadLocalInImpure.get() == null ? false : threadLocalInImpure.get();
        if ( traceMode && inIntegratonPoint ) {
            // we are in a trace and in an integration point
            // called from another integration point, so don't
            // trace this call
            return true;
        }
        if ( threadLocalIgnore.get() != null && threadLocalIgnore.get() ) {
            return true;
        }
        return false;
    }

    protected static void setIgnore( boolean ignore ) {
        threadLocalIgnore.set( ignore );
    }

    static CircuitBreaker getCircuitBreaker( String integrationPoint ) {
        if (!circuitBreakers.containsKey(integrationPoint)) {
            circuitBreakers.put( integrationPoint, new CircuitBreaker(integrationPoint, timeout, exceptionThreshold));
        }
        return circuitBreakers.get( integrationPoint );
    }

    @Around("execution(@com.jayway.dejavu.core.annotation.AttachThread * *(..))")
    public void attach( ProceedingJoinPoint proceed ) throws Throwable {
        Object[] args = proceed.getArgs();
        if ( threadId.get() != null && args != null ) {
            patch(args);
        }
        proceed.proceed( args );
    }

    private static String getChildThreadId() {
        if ( traceMode ) {
            // generate id for new thread that will begin this runnable
            setIgnore(true);
            String id = UUID.randomUUID().toString();
            setIgnore(false);
            return threadId.get() + "." + id;
        } else {
            // when in deja vu mode threadId is already set
            return DejaVuTrace.nextChildThreadId( threadId.get() );
        }
    }

    protected static void patch(Object[] args) {
        if ( args == null || args.length == 0 ) {
            return;
        }
        for (int i=0; i<args.length; i++) {
            Object arg = args[i];
            if ( arg instanceof Runnable ) {
                String childThreadId = getChildThreadId();
                args[i] = new AttachedRunnable((Runnable) arg, traceId.get(), childThreadId);
                // if runnable is passed as argument to @AttachThread and not
                // run, the trace will not stop
                runningTraces.get( traceId.get() ).threadAttached( childThreadId );
            } else if ( arg instanceof Callable ) {
                String childThreadId = getChildThreadId();
                args[i] = new AttachedCallable((Callable) arg, traceId.get(), childThreadId);
                runningTraces.get( traceId.get() ).threadAttached( childThreadId );
            }
        }
    }

    private static void add( Trace trace, Object value, Class<?> type ) {
        synchronized (trace) {
            TraceElement element = new TraceElement(threadId.get(), value, type);
            trace.addValue(element);
        }
    }

    private static void callbackIfFinished(Trace trace, Throwable t) {
        RunningTrace runningTrace = runningTraces.get(trace.getId());
        if ( runningTrace.completed() ) {
            if ( traceMode ) callback.traced(trace, t);
            runningTraces.remove( trace.getId() );
        }
        traceId.remove();
        threadId.remove();
        threadLocalInImpure.remove();
    }

    public static void threadStarted( String threadId, String traceId ) {
        DejaVuAspect.threadId.set( threadId );
        DejaVuAspect.traceId.set( traceId );
    }

    public static void threadCompleted() {
        RunningTrace runningTrace = DejaVuAspect.runningTraces.get( traceId.get() );
        runningTrace.threadCompleted(threadId.get());
        callbackIfFinished(runningTrace.getTrace(), runningTrace.getThrowable());
    }

    public static void threadThrowable(Throwable throwable) {
        if ( traceMode ) {
            Trace trace = runningTraces.get(traceId.get()).getTrace();
            synchronized ( trace ) {
                trace.addThreadThrowable( new ThreadThrowable( threadId.get(), throwable ));
            }
        }
    }

    public static boolean traceCompleted( String traceId ) {
        return runningTraces.get( traceId ) == null;
    }
}

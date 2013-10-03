package com.jayway.dejavu.core;

import com.jayway.dejavu.core.chainer.ChainBuilder;
import com.jayway.dejavu.core.exception.TraceEndedException;
import com.jayway.dejavu.core.repository.TraceCallback;

import java.util.*;
import java.util.concurrent.Callable;

public class RunningTrace implements ImpureHandler {

    private static ThreadLocal<String> threadId = new ThreadLocal<String>();
    private static ThreadLocal<Boolean> threadLocalIgnore = new ThreadLocal<Boolean>();
    private static ThreadLocal<Boolean> threadLocalInImpure = new ThreadLocal<Boolean>();
    private static List<ImpureHandler> impureHandlers = new ArrayList<ImpureHandler>();
    private static List<TraceValueHandler> traceValueHandlers = new ArrayList<TraceValueHandler>();

    private TraceCallback callback;
    private boolean recording;
    private final Trace trace;
    private Throwable throwable;
    private Set<String> attachedThreads;
    private Set<String> completedThreads;
    private TraceValueHandler traceValueHandler;
    private ImpureHandler impureHandler;
    private List<TraceElement> values;
    private int index;
    private Map<String, LinkedList<String>> childThreads;

    protected RunningTrace( Trace trace, TraceCallback callback, boolean recording ) {
        this.trace = trace;
        this.callback = callback;
        this.recording = recording;
        traceValueHandler = ChainBuilder.compose(TraceValueHandler.class).add(new TraceValueHandlerAdapter()).add( traceValueHandlers ).build();
        if ( recording ) {
            trace.setId(generateId());
            threadId.set(trace.getId());
            attachedThreads = new HashSet<String>();
            completedThreads = new HashSet<String>();
            setInImpure(false);
            impureHandler = ChainBuilder.all(ImpureHandler.class).add(new ImpureHandler() {
                public void before(RunningTrace runningTrace, String integrationPoint) {
                    setInImpure(true);
                }
                public void success(RunningTrace runningTrace, Object result) {
                    add(result);
                    setInImpure(false);
                }
                public void failure(RunningTrace runningTrace, Throwable t) {
                    add(new ThrownThrowable(t));
                    setInImpure(false);
                }
            }).add( impureHandlers).build();
        } else {
            threadId.set(trace.getId());
            values = trace.getValues();
            index = 0;
            childThreads = new HashMap<String, LinkedList<String>>();
            for (TraceElement element : trace.getValues()) {
                String threadId = element.getThreadId();
                if ( threadId.contains(".") ) {
                    // this is from a child thread
                    String parent = threadId.substring(0, threadId.lastIndexOf("."));
                    if ( !childThreads.containsKey( parent) ) {
                        childThreads.put( parent, new LinkedList<String>() );
                    }
                    if ( !childThreads.get(parent).contains( threadId) ) {
                        childThreads.get(parent).addLast( threadId );
                    }
                }
            }

        }
    }

    public static void initialize() {
        impureHandlers.clear();
        traceValueHandlers.clear();
    }

    public static void addImpureHandler( ImpureHandler handler ) {
        impureHandlers.add(handler);
    }

    public static void addTraceHandler( TraceValueHandler handler ) {
        traceValueHandlers.add(handler);
    }

    public synchronized void threadAttached( String id ) {
        if ( attachedThreads == null ) {
            attachedThreads = new HashSet<String>();
            completedThreads = new HashSet<String>();
        }
        attachedThreads.add(id);
    }

    public synchronized void threadCompleted() {
        completedThreads.add(threadId.get());
        callbackIfFinished(throwable);
    }

    public void callbackIfFinished(Throwable t) {
        throwable = t;
        if ( attachedThreadsCompleted() && isRecording() ) {
            callback.traced(trace, t);
        }
        threadId.remove();
        threadLocalInImpure.remove();
    }

    public void threadStarted( String threadId ) {
        RunningTrace.threadId.set( threadId );
        DejaVuPolicy.runningTrace.set( this );
    }

    public void threadThrowable(Throwable throwable) {
        if ( isRecording() ) {
            synchronized ( trace ) {
                trace.addThreadThrowable( new ThreadThrowable( threadId.get(), throwable ));
            }
        }
    }

    protected synchronized boolean attachedThreadsCompleted() {
        return attachedThreads == null || attachedThreads.size() == completedThreads.size();
    }

    public boolean isRecording() {
        return recording;
    }

    @Override
    public void before(RunningTrace runningTrace, String integrationPoint) {
        impureHandler.before(runningTrace, integrationPoint);
    }

    @Override
    public void success(RunningTrace runningTrace, Object result) {
        impureHandler.success(runningTrace, result);
    }

    @Override
    public void failure(RunningTrace runningTrace, Throwable t) {
        impureHandler.failure(runningTrace, t);
    }

    private void add( Object value ) {
        synchronized (trace) {
            TraceElement element = new TraceElement(threadId.get(), traceValueHandler.record(value));
            trace.addValue(element);
        }
    }

    protected synchronized Object nextValue() throws Throwable {
        while (true) {
            if (index >= values.size()) {
                throw new TraceEndedException();
            }
            TraceElement result = values.get(index);
            if ( threadId.get().equals(result.getThreadId()) ) {
                index++;
                notifyAll();
                if ( result.getValue() instanceof ThrownThrowable ) {
                    throw ((ThrownThrowable) result.getValue()).getThrowable();
                }
                return traceValueHandler.replay(result.getValue());
            } else {
                try {
                    // we need to wait for the value to be ready
                    wait();
                } catch (InterruptedException e) {
                    // ignore. Continue waiting
                }
            }
        }
    }

    public String getChildThreadId() {
        if (!isRecording()) {
            return childThreads.get(threadId.get()).removeFirst();
        }
        // generate id for new thread included in trace
        return threadId.get() + "." + generateId();
    }

    public void patch(Object[] args) {
        if ( args == null || args.length == 0 ) {
            return;
        }
        for (int i=0; i<args.length; i++) {
            Object arg = args[i];
            if ( arg instanceof Runnable ) {
                String childThreadId = getChildThreadId();
                args[i] = new AttachedRunnable((Runnable) arg, this, childThreadId);
                // if runnable is passed as argument to @AttachThread and not
                // run, the trace will not stop
                threadAttached(childThreadId);
            } else if ( arg instanceof Callable) {
                String childThreadId = getChildThreadId();
                args[i] = new AttachedCallable((Callable) arg, this, childThreadId);
                threadAttached(childThreadId);
            }
        }
    }

    public boolean ignore() {
        return threadLocalIgnore.get() != null && threadLocalIgnore.get();
    }

    public boolean inImpure() {
        return threadLocalInImpure.get() == null ? false : threadLocalInImpure.get();
    }

    private String generateId() {
        threadLocalIgnore.set( true );
        String id = UUID.randomUUID().toString();
        threadLocalIgnore.set( false );
        return id;
    }

    private void setInImpure(boolean inImpure ) {
        threadLocalInImpure.set(inImpure);
    }
}

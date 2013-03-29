package com.jayway.dejavu;

import com.jayway.dejavu.core.DejaVuAspect;
import com.jayway.dejavu.core.DejaVuTrace;
import com.jayway.dejavu.core.Trace;
import com.jayway.dejavu.core.TraceElement;
import com.jayway.dejavu.impl.TraceCallbackImpl;
import com.jayway.dejavu.impl.WithThreads;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiThreadedTest {

    private TraceCallbackImpl callback;

    @Before
    public void setup() {
        callback = new TraceCallbackImpl();
        DejaVuAspect.initialize(callback);
        DejaVuTrace.setBeforeRunCallback(null);
    }

    @Test
    public void with_three_child_threads() throws Throwable {
        WithThreads withThreads = new WithThreads();
        int threads = 5;
        withThreads.begin( threads );
        waitForCompletion();


        Trace trace = callback.getTrace();

        final List<TraceElement> values = new ArrayList<TraceElement>();
        DejaVuTrace.setNextValueCallback(new DejaVuTrace.NextValueCallback() {
            public void nextValue(Object value) {
                values.add(new TraceElement(Thread.currentThread().getName(), value));
            }
        });
        DejaVuTrace.run(trace);

        Map<String, String> threadNameMap = new HashMap<String, String>();
        for (int i=0; i<trace.getValues().size(); i++ ) {
            TraceElement element = trace.getValues().get(i);
            TraceElement rerunElement = values.get(i);
            Assert.assertEquals(element.getValue(), rerunElement.getValue());
            if ( threadNameMap.containsKey(element.getThreadId()) ) {
                Assert.assertEquals( threadNameMap.get( element.getThreadId()), rerunElement.getThreadId());
            } else {
                // first encounter of two threadIds, must be same for all other
                threadNameMap.put( element.getThreadId(), rerunElement.getThreadId() );
            }
        }

        // exactly three threads are expected to have run
        Assert.assertEquals( threads, threadNameMap.size() );
    }

    private void waitForCompletion() {
        // wait for the trace to be done
        while ( callback.getTrace() == null ) {
            try {
                Thread.sleep( 500 );
            } catch (InterruptedException e ){
                // ignore
            }
        }
    }
}
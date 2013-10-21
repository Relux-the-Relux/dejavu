package com.jayway.dejavu;

import com.jayway.dejavu.core.*;
import com.jayway.dejavu.core.interfaces.Trace;
import com.jayway.dejavu.core.interfaces.TraceValueHandler;
import com.jayway.dejavu.core.memorytrace.MemoryTraceBuilder;
import com.jayway.dejavu.impl.FailingWithThreads;
import com.jayway.dejavu.impl.TraceCallbackImpl;
import com.jayway.dejavu.impl.WithThreads;
import com.jayway.dejavu.recordreplay.RecordReplayFactory;
import com.jayway.dejavu.recordreplay.RecordReplayer;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiThreadedTracerTest {

    private Logger log = LoggerFactory.getLogger( MultiThreadedTracerTest.class );

    private TraceCallbackImpl callback;

    @Before
    public void setup() {
        callback = new TraceCallbackImpl();
        RecordReplayer.initialize(callback);
        DejaVuEngine.setFactory(new RecordReplayFactory());
    }

    @Test
    public void with_three_child_threads() throws Throwable {
        WithThreads withThreads = new WithThreads();
        int threads = 3;
        withThreads.begin(threads);
        waitForCompletion();


        Trace trace = callback.getTrace();

        final List<TraceElement> values = new ArrayList<TraceElement>();
        RecordReplayer.replay( trace, new TraceValueHandler() {
            @Override
            public Object handle(Object value) {
                values.add( new TraceElement(Thread.currentThread().getName(), value));
                return value;
            }
        });

        Map<String, String> threadNameMap = new HashMap<String, String>();
        int i = 0;
        for (TraceElement element: trace ) {
            TraceElement rerunElement = values.get(i);
            Assert.assertEquals(element.getValue(), rerunElement.getValue());
            if ( threadNameMap.containsKey(element.getThreadId()) ) {
                Assert.assertEquals( threadNameMap.get( element.getThreadId()), rerunElement.getThreadId());
            } else {
                // first encounter of two threadIds, must be same for all other
                threadNameMap.put( element.getThreadId(), rerunElement.getThreadId() );
            }
            i++;
        }

        // exactly three threads are expected to have run
        Assert.assertEquals(threads, threadNameMap.size());
    }

    @Test
    public void with_failing_trace() throws Throwable {
        int threadCount = 8;
        new FailingWithThreads().begin( threadCount );
        waitForCompletion();
        Trace trace = callback.getTrace();

        final List<TraceElement> values = new ArrayList<TraceElement>();
        RecordReplayer.replay( trace, new TraceValueHandler() {
            @Override
            public Object handle(Object value) {
                values.add( new TraceElement(Thread.currentThread().getName(), value));
                return value;
            }
        });

        Map<String, String> threadNameMap = new HashMap<String, String>();
        int i = 0;
        for (TraceElement element: trace ) {
            TraceElement rerunElement = values.get(i);
            //System.out.println(element.getValue() + " produced on thread " +element.getThreadId() );
            Assert.assertEquals(element.getValue(), rerunElement.getValue());
            if ( threadNameMap.containsKey(element.getThreadId()) ) {
                Assert.assertEquals( threadNameMap.get( element.getThreadId()), rerunElement.getThreadId());
            } else {
                // first encounter of two threadIds, must be same for all other
                threadNameMap.put( element.getThreadId(), rerunElement.getThreadId() );
            }
            i++;
        }

        Assert.assertEquals( callback.getThreadCauses().size() +  threadCount, threadNameMap.size() );
    }

    @Test
    public void withthreadstest() throws Throwable {
        TraceBuilder builder = new MemoryTraceBuilder("3bf8c479-cd1e-4281-9268-f17c93a4f816").
                startMethod(WithThreads.class);
        builder.startArguments(8);
        builder.threadIds("0b11975e-a976-485e-bbd2-0eca19f4df13", "8279a430-f2d9-4d16-b747-84ea002ed45a", "4f2e52d1-07d2-4145-ae3f-f818b8d5ebeb", "8885cd0f-f71e-4a43-9df7-785dd1750bc3", "f8b2190d-b977-4387-9422-db90b0ec67df", "54b8eea6-8811-4598-baf2-be6a78914460", "7c6ab34f-6dd7-4ba0-b4ed-e8d059a5373c", "3af6082b-b432-40f7-a50d-61dbd8e24224");

        builder.addT(1, "078b7afc-677a-46a8-8bff-44024f993e4d", 363L).
                addT(2, "765876ec-a678-40f9-86db-dea603c9015c").
                addT(3, "e288c813-c81c-4b1c-8f1e-b9db13bf5ab5").
                addT(2, 758L).
                addT(3, 148L).
                addT(4, "171076ea-e992-46b3-9f1a-74daab5ac4c4", 2L).
                addT(5, "1511b882-a7df-48d7-bd9b-b16988f04f6e", 58L).
                addT(6, "4218a66e-dbed-4082-a199-82a1e4975649", 167L).
                addT(7, "c9f3745f-b7a5-4956-b7f6-722769e03b21", 382L).
                addT(4, "e806e415-6a60-469f-8b8a-adc8647e7cdf").
                addT(8, "5f33b5ad-de1e-4584-b849-d68401394a7a", 925L).
                addT(5, "1a5fb8e4-72d3-489b-974b-13ed4719bef0").
                addT(3, "2abb6376-e4f1-48f1-847f-38176f037fd8").
                addT(6, "8a35036d-332a-4aae-a635-72f43e641641").
                addT(1, "a3e110c2-457a-402a-b1cb-2183432944d8").
                addT(7, "d6b2ca07-c2a4-4360-bf46-ffd80e103daa").
                addT(2, "4f374db2-809d-483b-b606-8f7411af210a").
                addT(8, "5b25b42c-1497-4156-831c-b81dcac83e31");


        final List<TraceElement> values = new ArrayList<TraceElement>();
        Trace build = builder.build();
        RecordReplayer.replay(build, new TraceValueHandler() {
            @Override
            public Object handle(Object value) {
                values.add( new TraceElement(Thread.currentThread().getName(), value));
                return value;
            }
        });

        Map<String, String> threadNameMap = new HashMap<String, String>();
        int i=0;
        for (TraceElement element: build ) {
            TraceElement rerunElement = values.get(i);
            Assert.assertEquals(element.getValue(), rerunElement.getValue());
            if ( threadNameMap.containsKey(element.getThreadId()) ) {
                Assert.assertEquals( threadNameMap.get( element.getThreadId()), rerunElement.getThreadId());
            } else {
                // first encounter of two threadIds, must be same for all other
                threadNameMap.put( element.getThreadId(), rerunElement.getThreadId() );
            }
            i++;
        }

        // exactly three threads are expected to have run
        Assert.assertEquals( 8, threadNameMap.size() );
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

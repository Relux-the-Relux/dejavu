package com.jayway.dejavu;

import com.jayway.dejavu.core.AutoImpureTraceValueHandler;
import com.jayway.dejavu.core.DejaVuPolicy;
import com.jayway.dejavu.core.Pure;
import com.jayway.dejavu.core.Trace;
import com.jayway.dejavu.core.marshaller.Marshaller;
import com.jayway.dejavu.core.marshaller.SimpleExceptionMarshaller;
import com.jayway.dejavu.impl.FileReading;
import com.jayway.dejavu.impl.RandomProxyExample;
import com.jayway.dejavu.impl.TraceCallbackImpl;
import com.jayway.dejavu.recordreplay.RecordReplayer;
import com.jayway.dejavu.recordreplay.TraceBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.List;

public class ProxiedTest {

    private Logger log = LoggerFactory.getLogger( ProxiedTest.class );

    private TraceCallbackImpl callback;

    @Before
    public void setup() {
        callback = new TraceCallbackImpl();
        DejaVuPolicy.initialize(callback);
        AutoImpureTraceValueHandler.initialize();
    }

    @Test
    public void randomProxy() throws Throwable {
        RandomProxyExample example = new RandomProxyExample();

        int result = example.invoke();

        Trace trace = callback.getTrace();
        Assert.assertNotNull( trace );

        String test = new Marshaller().marshal(trace);
        System.out.println( test );
        Integer result2 = RecordReplayer.replay(trace);

        System.out.println(result + " and " + result2);
        Assert.assertEquals(result, result2.intValue());
    }

    @Test
    public void notReadingFile() throws Throwable {
        // now read the file in test mode where it only produces one line
        TraceBuilder builder = TraceBuilder.build()
                .setMethod(FileReading.class)
                .addMethodArguments("not a filename");

        builder.add(Pure.PureFileReader, Pure.PureBufferedReader, "ONLY LINE", null);

        List<String> lines = (List<String>) builder.run();

        Assert.assertEquals( 1, lines.size());
        Assert.assertEquals( "ONLY LINE", lines.get(0));
    }

    @Test
    public void notReadingFileException() throws Throwable {
        // now read the file in test mode where it only produces one line
        TraceBuilder builder = TraceBuilder.build(new SimpleExceptionMarshaller())
                .setMethod(FileReading.class)
                .addMethodArguments( "nonexisting.xyz");

        // reading the first line of the Buffered reader
        // will produce a ConcurrentModificationException
        builder.add(ConcurrentModificationException.class );

        try {
            builder.run();
            Assert.fail();
        } catch (ConcurrentModificationException e ) {
            // it must throw IOException
        }
    }
}

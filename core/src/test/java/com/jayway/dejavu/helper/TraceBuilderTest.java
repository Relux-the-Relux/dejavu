package com.jayway.dejavu.helper;

import com.jayway.dejavu.core.DejaVuAspect;
import com.jayway.dejavu.core.DejaVuTrace;
import com.jayway.dejavu.core.Trace;
import com.jayway.dejavu.core.marshaller.TraceBuilder;
import com.jayway.dejavu.core.marshaller.Marshaller;
import com.jayway.dejavu.impl.ExampleTrace;
import com.jayway.dejavu.impl.TraceCallbackImpl;
import junit.framework.Assert;
import org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler;
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

public class TraceBuilderTest {

    @Before
    public void setup() {
        DejaVuTrace.setBeforeRunCallback(null);
    }

    @Test
    public void builder() throws Throwable {
        TraceBuilder builder = TraceBuilder.build().setMethod(ExampleTrace.class);

        builder.add( 349013193767909L, String.class, "d09c2893-2835-4cbe-8c8e-4c790c268ed0", 349013194166199L);

        try {
            builder.run();
            Assert.fail();
        } catch (ArithmeticException e) {

        }
    }


    @Test
    public void verify_generated_test() throws Throwable {
        TraceCallbackImpl callback = new TraceCallbackImpl();
        DejaVuAspect.initialize( callback );

        final Integer origResult = new WithSimpleTypes().simple();

        String test = new Marshaller().marshal(callback.getTrace());
        System.out.println( test );

        JavaSourceCompiler compiler = new JavaSourceCompilerImpl();
        JavaSourceCompiler.CompilationUnit compilationUnit = compiler.createCompilationUnit();
        compilationUnit.addJavaSource("com.jayway.dejavu.helper.WithSimpleTypesTest", test );
        ClassLoader classLoader = compiler.compile(compilationUnit);
        Class testClass = classLoader.loadClass("com.jayway.dejavu.helper.WithSimpleTypesTest");

        Object o = testClass.newInstance();
        Method method = testClass.getDeclaredMethod("withsimpletypestest");

        DejaVuTrace.setBeforeRunCallback( new DejaVuTrace.BeforeRunCallback() {
            public void beforeRun(Trace trace) {
                int loop = (Integer) trace.getValues().get(0).getValue();
                Assert.assertEquals( loop+1, trace.getValues().size() );
                int result = 1;
                for ( int i=1; i<loop+1; i++ ) {
                    result *= (Integer) trace.getValues().get( i ).getValue();
                }
                Assert.assertEquals( origResult.intValue(), result );
            }
        });
        method.invoke( o );
    }

    @Test
    public void simple_types_test() throws Throwable {
        TraceBuilder builder = TraceBuilder.build().setMethod(AllSimpleTypes.class);

        builder.add( String.class, "string", 1.1F, true, 2.2, 1L, 1 );

        String result = (String) builder.run();

        Assert.assertEquals("string1.1true2.211", result );
    }
}
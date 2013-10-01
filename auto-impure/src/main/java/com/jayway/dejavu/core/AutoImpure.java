package com.jayway.dejavu.core;

import com.jayway.dejavu.core.impl.ZipFileEnumeration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.Random;
import java.util.zip.ZipFile;

@Aspect
public class AutoImpure {

    public static void initialize() {
        RunningTrace.addTraceHandler( new AutoImpureTraceValueHandler());
    }

    /*@Around("call(* java.util.concurrent.ExecutorService.invoke*(..))")
    public Object threadPoolInvoke( ProceedingJoinPoint join ) throws Throwable {
        Collection<Callable> callables = (Collection<Callable>) join.getArgs()[0];
        for (Callable callable : callables) {
            DejaVuAspect.patch( callable );
        }
        return join.proceed( callables.toArray() );
    } */

    @Around("call(* java.util.concurrent.ExecutorService.submit(..))")
    public Object threadPoolSubmit( ProceedingJoinPoint join ) throws Throwable {
        Object[] args = join.getArgs();
        DejaVuPolicy.patchForAttachThread(args);
        return join.proceed( args );
    }

    @Around("call(java.util.Random.new(..))")
    public Random random(ProceedingJoinPoint proceed ) throws Throwable {
        return impureConstruction( proceed, Random.class );
    }

    @Around("call(* java.util.Random.*(..))")
    public Object randomMethods(ProceedingJoinPoint proceed ) throws Throwable {
        return impureMethod(proceed);
    }

    @Around("call(java.io.FileReader.new(..))")
    public FileReader fileReader(ProceedingJoinPoint proceed ) throws Throwable {
        return impureConstruction(proceed, FileReader.class);
    }

    @Around("call(* java.io.FileReader.*(..))")
    public Object fileReaderMethods( ProceedingJoinPoint proceed ) throws Throwable {
        return impureMethod(proceed);
    }

    @Around("call(java.io.BufferedReader.new(..))")
    public BufferedReader buffered(ProceedingJoinPoint proceed ) throws Throwable {
        return impureConstruction(proceed, BufferedReader.class);
    }

    @Around("call(* java.io.BufferedReader.*(..))")
    public Object bufferedReaderMethods( ProceedingJoinPoint proceed ) throws Throwable {
        return impureMethod(proceed);
    }

    @Around("call(java.io.InputStreamReader.new(..))")
    public InputStreamReader inputStreamReader(ProceedingJoinPoint proceed ) throws Throwable {
        return impureConstruction(proceed, InputStreamReader.class);
    }

    @Around("call(* java.util.UUID.*(..))")
    public Object uuid( ProceedingJoinPoint proceed ) throws Throwable {
        if (Modifier.isStatic( proceed.getSignature().getModifiers() )) {
            return proceed.proceed();
        }
        return impureMethod(proceed);
    }

    @Around("call(java.util.zip.ZipFile.new(..))")
    public ZipFile zipFile( ProceedingJoinPoint proceed ) throws Throwable {
        return impureConstruction( proceed, ZipFile.class );
    }

    @Around("call(* java.util.zip.ZipFile.*(..))")
    public Object zipFileMethods( ProceedingJoinPoint proceed ) throws Throwable {
        if ( proceed.getSignature().getName().equals("entries") ) {
            Enumeration enumeration = (Enumeration) proceed.proceed();
            return new ZipFileEnumeration( enumeration );
        }
        return impureMethod(proceed);
    }

    @Around("call(* java.util.zip.ZipEntry.*(..))")
    public Object zipEntryMethods( ProceedingJoinPoint proceed ) throws Throwable {
        return impureMethod(proceed);
    }


    private Object impureMethod(ProceedingJoinPoint proceed) throws Throwable {
        // if already inside an @impure just proceed
        DejaVuPolicy policy = new DejaVuPolicy();
        return policy.aroundImpure( new AspectJInterception(proceed), "" );
    }

    private <T> T impureConstruction( ProceedingJoinPoint proceed, Class<T> clazz ) throws Throwable {
        // if already inside an @impure just proceed
        DejaVuPolicy policy = new DejaVuPolicy();
        return (T) policy.aroundImpure(new AspectJInterception(proceed), "");
    }
}

package com.jayway.dejavu.core.chainer;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChainBuilder<T> implements MethodInterceptor {

    private Class<T> clazz;
    private List<T> instances;

    public static <T> ChainBuilder<T> chain( Class<T> clazz ) {
        return new ChainBuilder<T>(clazz);
    }

    private ChainBuilder( Class<T> clazz ) {
        this.clazz = clazz;
        instances = new ArrayList<T>();
    }

    public ChainBuilder<T> add( T t ) {
        instances.add( t );
        return this;
    }

    public ChainBuilder<T> add( T... ts ) {
        Collections.addAll(instances, ts);
        return this;
    }

    public T build() {
        if ( instances.isEmpty() ) throw new BuildException();
        // proxy handling delegating for each non-void method
        return (T) Enhancer.create( clazz, this );
    }

    @Override
    public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        finished.set( false );
        boolean isVoid = method.getReturnType().equals( Void.TYPE );
        for (T instance : instances) {
            try {
                Object result = method.invoke(instance, args);
                if (isVoid) continue;
                if (result != null || finished.get() ) {
                    return result;
                }
            } catch (InvocationTargetException e ) {
                throw e.getCause();
            }
        }
        if ( isVoid ) return null;

        // TODO better error message
        StringBuilder sb = new StringBuilder("Argument(s): ");
        boolean first = true;
        for (Object arg : args) {
            if ( first ) {
                first = false;
            } else {
                sb.append(", ");
            }
            if ( arg != null ) {
                sb.append("[").append(arg.getClass()).append(" : ").append(arg.toString()).append("]");
            } else {
                sb.append("null");
            }
        }
        throw new CouldNotHandleException(sb.toString());
    }

    public static void finished() {
        finished.set( true );
    }

    private static ThreadLocal<Boolean> finished = new ThreadLocal<Boolean>();
}

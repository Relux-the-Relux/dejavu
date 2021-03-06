package com.jayway.dejavu.core.chainer;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChainBuilder<T> implements MethodInterceptor {

    private Class<T> clazz;
    private List<T> instances;
    private boolean compose;

    public static <T> ChainBuilder<T> handle( Class<T> clazz ) {
        return new ChainBuilder<T>(clazz, false);
    }

    public static <T> ChainBuilder<T> compose( Class<T> clazz ) {
        return new ChainBuilder<T>(clazz, true);
    }

    private ChainBuilder( Class<T> clazz, boolean compose) {
        this.compose = compose;
        this.clazz = clazz;
        instances = new ArrayList<T>();
    }

    public ChainBuilder<T> add( T t ) {
        instances.add(t);
        return this;
    }

    public ChainBuilder<T> add(Collection<T> collection ) {
        if ( collection != null ) {
            for (T t : collection) {
                instances.add(t);
            }
        }
        return this;
    }

    public ChainBuilder<T> add( T... ts ) {
        Collections.addAll(instances, ts);
        return this;
    }

    public T build() {
        // only for 'all' is makes sense to be empty
        //if ( instances.isEmpty() ) throw new BuildException();
        // proxy handling delegating for each non-void method
        return (T) Enhancer.create( clazz, this );
    }

    @Override
    public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        if ( compose ) return compose(method, args);
        return handle(method, args);
    }

    // return  f(g(h( value )))
    private Object compose(Method method, Object[] args) throws Throwable {
        finished.set( false );
        Object lastResult = null;
        if ( instances.isEmpty() ) return args[0];
        for (T instance : instances) {
            try {
                if ( lastResult == null ) {
                    lastResult = method.invoke(instance, args);
                } else {
                    lastResult = method.invoke(instance, lastResult);
                }
            } catch (InvocationTargetException e ) {
                throw e.getCause();
            }
        }
        return lastResult;
    }

    // return first result
    private Object handle(Method method, Object[] args) throws Throwable {
        finished.set( false );
        for (T instance : instances) {
            try {
                Object result = method.invoke(instance, args);
                if (result != null || finished.get() ) {
                    return result;
                }
            } catch (InvocationTargetException e ) {
                throw e.getCause();
            }
        }

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

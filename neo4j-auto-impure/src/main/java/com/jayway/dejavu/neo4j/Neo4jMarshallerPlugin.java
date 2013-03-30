package com.jayway.dejavu.neo4j;

import com.jayway.dejavu.core.TraceElement;
import com.jayway.dejavu.core.marshaller.MarshallerPlugin;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.helpers.collection.PagingIterator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.easymock.EasyMock.createMock;

public class Neo4jMarshallerPlugin implements MarshallerPlugin  {

    private static final Set<Class<?>> neo4jClasses;

    static {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        // so far these are the supported classes
        classes.add( GraphDatabaseService.class );
        classes.add( Transaction.class );
        classes.add( Node.class);
        classes.add( IndexManager.class);
        classes.add( Index.class);
        classes.add( IndexHits.class);
        classes.add( PagingIterator.class);

        neo4jClasses = Collections.unmodifiableSet( classes );
    }

    @Override
    public Object unmarshal(Class<?> clazz, String marshalValue ) {
        if ( neo4jClasses.contains( clazz )) {
            // create mock instance
            return createMock( clazz );
        }
        return null;
    }

    @Override
    public String marshalObject(Object value) {
        if ( neo4jClasses.contains( value.getClass()) ) {
            // all neo4j classes serializes to the default value
            return "";
        }
        return null;
    }

    @Override
    public String asTraceBuilderArgument(TraceElement element ) {
        if ( element.getType() == null ) {
            return element.getValue().getClass().getSimpleName() + ".class";
        } else {
            return element.getType().getSimpleName() + ".class";
        }
    }
}

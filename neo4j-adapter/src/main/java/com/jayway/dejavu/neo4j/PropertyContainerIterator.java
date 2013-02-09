package com.jayway.dejavu.neo4j;

import com.jayway.dejavu.core.annotation.Impure;
import org.neo4j.graphdb.PropertyContainer;

import java.util.Iterator;

public class PropertyContainerIterator {

    private Iterator<PropertyContainer> iterator;

    PropertyContainerIterator(Iterator<PropertyContainer> iterator) {
        this.iterator = iterator;
    }

    @Impure( integrationPoint = "neo4j" )
    public Boolean hasNext() {
        return iterator.hasNext();
    }

    @Impure( integrationPoint = "neo4j" )
    public DVPropertyContainer next() {
        return new DVPropertyContainer(iterator.next());
    }

    @Impure( integrationPoint = "neo4j" )
    public void remove() {
        iterator.remove();
    }
}

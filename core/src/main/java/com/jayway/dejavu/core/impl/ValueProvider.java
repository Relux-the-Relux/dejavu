package com.jayway.dejavu.core.impl;

import com.jayway.dejavu.core.Trace;
import com.jayway.dejavu.core.TraceEndedException;
import com.jayway.dejavu.core.TracedElement;
import com.jayway.dejavu.value.Value;

import java.util.ArrayList;
import java.util.List;

public class ValueProvider {

    private List<Value> values;
    private int index;

    public ValueProvider( Trace trace ) {
        values = new ArrayList<Value>( trace.getTracedElements().size() );
        for (TracedElement element : trace.getTracedElements()) {
            values.add( element.getValue() );
        }
        index = 0;
    }
    public ValueProvider( List<Value> values ) {
        this.values = values;
        index = 0;
    }

    public Value getNext() {
        checkIndex();
        Value value = values.get(index);
        index++;
        return value;
    }

    private void checkIndex() {
        if (index >= values.size()) {
            throw new TraceEndedException();
        }
    }
}

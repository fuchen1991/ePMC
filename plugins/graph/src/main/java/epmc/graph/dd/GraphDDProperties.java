package epmc.graph.dd;

import java.io.Closeable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import epmc.dd.ContextDD;
import epmc.dd.DD;
import epmc.error.EPMCException;
import epmc.value.Type;
import epmc.value.Value;
import epmc.value.ValueObject;

public final class GraphDDProperties implements Closeable {
    private final Map<Object,Value> graphProperties = new LinkedHashMap<>();
    private final Map<Object,Value> graphPropertiesExternal = Collections.unmodifiableMap(graphProperties);
    private final Map<Object,DD> nodeProperties = new LinkedHashMap<>();
    private final Map<Object,DD> nodePropertiesExternal = Collections.unmodifiableMap(nodeProperties);
    private final Map<Object,DD> edgeProperties = new LinkedHashMap<>();
    private final Map<Object,DD> edgePropertiesExternal = Collections.unmodifiableMap(edgeProperties);
    private GraphDD graph;
    private boolean closed;

    public GraphDDProperties(GraphDD graph) {
        assert graph != null;
        this.graph = graph;
    }
    
    public final Set<Object> getGraphProperties() {
        return graphPropertiesExternal.keySet();
    }
    
    public final Value getGraphProperty(Object property) {
        assert property != null;
        return graphProperties.get(property);
    }


    public final void registerGraphProperty(Object propertyName, Type type) {
        assert propertyName != null;
        assert type != null;
        assert !graphProperties.containsKey(propertyName) : propertyName;
        graphProperties.put(propertyName, type.newValue());
    }

    public final void setGraphProperty(Object property, Value value)
            throws EPMCException {
        assert property != null;
        assert value != null;
        assert graphProperties.containsKey(property);        
        graphProperties.get(property).set(value);
    }

    
    public final void registerNodeProperty(Object propertyName, DD property) {
        assert propertyName != null;
        assert property != null;
        if (nodeProperties.containsKey(propertyName)) {
            return;
        }
        nodeProperties.put(propertyName, property.clone());
    }
    
    public final DD getNodeProperty(Object property) {
        assert property != null;
        return nodeProperties.get(property);
    }

    public final Set<Object> getNodeProperties() {
        return nodePropertiesExternal.keySet();
    }
    
    public final void registerEdgeProperty(Object propertyName,
            DD property) {
        assert propertyName != null;
        assert property != null;
        if (edgeProperties.containsKey(propertyName)) {
            return;
        }
        edgeProperties.put(propertyName, property.clone());
    }
    

    public final DD getEdgeProperty(Object property) {
        assert property != null;
        return edgeProperties.get(property);
    }

    public final Set<Object> getEdgeProperties() {
        return edgePropertiesExternal.keySet();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (DD nodeProperty : nodeProperties.values()) {
            nodeProperty.dispose();
        }
        for (DD edgeProperty : edgeProperties.values()) {
            edgeProperty.dispose();
        }
    }
    
    public GraphDD getGraph() {
        return graph;
    }
    
    public ContextDD getContextDD() throws EPMCException {
        return graph.getContextDD();
    }

    public void setGraphProperty(Object property, Object value) {
        ValueObject.asObject(getGraphProperty(property)).set(value);
    }

    public void removeGraphProperty(Object property) {
        graphProperties.remove(property);
    }

    public void removeNodeProperty(Object property) {
        nodeProperties.get(property).dispose();
        nodeProperties.remove(property);
    }

    public void removeEdgeProperty(Object property) {
        edgeProperties.get(property).dispose();
        edgeProperties.remove(property);
    }

    public void setGraphPropertyObject(Object property, Object value) {
        ValueObject.asObject(graphProperties.get(property)).set(value);
    }    
}
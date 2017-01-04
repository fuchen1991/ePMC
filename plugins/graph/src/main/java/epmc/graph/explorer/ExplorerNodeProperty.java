package epmc.graph.explorer;

import epmc.error.EPMCException;
import epmc.value.ContextValue;
import epmc.value.Type;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueBoolean;
import epmc.value.ValueEnum;
import epmc.value.ValueInteger;
import epmc.value.ValueObject;

/**
 * Node properties of an explorer.
 * This class allows to associate properties to the nodes of an
 * {@link Explorer}.
 * 
 * @author Ernst Moritz Hahn
 */
public interface ExplorerNodeProperty {
    /* methods to be implemented by implementing classes */
    
    /**
     * Get the explorer to which the node property belongs.
     * 
     * @return explorer to which the node property belongs
     */
    Explorer getExplorer();

    /**
     * Get value for node queried last.
     * The value obtained is the value for the node from the latest call of
     * {@link Explorer#queryNode(ExplorerNode)} of the explorer obtained by
     * {@link #getExplorer()}. The function must not be called before the first
     * call of {@link Explorer#queryNode(ExplorerNode)} of that explorer.
     * Note that for efficiency the value this function returns may be
     * the same value (with different content) for each call of the function.
     * Thus, the value returned should not be stored by reference, but rather
     * stored e.g. in an array value or copied using {@link UtilValue#clone(Value)}.
     * 
     * @return value for node queried last
     * @throws EPMCException thrown in case of problems obtaining the value
     */
    Value get() throws EPMCException;
    
    /**
     * Obtain type of the values returned by {@link #get()}.
     * 
     * @return type of the values returned by {@link #get()}
     */
    Type getType();
    
    
    /* default methods */
    
    /**
     * Return value of this node as integer.
     * In addition to the requirements of {@link #get()}, the node property must
     * indeed store integer values. If this is not the case, an
     * {@link AssertionError} may be thrown if assertions are enabled.
     * 
     * @return value of this node as integer
     * @throws EPMCException thrown in case of problems obtaining the value
     */
    default int getInt() throws EPMCException {
        Value value = get();
        assert ValueInteger.isInteger(value);
        return ValueInteger.asInteger(value).getInt();
    }

    /**
     * Return value of this node as boolean.
     * In addition to the requirements of {@link #get()}, the node property must
     * indeed store boolean values. If this is not the case, an
     * {@link AssertionError} may be thrown if assertions are enabled.
     * 
     * @return value of this node as boolean
     * @throws EPMCException thrown in case of problems obtaining the value
     */
    default boolean getBoolean() throws EPMCException {
        Value value = get();
        assert ValueBoolean.isBoolean(value);
        return ValueBoolean.asBoolean(value).getBoolean();
    }
    
    /**
     * Return value of this node as object.
     * In addition to the requirements of {@link #get()}, the node property must
     * indeed store object values. If this is not the case, an
     * {@link AssertionError} may be thrown if assertions are enabled.
     * 
     * @return value of this node as object
     * @throws EPMCException thrown in case of problems obtaining the value
     */
    default <T> T getObject() throws EPMCException {
        ValueObject value = ValueObject.asObject(get());
        return value.getObject();
    }
    
    /**
     * Return value of this node as enum.
     * In addition to the requirements of {@link #get()}, the node property must
     * indeed store enum values. If this is not the case, an
     * {@link AssertionError} may be thrown if assertions are enabled.
     * 
     * @return value of this node as enum
     * @throws EPMCException thrown in case of problems obtaining the value
     */
    default <T extends Enum<?>> T getEnum() throws EPMCException {
        Value value = get();
        assert ValueEnum.isEnum(value);
        return ValueEnum.asEnum(value).getEnum();
    }
    
    /**
     * Get value context used by explorer of this node property.
     * 
     * @return value context used by explorer of this node property
     */
    default ContextValue getContextValue() {
        return getExplorer().getContextValue();
    }
}
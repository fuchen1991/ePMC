/****************************************************************************

    ePMC - an extensible probabilistic model checker
    Copyright (C) 2017

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 *****************************************************************************/

package epmc.graph;

import java.io.Closeable;
import java.util.LinkedHashSet;
import java.util.Set;

import epmc.dd.DD;
import epmc.graph.StateMap;
import epmc.graph.StateSet;
import epmc.graph.dd.StateSetDD;
import epmc.value.Operator;
import epmc.value.Type;
import epmc.value.TypeArray;
import epmc.value.TypeBoolean;
import epmc.value.TypeInterval;
import epmc.value.TypeReal;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueArray;
import epmc.value.ValueBoolean;
import epmc.value.operator.OperatorAnd;
import epmc.value.operator.OperatorId;
import epmc.value.operator.OperatorMax;
import epmc.value.operator.OperatorMin;

public final class StateMapDD implements StateMap, Closeable, Cloneable {
    private final StateSetDD states;
    private final DD valuesDD;
    private final Type type;
    private int refs = 1;

    // note: consumes arguments states, valuesExplicit, and valuesDD
    public StateMapDD(StateSetDD states, DD valuesDD) {
        this.valuesDD = valuesDD;
        this.states = states;
        this.type = valuesDD.getType();
    }

    @Override
    public StateMapDD clone() {
        refs++;
        return this;
    }

    @Override
    public void close() {
        if (closed()) {
            return;
        }
        refs--;
        if (refs > 0) {
            return;
        }
        states.close();
        if (valuesDD != null) {
            valuesDD.dispose();
        }
    }

    public DD getValuesDD() {
        assert !closed();
        assert valuesDD != null;
        return valuesDD;
    }

    @Override
    public int size() {
        assert !closed();
        return states.size();
    }

    @Override
    public Type getType() {
        assert !closed();
        return type;
    }

    @Override
    public StateSet getStateSet() {
        return states;
    }

    @Override
    public StateMapDD restrict(StateSet to) {
        assert !closed();
        assert to != null;
        assert to.isSubsetOf(states);
        if (states.equals(to)) {
            return clone();
        }
        assert to instanceof StateSetDD;
        StateMapDD result = new StateMapDD((StateSetDD) to.clone(), valuesDD.clone());
        return result;
    }

    @Override
    public String toString() {
        if (closed()) {
            return "(closed StateMap)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append(states.getStatesDD());
        builder.append(valuesDD);
        builder.append("}");
        return builder.toString();
    }

    @Override
    public StateMap apply(Operator operator, StateMap operand)
    {
        assert !closed();
        assert operator != null;
        assert operand != null;
        StateMap result = null;
        StateMapDD operandDD = (StateMapDD) operand;
        DD values = valuesDD.apply(operandDD.getValuesDD(), operator);
        result = new StateMapDD(states.clone(), values);
        return result;
    }

    private Value toArray() {
        Set<Value> values = new LinkedHashSet<>();
        valuesDD.collectValues(values, states.getStatesDD());
        TypeArray typeArray = type.getTypeArray();
        ValueArray result = UtilValue.newArray(typeArray, values.size());
        int i = 0;
        for (Value v : values) {
            result.set(v, i);
            i++;
        }
        return result;
    }

    @Override
    public boolean isConstant() {
        assert !closed();
        if (size() == 0) {
            return true;
        }
        return valuesDD.isLeaf();
    }

    private boolean closed() {
        return refs == 0;
    }

    @Override
    public Value applyOver(Operator operator, StateSet over) {
        assert !closed();
        assert operator != null;
        assert over != null;
        assert over instanceof StateSetDD;
        StateSetDD overDD = (StateSetDD) over;
        return valuesDD.applyOverSat(operator, overDD.getStatesDD());
    }

    @Override
    public void getRange(Value range, StateSet of) {
        Value min = applyOver(OperatorMin.MIN, of);
        Value max = applyOver(OperatorMax.MAX, of);
        range.set(TypeInterval.get().newValue(min, max));
    }

    private boolean isAllTrue(StateSet of) {
        Value result = applyOver(OperatorAnd.AND, of);
        return ValueBoolean.asBoolean(result).getBoolean();
    }    

    @Override
    public void getSomeValue(Value to, StateSet of) {
        Value result = applyOver(OperatorId.ID, of);
        to.set(result);
    }

    @Override
    public Value subsumeResult(StateSet initialStates) {
        boolean takeFirstInitState = initialStates.size() == 1;
        Value entry = getType().newValue();
        if (takeFirstInitState) {
            getSomeValue(entry, initialStates);
            return entry;
        } else {
            Type info = getType();
            if (TypeBoolean.isBoolean(info)) {
                boolean allTrue = true;
                allTrue = isAllTrue(initialStates);
                return allTrue
                        ? TypeBoolean.get().getTrue()
                                : TypeBoolean.get().getFalse();
            } else if (hasMinAndMaxElements(initialStates)) {
                Value range = TypeInterval.get().newValue();
                getRange(range, initialStates);
                return range;
            } else {
                StateMapDD initValues = restrict(initialStates);
                return initValues.toArray();
            }
        }
    }

    private boolean hasMinAndMaxElements(StateSet of) {
        if (TypeReal.isReal(getType())) {
            return true;
        }
        getRange(TypeInterval.get().newValue(),
                getStateSet());
        return true;
    }
}

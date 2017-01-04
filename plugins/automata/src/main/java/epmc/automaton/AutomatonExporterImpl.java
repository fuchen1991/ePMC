package epmc.automaton;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.stack.TIntStack;
import gnu.trove.stack.array.TIntArrayStack;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import epmc.dd.ContextDD;
import epmc.dd.DD;
import epmc.dd.VariableDD;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.standard.ExpressionLiteral;
import epmc.expression.standard.ExpressionOperator;
import epmc.expression.standard.UtilExpressionStandard;
import epmc.expression.standard.evaluatordd.ExpressionToDD;
import epmc.util.BitSet;
import epmc.util.UtilBitSet;
import epmc.value.ContextValue;
import epmc.value.OperatorEq;
import epmc.value.TypeBoolean;
import epmc.value.TypeEnumerable;
import epmc.value.Value;
import epmc.value.ValueEnumerable;

public final class AutomatonExporterImpl implements AutomatonExporter {
    private Automaton automaton;
    private OutputStream outStream;
    private Format format = Format.DOT;
    private Value[][] validInputs;
    
    @Override
    public void setAutomaton(Automaton automaton) {
        this.automaton = automaton;
    }

    @Override
    public void setOutput(OutputStream out) {
        this.outStream = out;
    }

    @Override
    public void setFormat(Format format) {
        this.format = format;
    }

    @Override
    public void export() throws EPMCException {
        assert automaton != null;
        assert outStream != null;
        assert format != null;
        this.validInputs = computeValidInputs(automaton);
        PrintStream out = new PrintStream(outStream);
        out.println("digraph {");
        TIntObjectMap<Object> states = exploreStates();
        TIntObjectIterator<Object> iter = states.iterator();
        while (iter.hasNext()) {
            iter.advance();
            int key = iter.key();
            Object value = iter.value();
            out.println("  s" + key + " [label=\"" + value + "\"];");
        }
        out.println();
        iter = states.iterator();
        while (iter.hasNext()) {
            iter.advance();
            int node = iter.key();
            for (Value[] input : validInputs) {
                automaton.queryState(input, node);
                int succ = automaton.getSuccessorState();
                Object label = automaton.numberToLabel(automaton.getSuccessorLabel());
                out.print("  s" + node + " -> s" + succ + " [label=\"");
                printInput(input, out);
                out.print(" : ");
                out.println(label + "\"];");
            }
        }        
        
        out.println("}");
    }

    private void printInput(Value[] input, PrintStream out) {
        out.print("(");
        for (int i = 0; i < input.length; i++) {
            out.print(input[i]);
            if (i < input.length - 1) {
                out.print(",");
            }
        }
        out.print(")");
    }

    private Value[][] computeValidInputs(Automaton automaton)
            throws EPMCException {
    	assert automaton != null;
        ContextValue contextValue = automaton.getContextValue();
        ContextDD contextDD = ContextDD.get(contextValue);
        Expression[] expressions = automaton.getExpressions();
        Set<Expression> identifiers = new HashSet<>();
        for (Expression expression : expressions) {
            identifiers.addAll(UtilExpressionStandard.collectIdentifiers(expression));
        }
        Map<Expression,VariableDD> variables = new HashMap<>();
        for (Expression identifier : identifiers) {
            variables.put(identifier, contextDD.newVariable(identifier.toString(), TypeBoolean.get(contextValue), 1));
        }
        ExpressionToDD checkE2D = new ExpressionToDD(contextValue, variables);
        
        List<Value[]> values = new ArrayList<>();
        int maxNumValues = 1;
        for (Expression expression : expressions) {
            TypeEnumerable type = TypeBoolean.get(contextValue);
            maxNumValues *= type.getNumValues();
        }
        for (int entryNr = 0; entryNr < maxNumValues; entryNr++) {
            int usedNr = entryNr;
            DD check = contextDD.newConstant(true);
            boolean invalid = false;
            Value[] entry = new Value[expressions.length];
            for (int exprNr = 0; exprNr < expressions.length; exprNr++) {
                Expression expression = expressions[exprNr];
                TypeEnumerable type = TypeBoolean.get(contextValue);
                int numValues = type.getNumValues();
                int valueNr = usedNr % numValues;
                usedNr /= numValues;
                ValueEnumerable value = type.newValue();
                value.setValueNumber(valueNr);
                entry[exprNr] = value;
                Expression literal = new ExpressionLiteral.Builder()
                		.setValue(value)
                		.build();
                DD eq = checkE2D.translate(eq(expression, literal));
                check = check.andWith(eq);
                if (check.isFalse()) {
                    invalid = true;
                    break;
                }
            }
            check.dispose();
            if (!invalid) {
                values.add(entry);
            }
        }
        checkE2D.close();
        Value[][] result = new Value[values.size()][];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

	private TIntObjectMap<Object> exploreStates() throws EPMCException {
        TIntStack todo = new TIntArrayStack();
        assert automaton.getInitState() == 0;
        todo.push(0);
        BitSet exploredNodes = UtilBitSet.newBitSetUnbounded();
        exploredNodes.set(0);
        TIntObjectMap<Object> result = new TIntObjectHashMap<>();
        while (todo.size() > 0) {
            int node = todo.pop();
            result.put(node, automaton.numberToState(node));
            for (Value[] input : validInputs) {
                automaton.queryState(input, node);
                int succ = automaton.getSuccessorState();
                if (!exploredNodes.get(succ)) {
                    exploredNodes.set(succ);
                    todo.push(succ);
                }
            }
        }

        return result;
    }
    
    private Expression eq(Expression a, Expression b) {
    	return new ExpressionOperator.Builder()
        	.setOperator(getContextValue().getOperator(OperatorEq.IDENTIFIER))
        	.setOperands(a, b)
        	.build();
    }

    @Override
    public String toString() {
        return exportToString();
    }
    
    private ContextValue getContextValue() {
    	return this.automaton.getContextValue();
    }
}
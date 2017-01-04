package epmc.automaton;

import static epmc.error.UtilError.ensure;
import static epmc.error.UtilError.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import epmc.automaton.SpotParser;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.evaluatorexplicit.EvaluatorExplicit;
import epmc.expression.standard.ExpressionIdentifier;
import epmc.expression.standard.ExpressionLiteral;
import epmc.expression.standard.ExpressionOperator;
import epmc.expression.standard.ExpressionQuantifier;
import epmc.expression.standard.ExpressionTemporal;
import epmc.expression.standard.TemporalType;
import epmc.expression.standard.UtilExpressionStandard;
import epmc.expression.standard.evaluatorexplicit.UtilEvaluatorExplicit;
import epmc.graph.CommonProperties;
import epmc.graph.explicit.EdgeProperty;
import epmc.graph.explicit.GraphExplicit;
import epmc.options.Options;
import epmc.value.ContextValue;
import epmc.value.OperatorAnd;
import epmc.value.OperatorEq;
import epmc.value.OperatorIff;
import epmc.value.OperatorImplies;
import epmc.value.OperatorIte;
import epmc.value.OperatorNe;
import epmc.value.OperatorNot;
import epmc.value.OperatorOr;
import epmc.value.Type;
import epmc.value.TypeBoolean;
import epmc.value.Value;
import epmc.value.ValueBoolean;
import epmc.value.ValueInteger;

public class BuechiImpl implements Buechi {
	private final static String SPOT_PARAM_FORMULA = "-f";
	private final static String SPOT_PARAM_LOW_OPTIMISATIONS = "--low";
	
    private final static String IDENTIFIER = "buechi-spot";
    private final String ltl2tgba;
    private final GraphExplicit automaton;
    private int numLabels;
    private final int trueState;
    private boolean deterministic;
    private final EvaluatorExplicit[] evaluators;
    private final Options options;
    private final Expression[] expressions;
    private final Type[] expressionTypes;

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
    
    public BuechiImpl(ContextValue contextValue, Expression expression, Expression[] expressions)
            throws EPMCException {
        assert expression != null;
        this.options = contextValue.getOptions();
        // TODO does not work if used there
//        if (options.getBoolean(OptionsAutomaton.AUTOMATA_REPLACE_NE)) {
  //          expression = replaceNeOperator(expression);
    //    }
        this.ltl2tgba = options.getString(OptionsAutomaton.AUTOMATON_SPOT_LTL2TGBA_CMD);
        OptionsAutomaton.Ltl2BaAutomatonBuilder builder = options.getEnum(OptionsAutomaton.AUTOMATON_BUILDER);
        Set<Expression> expressionsSeen = new HashSet<>();
        if (builder == OptionsAutomaton.Ltl2BaAutomatonBuilder.SPOT) {
            automaton = createSpotAutomaton(contextValue, expression, expressionsSeen);
        } else {
            automaton = null;
        }
        if (expressions == null) {
            expressions = new Expression[expressionsSeen.size()];
            int index = 0;
            for (Expression expr : expressionsSeen) {
                expressions[index] = expr;
                index++;
            }
        }
        if (this.numLabels == 0) {
            fixNoLabels();
        }
        trueState = findTrueState();
        this.expressions = expressions.clone();
        expressionTypes = new Type[expressions.length];
        for (int exprNr = 0; exprNr < expressions.length; exprNr++) {
            expressionTypes[exprNr] = TypeBoolean.get(contextValue);
        }

        int totalSize = 0;
        for (int node = 0; node < automaton.getNumNodes(); node++) {
            automaton.queryNode(node);
            for (int succNr = 0; succNr < automaton.getNumSuccessors(); succNr++) {
            	totalSize++;
            }
        }
        this.evaluators = new EvaluatorExplicit[totalSize];
        totalSize = 0;
        EdgeProperty labels = automaton.getEdgeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int node = 0; node < automaton.getNumNodes(); node++) {
            automaton.queryNode(node);
            for (int succNr = 0; succNr < automaton.getNumSuccessors(); succNr++) {
                BuechiTransition trans = labels.getObject(succNr);
                Expression guard = trans.getExpression();
                evaluators[totalSize] = UtilEvaluatorExplicit.newEvaluator(guard,
                		new ExpressionToTypeBoolean(contextValue, expressions), expressions);
                totalSize++;
            }
        }
        totalSize = 0;
        for (int node = 0; node < automaton.getNumNodes(); node++) {
            automaton.queryNode(node);
            for (int succNr = 0; succNr < automaton.getNumSuccessors(); succNr++) {
                BuechiTransition trans = labels.getObject(succNr);
                ((BuechiTransitionImpl) trans).setResult(ValueBoolean.asBoolean(evaluators[totalSize].getResultValue()));
                totalSize++;
            }
        }
    }

    @Override
    public Expression[] getExpressions() {
        return expressions;
    }

    @Override
    public void query(Value[] get) throws EPMCException {
    	for (int i = 0; i < evaluators.length; i++) {
    		evaluators[i].evaluate(get);
    	}
    }
    
    private int findTrueState() throws EPMCException {
        int trueState = -1;
        EdgeProperty labels = automaton.getEdgeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int node = 0; node < automaton.getNumNodes(); node++) {
            automaton.queryNode(node);
            for (int succNr = 0; succNr < automaton.getNumSuccessors(); succNr++) {
                BuechiTransition trans = labels.getObject(succNr);
                Expression expr = trans.getExpression();
                boolean isTrue = isTrue(expr);
                if (isTrue && trans.getLabeling().cardinality() == numLabels) {
                    trueState = node;
                    break;
                }
            }
        }
        return trueState;
    }

    private GraphExplicit createSpotAutomaton(ContextValue contextValue, Expression expression,
            Set<Expression> expressionsSeen) throws EPMCException {
        assert expression != null;
        assert expressionsSeen != null;
        Map<Expression,String> expr2str = new HashMap<>();
        expression = UtilAutomaton.bounded2next(contextValue, expression);
        int[] numAPs = new int[1];
        UtilAutomaton.expr2string(contextValue, expression, expr2str, numAPs);
        expressionsSeen.addAll(expr2str.keySet());
        String spotFn = expr2spot(expression, expr2str);
        assert spotFn != null;
        Map<String,Expression> ap2expr = new LinkedHashMap<>();
        for (Entry<Expression,String> entry : expr2str.entrySet()) {
            ap2expr.put(entry.getValue(), entry.getKey());
        }
        try {
            // TODO it seems that one cannot produce both the automaton and the
            // statistics in a single call. Quite annoying. Check whether this
            // is possible once a new version of SPOT appears.
            final String[] autExecArgs = {ltl2tgba,
            		SPOT_PARAM_FORMULA, spotFn,
                    SPOT_PARAM_LOW_OPTIMISATIONS};
            final Process autProcess = Runtime.getRuntime().exec(autExecArgs);
            final BufferedReader autIn = new BufferedReader
                    (new InputStreamReader(autProcess.getInputStream()));
            GraphExplicit automaton;
            SpotParser spotParser = new SpotParser(autIn);
            automaton = spotParser.parseAutomaton(contextValue, ap2expr);
            try {
                ensure(autProcess.waitFor() == 0, ProblemsAutomaton.LTL2BA_SPOT_PROBLEM_EXIT_CODE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.numLabels = ValueInteger.asInteger(automaton.getGraphProperty(CommonProperties.NUM_LABELS)).getInt();
            final String[] detExecArr = {ltl2tgba, SPOT_PARAM_FORMULA, spotFn, "--stats", "%d",
                    "--low"};
            final Process detProcess = Runtime.getRuntime().exec(detExecArr);
            int detResult = detProcess.getInputStream().read();
            ensure(detResult != -1, ProblemsAutomaton.LTL2BA_SPOT_PROBLEM_IO);
            deterministic = ((char) detResult) == '1';
            return automaton;
        } catch (IOException e) {
            fail(ProblemsAutomaton.LTL2BA_SPOT_PROBLEM_IO, e, ltl2tgba);
            return null;
        }
    }

    private void fixNoLabels() throws EPMCException {
        EdgeProperty labels = automaton.getEdgeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int state = 0; state < automaton.getNumNodes(); state++) {
            automaton.queryNode(state);
            for (int succNr = 0; succNr < automaton.getNumSuccessors(); succNr++) {
                BuechiTransition trans = labels.getObject(succNr);
                trans.getLabeling().set(0);
            }
        }
        numLabels = 1;
    }
    
    @Override
    public boolean isDeterministic() {
        return deterministic;
    }
    
    @Override
    public int getNumLabels() {
        return numLabels;
    }
    
    @Override
    public GraphExplicit getGraph() {
        return automaton;
    }
    
    @Override
    public int getTrueState() {
        return trueState;
    }
    
    private static boolean isTrue(Expression expression) {
        assert expression != null;
        if (!ExpressionLiteral.isLiteral(expression)) {
            return false;
        }
        ExpressionLiteral expressionLiteral = ExpressionLiteral.asLiteral(expression);
        return ValueBoolean.isTrue(getValue(expressionLiteral));
    }
    
    private static Value getValue(Expression expression) {
        assert expression != null;
        assert ExpressionLiteral.isLiteral(expression);
        ExpressionLiteral expressionLiteral = ExpressionLiteral.asLiteral(expression);
        return expressionLiteral.getValue();
    }
    
    public static String expr2spot(Expression expression,
            Map<Expression, String> expr2str) {
        assert expression != null;
        assert expr2str != null;
        for (Entry<Expression, String> entry : expr2str.entrySet()) {
            assert entry.getKey() != null;
            assert entry.getValue() != null;
        }
        String result = expr2str.get(expression);
        if (result != null) {
            return result;
        }
        if (ExpressionLiteral.isLiteral(expression)) {
            // must be true or false
            result = expression.toString();
        } else if (expression instanceof ExpressionOperator) {
            ExpressionOperator op = (ExpressionOperator) expression;
            if (isAnd(op)) {
                String left = expr2spot(op.getOperand1(), expr2str);
                String right = expr2spot(op.getOperand2(), expr2str);
                result = "(" + left + " & " + right + ")";
            } else if (isOr(op)) {
                String left = expr2spot(op.getOperand1(), expr2str);
                String right = expr2spot(op.getOperand2(), expr2str);
                result = "(" + left + " | " + right + ")";
            } else if (isNot(op)) {
                String left = expr2spot(op.getOperand1(), expr2str);
                result =  "(!" + left + ")";
            } else if (isIff(op)) {
                String left = expr2spot(op.getOperand1(), expr2str);
                String right = expr2spot(op.getOperand2(), expr2str);
                result = "(" + left + " <=> " + right + ")";
            } else if (isImplies(op)) {
                String left = expr2spot(op.getOperand1(), expr2str);
                String right = expr2spot(op.getOperand2(), expr2str);
                result = "(" + left + " => " + right + ")";                
            } else if (isIte(op)) {
                String ifStr = expr2spot(op.getOperand1(), expr2str);
                String thenStr = expr2spot(op.getOperand2(), expr2str);
                String elseStr = expr2spot(op.getOperand3(), expr2str);
                result = "(" + ifStr + " & " + thenStr + " | !" + ifStr +
                        " & " + elseStr + ")";
            } else {
                assert false : expression;
            }
        } else if (ExpressionIdentifier.isIdentifier(expression)) {
            assert false;
        } else if (expression instanceof ExpressionTemporal) {
            ExpressionTemporal temp = (ExpressionTemporal) expression;
            if (isUntil(temp)) {
                String left = expr2spot(temp.getOperand1(), expr2str);
                String right = expr2spot(temp.getOperand2(), expr2str);
                if (isTrue(temp.getOperand1())) {
                    result = "(F " + right + ")";
                } else {
                    result = "(" + left + " U " + right + ")";
                }
            } else if (isRelease(temp)) {
                String left = expr2spot(temp.getOperand1(), expr2str);
                String right = expr2spot(temp.getOperand2(), expr2str);
                if (isFalse(temp.getOperand1())) {
                    result = "(G " + right + ")";
                } else {
                    result = "(" + left + " R " + right + ")";
                }
            } else if (isNext(temp)) {
                String left = expr2spot(temp.getOperand1(), expr2str);
                result = "(X " + left + ")";
            } else if (isFinally(temp)) {
                String inner = expr2spot(temp.getOperand1(), expr2str);
                result = "(F " + inner + ")";
            } else if (isGlobally(temp)) {
                String inner = expr2spot(temp.getOperand1(), expr2str);
                result = "(G " + inner + ")";
            } else {
                assert false;
            }
        } else if (expression instanceof ExpressionQuantifier) {
            assert false;
        }
        expr2str.put(expression,  result);
        return result;
    }
    
    private static boolean isAnd(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorAnd.IDENTIFIER);
    }

    private static boolean isNext(Expression expression) {
        if (!(expression instanceof ExpressionTemporal)) {
            return false;
        }
        ExpressionTemporal expressionTemporal = (ExpressionTemporal) expression;
        return expressionTemporal.getTemporalType() == TemporalType.NEXT;
    }
    
    private static boolean isFinally(Expression expression) {
        if (!(expression instanceof ExpressionTemporal)) {
            return false;
        }
        ExpressionTemporal expressionTemporal = (ExpressionTemporal) expression;
        return expressionTemporal.getTemporalType() == TemporalType.FINALLY;
    }

    private static boolean isGlobally(Expression expression) {
        if (!(expression instanceof ExpressionTemporal)) {
            return false;
        }
        ExpressionTemporal expressionTemporal = (ExpressionTemporal) expression;
        return expressionTemporal.getTemporalType() == TemporalType.GLOBALLY;
    }

    private static boolean isRelease(Expression expression) {
        if (!(expression instanceof ExpressionTemporal)) {
            return false;
        }
        ExpressionTemporal expressionTemporal = (ExpressionTemporal) expression;
        return expressionTemporal.getTemporalType() == TemporalType.RELEASE;
    }

    private static boolean isUntil(Expression expression) {
        if (!(expression instanceof ExpressionTemporal)) {
            return false;
        }
        ExpressionTemporal expressionTemporal = (ExpressionTemporal) expression;
        return expressionTemporal.getTemporalType() == TemporalType.UNTIL;
    }

    private static boolean isFalse(Expression expression) {
        assert expression != null;
        if (!ExpressionLiteral.isLiteral(expression)) {
            return false;
        }
        ExpressionLiteral expressionLiteral = ExpressionLiteral.asLiteral(expression);
        return ValueBoolean.isFalse(getValue(expressionLiteral));
    }

    private static boolean isNot(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorNot.IDENTIFIER);
    }
    
    private static boolean isIff(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorIff.IDENTIFIER);
    }
    
    private static boolean isOr(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorOr.IDENTIFIER);
    }
    
    private static boolean isImplies(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorImplies.IDENTIFIER);
    }
    
    private static boolean isIte(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorIte.IDENTIFIER);
    }
    
    public static Expression replaceNeOperator(ContextValue contextValue, Expression expression)
            throws EPMCException {
        assert expression != null;
        List<Expression> newChildren = new ArrayList<>();
        for (Expression child : expression.getChildren()) {
            newChildren.add(replaceNeOperator(contextValue, child));
        }
        if (!isNe(expression)) {
            return expression.replaceChildren(newChildren);
        } else {
            return not(contextValue, new ExpressionOperator.Builder()
                    .setOperator(contextValue.getOperator(OperatorEq.IDENTIFIER))
                    .setOperands(newChildren)
                    .build());
        }
    }

    private static boolean isNe(Expression expression) {
        if (!(expression instanceof ExpressionOperator)) {
            return false;
        }
        ExpressionOperator expressionOperator = (ExpressionOperator) expression;
        return expressionOperator.getOperator()
                .getIdentifier()
                .equals(OperatorNe.IDENTIFIER);
    }

    private static Expression not(ContextValue contextValue, Expression expression) {
        return new ExpressionOperator.Builder()
            .setOperator(contextValue.getOperator(OperatorNot.IDENTIFIER))
            .setOperands(expression)
            .build();
    }
}
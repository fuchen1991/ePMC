package epmc.expression.standard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import epmc.value.OperatorAdd;
import epmc.value.OperatorAddInverse;
import epmc.value.OperatorAnd;
import epmc.value.OperatorDivide;
import epmc.value.OperatorEq;
import epmc.value.OperatorIte;
import epmc.value.OperatorMax;
import epmc.value.OperatorMin;
import epmc.value.OperatorNot;
import epmc.value.OperatorOr;
import epmc.value.TypeInteger;
import epmc.value.UtilValue;
import epmc.expression.Expression;
import epmc.value.ContextValue;
import epmc.value.Operator;

// TODO probably should get rid of most of these methods

public final class UtilExpressionStandard {    
    /* Moritz: op* methods are methods used to construct new expressions from
     * given ones by a given operator. The reason not to use default methods of
     * the Expression class is that
     * - these methods need not and should not be possibly override,
     * - Expression already has too many methods,
     * - having them here allows for more flexibility, as e.g. allowing null
     *   arguments for opAnd might be equivalent to the other parameter.
     */    
    public static Expression opAdd(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorAdd.IDENTIFIER, op1, op2);
    }

    public static Expression opAdd(ContextValue context, Expression op1, int op2) {
        assert op1 != null;
        TypeInteger typeInteger = TypeInteger.get(context);
        
        Expression op2Expr = new ExpressionLiteral.Builder()
        		.setValue(UtilValue.newValue(typeInteger, op2))
        		.build();
        return newOperator(context, OperatorAdd.IDENTIFIER, op1, op2Expr);
    }

    public static Expression opAddInverse(ContextValue context, Expression operand) {
        assert operand != null;
        return newOperator(context, OperatorAddInverse.IDENTIFIER, operand);
    }

    public static Expression opDivide(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorDivide.IDENTIFIER, op1, op2);
    }

    public static Expression opAnd(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorAnd.IDENTIFIER, op1, op2);
    }

    public static Expression opOr(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorOr.IDENTIFIER, op1, op2);
    }

    public static Expression opMin(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorMin.IDENTIFIER, op1, op2);
    }

    public static Expression opMin(ContextValue context, int op1, Expression op2) {
        assert op2 != null;
        TypeInteger typeInteger = TypeInteger.get(context);
        Expression op1Expr = new ExpressionLiteral.Builder()
                .setValue(UtilValue.newValue(typeInteger, op1))
                .build();
        return newOperator(context, OperatorMin.IDENTIFIER, op1Expr, op2);
    }

    public static Expression opMax(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorMax.IDENTIFIER, op1, op2);
    }

    public static Expression opEq(ContextValue context, Expression op1, Expression op2) {
        assert op1 != null;
        assert op2 != null;
        return newOperator(context, OperatorEq.IDENTIFIER, op1, op2);
    }

    public static Expression opIte(ContextValue context, Expression op1, Expression op2, Expression op3) {
        assert op1 != null;
        assert op2 != null;
        assert op3 != null;
        return newOperator(context, OperatorIte.IDENTIFIER, op1, op2, op3);
    }

    public static Expression opIte(ContextValue context, Expression op1, Expression op2, int op3) {
        assert op1 != null;
        assert op2 != null;
        TypeInteger typeInteger = TypeInteger.get(context);
        Expression op3Expr = new ExpressionLiteral.Builder()
        		.setValue(UtilValue.newValue(typeInteger, op3))
        		.build();
        return newOperator(context, OperatorIte.IDENTIFIER, op1, op2, op3Expr);
    }

    public static Expression opNot(ContextValue context, Expression operand) {
        assert operand != null;
        return newOperator(context, OperatorNot.IDENTIFIER, operand);
    }

    public static Expression replace(Expression expression, Map<Expression, Expression> replacement) {
        assert expression != null;
        if (replacement.containsKey(expression)) {
            return replacement.get(expression);
        }
        ArrayList<Expression> newChildren = new ArrayList<>();
        for (Expression child : expression.getChildren()) {
            newChildren.add(replace(child, replacement));
        }
        return expression.replaceChildren(newChildren);
    }

    static Expression newOperator(ContextValue context, String operatorId, Expression... operands) {
        Operator operator = context.getOperator(operatorId);
        return new ExpressionOperator.Builder()
                .setOperator(operator)
                .setOperands(Arrays.asList(operands))
                .build();
    }
    
    public static Set<Expression> collectIdentifiers(Expression expression) {
        assert expression != null;
        if (expression instanceof ExpressionIdentifier) {
            return Collections.singleton(expression);
        }
        Set<Expression> result = new HashSet<>();
        for (Expression child : expression.getChildren()) {
            result.addAll(collectIdentifiers(child));
        }
        return result;
    }
    
    private UtilExpressionStandard() {
    }
}
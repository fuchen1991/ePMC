package epmc.constraintsolver.smtlib;

import static epmc.sexpression.UtilSExpression.newSExpression;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import epmc.constraintsolver.smtlib.options.OptionsSMTLib;
import epmc.constraintsolver.smtlib.options.SMTLibVersion;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.standard.ExpressionIdentifier;
import epmc.expression.standard.ExpressionIdentifierStandard;
import epmc.expression.standard.ExpressionLiteral;
import epmc.expression.standard.ExpressionOperator;
import epmc.modelchecker.UtilModelChecker;
import epmc.sexpression.SExpression;
import epmc.value.OperatorAdd;
import epmc.value.OperatorAddInverse;
import epmc.value.OperatorAnd;
import epmc.value.OperatorDivide;
import epmc.value.OperatorEq;
import epmc.value.OperatorGe;
import epmc.value.OperatorGt;
import epmc.value.OperatorIff;
import epmc.value.OperatorImplies;
import epmc.value.OperatorLe;
import epmc.value.OperatorLog;
import epmc.value.OperatorLt;
import epmc.value.OperatorMax;
import epmc.value.OperatorMin;
import epmc.value.OperatorMultiply;
import epmc.value.OperatorNe;
import epmc.value.OperatorNot;
import epmc.value.OperatorOr;
import epmc.value.OperatorSubtract;
import epmc.value.Type;
import epmc.value.TypeBoolean;
import epmc.value.TypeInteger;
import epmc.value.TypeReal;
import epmc.value.Value;
import epmc.value.ValueAlgebra;

final class InputWriter {
	private final static String SPACE = " ";
	private final static String COLON = ":";
	private final static String LANGLE = "(";
	private final static String RANGLE = ")";
	private final static String GEQ = ">=";
	private final static String LEQ = "<=";
	private final static String ASSERT = "assert";
	private final static String DECLARE_CONST = "declare-const";
	private final static String DECLARE_FUN = "declare-fun";
	private final static String SET_OPTION = "set-option";
	private final static String PRINT_SUCCESS = "print-success";
	private final static String PRODUCE_MODELS = "produce-models";
	private final static String GET_VALUE = "get-value";
	private final static String FALSE = "false";
	private final static String TRUE = "true";
	private final static String SET_LOGIC = "set-logic";
	private final static String CHECK_SAT = "check-sat";
	private final static String TYPE_BOOLEAN = "Boolean";
	private final static String TYPE_INT = "Int";
	private final static String TYPE_REAL = "Real";
	private final static String QF_NRA = "QF_NRA";
	private final static String QF_NIA = "QF_NIA";
	private final static String AUFLIRA = "AUFLIRA";
	private final static Map<String,SMTLibOperator> EPMC_TO_SMTLIB;
	static {
		Map<String,SMTLibOperator> iscasMCToISMTLib = new HashMap<>();
		iscasMCToISMTLib.put(OperatorAnd.IDENTIFIER, newOperator()
				.setIdentifier("and").build());
		iscasMCToISMTLib.put(OperatorOr.IDENTIFIER, newOperator()
				.setIdentifier("or").build());
		/* "nand" not supported */ 
		/* "nor" not supported */ 
		/* "xor" not supported */ 
		iscasMCToISMTLib.put(OperatorIff.IDENTIFIER, newOperator()
				.setIdentifier("nxor").build());
		iscasMCToISMTLib.put(OperatorImplies.IDENTIFIER, newOperator()
				.setIdentifier("impl").build());
		iscasMCToISMTLib.put(OperatorNot.IDENTIFIER, newOperator()
				.setIdentifier("not").build());

		iscasMCToISMTLib.put(OperatorAddInverse.IDENTIFIER, newOperator()
				.setIdentifier("-").build());
		iscasMCToISMTLib.put(OperatorAdd.IDENTIFIER, newOperator()
				.setIdentifier("+").build());
		iscasMCToISMTLib.put(OperatorSubtract.IDENTIFIER, newOperator()
				.setIdentifier("-").build());
		iscasMCToISMTLib.put(OperatorMultiply.IDENTIFIER, newOperator()
				.setIdentifier("*").build());
		iscasMCToISMTLib.put(OperatorDivide.IDENTIFIER, newOperator()
				.setIdentifier("/").build());
		/* "abs" not supported */ 
		iscasMCToISMTLib.put(OperatorMin.IDENTIFIER, newOperator()
				.setIdentifier("min").build());
		iscasMCToISMTLib.put(OperatorMax.IDENTIFIER, newOperator()
				.setIdentifier("max").build());
		/* "exp" not supported */
		/* "exp2" not supported */
		/* "exp10" not supported */
		iscasMCToISMTLib.put(OperatorLog.IDENTIFIER, newOperator()
				.setIdentifier("log").build());
		/* "log2" not supported */
		/* "log10" not supported */
		/* "sin" not supported */
		/* "cos" not supported */
		/* "pow" not supported */
		/* "nrt" not supported */
		
		iscasMCToISMTLib.put(OperatorLt.IDENTIFIER, newOperator()
				.setIdentifier("<").build());
		iscasMCToISMTLib.put(OperatorLe.IDENTIFIER, newOperator()
				.setIdentifier("<=").build());
		iscasMCToISMTLib.put(OperatorEq.IDENTIFIER, newOperator()
				.setIdentifier("=").build());
		iscasMCToISMTLib.put(OperatorNe.IDENTIFIER, newOperator()
				.setIdentifier("!=").build());
		iscasMCToISMTLib.put(OperatorGt.IDENTIFIER, newOperator()
				.setIdentifier(">").build());
		iscasMCToISMTLib.put(OperatorGe.IDENTIFIER, newOperator()
				.setIdentifier(">=").build());

		EPMC_TO_SMTLIB = Collections.unmodifiableMap(iscasMCToISMTLib);
	}
	private static SMTLibOperator.Builder newOperator() {
		return new SMTLibOperator.Builder();
	}
		
	private final OutputStream outStream;
	private final ConstraintSolverSMTLib solver;
	private final SMTLibVersion version;
	
	InputWriter(ConstraintSolverSMTLib solver, OutputStream outStream) {
		assert solver != null;
		assert outStream != null;
		this.solver = solver;
		this.outStream = outStream;
		version = solver.getOptions().get(OptionsSMTLib.SMTLIB_VERSION);
	}

	void write() {
		try (PrintStream out = new PrintStream(outStream);) {
			setOption(out, PRINT_SUCCESS, FALSE);
			setOption(out, PRODUCE_MODELS, TRUE);
			String logic = computeLogic();
			command(out, SET_LOGIC, logic);
			writeVariableDeclarations(out);
			writeConstraints(out);
			command(out, CHECK_SAT);
			for (SMTLibVariable variable : solver.getVariables()) {
				command(out, GET_VALUE, (Object) new String[]{variable.getName()});
			}
		}		
	}

	private void writeVariableDeclarations(PrintStream out) {
		for (SMTLibVariable variable : solver.getVariables()) {
			String typeString = typeToString(variable.getType());
			String name = variable.getName();
			if (version == SMTLibVersion.V20) {
				command(out, DECLARE_FUN, name, new Object[0], typeString);
			} else if (version == SMTLibVersion.V25) {
				command(out, DECLARE_CONST, name, typeString);
			} else {
				assert false;
			}
			Value lower = variable.getLower();
			Expression varExpr = null;
			if (lower != null && !ValueAlgebra.asAlgebra(lower).isNegInf()) {
				try {
					varExpr = UtilModelChecker.parseExpression(variable.getContextValue(), lower  + "");
				} catch (EPMCException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				};
				command(out, ASSERT,
						LANGLE + GEQ + SPACE + name + SPACE + translateExpression(varExpr) + RANGLE);
			}
			Value upper = variable.getUpper();
			if (upper != null && !ValueAlgebra.asAlgebra(upper).isPosInf()) {
				try {
					varExpr = UtilModelChecker.parseExpression(variable.getContextValue(), upper  + "");
				} catch (EPMCException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				};
				command(out, ASSERT,
						LANGLE + LEQ + SPACE + name + SPACE + translateExpression(varExpr) + RANGLE);
			}
		}
	}
	
	private void writeConstraints(PrintStream out) {
		for (Expression expression : solver.getConstraints()) {
			command(out, ASSERT, expression);
		}
	}
	
	private SExpression translateExpression(Expression expression) {
		assert expression != null;
		if (expression instanceof ExpressionIdentifier) {
			ExpressionIdentifierStandard expressionIdentifier = (ExpressionIdentifierStandard) expression;
			return newSExpression(expressionIdentifier.getName());
		} else if (ExpressionLiteral.isLiteral(expression)) {
			return newSExpression(getValue(expression).toString());
		} else if (expression instanceof ExpressionOperator) {
			return translateExpressionOperator((ExpressionOperator) expression);
		} else {
			assert false;
			return null;
		}
	}

	private SExpression translateExpressionOperator(ExpressionOperator expression) {
		SMTLibOperator operator = EPMC_TO_SMTLIB.get(expression.getOperator().getIdentifier());
		assert operator != null : expression.getOperator();
		SExpression[] result = new SExpression[expression.getChildren().size() + 1];
		result[0] = newSExpression(operator.getIdentifer());
		
		int i = 0;
		for (Expression operand : expression.getOperands()) {
			result[i + 1] = translateExpression(operand);
			i++;
		}
			
		return newSExpression(result);
	}
	
	private void setOption(PrintStream out, String option, String value) {
		out.print(LANGLE + SET_OPTION + SPACE + COLON);
		out.print(option);
		out.print(SPACE);
		out.print(value);
		out.println(RANGLE);
	}
	
	private void command(PrintStream out, String command, Object... parameters) {
		assert command != null;
		assert parameters != null;
		out.print(LANGLE);
		out.print(command);
		if (parameters.length > 0) {
			out.print(SPACE);
		}
		for (int i = 0; i < parameters.length; i++) {
			writeObject(out, parameters[i]);
			if (i < parameters.length - 1) {
				out.print(SPACE);
			}
		}
		out.println(RANGLE);
	}
	
	private void writeObject(PrintStream out, Object object) {
		if (object instanceof Object[]) {
			out.print(LANGLE);
			Object[] array = (Object[]) object;
			for (int j = 0; j < array.length; j++) {
				writeObject(out, array[j]);
				if (j < array.length - 1) {
					out.print(SPACE);
				}
			}
			out.print(RANGLE);
		} else if (object instanceof Expression) {
			out.print(translateExpression((Expression) object));
		} else {
			out.print(object);
		}
	}

	private String computeLogic() {
		boolean hasReal = false;
		boolean hasInt = false;
		for (SMTLibVariable variable : solver.getVariables()) {
			hasReal = hasReal | !TypeInteger.isInteger(variable.getType()) && TypeReal.isReal(variable.getType());
			hasInt = hasInt | TypeInteger.isInteger(variable.getType());
		}
		if (hasReal && !hasInt) {
			return QF_NRA;
		} else if (!hasReal && hasInt) {
			return QF_NIA;			
		} else {
			return AUFLIRA;
		}
	}
	
	private String typeToString(Type type) {
		assert type != null;
		if (TypeBoolean.isBoolean(type)) {
			return TYPE_BOOLEAN;
		} else if (TypeInteger.isInteger(type)) {
			return TYPE_INT;
		} else if (TypeReal.isReal(type)) {
			return TYPE_REAL;
		} else {
			assert false : type;
			return null;
		}
	}
	
    private static Value getValue(Expression expression) {
        assert expression != null;
        assert ExpressionLiteral.isLiteral(expression);
        ExpressionLiteral expressionLiteral = ExpressionLiteral.asLiteral(expression);
        return expressionLiteral.getValue();
    }
}
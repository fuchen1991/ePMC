package epmc.jani.model.expression;

import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.standard.ExpressionOperator;
import epmc.jani.model.JANIIdentifier;
import epmc.jani.model.JANINode;
import epmc.jani.model.JANIOperator;
import epmc.jani.model.JANIOperators;
import epmc.jani.model.ModelJANI;
import epmc.jani.model.UtilModelParser;
import epmc.util.UtilJSON;
import epmc.value.Operator;

/**
 * JANI expression for unary operators.
 * 
 * @author Ernst Moritz Hahn
 */
public final class JANIExpressionOperatorUnary implements JANIExpression {
	/** Identifier of this JANI expression type. */
	public final static String IDENTIFIER = "operator-unary";
	private final static String OP = "op";
	private final static String EXP = "exp";
	
	private Map<String, ? extends JANIIdentifier> validIdentifiers;
	private ModelJANI model;
	private boolean forProperty;
	
	private boolean initialized;
	private JANIOperator operator;
	private JANIExpression operand;

	private void resetFields() {
		initialized = false;
		operator = null;
		operand = null;
	}
	
	public JANIExpressionOperatorUnary() {
		resetFields();
	}

	@Override
	public JANINode parse(JsonValue value) throws EPMCException {
		return parseAsJANIExpression(value);
	}
	
	@Override 
	public JANIExpression parseAsJANIExpression(JsonValue value) throws EPMCException {
		assert model != null;
		assert validIdentifiers != null;
		assert value != null;
		resetFields();
		if (!(value instanceof JsonObject)) {
			return null;
		}
		JsonObject object = (JsonObject) value;
		if (!object.containsKey(OP)) {
			return null;
		}
		if (!(object.get(OP) instanceof JsonString)) {
			return null;
		}
		JANIOperators operators = model.getJANIOperators();
		if (!operators.containsOperatorByJANI(object.getString(OP))) {
			return null;
		}
		operator = UtilJSON.toOneOf(object, OP, operators::getOperatorByJANI);
		if (operator == null) {
			return null;
		}
		if (operator.getArity() != 1) {
			return null;
		}
		if (!object.containsKey(EXP)) {
			return null;
		}
		ExpressionParser parser = new ExpressionParser(model, validIdentifiers, forProperty);
		operand = parser.parseAsJANIExpression(object.get(EXP));
		if (operand == null) {
			return null;
		}
		initialized = true;
		return this;
	}

	@Override
	public JsonValue generate() throws EPMCException {
		assert initialized;
		assert model != null;
		assert validIdentifiers != null;
		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add(OP, operator.getJANI());
		builder.add(EXP, operand.generate());
		return builder.build();
	}

	@Override
	public JANIExpression matchExpression(ModelJANI model, Expression expression) throws EPMCException {
		assert expression != null;
		assert model != null;
		assert validIdentifiers != null;
		resetFields();
		if (!(expression instanceof ExpressionOperator)) {
			return null;
		}
		ExpressionOperator expressionOperator = (ExpressionOperator) expression;
		operator = getJANIOperators().getOperator(expressionOperator.getOperator());
		if (operator.getArity() != 1) {
			return null;
		}
		ExpressionParser parser = new ExpressionParser(model, validIdentifiers, forProperty);
		operand = parser.matchExpression(model, expressionOperator.getOperand1());
		if (operand == null) {
			return null;
		}
		initialized = true;
		return this;
	}

	@Override
	public Expression getExpression() throws EPMCException {
		assert initialized;
		assert model != null;
		assert validIdentifiers != null;
		Operator operator = this.operator.getOperator(model.getContextValue());
		return new ExpressionOperator.Builder()
				.setOperator(operator)
				.setOperands(operand.getExpression())
				.build();
	}

	private JANIOperators getJANIOperators() {
		assert model != null;
		return model.getJANIOperators();
	}

	@Override
	public void setIdentifiers(Map<String, ? extends JANIIdentifier> identifiers) {
		this.validIdentifiers = identifiers;
	}	

	@Override
	public void setForProperty(boolean forProperty) {
		this.forProperty = forProperty;
	}

	@Override
	public void setModel(ModelJANI model) {
		this.model = model;
	}

	@Override
	public ModelJANI getModel() {
		return model;
	}
	
	@Override
	public String toString() {
		return UtilModelParser.toString(this);
	}
}
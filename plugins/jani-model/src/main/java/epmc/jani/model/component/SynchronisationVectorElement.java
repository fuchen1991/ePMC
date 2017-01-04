package epmc.jani.model.component;

import java.util.Collections;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import epmc.error.EPMCException;
import epmc.jani.model.Action;
import epmc.jani.model.Automaton;
import epmc.jani.model.JANINode;
import epmc.jani.model.ModelJANI;
import epmc.util.UtilJSON;

public final class SynchronisationVectorElement implements JANINode {
	private final static String AUTOMATON = "automaton";
	private final static String INPUT_ENABLE = "input-enable";
	private final static String COMMENT = "comment";
	
	private ModelJANI model;
	private Automaton automaton;
	private String comment;
	private Set<Action> inputEnable;
	
	@Override
	public void setModel(ModelJANI model) {
		this.model = model;
	}
	
	@Override
	public ModelJANI getModel() {
		return model;
	}
	
	@Override
	public JANINode parse(JsonValue value) throws EPMCException {
		assert value != null;
		JsonObject object = UtilJSON.toObject(value);
		automaton = UtilJSON.toOneOf(object, AUTOMATON, model.getAutomata());
		comment = UtilJSON.getStringOrNull(object, COMMENT);
		if (object.containsKey(INPUT_ENABLE)) {
			inputEnable = UtilJSON.toSubsetOf(object, INPUT_ENABLE, model.getActions());
		}
		return this;
	}

	@Override
	public JsonValue generate() throws EPMCException {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add(AUTOMATON, automaton.getName());
		if (inputEnable != null) {
			JsonArrayBuilder inputEnableBuilder = Json.createArrayBuilder();
			for (Action action : inputEnable) {
				inputEnableBuilder.add(action.getName());
			}
			builder.add(INPUT_ENABLE, inputEnableBuilder);
		}
		UtilJSON.addOptional(builder, COMMENT, comment);
		return builder.build();
	}

	public void setAutomaton(Automaton automaton) {
		this.automaton = automaton;
	}
	
	public Automaton getAutomaton() {
		return automaton;
	}
	
	public void setComment(String comment) {
		this.comment = comment;
	}
	
	public String getComment() {
		return comment;
	}
	
	public void setInputEnable(Set<Action> inputEnable) {
		this.inputEnable = inputEnable;
	}
	
	public Set<Action> getInputEnable() {
		return inputEnable;
	}
	
	public Set<Action> getInputEnableOrEmpty() {
		if (inputEnable == null) {
			return Collections.emptySet();
		} else {
			return inputEnable;
		}
	}
}
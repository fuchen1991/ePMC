package epmc.prism.plugin;

import epmc.error.EPMCException;
import epmc.plugin.BeforeModelCreation;
import epmc.prism.value.OperatorPRISMPow;
import epmc.value.ContextValue;

public final class BeforeModelCreationPRISM implements BeforeModelCreation {
	public final static String IDENTIFIER = "before-model-creation-prism";

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public void process(ContextValue contextValue) throws EPMCException {
		assert contextValue != null;
		contextValue.addOrSetOperator(OperatorPRISMPow.class);
	}

}
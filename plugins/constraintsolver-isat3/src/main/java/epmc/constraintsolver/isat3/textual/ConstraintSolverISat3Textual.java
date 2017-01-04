package epmc.constraintsolver.isat3.textual;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import epmc.constraintsolver.ConstraintSolver;
import epmc.constraintsolver.ConstraintSolverResult;
import epmc.constraintsolver.Direction;
import epmc.constraintsolver.Feature;
import epmc.constraintsolver.sat3.options.OptionsISat3;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.value.ContextValue;
import epmc.value.Type;
import epmc.value.TypeBounded;
import epmc.value.Value;

public class ConstraintSolverISat3Textual implements ConstraintSolver {
	public final static String IDENTIFIER = "isat3-textual";
	private final static String TEMPFILE_PREFIX = "isat3";
	private final static String TEMPFILE_SUFFIX = ".hys";

	private boolean closed;
	private final Set<Feature> features = new LinkedHashSet<>();
	private final List<Expression> constraints = new ArrayList<>();
	private ContextValue contextValue;
	private final List<ISatVariable> variables = new ArrayList<>();
	private final Map<String,Integer> variableToNumber = new LinkedHashMap<>();
	private Value[] resultVariableValues;

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public void requireFeature(Feature feature) {
		assert !closed;
		assert feature != null;
		features.add(feature);
	}

	@Override
	public void setContextValue(ContextValue contextValue) {
		assert !closed;
		assert contextValue != null;
		this.contextValue = contextValue;
	}

	@Override
	public ContextValue getContextValue() {
		assert !closed;
		return contextValue;
	}

	@Override
	public boolean canHandle() {
		assert !closed;
		for (Feature feature : features) {
			if (feature != Feature.SMT) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void build() throws EPMCException {
		assert !closed;
	}

	@Override
	public int addVariable(String name, Type type) {
		assert !closed;
		return addVariable(name, type, TypeBounded.getLower(type), TypeBounded.getUpper(type));
	}

	/*
	@Override
	public int addVariable(ExpressionIdentifierStandard identifier) throws EPMCException {
		assert !closed;
		String name = identifier.getName();
		Type type = identifier.getType();
		return addVariable(name, type,
				TypeBounded.getLower(type), TypeBounded.getUpper(type));
	}
	*/
	
	@Override
	public int addVariable(String name, Type type, Value lower, Value upper) {
		assert !closed;
		assert name != null;
		assert type != null;
		this.variableToNumber.put(name, variables.size());
		variables.add(new ISatVariable.Builder()
				.setName(name)
				.setType(type)
				.setLower(lower)
				.setUpper(upper)
				.build());
		return variables.size() - 1;
	}

	@Override
	public void addConstraint(Expression expression) throws EPMCException {
		assert !closed;
		constraints.add(expression);
	}

	@Override
	public void setObjective(Expression objective) {
		assert !closed;
		assert false;
	}

	@Override
	public void setDirection(Direction direction) {
		assert !closed;
		assert direction != null;
		assert direction == Direction.FEASIBILITY;
	}

	@Override
	public ConstraintSolverResult solve() throws EPMCException {
		assert !closed;
		Path path = null;
		try {
			path = Files.createTempFile(TEMPFILE_PREFIX, TEMPFILE_SUFFIX);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		File file = path.toFile();
		if (!getOptions().getBoolean(OptionsISat3.ISAT3_KEEP_TEMPORARY_FILES)) {
			file.deleteOnExit();
		}
		try (OutputStream outStream = new FileOutputStream(file);) {
			HysWriter writer = new HysWriter(this, outStream);
			writer.write();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
		ISatResult result = callSolver(file.getAbsolutePath());
		this.resultVariableValues = result.values;
		return result.type;
	}

	private ISatResult callSolver(String file) throws EPMCException {
		List<String> callOptions = getOptions().get(OptionsISat3.ISAT3_COMMAND_LINE);
		List<String> execArgs = new ArrayList<>();
		for (String param : callOptions) {
			execArgs.add(MessageFormat.format(param, file));
		}
		
        Process isat3Process = null;
		try {
			isat3Process = Runtime.getRuntime().exec(execArgs.toArray(new String[0]));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
        final BufferedReader isat3In = new BufferedReader
                (new InputStreamReader(isat3Process.getInputStream()));
        ISatOutputReader reader = new ISatOutputReader(this);
        ISatResult result = reader.parseOutput(isat3In);
        try {
            assert isat3Process.waitFor() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
	}	

	@Override
	public Value[] getResultVariablesValues() {
		assert !closed;
		return resultVariableValues;
	}

	@Override
	public Value getResultObjectiveValue() {
		assert !closed;
		return null;
	}

	@Override
	public void close() {
		this.closed = true;
	}

	List<ISatVariable> getVariables() {
		return variables;
	}
	
	List<Expression> getConstraints() {
		return constraints;
	}
	
	Map<String, Integer> getVariableToNumber() {
		return variableToNumber;
	}
	
	@Override
	public String getVariableName(int number) {
		return variables.get(number).getName();
	}
}
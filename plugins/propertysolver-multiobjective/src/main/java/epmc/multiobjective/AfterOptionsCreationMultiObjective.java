package epmc.multiobjective;

import java.util.Map;

import epmc.error.EPMCException;
import epmc.graphsolver.OptionsGraphsolver;
import epmc.modelchecker.options.OptionsModelChecker;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveScheduledJava;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveScheduledJavaDouble;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveScheduledNative;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveWeightedJava;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveWeightedJavaDouble;
import epmc.multiobjective.graphsolver.GraphSolverIterativeMultiObjectiveWeightedNative;
import epmc.options.Options;
import epmc.plugin.AfterOptionsCreation;

public final class AfterOptionsCreationMultiObjective implements AfterOptionsCreation {
	private final static String IDENTIFIER = "after-options-multi-objective";

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public void process(Options options) throws EPMCException {
		assert options != null;
		Map<String,Class<?>> solvers = options.get(OptionsModelChecker.PROPERTY_SOLVER_CLASS);
		solvers.put(PropertySolverExplicitMultiObjective.IDENTIFIER, PropertySolverExplicitMultiObjective.class);
        Map<String, Class<?>> graphSolverMap = options.get(OptionsGraphsolver.GRAPHSOLVER_SOLVER_CLASS);
        assert graphSolverMap != null;
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveWeightedJava.IDENTIFIER, GraphSolverIterativeMultiObjectiveWeightedJava.class);
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveWeightedJavaDouble.IDENTIFIER, GraphSolverIterativeMultiObjectiveWeightedJavaDouble.class);
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveWeightedNative.IDENTIFIER, GraphSolverIterativeMultiObjectiveWeightedNative.class);
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveScheduledJava.IDENTIFIER, GraphSolverIterativeMultiObjectiveScheduledJava.class);
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveScheduledJavaDouble.IDENTIFIER, GraphSolverIterativeMultiObjectiveScheduledJavaDouble.class);
        graphSolverMap.put(GraphSolverIterativeMultiObjectiveScheduledNative.IDENTIFIER, GraphSolverIterativeMultiObjectiveScheduledNative.class);
	}
}
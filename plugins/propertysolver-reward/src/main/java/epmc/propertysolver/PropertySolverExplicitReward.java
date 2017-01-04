package epmc.propertysolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import epmc.algorithms.UtilAlgorithms;
import epmc.algorithms.explicit.ComponentsExplicit;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.evaluatorexplicit.EvaluatorExplicit;
import epmc.expression.standard.CmpType;
import epmc.expression.standard.DirType;
import epmc.expression.standard.ExpressionQuantifier;
import epmc.expression.standard.ExpressionReward;
import epmc.expression.standard.RewardSpecification;
import epmc.expression.standard.RewardType;
import epmc.expression.standard.evaluatorexplicit.UtilEvaluatorExplicit;
import epmc.graph.CommonProperties;
import epmc.graph.Player;
import epmc.graph.Semantics;
import epmc.graph.SemanticsMDP;
import epmc.graph.SemanticsMarkovChain;
import epmc.graph.StateMap;
import epmc.graph.StateSet;
import epmc.graph.UtilGraph;
import epmc.graph.explicit.EdgeProperty;
import epmc.graph.explicit.GraphExplicit;
import epmc.graph.explicit.NodeProperty;
import epmc.graph.explicit.StateMapExplicit;
import epmc.graph.explicit.StateSetExplicit;
import epmc.graphsolver.GraphSolverConfigurationExplicit;
import epmc.graphsolver.UtilGraphSolver;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitBounded;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitBoundedCumulative;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitBoundedCumulativeDiscounted;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitUnboundedCumulative;
import epmc.modelchecker.EngineExplicit;
import epmc.modelchecker.ModelChecker;
import epmc.modelchecker.PropertySolver;
import epmc.util.BitSet;
import epmc.util.UtilBitSet;
import epmc.value.ContextValue;
import epmc.value.Operator;
import epmc.value.Type;
import epmc.value.TypeArray;
import epmc.value.TypeWeight;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueAlgebra;
import epmc.value.ValueArray;
import epmc.value.ValueArrayAlgebra;
import epmc.value.ValueReal;

// TODO check whether this works for JANI MDPs - probably not

public final class PropertySolverExplicitReward implements PropertySolver {
    public final static String IDENTIFIER = "reward-explicit";
    private ModelChecker modelChecker;
    private GraphExplicit graph;
    private ContextValue contextValue;
	private Expression property;
	private StateSet forStates;

    @Override
    public void setModelChecker(ModelChecker modelChecker) {
        assert modelChecker != null;
        this.modelChecker = modelChecker;
        if (modelChecker.getEngine() instanceof EngineExplicit) {
        	this.graph = modelChecker.getLowLevel();
        }
        this.contextValue = modelChecker.getModel().getContextValue();
    }


	@Override
	public void setProperty(Expression property) {
		this.property = property;
	}

	@Override
	public void setForStates(StateSet forStates) {
		this.forStates = forStates;
	}

	@Override
	public Set<Object> getRequiredGraphProperties() throws EPMCException {
		Set<Object> required = new LinkedHashSet<>();
		required.add(CommonProperties.SEMANTICS);
		return Collections.unmodifiableSet(required);
	}


	@Override
	public Set<Object> getRequiredNodeProperties() throws EPMCException {
		Set<Object> required = new LinkedHashSet<>();
		required.add(CommonProperties.STATE);
		required.add(CommonProperties.PLAYER);
		ExpressionQuantifier propertyQuantifier = (ExpressionQuantifier) property;
        required.add(((ExpressionReward) (propertyQuantifier.getQuantified())).getReward());
        ExpressionReward quantifiedReward = (ExpressionReward) propertyQuantifier.getQuantified();
        RewardType rewardType = quantifiedReward.getRewardType();
        if (rewardType.isReachability()) {
        	required.addAll(modelChecker.getRequiredNodeProperties(quantifiedReward.getRewardReachSet(), forStates));
        }
		return Collections.unmodifiableSet(required);
	}

	@Override
	public Set<Object> getRequiredEdgeProperties() throws EPMCException {
		Set<Object> required = new LinkedHashSet<>();
		required.add(CommonProperties.WEIGHT);
		ExpressionQuantifier propertyQuantifier = (ExpressionQuantifier) property;
        required.add(((ExpressionReward) (propertyQuantifier.getQuantified())).getReward());
		return Collections.unmodifiableSet(required);
	}

    @Override
    public StateMap solve() throws EPMCException {
		ExpressionQuantifier propertyQuantifier = (ExpressionQuantifier) property;
        ExpressionReward quantifiedProp = (ExpressionReward) propertyQuantifier.getQuantified();
        if (quantifiedProp.getRewardType().isReachability()) {
        	StateSetExplicit allStates = UtilGraph.computeAllStatesExplicit(modelChecker.getLowLevel());
        	StateMapExplicit innerResult = (StateMapExplicit) modelChecker.check(quantifiedProp.getRewardReachSet(), allStates);
            UtilGraph.registerResult(graph, quantifiedProp.getRewardReachSet(), innerResult);
            allStates.close();
        }
        DirType dirType = ExpressionQuantifier.computeQuantifierDirType(propertyQuantifier);
        boolean min = dirType == DirType.MIN;
        StateMap result = doSolve(quantifiedProp, (StateSetExplicit) forStates, min);
        if (propertyQuantifier.getCompareType() != CmpType.IS) {
            StateMap compare = modelChecker.check(propertyQuantifier.getCompare(), forStates);
            Operator op = propertyQuantifier.getCompareType().asExOpType(contextValue);
            result = result.applyWith(op, compare);
        }
        return result;
    }

    public StateMap doSolve(Expression property, StateSetExplicit states, boolean min)
            throws EPMCException {
        RewardSpecification rewardStructure = ((ExpressionReward) property).getReward();
        NodeProperty stateReward = graph.getNodeProperty(rewardStructure);
        EdgeProperty transReward = graph.getEdgeProperty(rewardStructure);
        return solve(property, states, min, stateReward, transReward);
    }

    public StateMapExplicit solve(Expression property, StateSetExplicit states, boolean min,
            NodeProperty stateReward, EdgeProperty transReward)
                    throws EPMCException {
        assert property != null;
        assert states != null;
        assert stateReward != null;
        assert transReward != null;
        ContextValue contextValue = graph.getContextValue();
        List<BitSet> sinks = new ArrayList<>();
        RewardType rewardType = ((ExpressionReward) property).getRewardType();
        BitSet reachSink = computeReachSink(property);
        if (reachSink.length() > 0) {
            sinks.add(reachSink);            
        }
        BitSet reachNotOneSink = computeReachNotOneSink(property, reachSink, min);
        if (reachNotOneSink.length() > 0) {
            sinks.add(reachNotOneSink);
        }
        ExpressionReward propertyReward = (ExpressionReward) property;
        ValueAlgebra time = ValueAlgebra.asAlgebra(evaluateValue(propertyReward.getTime()));
        NodeProperty statesProp = graph.getNodeProperty(CommonProperties.STATE);
        ValueArrayAlgebra values = UtilValue.newArray(TypeWeight.get(contextValue).getTypeArray(), graph.getNumNodes());
        ValueArrayAlgebra cumulRewards = null;
        if (rewardType.isInstantaneous()) {
        	for (int graphNode = 0; graphNode < graph.getNumNodes(); graphNode++) {
                graph.queryNode(graphNode);
                Value reward = stateReward.get();
                values.set(reward, graphNode);
            }
        }
        if (rewardType.isCumulative() || rewardType.isReachability() || rewardType.isDiscounted()) {
            Semantics semantics = graph.getGraphPropertyObject(CommonProperties.SEMANTICS);
            
            if (SemanticsMarkovChain.isMarkovChain(semantics)) {
            	cumulRewards = UtilValue.newArray(TypeWeight.get(contextValue).getTypeArray(), graph.computeNumStates());
            } else if (SemanticsMDP.isMDP(semantics)) {
                int numNondet = graph.getNumNodes() - graph.computeNumStates();
                cumulRewards = UtilValue.newArray(TypeWeight.get(contextValue).getTypeArray(), numNondet);
            } else {
                assert false;
            }
            ValueAlgebra acc = newValueWeight();
            ValueAlgebra acc2 = newValueWeight();
            NodeProperty playerProp = graph.getNodeProperty(CommonProperties.PLAYER);
            int cumulRewIdx = 0;
            if (rewardType.isReachability()) {
                cumulRewIdx = sinks.size();
            }
        	for (int graphNode = 0; graphNode < graph.getNumNodes(); graphNode++) {
                if (reachSink.get(graphNode) || reachNotOneSink.get(graphNode)) {
                    continue;
                }
                graph.queryNode(graphNode);
                int numSuccessors = graph.getNumSuccessors();
                Value nodeRew = stateReward.get();
                Player player = playerProp.getEnum();
                EdgeProperty weight = graph.getEdgeProperty(CommonProperties.WEIGHT);
                if (!SemanticsMDP.isMDP(semantics)) {
                    acc.set(nodeRew);                        
                }
                for (int succNr = 0; succNr < numSuccessors; succNr++) {
                    if (SemanticsMDP.isMDP(semantics)) {
                        acc.set(nodeRew);                        
                    }
                    Value succWeight = weight.get(succNr);
                    Value transRew = transReward.get(succNr);
                    if (player == Player.STOCHASTIC) {
                        acc2.multiply(succWeight, transRew);
                        acc.add(acc, acc2);
                    } else {
                        acc.add(acc, transRew);
                    }
                    if (SemanticsMDP.isMDP(semantics) && player == Player.ONE) {
                        cumulRewards.set(acc, cumulRewIdx);
                        cumulRewIdx++;
                    }
                }
                if (SemanticsMarkovChain.isMarkovChain(semantics)) {
                    cumulRewards.set(acc, graphNode);
                }
            }
        }
        
        GraphSolverConfigurationExplicit configuration = UtilGraphSolver.newGraphSolverConfigurationExplicit(states.getContextValue().getOptions());
        if (rewardType.isInstantaneous()) {
        	GraphSolverObjectiveExplicitBounded objective = new GraphSolverObjectiveExplicitBounded();
        	objective.setGraph(graph);
        	objective.setMin(min);
        	objective.setValues(values);
        	objective.setTime(time);
        	objective.setSinks(sinks);
            configuration.setObjective(objective);
            configuration.solve();
            values = objective.getResult();
        } else if (rewardType.isCumulative() && !time.isPosInf()) {
            GraphSolverObjectiveExplicitBoundedCumulative objective = new GraphSolverObjectiveExplicitBoundedCumulative();
            objective.setGraph(graph);
            objective.setMin(min);
            objective.setTime(time);
            objective.setStateRewards(cumulRewards);
            configuration.setObjective(objective);
            configuration.solve();
            values = objective.getResult();
        } else if (rewardType.isReachability() || (rewardType.isCumulative() && time.isPosInf())) {
            GraphSolverObjectiveExplicitUnboundedCumulative objective = new GraphSolverObjectiveExplicitUnboundedCumulative();
            objective.setStateRewards(cumulRewards);
            objective.setGraph(graph);
            objective.setMin(min);
            objective.setSinks(sinks);
            configuration.setObjective(objective);
            configuration.solve();
            values = objective.getResult();
        } else if (rewardType.isDiscounted()) {
            GraphSolverObjectiveExplicitBoundedCumulativeDiscounted objective = new GraphSolverObjectiveExplicitBoundedCumulativeDiscounted();
            objective.setStateRewards(cumulRewards);
            objective.setGraph(graph);
            objective.setMin(min);
            objective.setDiscount(ValueReal.asReal(evaluateValue(propertyReward.getDiscount())));
            objective.setTime(time);
            configuration.setObjective(objective);
            configuration.solve();
            values = objective.getResult();
        } else if (rewardType.isSteadystate()) {
            assert false;
        }
        if (rewardType.isReachability()) {
        	for (int graphNode = 0; graphNode < graph.getNumNodes(); graphNode++) {
                graph.queryNode(graphNode);
                if (statesProp.getBoolean() && reachNotOneSink.get(graphNode)) {
                    values.set(TypeWeight.get(contextValue).getPosInf(), graphNode);
                }
            }
        }

        StateMapExplicit result = valuesToResult(values, states);
        return result;
    }

    private BitSet computeReachNotOneSink(Expression property, BitSet reachSink, boolean min)
            throws EPMCException {
        assert property != null;
        RewardType rewardType = ((ExpressionReward) property).getRewardType();
        BitSet reachNotOneSink = UtilBitSet.newBitSetUnbounded();
        BitSet graphNodes = UtilBitSet.newBitSetUnbounded();
        graphNodes.set(0, graph.getNumNodes());
        if (rewardType.isReachability()) {
            ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
            BitSet reachOneSink = components.reachPre(graph, reachSink, !min, true);
            reachNotOneSink = UtilBitSet.newBitSetUnbounded();
            reachNotOneSink.or(graphNodes);
            reachNotOneSink.andNot(reachOneSink);
        }
        return reachNotOneSink;
    }

    private BitSet computeReachSink(Expression property) throws EPMCException {
        assert property != null;
        ExpressionReward propertyReward = (ExpressionReward) property;
        RewardType rewardType = propertyReward.getRewardType();
        BitSet reachSink = UtilBitSet.newBitSetUnbounded();
        if (rewardType.isReachability()) {
        	NodeProperty reachSet = graph.getNodeProperty(propertyReward.getRewardReachSet());
        	for (int graphNode = 0; graphNode < graph.getNumNodes(); graphNode++) {
                graph.queryNode(graphNode);
                if (reachSet.getBoolean()) {
                    reachSink.set(graphNode);
                }
            }
        }
        return reachSink;
    }

    private StateMapExplicit valuesToResult(ValueArray values, StateSetExplicit states) {
        assert values != null;
        assert states != null;
        Type typeWeight = TypeWeight.get(contextValue);
        ValueArray resultValues = newValueArrayWeight(states.size());
        Value entry = typeWeight.newValue();
        for (int i = 0; i < states.size(); i++) {
            values.get(entry, i);
            resultValues.set(entry, i);
        }
        return UtilGraph.newStateMap(states.clone(), resultValues);
    }

    @Override
    public boolean canHandle() throws EPMCException {
        assert property != null;
        if (!(modelChecker.getEngine() instanceof EngineExplicit)) {
            return false;
        }
        if (!(property instanceof ExpressionQuantifier)) {
            return false;
        }
        ExpressionQuantifier propertyQuantifier = (ExpressionQuantifier) property;
        if (!(propertyQuantifier.getQuantified() instanceof ExpressionReward)) {
            return false;
        }
        ExpressionReward quantifiedReward = (ExpressionReward) propertyQuantifier.getQuantified();
        if (((ExpressionReward) (propertyQuantifier.getQuantified())).getRewardType().isReachability()) {
        	StateSet allStates = UtilGraph.computeAllStatesExplicit(modelChecker.getLowLevel());
        	modelChecker.ensureCanHandle(quantifiedReward.getRewardReachSet(), allStates);
        	if (allStates != null) {
        		allStates.close();
        	}
        }
        return true;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
    
    private ContextValue getContextValue() {
    	return modelChecker.getModel().getContextValue();
    }

    private ValueArray newValueArrayWeight(int size) {
        TypeArray typeArray = TypeWeight.get(getContextValue()).getTypeArray();
        return UtilValue.newArray(typeArray, size);
    }
    
    private ValueAlgebra newValueWeight() {
    	return TypeWeight.get(getContextValue()).newValue();
    }
    
    private Value evaluateValue(Expression expression) throws EPMCException {
        assert expression != null;
        EvaluatorExplicit evaluator = UtilEvaluatorExplicit.newEvaluator(expression, graph, new Expression[0]);
        return evaluator.evaluate();
    }
}
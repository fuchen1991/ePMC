/****************************************************************************

    ePMC - an extensible probabilistic model checker
    Copyright (C) 2017

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*****************************************************************************/

package epmc.coalition.graphsolver;

import java.util.ArrayList;
import java.util.List;

import epmc.error.EPMCException;
import epmc.graph.CommonProperties;
import epmc.graph.GraphBuilderExplicit;
import epmc.graph.Player;
import epmc.graph.Scheduler;
import epmc.graph.Semantics;
import epmc.graph.SemanticsSMG;
import epmc.graph.explicit.EdgeProperty;
import epmc.graph.explicit.GraphExplicit;
import epmc.graph.explicit.GraphExplicitSparseAlternate;
import epmc.graph.explicit.NodeProperty;
import epmc.graph.explicit.SchedulerSimple;
import epmc.graph.explicit.SchedulerSimpleArray;
import epmc.graph.explicit.SchedulerSimpleSettable;
import epmc.graphsolver.GraphSolverExplicit;
import epmc.graphsolver.iterative.IterationMethod;
import epmc.graphsolver.iterative.IterationStopCriterion;
import epmc.graphsolver.iterative.MessagesGraphSolverIterative;
import epmc.graphsolver.iterative.OptionsGraphSolverIterative;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicit;
import epmc.messages.OptionsMessages;
import epmc.modelchecker.Log;
import epmc.options.Options;
import epmc.util.BitSet;
import epmc.util.StopWatch;
import epmc.util.UtilBitSet;
import epmc.value.TypeAlgebra;
import epmc.value.TypeArrayAlgebra;
import epmc.value.TypeWeight;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueAlgebra;
import epmc.value.ValueArray;
import epmc.value.ValueArrayAlgebra;
import epmc.value.ValueObject;

/**
 * Iterative solver to solve game-related graph problems.
 * 
 * @author Ernst Moritz Hahn
 */
public final class GraphSolverIterativeCoalitionJava implements GraphSolverExplicit {
	/** Identifier of the iteration-based graph game solver. */
    public static String IDENTIFIER = "graph-solver-iterative-coalition-java";
    
    /** Original graph. */
    private GraphExplicit origGraph;
    /** Graph used for iteration, derived from original graph. */
    private GraphExplicit iterGraph;
    private ValueArrayAlgebra inputValues;
    private ValueArrayAlgebra outputValues;
    /** Number of iterations needed TODO check if indeed used*/
    private int numIterations;
    private GraphSolverObjectiveExplicit objective;
    private GraphBuilderExplicit builder;
	private int maxEnd;

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public void setGraphSolverObjective(GraphSolverObjectiveExplicit objective) {
    	this.objective = objective;
        origGraph = objective.getGraph();
    }

    @Override
    public boolean canHandle() {
        Semantics semantics = ValueObject.asObject(origGraph.getGraphProperty(CommonProperties.SEMANTICS)).getObject();
        if (!SemanticsSMG.isSMG(semantics)) {
        	return false;
        }
    	if (!(objective instanceof GraphSolverObjectiveExplicitUnboundedReachabilityGame)) {
            return false;
        }
        return true;
    }

    @Override
    public void solve() throws EPMCException {
    	prepareIterGraph();
        if (objective instanceof GraphSolverObjectiveExplicitUnboundedReachabilityGame) {
        	unboundedReachability();
            prepareResultValues();
        	GraphSolverObjectiveExplicitUnboundedReachabilityGame objUB = (GraphSolverObjectiveExplicitUnboundedReachabilityGame) objective;
        	if (objUB.isComputeScheduler()) {
        		SchedulerSimpleSettable strategy = new SchedulerSimpleArray(origGraph);
        		computeStrategy(strategy, objUB.getTarget(), outputValues);
        		objUB.setScheduler(strategy);
        	}
        } else {
            assert false;
        }
    }

    private void prepareIterGraph() throws EPMCException {
        assert origGraph != null;
        
        BitSet playerEven = UtilBitSet.newBitSetUnbounded();
        BitSet playerOdd = UtilBitSet.newBitSetUnbounded();
        BitSet playerStochastic = UtilBitSet.newBitSetUnbounded();
        NodeProperty playerProp = origGraph.getNodeProperty(CommonProperties.PLAYER);
        int origNumNodes = origGraph.getNumNodes();
        maxEnd = 0;
        for (int node = 0; node < origNumNodes; node++) {
            Player player = playerProp.getEnum(node);
            playerEven.set(node, player == Player.ONE);
            maxEnd += player == Player.ONE ? 1 : 0;
            playerOdd.set(node, player == Player.TWO);
            playerStochastic.set(node, player == Player.STOCHASTIC);
        }
        List<BitSet> parts = new ArrayList<>();
        parts.add(playerEven);
        parts.add(playerOdd);
        parts.add(playerStochastic);
        this.builder = new GraphBuilderExplicit();
        builder.setInputGraph(origGraph);
        builder.addDerivedGraphProperties(origGraph.getGraphProperties());
        builder.addDerivedEdgeProperties(origGraph.getEdgeProperties());
        builder.setParts(parts);
        builder.setReorder();
        builder.build();
        this.iterGraph = builder.getOutputGraph();
        assert iterGraph != null;
        BitSet targets = null;
        if (objective instanceof GraphSolverObjectiveExplicitUnboundedReachabilityGame) {
        	GraphSolverObjectiveExplicitUnboundedReachabilityGame objectiveUnboundedReachability = (GraphSolverObjectiveExplicitUnboundedReachabilityGame) objective;
        	targets = objectiveUnboundedReachability.getTarget();
        }
        assert this.inputValues == null;
        int numStates = iterGraph.computeNumStates();
        this.inputValues = UtilValue.newArray(TypeWeight.get().getTypeArray(), numStates);
        for (int origNode = 0; origNode < origNumNodes; origNode++) {
        	int iterNode = builder.inputToOutputNode(origNode);
        	if (iterNode < 0) {
        		continue;
        	}
        	this.inputValues.set(targets.get(origNode) ? 1 : 0, iterNode);
        }
    }

    private void prepareResultValues() throws EPMCException {
    	TypeAlgebra typeWeight = TypeWeight.get();
    	TypeArrayAlgebra typeArrayWeight = typeWeight.getTypeArray();
    	this.outputValues = UtilValue.newArray(typeArrayWeight, origGraph.getNumNodes());
    	ValueAlgebra val = typeWeight.newValue();
    	ValueAlgebra get = typeWeight.newValue();
    	ValueAlgebra weighted = typeWeight.newValue();
    	int origNumNodes = origGraph.getNumNodes();
    	GraphSolverObjectiveExplicitUnboundedReachabilityGame objective = (GraphSolverObjectiveExplicitUnboundedReachabilityGame) this.objective;
    	NodeProperty playerProp = origGraph.getNodeProperty(CommonProperties.PLAYER);
    	EdgeProperty weightProp = origGraph.getEdgeProperty(CommonProperties.WEIGHT);
    	for (int origNode = 0; origNode < origNumNodes; origNode++) {
    		Player player = playerProp.getEnum(origNode);
    		int iterState = builder.inputToOutputNode(origNode);
    		if (iterState == -1) {
    			continue;
    		}
    		inputValues.get(val, iterState);
    		outputValues.set(val, origNode);
    		assert player == Player.STOCHASTIC
    				|| !objective.getTarget().get(origNode)
    				|| val.isOne()
    				: origNode + " " + val + " " + player + " " + objective.getTarget().get(origNode);
    	}
    	for (int origNode = 0; origNode < origNumNodes; origNode++) {
    		Player player = playerProp.getEnum(origNode);
    		if (player == Player.STOCHASTIC) {
    			val.set(0);
    			int numSucc = origGraph.getNumSuccessors(origNode);

    			for (int succ = 0; succ < numSucc; succ++) {
    				outputValues.get(get, origGraph.getSuccessorNode(origNode, succ));
    				weighted.multiply(get, weightProp.get(origNode, succ));
    				val.add(val, weighted);
    			}
    			outputValues.set(val, origNode);
    		}
    	}
    	objective.setResult(outputValues);
    }

    private void unboundedReachability() throws EPMCException {
        Options options = Options.get();
        Log log = options.get(OptionsMessages.LOG);
        StopWatch timer = new StopWatch(true);
        log.send(MessagesGraphSolverIterative.ITERATING);
        IterationMethod iterMethod = options.getEnum(OptionsGraphSolverIterative.GRAPHSOLVER_ITERATIVE_METHOD);
        IterationStopCriterion stopCriterion = options.getEnum(OptionsGraphSolverIterative.GRAPHSOLVER_ITERATIVE_STOP_CRITERION);
        numIterations = 0;
        double precision = options.getDouble(OptionsGraphSolverIterative.GRAPHSOLVER_ITERATIVE_TOLERANCE);
        if (isSparseTPGJava(iterGraph) && iterMethod == IterationMethod.JACOBI) {
            tpgUnboundedJacobiJava(asSparseNondet(iterGraph), inputValues, stopCriterion, precision);
        } else if (isSparseTPGJava(iterGraph) && iterMethod == IterationMethod.GAUSS_SEIDEL) {
            tpgUnboundedGaussseidelJava(asSparseNondet(iterGraph), inputValues, stopCriterion, precision);
            assert false : iterGraph.getClass();
        }
        log.send(MessagesGraphSolverIterative.ITERATING_DONE, numIterations,
                timer.getTimeSeconds());
    }

    /* auxiliary methods */
    
    private static void compDiff(double[] distance, ValueAlgebra previous,
            Value current, IterationStopCriterion stopCriterion) throws EPMCException {
        if (stopCriterion == null) {
            return;
        }
        double thisDistance = previous.distance(current);
        if (stopCriterion == IterationStopCriterion.RELATIVE) {
            double presNorm = previous.norm();
            if (presNorm != 0.0) {
                thisDistance /= presNorm;
            }
        }
        distance[0] = Math.max(distance[0], thisDistance);
    }
    
    private static boolean isSparseNondet(GraphExplicit graph) {
        return graph instanceof GraphExplicitSparseAlternate;
    }
    
    private static boolean isSparseTPGJava(GraphExplicit graph) {
        if (!isSparseNondet(graph)) {
            return false;
        }
        Semantics semantics = graph.getGraphPropertyObject(CommonProperties.SEMANTICS);
        if (!SemanticsSMG.isSMG(semantics)) {
            return false;
        }
        return true;
    }
    
    private static GraphExplicitSparseAlternate asSparseNondet(GraphExplicit graph) {
        return (GraphExplicitSparseAlternate) graph;
    }
    
    /* implementation/native call of/to iteration algorithms */    
    
    private void tpgUnboundedGaussseidelJava(
            GraphExplicitSparseAlternate graph,
            ValueArrayAlgebra values, IterationStopCriterion stopCriterion,
            double precision) throws EPMCException {
    	
        int minEnd = graph.computeNumStates();
        int[] stateBounds = graph.getStateBoundsJava();
        int[] nondetBounds = graph.getNondetBoundsJava();
        int[] targets = graph.getTargetsJava();
        ValueArray weights = ValueArray.asArray(graph.getEdgeProperty(CommonProperties.WEIGHT).asSparseNondetOnlyNondet().getContent());
        ValueAlgebra weight = newValueWeight();
        ValueAlgebra weighted = newValueWeight();
        ValueAlgebra succStateProb = newValueWeight();
        ValueAlgebra nextStateProb = newValueWeight();
        ValueAlgebra choiceNextStateProb = newValueWeight();
        ValueAlgebra presStateProb = newValueWeight();
        double[] distance = new double[1];
        Value zero = values.getType().getEntryType().getZero();
        Value negInf = TypeWeight.asWeight(values.getType().getEntryType()).getNegInf();
        Value posInf = TypeWeight.asWeight(values.getType().getEntryType()).getPosInf();
        
        do {
            distance[0] = 0.0;
            for (int state = 0; state < maxEnd; state++) {
                values.get(presStateProb, state);
                int stateFrom = stateBounds[state];
                int stateTo = stateBounds[state + 1];
                nextStateProb.set(negInf);
                for (int nondetNr = stateFrom; nondetNr < stateTo; nondetNr++) {
                    int nondetFrom = nondetBounds[nondetNr];
                    int nondetTo = nondetBounds[nondetNr + 1];
                    choiceNextStateProb.set(zero);
                    for (int stateSucc = nondetFrom; stateSucc < nondetTo; stateSucc++) {
                        weights.get(weighted, stateSucc);
                        int succState = targets[stateSucc];
                        values.get(succStateProb, succState);
                        weighted.multiply(weight, succStateProb);
                        choiceNextStateProb.add(choiceNextStateProb, weighted);
                    }
                    nextStateProb.max(nextStateProb, choiceNextStateProb);
                }
                compDiff(distance, presStateProb, nextStateProb, stopCriterion);
                values.set(nextStateProb, state);
            }
            for (int state = maxEnd; state < minEnd; state++) {
                values.get(presStateProb, state);
                int stateFrom = stateBounds[state];
                int stateTo = stateBounds[state + 1];
                nextStateProb.set(posInf);
                for (int nondetNr = stateFrom; nondetNr < stateTo; nondetNr++) {
                    int nondetFrom = nondetBounds[nondetNr];
                    int nondetTo = nondetBounds[nondetNr + 1];
                    choiceNextStateProb.set(zero);
                    for (int stateSucc = nondetFrom; stateSucc < nondetTo; stateSucc++) {
                        weights.get(weight, stateSucc);
                        int succState = targets[stateSucc];
                        values.get(succStateProb, succState);
                        weighted.multiply(weight, succStateProb);
                        choiceNextStateProb.add(choiceNextStateProb, weighted);
                    }
                    nextStateProb.min(nextStateProb, choiceNextStateProb);
                }
                compDiff(distance, presStateProb, nextStateProb, stopCriterion);
                values.set(nextStateProb, state);
            }
        } while (distance[0] > precision / 2);
    }

    private void tpgUnboundedJacobiJava(
            GraphExplicitSparseAlternate graph,
            ValueArrayAlgebra values, IterationStopCriterion stopCriterion,
            double precision) throws EPMCException {
        int minEnd = graph.computeNumStates();
        int[] stateBounds = graph.getStateBoundsJava();
        int[] nondetBounds = graph.getNondetBoundsJava();
        int[] targets = graph.getTargetsJava();
        ValueArrayAlgebra weights = ValueArrayAlgebra.asArrayAlgebra(graph.getEdgeProperty(CommonProperties.WEIGHT).asSparseNondetOnlyNondet().getContent());
        ValueAlgebra weight = newValueWeight();
        ValueAlgebra weighted = newValueWeight();
        ValueAlgebra succStateProb = newValueWeight();
        ValueAlgebra nextStateProb = newValueWeight();
        ValueAlgebra choiceNextStateProb = newValueWeight();
        ValueAlgebra presStateProb = newValueWeight();
        double[] distance = new double[1];
        Value zero = values.getType().getEntryType().getZero();
        ValueArrayAlgebra presValues = values;
        ValueArrayAlgebra nextValues = UtilValue.newArray(values.getType(), minEnd);
        Value negInf = TypeWeight.asWeight(values.getType().getEntryType()).getNegInf();
        Value posInf = TypeWeight.asWeight(values.getType().getEntryType()).getPosInf();
        
        do {
            distance[0] = 0.0;
            for (int state = 0; state < maxEnd; state++) {
                presValues.get(presStateProb, state);
                int stateFrom = stateBounds[state];
                int stateTo = stateBounds[state + 1];
                nextStateProb.set(negInf);
                for (int nondetNr = stateFrom; nondetNr < stateTo; nondetNr++) {
                    int nondetFrom = nondetBounds[nondetNr];
                    int nondetTo = nondetBounds[nondetNr + 1];
                    choiceNextStateProb.set(zero);
                    for (int stateSucc = nondetFrom; stateSucc < nondetTo; stateSucc++) {
                        weights.get(weight, stateSucc);
                        int succState = targets[stateSucc];
                        presValues.get(succStateProb, succState);
                        weighted.multiply(weight, succStateProb);
                        choiceNextStateProb.add(choiceNextStateProb, weighted);
                    }
                    nextStateProb.max(nextStateProb, choiceNextStateProb);
                }
                compDiff(distance, presStateProb, nextStateProb, stopCriterion);
                nextValues.set(nextStateProb, state);
            }
            for (int state = maxEnd; state < minEnd; state++) {
                presValues.get(presStateProb, state);
                int stateFrom = stateBounds[state];
                int stateTo = stateBounds[state + 1];
                nextStateProb.set(posInf);
                for (int nondetNr = stateFrom; nondetNr < stateTo; nondetNr++) {
                    int nondetFrom = nondetBounds[nondetNr];
                    int nondetTo = nondetBounds[nondetNr + 1];
                    choiceNextStateProb.set(zero);
                    for (int stateSucc = nondetFrom; stateSucc < nondetTo; stateSucc++) {
                        weights.get(weight, stateSucc);
                        int succState = targets[stateSucc];
                        presValues.get(succStateProb, succState);
                        weighted.multiply(weight, succStateProb);
                        choiceNextStateProb.add(choiceNextStateProb, weighted);
                    }
                    nextStateProb.min(nextStateProb, choiceNextStateProb);
                }
                compDiff(distance, presStateProb, nextStateProb, stopCriterion);
                nextValues.set(nextStateProb, state);
            }
            ValueArrayAlgebra swap = nextValues;
            nextValues = presValues;
            presValues = swap;
        } while (distance[0] > precision / 2);
        for (int state = 0; state < minEnd; state++) {
            presValues.get(presStateProb, state);
            values.set(presStateProb, state);
        }
    }

    private void computeStrategy(SchedulerSimpleSettable strategy,
            BitSet target, ValueArrayAlgebra values)
                    throws EPMCException {
    	assert strategy != null;
        assert target != null;
        NodeProperty playerProperty = origGraph.getNodeProperty(CommonProperties.PLAYER);
        
        origGraph.computePredecessors();
        
        BitSet newNodes = target.clone();
        BitSet previousNodes = UtilBitSet.newBitSetBounded(origGraph.getNumNodes());
        BitSet seen = UtilBitSet.newBitSetBounded(origGraph.getNumNodes());
        seen.or(target);
        ValueAlgebra nodeValue = newValueWeight();
        ValueAlgebra predValue = newValueWeight();
        double tolerance = Options.get().getDouble(OptionsGraphSolverIterative.GRAPHSOLVER_ITERATIVE_TOLERANCE) * 4;
        do {
            BitSet swap = previousNodes;
            previousNodes = newNodes;
            newNodes = swap;
            newNodes.clear();
            for (int node = previousNodes.nextSetBit(0); node >= 0;
            		node = previousNodes.nextSetBit(node+1)) {
                Player player = playerProperty.getEnum(node);
                /* player even or odd node - predecessors are distributions */
                if (player == Player.ONE || player == Player.TWO) {
                    for (int predNr = 0; predNr < origGraph.getProperties().getNumPredecessors(node); predNr++) {
                        int pred = origGraph.getProperties().getPredecessorNode(node, predNr);
                        if (!seen.get(pred)) {
                            strategy.set(pred, origGraph.getSuccessorNumber(pred, node));
                            seen.set(pred);
                            newNodes.set(pred);
                        }
                    }
                } else if (player == Player.STOCHASTIC) {
                	/* distribution node - predecessors and successors are even or odd */
                	values.get(nodeValue, node);
                	/*
                	nodeValue.set(0);
                	for (int succNr = 0; succNr < origGraph.getNumSuccessors(); succNr++) {
                		int succNode = origGraph.getSuccessorNode(succNr);
                    	values.get(succValue, succNode);
                		Value weight = weightProperty.get(succNr);
                		weighted.multiply(weight, succValue);
                		nodeValue.add(nodeValue, weighted);
                	}
                	*/
                    for (int predNr = 0; predNr < origGraph.getProperties().getNumPredecessors(node); predNr++) {
                        int pred = origGraph.getProperties().getPredecessorNode(node, predNr);
                    	values.get(predValue, pred);
                    	if (!seen.get(pred) && predValue.distance(nodeValue) < tolerance) {
                            strategy.set(pred, origGraph.getSuccessorNumber(pred, node));
                            seen.set(pred);
                            newNodes.set(pred);
                    	}
                    }
                } else {
                	assert false;
                }
            }
        } while (!newNodes.isEmpty());
        for (int node = 0; node < origGraph.getNumNodes(); node++) {
        	Player player = playerProperty.getEnum(node);
        	if ((player == Player.ONE || player == Player.TWO)
        			&& !seen.get(node) && !target.get(node)) {
        		values.get(nodeValue, node);
        		assert nodeValue.distance(TypeWeight.get().getZero()) < tolerance : node + " " + nodeValue;
        		strategy.set(node, 0);
        	}
        }
        assert assertStrategyOK(strategy, target);
    }

    private boolean assertStrategyOK(SchedulerSimple strategy, BitSet target) throws EPMCException {
        /* make sure that we indeed computed the strategy correctly */
    	NodeProperty playerProperty = origGraph.getNodeProperty(CommonProperties.PLAYER);
        for (int node = 0; node < origGraph.getNumNodes(); node++) {
        	Player player = playerProperty.getEnum(node);
        	assert player == Player.STOCHASTIC || target.get(node) || strategy.getDecision(node) != Scheduler.UNSET : node;
        }
		return true;
	}

    private ValueAlgebra newValueWeight() {
    	return TypeWeight.get().newValue();
    }
}
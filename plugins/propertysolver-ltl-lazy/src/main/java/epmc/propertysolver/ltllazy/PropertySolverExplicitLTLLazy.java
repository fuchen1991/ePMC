package epmc.propertysolver.ltllazy;

import static epmc.error.UtilError.ensure;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import epmc.algorithms.UtilAlgorithms;
import epmc.algorithms.explicit.ComponentsExplicit;
import epmc.algorithms.explicit.EndComponents;
import epmc.automaton.Automaton;
import epmc.automaton.AutomatonRabin;
import epmc.automaton.AutomatonRabinLabel;
import epmc.automaton.AutomatonStateBuechi;
import epmc.automaton.Buechi;
import epmc.automaton.ProductGraphExplicit;
import epmc.automaton.UtilAutomaton;
import epmc.error.EPMCException;
import epmc.expression.Expression;
import epmc.expression.standard.CmpType;
import epmc.expression.standard.DirType;
import epmc.expression.standard.ExpressionQuantifier;
import epmc.expression.standard.ExpressionReward;
import epmc.graph.CommonProperties;
import epmc.graph.Semantics;
import epmc.graph.SemanticsMDP;
import epmc.graph.SemanticsMarkovChain;
import epmc.graph.SemanticsNonDet;
import epmc.graph.StateMap;
import epmc.graph.StateSet;
import epmc.graph.UtilGraph;
import epmc.graph.explicit.GraphExplicit;
import epmc.graph.explicit.GraphExplicitWrapper;
import epmc.graph.explicit.NodeProperty;
import epmc.graph.explicit.StateMapExplicit;
import epmc.graph.explicit.StateSetExplicit;
import epmc.graphsolver.GraphSolverConfigurationExplicit;
import epmc.graphsolver.UtilGraphSolver;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitUnboundedReachability;
import epmc.messages.OptionsMessages;
import epmc.modelchecker.EngineExplicit;
import epmc.modelchecker.Log;
import epmc.modelchecker.ModelChecker;
import epmc.modelchecker.PropertySolver;
import epmc.options.Options;
import epmc.propertysolver.ltllazy.automata.AutomatonBreakpoint;
import epmc.propertysolver.ltllazy.automata.AutomatonBreakpointLabel;
import epmc.propertysolver.ltllazy.automata.AutomatonSubset;
import epmc.propertysolver.ltllazy.automata.AutomatonSubsetLabel;
import epmc.propertysolver.ltllazy.automata.AutomatonSubsetState;
import epmc.util.BitSet;
import epmc.util.StopWatch;
import epmc.util.UtilBitSet;
import epmc.value.ContextValue;
import epmc.value.Operator;
import epmc.value.Type;
import epmc.value.TypeAlgebra;
import epmc.value.TypeBoolean;
import epmc.value.TypeReal;
import epmc.value.TypeWeight;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueAlgebra;
import epmc.value.ValueArray;
import epmc.value.ValueArrayAlgebra;
import epmc.value.ValueBoolean;
import epmc.value.ValueObject;

public class PropertySolverExplicitLTLLazy implements PropertySolver {
    public final static String IDENTIFIER = "ltl-explicit";
    private ModelChecker modelChecker;
    private ContextValue contextValue;
    private GraphExplicit graph;
    private boolean nonDet;
    private Options options;
    private Log log;
    private StateSetExplicit forStates;
    private boolean negate;
	private Expression property;
	private ExpressionQuantifier propertyQuantifier;

    @Override
    public void setModelChecker(ModelChecker modelChecker) {
        assert modelChecker != null;
        this.modelChecker = modelChecker;
        this.options = modelChecker.getModel().getContextValue().getOptions();
        this.contextValue = modelChecker.getModel().getContextValue();
        if (modelChecker.getEngine() instanceof EngineExplicit) {
        	this.graph = modelChecker.getLowLevel();
        }
        this.nonDet = SemanticsNonDet.isNonDet(modelChecker.getModel().getSemantics());
        this.log = options.get(OptionsMessages.LOG);
    }

    private StateMapExplicit checkTemporalLTLNonIncremental(Buechi buechi)
            throws EPMCException {
        StateMapExplicit result = null;
        if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_SUBSET)) {
            result = computeNonIncremental(DecisionMethod.SUBSET, buechi);
        }
        if (result == null && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_BREAKPOINT)) {
            result = computeNonIncremental(DecisionMethod.BREAKPOINT, buechi);
        }
        if (result == null && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_RABIN)) {
            result = computeNonIncremental(DecisionMethod.RABIN, buechi);
        }
        return result;
    }

    private StateMapExplicit computeNonIncremental(DecisionMethod method, Buechi buechi)
            throws EPMCException {
        Automaton automaton = null;
        StopWatch timer = new StopWatch(true);
        switch (method) {
        case SUBSET:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_SUBSET);
            automaton = new AutomatonSubset.Builder().setBuechi(buechi).build();
            break;
        case BREAKPOINT:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_BREAKPOINT);
            automaton = new AutomatonBreakpoint.Builder().setBuechi(buechi).build();
            break;
        case RABIN:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_RABIN);
            automaton = UtilAutomaton.newAutomatonRabinSafra(buechi, null);
            break;
        }
        System.out.println("Done building automaton in " + timer.getTimeSeconds() + " seconds ...");
        ProductGraphExplicit prodGraph = new ProductGraphExplicit.Builder()
        		.setModel(graph)
        		.setModelInitialNodes(forStates.getStatesExplicit())
        		.setAutomaton(automaton)
        		.addGraphProperties(graph.getGraphProperties())
        		.addNodeProperty(CommonProperties.PLAYER)
        		.addNodeProperty(CommonProperties.STATE)
        		.addEdgeProperty(CommonProperties.WEIGHT)
        		.build();
        GraphExplicitWrapper graph = new GraphExplicitWrapper(prodGraph);
        graph.addDerivedGraphProperties(prodGraph.getGraphProperties());
        graph.addDerivedNodeProperty(CommonProperties.STATE);
        graph.addDerivedNodeProperty(CommonProperties.PLAYER);
        graph.addDerivedNodeProperty(CommonProperties.AUTOMATON_LABEL);
        graph.addDerivedNodeProperty(CommonProperties.NODE_AUTOMATON);
        graph.addDerivedNodeProperty(CommonProperties.NODE_MODEL);
        graph.addDerivedEdgeProperty(CommonProperties.WEIGHT);
        switch (method) {
        case SUBSET:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_SUBSET_DONE);
            break;
        case BREAKPOINT:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_BREAKPOINT_DONE);
            break;
        case RABIN:
            log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_RABIN_DONE);
            break;
        default:
            break;
        }
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE);
        graph.explore();
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE_DONE, graph.computeNumStates(),
                automaton.getNumStates());
        log.send(MessagesLTLLazy.LTL_LAZY_COMPUTING_END_COMPONENTS);
        BitSet acc = UtilBitSet.newBitSetUnbounded();
        BitSet undecided = UtilBitSet.newBitSetUnbounded();
        BitSet initNs = graph.getInitialNodes();
        BitSet init = initNs.clone();
        
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        EndComponents endComponents = components.maximalEndComponents(graph);
        int numComponents = 0;
        int numECCStates = 0;
        NodeProperty isState = graph.getNodeProperty(CommonProperties.STATE);
        for (BitSet leafSCC = endComponents.next(); leafSCC != null; leafSCC = endComponents.next()) {
            numComponents++;
            for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
                graph.queryNode(node);
                if (isState.getBoolean()) {
                    numECCStates++;
                }
            }
            if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_STOP_IF_INIT_DECIDED)) {
                BitSet testInit = init.clone();
                testInit.and(acc);
                if (testInit.equals(init)) {
                    undecided.clear();
                    break;
                }
            }
            
            ComponentDecision decision = null;
            switch (method) {
            case SUBSET:
                decision = decideComponentSubset(leafSCC, graph, buechi.getNumLabels());
                break;
            case BREAKPOINT:
                if (nonDet) {
                    decision = decideComponentBreakpointMDPLeaf(graph, leafSCC);
                } else {
                    decision = decideComponentBreakpointMCLeaf(graph, leafSCC);                    
                }
                break;
            case RABIN:
                if (nonDet) {
                    decision = decideComponentRabinMDPLeaf(graph, leafSCC);
                } else {
                    decision = decideComponentRabinMCLeaf(graph, leafSCC);
                }
                break;
            }
            if (decision == ComponentDecision.ACCEPT) {
                if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_REMOVE_DECIDED)) {
                    leafSCC = components.reachMaxOne(graph, leafSCC);
                }
                acc.or(leafSCC);
            } else if (decision == ComponentDecision.UNDECIDED) {
                undecided.or(leafSCC);
            }
        }
        log.send(MessagesLTLLazy.LTL_LAZY_NUM_END_COMPONENTS, numComponents);
        log.send(MessagesLTLLazy.LTL_LAZY_NUM_ECC_STATES, numECCStates);
        undecided.andNot(acc);
        if (!undecided.isEmpty()) {
            return null;
        }
        
        return prodToOrigResult(prepareAndIterate(graph, acc), graph);
    }

    private StateMapExplicit prodToOrigResult(ValueArrayAlgebra iterResult,
            GraphExplicit prodGraph) throws EPMCException {
        // TODO implement more cleanly
        assert iterResult != null;
        assert prodGraph != null;
        Type typeWeight = TypeWeight.get(contextValue);
//        Value result = contextValue.newValueArrayWeight(modelGraph.getQueriedNodesLength());
        Value entry = typeWeight.newValue();
        BitSet nodes = forStates.getStatesExplicit();
//        NodeProperty nodeAutomaton = prodGraph.getNodeProperty(CommonProperties.NODE_MODEL);
        ValueArray resultValues = UtilValue.newArray(typeWeight.getTypeArray(), forStates.size());
        int i = 0;
        for (int node = nodes.nextSetBit(0); node >= 0; node = nodes.nextSetBit(node+1)) {
            prodGraph.queryNode(node);
//            int modelState = nodeAutomaton.getInt();
            iterResult.get(entry, i);
            resultValues.set(entry, i);
            i++;
        }
        return UtilGraph.newStateMap(forStates.clone(), resultValues);
    }

    private StateMapExplicit checkTemporalLTLIncremental(Buechi buechi)
            throws EPMCException {
        log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL);
        AutomatonSubset automaton = new AutomatonSubset.Builder().setBuechi(buechi).build();
        ProductGraphExplicit prodGraph = new ProductGraphExplicit.Builder()
        		.setModel(graph)
        		.setModelInitialNodes(forStates.getStatesExplicit())
        		.setAutomaton(automaton)
        		.addGraphProperties(graph.getGraphProperties())
        		.addNodeProperty(CommonProperties.PLAYER)
        		.addNodeProperty(CommonProperties.STATE)
        		.addEdgeProperty(CommonProperties.WEIGHT)
        		.build();
        GraphExplicitWrapper prodWrapper = new GraphExplicitWrapper(prodGraph);
        prodWrapper.addDerivedGraphProperties(prodGraph.getGraphProperties());
        prodWrapper.addDerivedNodeProperty(CommonProperties.STATE);
        prodWrapper.addDerivedNodeProperty(CommonProperties.PLAYER);
        prodWrapper.addDerivedNodeProperty(CommonProperties.AUTOMATON_LABEL);
        prodWrapper.addDerivedNodeProperty(CommonProperties.NODE_AUTOMATON);
        prodWrapper.addDerivedNodeProperty(CommonProperties.NODE_MODEL);
        prodWrapper.addDerivedEdgeProperty(CommonProperties.WEIGHT);
        log.send(MessagesLTLLazy.LTL_LAZY_INITIALISING_AUTOMATON_AND_PRODUCT_MODEL_DONE);
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE);
        prodWrapper.explore();
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE_DONE, prodWrapper.computeNumStates(),
                automaton.getNumStates());
        log.send(MessagesLTLLazy.LTL_LAZY_COMPUTING_END_COMPONENTS_INCREMENTALLY);
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        EndComponents endComponents = components.maximalEndComponents(prodWrapper);
        BitSet acc = UtilBitSet.newBitSetUnbounded();
        log.send(MessagesLTLLazy.LTL_LAZY_COMPUTING_END_COMPONENTS_INCREMENTALLY_DONE);
        decideComponents(buechi, endComponents, automaton.getNumLabels(), acc, prodWrapper);
        return prodToOrigResult(prepareAndIterate(prodWrapper, acc), prodGraph);
    }

    private ValueArrayAlgebra prepareAndIterate(GraphExplicit graph, BitSet acc)
            throws EPMCException {
        GraphSolverConfigurationExplicit configuration = UtilGraphSolver.newGraphSolverConfigurationExplicit(graph.getOptions());
        GraphSolverObjectiveExplicitUnboundedReachability objective = new GraphSolverObjectiveExplicitUnboundedReachability();
        objective.setMin(false);
        objective.setGraph(graph);
        objective.setTarget(acc);
        configuration.setObjective(objective);
        configuration.solve();
        ValueArrayAlgebra values = objective.getResult();
        return values;

        /*
        log.send(Messages.PREPARING_MDP_FOR_ITERATION);
        if (options.getBoolean(Options.ONE_PRECOMPUTATION)) {
            ComponentsExplicit components = UtilAlgorithm.newComponentsExplicit();
            acc = components.reachMaxOne(graph, acc);            
        }
        BitSet test = (BitSet) graph.getInitialNodes().clone();
        test.andNot(acc);
        Type typeWeight = contextValue.getTypeWeight();
        if (test.cardinality() == 0) {
            Value values = contextValue.newValueArrayWeight(graph.getQueriedNodesLength());
            for (int node = graph.getInitialNodes().nextSetBit(0); node >= 0;
                    node = graph.getInitialNodes().nextSetBit(node+1)) {
                values.set(typeWeight.getOne(), node);
            }
            return values;
        }

        Semantics semanticsType = graph.getGraphProperty(CommonProperties.SEMANTICS).getObject();
        boolean embed = semanticsType.isContinuousTime();
        GraphBuilderExplicit builder = modelChecker.newGraphBuilderExplicit();
        builder.setInputGraph(graph);
        builder.addDerivedGraphProperties(graph.getGraphProperties());
        builder.addDerivedNodeProperty(CommonProperties.PLAYER);
        builder.addDerivedNodeProperty(CommonProperties.STATE);
        builder.addDerivedEdgeProperty(CommonProperties.WEIGHT);
        builder.addSink(acc);
        builder.setForIteration();
        builder.build();
        GraphExplicit iterGraph = builder.getOutputGraph();
        if (embed) {
            GraphExplicitModifier.embed(iterGraph);
        }

        BitSet targetS = new BitSet(iterGraph.computeNumStates());
        
        NodeProperty isState = graph.getNodeProperty(CommonProperties.STATE);
        for (int node = acc.nextSetBit(0); node >= 0; node = acc.nextSetBit(node+1)) {
            graph.queryNode(node);
            if (isState.getBoolean()) {
                targetS.set(builder.inputToOutputNode(node));
            }
        }
        log.send(Messages.PREPARING_MDP_FOR_ITERATION_DONE, iterGraph.computeNumStates());
        GraphSolverConfiguration iters = modelChecker.newGraphSolverConfiguration();
        iters.setGraph(iterGraph);
        iters.setMin(false);
        iters.setTargetStates(targetS);
        iters.setObjective(CommonGraphSolverObjective.UNBOUNDED_REACHABILITY);
        iters.solve();
        Value values = iters.getOutputValues();
        Value entry = typeWeight.newValue();
        Value result = contextValue.newValueArrayWeight(graph.getQueriedNodesLength());
        for (int node = graph.getQueriedNodes().nextSetBit(0); node >= 0; node = graph.getQueriedNodes().nextSetBit(node+1)) {
            graph.queryNode(node);
            if (isState.getBoolean()) {
                int newNode = builder.inputToOutputNode(node);
                values.get(entry, newNode);
                result.set(entry, node);
            }
        }
        return result;
        */
    }

    private enum ComponentDecision {
        ACCEPT, REJECT, UNDECIDED
    }

    private enum DecisionMethod {
        SUBSET, BREAKPOINT, RABIN
    }
    
    private void decideComponents(Buechi buechi,
            EndComponents endComponents,
            int numLabels, BitSet acc, GraphExplicit graph)
                    throws EPMCException {
        int numComponents = 0;
        BitSet undecided = UtilBitSet.newBitSetUnbounded();
        BitSet init = graph.getInitialNodes().clone();
        int numECCStates = 0;
        NodeProperty isState = graph.getNodeProperty(CommonProperties.STATE);
        for (BitSet ecc = endComponents.next(); ecc != null; ecc = endComponents.next()) {
            if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_STOP_IF_INIT_DECIDED)) {
                BitSet testInit = init.clone();
                testInit.and(acc);
                if (testInit.equals(init)) {
                    undecided.clear();
                    break;
                }
            }
            for (int node = ecc.nextSetBit(0); node >= 0; node = ecc.nextSetBit(node+1)) {
                graph.queryNode(node);
                if (isState.getBoolean()) {
                    numECCStates++;
                }
            }
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_COMPONENT, numComponents);
            if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_REMOVE_DECIDED)) {
                BitSet testSet = ecc.clone();
                testSet.and(acc);
                if (testSet.equals(ecc)) {                    
                    log.send(MessagesLTLLazy.LTL_LAZY_SKIPPING_COMPONENT, numComponents);
                    numComponents++;
                    continue;
                }
            }
            ComponentDecision decision = ComponentDecision.UNDECIDED;
            boolean bscc = isBSCC(graph, ecc);
            if (decision == ComponentDecision.UNDECIDED
                    && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_SUBSET)) {
                decision = decideComponentSubset(ecc, graph, numLabels);
            }
            if (decision == ComponentDecision.UNDECIDED
                    && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_BREAKPOINT)) {
                decision = decideComponentBreakpoint(graph, ecc, bscc);
            }
            if (decision == ComponentDecision.UNDECIDED
                    && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_RABIN)) {
                decision = decideComponentRabin(graph, ecc, bscc);
            }
            if (decision == ComponentDecision.UNDECIDED
                    && options.getBoolean(OptionsLTLLazy.LTL_LAZY_USE_BREAKPOINT_SINGLETONS)) {
                decision = decideComponentBreakpointSingletons(graph, buechi, ecc, bscc);
            }
            
            if (decision == ComponentDecision.ACCEPT) {
                if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_REMOVE_DECIDED)) {
                    ComponentsExplicit components  = UtilAlgorithms.newComponentsExplicit();
                    ecc = components.reachMaxOne(graph, ecc);
                }
                acc.or(ecc);
            }
            if (decision == ComponentDecision.UNDECIDED) {
                undecided.or(ecc);
            } else {
                undecided.andNot(ecc);
            }
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_COMPONENT_DONE, numComponents);
            numComponents++;
        }
        log.send(MessagesLTLLazy.LTL_LAZY_NUM_END_COMPONENTS, numComponents);
        log.send(MessagesLTLLazy.LTL_LAZY_NUM_ECC_STATES, numECCStates);
        ensure(undecided.isEmpty(), ProblemsLTLLazy.LTL_LAZY_COULDNT_DECIDE);
    }

    private ComponentDecision decideComponentSubset(BitSet ecc, GraphExplicit graph, int numLabels)
            throws EPMCException {
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_SUBSET);
        BitSet labelsFoundLower = UtilBitSet.newBitSetUnbounded(numLabels);
        BitSet labelsFoundOver = UtilBitSet.newBitSetUnbounded(numLabels);
        NodeProperty automatonLabel = graph.getNodeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int node = ecc.nextSetBit(0); node >= 0; node = ecc.nextSetBit(node+1)) {
            graph.queryNode(node);
            AutomatonSubsetLabel label = automatonLabel.getObject();
            labelsFoundLower.or(label.getUnder());
            labelsFoundOver.or(label.getOver());
        }
        if (labelsFoundLower.cardinality() == numLabels) {
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_SUBSET_DONE_ACCEPT);
            return ComponentDecision.ACCEPT;
        } else if (labelsFoundOver.cardinality() != numLabels) {
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_SUBSET_DONE_REJECT);
            return ComponentDecision.REJECT;
        } else {
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_SUBSET_DONE_UNDECIDED);
            return ComponentDecision.UNDECIDED;
        }
    }

    private ComponentDecision decideComponentBreakpointSingletons(GraphExplicit subsetGraph,
            Buechi buechi, BitSet ecc, boolean bscc)
                    throws EPMCException {
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_SINGLETONS);
        BitSet toCheck = UtilBitSet.newBitSetUnbounded();
        toCheck.or(ecc);
        NodeProperty nodeModel = subsetGraph.getNodeProperty(CommonProperties.NODE_MODEL);
        NodeProperty nodeAutomaton = subsetGraph.getNodeProperty(CommonProperties.NODE_AUTOMATON);
        while (!toCheck.isEmpty()) {
            int pNode = toCheck.nextSetBit(0);
            subsetGraph.queryNode(pNode);
            int modelState = nodeModel.getInt();
            AutomatonSubsetState subsetState = nodeAutomaton.getObject();
            BitSet states = subsetState.getStates();
            for (int state = buechi.getNumStates() - 1; state >= 0; state--) {
                if (!states.get(state)) {
                    continue;
                }
                BitSet singletonBS = UtilBitSet.newBitSetUnbounded();
                singletonBS.set(state);
                int singletonNr = subsetState.getAutomaton().getState(singletonBS);
                AutomatonSubsetState singleton = (AutomatonSubsetState) subsetState.getAutomaton().numberToState(singletonNr);
                AutomatonBreakpoint breakpoint = new AutomatonBreakpoint.Builder().setBuechi(buechi).setInit(singleton).build();
                ProductGraphExplicit prodGraph = new ProductGraphExplicit.Builder()
                		.setModel(graph)
                		.setModelInitialNode(modelState)
                		.setAutomaton(breakpoint)
                		.addGraphProperties(graph.getGraphProperties())
                		.addNodeProperty(CommonProperties.PLAYER)
                		.addNodeProperty(CommonProperties.STATE)
                		.addEdgeProperty(CommonProperties.WEIGHT)
                		.build();
                GraphExplicitWrapper graph = new GraphExplicitWrapper(prodGraph);
                graph.addDerivedGraphProperties(prodGraph.getGraphProperties());
                graph.addDerivedNodeProperty(CommonProperties.STATE);
                graph.addDerivedNodeProperty(CommonProperties.PLAYER);
                graph.addDerivedNodeProperty(CommonProperties.AUTOMATON_LABEL);
                graph.addDerivedNodeProperty(CommonProperties.NODE_AUTOMATON);
                graph.addDerivedNodeProperty(CommonProperties.NODE_MODEL);
                graph.addDerivedEdgeProperty(CommonProperties.WEIGHT);
                graph.explore();
                log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE_DONE, graph.computeNumStates(),
                        breakpoint.getNumStates());
                ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
                EndComponents endComponents = components
                        .maximalEndComponents(graph);
                for (BitSet leafSCC = endComponents.next(); leafSCC != null; leafSCC = endComponents.next()) {
                    ComponentDecision decision;
                    if (!nonDet) {
                        decision = decideComponentBreakpointMCLeaf(graph,
                                leafSCC);
                    } else {
                        decision = decideComponentBreakpointMDPLeaf(graph,
                                leafSCC);
                    }
                    if (decision == ComponentDecision.ACCEPT) {
                        if (bscc) {
                            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_SINGLETONS_DONE_ACCEPT);
                            return ComponentDecision.ACCEPT;
                        } else {
                            ProductGraphExplicit productGraph = subsetGraph.getGraphPropertyObject(CommonProperties.INNER_GRAPH);
                            int checkNode = productGraph.combineToNode(modelState, singletonNr);
                            BitSet testSet = UtilBitSet.newBitSetUnbounded();
                            testSet.set(checkNode);
                            if (checkReachOne(subsetGraph, testSet, graph, leafSCC)) {
                                log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_SINGLETONS_DONE_ACCEPT);
                                return ComponentDecision.ACCEPT;                        
                            }
                        }
                    }
                }
            }
            BitSet startSet = UtilBitSet.newBitSetUnbounded();
            startSet.set(pNode);
            ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
            BitSet reaching = components.reachPre(subsetGraph, startSet, true, false);
            toCheck.andNot(reaching);
        }
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_SINGLETONS_DONE_REJECT);
        return ComponentDecision.REJECT;
    }
    
    private ComponentDecision decideComponentBreakpoint(GraphExplicit subsetGraph,
            BitSet ecc, boolean bscc)
                    throws EPMCException {
        AutomatonSubset automaton = ValueObject.asObject(subsetGraph.getGraphProperty(CommonProperties.AUTOMATON)).getObject();
        Buechi buechi = automaton.getBuechi();
        
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT);
        int pNode = ecc.nextSetBit(0);
        subsetGraph.queryNode(pNode);
        NodeProperty modelStateProp = subsetGraph.getNodeProperty(CommonProperties.NODE_MODEL);
        int modelState = modelStateProp.getInt();
        NodeProperty nodeAutomaton = subsetGraph.getNodeProperty(CommonProperties.NODE_AUTOMATON);
        AutomatonSubsetState subsetState = nodeAutomaton.getObject();
        AutomatonBreakpoint breakpoint = new AutomatonBreakpoint.Builder().setBuechi(buechi).setInit(subsetState).build();
        ProductGraphExplicit prodGraph = new ProductGraphExplicit.Builder()
        		.setModel(graph)
        		.setModelInitialNode(modelState)
        		.setAutomaton(breakpoint)
        		.addGraphProperties(graph.getGraphProperties())
        		.addNodeProperty(CommonProperties.PLAYER)
        		.addNodeProperty(CommonProperties.STATE)
        		.addEdgeProperty(CommonProperties.WEIGHT)
        		.build();        
        GraphExplicitWrapper graph = new GraphExplicitWrapper(prodGraph);
        graph.addDerivedGraphProperties(prodGraph.getGraphProperties());
        graph.addDerivedNodeProperty(CommonProperties.STATE);
        graph.addDerivedNodeProperty(CommonProperties.PLAYER);
        graph.addDerivedNodeProperty(CommonProperties.AUTOMATON_LABEL);
        graph.addDerivedNodeProperty(CommonProperties.NODE_AUTOMATON);
        graph.addDerivedNodeProperty(CommonProperties.NODE_MODEL);
        graph.addDerivedEdgeProperty(CommonProperties.WEIGHT);
        graph.explore();
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE_DONE, graph.computeNumStates(),
                breakpoint.getNumStates());
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        EndComponents endComponents = components
                .maximalEndComponents(graph);
        boolean undecidedSeen = false;
        for (BitSet leafSCC = endComponents.next(); leafSCC != null; leafSCC = endComponents.next()) {
            ComponentDecision decision;
            if (!nonDet) {
                decision = decideComponentBreakpointMCLeaf(graph, leafSCC);
            } else {
                decision = decideComponentBreakpointMDPLeaf(graph, leafSCC);
            }
            if (decision == ComponentDecision.ACCEPT) {
                if (bscc) {
                    log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_DONE_ACCEPT);
                    return ComponentDecision.ACCEPT;
                } else {
                    if (checkReachOne(subsetGraph, ecc, graph, leafSCC)) {
                        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_DONE_ACCEPT);
                        return ComponentDecision.ACCEPT;                        
                    } else {
                        undecidedSeen = true;
                    }
                }
            } else if (decision == ComponentDecision.UNDECIDED) {
                undecidedSeen = true;
            }
        }
        if (undecidedSeen) {
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_DONE_UNDECIDED);
            return ComponentDecision.UNDECIDED;
        } else {
            log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_BREAKPOINT_DONE_REJECT);
            return ComponentDecision.REJECT;
        }
    }

    private ComponentDecision decideComponentBreakpointMCLeaf(
            GraphExplicit graph, BitSet leafSCC) throws EPMCException {
        boolean accepting = false;
        boolean rejecting = false;
        NodeProperty automatonLabel = graph.getNodeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
            graph.queryNode(node);
            AutomatonBreakpointLabel label = automatonLabel.getObject();
            if (label.isAccepting()) {
                accepting = true;                
            } else if (label.isRejecting()) {
                rejecting = true;
            }
        }
        if (accepting) {
            return ComponentDecision.ACCEPT;
        } else if (rejecting) {
            return ComponentDecision.REJECT;
        } else {
            return ComponentDecision.UNDECIDED;
        }
    }

    private ComponentDecision decideComponentBreakpointMDPLeaf(
            GraphExplicit graph, BitSet leafSCC) throws EPMCException {
        BitSet existing = UtilBitSet.newBitSetUnbounded();

        NodeProperty automatonLabel = graph.getNodeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
            graph.queryNode(node);
            AutomatonBreakpointLabel label = automatonLabel.getObject();
            if (label.isAccepting()) {
                return ComponentDecision.ACCEPT;
            }
            if (!label.isRejecting()) {
                existing.set(node);
            }
        }
        
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        components.removeLeavingAttr(graph, existing);
        if (existing.cardinality() == 0) {
            return ComponentDecision.REJECT;
        } else {
            return ComponentDecision.UNDECIDED;
        }
    }

    private ComponentDecision decideComponentRabin(GraphExplicit subsetProd,
            BitSet scc, boolean bscc) throws EPMCException {
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_RABIN);
        AutomatonSubset subOb = ValueObject.asObject(subsetProd.getGraphProperty(CommonProperties.AUTOMATON)).getObject();
        Buechi buechi = subOb.getBuechi();
        int pNode = scc.nextSetBit(0);
        subsetProd.queryNode(pNode);
        NodeProperty nodeModel = subsetProd.getNodeProperty(CommonProperties.NODE_MODEL);
        int modelState = nodeModel.getInt();
        NodeProperty nodeAutomaton = subsetProd.getNodeProperty(CommonProperties.NODE_AUTOMATON);
        AutomatonSubsetState subsetState = nodeAutomaton.getObject();
        AutomatonRabin rabin = UtilAutomaton.newAutomatonRabinSafra(buechi, subsetState.getStates());
        ProductGraphExplicit prodGraph = new ProductGraphExplicit.Builder()
        		.setModel(graph)
        		.setModelInitialNode(modelState)
        		.setAutomaton(rabin)
        		.addGraphProperties(graph.getGraphProperties())
        		.addNodeProperty(CommonProperties.PLAYER)
        		.addNodeProperty(CommonProperties.STATE)
        		.addEdgeProperty(CommonProperties.WEIGHT)
        		.build();
        GraphExplicitWrapper graph = new GraphExplicitWrapper(prodGraph);
        graph.addDerivedGraphProperties(prodGraph.getGraphProperties());
        graph.addDerivedNodeProperty(CommonProperties.STATE);
        graph.addDerivedNodeProperty(CommonProperties.PLAYER);
        graph.addDerivedNodeProperty(CommonProperties.AUTOMATON_LABEL);
        graph.addDerivedNodeProperty(CommonProperties.NODE_AUTOMATON);
        graph.addDerivedNodeProperty(CommonProperties.NODE_MODEL);
        graph.explore();
        log.send(MessagesLTLLazy.LTL_LAZY_EXPLORING_STATE_SPACE_DONE, graph.computeNumStates(),
                rabin.getNumStates());
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        EndComponents endComponents = components
                .maximalEndComponents(graph);
        for (BitSet leafSCC = endComponents.next(); leafSCC != null; leafSCC = endComponents.next()) {
            ComponentDecision decision;
            if (!nonDet) {
                decision = decideComponentRabinMCLeaf(graph, leafSCC);
            } else {
                decision = decideComponentRabinMDPLeaf(graph, leafSCC);
            }
            if (decision == ComponentDecision.ACCEPT) {
                if (bscc) {
                    log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_RABIN_DONE_ACCEPT);
                    return ComponentDecision.ACCEPT;
                } else {
                    if (checkReachOne(subsetProd, scc, graph, leafSCC)) {
                        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_RABIN_DONE_ACCEPT);
                        return ComponentDecision.ACCEPT;                        
                    }
                }
            }
        }
        log.send(MessagesLTLLazy.LTL_LAZY_DECIDING_RABIN_DONE_REJECT);
        return ComponentDecision.REJECT;
    }

    private ComponentDecision decideComponentRabinMCLeaf(GraphExplicit graph,
            BitSet leafSCC) throws EPMCException {
        AutomatonRabin rabin = ValueObject.asObject(graph.getGraphProperty(CommonProperties.AUTOMATON)).getObject();
        NodeProperty automatonLabel = graph.getNodeProperty(CommonProperties.AUTOMATON_LABEL);
        BitSet accepting = UtilBitSet.newBitSetUnbounded(rabin.getNumPairs());
        BitSet stable = UtilBitSet.newBitSetUnbounded(rabin.getNumPairs());
        stable.set(0, rabin.getNumPairs());
        for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
            graph.queryNode(node);
            AutomatonRabinLabel label = automatonLabel.getObject();
            stable.and(label.getStable());
            accepting.or(label.getAccepting());
        }
        accepting.and(stable);
        if (accepting.cardinality() > 0) {
            return ComponentDecision.ACCEPT;
        } else {
            return ComponentDecision.REJECT;
        }
    }

    private ComponentDecision decideComponentRabinMDPLeaf(GraphExplicit graph,
            BitSet leafSCC) throws EPMCException {
        AutomatonRabin rabin = ValueObject.asObject(graph.getGraphProperty(CommonProperties.AUTOMATON)).getObject();
        boolean accepting = false;
        NodeProperty automatonLabel = graph.getNodeProperty(CommonProperties.AUTOMATON_LABEL);
        for (int label = 0; label < rabin.getNumPairs(); label++) {
            BitSet existing = UtilBitSet.newBitSetUnbounded();
            for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
                graph.queryNode(node);
                AutomatonRabinLabel rabinLabel = automatonLabel.getObject();
                BitSet stable = rabinLabel.getStable();
                if (stable.get(label)) {
                    existing.set(node);
                }
            }
            ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
            components.removeLeavingAttr(graph, existing);
            if (existing.cardinality() > 0) {
                for (int node = leafSCC.nextSetBit(0); node >= 0; node = leafSCC.nextSetBit(node+1)) {
                    if (existing.get(node)) {
                        graph.queryNode(node);
                        AutomatonRabinLabel rabinLabel = automatonLabel.getObject();
                        BitSet accSet = rabinLabel.getAccepting();
                        if (accSet.get(label)) {
                            accepting = true;
                            break;
                        }
                    }
                }
            }
            if (accepting) {
                break;
            }
        }
        if (accepting) {
            return ComponentDecision.ACCEPT;
        } else {
            return ComponentDecision.REJECT;
        }
    }

	@Override
	public void setProperty(Expression property) {
		this.property = property;
		if (property instanceof ExpressionQuantifier) {
			this.propertyQuantifier = (ExpressionQuantifier) property;
		}
	}

	@Override
	public void setForStates(StateSet forStates) {
		if (forStates != null && (forStates instanceof StateSetExplicit)) {
			this.forStates = (StateSetExplicit) forStates;
		}
	}

    @Override
    public StateMap solve() throws EPMCException {
        assert property != null;
        assert forStates != null;
        Expression quantifiedProp = propertyQuantifier.getQuantified();
        Set<Expression> inners = UtilLTL.collectLTLInner(quantifiedProp);
        StateSet allStates = UtilGraph.computeAllStatesExplicit(modelChecker.getLowLevel());
        for (Expression inner : inners) {
            StateMapExplicit innerResult = (StateMapExplicit) modelChecker.check(inner, allStates);
            UtilGraph.registerResult(graph, inner, innerResult);
        }
        allStates.close();
        DirType dirType = ExpressionQuantifier.computeQuantifierDirType(propertyQuantifier);
        boolean min = dirType == DirType.MIN;
        StateMap result = doSolve(quantifiedProp, forStates, min);
        if (propertyQuantifier.getCompareType() != CmpType.IS) {
            StateMap compare = modelChecker.check(propertyQuantifier.getCompare(), forStates);
            Operator op = propertyQuantifier.getCompareType().asExOpType(contextValue);
            result = result.applyWith(op, compare);
        }
        return result;
    }
    
    private StateMap doSolve(Expression property, StateSet forStates, boolean min)
            throws EPMCException {
        this.forStates = (StateSetExplicit) forStates;
        Semantics type = graph.getGraphPropertyObject(CommonProperties.SEMANTICS);
        if (!SemanticsNonDet.isNonDet(type)) {
            min = false;
        }
        this.negate = min;
        ValueBoolean negate = TypeBoolean.get(contextValue).newValue(this.negate);
        Expression[] expressions = UtilLTL.collectLTLInner(property).toArray(new Expression[0]);
        Buechi buechi = UtilAutomaton.newBuechi(property, expressions, nonDet, negate);
        StateMapExplicit innerResult;
        if (options.getBoolean(OptionsLTLLazy.LTL_LAZY_INCREMENTAL)) {
            innerResult = checkTemporalLTLIncremental(buechi);
        } else {
            innerResult = checkTemporalLTLNonIncremental(buechi);
        }
        
        if (negate.getBoolean()) {
            ValueAlgebra entry = TypeAlgebra.asAlgebra(innerResult.getType()).newValue();
            for (int i = 0; i < innerResult.size(); i++) {
                innerResult.getExplicitIthValue(entry, i);
                entry.subtract(TypeAlgebra.asAlgebra(innerResult.getType()).getOne(), entry);
                innerResult.setExplicitIthValue(entry, i);
            }
        }
        
        return innerResult;
    }
    
    private boolean isBSCC(GraphExplicit graph, BitSet ecc) throws EPMCException {
        boolean isBSCC = true;
        if (nonDet) {
            for (int node = ecc.nextSetBit(0); node >= 0; node = ecc.nextSetBit(node+1)) {
                graph.queryNode(node);
                for (int succNr = 0; succNr < graph.getNumSuccessors(); succNr++) {
                    int succ = graph.getSuccessorNode(succNr);
                    if (!ecc.get(succ)) {
                        isBSCC = false;
                        break;
                    }
                }
            }
        }
        return isBSCC;
    }

    // check whether there is node of ecc which can reach node from
    // leafSCC with probability one (max probability)
    private boolean checkReachOne(GraphExplicit prodWrapperGraph,
            BitSet subsetECC, GraphExplicit leafGraph, BitSet leafSCC)
                    throws EPMCException {
        ProductGraphExplicit prodGraph = prodWrapperGraph.getGraphPropertyObject(CommonProperties.INNER_GRAPH);
        AutomatonSubset subset = ValueObject.asObject(prodGraph.getGraphProperty(CommonProperties.AUTOMATON)).getObject();
        ComponentsExplicit components = UtilAlgorithms.newComponentsExplicit();
        BitSet reachOne = components.reachMaxOne(leafGraph, leafSCC);
        NodeProperty nodeModel = leafGraph.getNodeProperty(CommonProperties.NODE_MODEL);
        NodeProperty nodeAutomaton = leafGraph.getNodeProperty(CommonProperties.NODE_AUTOMATON);
        for (int node = reachOne.nextSetBit(0); node >= 0; node = reachOne.nextSetBit(node+1)) {
            leafGraph.queryNode(node);
            int modelState = nodeModel.getInt();
            AutomatonStateBuechi autState = nodeAutomaton.getObject();
            BitSet states = autState.getStates();
            int subState = subset.getState(states);
            int chkProdNode = prodGraph.combineToNode(modelState, subState);
            if (subsetECC.get(chkProdNode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canHandle() throws EPMCException {
        assert property != null;
        if (!(modelChecker.getEngine() instanceof EngineExplicit)) {
            return false;
        }
        Semantics semantics = modelChecker.getModel().getSemantics();
        if (!SemanticsMarkovChain.isMarkovChain(semantics) && !SemanticsMDP.isMDP(semantics)) {
        	return false;
        }
        if (!(property instanceof ExpressionQuantifier)) {
            return false;
        }
        if (propertyQuantifier.getQuantified() instanceof ExpressionReward) {
            return false;
        }
        if (!TypeReal.isReal(TypeWeight.get(modelChecker.getModel().getContextValue()))) {
        	return false;
        }
        Set<Expression> inners = UtilLTL.collectLTLInner(propertyQuantifier.getQuantified());
        StateSet allStates = UtilGraph.computeAllStatesExplicit(modelChecker.getLowLevel());
        for (Expression inner : inners) {
            modelChecker.ensureCanHandle(inner, allStates);
        }
        if (allStates != null) {
        	allStates.close();
        }
        return true;
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
        Set<Expression> inners = UtilLTL.collectLTLInner(propertyQuantifier.getQuantified());
        StateSet allStates = UtilGraph.computeAllStatesExplicit(modelChecker.getLowLevel());
        for (Expression inner : inners) {
            required.addAll(modelChecker.getRequiredNodeProperties(inner, allStates));
        }
    	return Collections.unmodifiableSet(required);
    }
    
    @Override
    public Set<Object> getRequiredEdgeProperties() throws EPMCException {
    	Set<Object> required = new LinkedHashSet<>();
    	required.add(CommonProperties.WEIGHT);
    	return Collections.unmodifiableSet(required);
    }
    
    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
}
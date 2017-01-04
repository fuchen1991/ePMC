package epmc.lumping.lumpingexplicitsignature;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import epmc.error.EPMCException;
import epmc.graph.CommonProperties;
import epmc.graph.Semantics;
import epmc.graph.SemanticsCTMC;
import epmc.graph.explicit.EdgeProperty;
import epmc.graph.explicit.GraphExplicit;
import epmc.graph.explicit.GraphExplicitSparse;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicit;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitLump;
import epmc.graphsolver.objective.GraphSolverObjectiveExplicitUnboundedReachability;
import epmc.value.TypeAlgebra;
import epmc.value.TypeWeight;
import epmc.value.UtilValue;
import epmc.value.Value;
import epmc.value.ValueAlgebra;
import epmc.value.ValueArray;

public final class EquivalenceWeakCTMC implements Equivalence {
    private GraphExplicit original;
    private int[] successorsFromTo;
    private int[] successorStates;
    private ValueArray successorWeights;
    private int[] predecessorsFromTo;
    private int[] predecessorStates;
    private Map<Signature,TIntList> signatureToStates = new TreeMap<>();
    private TIntIntMap blockToNumber = new TIntIntHashMap(10, 0.5f, -1, -1);
    private boolean[] blocksSeen;
    private int[] blocksArr;
    private Value weight;
    private Signature cmpSignature;
    private int maxOrigFanout;
    private final List<int[]> newBlocks = new ArrayList<>();
	private GraphSolverObjectiveExplicit objective;

    @Override
    public void setSuccessorsFromTo(int[] successorsFromTo) {
        assert successorsFromTo != null;
        this.successorsFromTo = successorsFromTo;
    }

    @Override
    public void setSuccessorStates(int[] successorStates) {
        assert successorStates != null;
        this.successorStates = successorStates;
    }

    @Override
    public void setSuccessorWeights(ValueArray weights) {
        assert weights != null;
        this.successorWeights = weights;
    }

    @Override
    public void setPrecessorsFromTo(int[] predecessorsFromTo) {
        assert predecessorsFromTo != null;
        this.predecessorsFromTo = predecessorsFromTo;
    }

    @Override
    public void setPrecessorStates(int[] predecessorStates) {
        assert predecessorStates != null;
        this.predecessorStates = predecessorStates;
    }

    @Override
    public void prepare() throws EPMCException {
        assert successorsFromTo != null;
        assert successorStates != null;
        assert successorWeights != null;
        assert predecessorsFromTo != null;
        assert predecessorStates != null;

        this.blocksSeen = new boolean[successorsFromTo.length - 1];
        this.blocksArr = new int[successorsFromTo.length - 1];
        this.weight = successorWeights.getType().getEntryType().newValue();
        computeCmpSignature();
    }
    
    @Override
	public void prepareInitialPartition(int[] partition) {
    }
    
    @Override
    public List<int[]> splitBlock(int[] block, int[] partition)
            throws EPMCException {
        newBlocks.clear();
        signatureToStates.clear();
        int blockSize = block.length;
        for (int i = 0; i < blockSize; i++) {
            computeSignature(block[i], partition);
        }
        newBlocks.clear();
        for (TIntList intList : signatureToStates.values()) {
            newBlocks.add(intList.toArray());
        }
        return newBlocks;
    }
    
    private void computeSignature(int node, int[] stateToBlock) throws EPMCException {
        blockToNumber.clear();
        int size = 0;
        int from = successorsFromTo[node];
        int to = successorsFromTo[node + 1];
        for (int succNr = from; succNr < to; succNr++) {
            int succState = successorStates[succNr];
            int block = stateToBlock[succState];
            if (node != succState && !blocksSeen[block]) {
                blocksSeen[block] = true;
                blocksArr[size] = block;
                size++;
            }
        }
        for (int succNr = from; succNr < to; succNr++) {
            int succState = successorStates[succNr];
            if (node != succState) {
                int block = stateToBlock[succState];
                blocksSeen[block] = false;
            }
        }
        
        Arrays.sort(blocksArr, 0, size);
        cmpSignature.size = size;
        for (int i = 0; i < size; i++) {
            int block = blocksArr[i];
            blockToNumber.put(block, i);
            cmpSignature.blocks[i] = block;
            cmpSignature.values[i].set(0);
        }
        
        for (int succNr = from; succNr < to; succNr++) {
            int succState = successorStates[succNr];
            if (node != succState) {
                int block = stateToBlock[succState];
                int blockNumber = blockToNumber.get(block);
                successorWeights.get(weight, succNr);
                cmpSignature.values[blockNumber].add(cmpSignature.values[blockNumber], weight);
            }
        }
        TIntList states = signatureToStates.get(cmpSignature);
        if (states == null) {
            states = new TIntArrayList();
            signatureToStates.put(cloneSignature(cmpSignature), states);
        }
        states.add(node);
    }
    
    private Signature cloneSignature(Signature signature) {
        assert signature != null;
        Signature clone = new Signature();
        clone.size = signature.size;
        clone.blocks = Arrays.copyOf(signature.blocks, signature.size);
        clone.values = new ValueAlgebra[signature.size];
        for (int i = 0; i < signature.size; i++) {
            clone.values[i] = UtilValue.clone(signature.values[i]);
        }
        return clone;
    }
    
    private void computeCmpSignature() throws EPMCException {
        this.maxOrigFanout = 0;
        int numStates = successorsFromTo.length - 1;
        for (int state = 0; state < numStates; state++) {
            int numSuccStates = successorsFromTo[state + 1] - successorsFromTo[state];
            maxOrigFanout = Math.max(maxOrigFanout, numSuccStates);
        }
        
        TypeAlgebra typeWeight = TypeWeight.get(successorWeights.getType().getContext());

        Signature cmpSignature = new Signature();
        cmpSignature.size = maxOrigFanout;
        cmpSignature.blocks = new int[maxOrigFanout];
        cmpSignature.values = new ValueAlgebra[maxOrigFanout];
        for (int i = 0; i < maxOrigFanout; i++) {
            cmpSignature.values[i] = typeWeight.newValue();
        }
        this.cmpSignature = cmpSignature;        
    }
    
    @Override
    public GraphExplicit computeQuotient(int[] originalToQuotientState, List<int[]> blocks) throws EPMCException {
        GraphExplicit quotient;
        int numStates = blocks.size();
        int numTotalOut = 0;
        blockToNumber.clear();
        int[] clearArr = new int[maxOrigFanout];
        int numBlocks = blocks.size();
        for (int j = 0; j < numBlocks; j++) {
            int[] block = blocks.get(j);
            int representant = block[0];
            int from = successorsFromTo[representant];
            int to = successorsFromTo[representant + 1];
            int fanout = 0;
            for (int succNr = from; succNr < to; succNr++) {
                int succState = successorStates[succNr];
                int succRepresentant = originalToQuotientState[succState];
                if (!blocksSeen[succRepresentant]) {
                    clearArr[fanout] = succRepresentant;
                    fanout++;
                    blocksSeen[succRepresentant] = true;
                }
            }
            for (int i = 0; i < fanout; i++) {
                int succRepresentant = clearArr[i];
                blocksSeen[succRepresentant] = false;
            }
            numTotalOut += fanout;
        }
        ValueAlgebra[] quotWeightsArr = new ValueAlgebra[maxOrigFanout];
        int[] quotSuccStatesArr = new int[maxOrigFanout];
        TypeAlgebra typeWeight = TypeWeight.get(original.getContextValue());
        quotient = new GraphExplicitSparse(original.getContextValue(), false, numStates, numTotalOut);
        EdgeProperty quotWeight = quotient.addSettableEdgeProperty(CommonProperties.WEIGHT, typeWeight);
        for (int i = 0; i < quotWeightsArr.length; i++) {
            quotWeightsArr[i] = typeWeight.newValue();
        }
        int quotState = 0;
        for (int j = 0; j < numBlocks; j++) {
            int[] block = blocks.get(j);
            int representant = block[0];
            int origFrom = successorsFromTo[representant];
            int origTo = successorsFromTo[representant + 1];
            int numQuotSucc = 0;
            for (int succNr = origFrom; succNr < origTo; succNr++) {
                int succState = successorStates[succNr];
                int succRepresentant = originalToQuotientState[succState];
                if (!blocksSeen[succRepresentant]) {
                    blockToNumber.put(succRepresentant, numQuotSucc);
                    numQuotSucc++;
                    blocksSeen[succRepresentant] = true;
                }
            }            
            for (int succNr = origFrom; succNr < origTo; succNr++) {
                int succState = successorStates[succNr];
                int succRepresentant = originalToQuotientState[succState];
                blocksSeen[succRepresentant] = false;
            }
            for (int i = 0; i < numQuotSucc; i++) {
                quotWeightsArr[i].set(0);
            }
            for (int succNr = origFrom; succNr < origTo; succNr++) {
                int succState = successorStates[succNr];
                int succRepresentant = originalToQuotientState[succState];
                int blockNr = blockToNumber.get(succRepresentant);
                ValueAlgebra blockValue = quotWeightsArr[blockNr];
                successorWeights.get(weight, succNr);
                blockValue.add(blockValue, weight);
                quotSuccStatesArr[blockNr] = succRepresentant;
            }
            quotient.queryNode(quotState);
            quotient.prepareNode(numQuotSucc);
            for (int i = 0; i < numQuotSucc; i++) {
                quotient.setSuccessorNode(i, quotSuccStatesArr[i]);
                quotWeight.set(quotWeightsArr[i], i);
            }
            
            quotState++;
        }
        return quotient;
    }
    
	@Override
	public void setObjective(GraphSolverObjectiveExplicit objective) {
		this.objective = objective;
		this.original = objective.getGraph();
	}
    
	@Override
	public boolean canHandle() {
		Semantics semantics = objective.getGraph().getGraphPropertyObject(CommonProperties.SEMANTICS);
		if (!SemanticsCTMC.isCTMC(semantics)) {
			return false;
		}
		if (!(objective instanceof GraphSolverObjectiveExplicitLump)
				&& !(objective instanceof GraphSolverObjectiveExplicitUnboundedReachability)) {
			return false;
		}
		return true;
	}
}
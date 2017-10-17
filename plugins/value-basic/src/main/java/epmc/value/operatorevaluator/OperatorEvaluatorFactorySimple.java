package epmc.value.operatorevaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import epmc.value.ContextValue;
import epmc.value.Operator;
import epmc.value.OperatorEvaluator;
import epmc.value.OperatorEvaluatorFactory;
import epmc.value.Type;

public final class OperatorEvaluatorFactorySimple implements OperatorEvaluatorFactory {
    private final static Map<ContextValue,OperatorEvaluatorFactorySimple> MAP = new HashMap<>();
    
    public final static OperatorEvaluatorFactorySimple get() {
        OperatorEvaluatorFactorySimple result = MAP.get(ContextValue.get());
        if (result != null) {
            return result;
        }
        result = new OperatorEvaluatorFactorySimple();
        MAP.put(ContextValue.get(), result);
        return result;
    }
    
    private final List<Class<? extends OperatorEvaluatorSimpleBuilder>> evaluators = new ArrayList<>();

    public void addEvaluator(Class<? extends OperatorEvaluatorSimpleBuilder> clazz) {
        assert clazz != null;
        evaluators.add(clazz);
    }
    
    @Override
    public OperatorEvaluator getEvaluator(Operator operator, Type... types) {
        for (Class<? extends OperatorEvaluatorSimpleBuilder> clazz : evaluators) {
            try {
                OperatorEvaluatorSimpleBuilder builder = clazz.newInstance();
                builder.setOperator(operator);
                builder.setTypes(types);
                OperatorEvaluator evaluator = builder.build();
                if (evaluator != null) {
                    return evaluator;
                }
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

}

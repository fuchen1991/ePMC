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

package epmc.prism.exporter.processor;

import static epmc.error.UtilError.ensure;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import epmc.expression.Expression;
import epmc.jani.model.Action;
import epmc.jani.model.Automaton;
import epmc.jani.model.Constant;
import epmc.jani.model.InitialStates;
import epmc.jani.model.Location;
import epmc.jani.model.ModelJANIProcessor;
import epmc.jani.model.Variable;
import epmc.prism.exporter.error.ProblemsPRISMExporter;
import epmc.time.TypeClock;

/**
 * Class that is responsible for registering the JANI components; the transient variables are considered as corresponding to rewards.
 * 
 * @author Andrea Turrini
 *
 */
public class JANIComponentRegistrar {

    private static final Collection<String> reservedWords;
    static {
        Set<String> reservedWordsMutable = new HashSet<>();
        reservedWordsMutable.add("A");
        reservedWordsMutable.add("bool");
        reservedWordsMutable.add("clock");
        reservedWordsMutable.add("const");
        reservedWordsMutable.add("ctmc");
        reservedWordsMutable.add("C");
        reservedWordsMutable.add("double");
        reservedWordsMutable.add("dtmc");
        reservedWordsMutable.add("E");
        reservedWordsMutable.add("endinit");
        reservedWordsMutable.add("endinvariant");
        reservedWordsMutable.add("endmodule");
        reservedWordsMutable.add("endrewards");
        reservedWordsMutable.add("endsystem");
        reservedWordsMutable.add("false");
        reservedWordsMutable.add("formula");
        reservedWordsMutable.add("filter");
        reservedWordsMutable.add("func");
        reservedWordsMutable.add("F");
        reservedWordsMutable.add("global");
        reservedWordsMutable.add("G");
        reservedWordsMutable.add("init");
        reservedWordsMutable.add("invariant");
        reservedWordsMutable.add("I");
        reservedWordsMutable.add("int");
        reservedWordsMutable.add("label");
        reservedWordsMutable.add("max");
        reservedWordsMutable.add("mdp");
        reservedWordsMutable.add("min");
        reservedWordsMutable.add("module");
        reservedWordsMutable.add("X");
        reservedWordsMutable.add("nondeterministic");
        reservedWordsMutable.add("Pmax");
        reservedWordsMutable.add("Pmin");
        reservedWordsMutable.add("P");
        reservedWordsMutable.add("probabilistic");
        reservedWordsMutable.add("prob");
        reservedWordsMutable.add("pta");
        reservedWordsMutable.add("rate");
        reservedWordsMutable.add("rewards");
        reservedWordsMutable.add("Rmax");
        reservedWordsMutable.add("Rmin");
        reservedWordsMutable.add("R");
        reservedWordsMutable.add("S");
        reservedWordsMutable.add("stochastic");
        reservedWordsMutable.add("system");
        reservedWordsMutable.add("true");
        reservedWordsMutable.add("U");
        reservedWordsMutable.add("W");
        reservedWords = Collections.unmodifiableCollection(reservedWordsMutable);
        reset();
    }

    private static Set<String> usedNames;
    private static Map<Constant, String> constantNames;
    private static Map<String, String> constantNameNames;

    private static Set<Variable> globalVariables;

    private static Map<Variable, String> variableNames;
    private static Map<String, Variable> variableByName;
    private static Map<String, String> variableNameNames;
    private static Map<Variable, Map<Action, Expression>> rewardTransitionExpressions;
    private static Map<Variable, Expression> rewardStateExpressions;

    private static Map<Variable, Automaton> variablesAssignedByAutomaton;
    private static Map<Automaton, Set<Variable>> automatonAssignsVariables;

    private static Map<Action, String> actionNames;

    private static Map<Automaton, String> automatonLocationName;
    private static Map<Automaton, Map<Location, Integer>> automatonLocationIdentifier;

    private static Automaton defaultAutomatonForUnassignedClocks;
    private static Set<Variable> unassignedClockVariables;

    private static InitialStates initialStates;
    private static Map<Automaton, Collection<Location>> initialLocations;
    private static Boolean usesInitialConditions;

    private static boolean isTimedModel;

    private static int reward_counter;
    private static int variable_counter;
    private static int action_counter;
    private static int constant_counter;
    private static int location_counter_name;
    private static int location_counter_id;

    static void reset() {
        usedNames = new HashSet<>(); 

        constantNames = new HashMap<>();
        constantNameNames = new HashMap<>();

        globalVariables  = new HashSet<>();
        variableNames = new HashMap<>();
        variableByName = new HashMap<>();
        variableNameNames = new HashMap<>();

        rewardTransitionExpressions = new HashMap<>();
        rewardStateExpressions = new HashMap<>();
        variablesAssignedByAutomaton = new HashMap<>();
        automatonAssignsVariables = new HashMap<>();
        actionNames = new HashMap<>();

        automatonLocationIdentifier = new HashMap<>();
        automatonLocationName = new HashMap<>();

        initialStates = null;
        initialLocations = new HashMap<>();
        usesInitialConditions = null;

        defaultAutomatonForUnassignedClocks = null;
        unassignedClockVariables = new HashSet<>();

        isTimedModel = false;
        reward_counter = 0;
        variable_counter = 0;
        action_counter = 0;
        constant_counter = 0;
        location_counter_name = 0;
        location_counter_id = 0;
    }

    public static void setIsTimedModel(boolean isTimedModel) {
        JANIComponentRegistrar.isTimedModel = isTimedModel;
    }

    public static boolean isTimedModel() {
        return isTimedModel;
    }

    public static void setDefaultAutomatonForUnassignedClocks(Automaton defaultAutomatonForUnassignedClocks) {
        assert defaultAutomatonForUnassignedClocks != null;

        JANIComponentRegistrar.defaultAutomatonForUnassignedClocks = defaultAutomatonForUnassignedClocks;
    }

    public static Automaton getDefaultAutomatonForUnassignedClocks() {
        return defaultAutomatonForUnassignedClocks;
    }

    public static Set<Variable> getUnassignedClockVariables() {
        return Collections.unmodifiableSet(unassignedClockVariables);
    }

    /**
     * Register a constant.
     * 
     * @param constant the constant to register
     */
    public static void registerConstant(Constant constant) {
        assert constant != null;

        ensure(!constantNames.containsKey(constant), ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_CONSTANT_DEFINED_TWICE, constant.getName());
        if (!constantNames.containsKey(constant)) {
            String name;
            name = constant.getName();
            if (! name.matches("^[A-Za-z_][A-Za-z0-9_]*$") || reservedWords.contains(name)) {
                name = "constant_" + constant_counter;
            }
            while (usedNames.contains(name)) {
                name = "constant_" + constant_counter;
                constant_counter++;
            }
            usedNames.add(name);
            constantNames.put(constant, name);
            constantNameNames.put(constant.getName(), name);
        }
    }

    /**
     * Register a location.
     * 
     * @param automaton the the automaton the location belongs to
     * @param location the location to register
     */
    public static void registerLocation(Automaton automaton, Location location) {
        assert automaton != null;
        assert location != null;

        Map<Location, Integer> mapLI = automatonLocationIdentifier.get(automaton);
        if (mapLI == null) {
            mapLI = new HashMap<>();
            automatonLocationIdentifier.put(automaton, mapLI);
        }
        ensure(!mapLI.containsKey(location), ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_LOCATION_DEFINED_TWICE, location.getName());
        mapLI.put(location, location_counter_id++);

        String name = "location";
        while (usedNames.contains(name)) {
            name = "location_" + location_counter_name;
            location_counter_name++;
        }
        usedNames.add(name);
        automatonLocationName.put(automaton, name);
    }

    public static String getLocationName(Automaton automaton) {
        assert automatonLocationName.containsKey(automaton);

        return automatonLocationName.get(automaton);
    }

    public static Integer getLocationIdentifier(Automaton automaton, Location location) {
        assert automatonLocationIdentifier.containsKey(automaton);

        Map<Location, Integer> mapLI = automatonLocationIdentifier.get(automaton);
        assert mapLI != null && mapLI.containsKey(location);

        return mapLI.get(location);
    }

    public static Range getLocationRange(Automaton automaton) {
        assert automatonLocationIdentifier.containsKey(automaton);

        Map<Location, Integer> mapLI = automatonLocationIdentifier.get(automaton);
        assert !mapLI.isEmpty();

        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;

        for (int value : mapLI.values()) {
            if (low > value) {
                low = value;
            }
            if (high < value) {
                high = value;
            }
        }

        assert low <= high;

        return new Range(low, high);
    }

    /**
     * Register a variable.
     * 
     * @param variable the variable to register
     */
    public static void registerVariable(Variable variable) {
        assert variable != null;

        ensure(!variableNames.containsKey(variable), ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_VARIABLE_DEFINED_TWICE, variable.getName());
        if (!variableNames.containsKey(variable)) {
            variableByName.put(variable.getName(), variable);
            String name;
            if (variable.isTransient()) {
                do {
                    name = "\"reward_" + reward_counter + "\"";
                    reward_counter++;
                } while (usedNames.contains(name));
            } else {
                name = variable.getName();
                if (! name.matches("^[A-Za-z_][A-Za-z0-9_]*$") || reservedWords.contains(name)) {
                    name = "variable_" + variable_counter;
                }
                while (usedNames.contains(name)) {
                    name = "variable_" + variable_counter;
                    variable_counter++;
                }
            }
            usedNames.add(name);
            variableNames.put(variable, name);
            variableNameNames.put(variable.getName(), name);
        }
    }

    /**
     * Return the unique name for the variable respecting the PRISM syntax
     * 
     * @param variable the wanted variable
     * @return the associated name or {@code null} if such a variable is unknown
     */
    public static String getVariableNameByVariable(Variable variable) {
        assert variable != null;

        return variableNames.get(variable);
    }

    /**
     * Return the unique name for the variable respecting the PRISM syntax
     * 
     * @param name the wanted name
     * @return the associated name or {@code null} if such a variable is unknown
     */
    public static String getVariableNameByName(String name) {
        assert name != null;

        return variableNameNames.get(name);
    }

    /**
     * Return the variable for the given name
     * 
     * @param name the wanted name
     * @return the associated variable or {@code null} if such a name is unknown
     */
    public static Variable getVariableByName(String name) {
        assert name != null;

        return variableByName.get(name);
    }

    /**
     * Register a variable as global whenever the variable is not assigned by an automaton.
     * Make sure to first register the variables assigned by automata.
     * 
     * Note that a variable is not registered if it is transient.
     * 
     * @param variable the variable to register
     */
    public static void registerGlobalVariable(Variable variable) {
        assert variable != null;

        if (variable.isTransient()) {
            return;
        }

        if (variable.getType().toType() instanceof TypeClock && !variablesAssignedByAutomaton.containsKey(variable)) {
            unassignedClockVariables.add(variable);
        } else { 
            if (!variablesAssignedByAutomaton.containsKey(variable)) {
                globalVariables.add(variable);
            }
        }
    }

    public static Set<Variable> getGlobalVariables() {
        return Collections.unmodifiableSet(globalVariables);
    }

    /**
     * Register a new expression for the given reward and action 
     * 
     * @param reward the reward structure
     * @param action the action the expression refers to
     * @param expression the expression
     */
    public static void registerTransitionRewardExpression(Variable reward, Action action, Expression expression) {
        assert reward != null;
        assert action != null;
        assert expression != null;
        assert reward.isTransient();

        ensure(variableNames.containsKey(reward), 
                ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_UNDEFINED_USED_VARIABLE, 
                reward.getName());

        Map<Action, Expression> mapAE = rewardTransitionExpressions.get(reward);
        if (mapAE == null) {
            mapAE = new HashMap<>();
            rewardTransitionExpressions.put(reward, mapAE);
        }
        Expression oldAssgn = mapAE.get(action);
        if (oldAssgn == null) {
            mapAE.put(action, expression);
        } else {
            ensure(expression.equals(oldAssgn), 
                    ProblemsPRISMExporter.PRISM_EXPORTER_UNSUPPORTED_FEATURE_TRANSIENT_VARIABLE_DIFFERENT_EXPRESSIONS, 
                    getVariableNameByVariable(reward));
        }
    }

    /**
     * Register a new expression for the given reward 
     * 
     * @param reward the reward structure
     * @param expression the expression
     */
    public static void registerStateRewardExpression(Variable reward, Expression expression) {
        assert reward != null;
        assert expression != null;
        assert reward.isTransient();

        ensure(variableNames.containsKey(reward), 
                ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_UNDEFINED_USED_VARIABLE, 
                "Variable used but not declared:", 
                reward.getName());

        Expression oldExp = rewardStateExpressions.get(reward);
        if (oldExp == null) {
            rewardStateExpressions.put(reward, expression);
        } else {
            ensure(expression.equals(oldExp), 
                    ProblemsPRISMExporter.PRISM_EXPORTER_UNSUPPORTED_FEATURE_TRANSIENT_VARIABLE_DIFFERENT_EXPRESSIONS, 
                    getVariableNameByVariable(reward));
        }
    }

    /**
     * Register a new automaton for the given action 
     * 
     * @param variable the variable
     * @param automaton the automaton
     */
    public static void registerNonTransientVariableAssignment(Variable variable, Automaton automaton) {
        assert variable != null;
        assert automaton != null;

        if (variable.isTransient()) {
            return;
        }

        Automaton oldAut = variablesAssignedByAutomaton.get(variable);
        if (oldAut == null) {
            variablesAssignedByAutomaton.put(variable, automaton);
        } else {
            ensure(automaton.equals(oldAut), 
                    ProblemsPRISMExporter.PRISM_EXPORTER_UNSUPPORTED_FEATURE_VARIABLE_ASSIGNED_MULTIPLE_AUTOMATA, 
                    getVariableNameByVariable(variable));
        }

        Set<Variable> assignedVariables = automatonAssignsVariables.get(automaton);
        if (assignedVariables == null) {
            assignedVariables = new HashSet<>();
            automatonAssignsVariables.put(automaton, assignedVariables);
        }
        assignedVariables.add(variable);
    }

    public static Set<Variable> getAssignedVariablesOrEmpty(Automaton automaton) {
        assert automaton != null;

        Set<Variable> assignedVariables = automatonAssignsVariables.get(automaton);
        if (assignedVariables == null) {
            assignedVariables = new HashSet<>();
        }

        return Collections.unmodifiableSet(assignedVariables);
    }

    public static String toPRISMRewards() {
        StringBuilder prism = new StringBuilder();
        JANI2PRISMProcessorStrict processor; 

        Expression expression;
        Action action;

        boolean remaining = false;
        for (Entry<Variable, String> entry: variableNames.entrySet()) {
            Variable reward = entry.getKey();
            String name = entry.getValue();
            if (!reward.isTransient()) {
                continue;
            }
            if (remaining) {
                prism.append("\n");
            } else {
                remaining = true;
            }
            prism.append("// Original variable name: ").append(reward.getName()).append("\n")
            .append("// New name: ").append(name).append("\n");
            prism.append("rewards ").append(name).append("\n");
            expression = rewardStateExpressions.get(reward);
            if (expression != null) {
                processor = ProcessorRegistrar.getProcessor(expression);
                prism.append(ModelJANIProcessor.INDENT).append("true : ").append(processor.toPRISM().toString()).append(";\n");
            }
            Map<Action, Expression> mapAA = rewardTransitionExpressions.get(reward);
            if (mapAA != null) {
                for(Entry<Action, Expression> entryAA : mapAA.entrySet()) {
                    action = entryAA.getKey();
                    processor = ProcessorRegistrar.getProcessor(action);
                    prism.append(ModelJANIProcessor.INDENT).append(processor.toPRISM().toString()).append(" true : ");

                    expression = entryAA.getValue();
                    processor = ProcessorRegistrar.getProcessor(expression);
                    prism.append(processor.toPRISM().toString()).append(";\n");

                }
            }
            prism.append("endrewards\n");
        }

        return prism.toString();
    }

    /**
     * Register an action.
     * 
     * @param action the variable to register
     */
    public static void registerAction(Action action) {
        if (!actionNames.containsKey(action)) {
            String name;
            //TODO: manage the case the variable name contains unexpected characters
            if (isSilentAction(action)) {
                name = "";
            } else {
                name = action.getName();
            }
            if (! name.matches("^[A-Za-z_][A-Za-z0-9_]*$") || reservedWords.contains(name)) {
                name = "action_" + action_counter;
            }
            while (usedNames.contains(name)) {
                name = "action_" + action_counter;
                action_counter++;
            }
            usedNames.add(name);
            actionNames.put(action, name);
        }
    }

    /**
     * Return the unique name for the action respecting the PRISM syntax
     * 
     * @param action the wanted action
     * @return the associated name
     */
    public static String getActionName(Action action) {
        assert action != null;
        ensure(actionNames.containsKey(action), ProblemsPRISMExporter.PRISM_EXPORTER_ERROR_UNDEFINED_USED_ACTION, action.getName());

        return actionNames.get(action);
    }

    public static boolean isSilentAction(Action action) {
        assert action != null;

        return "τ".equals(action.getName());
    }

    public static String constantsRenaming() {
        StringBuilder sb = new StringBuilder();

        for (Entry<Constant,String> entry : constantNames.entrySet()) {
            Constant constant = entry.getKey();
            String name = entry.getValue();
            if (! constant.getName().equals(name)) {
                sb.append("// Original constant name: ").append(constant.getName()).append("\n")
                .append("// New name: ").append(name).append("\n\n");
            }
        }

        return sb.toString();		
    }

    public static String globalVariablesRenaming() {
        StringBuilder sb = new StringBuilder();

        for (Entry<Variable,String> entry : variableNames.entrySet()) {
            Variable variable = entry.getKey();
            if (globalVariables.contains(variable)) {
                String name = entry.getValue();
                if (! variable.getName().equals(name)) {
                    sb.append("// Original variable name: ").append(variable.getName()).append("\n")
                    .append("// New name: ").append(name).append("\n\n");
                }
            }
        }

        return sb.toString();		
    }

    public static String variableRenaming(Variable variable, String prefix) {
        StringBuilder sb = new StringBuilder();

        String name = variableNames.get(variable);
        if (! variable.getName().equals(name)) {
            sb.append(prefix).append("// Original variable name: ").append(variable.getName()).append("\n")
            .append(prefix).append("// New name: ").append(name).append("\n\n");
        }

        return sb.toString();		
    }

    public static String actionsRenaming() {
        StringBuilder sb = new StringBuilder();

        for (Entry<Action,String> entry : actionNames.entrySet()) {
            Action action = entry.getKey();
            String name = entry.getValue();
            if (! action.getName().equals(name)) {
                sb.append("// Original action name: ").append(action.getName()).append("\n")
                .append("// New name: ").append(name).append("\n\n");
            }
        }

        return sb.toString();
    }

    public static String locationRenaming(Automaton automaton) {
        StringBuilder sb = new StringBuilder();
        assert automatonLocationIdentifier.containsKey(automaton);

        if (automatonLocationIdentifier.get(automaton).size() > 1) {
            String locationName = automatonLocationName.get(automaton);
            for (Entry<Location, Integer> entry : automatonLocationIdentifier.get(automaton).entrySet()) {
                sb.append("// Original location: ").append(entry.getKey().getName()).append("\n")
                .append("// Condition: ").append(locationName).append(" = ").append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    public static void registerInitialRestriction(InitialStates initialStates) {
        JANIComponentRegistrar.initialStates = initialStates;
        if (usesInitialConditions == null) {
            usesInitialConditions = new Boolean(initialStates != null);
        } else {
            usesInitialConditions |= initialStates != null;
        }
    }

    public static void registerInitialLocation(Automaton automaton, Collection<Location> locations) {
        assert automaton != null;
        assert locations != null;

        initialLocations.put(automaton, locations);
        if (usesInitialConditions == null) {
            usesInitialConditions = new Boolean(locations.size() > 1);
        } else {
            usesInitialConditions |= locations.size() > 1;
        }
    }

    public static boolean areInitialConditionsUsed() {
        assert usesInitialConditions != null;

        return usesInitialConditions.booleanValue();
    }

    public static String processInitialConditions() {
        assert usesInitialConditions != null;

        StringBuilder prism = new StringBuilder();
        JANI2PRISMProcessorStrict processor; 

        if (usesInitialConditions) {
            prism.append("init\n").append(ModelJANIProcessor.INDENT);
            boolean addAnd = false;

            for (Entry<Automaton, Collection<Location>> entry : initialLocations.entrySet()) {
                Automaton automaton = entry.getKey();
                if (entry.getValue().size() > 1) {
                    String locationName = JANIComponentRegistrar.getLocationName(automaton);
                    boolean remaining = false;
                    prism.append("(");
                    for (Location location : entry.getValue()) {
                        if (remaining) {
                            prism.append("|");
                        } else {
                            remaining = true;
                        }
                        prism.append("(")
                        .append(locationName)
                        .append("=")
                        .append(JANIComponentRegistrar.getLocationIdentifier(automaton, location))
                        .append(")");
                    }
                    prism.append(")");
                    addAnd = true;
                }
            }
            if (initialStates != null) {
                if (addAnd) {
                    prism.append("\n")
                    .append(ModelJANIProcessor.INDENT)
                    .append("&\n")
                    .append(ModelJANIProcessor.INDENT);
                }
                String comment = initialStates.getComment();
                if (comment != null) {
                    prism.append("// ").append(comment).append("\n").append(ModelJANIProcessor.INDENT);
                }
                Expression exp = initialStates.getExp(); 
                processor = ProcessorRegistrar.getProcessor(exp);
                prism.append(processor.toPRISM().toString());
            }	

            prism.append("\nendinit\n");
        }
        return prism.toString();
    }
}

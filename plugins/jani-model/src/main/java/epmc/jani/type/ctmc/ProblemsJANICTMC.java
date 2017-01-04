package epmc.jani.type.ctmc;

import epmc.error.Problem;
import epmc.error.UtilError;

/**
 * Class collecting problems potentially occurring in CTMC part of JANI plugin.
 * 
 * @author Ernst Moritz Hahn
 */
public final class ProblemsJANICTMC {
	/** Base name of resource file containing plugin problem descriptions. */
    private final static String PROBLEMS_JANI_CTMC = "ProblemsJANICTMC";
    
	public static final Problem JANI_CTMC_EDGE_REQUIRES_RATE = newProblem("jani-ctmc-edge-requires-rate");
	/** Multi-transition remain even though disallowed. */
	public static final Problem JANI_CTMC_DISALLOWED_MULTI_TRANSITIONS = newProblem("jani-ctmc-disallowed-multi-transitions");
	/** Time progress conditions are disallowed in CTMCs. */
	public static final Problem JANI_CTMC_DISALLOWED_TIME_PROGRESSES = newProblem("jani-ctmc-disallowed-time-progresses");
	
	/**
	 * Create new problem object using plugin resource file.
	 * The name parameter must not be {@code null}.
	 * 
	 * @param name problem identifier String
	 * @return newly created problem identifier
	 */
    private static Problem newProblem(String name) {
    	assert name != null;
    	return UtilError.newProblem(PROBLEMS_JANI_CTMC, name);
    }

    /**
     * Private constructor to prevent instantiation of this class.
     */
    private ProblemsJANICTMC() {
    }
}
package epmc.main.options;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.CaseFormat;

import epmc.messages.UtilMessages;
import epmc.modelchecker.UtilModelChecker;
import epmc.options.OptionTypeBoolean;
import epmc.options.OptionTypeInteger;
import epmc.options.OptionTypeString;
import epmc.options.OptionTypeStringList;
import epmc.options.Options;
import epmc.options.UtilOptions;
import epmc.plugin.UtilPlugin;

/**
 * Static auxiliary methods for working with EPMC-specific options.
 * This is in contrast to {@link UtilOptions}, which provides general utility
 * methods for working with program options not specific to EPMC.
 * 
 * @author Ernst Moritz Hahn
 */
public final class UtilOptionsEPMC {
    /** User-readable string indicating that we want to have filename there. */
    private final static String FILE = "file";
    /** Default RMI server name. */
    private final static String DEFAULT_SERVER_NAME = "EPMC";
    /** Default RMI server port. */
    private final static String DEFAULT_SERVER_PORT = "43333";

    /**
     * Create new {@link Options} with EPMC-specific options.
     * 
     * @return new {@link Options} with EPMC-specific options.
     */
    public static Options newOptions() {
        String defaultResourceBundleString = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, OptionsEPMC.OPTIONS_EPMC.name());
        Options options = new Options(defaultResourceBundleString);
        prepareOptions(options);
        return options;
    }
    
    /**
     * Adds options specific to EPMC to options.
     * This does not include options from plugins.
     * 
     * @param options options to add EPMC-specific options to
     */
    public static void prepareOptions(Options options) {
        assert options != null;
        OptionTypeString typeString = OptionTypeString.getInstance();
        OptionTypeInteger typeInteger = OptionTypeInteger.getInstance();
        OptionTypeBoolean typeBoolean = OptionTypeBoolean.getInstance();
        OptionTypeStringList typeFileList = new OptionTypeStringList(FILE);
        
        options.addOption().setIdentifier(OptionsEPMC.MODEL_INPUT_FILES)
            .setType(typeFileList).setCommandLine().build();
        options.addOption().setIdentifier(OptionsEPMC.PROPERTY_INPUT_FILES)
            .setType(typeFileList).setCommandLine().build();
        options.addOption().setIdentifier(OptionsEPMC.PORT)
            .setType(typeInteger).setDefault(DEFAULT_SERVER_PORT)
            .setCommandLine().build();
        options.addOption().setIdentifier(OptionsEPMC.SERVER_NAME)
            .setType(typeString).setDefault(DEFAULT_SERVER_NAME)
            .setCommandLine().setGui().build();
        options.addOption().setIdentifier(OptionsEPMC.PRINT_STACKTRACE)
            .setType(typeBoolean).setDefault(false).build();

        Map<String,Class<?>> commands = new HashMap<>();
        String commandTaskClassString = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, OptionsEPMC.COMMAND_CLASS.name());
        options.set(commandTaskClassString, commands);

        UtilMessages.addOptions(options);
        UtilModelChecker.addOptions(options);
        UtilPlugin.addOptions(options);
    }

    /**
     * Private constructor to prevent instantiation of this class.
     */
    private UtilOptionsEPMC() {
    }    
}
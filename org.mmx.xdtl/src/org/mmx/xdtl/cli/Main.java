package org.mmx.xdtl.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.mmx.xdtl.conf.XdtlModule;
import org.mmx.xdtl.log.XdtlLogger;
import org.mmx.xdtl.log.XdtlMdc;
import org.mmx.xdtl.model.XdtlException;
import org.mmx.xdtl.runtime.Engine;
import org.mmx.xdtl.runtime.impl.PackageLoader;
import org.mmx.xdtl.runtime.util.Urls;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class Main {
    private static final Logger logger = XdtlLogger.getLogger("xdtl.main");
    private static final String GLOBALS_NAME = "globals.xml";
    private static final String CONFIG_NAME = "xdtlrt.xml";

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(-1);
        }

        try {
            HashMap<String, Object> optionsMap = createArgumentMap(args, 0, "-");
            int numOptions = optionsMap.size();
            if (args.length <= numOptions) {
                usage();
                System.exit(-1);
            }

            boolean validate = optionsMap.remove("v") != null;
            boolean debug = optionsMap.remove("d") != null;

            HashMap<String, Object> cmdLineGlobals = new HashMap<String, Object>();
            HashMap<String, Object> cmdLineArgs = new HashMap<String, Object>();
            parseCommandLineTail(args, numOptions + 1, cmdLineGlobals, cmdLineArgs);

            createArgumentMap(args, numOptions + 1, null);
            initMDC(cmdLineGlobals);

            String homeDir = initXdtlHomeDir(optionsMap);
            Properties conf = loadProperties(homeDir, CONFIG_NAME);
            conf.putAll(optionsMap);

            Injector injector = Guice.createInjector(new XdtlModule(conf, debug));

            String taskUrl = args[numOptions];

            if (validate) {
                validate(injector, taskUrl);
                logger.info("validated");
            } else {
                Map<String,Object> globals = loadGlobals(homeDir);
                globals.putAll(cmdLineGlobals);

                String rtmDir = System.getenv("XDTL_RTM_DIR");
                if (rtmDir != null) {
                    globals.put("xdtlHome", rtmDir);
                }
                injector.getInstance(Engine.class).run(taskUrl, cmdLineArgs, globals);
                logger.info("done");
            }

            System.exit(0);
        } catch (Throwable t) {
            logError(t);
            System.exit(-1);
        }
    }

    private static void validate(Injector injector, String taskUrl) throws Exception {
        PackageLoader packageLoader = injector.getInstance(PackageLoader.class);
        packageLoader.loadPackage(Urls.toURL("file:"), Urls.removeRef(taskUrl));
    }

    private static void logError(Throwable t) {
        if (t instanceof XdtlException) {
            XdtlException e = (XdtlException) t;
            if (e.isLogged()) return;

            XdtlMdc.setState("", e.getSourceLocator());
        }

        String msg = t.getMessage();
        if (msg == null || msg.length() == 0) {
            Throwable cause = t.getCause();
            if (cause != null) {
                t = cause;
            }
        }

        if (logger.isTraceEnabled()) {
            logger.error("Execution failed", t);
        } else {
            logger.error(t.getMessage());
        }
    }

    private static void initMDC(HashMap<String, Object> argMap) {
        for (Entry<String,Object> entry: argMap.entrySet()) {
            MDC.put(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Map<String, Object> loadGlobals(String homeDir) throws Exception {
        return (Map) loadProperties(homeDir, GLOBALS_NAME);
    }

    private static Properties loadProperties(String homeDir, String name) throws Exception {
        Properties defaultprops = loadPropertiesFromClasspath("/" + name);
        Properties userprops = loadPropertiesFromFile(homeDir + name);
        defaultprops.putAll(userprops);
        return defaultprops;
    }

    private static String initXdtlHomeDir(HashMap<String, Object> optionsMap) {
        String homeDir = (String) optionsMap.get("home");
        if (homeDir == null) {
            homeDir = System.getProperty("user.home") + "/.xdtl/";
            optionsMap.put("home", homeDir);
        } else if (!homeDir.endsWith("/")) {
            homeDir += "/";
            optionsMap.put("home", homeDir);
        }

        return homeDir;
    }

    private static Properties loadPropertiesFromFile(String fileName) throws IOException,
            InvalidPropertiesFormatException {
        Properties props = new Properties();
        File file = new File(fileName);
        if (!file.exists()) {
            return props;
        }

        return loadPropertiesFromStream(props, new FileInputStream(file));
    }

    private static Properties loadPropertiesFromClasspath(String name)
            throws IOException, InvalidPropertiesFormatException {
        Properties props = new Properties();
        return loadPropertiesFromStream(props, Main.class.getResourceAsStream(name));
    }

    private static Properties loadPropertiesFromStream(Properties props, InputStream is)
            throws IOException, InvalidPropertiesFormatException {
        if (is != null) {
            try {
                props.loadFromXML(is);
            } finally {
                close(is);
            }
        }
        return props;
    }

    private static void parseCommandLineTail(String[] args, int start,
            Map<String, Object> globals, Map<String, Object> argMap) {
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-P")) {
                addMapEntry(argMap, arg.substring(2, arg.length()));
            }

            if (arg.startsWith("-")) {
                if (arg.length() == 1) {
                    throw new RuntimeException("Unexpected '-'");
                }

                char c = arg.charAt(1);
                if (c == 'P') {
                    addMapEntry(argMap, arg.substring(2, arg.length()));
                } else {
                    throw new RuntimeException("Invalid option '-" + c);
                }
            } else {
                addMapEntry(globals, arg);
            }
        }
    }

    private static void addMapEntry(Map<String, Object> map, String arg) {
        int pos = arg.indexOf('=');

        if (pos == -1) {
            throw new RuntimeException("'=' expected in argument '" + arg + "'");
        }

        if (pos == 0) {
            throw new RuntimeException("name is missing from argument '" + arg + "'");
        }

        String name = arg.substring(0, pos);
        String value;

        if (pos == arg.length() - 1) {
            value = "";
        } else {
            value = arg.substring(pos + 1, arg.length());
        }

        map.put(name, value);
    }

    private static HashMap<String, Object> createArgumentMap(String[] args, int start, String prefix)
            throws Exception {

        if (args.length < 1) {
            return null;
        }

        HashMap<String, Object> map = new HashMap<String, Object>();

        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (prefix != null) {
                if (!arg.startsWith(prefix)) return map;
                arg = arg.substring(prefix.length());
            }

            if (arg.equals("v") || arg.equals("d")) {
                map.put(arg, "");
            } else {
                addMapEntry(map, arg);
            }
        }

        return map;
    }

    private static void close(InputStream is) {
        try {
            is.close();
        } catch (IOException e) {
            logger.warn("Failed to close input stream", e);
        }
    }

    private static void usage() {
        System.out.println("Usage: xdtlrt [-option=val ...] <task url> [global=val -Pparam=val ...]");
    }
}

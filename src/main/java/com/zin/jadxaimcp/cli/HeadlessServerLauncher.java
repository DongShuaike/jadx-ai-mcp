package com.zin.jadxaimcp.cli;

import com.zin.jadxaimcp.JadxAIMCP;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Headless launcher for cloud servers without desktop sessions.
 *
 * This launcher does not use jadx-cli's main method, so it avoids forced
 * System.exit() and can keep the MCP plugin process alive.
 */
public final class HeadlessServerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessServerLauncher.class);

    private static final String PROP_HOST = "jadx.ai.mcp.host";
    private static final String PROP_PORT = "jadx.ai.mcp.port";
    private static final String PROP_REMOTE_MODE = "jadx.ai.mcp.remote_mode";
    private static final String PROP_HEADLESS_MODE = "jadx.ai.mcp.headless_mode";
    private static final String PROP_KEEP_ALIVE = "jadx.ai.mcp.keep_alive";

    private HeadlessServerLauncher() {
    }

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Argument error: " + e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }
        if (options == null) {
            return;
        }

        applySystemProperties(options);

        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setInputFiles(options.inputs);
        jadxArgs.setThreadsCount(options.threadsCount);
        // This process is used as an MCP service, not a file exporter.
        jadxArgs.setSkipFilesSave(true);

        JadxDecompiler decompiler = new JadxDecompiler(jadxArgs);
        decompiler.registerPlugin(new JadxAIMCP());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                decompiler.close();
            } catch (Exception e) {
                logger.warn("Error closing JadxDecompiler during shutdown: {}", e.getMessage());
            }
        }, "jadx-ai-mcp-shutdown"));

        try {
            logger.info("Headless launcher loading inputs: {}", options.inputs);
            decompiler.load();
            logger.info("Headless launcher loaded classes: {}", decompiler.getClassesWithInners().size());
            logger.info("Headless MCP service is running. Press Ctrl+C to stop.");

            // Keep process alive for MCP requests.
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Headless launcher failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void applySystemProperties(LaunchOptions options) {
        System.setProperty(PROP_HEADLESS_MODE, "true");
        System.setProperty(PROP_KEEP_ALIVE, String.valueOf(options.keepAlive));
        if (options.host != null) {
            System.setProperty(PROP_HOST, options.host);
        }
        if (options.port != null) {
            System.setProperty(PROP_PORT, String.valueOf(options.port));
        }
        if (options.remoteMode != null) {
            System.setProperty(PROP_REMOTE_MODE, String.valueOf(options.remoteMode));
        }
    }

    private static LaunchOptions parseArgs(String[] args) {
        if (args.length == 0) {
            printUsage();
            return null;
        }

        List<File> inputs = new ArrayList<>();
        String host = null;
        Integer port = null;
        Boolean remoteMode = null;
        boolean keepAlive = true;
        int threadsCount = JadxArgs.DEFAULT_THREADS_COUNT;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    printUsage();
                    return null;
                case "--host":
                    host = nextArg(args, ++i, "--host");
                    break;
                case "--port":
                    port = Integer.parseInt(nextArg(args, ++i, "--port"));
                    break;
                case "--remote-mode":
                    remoteMode = Boolean.parseBoolean(nextArg(args, ++i, "--remote-mode"));
                    break;
                case "--keep-alive":
                    keepAlive = Boolean.parseBoolean(nextArg(args, ++i, "--keep-alive"));
                    break;
                case "--threads":
                    threadsCount = Integer.parseInt(nextArg(args, ++i, "--threads"));
                    break;
                default:
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                    inputs.add(new File(arg));
                    break;
            }
        }

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one input file is required.");
        }

        return new LaunchOptions(inputs, host, port, remoteMode, keepAlive, threadsCount);
    }

    private static String nextArg(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for option: " + optionName);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java ... com.zin.jadxaimcp.cli.HeadlessServerLauncher [options] <input.apk|dex|jar> ...");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host <host>             Bind host for plugin server (default from plugin config/env)");
        System.out.println("  --port <port>             Bind port for plugin server (default from plugin config/env)");
        System.out.println("  --remote-mode <true|false> Enable/disable token auth mode");
        System.out.println("  --keep-alive <true|false> Keep process alive for MCP requests (default: true)");
        System.out.println("  --threads <count>         Jadx processing threads");
        System.out.println("  -h, --help                Show this help");
    }

    private static final class LaunchOptions {
        private final List<File> inputs;
        private final String host;
        private final Integer port;
        private final Boolean remoteMode;
        private final boolean keepAlive;
        private final int threadsCount;

        private LaunchOptions(List<File> inputs, String host, Integer port, Boolean remoteMode,
                boolean keepAlive, int threadsCount) {
            this.inputs = inputs;
            this.host = host;
            this.port = port;
            this.remoteMode = remoteMode;
            this.keepAlive = keepAlive;
            this.threadsCount = threadsCount;
        }
    }
}

package com.zin.jadxaimcp.server;

import io.javalin.Javalin;
import jadx.gui.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zin.jadxaimcp.utils.JadxAIMCPBanner;
import com.zin.jadxaimcp.utils.PaginationUtils;
import com.zin.jadxaimcp.server.routes.*; // MCP tool call's request handlers

public class PluginServer {
    private static final Logger logger = LoggerFactory.getLogger(PluginServer.class);
    private final MainWindow mainWindow;
    private final int port;
    private Javalin app;
    private final PaginationUtils paginationUtils;
    private volatile boolean isRunning = false;

    /**
     * @param mainWindows - The main Jadx window context
     * @param port        - The port to listen on
     */
    public PluginServer(MainWindow mainWindow, int port) {
        this.mainWindow = mainWindow;
        this.port = port;
        this.paginationUtils = new PaginationUtils();
    }

    /**
     * Starts the Javalin server and registers all routes
     */
    public void start() {
        try {
            // Configure and start Javalin
            app = Javalin.create(config -> {
                config.showJavalinBanner = false;
            }).start(port);

            // Register all route handlers
            registerRoutes();

            isRunning = true;

            // Log startup success and banner
            logger.info(JadxAIMCPBanner.banner);
            logger.info("// -------------------- JADX AI MCP PLUGIN -------------------- //");
            logger.info("JADX AI MCP Plugin HTTP Server Started at http://127.0.0.1:" + port + "/");
        
        } catch (Exception e) {
            logger.error("JADX-AI-MCP Plugin Error: Could not start HTTP Server. Exception: " + e.getMessage(), e);
            isRunning = false;
            // Re-throw to let the main plugin know startup failed
            throw new RuntimeException("Failed to start Javalin Server", e);
        }
    }

    /**
     * Stops the Javalin server gracefully
     */
    public void stop() {
        if (app != null) {
            try {
                app.stop();
                logger.info("JADX-AI-MCP Plugin: HTTP Server Stopped");
            } catch (Exception e) {
                logger.error("JADX-AI-MCP Plugin Error: Error during shutdown: " + e.getMessage(), e);
            } finally {
                app = null;
                isRunning = false;
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    /**
     * Central method to register all API endpoints
     * Groups related endpoints into specific Route Handler classes
     */
    private void registerRoutes() {
        // Instantiate Route Controllers
        // Passing 'mainWindow' and 'paginationUtils' to them so they can do their work
        GeneralRoutes generalRoutes = new GeneralRoutes(mainWindow, port, this);
        ClassRoutes classRoutes = new ClassRoutes(mainWindow, paginationUtils);
        SearchRoutes searchRoutes = new SearchRoutes(mainWindow, paginationUtils);
        ResourceRoutes resourceRoutes = new ResourceRoutes(mainWindow);
        RefactoringRoutes refactoringRoutes = new RefactoringRoutes(mainWindow);
        DebugRoutes debugRoutes = new DebugRoutes(mainWindow);
        XrefsRoutes analysisRoutes = new XrefsRoutes(mainWindow);

        // --- General & Health ---
        app.get("/health", generalRoutes::handleHealth);

        // --- Class & Code Navigation ---
        app.get("/current-class", classRoutes::handleCurrentClass);
        app.get("/all-classes", classRoutes::handleAllClasses);
        app.get("/selected-text", classRoutes::handleSelectedText);
        app.get("/class-source", classRoutes::handleClassSource);
        app.get("/smali-of-class", classRoutes::handleSmaliOfClass);
        app.get("/methods-of-class", classRoutes::handleMethodsOfClass);
        app.get("/fields-of-class", classRoutes::handleFieldsOfClass);

        // --- Search & Lookup ---
        app.get("/method-by-name", searchRoutes::handleMethodByName);
        app.get("/search-method", searchRoutes::handleSearchMethod);
        app.get("/search-classes-by-keyword", searchRoutes::handleSearchClassesByKeyword);

        // --- Analysis (Xrefs) ---
        app.get("/xrefs-to-class", analysisRoutes::handleXrefsToClass);
        app.get("/xrefs-to-method", analysisRoutes::handleXrefsToMethod);
        app.get("/xrefs-to-field", analysisRoutes::handleXrefsToField);

        // --- Resources & Manifest ---
        app.get("/manifest", resourceRoutes::handleManifest);
        app.get("/strings", resourceRoutes::handleStrings);
        app.get("/list-all-resource-files-names", resourceRoutes::handleListAllResourceFilesNames);
        app.get("/get-resource-file", resourceRoutes::handleGetResourceFile);
        app.get("/main-application-classes-code", resourceRoutes::handleMainApplicationClassesCode);
        app.get("/main-application-classes-names", resourceRoutes::handleMainApplicationClassesNames);
        app.get("/main-activity", resourceRoutes::handleMainActivity);

        // --- Refactoring (Renaming) ---
        app.get("/rename-class", refactoringRoutes::handleRenameClass);
        app.get("/rename-method", refactoringRoutes::handleRenameMethod);
        app.get("/rename-field", refactoringRoutes::handleRenameField);
        app.get("/rename-package", refactoringRoutes::handleRenamePackage);

        // --- Debugging ---
        app.get("/debug/stack-frames", debugRoutes::handleGetStackFrames);
        app.get("/debug/variables", debugRoutes::handleGetVariables);
        app.get("/debug/threads", debugRoutes::handleGetThreads);        
    }

}
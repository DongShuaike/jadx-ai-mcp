package jadx.gui.plugins;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.ResourceFile;
import jadx.api.ResourceType;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.utils.android.AppAttribute;
import jadx.core.utils.android.ApplicationParams;
import jadx.core.xmlgen.ResContainer;
import jadx.gui.JadxWrapper;
import jadx.gui.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class JadxAIMCP implements JadxPlugin {
    private MainWindow mainWindow;
    private final Gson gson = new Gson();
    public static final String PLUGIN_ID = "jadx-ai-mcp";

    @Override
    public void init(JadxPluginContext context) {
        // First check if we're in a GUI context - if not, exit gracefully
        if (context.getGuiContext() == null) {
            System.out.println("Plugin: Running in non-GUI mode, plugin features disabled.");
            return;
        }

        try {
            // Now safe to use GUI context
            this.mainWindow = (MainWindow) context.getGuiContext().getMainFrame();
            if (this.mainWindow == null) {
                System.err.println("Plugin: Main window is null. Plugin will not start.");
                return;
            }

            System.out.println("MCP HTTP Plugin: Starting HTTP server...");
            this.start(mainWindow);
        } catch (Exception e) {
            System.err.println("Plugin: Initialization error: " + e.getMessage());
            // Don't throw exceptions that will crash JADX
        }
    }


    /*
    @Override
    public void init(JadxPluginContext context) {
        if (context.getGuiContext() == null) {
            return;
        } else {
            this.mainWindow = (MainWindow) context.getGuiContext().getMainFrame();
        }
        System.out.println("MCP HTTP Plugin: Starting HTTP server...");
        this.start(mainWindow);
        //start();
        //new JadxAIPlugin(mainWindow).start();
    }
    */
    // Add this public no-argument constructor
    public JadxAIMCP() {
        // Empty constructor
    }

    //public JadxAIPlugin(MainWindow mainWindow) {
      //  this.mainWindow = mainWindow;
    //}

    public void start(MainWindow mainWindow) {

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8650), 0);
            server.createContext("/current-class", new CurrentClassHandler());
            server.createContext("/all-classes", new AllClassesHandler());
            server.createContext("/selected-text", new SelectedTextHandler());
            server.createContext("/method-by-name", new MethodByNameHandler());
            server.createContext("/class-source", new ClassSourceHandler());
            server.createContext("/search-method", new SearchMethodHandler());
            server.createContext("/methods-of-class", new MethodsOfClassHandler());
            server.createContext("/fields-of-class", new FieldsOfClassHandler());
            server.createContext("/smali-of-class", new SmaliOfClassHandler());
            server.createContext("/manifest", new ManifestHandler());
            server.createContext("/main-application", new MainApplicationHandler());
            server.createContext("/main-activity", new MainActivityHandler());

            server.setExecutor(null);
            server.start();
            System.out.println("JADX MCP plugin HTTP server started at http://127.0.0.1:8650/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public JadxPluginInfo getPluginInfo() {
        return JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
                .name("JADX-AI MCP Plugin")
                .description("Integrates MCP Server support for JADX")
                .homepage("https://github.com/zinja-coder/jadx-ai-mcp")
                .requiredJadxVersion("1.5.1, r2333")
                .build();

                /*new JadxPluginInfo(
                "jax-ai-mcp",
                "JADX-AI MCP Integration",
                "Provides MCP support to JADX",
                "1.0.5",
                "Zinja Coder");*/
    }

    class CurrentClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String className = getSelectedTabTitle();
            String code = extractTextFromCurrentTab();

            Map<String, Object> result = new HashMap<>();
            result.put("name", className != null ? className.replace(".java", "") : "unknown");
            result.put("type", "code/java");
            result.put("content", code != null ? code : "");

            sendJson(exchange, 200, result);
        }
    }


    class AllClassesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<String> classList = new ArrayList<>();

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();

                List<JavaClass> classes = wrapper.getIncludedClassesWithInners();
                for (JavaClass cls : classes) {
                    classList.add(cls.getFullName());
                }

                Map<String, Object> result = new HashMap<>();
                result.put("type", "class-list");
                result.put("count", classList.size());
                result.put("classes", classList);

                sendJson(exchange, 200, result);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Failed to load class list."));
            }
        }
    }

    class SelectedTextHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JTextArea textArea = findTextArea(mainWindow.getTabbedPane().getSelectedComponent());
            String selectedText = textArea != null ? textArea.getSelectedText() : null;

            Map<String, String> result = new HashMap<>();
            result.put("selectedText", selectedText != null ? selectedText : "");
            sendJson(exchange, 200, result);
        }
    }

    class MethodByNameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String methodName = params.get("method");

            if (methodName == null || methodName.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "Missing 'method' parameter"));
                return;
            }

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                if (wrapper == null) {
                    sendJson(exchange, 500, Map.of("error", "JadxWrapper not initialized"));
                    return;
                }

                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    for (jadx.api.JavaMethod method : cls.getMethods()) {
                        if (method.getName().equalsIgnoreCase(methodName)) {
                            String codeStr;
                            try {
                                codeStr = method.getCodeStr();
                            } catch (Exception e) {
                                codeStr = "Error retrieving code: " + e.getMessage();
                            }

                            Map<String, Object> result = new HashMap<>();
                            result.put("class", cls.getFullName());
                            result.put("method", method.getName());
                            result.put("decl", String.valueOf(method.getCodeNodeRef()));
                            result.put("code", codeStr);
                            sendJson(exchange, 200, result);
                            return;
                        }
                    }
                }

                sendJson(exchange, 404, Map.of("error", "Method not found in any class."));
            } catch (Exception e) {
                e.printStackTrace(); // Also log this to your IDE or terminal
                sendJson(exchange, 500, Map.of("error", "Internal error: " + e.getMessage()));
            }
        }
    }



    class ClassSourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String className = params.get("class");

            if (className == null || className.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "Missing 'class' parameter."));
                return;
            }

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    if (cls.getFullName().equals(className)) {
                        sendJson(exchange, 200, Map.of(
                                "class", className,
                                "type", "code/java",
                                "content", cls.getCode()
                        ));
                        return;
                    }
                }
                sendJson(exchange, 404, Map.of("error", "Class not found."));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Internal error retrieving class source."));
            }
        }
    }


    class SearchMethodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String methodName = params.get("method");
            List<String> results = new ArrayList<>();

            if (methodName == null) {
                sendJson(exchange, 400, Map.of("error", "Missing method parameter"));
                return;
            }

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    if (cls.getCode().contains(methodName)) {
                        results.add(cls.getFullName());
                    }
                }
                sendJson(exchange, 200, results);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Internal error during method search"));
            }
        }
    }


    class MethodsOfClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String className = params.get("class");

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    if (cls.getFullName().equals(className)) {
                        List<String> methods = new ArrayList<>();
                        for (JavaMethod m : cls.getMethods()) {
                            String s = m.getAccessFlags() + " " + m.getReturnType() + " " + m.getName() + m.getMethodNode() + m.getFullName();
                            methods.add(s);
                        }
                        sendJson(exchange, 200, methods);
                        return;
                    }
                }
                sendJson(exchange, 404, Map.of("error", "Class not found."));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Internal error retrieving methods."));
            }
        }
    }


    class FieldsOfClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String className = params.get("class");

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    if (cls.getFullName().equals(className)) {
                        List<String> fields = new ArrayList<>();
                        for (JavaField f : cls.getFields()) {
                            String s = f.getAccessFlags() + " " + f.getType() + " " + f.getName();
                            fields.add(s);
                        }
                        sendJson(exchange, 200, fields);
                        return;
                    }
                }
                sendJson(exchange, 404, Map.of("error", "Class not found."));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Internal error retrieving fields."));
            }
        }
    }


    class SmaliOfClassHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String className = params.get("class");

            if (className == null || className.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "Missing 'class' parameter."));
                return;
            }

            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                    if (cls.getFullName().equals(className)) {
                        sendJson(exchange, 200, Map.of(
                                "class", className,
                                "type", "code/smali",
                                "content", cls.getSmali()
                        ));
                        return;
                    }
                }
                sendJson(exchange, 404, Map.of("error", "Class not found."));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Internal error retrieving class source."));
            }
        }
    }

    class ManifestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                List<ResourceFile> resources = wrapper.getResources();

                ResourceFile manifest = AndroidManifestParser.getAndroidManifest(resources);
                if (manifest == null) {
                    sendJson(exchange, 404, Map.of("error", "AndroidManifest.xml not found."));
                    return;
                }

                ResContainer container = manifest.loadContent();
                String manifestContent = container.getText().getCodeStr();

                Map<String, Object> result = new HashMap<>();
                result.put("name", manifest.getOriginalName());
                result.put("type", "manifest/xml");
                result.put("content", manifestContent);

                sendJson(exchange, 200, result);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
            }
        }
    }

    class MainApplicationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                List<ResourceFile> resources = wrapper.getResources();

                AndroidManifestParser parser = new AndroidManifestParser(AndroidManifestParser.getAndroidManifest(resources), EnumSet.of(AppAttribute.APPLICATION), wrapper.getArgs().getSecurity());
                if (!parser.isManifestFound()) {
                    sendJson(exchange, 404, Map.of("error", "AndroidManifest.xml not found."));
                    return;
                }

                ApplicationParams results = parser.parse();
                if (results.getApplication() == null) {
                    sendJson(exchange, 404, Map.of("error", "Failed to get application from manifest"));
                    return;
                }

                JavaClass applicationClass = results.getApplicationJavaClass(wrapper.getDecompiler());

                if (applicationClass == null) {
                    sendJson(exchange, 404, Map.of("error", "Failed to get application class: " + results.getApplication()));
                    return;
                }

                Map<String, Object> result = new HashMap<>();
                result.put("name", applicationClass.getFullName());
                result.put("type", "code/java");
                result.put("content", applicationClass.getCode());

                sendJson(exchange, 200, result);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
            }
        }
    }

    class MainActivityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JadxWrapper wrapper = mainWindow.getWrapper();
                List<ResourceFile> resources = wrapper.getResources();

                AndroidManifestParser parser = new AndroidManifestParser(AndroidManifestParser.getAndroidManifest(resources), EnumSet.of(AppAttribute.MAIN_ACTIVITY), wrapper.getArgs().getSecurity());
                if (!parser.isManifestFound()) {
                    sendJson(exchange, 404, Map.of("error", "AndroidManifest.xml not found."));
                    return;
                }

                ApplicationParams results = parser.parse();
                if (results.getMainActivity() == null) {
                    sendJson(exchange, 404, Map.of("error", "Failed to get main activity from manifest"));
                    return;
                }

                JavaClass mainActivityClass = results.getMainActivityJavaClass(wrapper.getDecompiler());

                if (mainActivityClass == null) {
                    sendJson(exchange, 404, Map.of("error", "Failed to get activity class: " + results.getApplication()));
                    return;
                }

                Map<String, Object> result = new HashMap<>();
                result.put("name", mainActivityClass.getFullName());
                result.put("type", "code/java");
                result.put("content", mainActivityClass.getCode());

                sendJson(exchange, 200, result);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
            }
        }
    }


    // ----------------- Helpers ------------------

    private String getSelectedTabTitle() {
        JTabbedPane tabs = mainWindow.getTabbedPane();
        int index = tabs.getSelectedIndex();
        return (index != -1) ? tabs.getTitleAt(index) : null;
    }

    private String extractTextFromCurrentTab() {
        Component selectedComponent = mainWindow.getTabbedPane().getSelectedComponent();
        JTextArea textArea = findTextArea(selectedComponent);
        return textArea != null ? textArea.getText() : null;
    }

    private JTextArea findTextArea(Component component) {
        if (component instanceof JTextArea) return (JTextArea) component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JTextArea result = findTextArea(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object responseObj) throws IOException {
        String json = gson.toJson(responseObj);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> queryToMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                map.put(pair[0], java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

}
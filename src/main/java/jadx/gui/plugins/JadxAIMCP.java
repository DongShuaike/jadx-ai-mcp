package jadx.gui.plugins;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class JadxAIMCP implements JadxPlugin {
    private MainWindow mainWindow;
    private final Gson gson = new Gson();
    private Javalin app;
    public static final String PLUGIN_ID = "jadx-ai-mcp";
    private static final Logger logger = LoggerFactory.getLogger(JadxAIMCP.class);

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

            System.out.println("MCP Javalin Plugin: Starting HTTP server...");
            this.start(mainWindow);
        } catch (Exception e) {
            System.err.println("Plugin: Initialization error: " + e.getMessage());
            // Don't throw exceptions that will crash JADX
        }
    }

    // public no-argument constructor
    public JadxAIMCP() {
        // Empty constructor
    }

    public void start(MainWindow mainWindow) {
        try {
            app = Javalin.create().start(8650);

            // Setup routes
            app.get("/current-class", this::handleCurrentClass);
            app.get("/all-classes", this::handleAllClasses);
            app.get("/selected-text", this::handleSelectedText);
            app.get("/method-by-name", this::handleMethodByName);
            app.get("/class-source", this::handleClassSource);
            app.get("/search-method", this::handleSearchMethod);
            app.get("/methods-of-class", this::handleMethodsOfClass);
            app.get("/fields-of-class", this::handleFieldsOfClass);
            app.get("/smali-of-class", this::handleSmaliOfClass);
            app.get("/manifest", this::handleManifest);
            app.get("/main-application", this::handleMainApplication);
            app.get("/main-activity", this::handleMainActivity);

            logger.info("JADX MCP plugin HTTP server started at http://127.0.0.1:8650/");
        } catch (Exception e) {
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
    }

    private void handleCurrentClass(Context ctx) {
        try {
            String className = getSelectedTabTitle();
            String code = extractTextFromCurrentTab();

            Map<String, Object> result = new HashMap<>();
            result.put("name", className != null ? className.replace(".java", "") : "unknown");
            result.put("type", "code/java");
            result.put("content", code != null ? code : "");

            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error while trying to fetch current class: " + e.getMessage()));
        }
    }

    private void handleAllClasses(Context ctx) {
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

            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Failed to load class list: " + e.getMessage()));
        }
    }

    private void handleSelectedText(Context ctx) {
        try {
            JTextArea textArea = findTextArea(mainWindow.getTabbedPane().getSelectedComponent());
            String selectedText = textArea != null ? textArea.getSelectedText() : null;

            Map<String, String> result = new HashMap<>();
            result.put("selectedText", selectedText != null ? selectedText : "");
            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error while trying to fetch selected text: " + e.getMessage()));
        }
    }

    private void handleMethodByName(Context ctx) {
        String methodName = ctx.queryParam("method");

        if (methodName == null || methodName.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Missing 'method' parameter"));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            if (wrapper == null) {
                ctx.status(500).json(Map.of("error", "JadxWrapper not initialized"));
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
                        ctx.json(result);
                        return;
                    }
                }
            }

            ctx.status(404).json(Map.of("error", "Method not found in any class."));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error while trying to retrieve method code: " + e.getMessage()));
        }
    }

    private void handleClassSource(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Missing 'class' parameter."));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    ctx.json(Map.of(
                            "class", className,
                            "type", "code/java",
                            "content", cls.getCode()
                    ));
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found."));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving class source: " + e.getMessage()));
        }
    }

    private void handleSearchMethod(Context ctx) {
        String methodName = ctx.queryParam("method");
        List<String> results = new ArrayList<>();

        if (methodName == null) {
            ctx.status(400).json(Map.of("error", "Missing method parameter"));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getCode().contains(methodName)) {
                    results.add(cls.getFullName());
                }
            }
            ctx.json(results);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error during method search: " + e.getMessage()));
        }
    }

    private void handleMethodsOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> methods = new ArrayList<>();
                    for (JavaMethod m : cls.getMethods()) {
                        String s = m.getAccessFlags() + " " + m.getReturnType() + " " + m.getName() + m.getMethodNode() + m.getFullName();
                        methods.add(s);
                    }
                    ctx.json(methods);
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found."));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving methods: " + e.getMessage()));
        }
    }

    private void handleFieldsOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> fields = new ArrayList<>();
                    for (JavaField f : cls.getFields()) {
                        String s = f.getAccessFlags() + " " + f.getType() + " " + f.getName();
                        fields.add(s);
                    }
                    ctx.json(fields);
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found."));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving fields: " + e.getMessage()));
        }
    }

    private void handleSmaliOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Missing 'class' parameter."));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    ctx.json(Map.of(
                            "class", className,
                            "type", "code/smali",
                            "content", cls.getSmali()
                    ));
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found."));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving class source: " + e.getMessage()));
        }
    }

    private void handleManifest(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            ResourceFile manifest = AndroidManifestParser.getAndroidManifest(resources);
            if (manifest == null) {
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            ResContainer container = manifest.loadContent();
            String manifestContent = container.getText().getCodeStr();

            Map<String, Object> result = new HashMap<>();
            result.put("name", manifest.getOriginalName());
            result.put("type", "manifest/xml");
            result.put("content", manifestContent);

            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }

    private void handleMainApplication(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            AndroidManifestParser parser = new AndroidManifestParser(
                    AndroidManifestParser.getAndroidManifest(resources),
                    EnumSet.of(AppAttribute.APPLICATION),
                    wrapper.getArgs().getSecurity()
            );

            if (!parser.isManifestFound()) {
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            ApplicationParams results = parser.parse();

            if (results.getApplication() == null) {
                ctx.status(404).json(Map.of("error", "Failed to get application from manifest " + results.getApplication()));
                return;
            }

            JavaClass applicationClass = results.getApplicationJavaClass(wrapper.getDecompiler());
            if (applicationClass == null) {
                ctx.status(404).json(Map.of("error", "Failed to get application class: " + results.getApplication()));
                return;
            }

            // Fetch all classes based on manifest package name
            String manifestPkg = parser.parse().getApplication();// This is the manifest `package`
            System.out.println(manifestPkg);
            List<JavaClass> matchedClasses = wrapper.getDecompiler().getClasses().stream()
                    .filter(cls -> cls.getFullName().startsWith(manifestPkg))
                    .collect(Collectors.toList());

            List<Map<String, Object>> classesInfo = new ArrayList<>();
            for (JavaClass cls : matchedClasses) {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("name", cls.getFullName());
                classInfo.put("type", "code/java");
                classInfo.put("content", cls.getCode());
                classesInfo.add(classInfo);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("applicationClass", applicationClass.getFullName());
            result.put("allClassesInPackage", classesInfo);

            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }


    private void handleMainActivity(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            AndroidManifestParser parser = new AndroidManifestParser(AndroidManifestParser.getAndroidManifest(resources), EnumSet.of(AppAttribute.MAIN_ACTIVITY), wrapper.getArgs().getSecurity());
            if (!parser.isManifestFound()) {
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            ApplicationParams results = parser.parse();
            if (results.getMainActivity() == null) {
                ctx.status(404).json(Map.of("error", "Failed to get main activity from manifest"));
                return;
            }

            JavaClass mainActivityClass = results.getMainActivityJavaClass(wrapper.getDecompiler());

            if (mainActivityClass == null) {
                ctx.status(404).json(Map.of("error", "Failed to get activity class: " + results.getApplication()));
                return;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("name", mainActivityClass.getFullName());
            result.put("type", "code/java");
            result.put("content", mainActivityClass.getCode());

            ctx.json(result);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
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
}
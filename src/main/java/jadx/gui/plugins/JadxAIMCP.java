package jadx.gui.plugins;

//import com.google.gson.Gson;

import io.javalin.Javalin;
import io.javalin.http.Context;

import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.ResourceFile;
//import jadx.api.ResourceType;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.security.IJadxSecurity;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.utils.android.AppAttribute;
import jadx.core.utils.android.ApplicationParams;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.xmlgen.ResContainer;
import jadx.gui.JadxWrapper;
import jadx.gui.ui.MainWindow;
import jadx.api.JadxDecompiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Element;

import com.android.tools.r8.internal.su;

import org.w3c.dom.Document;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class JadxAIMCP implements JadxPlugin {
    private MainWindow mainWindow;
    //private final Gson gson = new Gson();
    private Javalin app;
    private static final Logger logger = LoggerFactory.getLogger(JadxAIMCP.class);
    private static final Logger logger2 = LoggerFactory.getLogger("Logger");
    //private static final Logger logger3 = LoggerFactory.getILoggerFactory();
    public static final String PLUGIN_ID = "jadx-ai-mcp";

    @Override
    public void init(JadxPluginContext context) {
        // first check for GUI context, if not then exit gracefully
        if (context.getGuiContext() == null) {
            System.out.println("JADX-AI-MCP Plugin: Running in non-GUI mode, plugin features disabled.");
            return;
        }

        try {
            // now safe to use GUI context
            this.mainWindow = (MainWindow) context.getGuiContext().getMainFrame();
            if (this.mainWindow == null) {
                System.err.println("JADX-AI-MCP Plugin: Main windows is null. JADX AI MCP will not start.");
                return;
            }

            logger.info("JADX AI MCP Plugin: Starting HTTP Server...");
            this.start(mainWindow);
        } catch (Exception e) {
            logger.error("JADX AI MCP Plugin: Initialization error: " + e.getStackTrace());
        }
    }

    @Override
    public JadxPluginInfo getPluginInfo() {
        return JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
                .name("JADX-AI-MCP Plugin")
                .description("Integrates MCP Server support for JADX")
                .homepage("https://github.com/zinja-coder/jadx-ai-mcp")
                .requiredJadxVersion("1.5.1, r2333")
                .build();
    }

    // public no-argument constructor
    public JadxAIMCP() {
        // empty constructor
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
            app.get("/main-application-classes-code", this::handleMainApplicationClassesCode);
            app.get("/main-application-classes-name", this::handleMainApplicationClassesNames);
            app.get("/main-activity", this::handleMainActivity);
            app.get("/strings", this::handleStrings);
            app.get("/list-all-resource-files-names", this::handleListAllResourceFilesNames);

            logger.info("JADX AI MCP Plugin HTTP Serve Started at http://127.0.0.1:8650/");
        } catch (Exception e) {
            logger.error("JADX-AI-MCP Plugin Error: Could not start HTTP Server on. Exception: " + e.getStackTrace());
        }
    }

    // -------------------------- various request handlers -------------------------- //
    
    // method to handle /current-class request //
    public void handleCurrentClass(Context ctx){
        try {
            String className = getSelectedTabTitle();
            String code = extractTextFromCurrentTab();
    
            Map<String, Object> result = new HashMap<>();
            result.put("name", className != null ? className.replace(".java", "") : "unknown");
            result.put("type", "code/java");
            result.put("content", code != null ? code : "");
            
            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal Error while trying to fetch current class: " + e.getMessage()));
        }
    }

    // method to handle /all-classes call
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
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Failed to load class list: " + e.getMessage()));
        }
    }

    // method to handle /selected-text call
    private void handleSelectedText(Context ctx) {
        try {
            JTextArea textArea = findTextArea(mainWindow.getTabbedPane().getSelectedComponent());
            String selectedText = textArea != null ? textArea.getSelectedText() : null;

            Map<String, String> result = new HashMap<>();
            result.put("selectedText", selectedText != null ? selectedText : "");
            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error while trying to fetch selected text: " + e.getMessage()));
        }
    }

    // method to handle /method-by-name call
    private void handleMethodByName(Context ctx) {
        String methodName = ctx.queryParam("method");

        if (methodName == null || methodName.isEmpty()) {
            logger.error("JADX AI MCP Error: Missing 'method' parameter.");
            ctx.status(400).json(Map.of("error", "Missing 'method' parameter"));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            if (wrapper == null) {
                logger.error("JADX AI MCP Error: JadxWrapper not initialized");
                ctx.status(500).json(Map.of("error", "JadxWrapper no initialized"));
                return;
            }

            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                for (jadx.api.JavaMethod method : cls.getMethods()) {
                    if (method.getName().equalsIgnoreCase(methodName)) {
                        String codeStr;
                        try {
                            codeStr = method.getCodeStr();
                        } catch (Exception e) {
                            logger.error("JADX AI MCP Error: " + e.getStackTrace());
                            codeStr = "Error retrieving code from method: " + e.getMessage();
                        }

                        Map<String, Object> result = new HashMap<>();
                        result.put("class", cls.getFullName());
                        result.put("method", method.getName());
                        result.put("decl", String.valueOf(method.getCodeNodeRef()));
                        ctx.json(result);
                        return;
                    }
                }
            }

            ctx.status(404).json(Map.of("error", "Method not found in any class."));
            logger.error("JADX AI MCP Error: Method not found in any class");
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error while trying to retrieve method code: " + e.getMessage()));
        }
    }

    // method to handle /class-source
    private void handleClassSource(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            logger.error("JADX AI MCP Error: Missing 'class' parameter.");
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
            logger.error("JADX AI MCP Error: Class not found.");
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving class source: " + e.getMessage()));
        }
    }

    // method to handle /search-method 
    private void handleSearchMethod(Context ctx) {
        String methodName = ctx.queryParam("method");
        List<String> results = new ArrayList<>();

        if (methodName == null) {
            logger.error("JADX AI MCP Error: Missing 'method' parameter.");
            ctx.status(400).json(Map.of("error", "Missing method parameter"));
            return;
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getCode().toLowerCase().contains(methodName.toLowerCase())) {
                    results.add(cls.getFullName());
                }
            }
            ctx.json(results);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error during method search: " + e.getMessage()));
        }
    }

    // method to handle /methods-of-class call 
    private void handleMethodsOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            logger.error("JADX AI MCP Error: Missing 'class' parameter.");
            ctx.status(400).json(Map.of("error", "Missing required parameter 'class'"));
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> methods = new ArrayList<>();
                    for (JavaMethod method : cls.getMethods()) {
                        String methodData = method.getAccessFlags() + " " + method.getReturnType() + " " + 
                                    method.getName() + method.getMethodNode() + method.getFullName();
                        methods.add(methodData);
                    }
                    ctx.json(methods);
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found."));
            logger.error("JADX AI MCP Error: Class not found.");
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving methods: " + e.getMessage()));
        }
    }

    // method to handle /fields-of-class call
    private void handleFieldsOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            logger.error("JADX AI MCP Error: Missing 'class' parameter.");
            ctx.status(400).json(Map.of("error", "Missing required parameter 'class'"));
        }

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> fields = new ArrayList<>();
                    for (JavaField field : cls.getFields()) {
                        String fieldData = field.getAccessFlags() + " "
                                            + field.getType() + " " + field.getName();
                        fields.add(fieldData);
                    }
                    ctx.json(fields);
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class not found"));
            logger.error("JADX AI MCP Error: Class not found.");
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving fields: " + e.getMessage()));
        }
    }

    // method to handle /smali-of-class call
    private void handleSmaliOfClass(Context ctx) {
        String className = ctx.queryParam("class");

        if (className == null || className.isEmpty()) {
            logger.error("JADX AI MCP Error: Missing 'class' parameter.");
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
            logger.error("JADX AI MCP Error: Class not found.");
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving class source: " + e.getMessage()));
        }
    }

    // method to handle /manifest
    private void handleManifest(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();
            ResourceFile manifest = AndroidManifestParser.getAndroidManifest(resources);

            if (manifest == null) {
                logger.error("JADX AI MCP Error: AndroidManifest.xml not found.");
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
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }

    // method to handle /main-application-classes-name
    private void handleMainApplicationClassesNames(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            // Get the manifest ResourceFile
            ResourceFile manifestRes = AndroidManifestParser.getAndroidManifest(resources);
            if (manifestRes == null) {
                logger.error("JADX AI MCP Error: AndroidManifest.xml not found.");
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            // Load manifest content and parse XML
            String manifestXml = manifestRes.loadContent().getText().getCodeStr();
            Document manifestDoc = parseManifestXml(manifestXml, wrapper.getArgs().getSecurity());
            
            // Extract the package name from the <manifest> tag
            Element manifestElement = (Element) manifestDoc.getElementsByTagName("manifest").item(0);
            String packageName = manifestElement.getAttribute("package");

            if (packageName.isEmpty()) {
                logger.error("JADX AI MCP Error: Package name not found in manifest");
                ctx.status(404).json(Map.of("error", "Package name not found in manifest."));
                return;
            }

            // Filter classes under this package
            List<JavaClass> matchedClasses = wrapper.getDecompiler()
                    .getClasses()
                    .stream()
                    .filter(cls -> cls.getFullName().startsWith(packageName))
                    .collect(Collectors.toList());
            
            List<Map<String, Object>> classesInfo = new ArrayList<>();
            for (JavaClass cls : matchedClasses) {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("name", cls.getFullName());
                classesInfo.add(classInfo);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("allClassesInPackageName",  classesInfo);
            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }

    // handle /main-application-classes-codes 
    private void handleMainApplicationClassesCode(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            // get the manifest resource file
            ResourceFile manifestRes = AndroidManifestParser.getAndroidManifest(resources);
            if (manifestRes == null) {
                logger.error("JADX AI MCP Error: AndroidManifest.xml not found.");
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            // load manifest content and parse xml
            String manifestXml = manifestRes.loadContent()
                .getText()
                .getCodeStr();
            Document manifestDoc = parseManifestXml(manifestXml, wrapper.getArgs().getSecurity());

            // Extract the package name from the <manifest> tag
            Element manifestElement = (Element) manifestDoc.getElementsByTagName("manifest").item(0);
            String packageName = manifestElement.getAttribute("package");

            if (packageName.isEmpty()) {
                logger.error("JADX AI MCP Error: Package name not found manifest.");
                ctx.status(404).json(Map.of("error", "Package name not found manifest."));
                return;
            }

            // filter classes under this package
            List<JavaClass> matchedClasses = wrapper.getDecompiler()
                .getClasses()
                .stream()
                .filter(cls -> cls.getFullName().startsWith(packageName))
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
            result.put("allClassesInPackage", classesInfo);
            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }

    // method to handle /main-activity
    private void handleMainActivity(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resources = wrapper.getResources();

            AndroidManifestParser parser = new AndroidManifestParser(
                AndroidManifestParser.getAndroidManifest(resources), 
                EnumSet.of(AppAttribute.MAIN_ACTIVITY),
                wrapper.getArgs().getSecurity()
                );
            
            if (!parser.isManifestFound()) {
                logger.error("JADX AI MCP Error: AndroidManifest.xml not found.");
                ctx.status(404).json(Map.of("error", "AndroidManifest.xml not found."));
                return;
            }

            ApplicationParams results = parser.parse();
            if (results.getMainActivity() == null) {
                logger.error("JADX AI MCP Error: Failed to get main activity from manifest.");
                ctx.status(404).json(Map.of("error", "Failed to get main activity from manifest."));
                return;
            }

            JavaClass mainActivityClass = results.getMainActivityJavaClass(wrapper.getDecompiler());

            if (mainActivityClass == null) {
                logger.error("JADX AI MCP Error: Failed to get activity class: " + results.getApplication());
                ctx.status(404).json(Map.of("error", "Failed to get activity class: " + results.getApplication()));
                return;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("name", mainActivityClass.getFullName());
            result.put("type", "code/java");
            result.put("content", mainActivityClass.getCode());
            
            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error", "Internal error retrieving AndroidManifest.xml: " + e.getMessage()));
        }
    }

    // method to handle /strings
    private void handleStrings(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resourceFiles = wrapper.getResources();
            List<Map<String, Object>> stringResources = new ArrayList<>();

            for (ResourceFile resFile : resourceFiles) {
                //JadxDecompiler.log
                //logger.info(resFile.getDeobfName());
                if (resFile.getDeobfName().equals("resources.arsc")) {
                    try {
                        ResContainer container = resFile.loadContent();
                        List<ResContainer> subFiles = container.getSubFiles();
                        for (ResContainer file : subFiles) {
                            logger.info(file.getFileName());
                            if (file.getFileName().equals("res/values/strings.xml")){
                                Map<String, Object> stringsFile = new HashMap<>();
                                stringsFile.put("file", file.getFileName());
                                stringsFile.put("content", file.getText());//container.getText().getCodeStr());
                                stringResources.add(stringsFile);
                                break;
                            }
                        }
                        //Map<String, Object> stringsFile = new HashMap<>();
                        //String str = container.
                        //stringsFile.put("file", resFile.getDeobfName());
                        //stringsFile.put("content", container.getText().getCodeStr());
                        //stringResources.add(stringsFile);
                        //break;
                    } catch (Exception e) {
                        logger.error("JADX AI MCP Error: " + e.getStackTrace());
                    }
                }
            }

            if (stringResources.isEmpty()) {
                ctx.status(404).json(Map.of("error", "No strings.xml resource found"));
                return;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("type", "resource/strings-mxl");
            result.put("file", stringResources);

            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error","Internal error while retrieving strings.xml file: " + e.getMessage()));
        }
    }

    // method to handle /strings
    private void handleListAllResourceFilesNames(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<ResourceFile> resourceFiles = wrapper.getResources();
            List<String> resourceFileNames = new ArrayList<>();

            for (ResourceFile resFile : resourceFiles) {
                try {
                //JadxDecompiler.log
                //logger.info(resFile.getDeobfName());
                if (resFile.getDeobfName().equals("resources.arsc")) {
                   
                        ResContainer container = resFile.loadContent();
                        List<ResContainer> subFiles = container.getSubFiles();
                        for (ResContainer file : subFiles) {
                            resourceFileNames.add(file.getFileName());
                           // logger.info(file.getFileName());
                            //if (file.getFileName().equals("res/values/strings.xml")){
                                //Map<String, Object> stringsFile = new HashMap<>();
                                //stringsFile.put("Name", file.getFileName());
                                //stringsFile.put("content", file.getText());//container.getText().getCodeStr());
                                //stringResources.add(stringsFile);

                            //    break;
                           // }
                        }

                    
                        //Map<String, Object> stringsFile = new HashMap<>();
                        //String str = container.
                        //stringsFile.put("file", resFile.getDeobfName());
                        //stringsFile.put("content", container.getText().getCodeStr());
                        //stringResources.add(stringsFile);
                        //break;
                    }
                    resourceFileNames.add(resFile.getDeobfName());
                } catch (Exception e) {
                        logger.error("JADX AI MCP Error: " + e.getStackTrace());
                    }
                }
            

            if (resourceFileNames.isEmpty()) {
                ctx.status(404).json(Map.of("error", "No resources found"));
                return;
            }
            
            Map<String, Object> result = new HashMap<>();
            //result.put("type", "resource/names");
            result.put("files", resourceFileNames);

            ctx.json(result);
        } catch (Exception e) {
            logger.error("JADX AI MCP Error: " + e.getStackTrace());
            ctx.status(500).json(Map.of("error","Internal error while retrieving strings.xml file: " + e.getMessage()));
        }
    }

    // -------------------------- helper methods to assist the request handler methods -------------------------- //
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

    // reusing jadx's secure xml parsing logic for parsing manifest xml file
    // this code is taken from jadx - https://github.com/skylot/jadx/blob/47647bbb9a9a3cd3150705e09cc1f84a5e9f0be6/jadx-core/src/main/java/jadx/core/utils/android/AndroidManifestParser.java#L214
    private Document parseManifestXml(String xmlContent, IJadxSecurity security) {
        try (InputStream xmlStream = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))) {
            Document doc = security.parseXml(xmlStream);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to parse AndroidManifest.xml", e);
        }
    }
}

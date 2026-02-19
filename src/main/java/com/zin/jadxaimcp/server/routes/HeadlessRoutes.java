package com.zin.jadxaimcp.server.routes;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import com.zin.jadxaimcp.utils.PaginationUtils;
import com.zin.jadxaimcp.utils.PaginationUtils.PaginationException;
import io.javalin.http.Context;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.ResourceFile;
import jadx.api.security.IJadxSecurity;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.utils.android.AppAttribute;
import jadx.core.utils.android.ApplicationParams;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.xmlgen.ResContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CLI/headless handlers backed by JadxDecompiler only (no Swing/MainWindow).
 */
public class HeadlessRoutes {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessRoutes.class);

    private final JadxDecompiler decompiler;
    private final PaginationUtils paginationUtils;

    private static final Pattern OBFUSCATED_PACKAGE_PATTERN = Pattern.compile("^p\\d+$");

    public enum SearchLocation {
        CLASS_NAME,
        METHOD_NAME,
        FIELD_NAME,
        CODE,
        COMMENT
    }

    private static final Map<String, SearchLocation> SEARCH_LOCATION_MAP = new HashMap<>();

    static {
        SEARCH_LOCATION_MAP.put("class", SearchLocation.CLASS_NAME);
        SEARCH_LOCATION_MAP.put("class_name", SearchLocation.CLASS_NAME);
        SEARCH_LOCATION_MAP.put("method", SearchLocation.METHOD_NAME);
        SEARCH_LOCATION_MAP.put("method_name", SearchLocation.METHOD_NAME);
        SEARCH_LOCATION_MAP.put("field", SearchLocation.FIELD_NAME);
        SEARCH_LOCATION_MAP.put("field_name", SearchLocation.FIELD_NAME);
        SEARCH_LOCATION_MAP.put("code", SearchLocation.CODE);
        SEARCH_LOCATION_MAP.put("comment", SearchLocation.COMMENT);
    }

    public HeadlessRoutes(JadxDecompiler decompiler, PaginationUtils paginationUtils) {
        this.decompiler = decompiler;
        this.paginationUtils = paginationUtils;
    }

    // ----------------------- Unsupported-in-headless endpoints -----------------------

    public void handleCurrentClass(Context ctx) {
        unsupported(ctx, "/current-class is not available in headless mode");
    }

    public void handleSelectedText(Context ctx) {
        unsupported(ctx, "/selected-text is not available in headless mode");
    }

    public void handleRenameClass(Context ctx) {
        unsupported(ctx, "/rename-class requires JADX GUI");
    }

    public void handleRenameMethod(Context ctx) {
        unsupported(ctx, "/rename-method requires JADX GUI");
    }

    public void handleRenameField(Context ctx) {
        unsupported(ctx, "/rename-field requires JADX GUI");
    }

    public void handleRenamePackage(Context ctx) {
        unsupported(ctx, "/rename-package requires JADX GUI");
    }

    public void handleRenameVariable(Context ctx) {
        unsupported(ctx, "/rename-variable requires JADX GUI");
    }

    public void handleGetStackFrames(Context ctx) {
        unsupported(ctx, "/debug/stack-frames requires JADX GUI debugger");
    }

    public void handleGetVariables(Context ctx) {
        unsupported(ctx, "/debug/variables requires JADX GUI debugger");
    }

    public void handleGetThreads(Context ctx) {
        unsupported(ctx, "/debug/threads requires JADX GUI debugger");
    }

    // ----------------------- Class / Method / Resource endpoints ---------------------

    public void handleAllClasses(Context ctx) {
        try {
            List<JavaClass> classes = decompiler.getClassesWithInners();
            Map<String, Object> result = paginationUtils.handlePagination(
                    ctx,
                    classes,
                    "class-list",
                    "classes",
                    JavaClass::getFullName);
            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx, "Pagination Error: " + e.getMessage(), e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Failed to load class list: " + e.getMessage(), e, logger);
        }
    }

    public void handleClassSource(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) {
            return;
        }

        try {
            JavaClass cls = findClassByName(className);
            if (cls == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }
            ctx.result(cls.getCode());
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving class source: " + e.getMessage(), e,
                    logger);
        }
    }

    public void handleMethodsOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) {
            return;
        }

        try {
            JavaClass cls = findClassByName(className);
            if (cls == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }

            List<String> methods = new ArrayList<>();
            for (JavaMethod method : cls.getMethods()) {
                String fullMethodName = cls.getFullName() + "." + method.getName();
                String methodData = method.getAccessFlags()
                        + " " + method.getReturnType()
                        + " " + method.getName()
                        + " " + method.getMethodNode()
                        + " " + fullMethodName;
                methods.add(methodData);
            }
            ctx.result(String.join("\n", methods));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving methods: " + e.getMessage(), e, logger);
        }
    }

    public void handleFieldsOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) {
            return;
        }

        try {
            JavaClass cls = findClassByName(className);
            if (cls == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }

            List<String> fields = new ArrayList<>();
            for (JavaField field : cls.getFields()) {
                String fieldData = field.getAccessFlags()
                        + " " + field.getType()
                        + " " + field.getName();
                fields.add(fieldData);
            }
            ctx.result(String.join("\n", fields));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving fields: " + e.getMessage(), e, logger);
        }
    }

    public void handleSmaliOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) {
            return;
        }

        try {
            JavaClass cls = findClassByName(className);
            if (cls == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }
            ctx.result(cls.getSmali());
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving smali: " + e.getMessage(), e, logger);
        }
    }

    public void handleMethodByName(Context ctx) {
        String className = ctx.queryParam("class_name");
        String methodName = validateMethodParam(ctx);
        if (methodName == null) {
            return;
        }

        try {
            if (className == null || className.isEmpty()) {
                for (JavaClass cls : decompiler.getClassesWithInners()) {
                    for (JavaMethod method : cls.getMethods()) {
                        if (method.getName().equalsIgnoreCase(methodName)) {
                            returnMethodResult(ctx, cls, method);
                            return;
                        }
                    }
                }
            } else {
                JavaClass cls = findClassByName(className);
                if (cls == null) {
                    JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                    return;
                }
                for (JavaMethod method : cls.getMethods()) {
                    if (method.getName().equalsIgnoreCase(methodName)) {
                        returnMethodResult(ctx, cls, method);
                        return;
                    }
                }
            }

            JadxAIMCPPluginError.handleError(ctx, 404, "Requested method " + methodName + " not found.", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error occurred while retrieving method: " + e.getMessage(),
                    e, logger);
        }
    }

    public void handleSearchMethod(Context ctx) {
        String methodName = validateMethodParam(ctx);
        if (methodName == null) {
            return;
        }

        try {
            List<String> results = new ArrayList<>();
            for (JavaClass cls : decompiler.getClassesWithInners()) {
                String code = cls.getCode();
                if (code != null && code.toLowerCase().contains(methodName.toLowerCase())) {
                    results.add(cls.getFullName());
                }
            }
            ctx.result(String.join("\n", results));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error during method search: " + e.getMessage(), e, logger);
        }
    }

    public void handleManifest(Context ctx) {
        try {
            ResourceFile manifest = getManifestFile();
            if (manifest == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "AndroidManifest.xml not found.", logger);
                return;
            }
            ResContainer container = manifest.loadContent();
            String content = container.getText().getCodeStr();
            ctx.json(Map.of("name", manifest.getOriginalName(), "type", "manifest/xml", "content", content));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to fetch the AndroidManifest.xml file: " + e.getMessage(),
                    e, logger);
        }
    }

    public void handleStrings(Context ctx) {
        try {
            List<Map<String, String>> allStringEntries = new ArrayList<>();
            List<ResourceFile> resourceFiles = decompiler.getResources();

            for (ResourceFile resFile : resourceFiles) {
                try {
                    if ("resources.arsc".equals(resFile.getDeobfName())) {
                        for (ResContainer file : resFile.loadContent().getSubFiles()) {
                            if ("res/values/strings.xml".equals(file.getFileName()) && file.getText() != null) {
                                allStringEntries.add(Map.of("file", file.getFileName(),
                                        "content", file.getText().getCodeStr()));
                            }
                        }
                    } else if ("res/values/strings.xml".equals(resFile.getDeobfName())
                            && resFile.loadContent().getText() != null) {
                        allStringEntries.add(Map.of("file", resFile.getDeobfName(),
                                "content", resFile.loadContent().getText().getCodeStr()));
                    }
                } catch (Exception e) {
                    logger.error("Error processing resource file during handleStrings(): " + e.getMessage());
                }
            }

            if (allStringEntries.isEmpty()) {
                JadxAIMCPPluginError.handleError(ctx, 404, "No strings.xml resource found.", logger);
                return;
            }

            Map<String, Object> result = paginationUtils.handlePagination(
                    ctx,
                    allStringEntries,
                    "resource/strings-xml",
                    "strings",
                    item -> item);
            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while generating pagination result for handleStrings(): " + e.getMessage(),
                    e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to handle /strings: " + e.getMessage(), e, logger);
        }
    }

    public void handleGetResourceFile(Context ctx) {
        String fileName = ctx.queryParam("file_name");
        if (fileName == null || fileName.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing required 'file_name' parameter.", logger);
            return;
        }

        try {
            List<ResourceFile> resourceFiles = decompiler.getResources();

            for (ResourceFile resFile : resourceFiles) {
                if (fileName.equals(resFile.getDeobfName())) {
                    String content = safeResourceText(resFile.loadContent());
                    if (content == null) {
                        JadxAIMCPPluginError.handleError(ctx, 404, "Resource file has no text content", logger);
                        return;
                    }
                    ctx.json(Map.of(
                            "type", "resource/text",
                            "file", Map.of("file_name", resFile.getDeobfName(), "content", content)));
                    return;
                }

                if ("resources.arsc".equals(resFile.getDeobfName())) {
                    for (ResContainer sub : resFile.loadContent().getSubFiles()) {
                        if (fileName.equals(sub.getFileName())) {
                            String subContent = (sub.getText() != null) ? sub.getText().getCodeStr() : null;
                            if (subContent == null) {
                                JadxAIMCPPluginError.handleError(ctx, 404, "Resource file has no text content",
                                        logger);
                                return;
                            }
                            ctx.json(Map.of(
                                    "type", "resource/text",
                                    "file", Map.of("file_name", sub.getFileName(), "content", subContent)));
                            return;
                        }
                    }
                }
            }

            JadxAIMCPPluginError.handleError(ctx, 404, "No resource file found", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal Error occurred while trying to handle get-resource-file: " + e.getMessage(), e, logger);
        }
    }

    public void handleListAllResourceFilesNames(Context ctx) {
        try {
            List<ResourceFile> resourceFiles = decompiler.getResources();
            Set<String> resourceFileNames = new LinkedHashSet<>();

            for (ResourceFile resFile : resourceFiles) {
                try {
                    if ("resources.arsc".equals(resFile.getDeobfName())) {
                        ResContainer container = resFile.loadContent();
                        for (ResContainer file : container.getSubFiles()) {
                            resourceFileNames.add(file.getFileName());
                        }
                    }
                    resourceFileNames.add(resFile.getDeobfName());
                } catch (Exception e) {
                    logger.error("Error reading resource file names: " + e.getMessage(), e);
                }
            }

            if (resourceFileNames.isEmpty()) {
                JadxAIMCPPluginError.handleError(ctx, 404, "No resources found.", logger);
                return;
            }

            Map<String, Object> result = paginationUtils.handlePagination(
                    ctx,
                    new ArrayList<>(resourceFileNames),
                    "application-resources",
                    "files",
                    item -> item);
            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while generating pagination result for list-all-resource-files-names: "
                            + e.getMessage(),
                    e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while retrieving list of resource files names: " + e.getMessage(), e, logger);
        }
    }

    public void handleMainActivity(Context ctx) {
        try {
            ResourceFile manifestRes = AndroidManifestParser.getAndroidManifest(decompiler.getResources());
            if (manifestRes == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "AndroidManifest.xml not found", logger);
                return;
            }

            AndroidManifestParser parser = new AndroidManifestParser(
                    manifestRes,
                    EnumSet.of(AppAttribute.MAIN_ACTIVITY),
                    decompiler.getArgs().getSecurity());

            if (!parser.isManifestFound()) {
                JadxAIMCPPluginError.handleError(ctx, 404, "AndroidManifest.xml not found.", logger);
                return;
            }

            ApplicationParams results = parser.parse();
            if (results.getMainActivity() == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Failed to get main activity from manifest.", logger);
                return;
            }

            JavaClass mainActivityClass = results.getMainActivityJavaClass(decompiler);
            if (mainActivityClass == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Failed to get activity class: " + results.getApplication(),
                        logger);
                return;
            }

            ctx.json(Map.of(
                    "name", mainActivityClass.getFullName(),
                    "type", "code/java",
                    "content", mainActivityClass.getCode()));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to get the Main Activity class code: " + e.getMessage(),
                    e, logger);
        }
    }

    public void handleMainApplicationClassesNames(Context ctx) {
        try {
            String packageName = getMainPackageFromManifest();
            if (packageName == null || packageName.isEmpty()) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Package name not found in AndroidManifest.xml", logger);
                return;
            }

            List<JavaClass> matchedClasses = decompiler.getClassesWithInners().stream()
                    .filter(cls -> cls.getFullName().startsWith(packageName))
                    .collect(Collectors.toList());

            List<Map<String, Object>> classesInfo = new ArrayList<>();
            for (JavaClass cls : matchedClasses) {
                classesInfo.add(Map.of("name", cls.getFullName()));
            }

            ctx.json(Map.of("classes", classesInfo));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while trying to fetch all classes names: " + e.getMessage(), e, logger);
        }
    }

    public void handleMainApplicationClassesCode(Context ctx) {
        try {
            String packageName = getMainPackageFromManifest();
            if (packageName == null || packageName.isEmpty()) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Package name not found in AndroidManifest.xml", logger);
                return;
            }

            List<JavaClass> matchedClasses = decompiler.getClassesWithInners().stream()
                    .filter(cls -> cls.getFullName().startsWith(packageName))
                    .collect(Collectors.toList());

            List<Map<String, Object>> classInfoList = new ArrayList<>();
            for (JavaClass cls : matchedClasses) {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("name", cls.getFullName());
                classInfo.put("type", "code/java");
                try {
                    classInfo.put("content", cls.getCode());
                } catch (Exception e) {
                    classInfo.put("content", "// Error decompiling class: " + e.getMessage());
                }
                classInfoList.add(classInfo);
            }

            Map<String, Object> result = paginationUtils.handlePagination(
                    ctx,
                    classInfoList,
                    "application-classes",
                    "classes",
                    item -> item);

            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while generating pagination result for main-application-classes-code: "
                            + e.getMessage(),
                    e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while retrieving main application classes' code: " + e.getMessage(),
                    e, logger);
        }
    }

    public void handleSearchClassesByKeyword(Context ctx) {
        String searchTerm = ctx.queryParam("search_term");
        if (searchTerm == null || searchTerm.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing 'search_term' parameter.", logger);
            return;
        }

        String packageFilter = ctx.queryParam("package");
        Set<SearchLocation> searchLocations = parseSearchLocations(ctx.queryParam("search_in"));

        try {
            List<JavaClass> allClasses = decompiler.getClassesWithInners();
            String term = searchTerm.toLowerCase();
            boolean applyPackageFilter = isValidPackageFilter(packageFilter);
            if (packageFilter != null && !applyPackageFilter) {
                logger.info("Package filter '{}' appears obfuscated, skipping package filtering", packageFilter);
            }

            Set<JavaClass> matchingClassesSet = new LinkedHashSet<>();
            for (SearchLocation location : searchLocations) {
                Set<JavaClass> locationResults = searchInLocation(allClasses, term, location, packageFilter,
                        applyPackageFilter);
                matchingClassesSet.addAll(locationResults);
            }

            List<JavaClass> matchingClasses = new ArrayList<>(matchingClassesSet);

            Map<String, Object> result = paginationUtils.handlePagination(
                    ctx,
                    matchingClasses,
                    "class-list",
                    "classes",
                    JavaClass::getFullName);
            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while generating pagination result for search-classes-by-keyword: "
                            + e.getMessage(),
                    e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error while handling search classes by keyword request: " + e.getMessage(), e, logger);
        }
    }

    // ---------------------------------- Xrefs ----------------------------------

    public void handleXrefsToClass(Context ctx) {
        String className = validateRequiredParam(ctx, "class_name");
        if (className == null) {
            return;
        }

        try {
            JavaClass targetJavaClass = findClassByName(className);
            if (targetJavaClass == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }

            ClassNode targetClassNode = targetJavaClass.getClassNode();
            List<ClassNode> classReferences = targetClassNode.getUseIn();
            List<MethodNode> methodReferences = new ArrayList<>(targetClassNode.getUseInMth());

            for (JavaMethod javaMethod : targetJavaClass.getMethods()) {
                if (javaMethod.isConstructor()) {
                    methodReferences.addAll(javaMethod.getMethodNode().getUseIn());
                }
            }

            Map<String, Set<String>> classToMethodsMap = new HashMap<>();
            for (MethodNode mth : methodReferences) {
                ClassNode parentClass = mth.getParentClass();
                if (parentClass != null) {
                    classToMethodsMap.computeIfAbsent(parentClass.getFullName(), k -> new HashSet<>()).add(mth.getName());
                }
            }

            Set<String> existingClassNames = new HashSet<>();
            for (ClassNode cls : classReferences) {
                existingClassNames.add(cls.getFullName());
            }
            for (MethodNode mth : methodReferences) {
                ClassNode parentClass = mth.getParentClass();
                if (parentClass != null && !existingClassNames.contains(parentClass.getFullName())) {
                    classReferences.add(parentClass);
                    existingClassNames.add(parentClass.getFullName());
                }
            }

            List<Map<String, String>> referenceList = new ArrayList<>();
            Set<String> seenReferences = new HashSet<>();

            for (ClassNode refClassNode : classReferences) {
                String refClassName = refClassNode.getFullName();

                if (classToMethodsMap.containsKey(refClassName)) {
                    for (MethodNode mth : methodReferences) {
                        if (mth.getParentClass() != null && mth.getParentClass().getFullName().equals(refClassName)) {
                            Map<String, String> refInfo = extractMethodNodeReferenceInfo(mth);
                            addIfUnique(referenceList, seenReferences, refInfo);
                        }
                    }
                } else {
                    Map<String, String> refInfo = new HashMap<>();
                    refInfo.put("class", refClassName);
                    refInfo.put("method", "");
                    addIfUnique(referenceList, seenReferences, refInfo);
                }
            }
            sendXrefsResponse(ctx, referenceList);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    400,
                    "Pagination error occurred while trying to handle xrefs-to-class: " + e.getMessage(),
                    logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to find class references: " + e.getMessage(), e, logger);
        }
    }

    public void handleXrefsToMethod(Context ctx) {
        String className = validateRequiredParam(ctx, "class_name");
        String methodName = validateRequiredParam(ctx, "method_name");
        if (className == null || methodName == null) {
            return;
        }

        try {
            JavaClass containingClass = findClassByName(className);
            if (containingClass == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }

            List<JavaMethod> matchedMethods = findMethodsByName(ctx, containingClass, methodName);
            if (matchedMethods == null) {
                return;
            }

            List<JavaMethod> relatedMethods = new ArrayList<>();
            for (JavaMethod baseMethod : matchedMethods) {
                for (JavaMethod m : getMethodWithOverrides(baseMethod)) {
                    if (!relatedMethods.contains(m)) {
                        relatedMethods.add(m);
                    }
                }
            }

            List<MethodNode> allMethodReferences = new ArrayList<>();
            for (JavaMethod relatedMethod : relatedMethods) {
                allMethodReferences.addAll(relatedMethod.getMethodNode().getUseIn());
            }

            List<Map<String, String>> referenceList = collectMethodNodeReferences(allMethodReferences);
            sendXrefsResponse(ctx, referenceList);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    400,
                    "Pagination error occurred while trying to handle xrefs-to-method: " + e.getMessage(),
                    logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to find method references: " + e.getMessage(), e, logger);
        }
    }

    public void handleXrefsToField(Context ctx) {
        String className = validateRequiredParam(ctx, "class_name");
        String fieldName = validateRequiredParam(ctx, "field_name");
        if (className == null || fieldName == null) {
            return;
        }

        try {
            JavaClass containingClass = findClassByName(className);
            if (containingClass == null) {
                JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
                return;
            }

            JavaField targetField = findFieldByName(ctx, containingClass, fieldName);
            if (targetField == null) {
                return;
            }

            FieldNode fieldNode = targetField.getFieldNode();
            List<MethodNode> fieldReferences = fieldNode.getUseIn();
            List<Map<String, String>> referenceList = collectMethodNodeReferences(fieldReferences);
            sendXrefsResponse(ctx, referenceList);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx,
                    400,
                    "Pagination error occurred while trying to handle xrefs-to-field: " + e.getMessage(),
                    logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx,
                    "Internal error occurred while trying to find field references: " + e.getMessage(), e, logger);
        }
    }

    // -------------------------------- Helpers ----------------------------------

    private void unsupported(Context ctx, String message) {
        JadxAIMCPPluginError.handleError(ctx, 501, message, logger);
    }

    private JavaClass findClassByName(String className) {
        for (JavaClass cls : decompiler.getClassesWithInners()) {
            if (cls.getFullName().equals(className)) {
                return cls;
            }
        }
        return null;
    }

    private String checkClassParam(Context ctx) {
        String className = ctx.queryParam("class_name");
        if (className == null || className.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing required parameter 'class_name'", logger);
            return null;
        }
        return className;
    }

    private String validateMethodParam(Context ctx) {
        String methodName = ctx.queryParam("method_name");
        if (methodName == null || methodName.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing required parameter 'method_name'", logger);
            return null;
        }
        return methodName;
    }

    private void returnMethodResult(Context ctx, JavaClass cls, JavaMethod method) {
        String codeStr;
        try {
            codeStr = method.getCodeStr();
        } catch (Exception e) {
            logger.error("Error retrieving method code: {}", e.getMessage());
            codeStr = "Error retrieving code: " + e.getMessage();
        }

        Map<String, String> result = new HashMap<>();
        result.put("class_name", cls.getFullName());
        result.put("method_name", method.getName());
        result.put("decl", String.valueOf(method.getCodeNodeRef()));
        result.put("code", codeStr);
        ctx.json(result);
    }

    private ResourceFile getManifestFile() {
        return AndroidManifestParser.getAndroidManifest(decompiler.getResources());
    }

    private String safeResourceText(ResContainer container) {
        if (container == null || container.getText() == null) {
            return null;
        }
        return container.getText().getCodeStr();
    }

    private String getMainPackageFromManifest() {
        ResourceFile manifestRes = AndroidManifestParser.getAndroidManifest(decompiler.getResources());
        if (manifestRes == null) {
            return null;
        }
        String manifestXml = manifestRes.loadContent().getText().getCodeStr();
        Document manifestDoc = parseManifestXml(manifestXml, decompiler.getArgs().getSecurity());
        Element manifestElement = (Element) manifestDoc.getElementsByTagName("manifest").item(0);
        return manifestElement.getAttribute("package");
    }

    private Document parseManifestXml(String xmlContent, IJadxSecurity security) {
        try (InputStream xmlStream = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))) {
            Document doc = security.parseXml(xmlStream);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to parse AndroidManifest.xml", e);
        }
    }

    private String validateRequiredParam(Context ctx, String paramName) {
        String value = ctx.queryParam(paramName);
        if (value == null || value.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing required parameter '" + paramName + "'", logger);
            return null;
        }
        return value;
    }

    private List<JavaMethod> findMethodsByName(Context ctx, JavaClass javaClass, String methodName) {
        List<JavaMethod> matchedMethods = new ArrayList<>();
        String simpleClassName = javaClass.getName();
        for (JavaMethod method : javaClass.getMethods()) {
            if (!method.isConstructor() && method.getName().equals(methodName)) {
                matchedMethods.add(method);
            } else if (method.isConstructor() && methodName.equals(simpleClassName)) {
                matchedMethods.add(method);
            }
        }
        if (matchedMethods.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 404,
                    "Method " + methodName + " not found in class " + javaClass.getFullName(),
                    logger);
            return null;
        }
        return matchedMethods;
    }

    private JavaField findFieldByName(Context ctx, JavaClass javaClass, String fieldName) {
        for (JavaField field : javaClass.getFields()) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        JadxAIMCPPluginError.handleError(ctx, 404,
                "Field " + fieldName + " not found in class " + javaClass.getFullName(),
                logger);
        return null;
    }

    private List<Map<String, String>> collectMethodNodeReferences(List<MethodNode> methodNodes) {
        Set<String> seenReferences = new HashSet<>();
        List<Map<String, String>> referenceList = new ArrayList<>();
        for (MethodNode refMethodNode : methodNodes) {
            Map<String, String> refInfo = extractMethodNodeReferenceInfo(refMethodNode);
            addIfUnique(referenceList, seenReferences, refInfo);
        }
        return referenceList;
    }

    private void addIfUnique(List<Map<String, String>> list, Set<String> seen, Map<String, String> item) {
        if (item != null) {
            String key = item.get("class") + "#" + item.get("method");
            if (!seen.contains(key)) {
                seen.add(key);
                list.add(item);
            }
        }
    }

    private Map<String, String> extractMethodNodeReferenceInfo(MethodNode methodNode) {
        if (methodNode == null) {
            return null;
        }
        try {
            Map<String, String> refInfo = new HashMap<>();
            ClassNode parent = methodNode.getParentClass();
            if (parent != null) {
                refInfo.put("class", parent.getFullName());
                ensureClassDecompiled(parent);
            }
            JavaMethod javaMethod = methodNode.getJavaNode();
            String name = (javaMethod != null) ? javaMethod.getName() : methodNode.getName();
            if ("<clinit>".equals(name)) {
                name = "";
            }
            refInfo.put("method", name);
            return refInfo;
        } catch (Exception e) {
            logger.warn("Failed to extract reference info: {}", e.getMessage());
            return null;
        }
    }

    private void ensureClassDecompiled(ClassNode classNode) {
        if (classNode != null && !classNode.getState().isProcessComplete()) {
            try {
                if (classNode.getJavaNode() != null) {
                    classNode.getJavaNode().decompile();
                }
            } catch (Exception e) {
                logger.warn("Failed to decompile class {}: {}", classNode.getFullName(), e.getMessage());
            }
        }
    }

    private List<JavaMethod> getMethodWithOverrides(JavaMethod javaMethod) {
        List<JavaMethod> related = javaMethod.getOverrideRelatedMethods();
        return (related != null && !related.isEmpty()) ? related : Collections.singletonList(javaMethod);
    }

    private void sendXrefsResponse(Context ctx, List<Map<String, String>> referenceList) throws PaginationException {
        Map<String, Object> result = paginationUtils.handlePagination(
                ctx,
                referenceList,
                "xrefs",
                "references",
                ref -> ref);
        ctx.json(result);
    }

    private Set<SearchLocation> parseSearchLocations(String searchIn) {
        Set<SearchLocation> locations = EnumSet.noneOf(SearchLocation.class);

        if (searchIn == null || searchIn.trim().isEmpty()) {
            locations.add(SearchLocation.CODE);
            return locations;
        }

        String[] parts = searchIn.toLowerCase().split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            SearchLocation loc = SEARCH_LOCATION_MAP.get(trimmed);
            if (loc != null) {
                locations.add(loc);
            } else {
                logger.warn("Invalid search location '{}', ignoring. Valid values: {}", trimmed,
                        SEARCH_LOCATION_MAP.keySet());
            }
        }

        if (locations.isEmpty()) {
            locations.add(SearchLocation.CODE);
        }
        return locations;
    }

    private boolean isValidPackageFilter(String packageFilter) {
        if (packageFilter == null || packageFilter.trim().isEmpty()) {
            return false;
        }
        if ("defpackage".equals(packageFilter)) {
            return false;
        }

        String firstPart = packageFilter.split("\\.")[0];
        return !OBFUSCATED_PACKAGE_PATTERN.matcher(firstPart).matches();
    }

    private boolean matchesPackageFilter(JavaClass cls, String packageFilter) {
        if (packageFilter == null || packageFilter.trim().isEmpty()) {
            return true;
        }
        String fullName = cls.getFullName();
        return fullName.startsWith(packageFilter + ".") || fullName.equals(packageFilter);
    }

    private Set<JavaClass> searchInLocation(List<JavaClass> allClasses, String term,
            SearchLocation location, String packageFilter, boolean applyPackageFilter) {
        switch (location) {
            case CLASS_NAME:
                return searchByClassName(allClasses, term, packageFilter, applyPackageFilter);
            case METHOD_NAME:
                return searchByMethodName(allClasses, term, packageFilter, applyPackageFilter);
            case FIELD_NAME:
                return searchByFieldName(allClasses, term, packageFilter, applyPackageFilter);
            case CODE:
                return searchByCode(allClasses, term, packageFilter, applyPackageFilter);
            case COMMENT:
                return searchByComment(allClasses, term, packageFilter, applyPackageFilter);
            default:
                return new HashSet<>();
        }
    }

    private Set<JavaClass> searchByClassName(List<JavaClass> allClasses, String term,
            String packageFilter, boolean applyPackageFilter) {
        return allClasses.parallelStream()
                .filter(cls -> {
                    if (applyPackageFilter && !matchesPackageFilter(cls, packageFilter)) {
                        return false;
                    }
                    return cls.getName().toLowerCase().contains(term);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<JavaClass> searchByMethodName(List<JavaClass> allClasses, String term,
            String packageFilter, boolean applyPackageFilter) {
        return allClasses.parallelStream()
                .filter(cls -> {
                    if (applyPackageFilter && !matchesPackageFilter(cls, packageFilter)) {
                        return false;
                    }
                    for (JavaMethod method : cls.getMethods()) {
                        if (method.getName().toLowerCase().contains(term)) {
                            return true;
                        }
                        if (method.isConstructor()) {
                            String classSimpleName = cls.getName().toLowerCase();
                            if (classSimpleName.contains(term)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<JavaClass> searchByFieldName(List<JavaClass> allClasses, String term,
            String packageFilter, boolean applyPackageFilter) {
        return allClasses.parallelStream()
                .filter(cls -> {
                    if (applyPackageFilter && !matchesPackageFilter(cls, packageFilter)) {
                        return false;
                    }
                    for (JavaField field : cls.getFields()) {
                        if (field.getName().toLowerCase().contains(term)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<JavaClass> searchByCode(List<JavaClass> allClasses, String term,
            String packageFilter, boolean applyPackageFilter) {
        return allClasses.parallelStream()
                .filter(cls -> {
                    try {
                        if (applyPackageFilter && !matchesPackageFilter(cls, packageFilter)) {
                            return false;
                        }
                        String code = cls.getCode();
                        return code != null && code.toLowerCase().contains(term);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<JavaClass> searchByComment(List<JavaClass> allClasses, String term,
            String packageFilter, boolean applyPackageFilter) {
        Pattern singleLineComment = Pattern.compile("//.*?" + Pattern.quote(term) + ".*", Pattern.CASE_INSENSITIVE);
        Pattern multiLineComment = Pattern.compile("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", Pattern.DOTALL);

        return allClasses.parallelStream()
                .filter(cls -> {
                    try {
                        if (applyPackageFilter && !matchesPackageFilter(cls, packageFilter)) {
                            return false;
                        }
                        String code = cls.getCode();
                        if (code == null) {
                            return false;
                        }

                        if (singleLineComment.matcher(code).find()) {
                            return true;
                        }

                        java.util.regex.Matcher matcher = multiLineComment.matcher(code);
                        while (matcher.find()) {
                            String comment = matcher.group();
                            if (comment.toLowerCase().contains(term)) {
                                return true;
                            }
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

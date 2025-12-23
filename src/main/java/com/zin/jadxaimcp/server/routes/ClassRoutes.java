package com.zin.jadxaimcp.server.routes;

import io.javalin.http.Context;

import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.gui.JadxWrapper;
import jadx.gui.ui.MainWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zin.jadxaimcp.utils.PaginationUtils;
import com.zin.jadxaimcp.utils.PaginationUtils.PaginationException;
import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;

public class ClassRoutes {
    private static final Logger logger = LoggerFactory.getLogger(ClassRoutes.class);
    private final MainWindow mainWindow;
    private final PaginationUtils paginationUtils;

    public ClassRoutes(MainWindow mainWindow, PaginationUtils paginationUtils) {
        this.mainWindow = mainWindow;
        this.paginationUtils = paginationUtils;
    }

    // ------------------------------- Request Handlers --------------------------

    /**
     * @param   Context
     * @return  void
     * 
     * This handler method handle the /current-class api call, 
     * It return currently open/active/visible class code in UI in jadx.
     * Using helper methods getSelectedTabTitle() and extractTextFromCurrentTab() it gets
     * the title of UI component holding class code and then using that UI component extracts
     * the text from  that UI component.
     * 
     * After getting the code it returns it.
     */
    public void handleCurrentClass(Context ctx) {
        try {
            String className = getSelectedTabTitle();
            String code = extractTextFromCurrentTab();

            Map<String, String> result = new HashMap<>();
            result.put("name", className != null ? className.replace(".java", "") : "unknown");
            result.put("type", "code/java");
            result.put("content", code != null ? code : "");

            ctx.json(result);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal Error while trying to fetch current class class: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @return
     * @param Context
     * 
     * This routing method returns all classes decompiled from apk by jadx
     * It first fetches the list of JavaClass classes using JadxWrapper.
     * Then it combines this JavaClass list into Map and uses pagination utils to return the 
     * details of all classes.
     */
    public void handleAllClasses(Context ctx) {
        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            List<JavaClass> classes = wrapper.getIncludedClassesWithInners();

            Map<String, Object> result = paginationUtils.handlePagination(
                ctx,
                classes,
                "class-list",
                "classes",
                JavaClass::getFullName
            );
            ctx.json(result);
        } catch (PaginationException e) {
            JadxAIMCPPluginError.handleError(ctx, "Pagination Error: " + e.getMessage(), e, logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Failed to load class list: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param Context
     * @return
     * 
     * This routing method handles the /selected-text api call
     * it first gets the currently selecte UI component using MainWindow's methods
     * Then it find the text area from the currently active UI component, this text area
     * holds the selected text.
     * 
     * From this text area, it fetches the selected text using getSelectedText() method and
     * returns this using Map and ctx.
     */
    public void handleSelectedText(Context ctx) {
        try {
            Component selectedComponent = mainWindow.getTabbedPane().getSelectedComponent();
            JTextArea textArea = findTextArea(selectedComponent);
            String selectedText = textArea != null ? textArea.getSelectedText() : null;

            Map<String, String> result = new HashMap<>();
            result.put("selectedText", selectedText != null ? selectedText : "");
            ctx.json(result);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error while trying to fetch selected text: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param Context
     * @return void
     * 
     * This routing method handles the /class-source MCP tool call
     * First it checks for request validity, the check is availability of
     * 'class' parameter in http request, then it fetches the source code of the class
     * by fetching the classes one by one and compares it with the requested class name, if 
     * it matches returns the requested classe's code.
     */
    public void handleClassSource(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) return;

        // Removing this line to solve issue #37 as raised and contributed by
        // github@ljt270864457
        // This solves following bug -> Bug: Inner classes with $ symbol cannot be
        // retrieved via /class-source endpoint
        // className = className.replace('$', '.');

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)){
                    ctx.result(cls.getCode());
                    return;
                }
            }
            ctx.status(404).json(Map.of("error", "Class " + className + " not found"));
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving class source: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param Context
     * @return void
     * 
     * This routing method handles the /methods-of-class endpoint.
     * First it checks whether the 'class_name' parameter is present or not in http request
     * then it iterates over each class present in jadx, and matches it for the `class_name`'s value
     * Then once the requested class is found, it iterates over the methods of that class and gathers 
     * their details. 
     * 
     * After gathering the details it returns the methods details.
     */
    public void handleMethodsOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) return;

        // Removing this line to solve issue #37 as raised and contributed by
        // github@ljt270864457
        // This solves following bug -> Bug: Inner classes with $ symbol cannot be
        // retrieved via /methods-of-class endpoint
        // className = className.replace('$', '.');

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> methods = new ArrayList<>();
                    for (JavaMethod method : cls.getMethods()) {
                        String fullMethodName = cls.getFullName() + "." + method.getName();
                        String methodData = method.getAccessFlags() +
                        " " + method.getReturnType() + 
                        " " + method.getName() + 
                        " " + method.getMethodNode() + 
                        " " + fullMethodName;
                        methods.add(methodData);
                    }
                    ctx.result(String.join("\n", methods));
                    return;
                }
            }
            JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving methods: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param Context
     * @return void
     * 
     * This routing method handles the /fields-of-class mcp tool call
     * After checking for presence of 'class_name' parameter, it finds the class with 
     * 'class_name' name, after finding the requested class, it fetches the fields of class 
     * starts gathering their details.
     * 
     * Then it return these details.
     */
    public void handleFieldsOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) return;

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    List<String> fields = new ArrayList<>();
                    for (JavaField field : cls.getFields()) {
                        String fieldData = field.getAccessFlags() + 
                        " " + field.getType() +
                        " " + field.getName();
                        fields.add(fieldData);
                    }
                    ctx.result(String.join("\n", fields));
                    return;
                }
            }
            JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving fields: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param Context
     * @return void
     * 
     * This routing method handles the /smali-of-class mcp tool call
     * After checking for availability of 'class' parameter in request, it finds that class,
     * After finding that class it fetch smali of that class and returns it.
     */
    public void handleSmaliOfClass(Context ctx) {
        String className = checkClassParam(ctx);
        if (className == null) return;

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    ctx.result(cls.getSmali());
                    return;
                }
            }
            JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error retrieving smali: " + e.getMessage(), e, logger);
        }
    }



    // -------------------------------- Helper methods ----------------------------
    
    /**
     * @param Context
     * @return String
     * 
     * Checks if the HTTP request contains the 'class_name' param or not, if yes then returns it,
     * else returns null
     */
    private String checkClassParam(Context ctx) {
        String className = ctx.queryParam("class_name");
        if (className == null || className.isEmpty()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing required parameter 'class_name'", logger);
            return null;
        }
        return className;
    }

    /**
     * @param
     * @return String
     * 
     * This helper method extracts the selected(currently open class's UI tab)'s
     * title. First it checks whether the mainWindow is null or not if it is null then
     * return null.
     * 
     * Then first it gets's the index of TabbedPane if it is -1 then it is not valid/ there
     * is no selected class UI. Else it extracts title of tab using it's index and returns it 
     * as String.
     */
    private String getSelectedTabTitle() {
        if (mainWindow == null || mainWindow.getTabbedPane() == null) return null;
        
        int index = mainWindow.getTabbedPane().getSelectedIndex();
        if (index != -1) {
            return mainWindow.getTabbedPane().getTitleAt(index);
        }

        return null;
    }

    /**
     * @param 
     * @return String
     * 
     * This helper method extracts the text from current tab (active tab in UI) in other
     * words, UI where we see class code.
     * 
     * After checking for mainWindow's state for `null`, it first creates the Component object
     * to store the current tab ( UI where we see class code ), Then using findTextArea() it 
     * extracts all text (class code) from it and return it via textArea.getText() method after 
     * checking for null.
     */
    private String extractTextFromCurrentTab() {
        if (mainWindow == null) return null;

        Component component = mainWindow.getTabbedPane().getSelectedComponent();
        JTextArea textArea = findTextArea(component);

        return textArea != null ? textArea.getText() : null;
    }

    /**
     * @return JTextArea
     * @param Component
     * Recursively searches for a JTextArea (or compatible component) inside the given container.
     * 
     * This helper method is used in extractTextFromCurrentTab() method. It takes the UI component
     * and recursively check if there is any JTextArea in that UI compoenet, if yes then return it
     * else return null
     */
    private JTextArea findTextArea(Component component) {
        if (component instanceof JTextArea) {
            return (JTextArea) component;
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JTextArea found = findTextArea(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

}

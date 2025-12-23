package com.zin.jadxaimcp.server.routes;

import io.javalin.http.Context;

import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.plugins.events.types.NodeRenamedByUser;
import jadx.gui.JadxWrapper;
import jadx.gui.ui.MainWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;

public class RefactoringRoutes {
    private static final Logger logger = LoggerFactory.getLogger(RefactoringRoutes.class);
    private final MainWindow mainWindow;
    
    public RefactoringRoutes(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
    }

    /**
     * @return void
     * @param Context
     * 
     * This routing method handle the /rename-class mcp tool call's http request, After validating the 
     * required http params, it tries to find the class which has to be renamed. If it is found
     * then it renames it using NodeRenamedByUser class' events methods 'setRenameNode' and 'setResetName'.
     * Then it sends these events using MainWindows's send() method.
     */
    public void handleRenameClass(Context ctx) {
        String className = ctx.queryParam("class_name");
        String newName = ctx.queryParam("new_name");

        if (validateParams(ctx, className, newName)) return;

        try {
            JadxWrapper wrapper = mainWindow.getWrapper();
            for (JavaClass cls : wrapper.getIncludedClassesWithInners()) {
                if (cls.getFullName().equals(className)) {
                    ICodeNodeRef nodeRef = cls.getCodeNodeRef();
                    NodeRenamedByUser event = new NodeRenamedByUser(nodeRef, cls.getName(), newName);
                    event.setRenameNode(cls.getClassNode());
                    event.setResetName(newName.isEmpty());
                    mainWindow.events().send(event);

                    logger.info("Renaming Class {} to {}", cls.getName(), newName);
                    ctx.json(Map.of("result", "Renamed Class " + cls.getName() + " to " + newName));
                    return;
                }
            }
            JadxAIMCPPluginError.handleError(ctx, 404, "Class " + className + " not found.", logger);
        } catch (Exception e) {
            JadxAIMCPPluginError.handleError(ctx, "Internal error while trying to rename the class: " + e.getMessage(), e, logger);
        }
    }

    /**
     * @param
     * @return
     * 
     * 
     */


    // Helper methods

    /**
     * @param Context, String, String
     * @return boolean
     * 
     * This method is used to validate the availability of required http params in RefactoringRoutes
     * MCP tool's HTTP requests. If params are ok return true else return false.
     */
    private boolean validateParams(Context ctx, String p1, String p2) {
        if (p1 == null || p1.isEmpty() || p2 == null || p2.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Missing required parameters."));
            return true;
        }
        return false;
    }

}

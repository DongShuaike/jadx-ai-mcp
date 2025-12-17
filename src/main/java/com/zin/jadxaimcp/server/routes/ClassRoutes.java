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
     * @name    handleCurrentClass
     * @param   ctx
     * @return  void
     */
    public void handleCurrentClass(Context ctx) {
        try {
            String className = getSelectedTabTitle();
        } catch (Exception e) {}
    }


    // -------------------------------- Helper methods ----------------------------
    
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

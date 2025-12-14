package com.zin.jadxaimcp.ui;

import com.zin.jadxaimcp.JadxAIMCP;
import jadx.gui.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class PluginMenu {
    private static final Logger logger = LoggerFactory.getLogger(PluginMenu.class);
    private MainWindow mainWindow;
    private final JadxAIMCP plugin;

    public PluginMenu(MainWindow mainWindow, JadxAIMCP plugin) {
        this.mainWindow = mainWindow;
        this.plugin = plugin;
    }

    public void addMenuItems() {
        SwingUtilities.invokeLater(() -> {
            try {
                JMenuBar menuBar = mainWindow.getJMenuBar();
                if (menuBar == null) {
                    logger.warn("JADX-AI-MCP Plugin: Menu bar not found");
                    return;
                }

                JMenu pluginsMenu = findOrCreatePluginsMenu(menuBar);
                JMenu mcpMenu = new JMenu("JADX AI MCP Server");

                // 1. Configure Port
                JMenuItem portItem = new JMenuItem("Configure Port...");
                portItem.addActionListener(e -> showPortConfigDialog());

                // 2. Default Port
                JMenuItem defaultPortItem = new JMenuItem("Default Port");
                defaultPortItem.addActionListener(e -> {
                    plugin.resetToDefaultPort();
                    plugin.restartServer();
                });

                // 3. Restart Server
                JMenuItem restartItem = new JMenuItem("Restart Server");
                restartItem.addActionListener(e -> plugin.restartServer());

                // 4. Server Status
                JMenuItem statusItem = new JMenuItem("Server Status");
                statusItem.addActionListener(e -> showServerStatus());

                mcpMenu.add(portItem);
                mcpMenu.add(defaultPortItem);
                mcpMenu.addSeparator();
                mcpMenu.add(restartItem);
                mcpMenu.add(statusItem);
                pluginsMenu.add(mcpMenu);
                
                logger.debug("JADX-AI-MCP Plugin: Menu items added");
            } catch (Exception e) {

            }
        });
    }
    
    private JMenu findOrCreatePluginsMenu(JMenuBar menuBar) {
        // Look for existing "Plugins" menu
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && ("Plugins".equals(menu.getText()) || "Plugin".equals(menu.getText()))) {
                return menu;
            }
        }

        // Create new if not found, inserting before "Help" if possible
        JMenu pluginsMenu = new JMenu("Plugins");
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            if ("Help".equals(menuBar.getMenu(i).getText())) {
                menuBar.add(pluginsMenu, i);
                return pluginsMenu;
            }
        }
        menuBar.add(pluginsMenu);
        return pluginsMenu;
    }

    private void showPortConfigDialog() {
        String input = JOptionPane.showInputDialog(mainWindow,
            "Enter Server Port (1024-65535):", String.valueOf(plugin.getCurrentPort())
        );

        if (input != null) {
            try {
                int newPort = Integer.parseInt(input.trim());
                if (newPort >= 1024 && newPort <= 65535) {
                    if (newPort != plugin.getCurrentPort()) {
                        plugin.updatePort(newPort);
                        plugin.restartServer();
                    }
                } else {
                    JOptionPane.showMessageDialog(mainWindow, "Port must be between 1024 and 65535",
                        "Ivalid Port", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(mainWindow, "Invalid number format",
                    "Error", JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void showServerStatus() {
        boolean running = plugin.isServerRunning();
        String status = running ? "Running" : "Stopped";
        String url = running ? "http://127.0.0.1:" + plugin.getCurrentPort() + "/" : "N/A";

        JOptionPane.showMessageDialog(mainWindow,
            "Status " + status + "\nPort: " + plugin.getCurrentPort() + "\nURL: " + url,
            "MCP Server Status", JOptionPane.INFORMATION_MESSAGE);
    }
}

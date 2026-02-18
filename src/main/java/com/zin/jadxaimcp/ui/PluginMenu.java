package com.zin.jadxaimcp.ui;

import com.zin.jadxaimcp.JadxAIMCP;
import jadx.gui.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class PluginMenu {
    private static final Logger logger = LoggerFactory.getLogger(PluginMenu.class);
    private MainWindow mainWindow;
    private final JadxAIMCP plugin;

    public PluginMenu(MainWindow mainWindow, JadxAIMCP plugin) {
        this.mainWindow = mainWindow;
        this.plugin = plugin;
    }

    /**
     * @return void
     * 
     * This method creates and adds plugin UI menu items to the JADX menu bar.
     * 1. It runs on the Swing EDT thread using SwingUtilities.invokeLater()
     * 2. It retrieves the main window's menu bar
     * 3. It finds or creates a "Plugins" menu in the menu bar
     * 4. It creates a "JADX AI MCP Server" submenu with the following items:
     *    - Configure Port: Opens dialog to change server port
     *    - Default Port: Resets to default port (8650) and restarts server
     *    - Restart Server: Manually restarts the MCP server
     *    - Server Status: Shows current server status and connection details
     * 5. It adds the submenu to the plugins menu
     * 
     * All UI operations are performed on the EDT to ensure thread safety.
     */
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

                // 2. Configure Host
                JMenuItem hostItem = new JMenuItem("Configure Host...");
                hostItem.addActionListener(e -> showHostConfigDialog());

                // 3. Remote Mode Toggle
                JCheckBoxMenuItem remoteModeItem = new JCheckBoxMenuItem("Enable Remote Mode");
                remoteModeItem.setSelected(plugin.isRemoteModeEnabled());
                remoteModeItem.addActionListener(e -> toggleRemoteMode(remoteModeItem.isSelected()));

                // 4. Reset secure defaults
                JMenuItem defaultsItem = new JMenuItem("Reset to Local Defaults");
                defaultsItem.addActionListener(e -> {
                    plugin.resetToSecureDefaults();
                    plugin.restartServer();
                });

                // 5. Restart Server
                JMenuItem restartItem = new JMenuItem("Restart Server");
                restartItem.addActionListener(e -> plugin.restartServer());

                // 6. Rotate one-time token
                JMenuItem rotateTokenItem = new JMenuItem("Rotate One-Time Token");
                rotateTokenItem.addActionListener(e -> rotateOneTimeToken());

                // 7. Server Status
                JMenuItem statusItem = new JMenuItem("Server Status");
                statusItem.addActionListener(e -> showServerStatus());

                mcpMenu.add(portItem);
                mcpMenu.add(hostItem);
                mcpMenu.add(remoteModeItem);
                mcpMenu.add(defaultsItem);
                mcpMenu.addSeparator();
                mcpMenu.add(restartItem);
                mcpMenu.add(rotateTokenItem);
                mcpMenu.add(statusItem);
                pluginsMenu.add(mcpMenu);
                
                logger.debug("JADX-AI-MCP Plugin: Menu items added");
            } catch (Exception e) {

            }
        });
    }
    
    /**
     * @param menuBar The main window's menu bar
     * @return JMenu The existing or newly created Plugins menu
     * 
     * This method locates or creates the "Plugins" menu in the JADX menu bar.
     * 1. It searches through existing menus for one named "Plugins" or "Plugin"
     * 2. If found, it returns the existing menu
     * 3. If not found, it creates a new "Plugins" menu
     * 4. It attempts to insert the new menu before the "Help" menu if it exists
     * 5. Otherwise, it appends the menu to the end of the menu bar
     * 
     * This ensures consistent menu organization across different JADX versions.
     */
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

    /**
     * @return void
     * 
     * This method displays a dialog for configuring the server port.
     * 1. It shows an input dialog with the current port as default value
     * 2. It validates the input is a valid integer
     * 3. It ensures the port is in the valid range (1024-65535)
     * 4. If the port differs from current port, it:
     *    - Updates the port configuration
     *    - Restarts the server on the new port
     * 5. It displays error messages for invalid input or out-of-range values
     * 
     * Ports below 1024 are reserved and require root privileges.
     */
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

    private void showHostConfigDialog() {
        String input = JOptionPane.showInputDialog(mainWindow,
                "Enter bind host (example: 127.0.0.1, 0.0.0.0, or cloud IP):",
                plugin.getCurrentHost());

        if (input == null) {
            return;
        }

        try {
            String newHost = input.trim();
            if (newHost.isEmpty()) {
                JOptionPane.showMessageDialog(mainWindow, "Host must not be empty",
                        "Invalid Host", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!newHost.equals(plugin.getCurrentHost())) {
                plugin.updateHost(newHost);
                plugin.restartServer();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainWindow, "Failed to update host: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleRemoteMode(boolean enabled) {
        plugin.setRemoteModeEnabled(enabled);
        plugin.restartServer();
        String mode = enabled ? "enabled" : "disabled";
        String hint = enabled
                ? "\nA one-time token was printed in logs. Use SSH tunnel + Authorization header."
                : "\nServer is restricted to local-safe defaults if needed.";
        JOptionPane.showMessageDialog(mainWindow,
                "Remote mode " + mode + "." + hint,
                "Remote Mode", JOptionPane.INFORMATION_MESSAGE);
    }

    private void rotateOneTimeToken() {
        if (!plugin.isAuthRequired()) {
            JOptionPane.showMessageDialog(mainWindow,
                    "Remote mode is disabled. Token rotation is not available.",
                    "Token Rotation", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean rotated = plugin.rotateOneTimeToken();
        if (!rotated) {
            JOptionPane.showMessageDialog(mainWindow,
                    "Server is not running. Start/restart the server first.",
                    "Token Rotation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(mainWindow,
                "Token rotated. New one-time token was printed in logs.",
                "Token Rotation", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * @return void
     * 
     * This method displays the current server status in a dialog.
     * 1. It checks whether the server is currently running
     * 2. It retrieves the configured port number
     * 3. It constructs the server URL if running (http://127.0.0.1:<port>/)
     * 4. It displays a dialog showing:
     *    - Status (Running/Stopped)
     *    - Port number
     *    - Server URL (or N/A if stopped)
     * 
     * This provides users with quick access to connection information.
     */
    private void showServerStatus() {
        boolean running = plugin.isServerRunning();
        String status = running ? "Running" : "Stopped";
        String url = running ? plugin.getServerUrl() : "N/A";
        String authMode = plugin.isAuthRequired() ? "Enabled" : "Disabled";
        String remoteMode = plugin.isRemoteModeEnabled() ? "Enabled" : "Disabled";
        String tokenHint = plugin.isAuthRequired() ? plugin.getMaskedToken() : "N/A";

        JOptionPane.showMessageDialog(mainWindow,
            "Status: " + status
                    + "\nHost: " + plugin.getCurrentHost()
                    + "\nPort: " + plugin.getCurrentPort()
                    + "\nURL: " + url
                    + "\nRemote Mode: " + remoteMode
                    + "\nAuth Required: " + authMode
                    + "\nToken Hint: " + tokenHint,
            "MCP Server Status", JOptionPane.INFORMATION_MESSAGE);
    }
}

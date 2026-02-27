package com.sh3d.mcp.plugin;

import com.sh3d.mcp.config.ClaudeDesktopConfigurator;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.server.ServerState;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Диалог настроек MCP-сервера: управление сервером + автоконфигурация Claude Desktop.
 */
public class McpSettingsDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(McpSettingsDialog.class.getName());

    static final String REPO_URL = "https://github.com/grimashevich/sweethome3d-mcp-server";
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/grimashevich/sweethome3d-mcp-server/releases/latest";

    private final HttpMcpServer httpServer;
    private final String pluginVersion;

    private JLabel statusLabel;
    private JLabel versionLabel;
    private JButton checkUpdatesButton;
    private JTextField portField;
    private JButton toggleButton;
    private JTextArea jsonArea;
    private JButton configureButton;

    public McpSettingsDialog(Frame owner, HttpMcpServer httpServer) {
        super(owner, "MCP Server Settings", false);
        this.httpServer = httpServer;
        this.pluginVersion = readPluginVersion();

        initComponents();
        layoutComponents();
        updateState(httpServer.getState());

        httpServer.addStateListener((oldState, newState) ->
                SwingUtilities.invokeLater(() -> updateState(newState)));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        versionLabel = new JLabel("Version: " + pluginVersion);
        versionLabel.setForeground(Color.GRAY);

        checkUpdatesButton = new JButton("Check for updates");
        checkUpdatesButton.addActionListener(e -> onCheckForUpdates());

        portField = new JTextField(String.valueOf(httpServer.getPort()), 8);

        toggleButton = new JButton();
        toggleButton.addActionListener(e -> onToggle());

        jsonArea = new JTextArea(8, 42);
        jsonArea.setEditable(false);
        jsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        updateJsonArea();
        installContextMenu(jsonArea);

        // Обновлять JSON при изменении порта
        portField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
        });

        configureButton = new JButton("Auto-configure Claude Desktop");
        configureButton.setToolTipText("Write MCP config to Claude Desktop config file (with .bak backup)");
        configureButton.addActionListener(e -> onAutoConfigure());
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // --- Server panel ---
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Server",
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Status row
        gbc.gridx = 0; gbc.gridy = 0;
        serverPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(statusLabel, gbc);

        // Version row
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel("Version:"), gbc);
        gbc.gridx = 1;
        serverPanel.add(versionLabel, gbc);
        gbc.gridx = 2;
        serverPanel.add(checkUpdatesButton, gbc);

        // Port row
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        serverPanel.add(portField, gbc);
        gbc.gridx = 2;
        serverPanel.add(toggleButton, gbc);

        content.add(serverPanel, BorderLayout.NORTH);

        // --- MCP Configuration panel ---
        JPanel claudePanel = new JPanel(new BorderLayout(0, 6));
        claudePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "MCP Configuration",
                TitledBorder.LEFT, TitledBorder.TOP));

        JScrollPane scrollPane = new JScrollPane(jsonArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        claudePanel.add(scrollPane, BorderLayout.CENTER);

        JPanel claudeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> onCopyJson());
        claudeButtons.add(copyButton);
        claudeButtons.add(configureButton);

        // Показать путь к конфигу
        Path configPath = ClaudeDesktopConfigurator.resolveConfigPath();
        if (configPath != null) {
            JLabel pathLabel = new JLabel(configPath.toString());
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.ITALIC, 10f));
            pathLabel.setForeground(Color.GRAY);
            JPanel claudeBottom = new JPanel(new BorderLayout(0, 4));
            claudeBottom.add(claudeButtons, BorderLayout.NORTH);
            claudeBottom.add(pathLabel, BorderLayout.SOUTH);
            claudePanel.add(claudeBottom, BorderLayout.SOUTH);
        } else {
            claudePanel.add(claudeButtons, BorderLayout.SOUTH);
        }

        content.add(claudePanel, BorderLayout.CENTER);

        // --- Bottom panel: GitHub link (left) + Close (right) ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JLabel repoLink = createLinkLabel("GitHub Repository \u2197", REPO_URL);
        repoLink.setFont(repoLink.getFont().deriveFont(11f));
        bottomPanel.add(repoLink, BorderLayout.WEST);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.add(closeButton);
        bottomPanel.add(closeWrap, BorderLayout.EAST);

        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void updateState(ServerState state) {
        switch (state) {
            case RUNNING:
                statusLabel.setText("\u25CF Running on port " + httpServer.getPort());
                statusLabel.setForeground(new Color(0, 128, 0));
                toggleButton.setText("Stop");
                toggleButton.setEnabled(true);
                portField.setEnabled(false);
                break;
            case STOPPED:
                statusLabel.setText("\u25CF Stopped");
                statusLabel.setForeground(Color.GRAY);
                toggleButton.setText("Start");
                toggleButton.setEnabled(true);
                portField.setEnabled(true);
                // Показать ошибку при неудачном старте
                Exception error = httpServer.getLastStartupError();
                if (error != null) {
                    showError("Failed to start server on port " + httpServer.getPort()
                            + ":\n" + error.getMessage());
                }
                break;
            case STARTING:
                statusLabel.setText("\u25CF Starting...");
                statusLabel.setForeground(new Color(200, 150, 0));
                toggleButton.setEnabled(false);
                portField.setEnabled(false);
                break;
            case STOPPING:
                statusLabel.setText("\u25CF Stopping...");
                statusLabel.setForeground(new Color(200, 150, 0));
                toggleButton.setEnabled(false);
                portField.setEnabled(false);
                break;
        }
    }

    private void onToggle() {
        if (httpServer.getState() == ServerState.STOPPED) {
            int port = parsePort();
            if (port < 0) return;

            if (port != httpServer.getPort()) {
                httpServer.setPort(port);
            }
            httpServer.start();
        } else {
            httpServer.stop();
        }
    }

    private void onCheckForUpdates() {
        checkUpdatesButton.setEnabled(false);
        checkUpdatesButton.setText("Checking...");

        Thread t = new Thread(() -> {
            String latest = fetchLatestVersion();
            SwingUtilities.invokeLater(() -> {
                checkUpdatesButton.setEnabled(true);
                checkUpdatesButton.setText("Check for updates");

                if (latest == null) {
                    JOptionPane.showMessageDialog(this,
                            "Could not check for updates.\nPlease check your internet connection.",
                            "Update check failed", JOptionPane.WARNING_MESSAGE);
                } else if (isNewerVersion(latest, pluginVersion)) {
                    int choice = JOptionPane.showConfirmDialog(this,
                            "Update available: v" + latest
                                    + "\nCurrent version: v" + pluginVersion
                                    + "\n\nOpen release page?",
                            "Update available",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        openUrl(REPO_URL + "/releases/tag/v" + latest);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "You have the latest version (v" + pluginVersion + ").",
                            "Up to date", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }, "mcp-update-check");
        t.setDaemon(true);
        t.start();
    }

    private String fetchLatestVersion() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            // Extract "tag_name" value without external JSON library
            String json = sb.toString();
            int idx = json.indexOf("\"tag_name\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int start = json.indexOf('"', colon) + 1;
            int end = json.indexOf('"', start);
            if (start <= 0 || end <= start) return null;
            String tag = json.substring(start, end);
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Update check failed", e);
            return null;
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(l.length, c.length);
            for (int i = 0; i < len; i++) {
                int lv = i < l.length ? Integer.parseInt(l[i].trim()) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i].trim()) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private JLabel createLinkLabel(String text, String url) {
        JLabel label = new JLabel("<html><a href=''>" + text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl(url);
            }
        });
        return label;
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not open URL: " + url, e);
        }
    }

    private int parsePort() {
        String text = portField.getText().trim();
        try {
            int port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535.");
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            showError("Invalid port number: " + text);
            return -1;
        }
    }

    private int getDisplayPort() {
        String text = portField.getText().trim();
        try {
            int port = Integer.parseInt(text);
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
        }
        return httpServer.getPort();
    }

    private void updateJsonArea() {
        jsonArea.setText(ClaudeDesktopConfigurator.generateMcpJson(getDisplayPort()));
        jsonArea.setCaretPosition(0);
    }

    private void onCopyJson() {
        String json = jsonArea.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(json), null);
        JOptionPane.showMessageDialog(this, "JSON copied to clipboard.",
                "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onAutoConfigure() {
        int port = getDisplayPort();
        int choice = JOptionPane.showConfirmDialog(this,
                "This will merge the MCP server config into Claude Desktop's\n"
                        + "configuration file. A .bak backup will be created.\n\n"
                        + "Proceed?",
                "Auto-configure Claude Desktop",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) return;

        try {
            ClaudeDesktopConfigurator.ConfigureResult result =
                    ClaudeDesktopConfigurator.configure(port);
            String msg = result.isCreated()
                    ? "Configuration file created:\n" + result.getConfigPath()
                    : "Configuration updated:\n" + result.getConfigPath()
                    + "\nBackup: " + result.getBackupPath();
            JOptionPane.showMessageDialog(this, msg,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Auto-configure failed", ex);
            showError("Failed to configure Claude Desktop:\n" + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void installContextMenu(JTextArea area) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> area.copy());
        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.addActionListener(e -> area.selectAll());
        menu.add(copyItem);
        menu.addSeparator();
        menu.add(selectAllItem);
        area.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    copyItem.setEnabled(area.getSelectedText() != null);
                    menu.show(area, e.getX(), e.getY());
                }
            }
        });
    }

    private static String readPluginVersion() {
        try (InputStream in = McpSettingsDialog.class
                .getResourceAsStream("/ApplicationPlugin.properties")) {
            if (in == null) return "unknown";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}

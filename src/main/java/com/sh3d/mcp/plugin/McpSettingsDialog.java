package com.sh3d.mcp.plugin;

import com.sh3d.mcp.config.ClaudeDesktopConfigurator;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.http.McpEndpointUrls;
import com.sh3d.mcp.server.ServerState;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
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
import java.text.MessageFormat;
import java.util.Properties;
import java.util.ResourceBundle;
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
    private final ResourceBundle bundle;
    private final String pluginVersion;

    private JLabel statusLabel;
    private JLabel versionLabel;
    private JButton checkUpdatesButton;
    private JTextField portField;
    private JTextField sseEndpointField;
    private JTextField mcpEndpointField;
    private JButton toggleButton;
    private JTabbedPane configTabs;
    private JTextArea mcpJsonArea;
    private JTextArea claudeJsonArea;
    private JButton configureButton;

    public McpSettingsDialog(Frame owner, HttpMcpServer httpServer, ResourceBundle bundle) {
        super(owner, bundle.getString("dialog.title"), false);
        this.httpServer = httpServer;
        this.bundle = bundle;
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

        versionLabel = new JLabel(MessageFormat.format(bundle.getString("dialog.version.text"), pluginVersion));
        versionLabel.setForeground(Color.GRAY);

        checkUpdatesButton = new JButton(bundle.getString("dialog.button.checkUpdates"));
        checkUpdatesButton.addActionListener(e -> onCheckForUpdates());

        portField = new JTextField(String.valueOf(httpServer.getPort()), 8);
        sseEndpointField = createReadOnlyField();
        mcpEndpointField = createReadOnlyField();

        toggleButton = new JButton();
        toggleButton.addActionListener(e -> onToggle());

        mcpJsonArea = createConfigArea();
        claudeJsonArea = createConfigArea();
        configTabs = new JTabbedPane();
        configTabs.addTab(bundle.getString("dialog.config.tab.mcp"), wrapConfigArea(mcpJsonArea));
        configTabs.addTab(bundle.getString("dialog.config.tab.claude"), wrapConfigArea(claudeJsonArea));
        updateConfigViews();

        // Обновлять URL и config snippets при изменении порта
        portField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateConfigViews(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateConfigViews(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateConfigViews(); }
        });

        configureButton = new JButton(bundle.getString("dialog.button.autoConfigure"));
        configureButton.setToolTipText(bundle.getString("dialog.button.autoConfigure.tooltip"));
        configureButton.addActionListener(e -> onAutoConfigure());
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // --- Server panel ---
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), bundle.getString("dialog.server.border"),
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Status row
        gbc.gridx = 0; gbc.gridy = 0;
        serverPanel.add(new JLabel(bundle.getString("dialog.status.label")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(statusLabel, gbc);

        // Version row
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel(bundle.getString("dialog.version.label")), gbc);
        gbc.gridx = 1;
        serverPanel.add(versionLabel, gbc);
        gbc.gridx = 2;
        serverPanel.add(checkUpdatesButton, gbc);

        // Port row
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel(bundle.getString("dialog.port.label")), gbc);
        gbc.gridx = 1;
        serverPanel.add(portField, gbc);
        gbc.gridx = 2;
        serverPanel.add(toggleButton, gbc);

        // Compatibility endpoint row
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel(bundle.getString("dialog.endpoint.sse.label")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(sseEndpointField, gbc);

        // MCP endpoint row
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel(bundle.getString("dialog.endpoint.mcp.label")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(mcpEndpointField, gbc);

        content.add(serverPanel, BorderLayout.NORTH);

        // --- MCP Configuration panel ---
        JPanel claudePanel = new JPanel(new BorderLayout(0, 6));
        claudePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), bundle.getString("dialog.config.border"),
                TitledBorder.LEFT, TitledBorder.TOP));

        claudePanel.add(configTabs, BorderLayout.CENTER);

        JPanel claudeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton copyButton = new JButton(bundle.getString("dialog.button.copyClipboard"));
        copyButton.addActionListener(e -> onCopyConfig());
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
        JLabel repoLink = createLinkLabel(bundle.getString("dialog.link.github"), REPO_URL);
        repoLink.setFont(repoLink.getFont().deriveFont(11f));
        bottomPanel.add(repoLink, BorderLayout.WEST);

        JButton closeButton = new JButton(bundle.getString("dialog.button.close"));
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
                statusLabel.setText(MessageFormat.format(bundle.getString("dialog.status.running"), httpServer.getPort()));
                statusLabel.setForeground(new Color(0, 128, 0));
                toggleButton.setText(bundle.getString("dialog.button.stop"));
                toggleButton.setEnabled(true);
                portField.setEnabled(false);
                break;
            case STOPPED:
                statusLabel.setText(bundle.getString("dialog.status.stopped"));
                statusLabel.setForeground(Color.GRAY);
                toggleButton.setText(bundle.getString("dialog.button.start"));
                toggleButton.setEnabled(true);
                portField.setEnabled(true);
                // Показать ошибку при неудачном старте
                Exception error = httpServer.getLastStartupError();
                if (error != null) {
                    showError(MessageFormat.format(bundle.getString("dialog.error.startFailed"), httpServer.getPort(), error.getMessage()));
                }
                break;
            case STARTING:
                statusLabel.setText(bundle.getString("dialog.status.starting"));
                statusLabel.setForeground(new Color(200, 150, 0));
                toggleButton.setEnabled(false);
                portField.setEnabled(false);
                break;
            case STOPPING:
                statusLabel.setText(bundle.getString("dialog.status.stopping"));
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
        checkUpdatesButton.setText(bundle.getString("dialog.button.checking"));

        Thread t = new Thread(() -> {
            String latest = fetchLatestVersion();
            SwingUtilities.invokeLater(() -> {
                checkUpdatesButton.setEnabled(true);
                checkUpdatesButton.setText(bundle.getString("dialog.button.checkUpdates"));

                if (latest == null) {
                    JOptionPane.showMessageDialog(this,
                            bundle.getString("dialog.update.failed.message"),
                            bundle.getString("dialog.update.failed.title"), JOptionPane.WARNING_MESSAGE);
                } else if (isNewerVersion(latest, pluginVersion)) {
                    int choice = JOptionPane.showConfirmDialog(this,
                            MessageFormat.format(bundle.getString("dialog.update.available.message"), latest, pluginVersion),
                            bundle.getString("dialog.update.available.title"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        openUrl(REPO_URL + "/releases/tag/v" + latest);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            MessageFormat.format(bundle.getString("dialog.update.upToDate.message"), pluginVersion),
                            bundle.getString("dialog.update.upToDate.title"), JOptionPane.INFORMATION_MESSAGE);
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
                showError(bundle.getString("dialog.error.portRange"));
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            showError(MessageFormat.format(bundle.getString("dialog.error.portInvalid"), text));
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

    private void updateConfigViews() {
        int port = getDisplayPort();
        sseEndpointField.setText(McpEndpointUrls.sseLocalhostUrl(port));
        sseEndpointField.setCaretPosition(0);
        mcpEndpointField.setText(McpEndpointUrls.mcpLocalhostUrl(port));
        mcpEndpointField.setCaretPosition(0);

        mcpJsonArea.setText(ClaudeDesktopConfigurator.generateStreamableHttpJson(port));
        mcpJsonArea.setCaretPosition(0);
        claudeJsonArea.setText(ClaudeDesktopConfigurator.generateClaudeDesktopJson(port));
        claudeJsonArea.setCaretPosition(0);
    }

    private void onCopyConfig() {
        String json = getSelectedConfigArea().getText();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(json), null);
        JOptionPane.showMessageDialog(this, bundle.getString("dialog.clipboard.message"),
                bundle.getString("dialog.clipboard.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void onAutoConfigure() {
        int port = getDisplayPort();
        int choice = JOptionPane.showConfirmDialog(this,
                bundle.getString("dialog.configure.confirm.message"),
                bundle.getString("dialog.configure.confirm.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) return;

        try {
            ClaudeDesktopConfigurator.ConfigureResult result =
                    ClaudeDesktopConfigurator.configure(port);
            String msg = result.isCreated()
                    ? MessageFormat.format(bundle.getString("dialog.configure.created.message"), result.getConfigPath())
                    : MessageFormat.format(bundle.getString("dialog.configure.updated.message"), result.getConfigPath(), result.getBackupPath());
            JOptionPane.showMessageDialog(this, msg,
                    bundle.getString("dialog.configure.success.title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Auto-configure failed", ex);
            showError(MessageFormat.format(bundle.getString("dialog.configure.error"), ex.getMessage()));
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                bundle.getString("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    private JTextArea createConfigArea() {
        JTextArea area = new JTextArea(8, 42);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        installContextMenu(area);
        return area;
    }

    private JScrollPane wrapConfigArea(JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    private JTextField createReadOnlyField() {
        JTextField field = new JTextField(42);
        field.setEditable(false);
        installContextMenu(field);
        return field;
    }

    private JTextArea getSelectedConfigArea() {
        return configTabs.getSelectedIndex() == 0 ? mcpJsonArea : claudeJsonArea;
    }

    private void installContextMenu(JTextComponent area) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem(bundle.getString("dialog.context.copy"));
        copyItem.addActionListener(e -> area.copy());
        JMenuItem selectAllItem = new JMenuItem(bundle.getString("dialog.context.selectAll"));
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

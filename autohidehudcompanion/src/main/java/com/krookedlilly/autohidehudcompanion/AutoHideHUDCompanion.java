package com.krookedlilly.autohidehudcompanion;

import com.google.gson.Gson;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class AutoHideHUDCompanion extends JFrame {
    private Config config;
    private Socket socket;
    private BufferedReader in;
    private final String HOST = "localhost";
    private int PORT;

    private JLabel statusLabel;
    ColoredTextPane coloredPane;
    private JScrollPane scrollPane;
    private JPanel mainPanel;
    private JPanel buttonPanel;
    private JButton connectButton;
    private JButton fullscreenButton;
    private JButton closeButton;

    private Point mouseDownCompCoords = null;
    private boolean isFullscreen = false;
    private Rectangle normalBounds;
    private boolean isConnected = false;
    private Timer connectionAttemptsTimer;

    // Resize variables
    private Point resizeStart = null;
    private final Dimension minSize = new Dimension(300, 400);
    private Point currentSize = new Point(500, 400);
    private Point windowedLocation = new Point(0, 0);

    private Rectangle boundsAtResizeStart = null;
    private static final int RESIZE_MARGIN = 10;
    private int resizeDirection = 0; // 0=none, 1=N, 2=S, 3=E, 4=W, 5=NE, 6=NW, 7=SE, 8=SW
    private boolean isResizing = false;
    private Color focusedBGColor = new Color(0, 0, 0, 255);
    private int focusedBGOpacity = 100;
    private Color notFocusedBGColor = new Color(0, 0, 0, 255);
    private int notFocusedBGOpacity = 0;
    private volatile boolean isFocused = false;
    private String failedToConnectMessage = "";
    private int connectionAttemptDelay = 2000; // in ms

    private PlayerData lastPlayerData;

    public AutoHideHUDCompanion() {
        // Load configuration first
        config = new Config();
        PORT = config.getPort();

        setupFocusedBGColor();
        setupNotFocusedBGColor();
        setupTransparentWindow();
    }

    private void setupFocusedBGColor() {
        // Parse the background color from config
        String focusedColorHex = config.getFocusedBackgroundColor();
        if (!focusedColorHex.startsWith("#")) {
            focusedColorHex = "#" + focusedColorHex;
        }

        focusedBGOpacity = config.getFocusedBackgroundOpacity();
        float percent = focusedBGOpacity / 100f;
        int opacity = (int) (255 * percent);
        String paddedHexString = String.format("%02X", opacity);

        // Create a StringBuilder from the original string
        StringBuilder sb = new StringBuilder(focusedColorHex);
        // Insert the characters after the first character (at index 1)
        sb.insert(1, paddedHexString);

        // Convert the StringBuilder back to a String
        focusedColorHex = sb.toString();

        int rgba = (int) Long.parseLong(focusedColorHex.substring(1), 16);
        focusedBGColor = new Color(rgba, true);
    }

    private void setupNotFocusedBGColor() {
        // Parse the background color from config
        String notFocusedColorHex = config.getNotFocusedBackgroundColor();
        if (!notFocusedColorHex.startsWith("#")) {
            notFocusedColorHex = "#" + notFocusedColorHex;
        }

        notFocusedBGOpacity = config.getNotFocusedBackgroundOpacity();
        float percent = notFocusedBGOpacity / 100f;
        int opacity = (int) (255 * percent);
        String paddedHexString = String.format("%02X", opacity);

        // Create a StringBuilder from the original string
        StringBuilder sb = new StringBuilder(notFocusedColorHex);
        // Insert the characters after the first character (at index 1)
        sb.insert(1, paddedHexString);

        // Convert the StringBuilder back to a String
        notFocusedColorHex = sb.toString();

        int rgba = (int) Long.parseLong(notFocusedColorHex.substring(1), 16);
        notFocusedBGColor = new Color(rgba, true);
    }

    private void setupTransparentWindow() {
        // Make the window undecorated (no title bar)
        setUndecorated(true);

        // Set window always on top
        setAlwaysOnTop(true);

        setBackground(focusedBGColor);

        // Set size and location
        setSize(currentSize.x, currentSize.y);
        setMinimumSize(minSize);
        setLocationRelativeTo(null);
        windowedLocation = getLocation();
        normalBounds = getBounds();

        // Create main panel with semi-transparent background
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(new Color(0, 0, 0, 1)); // Semi-transparent black
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top panel with status and controls
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Status label
        statusLabel = new JLabel("Not Connected", SwingConstants.CENTER);
        statusLabel.setForeground(Color.YELLOW);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        topPanel.add(statusLabel, BorderLayout.CENTER);

        // Control buttons panel
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);

        // Connect button
        connectButton = new JButton("Connect");
        connectButton.setToolTipText("Connect to Auto Hide HUD Server");
        connectButton.setPreferredSize(new Dimension(110, 25));
        connectButton.addActionListener(e -> toggleConnectionAttempts());
        buttonPanel.add(connectButton);

        fullscreenButton = new JButton("⛶");
        fullscreenButton.setToolTipText("Toggle Fullscreen");
        fullscreenButton.setPreferredSize(new Dimension(45, 25));
        fullscreenButton.addActionListener(e -> toggleFullscreen());
        buttonPanel.add(fullscreenButton);

        closeButton = new JButton("✕");
        closeButton.setToolTipText("Close");
        closeButton.setPreferredSize(new Dimension(45, 25));
        closeButton.addActionListener(e -> {
            disconnect();

            if (connectionAttemptsTimer != null) {
                connectionAttemptsTimer.stop();
            }

            System.exit(0);
        });
        buttonPanel.add(closeButton);

        topPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Data display area
        coloredPane = new ColoredTextPane();
        scrollPane = new JScrollPane(coloredPane);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        // Add a ChangeListener to the Viewport
        scrollPane.getViewport().addChangeListener(e -> repaint());
        mainPanel.add(scrollPane);

        // Mouse listeners for dragging and resizing
        mainPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleFullscreen();
                }
            }

            public void mousePressed(MouseEvent e) {
                if (isFullscreen) return;

                int direction = getResizeDirection(e.getPoint());
                if (direction > 0) {
                    resizeStart = e.getLocationOnScreen();
                    boundsAtResizeStart = getBounds();
                    resizeDirection = direction;
                    isResizing = true;
                } else {
                    mouseDownCompCoords = e.getPoint();
                }
            }

            public void mouseReleased(MouseEvent e) {
                mouseDownCompCoords = null;
                resizeStart = null;
                resizeDirection = 0;
                isResizing = false;
            }
        });

        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                if (isFullscreen || isResizing) return;

                updateCursor(e.getPoint());
            }

            public void mouseDragged(MouseEvent e) {
                if (isFullscreen) return;

                if (resizeStart != null && isResizing) {
                    handleResize(e);
                } else if (mouseDownCompCoords != null) {
                    Point currCoords = e.getLocationOnScreen();
                    setLocation(currCoords.x - mouseDownCompCoords.x,
                            currCoords.y - mouseDownCompCoords.y);
                }
                windowedLocation = getLocation();
            }
        });

        add(mainPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", evt -> {
            if (evt.getNewValue() == null)
                windowLostFocus();
            else windowGainedFocus();
        });
    }

    private void updateCursor(Point p) {
        int direction = getResizeDirection(p);
        switch (direction) {
            case 1: // N
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                break;
            case 2: // S
                setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
                break;
            case 3: // E
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                break;
            case 4: // W
                setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                break;
            case 5: // NE
                setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
                break;
            case 6: // NW
                setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                break;
            case 7: // SE
                setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                break;
            case 8: // SW
                setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
                break;
            default:
                setCursor(Cursor.getDefaultCursor());
        }
    }

    private int getResizeDirection(Point p) {
        int w = getWidth();
        int h = getHeight();

        boolean nearTop = p.y <= RESIZE_MARGIN;
        boolean nearBottom = p.y >= h - RESIZE_MARGIN;
        boolean nearLeft = p.x <= RESIZE_MARGIN;
        boolean nearRight = p.x >= w - RESIZE_MARGIN;

        // Corners
        if (nearTop && nearLeft) return 6;    // NW
        if (nearTop && nearRight) return 5;   // NE
        if (nearBottom && nearLeft) return 8; // SW
        if (nearBottom && nearRight) return 7; // SE

        // Edges
        if (nearTop) return 1;    // N
        if (nearBottom) return 2; // S
        if (nearRight) return 3;  // E
        if (nearLeft) return 4;   // W

        return 0; // no resize
    }

    private void handleResize(MouseEvent e) {
        Point currentPos = e.getLocationOnScreen();
        int deltaX = currentPos.x - resizeStart.x;
        int deltaY = currentPos.y - resizeStart.y;

        Rectangle newBounds = new Rectangle(boundsAtResizeStart);

        switch (resizeDirection) {
            case 1: // N
                newBounds.y += deltaY;
                newBounds.height -= deltaY;
                break;
            case 2: // S
                newBounds.height += deltaY;
                break;
            case 3: // E
                newBounds.width += deltaX;
                break;
            case 4: // W
                newBounds.x += deltaX;
                newBounds.width -= deltaX;
                break;
            case 5: // NE
                newBounds.y += deltaY;
                newBounds.height -= deltaY;
                newBounds.width += deltaX;
                break;
            case 6: // NW
                newBounds.y += deltaY;
                newBounds.height -= deltaY;
                newBounds.x += deltaX;
                newBounds.width -= deltaX;
                break;
            case 7: // SE
                newBounds.height += deltaY;
                newBounds.width += deltaX;
                break;
            case 8: // SW
                newBounds.height += deltaY;
                newBounds.x += deltaX;
                newBounds.width -= deltaX;
                break;
        }

        // Apply minimum size constraints
        if (newBounds.width < getMinimumSize().width) {
            if (resizeDirection == 4 || resizeDirection == 6 || resizeDirection == 8) {
                newBounds.x = boundsAtResizeStart.x + boundsAtResizeStart.width - getMinimumSize().width;
            }
            newBounds.width = getMinimumSize().width;
        }

        if (newBounds.height < getMinimumSize().height) {
            if (resizeDirection == 1 || resizeDirection == 5 || resizeDirection == 6) {
                newBounds.y = boundsAtResizeStart.y + boundsAtResizeStart.height - getMinimumSize().height;
            }
            newBounds.height = getMinimumSize().height;
        }

        setBounds(newBounds);
        currentPos.x = newBounds.x;
        currentPos.y = newBounds.y;
        currentSize.x = newBounds.width;
        currentSize.y = newBounds.height;
    }

    private void toggleConnection() {
        if (isConnected) {
            disconnect();

            coloredPane.clearDocument();
            SwingUtilities.invokeLater(() -> {
                connectButton.setText("Connect");
                statusLabel.setText("Disconnected");
                statusLabel.setForeground(Color.YELLOW);
                repaint();
            });
        } else {
            try {
                connect();
                stopConnectionAttempts();
                startListening();
            } catch (IOException e) {
                if (failedToConnectMessage.equals(e.getMessage())) return;

                failedToConnectMessage = e.getMessage();
                ArrayList<ColoredTextPane.ColoredTextSegment> segments = new ArrayList<>();

                segments.add(new ColoredTextPane.ColoredTextSegment("Failed to connect: " + e.getMessage() + "\n", coloredPane.errorStyle));
                if (e.getCause() == null)
                    segments.add(new ColoredTextPane.ColoredTextSegment("server probably not running or incorrect port number, will retry every 2 seconds" + "\n", coloredPane.errorStyle));

                coloredPane.writeColoredText(segments);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection Failed");
                    statusLabel.setForeground(Color.RED);
                    repaint();
                });
            }
        }
    }

    public static GraphicsDevice getCurrentMonitor(JFrame frame) {
        // Get the bounds of the JFrame
        Rectangle frameBounds = frame.getBounds();

        // Get all available screen devices
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();

        // Iterate through all screen devices to find the one that contains the frame
        for (GraphicsDevice screen : screens) {
            GraphicsConfiguration config = screen.getDefaultConfiguration();
            Rectangle screenBounds = config.getBounds();

            // Check if the frame's bounds intersect with the screen's bounds
            if (screenBounds.intersects(frameBounds)) {
                return screen; // Found the monitor the frame is on
            }
        }

        // If no intersection is found (e.g., the frame is somehow off-screen or between screens)
        // you might return the default screen device.
        return ge.getDefaultScreenDevice();
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            // Exit fullscreen
            setSize(currentSize.x, currentSize.y);
            setLocation(windowedLocation.x, windowedLocation.y);
            isFullscreen = false;
        } else {
            // Enter fullscreen
            GraphicsDevice device = getCurrentMonitor(this);
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();

            setSize(device.getDisplayMode().getWidth(), device.getDisplayMode().getHeight());
            setLocation(bounds.x, bounds.y);
            isFullscreen = true;
        }
    }

    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        socket.setSoTimeout(5000); // 5 second timeout for read operations
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        isConnected = true;

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Connected to Minecraft Server");
            statusLabel.setForeground(Color.GREEN);
            connectButton.setText("Disconnect");
            if (isFullscreen) {
                statusLabel.setVisible(false);
                buttonPanel.setVisible(false);
            }

            repaint();
        });

        lastPlayerData = null;
        failedToConnectMessage = "";
    }

    public void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while (isConnected) {
                    // Check if socket is still connected
                    if (socket.isClosed() || !socket.isConnected()) {
                        throw new IOException("Socket closed");
                    }

                    line = in.readLine();
                    if (line == null) {
                        // Connection closed by server
                        throw new IOException("Server closed connection");
                    }
                    handlePlayerData(line);
                }
            } catch (IOException e) {
                coloredPane.writeText("\n=== Connection closed ===\n", coloredPane.errorStyle);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connection Lost");
                    statusLabel.setForeground(Color.RED);

                    statusLabel.setVisible(true);
                    buttonPanel.setVisible(true);
                    connectButton.setText("Connect");
                    repaint();
                });

                disconnect(); // Ensure cleanup
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void toggleConnectionAttempts() {
        if (connectionAttemptsTimer != null) {
            failedToConnectMessage = "";
            stopConnectionAttempts();
        } else if (isConnected) {
            disconnect();
        } else {
            startConnectionAttempts();
        }
    }

    private void startConnectionAttempts() {
        if (isConnected) {
            if (connectionAttemptsTimer != null) {
                stopConnectionAttempts();
                return;
            }
        }

        connectionAttemptsTimer = new Timer(connectionAttemptDelay, e -> {
            toggleConnection();
        });

        connectionAttemptsTimer.setInitialDelay(0);
        connectionAttemptsTimer.start();

        connectButton.setText("Stop");
    }

    private void stopConnectionAttempts() {
        if (connectionAttemptsTimer != null) {
            connectionAttemptsTimer.stop();
            connectionAttemptsTimer = null;

            if (!isConnected) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Disconnected");
                    statusLabel.setForeground(Color.YELLOW);
                    connectButton.setText("Connect");

                    coloredPane.clearDocument();

                    repaint();
                });
            }
        }
    }

    private void handlePlayerData(String jsonData) {
        Gson gson = new Gson();
        PlayerData playerData = gson.fromJson(jsonData, PlayerData.class);

        if (!isPlayerDataDifferent(playerData)) return;

        try {
            ArrayList<ColoredTextPane.ColoredTextSegment> segments = new ArrayList<>();

            if (playerData.showPosition) {
                int x = (int) playerData.x;
                int y = (int) playerData.y;
                int z = (int) playerData.z;

                boolean xBorder = x % 16 == 0;
                boolean yBorder = y % 16 == 0;
                boolean zBorder = z % 16 == 0;

                String output = "Position: X=" + String.format("%.2f", playerData.x) + (xBorder ? "B" : "") +
                        " Y=" + String.format("%.2f", playerData.y) + (yBorder ? "B" : "") +
                        " Z=" + String.format("%.2f", playerData.z) + (zBorder ? "B" : "") + "\n";
                segments.add(new ColoredTextPane.ColoredTextSegment(output, coloredPane.defaultStyle));
            }

            if (playerData.showFacing) {
                String firstLetter = playerData.facing.substring(0, 1).toUpperCase();

                String remainingLetters = playerData.facing.substring(1);

                String capitalizedString = firstLetter + remainingLetters;

                String towardsAxis = switch (capitalizedString) {
                    case "North" -> "(Towards Negative Z)";
                    case "South" -> "(Towards Positive Z)";
                    case "West" -> "(Towards Negative X)";
                    case "East" -> "(Towards Positive X)";
                    default -> "";
                };

                segments.add(new ColoredTextPane.ColoredTextSegment("Facing: " + capitalizedString + " " + towardsAxis + "(" + String.format("%.1f", playerData.yaw) + "/" + String.format("%.1f", playerData.pitch) + ")" + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showArmorLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Armor: " + playerData.armor + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showHealth) {
                float health = playerData.health;
                segments.add(new ColoredTextPane.ColoredTextSegment("Health: " + String.format("%.1f", health), coloredPane.defaultStyle));
                segments.add(new ColoredTextPane.ColoredTextSegment(" ❤\n", coloredPane.errorStyle));

                if (playerData.showHealthWarning > 0 && health / playerData.maxHealth < playerData.showHealthWarning / 100f) {
                    segments.add(new ColoredTextPane.ColoredTextSegment("  ⚠ LOW HEALTH WARNING!\n", coloredPane.warningStyle));
                }
            }

            if (playerData.showVehicleHealth && playerData.vehicleHealth > .01f) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Vehicle Health: " + String.format("%.1f", playerData.vehicleHealth) + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showFoodLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Food Level: " + playerData.foodLevel + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showSaturationLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Saturation: " + String.format("%.1f", playerData.saturation) + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showAirLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Air Supply: " + playerData.airSupply + "\n", coloredPane.defaultStyle));
            }

            if (playerData.showExperienceLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Level: " + playerData.experienceLevel, coloredPane.defaultStyle));
            }

            if (playerData.showExperienceProgress) {
                segments.add(new ColoredTextPane.ColoredTextSegment((!playerData.showExperienceLevel ? "Level" : "") + " Progress: %" + String.format("%.1f", playerData.experienceProgress * 100f) + "\n", coloredPane.defaultStyle));
            } else if (playerData.showExperienceLevel) {
                segments.add(new ColoredTextPane.ColoredTextSegment("\n", coloredPane.defaultStyle));
            }

            if (playerData.showStatusEffects && !playerData.statusEffects.isEmpty()) {
                StringBuilder output = new StringBuilder();
                output.append("Status Effects: ").append("\n");
                playerData.statusEffects.forEach(effect -> {
                    output.append("  ").append(effect.name).append("\n")
                            .append("  Duration: ").append(effect.duration).append("\n");
                });
                segments.add(new ColoredTextPane.ColoredTextSegment(output.toString(), coloredPane.defaultStyle));
            }

            if (playerData.showHotbarItems) {
                segments.add(new ColoredTextPane.ColoredTextSegment("Hotbar Items:\n", coloredPane.defaultStyle));

                if (playerData.inventoryData[9] == null) {
                    segments.add(new ColoredTextPane.ColoredTextSegment("  Empty", coloredPane.successStyle));
                } else {
                    segments.add(new ColoredTextPane.ColoredTextSegment("  " + playerData.inventoryData[9].name + " X" + playerData.inventoryData[9].count + " Durability: " + playerData.inventoryData[9].durability, coloredPane.successStyle));
                }

                segments.add(new ColoredTextPane.ColoredTextSegment(" OFFHAND\n", coloredPane.successStyle));

                for (int i = 0; i < 9; i++) {
                    Style s = coloredPane.defaultStyle;

                    if (playerData.selectedHotbarSlot == i) {
                        s = coloredPane.successStyle;
                    }

                    if (playerData.inventoryData[i] == null) {
                        segments.add(new ColoredTextPane.ColoredTextSegment("  Empty\n", s));
                    } else {
                        segments.add(new ColoredTextPane.ColoredTextSegment("  " + playerData.inventoryData[i].name + " X" + playerData.inventoryData[i].count, s));

                        if (playerData.showHotbarItemsDurability && playerData.inventoryData[i].maxDamage > 0) {
                            float durability = playerData.showHotbarItemsDurabilityPercent ? ((float) playerData.inventoryData[i].durability / (float) playerData.inventoryData[i].maxDamage) * 100
                                    : playerData.inventoryData[i].durability;

                            segments.add(new ColoredTextPane.ColoredTextSegment(" Durability: " + (playerData.showHotbarItemsDurabilityPercent ? (String.format("%.1f", durability) + "%") : String.format("%.0f", durability)), s));
                        }

                        if (playerData.selectedHotbarSlot == i && playerData.showSelectedItemLabel) {
                            segments.add(new ColoredTextPane.ColoredTextSegment(" SELECTED\n", s));
                        } else if (i != 8) {
                            segments.add(new ColoredTextPane.ColoredTextSegment("\n", s));
                        }
                    }
                }
            }

            coloredPane.writeColoredText(segments);

            if (playerData.portNumber != lastPlayerData.portNumber) {
                PORT = config.parsePort(String.valueOf(playerData.portNumber));
                config.setPort(PORT);
                config.saveConfig();
            }

            if (!playerData.focusedBackgroundColor.equals(lastPlayerData.focusedBackgroundColor)) {
                config.setFocusedBackgroundColor(config.parseFocusedBackgroundColor(playerData.focusedBackgroundColor));
                setupFocusedBGColor();
                config.saveConfig();

                if (isFocused)
                    setBackground(focusedBGColor);
            }

            if (playerData.focusedBackgroundOpacity != lastPlayerData.focusedBackgroundOpacity) {
                config.setFocusedBackgroundOpacity(config.parseOpacity(String.valueOf(playerData.focusedBackgroundOpacity), "focusedBackgroundOpacity"));
                setupFocusedBGColor();
                config.saveConfig();

                if (isFocused)
                    setBackground(focusedBGColor);
            }

            if (!playerData.notFocusedBackgroundColor.equals(lastPlayerData.notFocusedBackgroundColor)) {
                config.setNotFocusedBackgroundColor(config.parseNotFocusedBackgroundColor(playerData.notFocusedBackgroundColor));
                setupNotFocusedBGColor();
                config.saveConfig();

                if (!isFocused)
                    setBackground(notFocusedBGColor);
            }

            if (playerData.notFocusedBackgroundOpacity != lastPlayerData.notFocusedBackgroundOpacity) {
                config.setNotFocusedBackgroundOpacity(config.parseOpacity(String.valueOf(playerData.notFocusedBackgroundOpacity), "notFocusedBackgroundOpacity"));
                setupNotFocusedBGColor();
                config.saveConfig();

                if (!isFocused)
                    setBackground(notFocusedBGColor);
            }

            lastPlayerData = playerData;
            repaint();
        } catch (Exception e) {
            coloredPane.writeText("Error parsing data: " + e.getMessage() + "\n", coloredPane.errorStyle);
        }
    }

    private boolean isPlayerDataDifferent(PlayerData playerData) {
        if (lastPlayerData == null) {
            lastPlayerData = playerData;
            return true;
        }

        boolean dataChanged = false;

        if (lastPlayerData.health != playerData.health) dataChanged = true;
        if (lastPlayerData.armor != playerData.armor) dataChanged = true;
        if (lastPlayerData.foodLevel != playerData.foodLevel) dataChanged = true;
        if (lastPlayerData.saturation != playerData.saturation) dataChanged = true;
        if (lastPlayerData.airSupply != playerData.airSupply) dataChanged = true;
        if (lastPlayerData.vehicleHealth != playerData.vehicleHealth) dataChanged = true;
        if (lastPlayerData.experienceLevel != playerData.experienceLevel) dataChanged = true;
        if (lastPlayerData.experienceProgress != playerData.experienceProgress) dataChanged = true;
        if (lastPlayerData.selectedHotbarSlot != playerData.selectedHotbarSlot) dataChanged = true;
        if (lastPlayerData.x != playerData.x) dataChanged = true;
        if (lastPlayerData.y != playerData.y) dataChanged = true;
        if (lastPlayerData.z != playerData.z) dataChanged = true;
        if (lastPlayerData.yaw != playerData.yaw) dataChanged = true;
        if (lastPlayerData.pitch != playerData.pitch) dataChanged = true;
        if (!lastPlayerData.facing.equals(playerData.facing)) dataChanged = true;
        if (!Arrays.equals(lastPlayerData.inventoryData, playerData.inventoryData)) dataChanged = true;
        if (!Arrays.equals(lastPlayerData.statusEffects.toArray(), playerData.statusEffects.toArray()))
            dataChanged = true;

        if (lastPlayerData.showArmorLevel != playerData.showArmorLevel) dataChanged = true;
        if (lastPlayerData.showPosition != playerData.showPosition) dataChanged = true;
        if (lastPlayerData.showFacing != playerData.showFacing) dataChanged = true;
//        if (lastPlayerData.showYawPitch != playerData.showYawPitch) dataChanged = true;
        if (lastPlayerData.showHealth != playerData.showHealth) dataChanged = true;
        if (lastPlayerData.showHealthWarning != playerData.showHealthWarning) dataChanged = true;
        if (lastPlayerData.showVehicleHealth != playerData.showVehicleHealth) dataChanged = true;
        if (lastPlayerData.showFoodLevel != playerData.showFoodLevel) dataChanged = true;
        if (lastPlayerData.showSaturationLevel != playerData.showSaturationLevel) dataChanged = true;
        if (lastPlayerData.showAirLevel != playerData.showAirLevel) dataChanged = true;
        if (lastPlayerData.showExperienceLevel != playerData.showExperienceLevel) dataChanged = true;
        if (lastPlayerData.showExperienceProgress != playerData.showExperienceProgress) dataChanged = true;
        if (lastPlayerData.showStatusEffects != playerData.showStatusEffects) dataChanged = true;
        if (lastPlayerData.showHotbarItems != playerData.showHotbarItems) dataChanged = true;
        if (lastPlayerData.showHotbarItemsDurability != playerData.showHotbarItemsDurability) dataChanged = true;
        if (lastPlayerData.showHotbarItemsDurabilityPercent != playerData.showHotbarItemsDurabilityPercent)
            dataChanged = true;
        if (lastPlayerData.showSelectedItemLabel != playerData.showSelectedItemLabel) dataChanged = true;
        if (lastPlayerData.portNumber != playerData.portNumber) dataChanged = true;
        if (!lastPlayerData.focusedBackgroundColor.equals(playerData.focusedBackgroundColor)) dataChanged = true;
        if (!lastPlayerData.notFocusedBackgroundColor.equals(playerData.notFocusedBackgroundColor)) dataChanged = true;
        if (lastPlayerData.focusedBackgroundOpacity != playerData.focusedBackgroundOpacity) dataChanged = true;
        if (lastPlayerData.notFocusedBackgroundOpacity != playerData.notFocusedBackgroundOpacity) dataChanged = true;

        return dataChanged;
    }

    public void disconnect() {
        isConnected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            }
            if (in != null) in.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket = null;
            in = null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AutoHideHUDCompanion client = new AutoHideHUDCompanion();
            client.setVisible(true);
        });
    }

    public void windowGainedFocus() {
        // show bg color if transparent
        isFocused = true;
        statusLabel.setVisible(true);
        buttonPanel.setVisible(true);

        setBackground(focusedBGColor);
        repaint();
    }

    public void windowLostFocus() {
        // make bg transparent if option is set
        isFocused = false;

        if (isConnected) {
            Timer t = new Timer(1, e -> {
                buttonPanel.setVisible(false);
                statusLabel.setVisible(false);
            });
            t.setRepeats(false);
            t.start();
        }

        setBackground(notFocusedBGColor);
        repaint();
    }

    // Inner class for the colored text pane
    static class ColoredTextPane extends JPanel {
        private JTextPane textPane;
        private StyledDocument doc;

        private Style defaultStyle;
        private Style errorStyle;
        private Style warningStyle;
        private Style successStyle;

        public ColoredTextPane() {
            setLayout(new BorderLayout());
            setOpaque(false);

            textPane = new JTextPane();
            textPane.setEditable(false);
            textPane.setHighlighter(null);
            textPane.setFocusable(false);
            textPane.setOpaque(false);
            textPane.setDoubleBuffered(true);
            textPane.setFont(new Font("Monospaced", Font.PLAIN, 24));
            doc = textPane.getStyledDocument();

            add(textPane, BorderLayout.CENTER);

            initStyles();
        }

        private void initStyles() {
            defaultStyle = textPane.addStyle("Default", null);
            StyleConstants.setForeground(defaultStyle, Color.WHITE);

            errorStyle = textPane.addStyle("Error", null);
            StyleConstants.setForeground(errorStyle, Color.RED);
            StyleConstants.setBold(errorStyle, true);

            warningStyle = textPane.addStyle("Warning", null);
            StyleConstants.setForeground(warningStyle, new Color(255, 140, 0));

            successStyle = textPane.addStyle("Success", null);
            StyleConstants.setForeground(successStyle, new Color(0, 200, 0));
        }

        // Clears the document and writes new text with default style
        public void writeText(String text) {
            writeText(text, defaultStyle);
        }

        // Clears the document and writes new text with specified style
        public void writeText(String text, Style style) {
            clearDocument();
            appendText(text, style);
        }

        // Clears the document and writes multiple colored text segments
        public void writeColoredText(ArrayList<ColoredTextSegment> segments) {
            clearDocument();
            for (ColoredTextSegment segment : segments) {
                appendText(segment.text, segment.style);
            }
        }

        // Clears the document and writes multiple colored text segments
        public void writeColoredText(ColoredTextSegment... segments) {
            clearDocument();
            for (ColoredTextSegment segment : segments) {
                appendText(segment.text, segment.style);
            }
        }

        // Appends text without clearing (useful for building multi-colored output)
        public void appendText(String text, Style style) {
            try {
                doc.insertString(doc.getLength(), text, style == null ? defaultStyle : style);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        // Clears the entire document
        public void clearDocument() {
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        public void setFont(Font font) {
            super.setFont(font);
            if (textPane != null) {
                textPane.setFont(font);
                textPane.setFont(font);
            }
        }

        public static class ColoredTextSegment {
            String text;
            Style style;

            public ColoredTextSegment(String text, Style style) {
                this.text = text;
                this.style = style;
            }
        }
    }

    static class PlayerData {
        float health = 20.0f;
        float maxHealth = 20.0f;
        int armor = 0;
        int foodLevel = 20;
        int maxFoodLevel = 20;
        float saturation = 20f;
        boolean isCreative = false;
        int experienceLevel = 0;
        float experienceProgress = 0f;
        int selectedHotbarSlot = 0;
        int airSupply = 300;
        int maxAirSupply = 300;
        float vehicleHealth = .01f;
        float maxVehicleHealth = .01f;
        boolean inMenu = false;
        double x = 0d;
        double y = 0d;
        double z = 0d;
        float yaw = 0f;
        float pitch = 0f;
        String facing = "";
        InventoryItemData[] inventoryData = new InventoryItemData[10];
        ArrayList<StatusEffectData> statusEffects = new ArrayList<StatusEffectData>();

        // server settings
        int portNumber = 33333;
        boolean showPosition = true;
        boolean showFacing = true;
        //        boolean showYawPitch = true;
        boolean showHealth = true;
        int showHealthWarning = 30;
        boolean showArmorLevel = true;
        boolean showFoodLevel = true;
        boolean showSaturationLevel = true;
        boolean showVehicleHealth = true;
        boolean showAirLevel = true;
        boolean showExperienceLevel = true;
        boolean showExperienceProgress = true;
        boolean showStatusEffects = true;
        boolean showHotbarItems = true;
        boolean showHotbarItemsDurability = true;
        boolean showHotbarItemsDurabilityPercent = true;
        boolean showSelectedItemLabel = false;
        String focusedBackgroundColor = "#000000";
        int focusedBackgroundOpacity = 255;
        String notFocusedBackgroundColor = "#000000";
        int notFocusedBackgroundOpacity = 0;
    }

    static class InventoryItemData {
        String name = "";
        String id = "";
        int count = 0;
        int slot = 0;
        int damage = 0;
        int maxDamage = 0;
        int durability = 0;
        boolean enchanted = false;
        boolean selected = false;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            InventoryItemData other = (InventoryItemData) obj;
            return count == other.count &&
                    slot == other.slot &&
                    damage == other.damage &&
                    maxDamage == other.maxDamage &&
                    durability == other.durability &&
                    enchanted == other.enchanted &&
                    selected == other.selected &&
                    (Objects.equals(name, other.name)) &&
                    (Objects.equals(id, other.id));
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (id != null ? id.hashCode() : 0);
            result = 31 * result + count;
            result = 31 * result + slot;
            result = 31 * result + damage;
            result = 31 * result + maxDamage;
            result = 31 * result + durability;
            result = 31 * result + (enchanted ? 1 : 0);
            result = 31 * result + (selected ? 1 : 0);
            return result;
        }
    }

    static class StatusEffectData {
        String name = "";
        int amplifier = 0;
        int duration = 0;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            StatusEffectData other = (StatusEffectData) obj;
            return amplifier == other.amplifier &&
                    duration == other.duration &&
                    (Objects.equals(name, other.name));
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + amplifier;
            result = 31 * result + duration;
            return result;
        }
    }
}

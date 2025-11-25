package com.krookedlilly.autohidehudcompanion;

import java.io.*;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE_NAME = "autohidehudcompanion.properties";
    private static final String DEFAULT_CONFIG_PATH = "/config/default.properties";

    // Default values
    private static final int DEFAULT_PORT = 25922;
    private static final String DEFAULT_FOCUSED_BG_COLOR = "#000000";
    private static final boolean DEFAULT_TRANSPARENT_WHEN_NOT_FOCUSED = true;

    // Config values
    private int port;
    private String focusedBGColor;
    private boolean transparentWhenNotFocused;

    private File configFile;

    public Config() {
        // Get the config file in the application's root directory
        String userDir = System.getProperty("user.dir");
        configFile = new File(userDir, CONFIG_FILE_NAME);

        // Load configuration
        loadConfig();
    }

    private void loadConfig() {
        Properties props = new Properties();

        // If config file doesn't exist, try to copy default from JAR
        if (!configFile.exists()) {
            copyDefaultConfig();
        }

        // Load from file if it exists
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                System.out.println("Loaded configuration from: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error loading config file: " + e.getMessage());
                System.err.println("Using default values");
            }
        }

        // Parse values with defaults as fallback
        port = parsePort(props.getProperty("port"));
        focusedBGColor = parseFocusedBGColor(props.getProperty("focusedBGColor"));
        transparentWhenNotFocused = parseBoolean(props.getProperty("transparentWhenNotFocused"));
    }

    private void copyDefaultConfig() {
        // Try to load default config from JAR resources
        try (InputStream defaultConfig = getClass().getResourceAsStream(DEFAULT_CONFIG_PATH)) {
            if (defaultConfig != null) {
                try (FileOutputStream fos = new FileOutputStream(configFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = defaultConfig.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    System.out.println("Created default config file at: " + configFile.getAbsolutePath());
                }
            } else {
                // If no default in JAR, create one with hardcoded defaults
                createDefaultConfigFile();
            }
        } catch (IOException e) {
            System.err.println("Error copying default config: " + e.getMessage());
            createDefaultConfigFile();
        }
    }

    private void createDefaultConfigFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# AutoHideHUD Companion Configuration\n");
            writer.write("# This file stores settings for the companion overlay application\n");
            writer.write("\n");
            writer.write("# Port to connect to the Minecraft mod server\n");
            writer.write("# Valid range: 1-65535\n");
            writer.write("port=" + DEFAULT_PORT + "\n");
            writer.write("\n");
            writer.write("# Background color when window is focused (hex format with or without #)\n");
            writer.write("# Supports 6-digit RGB (#RRGGBB) or 8-digit RGBA (#RRGGBBAA) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00AA (semi-transparent green)\n");
            writer.write("focusedBGColor=" + DEFAULT_FOCUSED_BG_COLOR + "\n");
            writer.write("\n");
            writer.write("# Whether the window background should be transparent when not focused\n");
            writer.write("# Set to true for transparent background, false to always show the focused color\n");
            writer.write("transparentWhenNotFocused=" + DEFAULT_TRANSPARENT_WHEN_NOT_FOCUSED + "\n");

            System.out.println("Created default config file at: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error creating default config file: " + e.getMessage());
        }
    }

    public void saveConfig() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# AutoHideHUD Companion Configuration\n");
            writer.write("# This file stores settings for the companion overlay application\n");
            writer.write("\n");
            writer.write("# Port to connect to the Minecraft mod server\n");
            writer.write("# Valid range: 1-65535\n");
            writer.write("port=" + port + "\n");
            writer.write("\n");
            writer.write("# Background color when window is focused (hex format with or without #)\n");
            writer.write("# Supports 6-digit RGB (#RRGGBB) or 8-digit RGBA (#RRGGBBAA) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00AA (semi-transparent green)\n");
            writer.write("focusedBGColor=" + focusedBGColor + "\n");
            writer.write("\n");
            writer.write("# Whether the window background should be transparent when not focused\n");
            writer.write("# Set to true for transparent background, false to always show the focused color\n");
            writer.write("transparentWhenNotFocused=" + transparentWhenNotFocused + "\n");

            System.out.println("Saved configuration to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving config file: " + e.getMessage());
        }
    }

    // Parsing methods with validation
    public int parsePort(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_PORT;
        }
        try {
            int parsedPort = Integer.parseInt(value.trim());
            if (parsedPort < 1 || parsedPort > 65535) {
                System.err.println("Invalid port number: " + parsedPort + ". Using default: " + DEFAULT_PORT);
                return DEFAULT_PORT;
            }
            return parsedPort;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port format: " + value + ". Using default: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private String parseFocusedBGColor(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_FOCUSED_BG_COLOR;
        }

        String color = value.trim();
        // Ensure it has # prefix
        if (!color.startsWith("#")) {
            color = "#" + color;
        }

        // Validate hex color format (6 or 8 hex digits)
        if (color.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            return color;
        } else {
            System.err.println("Invalid color format: " + value + ". Using default: " + DEFAULT_FOCUSED_BG_COLOR);
            return DEFAULT_FOCUSED_BG_COLOR;
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_TRANSPARENT_WHEN_NOT_FOCUSED;
        }
        return Boolean.parseBoolean(value.trim());
    }

    // Getters
    public int getPort() {
        return port;
    }

    public String getFocusedBGColor() {
        return focusedBGColor;
    }

    public boolean isTransparentWhenNotFocused() {
        return transparentWhenNotFocused;
    }

    // Setters (if you want to update config at runtime)
    public void setPort(int port) {
        this.port = port;
    }

    public void setFocusedBGColor(String focusedBGColor) {
        this.focusedBGColor = focusedBGColor;
    }

    public void setTransparentWhenNotFocused(boolean transparentWhenNotFocused) {
        this.transparentWhenNotFocused = transparentWhenNotFocused;
    }
}

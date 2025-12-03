package com.krookedlilly.autohidehudcompanion;

import java.io.*;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE_NAME = "autohidehudcompanion.properties";
    private static final String DEFAULT_CONFIG_PATH = "/config/default.properties";

    // Default values
    private static final int DEFAULT_PORT = 25592;
    private static final String DEFAULT_FOCUSED_BG_COLOR = "#000000";
    private static final int DEFAULT_FOCUSED_BG_OPACITY = 100;
    private static final String DEFAULT_NOT_FOCUSED_BG_COLOR = "#000000";
    private static final int DEFAULT_NOT_FOCUSED_BG_OPACITY = 0;

    // Config values
    private int port;
    private String focusedBGColor;
    private int focusedBackgroundOpacity;
    private String notFocusedBackgroundColor;
    private int notFocusedBackgroundOpacity;

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
        boolean needsMigration = false;

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

        // Check if migration is needed
        if (props.containsKey("transparentWhenNotFocused")) {
            System.out.println("Detected old config format - migrating...");
            needsMigration = true;

            // Migrate transparentWhenNotFocused to new opacity-based system
            boolean wasTransparent = Boolean.parseBoolean(props.getProperty("transparentWhenNotFocused"));
            if (wasTransparent) {
                // If it was transparent when not focused, set not focused opacity to 0
                props.setProperty("notFocusedBackgroundOpacity", "0");
            } else {
                // If it wasn't transparent, set not focused opacity to match focused
                props.setProperty("notFocusedBackgroundOpacity", "100");
            }
            props.remove("transparentWhenNotFocused");
        }

        // Check for missing properties
        if (!props.containsKey("focusedBackgroundOpacity")) {
            props.setProperty("focusedBackgroundOpacity", String.valueOf(DEFAULT_FOCUSED_BG_OPACITY));
            needsMigration = true;
        }
        if (!props.containsKey("notFocusedBackgroundColor")) {
            props.setProperty("notFocusedBackgroundColor", DEFAULT_NOT_FOCUSED_BG_COLOR);
            needsMigration = true;
        }
        if (!props.containsKey("notFocusedBackgroundOpacity")) {
            props.setProperty("notFocusedBackgroundOpacity", String.valueOf(DEFAULT_NOT_FOCUSED_BG_OPACITY));
            needsMigration = true;
        }

        // Parse values with defaults as fallback
        port = parsePort(props.getProperty("port"));
        focusedBGColor = parseFocusedBackgroundColor(props.getProperty("focusedBGColor"));
        focusedBackgroundOpacity = parseOpacity(props.getProperty("focusedBackgroundOpacity"), "focusedBackgroundOpacity");
        notFocusedBackgroundColor = parseColor(props.getProperty("notFocusedBackgroundColor"), "notFocusedBackgroundColor");
        notFocusedBackgroundOpacity = parseOpacity(props.getProperty("notFocusedBackgroundOpacity"), "notFocusedBackgroundOpacity");

        // If migration was needed, save the updated config
        if (needsMigration) {
            System.out.println("Config file updated with new properties");
            saveConfig();
        }
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
            writer.write("# Supports 6-digit RGB (#RRGGBB) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00 (green)\n");
            writer.write("focusedBGColor=" + DEFAULT_FOCUSED_BG_COLOR + "\n");
            writer.write("\n");
            writer.write("# Background opacity % when window is focused\n");
            writer.write("# Valid range: 0-100 (0 = fully transparent, 100 = fully opaque)\n");
            writer.write("focusedBackgroundOpacity=" + DEFAULT_FOCUSED_BG_OPACITY + "\n");
            writer.write("\n");
            writer.write("# Background color when window is not focused (hex format with or without #)\n");
            writer.write("# Supports 6-digit RGB (#RRGGBB) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00 (green)\n");
            writer.write("notFocusedBackgroundColor=" + DEFAULT_NOT_FOCUSED_BG_COLOR + "\n");
            writer.write("\n");
            writer.write("# Background opacity % when window is not focused\n");
            writer.write("# Valid range: 0-100 (0 = fully transparent, 100 = fully opaque)\n");
            writer.write("notFocusedBackgroundOpacity=" + DEFAULT_NOT_FOCUSED_BG_OPACITY + "\n");

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
            writer.write("# Supports 6-digit RGB (#RRGGBB) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00 (green)\n");
            writer.write("focusedBGColor=" + focusedBGColor + "\n");
            writer.write("\n");
            writer.write("# Background opacity % when window is focused\n");
            writer.write("# Valid range: 0-100 (0 = fully transparent, 100 = fully opaque)\n");
            writer.write("focusedBackgroundOpacity=" + focusedBackgroundOpacity + "\n");
            writer.write("\n");
            writer.write("# Background color when window is not focused (hex format with or without #)\n");
            writer.write("# Supports 6-digit RGB (#RRGGBB) format\n");
            writer.write("# Examples: #000000 (black), #FF0000 (red), #00FF00 (green)\n");
            writer.write("notFocusedBackgroundColor=" + notFocusedBackgroundColor + "\n");
            writer.write("\n");
            writer.write("# Background opacity % when window is not focused\n");
            writer.write("# Valid range: 0-100 (0 = fully transparent, 100 = fully opaque)\n");
            writer.write("notFocusedBackgroundOpacity=" + notFocusedBackgroundOpacity + "\n");

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

    public int parseOpacity(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            if (propertyName.equals("focusedBackgroundOpacity")) {
                return DEFAULT_FOCUSED_BG_OPACITY;
            } else {
                return DEFAULT_NOT_FOCUSED_BG_OPACITY;
            }
        }
        try {
            int parsedOpacity = Integer.parseInt(value.trim());
            if (parsedOpacity < 0 || parsedOpacity > 100) {
                System.err.println("Invalid " + propertyName + ": " + parsedOpacity + ". Must be 0-100. Using default.");
                return propertyName.equals("focusedBackgroundOpacity") ? DEFAULT_FOCUSED_BG_OPACITY : DEFAULT_NOT_FOCUSED_BG_OPACITY;
            }
            return parsedOpacity;
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + propertyName + " format: " + value + ". Using default.");
            return propertyName.equals("focusedBackgroundOpacity") ? DEFAULT_FOCUSED_BG_OPACITY : DEFAULT_NOT_FOCUSED_BG_OPACITY;
        }
    }

    public String parseFocusedBackgroundColor(String value) {
        return parseColor(value, "focusedBGColor");
    }

    public String parseNotFocusedBackgroundColor(String value) {
        return parseColor(value, "notFocusedBGColor");
    }

    public String parseColor(String value, String propertyName) {
        String defaultColor = propertyName.equals("focusedBGColor") ? DEFAULT_FOCUSED_BG_COLOR : DEFAULT_NOT_FOCUSED_BG_COLOR;

        if (value == null || value.trim().isEmpty()) {
            return defaultColor;
        }

        String color = value.trim();
        // Ensure it has # prefix
        if (!color.startsWith("#")) {
            color = "#" + color;
        }

        // Validate hex color format (6 hex digits only - no alpha)
        if (color.matches("#[0-9A-Fa-f]{6}")) {
            return color;
        } else {
            System.err.println("Invalid " + propertyName + " format: " + value + ". Using default: " + defaultColor);
            return defaultColor;
        }
    }

    // Getters
    public int getPort() {
        return port;
    }

    public String getFocusedBackgroundColor() {
        return focusedBGColor;
    }

    public int getFocusedBackgroundOpacity() {
        return focusedBackgroundOpacity;
    }

    public String getNotFocusedBackgroundColor() {
        return notFocusedBackgroundColor;
    }

    public int getNotFocusedBackgroundOpacity() {
        return notFocusedBackgroundOpacity;
    }

    // Setters (if you want to update config at runtime)
    public void setPort(int port) {
        this.port = port;
    }

    public void setFocusedBackgroundColor(String focusedBGColor) {
        this.focusedBGColor = focusedBGColor;
    }

    public void setFocusedBackgroundOpacity(int focusedBackgroundOpacity) {
        this.focusedBackgroundOpacity = focusedBackgroundOpacity;
    }

    public void setNotFocusedBackgroundColor(String notFocusedBackgroundColor) {
        this.notFocusedBackgroundColor = notFocusedBackgroundColor;
    }

    public void setNotFocusedBackgroundOpacity(int notFocusedBackgroundOpacity) {
        this.notFocusedBackgroundOpacity = notFocusedBackgroundOpacity;
    }
}
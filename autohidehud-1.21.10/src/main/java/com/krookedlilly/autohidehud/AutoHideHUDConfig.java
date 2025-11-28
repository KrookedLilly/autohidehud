package com.krookedlilly.autohidehud;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.awt.*;

@EventBusSubscriber
public class AutoHideHUDConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // General Settings
    public static final ModConfigSpec.BooleanValue enableAutoHiding;
    public static final ModConfigSpec.IntValue ticksBeforeHiding;
    public static final ModConfigSpec.DoubleValue fadeOutSpeed;
    public static final ModConfigSpec.DoubleValue targetOpacity;

    // Hide Options
    public static final ModConfigSpec.BooleanValue hideHotbar;
    public static final ModConfigSpec.BooleanValue hideSelectedItemName;
    public static final ModConfigSpec.BooleanValue hideHealthBar;
    public static final ModConfigSpec.BooleanValue hideArmorLevel;
    public static final ModConfigSpec.BooleanValue hideFoodLevel;
    public static final ModConfigSpec.BooleanValue hideVehicleHealth;
    public static final ModConfigSpec.BooleanValue hideAirLevel;
    public static final ModConfigSpec.BooleanValue hideExperienceLevel;
    public static final ModConfigSpec.BooleanValue hideContextualInfoBar;
    public static final ModConfigSpec.BooleanValue hideCrossHair;
    public static final ModConfigSpec.BooleanValue hideStatusEffects;
    public static final ModConfigSpec.BooleanValue hideChatMessages;
    public static final ModConfigSpec.BooleanValue hideSleepOverlay;

    // Reveal Options
    public static final ModConfigSpec.IntValue revealWhenPlayerHealthChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenPlayerFoodChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenVehicleHealthChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenPlayerAirChangedBelow;
    public static final ModConfigSpec.BooleanValue revealOnPlayerArmorChange;
    public static final ModConfigSpec.IntValue revealOnPlayerExperienceLevelChange;
    public static final ModConfigSpec.BooleanValue revealOnPlayerExperienceProgressChange;
    public static final ModConfigSpec.BooleanValue revealOnPlayerHotbarSlotChange;
    public static final ModConfigSpec.BooleanValue returnToStateFromMenu;

    // Data Server Settings
    public static final ModConfigSpec.BooleanValue autoStartPlayerDataServer;
    public static final ModConfigSpec.IntValue playerDataServerPort;
    public static final ModConfigSpec.IntValue playerDataServerTickRate;
    public static final ModConfigSpec.BooleanValue showPosition;
    public static final ModConfigSpec.BooleanValue showFacing;
    public static final ModConfigSpec.BooleanValue showHealth;
    public static final ModConfigSpec.IntValue showHealthWarning;
    public static final ModConfigSpec.BooleanValue showArmorLevel;
    public static final ModConfigSpec.BooleanValue showFoodLevel;
    public static final ModConfigSpec.BooleanValue showSaturationLevel;
    public static final ModConfigSpec.BooleanValue showVehicleHealth;
    public static final ModConfigSpec.BooleanValue showAirLevel;
    public static final ModConfigSpec.BooleanValue showExperienceLevel;
    public static final ModConfigSpec.BooleanValue showExperienceProgress;
    public static final ModConfigSpec.BooleanValue showStatusEffects;
    public static final ModConfigSpec.BooleanValue showHotbarItems;
    public static final ModConfigSpec.ConfigValue<String> focusedBackgroundColor;
    public static final ModConfigSpec.BooleanValue transparentBackgroundNotFocused;

    public static final ModConfigSpec SPEC;

    static {
        // General Settings Section
        BUILDER.comment("Auto Hide Settings").push("generalGroup");

        enableAutoHiding = BUILDER
                .comment("Enable auto hiding")
                .define("enableAutoHiding", true);

        ticksBeforeHiding = BUILDER
                .comment("How long in game ticks before hiding (20 ticks = 1 sec)")
                .defineInRange("ticksBeforeHiding", 100, 1, Integer.MAX_VALUE);

        fadeOutSpeed = BUILDER
                .comment("Fade out duration in seconds")
                .defineInRange("fadeOutSpeed", 1f, 0,  Integer.MAX_VALUE);

        targetOpacity = BUILDER
                .comment("The final opacity of the hud elements")
                .defineInRange("targetOpacity", 0f, 0f, 1);

        BUILDER.pop();

        // Hide Options Section
        BUILDER.comment("Hide Options - Configure which HUD elements to hide").push("hideOptionsGroup");

        hideHotbar = BUILDER
                .comment("Hides the hotbar")
                .define("hideHotbar", true);

        hideSelectedItemName = BUILDER
                .comment("Hides the selected hotbar item name")
                .define("hideSelectedItemName", true);

        hideArmorLevel = BUILDER
                .comment("Hides the player armor")
                .define("hideArmorLevel", true);

        hideHealthBar = BUILDER
                .comment("Hides the player health")
                .define("hideHealthBar", true);

        hideVehicleHealth = BUILDER
                .comment("Hides the vehicle health")
                .define("hideVehicleHealth", true);

        hideFoodLevel = BUILDER
                .comment("Hides the player food level")
                .define("hideFoodLevel", true);

        hideAirLevel = BUILDER
                .comment("Hides the player air level")
                .define("hideAirLevel", true);

        hideExperienceLevel = BUILDER
                .comment("Hides the player level")
                .define("hideExperienceLevel", true);

        hideContextualInfoBar = BUILDER
                .comment("Hides the contextual bar (experience progress and player nav)")
                .define("hideContextualInfoBar", true);

        hideCrossHair = BUILDER
                .comment("Hides the crosshairs")
                .define("hideCrossHair", true);

        hideStatusEffects = BUILDER
                .comment("Hides the player status effects")
                .define("hideStatusEffects", true);

        hideChatMessages = BUILDER
                .comment("Hides the chat messages")
                .define("hideChatMessages", true);

        hideSleepOverlay = BUILDER
                .comment("Hides the sleep overlay while in bed")
                .define("hideSleepOverlay", true);

        BUILDER.pop();

        // Reveal Options Section
        BUILDER.comment("Reveal Conditions - Configure when the HUD should be revealed").push("revealConditionsGroup");

        revealWhenPlayerHealthChangedBelow = BUILDER
                .comment("Reveals the HUD while player health is below % (0 = disabled)")
                .defineInRange("revealWhenPlayerHealthChangedBelow", 50, 0, 100);

        revealWhenPlayerFoodChangedBelow = BUILDER
                .comment("Reveals the HUD while player food is below % (0 = disabled)")
                .defineInRange("revealWhenPlayerFoodChangedBelow", 50, 0, 100);

        revealWhenVehicleHealthChangedBelow = BUILDER
                .comment("Reveals the HUD while mount health is below % (0 = disabled)")
                .defineInRange("revealWhenVehicleHealthChangedBelow", 50, 0, 100);

        revealWhenPlayerAirChangedBelow = BUILDER
                .comment("Reveals the HUD while player air is below % (0 = disabled)")
                .defineInRange("revealWhenPlayerAirChangedBelow", 50, 0, 100);

        revealOnPlayerExperienceLevelChange = BUILDER
                .comment("Temp reveals the HUD when player experience level changes and is a multiple of value (0 = disabled)")
                .defineInRange("revealOnPlayerExperienceLevelChange", 1, 0, Integer.MAX_VALUE);

        revealOnPlayerExperienceProgressChange = BUILDER
                .comment("Temp reveals the HUD when player experience progress changes")
                .define("revealOnPlayerExperienceProgressChange", true);

        revealOnPlayerArmorChange = BUILDER
                .comment("Temp reveals the HUD when player armour changes")
                .define("revealOnPlayerArmorChange", true);

        revealOnPlayerHotbarSlotChange = BUILDER
                .comment("Temp reveals the HUD when selected hotbar slot changes")
                .define("revealOnPlayerHotbarSlotChange", true);

        returnToStateFromMenu = BUILDER
                .comment("When closing a menu, should the HUD instantly fade if it was hidden before the menu was opened?")
                .define("returnToStateFromMenu", true);

        BUILDER.pop();

        // Companion App Section
        BUILDER.comment("Companion App Settings").push("dataServerGroup");

        autoStartPlayerDataServer = BUILDER
                .comment("Should the data server start automatically when in a world")
                .define("autoStartPlayerDataServer", false);

        playerDataServerPort = BUILDER
                .comment("Specifies the data server port number. Changing this while connected will also set the port of the companion app. Otherwise, you will need to manually set it. (Requires companion to be restarted)")
                .defineInRange("playerDataServerPort", 33333, 1, 65535);

        playerDataServerTickRate = BUILDER
                .comment("How often will the data server send updates to the companion app in game ticks (20 ticks = 1 sec)")
                .defineInRange("playerDataServerTickRate", 1, 1, Integer.MAX_VALUE);

        showPosition = BUILDER
                .comment("Displays the player's position on the companion app")
                .define("showPosition", true);

        showFacing = BUILDER
                .comment("Displays the player's facing (North, East, South, West) on the companion app")
                .define("showFacing", true);

        showArmorLevel = BUILDER
                .comment("Displays the player's armor level on the companion app")
                .define("showArmorLevel", true);

        showHealth = BUILDER
                .comment("Displays the player's health on the companion app")
                .define("showHealth", true);

        showHealthWarning = BUILDER
                .comment("Displays a low health warning on the companion app if players health is below % (0 = disabled)")
                .defineInRange("showHealthWarning", 30, 0, 100);

        showVehicleHealth = BUILDER
                .comment("Displays the player's mount health on the companion app")
                .define("showVehicleHealth", true);

        showFoodLevel = BUILDER
                .comment("Displays the player's food level on the companion app")
                .define("showFoodLevel", true);

        showSaturationLevel = BUILDER
                .comment("Displays the player's food saturation on the companion app")
                .define("showSaturationLevel", true);

        showAirLevel = BUILDER
                .comment("Displays the player's remaining air on the companion app")
                .define("showAirLevel", true);

        showExperienceLevel = BUILDER
                .comment("Displays the player's level on the companion app")
                .define("showExperienceLevel", true);

        showExperienceProgress = BUILDER
                .comment("Displays the player's experience progress on the companion app")
                .define("showExperienceProgress", true);

        showStatusEffects = BUILDER
                .comment("Displays the player's status effects on the companion app")
                .define("showStatusEffects", true);

        showHotbarItems = BUILDER
                .comment("Displays the player's hotbar items on the companion app")
                .define("showHotbarItems", true);

        focusedBackgroundColor = BUILDER
                .comment("Sets the companion app's background color (hex format)")
                .define("focusedBackgroundColor", "#000000");

        transparentBackgroundNotFocused = BUILDER
                .comment("Sets the companion app's background to transparent when not in focus")
                .define("transparentBackgroundNotFocused", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        AutoHideHUD.tickDuration = AutoHideHUDConfig.ticksBeforeHiding.get();
        AutoHideHUD.serverTickRate = AutoHideHUDConfig.playerDataServerTickRate.get();
        PlayerDataServer.PORT = AutoHideHUDConfig.playerDataServerPort.get();
    }

    @SubscribeEvent
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        AutoHideHUD.tickDuration = AutoHideHUDConfig.ticksBeforeHiding.get();
        AutoHideHUD.serverTickRate = AutoHideHUDConfig.playerDataServerTickRate.get();
        PlayerDataServer.PORT = AutoHideHUDConfig.playerDataServerPort.get();

        // allow the server to continue running even if we decide to disable auto hiding
        if (AutoHideHUDConfig.autoStartPlayerDataServer.get()) {
            AutoHideHUD.startServer();
        } else {
            AutoHideHUD.stopServer();
        }
    }
}
package com.krookedlilly.autohidehud;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber
public class AutoHideHUDConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // General Settings
    public static final ModConfigSpec.BooleanValue enableAutoHiding;
    public static final ModConfigSpec.IntValue ticksBeforeHiding;

    // Hide Options
    public static final ModConfigSpec.BooleanValue hideHotbar;
    public static final ModConfigSpec.BooleanValue hideHealthBar;
    public static final ModConfigSpec.BooleanValue hideArmorLevel;
    public static final ModConfigSpec.BooleanValue hideFoodLevel;
    public static final ModConfigSpec.BooleanValue hideVehicleHealth;
    public static final ModConfigSpec.BooleanValue hideAirLevel;
    public static final ModConfigSpec.BooleanValue hideExperienceLevel;
    public static final ModConfigSpec.BooleanValue hideContextualInfoBar;
    public static final ModConfigSpec.BooleanValue hideCrossHair;

    // Reveal Options
    public static final ModConfigSpec.IntValue revealWhenPlayerHealthChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenPlayerFoodChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenVehicleHealthChangedBelow;
    public static final ModConfigSpec.IntValue revealWhenPlayerAirChangedBelow;
    public static final ModConfigSpec.BooleanValue revealOnPlayerArmorChange;
    public static final ModConfigSpec.BooleanValue revealOnPlayerExperienceLevelChange;
    public static final ModConfigSpec.BooleanValue revealOnPlayerExperienceProgressChange;
    public static final ModConfigSpec.BooleanValue revealOnPlayerHotbarSlotChange;
    public static final ModConfigSpec.BooleanValue revealOnShowMenu;
    public static final ModConfigSpec.BooleanValue returnToStateFromMenu;

    // Data Server Settings
    public static final ModConfigSpec.BooleanValue autoStartPlayerDataServer;
    public static final ModConfigSpec.IntValue playerDataServerPort;
    public static final ModConfigSpec.IntValue playerDataServerTickRate;

    public static final ModConfigSpec SPEC;

    static {
        // General Settings Section
        BUILDER.comment("Auto Hide Settings").push("generalGroup");

        enableAutoHiding = BUILDER
                .comment("Enable auto hiding")
                .define("enableAutoHiding", true);

        ticksBeforeHiding = BUILDER
                .comment("How long in game ticks before hiding the HUD (20 ticks = 1 sec)")
                .defineInRange("ticksBeforeHiding", 100, 1, Integer.MAX_VALUE);

        BUILDER.pop();

        // Hide Options Section
        BUILDER.comment("Hide Options - Configure which HUD elements to hide").push("hideOptionsGroup");

        hideHotbar = BUILDER
                .comment("Hides the hotbar")
                .define("hideHotbar", true);

        hideHealthBar = BUILDER
                .comment("Hides the player health")
                .define("hideHealthBar", true);

        hideArmorLevel = BUILDER
                .comment("Hides the player armor")
                .define("hideArmorLevel", true);

        hideFoodLevel = BUILDER
                .comment("Hides the player food level")
                .define("hideFoodLevel", true);

        hideVehicleHealth = BUILDER
                .comment("Hides the vehicle health")
                .define("hideVehicleHealth", true);

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

        BUILDER.pop();

        // Reveal Options Section
        BUILDER.comment("Reveal Conditions - Configure when the HUD should be revealed").push("revealConditionsGroup");

        revealWhenPlayerHealthChangedBelow = BUILDER
                .comment("Reveals the HUD when player health changes while below % (0 = disabled)")
                .defineInRange("revealWhenPlayerHealthChangedBelow", 100, 0, 100);

        revealWhenPlayerFoodChangedBelow = BUILDER
                .comment("Reveals the HUD when player food changes while below % (0 = disabled)")
                .defineInRange("revealWhenPlayerFoodChangedBelow", 100, 0, 100);

        revealWhenVehicleHealthChangedBelow = BUILDER
                .comment("Reveals the HUD when vehicle health changes while below % (0 = disabled)")
                .defineInRange("revealWhenVehicleHealthChangedBelow", 100, 0, 100);

        revealWhenPlayerAirChangedBelow = BUILDER
                .comment("Reveals the HUD when player air changes while below % (0 = disabled)")
                .defineInRange("revealWhenPlayerAirChangedBelow", 100, 0, 100);

        revealOnPlayerArmorChange = BUILDER
                .comment("Reveals the HUD when player armour changes")
                .define("revealOnPlayerArmorChange", true);

        revealOnPlayerExperienceLevelChange = BUILDER
                .comment("Reveals the HUD when player experience level changes")
                .define("revealOnPlayerExperienceLevelChange", true);

        revealOnPlayerExperienceProgressChange = BUILDER
                .comment("Reveals the HUD when player experience progress changes")
                .define("revealOnPlayerExperienceProgressChange", true);

        revealOnPlayerHotbarSlotChange = BUILDER
                .comment("Reveals the HUD when selected hotbar slot changes")
                .define("revealOnPlayerHotbarSlotChange", true);

        revealOnShowMenu = BUILDER
                .comment("Reveals the HUD when in a menu")
                .define("revealOnShowMenu", true);

        returnToStateFromMenu = BUILDER
                .comment("When Show in menu is ON should the HUD return to the state it was in when the menu is closed?")
                .define("returnToStateFromMenu", true);

        BUILDER.pop();

        // Data Server Section
        BUILDER.comment("Data Server Settings - Configure the player data server").push("dataServerGroup");

        autoStartPlayerDataServer = BUILDER
                .comment("Should the player data server start automatically when loading into a world")
                .define("autoStartPlayerDataServer", false);

        playerDataServerPort = BUILDER
                .comment("Specifies the port number the player data server will attempt to use")
                .defineInRange("playerDataServerPort", 33333, 0, 65535);

        playerDataServerTickRate = BUILDER
                .comment("How often will the data server send updates to clients in game ticks (20 ticks = 1 sec)")
                .defineInRange("playerDataServerTickRate", 5, 1, Integer.MAX_VALUE);

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

        if (AutoHideHUDConfig.autoStartPlayerDataServer.get()) {
            AutoHideHUD.startServer();
        } else {
            AutoHideHUD.stopServer();
        }
    }
}
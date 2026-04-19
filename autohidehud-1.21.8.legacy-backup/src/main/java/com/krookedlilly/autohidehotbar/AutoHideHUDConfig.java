package com.krookedlilly.autohidehotbar;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber
public class AutoHideHUDConfig {
    // define the config builder
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // add config properties
    public static final ModConfigSpec.IntValue ticksBeforeHiding = BUILDER
            .comment("How long in game ticks before hiding the HUD (20 ticks = 1 sec)")
            .defineInRange("ticksBeforeHiding", 100, 1, Integer.MAX_VALUE);

    // hide options
    public static final ModConfigSpec.BooleanValue hideHotbar = BUILDER
                .comment("Hides the hotbar")
                .define("hideHotbar", true);

    public static final ModConfigSpec.BooleanValue hideHealthBar = BUILDER
            .comment("Hides the player health")
            .define("hideHealthBar", true);

    public static final ModConfigSpec.BooleanValue hideArmorLevel = BUILDER
            .comment("Hides the player armor")
            .define("hideArmorLevel", true);

    public static final ModConfigSpec.BooleanValue hideFoodLevel = BUILDER
            .comment("Hides the player food level")
            .define("hideFoodLevel", true);

    public static final ModConfigSpec.BooleanValue hideVehicleHealth = BUILDER
            .comment("Hides the vehicle health")
            .define("hideVehicleHealth", true);

    public static final ModConfigSpec.BooleanValue hideAirLevel = BUILDER
            .comment("Hides the player air level")
            .define("hideAirLevel", true);

    public static final ModConfigSpec.BooleanValue hideExperienceLevel = BUILDER
            .comment("Hides the player level")
            .define("hideExperienceLevel", true);

    public static final ModConfigSpec.BooleanValue hideContextualInfoBar = BUILDER
            .comment("Hides the contextual bar (experience progress and player nav)")
            .define("hideContextualInfoBar", true);

    public static final ModConfigSpec.BooleanValue hideCrossHair = BUILDER
            .comment("Hides the crosshairs")
            .define("hideCrossHair", true);

    // reveal options
    public static final ModConfigSpec.IntValue revealWhenPlayerHealthChangedBelow
            = BUILDER
            .comment("Reveals the HUD when player health changes while below % (0 = disabled)")
            .defineInRange("revealWhenPlayerHealthChangedBelow", 100, 0, 100);

    public static final ModConfigSpec.IntValue revealWhenPlayerFoodChangedBelow = BUILDER
            .comment("Reveals the HUD when player food changes while below % (0 = disabled)")
            .defineInRange("revealWhenPlayerFoodChangedBelow", 100, 0, 100);

    public static final ModConfigSpec.IntValue revealWhenVehicleHealthChangedBelow = BUILDER
            .comment("Reveals the HUD when vehicle health changes while below % (0 = disabled)")
            .defineInRange("revealWhenVehicleHealthChangedBelow", 100, 0, 100);

    public static final ModConfigSpec.IntValue revealWhenPlayerAirChangedBelow = BUILDER
            .comment("Reveals the HUD when player air changes while below % (0 = disabled)")
            .defineInRange("revealWhenPlayerAirChangedBelow", 100, 0, 100);


    public static final ModConfigSpec.BooleanValue revealOnPlayerArmorChange = BUILDER
            .comment("Reveals the HUD when player armour changes")
            .define("revealOnPlayerArmorChange", true);

    public static final ModConfigSpec.BooleanValue revealOnPlayerExperienceLevelChange = BUILDER
            .comment("Reveals the HUD when player experience level changes")
            .define("revealOnPlayerExperienceLevelChange", true);

    public static final ModConfigSpec.BooleanValue revealOnPlayerExperienceProgressChange = BUILDER
            .comment("Reveals the HUD when player experience progress changes")
            .define("revealOnPlayerExperienceProgressChange", true);

    public static final ModConfigSpec.BooleanValue revealOnPlayerHotbarSlotChange = BUILDER
            .comment("Reveals the HUD when selected hotbar slot changes")
            .define("revealOnPlayerHotbarSlotChange", true);

    public static final ModConfigSpec.BooleanValue revealOnShowMenu = BUILDER
            .comment("Reveals the HUD when in a menu")
            .define("revealOnShowMenu", true);

    public static final ModConfigSpec.BooleanValue returnToStateFromMenu = BUILDER
            .comment("When Show in menu is ON should the HUD return to the state it was in when the menu is closed?")
            .define("returnToStateFromMenu", true);

    // build the config
    public static final ModConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        AutoHideHUD.tickDuration = AutoHideHUDConfig.ticksBeforeHiding.get();
    }

    @SubscribeEvent
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        AutoHideHUD.tickDuration = AutoHideHUDConfig.ticksBeforeHiding.get();
    }
}
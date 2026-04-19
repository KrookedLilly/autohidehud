package com.krookedlilly.autohidehotbar;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = AutoHideHUD.MOD_ID, dist = Dist.CLIENT)
public class AutoHideHUD {
    public static final String MOD_ID = "autohidehud";
    private static final PlayerData lastPlayerData = new PlayerData();
    private static final PlayerData tempPlayerData = new PlayerData();

    private static int currentTick = 0;
    private static int lastUpdatedTick = 0;
    private static boolean hudState = true;
    private static boolean preventHide = false;

    protected static final Logger LOGGER = LogUtils.getLogger();
    protected static int tickDuration = 100;

    public AutoHideHUD(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AutoHideHUDConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        currentTick++;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player != null) {
            tempPlayerData.health = player.getHealth();
            tempPlayerData.maxHealth = player.getMaxHealth();
            tempPlayerData.armor = player.getArmorValue();
            tempPlayerData.foodLevel = player.getFoodData().getFoodLevel();
            tempPlayerData.airSupply = player.getAirSupply();
            tempPlayerData.isCreative = player.isCreative();
            tempPlayerData.experienceLevel = player.experienceLevel;
            tempPlayerData.experienceProgress = player.experienceProgress;
            tempPlayerData.hotbarSlot = player.getInventory().getSelectedSlot();
            tempPlayerData.vehicleHealth = .01f;
            tempPlayerData.vehicleMaxHealth = .01f;
            tempPlayerData.inMenu = minecraft.screen != null;

            if (player.getVehicle() instanceof LivingEntity vehicle) {
                tempPlayerData.vehicleHealth = vehicle.getHealth();
                tempPlayerData.vehicleMaxHealth = vehicle.getMaxHealth();
            }

            if (tempPlayerData.inMenu != lastPlayerData.inMenu && AutoHideHUDConfig.revealOnShowMenu.get()) {
                // menu state changed
                lastPlayerData.inMenu = tempPlayerData.inMenu;

                if (tempPlayerData.inMenu && AutoHideHUDConfig.returnToStateFromMenu.get()) {
                    if (!hudState) {
                        // the HUD was not being shown when the menu was opened
                        // so temp disable render cancel
                        preventHide = true;
                    } else {
                        // the HUD was being shown when the menu was opened
                        preventHide = false;
                        lastUpdatedTick = currentTick;
                    }
                } else if (tempPlayerData.inMenu) {
                    preventHide = false;
                    lastUpdatedTick = currentTick;
                } else {
                    preventHide = false;
                }
            }

            if ((tempPlayerData.health != lastPlayerData.health && AutoHideHUDConfig.revealWhenPlayerHealthChangedBelow.get() / 100f > tempPlayerData.health / tempPlayerData.maxHealth) ||
                    (tempPlayerData.vehicleHealth != lastPlayerData.vehicleHealth && AutoHideHUDConfig.revealWhenVehicleHealthChangedBelow.get() / 100f > tempPlayerData.vehicleHealth / tempPlayerData.vehicleMaxHealth) ||
                    (tempPlayerData.armor != lastPlayerData.armor && AutoHideHUDConfig.revealOnPlayerArmorChange.get()) ||
                    (tempPlayerData.foodLevel != lastPlayerData.foodLevel && AutoHideHUDConfig.revealWhenPlayerFoodChangedBelow.get() / 100f > (float)tempPlayerData.foodLevel / (float)tempPlayerData.maxFoodLevel) ||
                    (tempPlayerData.airSupply != lastPlayerData.airSupply && AutoHideHUDConfig.revealWhenPlayerAirChangedBelow.get() / 100f > (float)tempPlayerData.airSupply / (float)tempPlayerData.maxAirSupply) ||
                    (tempPlayerData.isCreative != lastPlayerData.isCreative) ||
                    (tempPlayerData.experienceLevel != lastPlayerData.experienceLevel && AutoHideHUDConfig.revealOnPlayerExperienceLevelChange.get()) ||
                    (tempPlayerData.experienceProgress != lastPlayerData.experienceProgress && AutoHideHUDConfig.revealOnPlayerExperienceProgressChange.get()) ||
                    (tempPlayerData.hotbarSlot != lastPlayerData.hotbarSlot && AutoHideHUDConfig.revealOnPlayerHotbarSlotChange.get())
            ) {
                lastUpdatedTick = currentTick;

                lastPlayerData.health = tempPlayerData.health;
                lastPlayerData.maxHealth = tempPlayerData.maxHealth;
                lastPlayerData.armor = tempPlayerData.armor;
                lastPlayerData.foodLevel = tempPlayerData.foodLevel;
                lastPlayerData.airSupply = tempPlayerData.airSupply;
                lastPlayerData.isCreative = tempPlayerData.isCreative;
                lastPlayerData.experienceLevel = tempPlayerData.experienceLevel;
                lastPlayerData.experienceProgress = tempPlayerData.experienceProgress;
                lastPlayerData.hotbarSlot = tempPlayerData.hotbarSlot;
                lastPlayerData.vehicleHealth = tempPlayerData.vehicleHealth;
            }
        }
    }

    @SubscribeEvent
    public void onRenderHUD(RenderGuiLayerEvent.Pre event) {
        if (!preventHide && currentTick - lastUpdatedTick > tickDuration) {
            hudState = false;

            if (shouldHideLayer(event.getName())) {
                // Cancels rendering of the HUD layer.
                event.setCanceled(true);
            }
        } else if (!preventHide) {
            hudState = true;
        }
    }

    private boolean shouldHideLayer(ResourceLocation layerName) {
        if (AutoHideHUDConfig.hideHotbar.get() && layerName.equals(VanillaGuiLayers.HOTBAR)) return true;
        if (AutoHideHUDConfig.hideHealthBar.get() && layerName.equals(VanillaGuiLayers.PLAYER_HEALTH))
            return true;
        if (AutoHideHUDConfig.hideArmorLevel.get() && layerName.equals(VanillaGuiLayers.ARMOR_LEVEL))
            return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.equals(VanillaGuiLayers.FOOD_LEVEL)) return true;
        if (AutoHideHUDConfig.hideVehicleHealth.get() && layerName.equals(VanillaGuiLayers.VEHICLE_HEALTH))
            return true;
        if (AutoHideHUDConfig.hideAirLevel.get() && layerName.equals(VanillaGuiLayers.AIR_LEVEL)) return true;
        if (AutoHideHUDConfig.hideExperienceLevel.get() && layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
            return true;
        if (AutoHideHUDConfig.hideContextualInfoBar.get() && layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR))
            return true;
        if (AutoHideHUDConfig.hideContextualInfoBar.get() && layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND))
            return true;
        if (AutoHideHUDConfig.hideCrossHair.get() && layerName.equals(VanillaGuiLayers.CROSSHAIR))
            return true;
        return false;
    }

    static class PlayerData {
        float health = 20.0f;
        float maxHealth = 20.0f;
        int armor = 0;
        int foodLevel = 20;
        int maxFoodLevel = 20;
        boolean isCreative = false;
        int experienceLevel = 0;
        float experienceProgress = 0f;
        int hotbarSlot = 0;
        int airSupply = 300;
        int maxAirSupply = 300;
        float vehicleHealth = .01f;
        float vehicleMaxHealth = .01f;
        boolean inMenu = false;
    }
}
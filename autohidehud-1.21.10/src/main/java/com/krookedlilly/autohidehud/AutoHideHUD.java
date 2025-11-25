package com.krookedlilly.autohidehud;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

import java.util.ArrayList;

@Mod(value = AutoHideHUD.MOD_ID, dist = Dist.CLIENT)
public class AutoHideHUD {
    public static final String MOD_ID = "autohidehud";

    private static final Gson gson = new Gson();
    private static final PlayerData lastPlayerData = new PlayerData();
    private static final PlayerData tempPlayerData = new PlayerData();

    private static PlayerDataServer dataServer;
    private static int currentTick = 0;
    private static int lastUpdatedTick = 0;
    private static boolean hudState = true;
    private static boolean preventHide = false;

    protected static final Logger LOGGER = LogUtils.getLogger();
    protected static int tickDuration = 100;
    protected static int serverTickRate = 5;

    public AutoHideHUD(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AutoHideHUDConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public static void startServer() {
        if (!AutoHideHUDConfig.autoStartPlayerDataServer.get()) return;

        if (dataServer != null && dataServer.isRunning()) {
            LOGGER.info("Server already running, skipping start");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (minecraft.level == null || !minecraft.level.isClientSide()) {
            LOGGER.info("Level is NULL or not client side, skipping start");
            return;
        }

        if (player == null) {
            LOGGER.info("Local player is NULL, skipping start");
            return;
        }

        LOGGER.info("Starting player data server");
        dataServer = new PlayerDataServer();
        dataServer.start();
    }

    public static void stopServer() {
        if (dataServer == null || !dataServer.isRunning()) {
            LOGGER.info("Data server was NULL or not running. Nothing top stop");
            return;
        }

        if (dataServer != null) {
            dataServer.stop();
            dataServer = null;
            LOGGER.info("Player data server stopped");
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Check if the local player is joining a level
        if (!AutoHideHUDConfig.autoStartPlayerDataServer.get()) return;

        if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer) {
            LOGGER.info("Local player joined world, starting data server");
            startServer();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        currentTick++;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            if (dataServer != null && dataServer.isRunning()) {
                LOGGER.info("Player disconnected, stopping data server");
                stopServer();
            }
        } else {
            tempPlayerData.health = player.getHealth();
            tempPlayerData.maxHealth = player.getMaxHealth();
            tempPlayerData.armor = player.getArmorValue();
            tempPlayerData.foodLevel = player.getFoodData().getFoodLevel();
            tempPlayerData.saturation = player.getFoodData().getSaturationLevel();
            tempPlayerData.airSupply = player.getAirSupply();
            tempPlayerData.isCreative = player.isCreative();
            tempPlayerData.experienceLevel = player.experienceLevel;
            tempPlayerData.experienceProgress = player.experienceProgress;
            tempPlayerData.selectedHotbarSlot = player.getInventory().getSelectedSlot();
            tempPlayerData.vehicleHealth = .01f;
            tempPlayerData.maxVehicleHealth = .01f;
            tempPlayerData.inMenu = minecraft.screen != null;
            tempPlayerData.x = player.getX();
            tempPlayerData.y = player.getY();
            tempPlayerData.z = player.getZ();
            tempPlayerData.yaw = player.getYRot();
            tempPlayerData.pitch = player.getXRot();
            tempPlayerData.facing = player.getDirection().toString();

            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    InventoryItemData data = new InventoryItemData();
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                    data.slot = i;
                    data.id = itemId.toString();
                    data.name = stack.getHoverName().getString();
                    data.count = stack.getCount();
                    data.damage = stack.getDamageValue();
                    data.maxDamage = stack.getMaxDamage();
                    data.durability = data.maxDamage - data.damage;
                    data.enchanted = stack.isEnchanted();

                    tempPlayerData.inventoryData[i] = data;
                } else {
                    tempPlayerData.inventoryData[i] = null;
                }
            }

            tempPlayerData.statusEffects.clear();
            player.getActiveEffects().forEach(effect -> {
                StatusEffectData data = new StatusEffectData();
                data.name = effect.getEffect().getRegisteredName();
                data.amplifier = effect.getAmplifier();
                data.duration = effect.getDuration();
                tempPlayerData.statusEffects.add(data);
            });

            if (player.getVehicle() instanceof LivingEntity vehicle) {
                tempPlayerData.vehicleHealth = vehicle.getHealth();
                tempPlayerData.maxVehicleHealth = vehicle.getMaxHealth();
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
                    (tempPlayerData.vehicleHealth != lastPlayerData.vehicleHealth && AutoHideHUDConfig.revealWhenVehicleHealthChangedBelow.get() / 100f > tempPlayerData.vehicleHealth / tempPlayerData.maxVehicleHealth) ||
                    (tempPlayerData.armor != lastPlayerData.armor && AutoHideHUDConfig.revealOnPlayerArmorChange.get()) ||
                    (tempPlayerData.foodLevel != lastPlayerData.foodLevel && AutoHideHUDConfig.revealWhenPlayerFoodChangedBelow.get() / 100f > (float) tempPlayerData.foodLevel / (float) tempPlayerData.maxFoodLevel) ||
                    (tempPlayerData.airSupply != lastPlayerData.airSupply && AutoHideHUDConfig.revealWhenPlayerAirChangedBelow.get() / 100f > (float) tempPlayerData.airSupply / (float) tempPlayerData.maxAirSupply) ||
                    (tempPlayerData.isCreative != lastPlayerData.isCreative) ||
                    (tempPlayerData.experienceLevel != lastPlayerData.experienceLevel && AutoHideHUDConfig.revealOnPlayerExperienceLevelChange.get()) ||
                    (tempPlayerData.experienceProgress != lastPlayerData.experienceProgress && AutoHideHUDConfig.revealOnPlayerExperienceProgressChange.get()) ||
                    (tempPlayerData.selectedHotbarSlot != lastPlayerData.selectedHotbarSlot && AutoHideHUDConfig.revealOnPlayerHotbarSlotChange.get())
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
                lastPlayerData.selectedHotbarSlot = tempPlayerData.selectedHotbarSlot;
                lastPlayerData.vehicleHealth = tempPlayerData.vehicleHealth;
            }

            if (currentTick % serverTickRate == 0 && dataServer != null) {
                dataServer.broadcastPlayerData(gson.toJson(tempPlayerData));
            }
        }
    }

    @SubscribeEvent
    public void onRenderHUDPre(RenderGuiLayerEvent.Pre event) {
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
        InventoryItemData[] inventoryData = new InventoryItemData[9];
        //        ArrayList<InventoryItemData> inventoryData = new ArrayList<InventoryItemData>();
        ArrayList<StatusEffectData> statusEffects = new ArrayList<StatusEffectData>();
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
    }

    static class StatusEffectData {
        String name = "";
        int amplifier = 0;
        int duration = 0;
    }
}
package com.krookedlilly.autohidehud;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
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
import java.util.Objects;

@Mod(value = AutoHideHUD.MOD_ID, dist = Dist.CLIENT)
public class AutoHideHUD {
    public static final String MOD_ID = "autohidehud";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new Gson();
    private static final PlayerData LAST_PLAYER_DATA = new PlayerData();
    private static final PlayerData TEMP_PLAYER_DATA = new PlayerData();

    private static PlayerDataServer dataServer;
    private static int currentTick = 0;
    private static int lastUpdatedTick = 0;
    private static long fadeStartTime = -1;
    private static boolean hudState = true;
    private static boolean preventHide = false;
    private static float alpha = 1f;
    private static float currentAlpha = 1f;

    protected static int tickDuration = 100;
    protected static int serverTickRate = 5;

    public static boolean inMenu = false;
    public static float hotbarAlpha = 1f;

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
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            if (dataServer != null && dataServer.isRunning()) {
                LOGGER.info("Player disconnected, stopping data server");
                stopServer();
            }
        } else {
            TEMP_PLAYER_DATA.health = player.getHealth();
            TEMP_PLAYER_DATA.maxHealth = player.getMaxHealth();
            TEMP_PLAYER_DATA.armor = player.getArmorValue();
            TEMP_PLAYER_DATA.foodLevel = player.getFoodData().getFoodLevel();
            TEMP_PLAYER_DATA.saturation = player.getFoodData().getSaturationLevel();
            TEMP_PLAYER_DATA.airSupply = player.getAirSupply();
            TEMP_PLAYER_DATA.isCreative = player.isCreative();
            TEMP_PLAYER_DATA.experienceLevel = player.experienceLevel;
            TEMP_PLAYER_DATA.experienceProgress = player.experienceProgress;
            TEMP_PLAYER_DATA.selectedHotbarSlot = player.getInventory().getSelectedSlot();
            TEMP_PLAYER_DATA.vehicleHealth = .01f;
            TEMP_PLAYER_DATA.maxVehicleHealth = .01f;
//            TEMP_PLAYER_DATA.inMenu = minecraft.screen != null;
            TEMP_PLAYER_DATA.inMenu = minecraft.screen != null && player.getBedOrientation() == null && (minecraft.screen.isInGameUi() || minecraft.screen.isPauseScreen());
            TEMP_PLAYER_DATA.x = player.getX();
            TEMP_PLAYER_DATA.y = player.getY();
            TEMP_PLAYER_DATA.z = player.getZ();
            TEMP_PLAYER_DATA.yaw = player.getYRot();
            TEMP_PLAYER_DATA.pitch = player.getXRot();
            TEMP_PLAYER_DATA.facing = player.getDirection().toString();

            TEMP_PLAYER_DATA.showPosition = AutoHideHUDConfig.showPosition.get();
            TEMP_PLAYER_DATA.showFacing = AutoHideHUDConfig.showFacing.get();
            TEMP_PLAYER_DATA.showHealth = AutoHideHUDConfig.showHealth.get();
            TEMP_PLAYER_DATA.showHealthWarning = AutoHideHUDConfig.showHealthWarning.get();
            TEMP_PLAYER_DATA.showArmorLevel = AutoHideHUDConfig.showArmorLevel.get();
            TEMP_PLAYER_DATA.showFoodLevel = AutoHideHUDConfig.showFoodLevel.get();
            TEMP_PLAYER_DATA.showSaturationLevel = AutoHideHUDConfig.showSaturationLevel.get();
            TEMP_PLAYER_DATA.showAirLevel = AutoHideHUDConfig.showAirLevel.get();
            TEMP_PLAYER_DATA.showVehicleHealth = AutoHideHUDConfig.showVehicleHealth.get();
            TEMP_PLAYER_DATA.showExperienceLevel = AutoHideHUDConfig.showExperienceLevel.get();
            TEMP_PLAYER_DATA.showExperienceProgress = AutoHideHUDConfig.showExperienceProgress.get();
            TEMP_PLAYER_DATA.showStatusEffects = AutoHideHUDConfig.showStatusEffects.get();
            TEMP_PLAYER_DATA.showHotbarItems = AutoHideHUDConfig.showHotbarItems.get();
            TEMP_PLAYER_DATA.focusedBackgroundColor = AutoHideHUDConfig.focusedBackgroundColor.get();
            TEMP_PLAYER_DATA.transparentBackgroundNotFocused = AutoHideHUDConfig.transparentBackgroundNotFocused.get();
            TEMP_PLAYER_DATA.portNumber = PlayerDataServer.PORT;

            inMenu = TEMP_PLAYER_DATA.inMenu;

            if (!inMenu) {
                currentTick++;
                LOGGER.info("bed dir: {}", player.getBedOrientation());
            }
            else {
//                LOGGER.info("screen name: {}", minecraft.screen.getTitle().getString());
            }

            Inventory inventory = player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.getItem(i);
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

                    TEMP_PLAYER_DATA.inventoryData[i] = data;
                } else {
                    TEMP_PLAYER_DATA.inventoryData[i] = null;
                }
            }

            ItemStack stack = player.getOffhandItem();
            if (!stack.isEmpty()) {
                InventoryItemData data = new InventoryItemData();
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

                data.slot = 9;
                data.id = itemId.toString();
                data.name = stack.getHoverName().getString();
                data.count = stack.getCount();
                data.damage = stack.getDamageValue();
                data.maxDamage = stack.getMaxDamage();
                data.durability = data.maxDamage - data.damage;
                data.enchanted = stack.isEnchanted();

                TEMP_PLAYER_DATA.inventoryData[9] = data;
            } else {
                TEMP_PLAYER_DATA.inventoryData[9] = null;
            }

            TEMP_PLAYER_DATA.statusEffects.clear();
            player.getActiveEffects().forEach(effect -> {
                StatusEffectData data = new StatusEffectData();
                data.name = effect.getEffect().getRegisteredName();
                data.amplifier = effect.getAmplifier();
                data.duration = effect.getDuration();
                TEMP_PLAYER_DATA.statusEffects.add(data);
            });

            if (player.getVehicle() instanceof LivingEntity vehicle) {
                TEMP_PLAYER_DATA.vehicleHealth = vehicle.getHealth();
                TEMP_PLAYER_DATA.maxVehicleHealth = vehicle.getMaxHealth();
            }

            if (TEMP_PLAYER_DATA.inMenu != LAST_PLAYER_DATA.inMenu) {
                // menu state changed
                LAST_PLAYER_DATA.inMenu = TEMP_PLAYER_DATA.inMenu;

                if (TEMP_PLAYER_DATA.inMenu && AutoHideHUDConfig.returnToStateFromMenu.get()) {
                    if (!hudState) {
//                        if (AutoHideHUDConfig.hideChatMessages.get() && inMenu && minecraft.screen.getTitle().getString().equals("Chat screen")) {
//                            LOGGER.info("here");
//                            preventHide = false;
//                            return;
//                        }
                        // the HUD was not being shown when the menu was opened
                        // so temp disable render cancel
                        preventHide = true;
                        hotbarAlpha = 1f;
                    } else {
                        // the HUD was being shown when the menu was opened
                        preventHide = false;
                        lastUpdatedTick = currentTick;
                    }
                } else if (TEMP_PLAYER_DATA.inMenu) {
                    // we opened a menu but do not care about maintaining state
                    preventHide = false;
                    lastUpdatedTick = currentTick;
                    hotbarAlpha = 1f;
                } else if (AutoHideHUDConfig.returnToStateFromMenu.get()) {
                    // we closed a menu but do care about maintaining state
                    preventHide = false;
                } else {
                    // we closed a menu and do not care about maintaining state
                    preventHide = false;
                    lastUpdatedTick = currentTick;
                }
            }

            if ((TEMP_PLAYER_DATA.isCreative != LAST_PLAYER_DATA.isCreative) ||
                    (AutoHideHUDConfig.revealWhenPlayerHealthChangedBelow.get() / 100f > TEMP_PLAYER_DATA.health / TEMP_PLAYER_DATA.maxHealth) ||
                    (AutoHideHUDConfig.revealWhenVehicleHealthChangedBelow.get() / 100f > TEMP_PLAYER_DATA.vehicleHealth / TEMP_PLAYER_DATA.maxVehicleHealth) ||
                    (AutoHideHUDConfig.revealWhenPlayerFoodChangedBelow.get() / 100f > (float) TEMP_PLAYER_DATA.foodLevel / (float) TEMP_PLAYER_DATA.maxFoodLevel) ||
                    (AutoHideHUDConfig.revealWhenPlayerAirChangedBelow.get() / 100f > (float) TEMP_PLAYER_DATA.airSupply / (float) TEMP_PLAYER_DATA.maxAirSupply) ||
                    (TEMP_PLAYER_DATA.armor != LAST_PLAYER_DATA.armor && AutoHideHUDConfig.revealOnPlayerArmorChange.get()) ||
                    (TEMP_PLAYER_DATA.experienceLevel != LAST_PLAYER_DATA.experienceLevel && TEMP_PLAYER_DATA.experienceLevel % AutoHideHUDConfig.revealOnPlayerExperienceLevelChange.get() == 0) ||
                    (TEMP_PLAYER_DATA.experienceProgress != LAST_PLAYER_DATA.experienceProgress && AutoHideHUDConfig.revealOnPlayerExperienceProgressChange.get()) ||
                    (TEMP_PLAYER_DATA.selectedHotbarSlot != LAST_PLAYER_DATA.selectedHotbarSlot && AutoHideHUDConfig.revealOnPlayerHotbarSlotChange.get())
            ) {
                lastUpdatedTick = currentTick;

                LAST_PLAYER_DATA.health = TEMP_PLAYER_DATA.health;
                LAST_PLAYER_DATA.maxHealth = TEMP_PLAYER_DATA.maxHealth;
                LAST_PLAYER_DATA.armor = TEMP_PLAYER_DATA.armor;
                LAST_PLAYER_DATA.foodLevel = TEMP_PLAYER_DATA.foodLevel;
                LAST_PLAYER_DATA.airSupply = TEMP_PLAYER_DATA.airSupply;
                LAST_PLAYER_DATA.isCreative = TEMP_PLAYER_DATA.isCreative;
                LAST_PLAYER_DATA.experienceLevel = TEMP_PLAYER_DATA.experienceLevel;
                LAST_PLAYER_DATA.experienceProgress = TEMP_PLAYER_DATA.experienceProgress;
                LAST_PLAYER_DATA.selectedHotbarSlot = TEMP_PLAYER_DATA.selectedHotbarSlot;
                LAST_PLAYER_DATA.vehicleHealth = TEMP_PLAYER_DATA.vehicleHealth;
            }

            if (currentTick % serverTickRate == 0 && dataServer != null) {
                dataServer.broadcastPlayerData(GSON.toJson(TEMP_PLAYER_DATA));
            }
        }
    }

    @SubscribeEvent
    public void onRenderHUDPre(RenderGuiLayerEvent.Pre event) {
        if (!AutoHideHUDConfig.enableAutoHiding.get()) return;
        if (!preventHide && currentTick - lastUpdatedTick > tickDuration) {
            float targetAlpha = (float) AutoHideHUDConfig.targetOpacity.getAsDouble();

            hudState = false;

            if (shouldHideLayer(event.getName())) {
                if (currentAlpha <= targetAlpha) {
                    alpha = currentAlpha;

                    if (AutoHideHUDConfig.hideHotbar.get() && event.getName().equals(VanillaGuiLayers.HOTBAR)) {
                        hotbarAlpha = alpha;
                    }

                    return;
                }

                float fadeDuration = (float) AutoHideHUDConfig.fadeOutSpeed.getAsDouble();

                if (fadeDuration == 0f) {
                    alpha = currentAlpha = targetAlpha;
                    if (AutoHideHUDConfig.hideHotbar.get() && event.getName().equals(VanillaGuiLayers.HOTBAR)) {
                        hotbarAlpha = alpha;
                    }

                    fadeStartTime = -1;
                    return;
                }

                // mark the start time
                if (fadeStartTime == -1) {
                    fadeStartTime = System.currentTimeMillis();
                }

                currentAlpha = lerpAlpha((long) (fadeDuration * 1000), targetAlpha);

                if (currentAlpha <= targetAlpha) {
                    currentAlpha = targetAlpha;
                    fadeStartTime = -1;
                }

                alpha = currentAlpha;

                if (AutoHideHUDConfig.hideHotbar.get() && event.getName().equals(VanillaGuiLayers.HOTBAR)) {
                    hotbarAlpha = alpha;
                }
            } else {
                alpha = 1f;
//                LOGGER.info("layer: {}", event.getName());
            }
        } else if (!preventHide) {
            hudState = true;
            alpha = hotbarAlpha = currentAlpha = 1f;
            fadeStartTime = -1;
        } else {
            Minecraft minecraft = Minecraft.getInstance();
//            if (minecraft.screen != null)
//                LOGGER.info("minecraft screensafsdfasfasdfda pre check: {}", minecraft.screen.getTitle().getString());
        }
    }

    private boolean shouldHideLayer(ResourceLocation layerName) {
        if (AutoHideHUDConfig.hideHotbar.get() && layerName.equals(VanillaGuiLayers.HOTBAR)) return true;
        if (AutoHideHUDConfig.hideSelectedItemName.get() && layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
            return true;
        if (AutoHideHUDConfig.hideHealthBar.get() && layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)) return true;
        if (AutoHideHUDConfig.hideHealthBar.get() && layerName.toString().equals("appleskin:health_offset"))
            return true;
        if (AutoHideHUDConfig.hideHealthBar.get() && layerName.toString().equals("appleskin:health_restored"))
            return true;
        if (AutoHideHUDConfig.hideArmorLevel.get() && layerName.equals(VanillaGuiLayers.ARMOR_LEVEL)) return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.equals(VanillaGuiLayers.FOOD_LEVEL)) return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.toString().equals("appleskin:hunger_restored"))
            return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.toString().equals("appleskin:food_offset")) return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.toString().equals("appleskin:saturation_level"))
            return true;
        if (AutoHideHUDConfig.hideFoodLevel.get() && layerName.toString().equals("appleskin:exhaustion_level"))
            return true;
        if (AutoHideHUDConfig.hideVehicleHealth.get() && layerName.equals(VanillaGuiLayers.VEHICLE_HEALTH)) return true;
        if (AutoHideHUDConfig.hideAirLevel.get() && layerName.equals(VanillaGuiLayers.AIR_LEVEL)) return true;
        if (AutoHideHUDConfig.hideExperienceLevel.get() && layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
            return true;
        if (AutoHideHUDConfig.hideContextualInfoBar.get() && layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR))
            return true;
        if (AutoHideHUDConfig.hideContextualInfoBar.get() && layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND))
            return true;
        if (AutoHideHUDConfig.hideCrossHair.get() && layerName.equals(VanillaGuiLayers.CROSSHAIR)) return true;
        if (AutoHideHUDConfig.hideStatusEffects.get() && layerName.equals(VanillaGuiLayers.EFFECTS)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.CHAT)) return true;
//        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.SUBTITLE_OVERLAY)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.SLEEP_OVERLAY)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.TITLE)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.BOSS_OVERLAY)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.SCOREBOARD_SIDEBAR)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.DEMO_OVERLAY)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.AFTER_CAMERA_DECORATIONS)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.OVERLAY_MESSAGE)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.TAB_LIST)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.SPECTATOR_TOOLTIP)) return true;
        if (AutoHideHUDConfig.hideChatMessages.get() && layerName.equals(VanillaGuiLayers.CAMERA_OVERLAYS)) return true;

        if (AutoHideHUDConfig.hideSleepOverlay.get() && layerName.equals(VanillaGuiLayers.SUBTITLE_OVERLAY)) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (minecraft.screen != null && player != null && minecraft.screen.getTitle().getString().equals("Chat screen") && player.getBedOrientation() != null) {
                return true;
            }
        }

//        Minecraft minecraft = Minecraft.getInstance();
//        LOGGER.info("minecraft screen pre check: {}", minecraft.screen);
//        if (minecraft.screen != null) {
//            LOGGER.info("minecraft screen post check: {}", minecraft.screen.getTitle().getString());
//            return true;
//        }

        return false;
    }

    public float lerpAlpha(long duration, float endAlpha) {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - fadeStartTime;
        float t = Math.min(1.0f, (float) elapsed / (float) duration);
        return 1f + (endAlpha - 1f) * t;
    }

    public static int applyAlpha(int color) {
        return applyAlpha(color, false);
    }

    public static int applyAlpha(int color, boolean isText) {
        if (alpha >= 1f || inMenu) return color;

        // Extract ARGB components
        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        int newAlpha = (int) (alpha * (AutoHideHUD.alpha + (isText && alpha != 0f ? 0.005f : 0f)));

        // Recombine
        return (newAlpha << 24) | rgb;
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
        String focusedBackgroundColor = "#000000";
        boolean transparentBackgroundNotFocused = true;
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
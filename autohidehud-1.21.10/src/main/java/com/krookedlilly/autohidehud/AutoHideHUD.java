package com.krookedlilly.autohidehud;

import com.google.gson.Gson;
import com.krookedlilly.autohidehud.common.model.InventoryItemData;
import com.krookedlilly.autohidehud.common.model.PlayerData;
import com.krookedlilly.autohidehud.common.model.StatusEffectData;
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
    public static boolean wasSleeping = false;

    // posePushed is set in onRenderHUDPre when we push the pose matrix for a
    // HUD-position offset, and consumed in onRenderHUDPost to pop it. The
    // single-boolean approach is only safe because this mod never cancels
    // RenderGuiLayerEvent.Pre (so Post is guaranteed to fire) and layers do
    // not nest. If a future change ever cancels Pre, this needs to become a
    // ResourceLocation-keyed map so the right pushes get popped.
    private static boolean posePushed = false;
    private static final int[] NO_OFFSET = {0, 0};

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
            LOGGER.info("Data server was NULL or not running. Nothing to stop");
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
        if (AutoHideHUDKeyBindings.REVEAL_KEY.get().consumeClick()) {
            // This is client-side, so send packet to server if needed
            preventHide = false;
            lastUpdatedTick = currentTick;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            if (dataServer != null && dataServer.isRunning()) {
                LOGGER.info("Player disconnected, stopping data server");
                stopServer();
            }
        } else {
            Inventory inventory = player.getInventory();

            TEMP_PLAYER_DATA.health = player.getHealth();
            TEMP_PLAYER_DATA.maxHealth = player.getMaxHealth();
            TEMP_PLAYER_DATA.armor = player.getArmorValue();
            TEMP_PLAYER_DATA.foodLevel = player.getFoodData().getFoodLevel();
            TEMP_PLAYER_DATA.saturation = player.getFoodData().getSaturationLevel();
            TEMP_PLAYER_DATA.airSupply = player.getAirSupply();
            TEMP_PLAYER_DATA.isCreative = player.isCreative();
            TEMP_PLAYER_DATA.experienceLevel = player.experienceLevel;
            TEMP_PLAYER_DATA.experienceProgress = player.experienceProgress;
            TEMP_PLAYER_DATA.selectedHotbarSlot = inventory.getSelectedSlot();
            TEMP_PLAYER_DATA.vehicleHealth = .01f;
            TEMP_PLAYER_DATA.maxVehicleHealth = .01f;
            TEMP_PLAYER_DATA.inMenu = minecraft.screen != null && player.getBedOrientation() == null;
            TEMP_PLAYER_DATA.x = player.getX();
            TEMP_PLAYER_DATA.y = player.getY();
            TEMP_PLAYER_DATA.z = player.getZ();
            TEMP_PLAYER_DATA.yaw = player.getYRot();
            TEMP_PLAYER_DATA.pitch = player.getXRot();
            TEMP_PLAYER_DATA.facing = player.getDirection().toString();

            TEMP_PLAYER_DATA.showPosition = AutoHideHUDConfig.showPosition.get();
            TEMP_PLAYER_DATA.showFacing = AutoHideHUDConfig.showFacing.get();
//            TEMP_PLAYER_DATA.showYawPitch = AutoHideHUDConfig.showYawPitch.get();
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
            TEMP_PLAYER_DATA.showHotbarItemsDurability = AutoHideHUDConfig.showHotbarItemsDurability.get();
            TEMP_PLAYER_DATA.showHotbarItemsDurabilityPercent = AutoHideHUDConfig.showHotbarItemsDurabilityPercent.get();
            TEMP_PLAYER_DATA.showSelectedItemLabel = AutoHideHUDConfig.showSelectedItemLabel.get();
            TEMP_PLAYER_DATA.focusedBackgroundColor = AutoHideHUDConfig.focusedBackgroundColor.get();
            TEMP_PLAYER_DATA.focusedBackgroundOpacity = AutoHideHUDConfig.focusedBackgroundOpacity.get();
            TEMP_PLAYER_DATA.notFocusedBackgroundColor = AutoHideHUDConfig.notFocusedBackgroundColor.get();
            TEMP_PLAYER_DATA.notFocusedBackgroundOpacity = AutoHideHUDConfig.notFocusedBackgroundOpacity.get();
            TEMP_PLAYER_DATA.portNumber = PlayerDataServer.PORT;

            inMenu = TEMP_PLAYER_DATA.inMenu;

            if (!inMenu) {
                currentTick++;
            }

            InventoryItemData selectedItem = null;
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
                    data.selected = i == TEMP_PLAYER_DATA.selectedHotbarSlot;

                    if (data.selected)
                        selectedItem = data;

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

            if ((TEMP_PLAYER_DATA.isCreative && AutoHideHUDConfig.revealIfInCreative.get()) ||
                    (AutoHideHUDConfig.revealWhenPlayerHealthChangedBelow.get() / 100f > TEMP_PLAYER_DATA.health / TEMP_PLAYER_DATA.maxHealth) ||
                    (AutoHideHUDConfig.revealWhenVehicleHealthChangedBelow.get() / 100f > TEMP_PLAYER_DATA.vehicleHealth / TEMP_PLAYER_DATA.maxVehicleHealth) ||
                    (AutoHideHUDConfig.revealWhenPlayerFoodChangedBelow.get() / 100f > (float) TEMP_PLAYER_DATA.foodLevel / (float) TEMP_PLAYER_DATA.maxFoodLevel) ||
                    (AutoHideHUDConfig.revealWhenPlayerAirChangedBelow.get() / 100f > (float) TEMP_PLAYER_DATA.airSupply / (float) TEMP_PLAYER_DATA.maxAirSupply) ||
                    (selectedItem != null && selectedItem.maxDamage != 0 && AutoHideHUDConfig.revealOnSelectedHotbarDurabilityChange.get() / 100f > (float) selectedItem.durability / (float) selectedItem.maxDamage) ||
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
        int[] off = getLayerOffset(event.getName());
        int rot = getLayerRotation(event.getName());
        if (off[0] != 0 || off[1] != 0 || rot != 0) {
            var pose = event.getGuiGraphics().pose();
            pose.pushMatrix();
            if (rot != 0) {
                // Rotate around screen center, then apply user offset on top.
                // Composition (read right-to-left): vertex - centerXY -> rotate -> + centerXY + offsetXY.
                int cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
                int cy = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
                pose.translate(off[0] + cx, off[1] + cy);
                pose.rotate((float) Math.toRadians(rot));
                pose.translate(-cx, -cy);
            } else {
                pose.translate(off[0], off[1]);
            }
            posePushed = true;
        }


        // When any screen is open, force HUD visible and short-circuit the fade
        // logic. lerpAlpha is time-based (System.currentTimeMillis), so it keeps
        // progressing even when the game tick is paused — NeoForge 21.0-21.4 stops
        // firing ClientTickEvent.Post during pause, so onClientTick never resets us.
        // Without this gate, alpha drains while in a menu, the item-cancel mixins
        // skip rendering items, and the HUD disappears behind the screen.
        if (Minecraft.getInstance().screen != null) {
            hudState = true;
            alpha = hotbarAlpha = currentAlpha = 1f;
            fadeStartTime = -1;
            return;
        }

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
            }
        } else if (!preventHide) {
            hudState = true;
            alpha = hotbarAlpha = currentAlpha = 1f;
            fadeStartTime = -1;
        }
    }

    @SubscribeEvent
    public void onRenderHUDPost(RenderGuiLayerEvent.Post event) {
        if (posePushed) {
            event.getGuiGraphics().pose().popMatrix();
            posePushed = false;
        }
        // Reset alpha so the GuiGraphics color-modifying mixin no-ops on UI rendered after
        // this layer (JEI/REI overlays). alpha is read per-draw-call so a per-layer reset works.
        // hotbarAlpha is intentionally NOT reset here: hotbar item draws are queued during
        // the HOTBAR layer's render but flushed later via GuiRenderer.submitBlitFromItemAtlas,
        // and our GuiRendererMixin reads hotbarAlpha at flush time. inMenu guards JEI items.
        alpha = 1f;
    }

    // Returns the effective (x, y) offset for a given HUD layer as an int[2].
    // effectiveOffset = globalOffset (when layer is "core HUD") + per-element override.
    // Crosshair, chat, status effects are not in "core HUD" — they get per-element only.
    private int[] getLayerOffset(ResourceLocation layerName) {
        int gx = AutoHideHUDConfig.globalOffsetX.get();
        int gy = AutoHideHUDConfig.globalOffsetY.get();

        if (layerName.equals(VanillaGuiLayers.HOTBAR))
            return new int[]{gx + AutoHideHUDConfig.hotbarOffsetX.get(), gy + AutoHideHUDConfig.hotbarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layerName.toString().equals("appleskin:health_offset")
                || layerName.toString().equals("appleskin:health_restored"))
            return new int[]{gx + AutoHideHUDConfig.healthBarOffsetX.get(), gy + AutoHideHUDConfig.healthBarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.ARMOR_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.armorOffsetX.get(), gy + AutoHideHUDConfig.armorOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layerName.toString().equals("appleskin:hunger_restored")
                || layerName.toString().equals("appleskin:food_offset")
                || layerName.toString().equals("appleskin:saturation_level")
                || layerName.toString().equals("appleskin:exhaustion_level"))
            return new int[]{gx + AutoHideHUDConfig.foodOffsetX.get(), gy + AutoHideHUDConfig.foodOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.AIR_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.airOffsetX.get(), gy + AutoHideHUDConfig.airOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.VEHICLE_HEALTH))
            return new int[]{gx + AutoHideHUDConfig.vehicleHealthOffsetX.get(), gy + AutoHideHUDConfig.vehicleHealthOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.experienceLevelOffsetX.get(), gy + AutoHideHUDConfig.experienceLevelOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR) || layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND))
            return new int[]{gx + AutoHideHUDConfig.contextualInfoBarOffsetX.get(), gy + AutoHideHUDConfig.contextualInfoBarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
            return new int[]{gx + AutoHideHUDConfig.selectedItemNameOffsetX.get(), gy + AutoHideHUDConfig.selectedItemNameOffsetY.get()};

        // Not "core HUD" — no global, only per-element
        if (layerName.equals(VanillaGuiLayers.EFFECTS))
            return new int[]{AutoHideHUDConfig.statusEffectsOffsetX.get(), AutoHideHUDConfig.statusEffectsOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CHAT))
            return new int[]{AutoHideHUDConfig.chatOffsetX.get(), AutoHideHUDConfig.chatOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CROSSHAIR))
            return new int[]{AutoHideHUDConfig.crosshairOffsetX.get(), AutoHideHUDConfig.crosshairOffsetY.get()};

        // additionalLayerIds get global only
        if (AutoHideHUDConfig.additionalLayerIds.get().contains(layerName.toString()))
            return new int[]{gx, gy};

        return NO_OFFSET;
    }

    // Returns the effective rotation (in degrees, 0-360) for a given HUD layer.
    // effectiveRotation = (globalRotation + perElementRotation) mod 360 for "core HUD" layers;
    // crosshair, chat, status effects get per-element only. Same grouping as getLayerOffset.
    private int getLayerRotation(ResourceLocation layerName) {
        int gr = AutoHideHUDConfig.globalRotation.get();

        if (layerName.equals(VanillaGuiLayers.HOTBAR))
            return (gr + AutoHideHUDConfig.hotbarRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layerName.toString().equals("appleskin:health_offset")
                || layerName.toString().equals("appleskin:health_restored"))
            return (gr + AutoHideHUDConfig.healthBarRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.ARMOR_LEVEL))
            return (gr + AutoHideHUDConfig.armorRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layerName.toString().equals("appleskin:hunger_restored")
                || layerName.toString().equals("appleskin:food_offset")
                || layerName.toString().equals("appleskin:saturation_level")
                || layerName.toString().equals("appleskin:exhaustion_level"))
            return (gr + AutoHideHUDConfig.foodRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.AIR_LEVEL))
            return (gr + AutoHideHUDConfig.airRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.VEHICLE_HEALTH))
            return (gr + AutoHideHUDConfig.vehicleHealthRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
            return (gr + AutoHideHUDConfig.experienceLevelRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR) || layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND))
            return (gr + AutoHideHUDConfig.contextualInfoBarRotation.get()) % 360;
        if (layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
            return (gr + AutoHideHUDConfig.selectedItemNameRotation.get()) % 360;

        // Not "core HUD" — no global, only per-element
        if (layerName.equals(VanillaGuiLayers.EFFECTS))
            return AutoHideHUDConfig.statusEffectsRotation.get() % 360;
        if (layerName.equals(VanillaGuiLayers.CHAT))
            return AutoHideHUDConfig.chatRotation.get() % 360;
        if (layerName.equals(VanillaGuiLayers.CROSSHAIR))
            return AutoHideHUDConfig.crosshairRotation.get() % 360;

        // additionalLayerIds get global only
        if (AutoHideHUDConfig.additionalLayerIds.get().contains(layerName.toString()))
            return gr % 360;

        return 0;
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
        if (AutoHideHUDConfig.hideSleepOverlay.get() && layerName.equals(VanillaGuiLayers.SUBTITLE_OVERLAY)) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;

            if (minecraft.screen != null && player != null && minecraft.screen.getTitle().getString().equals("Chat screen") && player.getBedOrientation() != null) {
                wasSleeping = true;
                return true;
            } else if (wasSleeping) {
                wasSleeping = false;
                return true;
            }

            return false;
        }
        if(AutoHideHUDConfig.additionalLayerIds.get().contains(layerName.toString())) return true;

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
        if (alpha >= 1f || inMenu || Minecraft.getInstance().screen != null) return color;

        // Extract ARGB components
        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        int newAlpha = (int) (alpha * (AutoHideHUD.alpha + (isText && alpha != 0f ? 0.005f : 0f)));

        // Recombine
        return (newAlpha << 24) | rgb;
    }

}

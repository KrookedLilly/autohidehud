package com.krookedlilly.autohidehud.common.model;

import java.util.ArrayList;

// Plain DTO serialized over the mod <-> companion TCP link via Gson.
// Lives in :common so the mod (producer) and the companion (consumer) share one source of truth
// for field names/types — eliminating the previous hand-mirrored definitions in each side.
public class PlayerData {
    public float health = 20.0f;
    public float maxHealth = 20.0f;
    public int armor = 0;
    public int foodLevel = 20;
    public int maxFoodLevel = 20;
    public float saturation = 20f;
    public boolean isCreative = false;
    public int experienceLevel = 0;
    public float experienceProgress = 0f;
    public int selectedHotbarSlot = 0;
    public int airSupply = 300;
    public int maxAirSupply = 300;
    public float vehicleHealth = .01f;
    public float maxVehicleHealth = .01f;
    public boolean inMenu = false;
    public double x = 0d;
    public double y = 0d;
    public double z = 0d;
    public float yaw = 0f;
    public float pitch = 0f;
    public String facing = "";
    public InventoryItemData[] inventoryData = new InventoryItemData[10];
    public ArrayList<StatusEffectData> statusEffects = new ArrayList<>();

    // server settings
    public int portNumber = 25922;
    public boolean showPosition = true;
    public boolean showFacing = true;
    public boolean showHealth = true;
    public int showHealthWarning = 30;
    public boolean showArmorLevel = true;
    public boolean showFoodLevel = true;
    public boolean showSaturationLevel = true;
    public boolean showVehicleHealth = true;
    public boolean showAirLevel = true;
    public boolean showExperienceLevel = true;
    public boolean showExperienceProgress = true;
    public boolean showStatusEffects = true;
    public boolean showHotbarItems = true;
    public boolean showHotbarItemsDurability = true;
    public boolean showHotbarItemsDurabilityPercent = true;
    public boolean showSelectedItemLabel = false;
    public String focusedBackgroundColor = "#000000";
    public int focusedBackgroundOpacity = 255;
    public String notFocusedBackgroundColor = "#000000";
    public int notFocusedBackgroundOpacity = 0;
}

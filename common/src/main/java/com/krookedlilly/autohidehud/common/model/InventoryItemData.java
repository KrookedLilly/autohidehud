package com.krookedlilly.autohidehud.common.model;

import java.util.Objects;

public class InventoryItemData {
    public String name = "";
    public String id = "";
    public int count = 0;
    public int slot = 0;
    public int damage = 0;
    public int maxDamage = 0;
    public int durability = 0;
    public boolean enchanted = false;
    public boolean selected = false;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        InventoryItemData other = (InventoryItemData) obj;
        return count == other.count
                && slot == other.slot
                && damage == other.damage
                && maxDamage == other.maxDamage
                && durability == other.durability
                && enchanted == other.enchanted
                && selected == other.selected
                && Objects.equals(name, other.name)
                && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + count;
        result = 31 * result + slot;
        result = 31 * result + damage;
        result = 31 * result + maxDamage;
        result = 31 * result + durability;
        result = 31 * result + (enchanted ? 1 : 0);
        result = 31 * result + (selected ? 1 : 0);
        return result;
    }
}

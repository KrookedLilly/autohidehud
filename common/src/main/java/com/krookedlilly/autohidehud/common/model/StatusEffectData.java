package com.krookedlilly.autohidehud.common.model;

import java.util.Objects;

public class StatusEffectData {
    public String name = "";
    public int amplifier = 0;
    public int duration = 0;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StatusEffectData other = (StatusEffectData) obj;
        return amplifier == other.amplifier
                && duration == other.duration
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + amplifier;
        result = 31 * result + duration;
        return result;
    }
}

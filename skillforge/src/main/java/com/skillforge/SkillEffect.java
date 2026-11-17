package com.skillforge;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** เอฟเฟกต์เสริมที่ติดไปกับเป้าหมาย */
public enum SkillEffect {
    NONE("none"),
    FIRE("fire"),
    SLOW("slow"),
    POISON("poison"),
    WITHER("wither"),
    KNOCKBACK("knockback");

    private final String id;

    SkillEffect(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public SkillEffect next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Optional<SkillEffect> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equalsIgnoreCase(id)).findFirst();
    }

    public static SkillEffect fromId(String id) {
        return byId(id).orElse(NONE);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(SkillEffect::id).toList();
    }
}

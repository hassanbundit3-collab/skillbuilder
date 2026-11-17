package com.skillforge;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** ประเภทของสกิล */
public enum SkillType {
    PROJECTILE("projectile"),
    DASH("dash"),
    AREA("area"),
    HEAL("heal");

    private final String id;

    SkillType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public SkillType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Optional<SkillType> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equalsIgnoreCase(id)).findFirst();
    }

    public static SkillType fromId(String id) {
        return byId(id).orElse(PROJECTILE);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(SkillType::id).toList();
    }
}

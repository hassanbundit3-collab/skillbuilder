package com.skillforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * สกิลหนึ่งอัน เก็บอยู่ในช่อง (slot) 1-4 ของผู้เล่น
 * ค่า damage ใช้เป็นจำนวนฮีลเมื่อ type = HEAL
 */
public record Skill(int slot, String name, SkillType type, float damage, float range,
                    int cooldownTicks, SkillEffect effect) {

    // ขีดจำกัดที่เซิร์ฟเวอร์บังคับใช้ (กันสกิลโกง) ปรับได้ตามต้องการ
    public static final int SLOTS = 4;
    public static final int MAX_NAME = 16;
    public static final float MAX_DAMAGE = 20f;
    public static final float MAX_RANGE = 40f;
    public static final int MIN_COOLDOWN_TICKS = 10;
    public static final float MAX_COOLDOWN_SECONDS = 60f;

    public static final Codec<Skill> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(Skill::slot),
            Codec.STRING.fieldOf("name").forGetter(Skill::name),
            Codec.STRING.xmap(SkillType::fromId, SkillType::id).fieldOf("type").forGetter(Skill::type),
            Codec.FLOAT.fieldOf("damage").forGetter(Skill::damage),
            Codec.FLOAT.fieldOf("range").forGetter(Skill::range),
            Codec.INT.fieldOf("cooldown").forGetter(Skill::cooldownTicks),
            Codec.STRING.xmap(SkillEffect::fromId, SkillEffect::id).fieldOf("effect").forGetter(Skill::effect)
    ).apply(i, Skill::new));
}

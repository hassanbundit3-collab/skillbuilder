package com.skillforge;

import java.util.ArrayList;
import java.util.List;

/** ชนิดของบล็อกบนไทม์ไลน์ พร้อมนิยามช่องค่าที่ปรับได้ (ใช้ร่วมกันทั้ง client และ server) */
public enum BlockType {
    WAIT("wait", "รอ",
            num("เวลา (tick)", 0, 200, 5, 10)),
    PARTICLE("particle", "อนุภาค",
            pick("ชนิด", 9, 0), num("จำนวน", 1, 100, 5, 20), num("กระจาย", 0, 4, 0.25f, 0.5f), num("ระยะหน้า", 0, 30, 1, 3)),
    HITBOX("hitbox", "ฮิตบ็อกซ์",
            num("ขนาด", 0.5f, 8, 0.5f, 2), num("ระยะหน้า", 0, 10, 0.5f, 2), num("ดาเมจ", 0, 20, 1, 5), num("แรงผลัก", 0, 4, 0.25f, 1)),
    PROJECTILE("projectile", "กระสุน",
            num("ความเร็ว", 0.2f, 3, 0.1f, 1), num("ระยะ", 5, 60, 5, 30), num("ดาเมจ", 0, 20, 1, 6),
            num("แรงผลัก", 0, 4, 0.25f, 1), pick("อนุภาค", 9, 0)),
    VELOCITY("velocity", "พุ่งตัว",
            num("แรงพุ่ง", 0, 4, 0.2f, 1.5f), num("แรงยก", -1, 2, 0.2f, 0.2f)),
    TELEPORT("teleport", "วาร์ป",
            num("ระยะ", 1, 30, 1, 8)),
    SOUND("sound", "เสียง",
            sound("เสียง", 2, 0), num("ความดัง", 0.1f, 2, 0.1f, 1), num("โทน", 0.5f, 2, 0.1f, 1));

    public static final int KIND_NUMBER = 0;
    public static final int KIND_PARTICLE = 1;
    public static final int KIND_SOUND = 2;

    /** ลำดับต้องตรงกับ PARTICLES ใน SkillCaster */
    public static final String[] PARTICLE_NAMES = {
            "ไฟ", "ไฟวิญญาณ", "สายฟ้า", "แสง", "คริติคอล", "หิมะ", "เวทมนตร์", "ควัน", "เมฆ", "หัวใจ"};
    /** ลำดับต้องตรงกับ SOUNDS ใน SkillCaster */
    public static final String[] SOUND_NAMES = {"ยิงไฟ", "ระเบิด", "เทเลพอร์ต"};

    public record Param(String label, float min, float max, float step, float def, int kind) {
    }

    public final String id;
    public final String label;
    public final Param[] params;

    BlockType(String id, String label, Param... params) {
        this.id = id;
        this.label = label;
        this.params = params;
    }

    private static Param num(String label, float min, float max, float step, float def) {
        return new Param(label, min, max, step, def, KIND_NUMBER);
    }

    private static Param pick(String label, int max, int def) {
        return new Param(label, 0, max, 1, def, KIND_PARTICLE);
    }

    private static Param sound(String label, int max, int def) {
        return new Param(label, 0, max, 1, def, KIND_SOUND);
    }

    public float clamp(int i, float v) {
        if (i >= params.length) return v;
        Param p = params[i];
        if (Float.isNaN(v) || Float.isInfinite(v)) v = p.def();
        v = Math.max(p.min(), Math.min(p.max(), v));
        if (p.kind() != KIND_NUMBER) v = Math.round(v);
        return v;
    }

    public Skill.Block defaultBlock() {
        List<Float> l = new ArrayList<>();
        for (Param p : params) l.add(p.def());
        return new Skill.Block(id, l);
    }

    public static BlockType byId(String id) {
        for (BlockType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}

package com.skillforge;

import java.util.ArrayList;
import java.util.List;

/**
 * ชนิดของบล็อกบนไทม์ไลน์ พร้อมนิยามช่องค่าที่พิมพ์ได้ (ใช้ร่วมกันทั้ง client และ server)
 *
 * ตำแหน่ง "หน้า/ขวา/สูง" นับจากระดับตาของผู้เล่น (ค่าลบ = ถอยหลัง/ซ้าย/ต่ำ)
 * บล็อกที่มี เริ่ม→จบ จะเคลื่อนจากตำแหน่งเริ่มไปตำแหน่งจบตลอดช่วง "ใช้เวลา" (0 = อยู่ที่ตำแหน่งเริ่มครั้งเดียว)
 * "ตามตัว" 1 = ตำแหน่งขยับตามผู้เล่นตลอด, 0 = ล็อกตำแหน่งตอนบล็อกเริ่มทำงาน
 */
public enum BlockType {
    WAIT("wait", "รอ",
            num("เวลา (tick)", 0, 400, 10)),
    PARTICLE("particle", "อนุภาค",
            pick("ชนิด", 9, 0), num("จำนวน/tick", 1, 100, 20), num("กระจาย", 0, 5, 0.5f),
            num("เริ่ม หน้า", -60, 60, 3), num("เริ่ม ขวา", -60, 60, 0), num("เริ่ม สูง", -60, 60, 0),
            num("จบ หน้า", -60, 60, 3), num("จบ ขวา", -60, 60, 0), num("จบ สูง", -60, 60, 0),
            num("ใช้เวลา (tick)", 0, 200, 0), num("ตามตัว 0/1", 0, 1, 0)),
    HITBOX("hitbox", "ฮิตบ็อกซ์",
            num("กว้าง", 0.1f, 20, 2), num("สูง", 0.1f, 20, 2), num("ยาว", 0.1f, 20, 2),
            num("ดาเมจ", 0, 20, 5), num("แรงผลัก", 0, 4, 1),
            num("เริ่ม หน้า", -60, 60, 2), num("เริ่ม ขวา", -60, 60, 0), num("เริ่ม สูง", -60, 60, 0),
            num("จบ หน้า", -60, 60, 2), num("จบ ขวา", -60, 60, 0), num("จบ สูง", -60, 60, 0),
            num("ใช้เวลา (tick)", 0, 200, 0), num("ตามตัว 0/1", 0, 1, 1)),
    PROJECTILE("projectile", "กระสุน",
            num("ความเร็ว", 0.1f, 4, 1), num("ระยะ", 1, 80, 30), num("ดาเมจ", 0, 20, 6),
            num("แรงผลัก", 0, 4, 1), pick("อนุภาค", 9, 0), num("รัศมี", 0.1f, 5, 0.6f),
            num("เริ่ม หน้า", -60, 60, 0), num("เริ่ม ขวา", -60, 60, 0), num("เริ่ม สูง", -60, 60, 0)),
    VELOCITY("velocity", "พุ่งตัว",
            num("แรงพุ่ง", 0, 4, 1.5f), num("แรงยก", -1, 2, 0.2f)),
    TELEPORT("teleport", "วาร์ป",
            num("ระยะ", 1, 30, 8)),
    SOUND("sound", "เสียง",
            sound("เสียง", 2, 0), num("ความดัง", 0.1f, 2, 1), num("โทน", 0.5f, 2, 1));

    public static final int KIND_NUMBER = 0;
    public static final int KIND_PARTICLE = 1;
    public static final int KIND_SOUND = 2;

    /** ลำดับต้องตรงกับ PARTICLES ใน SkillCaster */
    public static final String[] PARTICLE_NAMES = {
            "ไฟ", "ไฟวิญญาณ", "สายฟ้า", "แสง", "คริติคอล", "หิมะ", "เวทมนตร์", "ควัน", "เมฆ", "หัวใจ"};
    /** ลำดับต้องตรงกับ SOUNDS ใน SkillCaster */
    public static final String[] SOUND_NAMES = {"ยิงไฟ", "ระเบิด", "เทเลพอร์ต"};

    public record Param(String label, float min, float max, float def, int kind) {
    }

    public final String id;
    public final String label;
    public final Param[] params;

    BlockType(String id, String label, Param... params) {
        this.id = id;
        this.label = label;
        this.params = params;
    }

    private static Param num(String label, float min, float max, float def) {
        return new Param(label, min, max, def, KIND_NUMBER);
    }

    private static Param pick(String label, int max, int def) {
        return new Param(label, 0, max, def, KIND_PARTICLE);
    }

    private static Param sound(String label, int max, int def) {
        return new Param(label, 0, max, def, KIND_SOUND);
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

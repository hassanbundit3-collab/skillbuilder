package com.skillforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * สกิลหนึ่งอัน = ชื่อ + คูลดาวน์ + ลิสต์ของบล็อกที่รันเรียงกันบนไทม์ไลน์
 * เก็บอยู่ในช่อง (slot) 1-4 ของผู้เล่น
 */
public record Skill(int slot, String name, int cooldownTicks, List<Block> blocks) {

    public static final int SLOTS = 4;
    public static final int MAX_NAME = 16;
    public static final int MAX_BLOCKS = 30;
    public static final int MIN_COOLDOWN_TICKS = 10;
    public static final int MAX_COOLDOWN_TICKS = 1200;

    /** บล็อกหนึ่งอัน: ชนิด + ค่าตัวเลขตามลำดับช่องใน BlockType.params */
    public record Block(String type, List<Float> v) {
        public static final Codec<Block> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Block::type),
                Codec.FLOAT.listOf().fieldOf("v").forGetter(Block::v)
        ).apply(i, Block::new));

        public float get(int idx) {
            return idx < v.size() ? v.get(idx) : 0f;
        }
    }

    public static final Codec<Skill> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(Skill::slot),
            Codec.STRING.fieldOf("name").forGetter(Skill::name),
            Codec.INT.fieldOf("cooldown").forGetter(Skill::cooldownTicks),
            Block.CODEC.listOf().fieldOf("blocks").forGetter(Skill::blocks)
    ).apply(i, Skill::new));

    /** แปลงเป็นข้อความสั้นๆ เพื่อส่งไปเซิร์ฟเวอร์ผ่านคำสั่ง /skill upload */
    public static String encode(String name, int cooldownTicks, List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('|').append(cooldownTicks).append('|');
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(';');
            Block b = blocks.get(i);
            sb.append(b.type());
            for (float f : b.v()) sb.append(':').append(f);
        }
        return sb.toString();
    }

    /** เซิร์ฟเวอร์ใช้แปลงข้อความกลับ พร้อมบังคับค่าให้อยู่ในขีดจำกัด (กันสกิลโกง) */
    public static Skill decode(int slot, String s) {
        String[] top = s.split("\\|", 3);
        if (top.length < 3) return null;

        String name = top[0].trim();
        if (name.isEmpty()) name = "Skill" + slot;
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);

        int cd;
        try {
            cd = Integer.parseInt(top[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        cd = Math.max(MIN_COOLDOWN_TICKS, Math.min(MAX_COOLDOWN_TICKS, cd));

        List<Block> blocks = new ArrayList<>();
        if (!top[2].isEmpty()) {
            for (String part : top[2].split(";")) {
                if (blocks.size() >= MAX_BLOCKS) break;
                String[] f = part.split(":");
                BlockType t = BlockType.byId(f[0]);
                if (t == null) continue;
                List<Float> vals = new ArrayList<>();
                for (int i = 0; i < t.params.length; i++) {
                    float v = t.params[i].def();
                    if (i + 1 < f.length) {
                        try {
                            v = Float.parseFloat(f[i + 1]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    vals.add(t.clamp(i, v));
                }
                blocks.add(new Block(t.id, List.copyOf(vals)));
            }
        }
        return new Skill(slot, name, cd, List.copyOf(blocks));
    }
}

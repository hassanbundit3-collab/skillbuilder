package com.skillforge;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * เก็บสกิลของผู้เล่นด้วย Fabric Data Attachment
 * - persistent: บันทึกลงไฟล์ผู้เล่น ไม่หายตอนออกเกม
 * - copyOnDeath: ตายแล้วสกิลยังอยู่
 * - syncWith: ส่งไปให้ client ของเจ้าของ เพื่อให้เมนูสร้างสกิลแสดงข้อมูลได้
 * (ใช้ id "skills_v3" เพราะรูปแบบข้อมูลสกิลเปลี่ยนจากเวอร์ชันแรก)
 */
public final class SkillAttachment {
    public static final AttachmentType<List<Skill>> SKILLS = AttachmentRegistry.<List<Skill>>create(
            Identifier.fromNamespaceAndPath(SkillForge.MOD_ID, "skills_v3"),
            builder -> builder
                    .initializer(List::of)
                    .persistent(Skill.CODEC.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.fromCodec(Skill.CODEC.listOf()), AttachmentSyncPredicate.targetOnly())
    );

    private SkillAttachment() {
    }

    /** เรียกเพื่อให้ class ถูกโหลดและ register attachment */
    public static void init() {
    }

    public static List<Skill> get(Entity entity) {
        return entity.getAttachedOrElse(SKILLS, List.of());
    }

    public static Skill find(Entity entity, int slot) {
        for (Skill s : get(entity)) {
            if (s.slot() == slot) return s;
        }
        return null;
    }

    public static void put(Entity entity, Skill skill) {
        List<Skill> list = new ArrayList<>(get(entity));
        list.removeIf(s -> s.slot() == skill.slot());
        list.add(skill);
        list.sort(Comparator.comparingInt(Skill::slot));
        entity.setAttached(SKILLS, List.copyOf(list));
    }

    public static boolean remove(Entity entity, int slot) {
        List<Skill> list = new ArrayList<>(get(entity));
        boolean removed = list.removeIf(s -> s.slot() == slot);
        if (removed) entity.setAttached(SKILLS, List.copyOf(list));
        return removed;
    }
}

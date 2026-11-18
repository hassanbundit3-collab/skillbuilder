package com.skillforge.client;

import com.skillforge.Skill;
import com.skillforge.SkillAttachment;
import com.skillforge.SkillEffect;
import com.skillforge.SkillType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * เมนูสร้างสกิล (ดีไซน์ใหม่)
 * - แถบเลือกช่อง 4 ปุ่ม แสดงปุ่มลัดและชื่อสกิลที่มีอยู่ กดแล้วโหลดสกิลนั้นมาแก้ได้
 * - เลือกประเภท/เอฟเฟกต์ด้วยปุ่มเรียงกัน ตัวที่เลือกอยู่เป็นสีเหลือง
 * - ปรับค่าด้วยปุ่ม - และ +
 * ใช้เฉพาะ widget สำเร็จรูปของเกม (ไม่วาดเอง) เพื่อลดผลกระทบจากการเปลี่ยน API ด้าน rendering
 */
public class SkillBuilderScreen extends Screen {
    private static final float[] DAMAGES = {2, 4, 6, 8, 10, 14, 20};
    private static final float[] RANGES = {4, 8, 12, 20, 30, 40};
    private static final float[] COOLDOWNS = {0.5f, 1, 2, 3, 5, 8, 12, 20, 30};
    private static final String[] KEYS = {"Z", "X", "C", "V"};
    private static final int W = 316;

    private int slot = 1;
    private int dmgIdx = 2;
    private int rangeIdx = 2;
    private int cdIdx = 3;
    private SkillType type = SkillType.PROJECTILE;
    private SkillEffect effect = SkillEffect.NONE;
    private boolean loaded = false;

    private EditBox nameBox;
    private final Button[] slotBtns = new Button[Skill.SLOTS];
    private final Button[] typeBtns = new Button[SkillType.values().length];
    private final Button[] effectBtns = new Button[SkillEffect.values().length];
    private StringWidget dmgLabel;
    private StringWidget rangeLabel;
    private StringWidget cdLabel;
    private StringWidget summary;

    public SkillBuilderScreen() {
        super(Component.translatable("screen.skillforge.builder"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x0 = cx - W / 2;
        int top = Math.max(4, (this.height - 238) / 2);
        String keep = nameBox != null ? nameBox.getValue() : null;

        addRenderableWidget(centered(cx, top, this.title.copy().withStyle(ChatFormatting.GOLD)));

        // แถบช่อง 1-4
        int slotW = (W - 12) / 4;
        for (int i = 0; i < Skill.SLOTS; i++) {
            final int idx = i + 1;
            slotBtns[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                loadSlot(idx);
                refresh();
            }).bounds(x0 + i * (slotW + 4), top + 14, slotW, 20).build());
        }

        // ชื่อสกิล
        nameBox = addRenderableWidget(new EditBox(this.font, x0, top + 38, W, 20,
                Component.translatable("screen.skillforge.name_hint")));
        nameBox.setMaxLength(Skill.MAX_NAME);
        nameBox.setHint(Component.translatable("screen.skillforge.name_hint"));

        // ประเภทสกิล
        SkillType[] types = SkillType.values();
        int typeW = (W - 4 * (types.length - 1)) / types.length;
        for (int i = 0; i < types.length; i++) {
            final SkillType t = types[i];
            typeBtns[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                type = t;
                refresh();
            }).bounds(x0 + i * (typeW + 4), top + 62, typeW, 20).build());
        }

        // เอฟเฟกต์ (3 ปุ่มต่อแถว)
        SkillEffect[] effects = SkillEffect.values();
        int effW = (W - 8) / 3;
        for (int i = 0; i < effects.length; i++) {
            final SkillEffect e = effects[i];
            int col = i % 3;
            int row = i / 3;
            effectBtns[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                effect = e;
                refresh();
            }).bounds(x0 + col * (effW + 4), top + 86 + row * 22, effW, 20).build());
        }

        // ค่าตัวเลข
        dmgLabel = addStat(x0, top + 134,
                () -> dmgIdx = Math.max(0, dmgIdx - 1),
                () -> dmgIdx = Math.min(DAMAGES.length - 1, dmgIdx + 1));
        rangeLabel = addStat(x0, top + 156,
                () -> rangeIdx = Math.max(0, rangeIdx - 1),
                () -> rangeIdx = Math.min(RANGES.length - 1, rangeIdx + 1));
        cdLabel = addStat(x0, top + 178,
                () -> cdIdx = Math.max(0, cdIdx - 1),
                () -> cdIdx = Math.min(COOLDOWNS.length - 1, cdIdx + 1));

        summary = addRenderableWidget(new StringWidget(x0, top + 204, W, 12, Component.empty(), this.font));

        // ปุ่มล่าง
        int bw = (W - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.save"), b -> save())
                .bounds(x0, top + 218, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.delete"), b -> {
            send("skill delete " + slot);
            onClose();
        }).bounds(x0 + bw + 4, top + 218, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.close"), b -> onClose())
                .bounds(x0 + 2 * (bw + 4), top + 218, bw, 20).build());

        if (!loaded) {
            loaded = true;
            loadSlot(slot);
        } else if (keep != null) {
            nameBox.setValue(keep);
        }
        refresh();
    }

    /** แถวปรับค่า: ข้อความด้านซ้าย ปุ่ม - และ + ด้านขวา */
    private StringWidget addStat(int x0, int y, Runnable dec, Runnable inc) {
        StringWidget label = addRenderableWidget(new StringWidget(x0, y + 6, 220, 12, Component.empty(), this.font));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            dec.run();
            refresh();
        }).bounds(x0 + 228, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            inc.run();
            refresh();
        }).bounds(x0 + 272, y, 40, 20).build());
        return label;
    }

    /** ข้อความกึ่งกลาง (คำนวณตำแหน่งเอง เพราะ StringWidget.alignCenter ถูกเอาออกใน 26.x) */
    private StringWidget centered(int centerX, int y, Component text) {
        int w = this.font.width(text);
        return new StringWidget(centerX - w / 2, y, w, 12, text, this.font);
    }

    private void loadSlot(int s) {
        slot = s;
        Skill sk = skillAt(slot);
        String name = "";
        if (sk != null) {
            type = sk.type();
            effect = sk.effect();
            dmgIdx = nearest(DAMAGES, sk.damage());
            rangeIdx = nearest(RANGES, sk.range());
            cdIdx = nearest(COOLDOWNS, sk.cooldownTicks() / 20f);
            name = sk.name();
        } else {
            type = SkillType.PROJECTILE;
            effect = SkillEffect.NONE;
            dmgIdx = 2;
            rangeIdx = 2;
            cdIdx = 3;
        }
        if (nameBox != null) nameBox.setValue(name);
    }

    private void refresh() {
        for (int i = 0; i < Skill.SLOTS; i++) {
            Skill s = skillAt(i + 1);
            String text = "[" + KEYS[i] + "] " + (s == null ? "-" : shorten(s.name(), 6));
            MutableComponent c = Component.literal(text);
            if (i + 1 == slot) c.withStyle(ChatFormatting.YELLOW);
            slotBtns[i].setMessage(c);
        }
        SkillType[] types = SkillType.values();
        for (int i = 0; i < types.length; i++) {
            MutableComponent c = Component.translatable("skillforge.type." + types[i].id());
            if (types[i] == type) c.withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD);
            typeBtns[i].setMessage(c);
        }
        SkillEffect[] effects = SkillEffect.values();
        for (int i = 0; i < effects.length; i++) {
            MutableComponent c = Component.translatable("skillforge.effect." + effects[i].id());
            if (effects[i] == effect) c.withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD);
            effectBtns[i].setMessage(c);
        }
        dmgLabel.setMessage(Component.translatable("screen.skillforge.damage", fmt(DAMAGES[dmgIdx])));
        rangeLabel.setMessage(Component.translatable("screen.skillforge.range", fmt(RANGES[rangeIdx])));
        cdLabel.setMessage(Component.translatable("screen.skillforge.cooldown", fmt(COOLDOWNS[cdIdx])));

        MutableComponent sum = Component.empty()
                .append(Component.translatable("skillforge.type." + type.id()))
                .append(Component.literal("  +  "))
                .append(Component.translatable("skillforge.effect." + effect.id()));
        summary.setMessage(sum.withStyle(ChatFormatting.AQUA));
    }

    private void save() {
        String name = nameBox.getValue().trim().replaceAll("[\"\\\\]", "");
        if (name.isEmpty()) name = "Skill" + slot;
        send("skill create " + slot + " \"" + name + "\" " + type.id() + " "
                + DAMAGES[dmgIdx] + " " + RANGES[rangeIdx] + " " + COOLDOWNS[cdIdx] + " " + effect.id());
        onClose();
    }

    private static Skill skillAt(int slot) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : SkillAttachment.find(mc.player, slot);
    }

    private static int nearest(float[] arr, float v) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) {
            if (Math.abs(arr[i] - v) < Math.abs(arr[best] - v)) best = i;
        }
        return best;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "..";
    }

    private static void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static String fmt(float f) {
        return f == (int) f ? Integer.toString((int) f) : Float.toString(f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.skillforge.client;

import com.skillforge.Skill;
import com.skillforge.SkillAttachment;
import com.skillforge.SkillEffect;
import com.skillforge.SkillType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * เมนูสร้างสกิล: เลือกช่อง ตั้งชื่อ เลือกประเภท/เอฟเฟกต์ และค่าตัวเลข แล้วกดบันทึก
 * ใช้เฉพาะ widget สำเร็จรูปของเกม (ไม่วาดเอง) เพื่อลดผลกระทบจากการเปลี่ยน API ด้าน rendering
 */
public class SkillBuilderScreen extends Screen {
    private static final float[] DAMAGES = {2, 4, 6, 8, 10, 14, 20};
    private static final float[] RANGES = {4, 8, 12, 20, 30, 40};
    private static final float[] COOLDOWNS = {0.5f, 1, 2, 3, 5, 8, 12, 20, 30};
    private static final String[] KEYS = {"Z", "X", "C", "V"};

    private int slot = 1;
    private int dmgIdx = 2;
    private int rangeIdx = 2;
    private int cdIdx = 3;
    private SkillType type = SkillType.PROJECTILE;
    private SkillEffect effect = SkillEffect.NONE;

    private EditBox nameBox;
    private Button slotBtn;
    private Button typeBtn;
    private Button effectBtn;
    private Button dmgBtn;
    private Button rangeBtn;
    private Button cdBtn;

    public SkillBuilderScreen() {
        super(Component.translatable("screen.skillforge.builder"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x1 = cx - 155;
        int x2 = cx + 5;
        int w = 150;

        addRenderableWidget(new StringWidget(cx - 100, 14, 200, 12, this.title, this.font).alignCenter());

        slotBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            slot = slot % Skill.SLOTS + 1;
            refresh();
        }).bounds(x1, 40, w, 20).build());

        nameBox = addRenderableWidget(new EditBox(this.font, x2, 40, w, 20,
                Component.translatable("screen.skillforge.name_hint")));
        nameBox.setMaxLength(Skill.MAX_NAME);
        nameBox.setHint(Component.translatable("screen.skillforge.name_hint"));

        typeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            type = type.next();
            refresh();
        }).bounds(x1, 66, w, 20).build());

        effectBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            effect = effect.next();
            refresh();
        }).bounds(x2, 66, w, 20).build());

        dmgBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            dmgIdx = (dmgIdx + 1) % DAMAGES.length;
            refresh();
        }).bounds(x1, 92, w, 20).build());

        rangeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            rangeIdx = (rangeIdx + 1) % RANGES.length;
            refresh();
        }).bounds(x2, 92, w, 20).build());

        cdBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            cdIdx = (cdIdx + 1) % COOLDOWNS.length;
            refresh();
        }).bounds(x1, 118, w, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.delete"), b -> {
            send("skill delete " + slot);
            onClose();
        }).bounds(x2, 118, w, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.save"), b -> save())
                .bounds(x1, 150, w, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.skillforge.close"), b -> onClose())
                .bounds(x2, 150, w, 20).build());

        // แสดงสกิลที่มีอยู่ในแต่ละช่อง
        for (int i = 0; i < Skill.SLOTS; i++) {
            Skill s = Minecraft.getInstance().player == null ? null
                    : SkillAttachment.find(Minecraft.getInstance().player, i + 1);
            Component line = s == null
                    ? Component.translatable("screen.skillforge.slot_empty", KEYS[i])
                    : Component.translatable("screen.skillforge.slot_line", KEYS[i], s.name(),
                    Component.translatable("skillforge.type." + s.type().id()));
            addRenderableWidget(new StringWidget(cx - 155, 184 + i * 13, 310, 12, line, this.font).alignCenter());
        }

        refresh();
    }

    private void refresh() {
        slotBtn.setMessage(Component.translatable("screen.skillforge.slot", slot));
        typeBtn.setMessage(Component.translatable("screen.skillforge.type",
                Component.translatable("skillforge.type." + type.id())));
        effectBtn.setMessage(Component.translatable("screen.skillforge.effect",
                Component.translatable("skillforge.effect." + effect.id())));
        dmgBtn.setMessage(Component.translatable("screen.skillforge.damage", fmt(DAMAGES[dmgIdx])));
        rangeBtn.setMessage(Component.translatable("screen.skillforge.range", fmt(RANGES[rangeIdx])));
        cdBtn.setMessage(Component.translatable("screen.skillforge.cooldown", fmt(COOLDOWNS[cdIdx])));
    }

    private void save() {
        String name = nameBox.getValue().trim().replaceAll("[\"\\\\]", "");
        if (name.isEmpty()) name = "Skill" + slot;
        send("skill create " + slot + " \"" + name + "\" " + type.id() + " "
                + DAMAGES[dmgIdx] + " " + RANGES[rangeIdx] + " " + COOLDOWNS[cdIdx] + " " + effect.id());
        onClose();
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

package com.skillforge.client;

import com.skillforge.BlockType;
import com.skillforge.Skill;
import com.skillforge.SkillAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * ตัวสร้างสกิลแบบไทม์ไลน์ (สไตล์ JJS): ซ้ายคือลิสต์บล็อกเรียงกัน ขวาคือค่าของบล็อกที่เลือก
 * แถวล่างคือปุ่มเพิ่มบล็อก ไม่ต้องเขียนโค้ด
 * ใช้เฉพาะ widget สำเร็จรูปของเกม (ไม่วาดเอง) เพื่อลดผลกระทบจากการเปลี่ยน API ด้าน rendering
 */
public class SkillBuilderScreen extends Screen {
    private static final float[] COOLDOWNS = {0.5f, 1, 2, 3, 5, 8, 12, 20, 30, 60};
    private static final String[] KEYS = {"Z", "X", "C", "V"};
    private static final int W = 420;
    private static final int PAGE = 6;
    private static final int CHUNK = 120;

    private int slot = 1;
    private String name = "";
    private int cdIdx = 4;
    private final List<Skill.Block> blocks = new ArrayList<>();
    private int selected = -1;
    private int page = 0;
    private boolean loaded = false;
    private EditBox nameBox;

    public SkillBuilderScreen() {
        super(Component.translatable("screen.skillforge.builder"));
    }

    @Override
    protected void init() {
        if (nameBox != null) name = nameBox.getValue();
        if (!loaded) {
            loaded = true;
            loadSlot(slot);
        }
        int cx = this.width / 2;
        int x0 = cx - W / 2;
        int x1 = x0 + 212;
        int top = Math.max(4, (this.height - 240) / 2);

        addRenderableWidget(centered(cx, top, this.title.copy().withStyle(ChatFormatting.GOLD)));

        // ช่องสกิล 1-4
        int sw = (W - 12) / 4;
        for (int i = 0; i < Skill.SLOTS; i++) {
            final int idx = i + 1;
            Skill s = skillAt(idx);
            MutableComponent c = Component.literal("[" + KEYS[i] + "] " + (s == null ? "-" : shorten(s.name(), 8)));
            if (idx == slot) c.withStyle(ChatFormatting.YELLOW);
            btn(c, x0 + i * (sw + 4), top + 12, sw, () -> {
                loadSlot(idx);
                rebuildWidgets();
            });
        }

        // ชื่อ + คูลดาวน์
        nameBox = addRenderableWidget(new EditBox(this.font, x0, top + 32, 240, 18,
                Component.translatable("screen.skillforge.name_hint")));
        nameBox.setMaxLength(Skill.MAX_NAME);
        nameBox.setHint(Component.translatable("screen.skillforge.name_hint"));
        nameBox.setValue(name);

        addRenderableWidget(new StringWidget(x0 + 248, top + 37, 82, 12,
                Component.literal("คูลดาวน์ " + fmt(COOLDOWNS[cdIdx]) + "s"), this.font));
        btn(Component.literal("-"), x0 + 332, top + 32, 40, () -> {
            cdIdx = Math.max(0, cdIdx - 1);
            rebuildWidgets();
        });
        btn(Component.literal("+"), x0 + 376, top + 32, 40, () -> {
            cdIdx = Math.min(COOLDOWNS.length - 1, cdIdx + 1);
            rebuildWidgets();
        });

        // หัวข้อ + ปุ่มเปลี่ยนหน้า
        int maxPage = Math.max(0, (blocks.size() - 1) / PAGE);
        page = Math.min(page, maxPage);
        addRenderableWidget(new StringWidget(x0, top + 55, 150, 12,
                Component.literal("ไทม์ไลน์ (" + blocks.size() + "/" + Skill.MAX_BLOCKS + ")")
                        .withStyle(ChatFormatting.AQUA), this.font));
        addRenderableWidget(Button.builder(Component.literal("^"), b -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(x0 + 158, top + 52, 20, 14).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        }).bounds(x0 + 180, top + 52, 20, 14).build());

        // ลิสต์บล็อก (ซ้าย)
        for (int i = 0; i < PAGE; i++) {
            final int bi = page * PAGE + i;
            if (bi >= blocks.size()) break;
            int y = top + 66 + i * 20;
            BlockType t = BlockType.byId(blocks.get(bi).type());
            MutableComponent label = Component.literal("#" + (bi + 1) + " " + (t == null ? "?" : t.label));
            if (bi == selected) label.withStyle(ChatFormatting.YELLOW);
            btn(label, x0, y, 136, () -> {
                selected = bi;
                rebuildWidgets();
            });
            btn(Component.literal("^"), x0 + 138, y, 20, () -> {
                if (bi > 0) {
                    java.util.Collections.swap(blocks, bi, bi - 1);
                    if (selected == bi) selected = bi - 1;
                    rebuildWidgets();
                }
            });
            btn(Component.literal("v"), x0 + 160, y, 20, () -> {
                if (bi < blocks.size() - 1) {
                    java.util.Collections.swap(blocks, bi, bi + 1);
                    if (selected == bi) selected = bi + 1;
                    rebuildWidgets();
                }
            });
            btn(Component.literal("x"), x0 + 182, y, 18, () -> {
                blocks.remove(bi);
                selected = -1;
                rebuildWidgets();
            });
        }

        // ค่าของบล็อกที่เลือก (ขวา)
        addRenderableWidget(new StringWidget(x1, top + 55, 150, 12,
                Component.literal("ค่าของบล็อก").withStyle(ChatFormatting.AQUA), this.font));
        if (selected >= 0 && selected < blocks.size()) {
            Skill.Block b = blocks.get(selected);
            BlockType t = BlockType.byId(b.type());
            if (t != null) {
                for (int pi = 0; pi < t.params.length; pi++) {
                    final int p = pi;
                    BlockType.Param param = t.params[pi];
                    int y = top + 66 + pi * 20;
                    addRenderableWidget(new StringWidget(x1, y + 5, 130, 12,
                            Component.literal(param.label() + ": " + valueText(param, b.get(pi))), this.font));
                    btn(Component.literal("-"), x1 + 132, y, 36, () -> adjust(t, p, -1));
                    btn(Component.literal("+"), x1 + 172, y, 36, () -> adjust(t, p, +1));
                }
            }
        } else {
            addRenderableWidget(new StringWidget(x1, top + 70, 208, 12,
                    Component.literal("เลือกบล็อกทางซ้ายเพื่อแก้ค่า").withStyle(ChatFormatting.GRAY), this.font));
        }

        // แถบเพิ่มบล็อก
        addRenderableWidget(new StringWidget(x0, top + 189, 150, 12,
                Component.literal("เพิ่มบล็อก:").withStyle(ChatFormatting.AQUA), this.font));
        BlockType[] types = BlockType.values();
        int pw = (W - 4 * (types.length - 1)) / types.length;
        for (int i = 0; i < types.length; i++) {
            final BlockType t = types[i];
            btn(Component.literal(t.label), x0 + i * (pw + 4), top + 201, pw, () -> {
                if (blocks.size() < Skill.MAX_BLOCKS) {
                    blocks.add(t.defaultBlock());
                    selected = blocks.size() - 1;
                    page = selected / PAGE;
                    rebuildWidgets();
                }
            });
        }

        // ปุ่มล่าง
        int bw = (W - 8) / 3;
        btn(Component.translatable("screen.skillforge.save"), x0, top + 222, bw, this::save);
        btn(Component.translatable("screen.skillforge.delete"), x0 + bw + 4, top + 222, bw, () -> {
            send("skill delete " + slot);
            onClose();
        });
        btn(Component.translatable("screen.skillforge.close"), x0 + 2 * (bw + 4), top + 222, bw, this::onClose);
    }

    private Button btn(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 18).build());
    }

    /** ข้อความกึ่งกลาง (คำนวณตำแหน่งเอง เพราะ StringWidget.alignCenter ถูกเอาออกใน 26.x) */
    private StringWidget centered(int centerX, int y, Component text) {
        int w = this.font.width(text);
        return new StringWidget(centerX - w / 2, y, w, 12, text, this.font);
    }

    private void adjust(BlockType t, int pi, int dir) {
        if (selected < 0 || selected >= blocks.size()) return;
        Skill.Block b = blocks.get(selected);
        BlockType.Param p = t.params[pi];
        List<Float> nv = new ArrayList<>(b.v());
        while (nv.size() <= pi) nv.add(p.def());
        float v = nv.get(pi) + dir * p.step();
        v = Math.round(v * 100f) / 100f;
        nv.set(pi, t.clamp(pi, v));
        blocks.set(selected, new Skill.Block(b.type(), nv));
        rebuildWidgets();
    }

    private void loadSlot(int s) {
        slot = s;
        selected = -1;
        page = 0;
        blocks.clear();
        Skill sk = skillAt(slot);
        if (sk != null) {
            name = sk.name();
            blocks.addAll(sk.blocks());
            cdIdx = nearest(COOLDOWNS, sk.cooldownTicks() / 20f);
        } else {
            name = "";
            cdIdx = 4;
        }
        nameBox = null; // ให้ init() ใช้ค่า name ที่เพิ่งโหลด ไม่ใช่ข้อความเก่าในกล่อง
    }

    private void save() {
        String clean = nameBox.getValue().trim().replaceAll("[|;:\"\\\\]", "").replace(' ', '_');
        if (clean.isEmpty()) clean = "Skill" + slot;
        int cd = Math.round(COOLDOWNS[cdIdx] * 20f);
        String data = Skill.encode(clean, cd, blocks);

        int total = (data.length() + CHUNK - 1) / CHUNK;
        for (int i = 0; i < total; i++) {
            String chunk = data.substring(i * CHUNK, Math.min(data.length(), (i + 1) * CHUNK));
            send("skill upload " + slot + " " + i + " " + total + " " + chunk);
        }
        onClose();
    }

    private static String valueText(BlockType.Param p, float v) {
        int i = Math.round(v);
        if (p.kind() == BlockType.KIND_PARTICLE) {
            return BlockType.PARTICLE_NAMES[Math.max(0, Math.min(BlockType.PARTICLE_NAMES.length - 1, i))];
        }
        if (p.kind() == BlockType.KIND_SOUND) {
            return BlockType.SOUND_NAMES[Math.max(0, Math.min(BlockType.SOUND_NAMES.length - 1, i))];
        }
        return fmt(v);
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

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ตัวสร้างสกิลแบบไทม์ไลน์ (สไตล์ JJS): ซ้ายคือลิสต์บล็อกเรียงกัน ขวาคือค่าของบล็อกที่เลือก
 * ค่าตัวเลขทั้งหมดพิมพ์เองได้ ปุ่ม "ดูกล่อง" แสดงกล่องฮิตบ็อกซ์ในโลกด้วยอนุภาค
 * ใช้เฉพาะ widget สำเร็จรูปของเกม (ไม่วาดเอง) เพื่อลดผลกระทบจากการเปลี่ยน API ด้าน rendering
 */
public class SkillBuilderScreen extends Screen {
    private static final String[] KEYS = {"Z", "X", "C", "V"};
    private static final int W = 420;
    private static final int PAGE = 6;
    private static final int CHUNK = 120;

    private int slot = 1;
    private String name = "";
    private float cdSec = 5f;
    private final List<Skill.Block> blocks = new ArrayList<>();
    private int selected = -1;
    private int page = 0;
    private int ppage = 0;
    private boolean loaded = false;

    private EditBox nameBox;
    private EditBox cdBox;
    private final Map<Integer, EditBox> paramBoxes = new LinkedHashMap<>();
    private int boxBlock = -1;

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
        paramBoxes.clear();
        boxBlock = -1;

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

        // ชื่อ + คูลดาวน์ (พิมพ์เอง หน่วยวินาที)
        nameBox = addRenderableWidget(new EditBox(this.font, x0, top + 32, 240, 18,
                Component.translatable("screen.skillforge.name_hint")));
        nameBox.setMaxLength(Skill.MAX_NAME);
        nameBox.setHint(Component.translatable("screen.skillforge.name_hint"));
        nameBox.setValue(name);

        addRenderableWidget(new StringWidget(x0 + 246, top + 37, 70, 12,
                Component.literal("คูลดาวน์ (วิ)"), this.font));
        cdBox = addRenderableWidget(new EditBox(this.font, x0 + 330, top + 32, 86, 18,
                Component.literal("cooldown")));
        cdBox.setMaxLength(8);
        cdBox.setValue(fmt(cdSec));

        // หัวข้อซ้าย + ปุ่มเปลี่ยนหน้า
        int maxPage = Math.max(0, (blocks.size() - 1) / PAGE);
        page = Math.min(page, maxPage);
        addRenderableWidget(new StringWidget(x0, top + 55, 150, 12,
                Component.literal("ไทม์ไลน์ (" + blocks.size() + "/" + Skill.MAX_BLOCKS + ")")
                        .withStyle(ChatFormatting.AQUA), this.font));
        btn(Component.literal("^"), x0 + 158, top + 52, 20, 14, () -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        });
        btn(Component.literal("v"), x0 + 180, top + 52, 20, 14, () -> {
            page = Math.min(maxPage, page + 1);
            rebuildWidgets();
        });

        // ลิสต์บล็อก (ซ้าย)
        for (int i = 0; i < PAGE; i++) {
            final int bi = page * PAGE + i;
            if (bi >= blocks.size()) break;
            int y = top + 66 + i * 20;
            BlockType bt = BlockType.byId(blocks.get(bi).type());
            MutableComponent label = Component.literal("#" + (bi + 1) + " " + (bt == null ? "?" : bt.label));
            if (bi == selected) label.withStyle(ChatFormatting.YELLOW);
            btn(label, x0, y, 136, () -> {
                selected = bi;
                ppage = 0;
                rebuildWidgets();
            });
            btn(Component.literal("^"), x0 + 138, y, 20, () -> {
                if (bi > 0) {
                    Collections.swap(blocks, bi, bi - 1);
                    if (selected == bi) selected = bi - 1;
                    rebuildWidgets();
                }
            });
            btn(Component.literal("v"), x0 + 160, y, 20, () -> {
                if (bi < blocks.size() - 1) {
                    Collections.swap(blocks, bi, bi + 1);
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
        if (selected >= 0 && selected < blocks.size()) {
            Skill.Block b = blocks.get(selected);
            BlockType t = BlockType.byId(b.type());
            if (t != null) {
                boxBlock = selected;
                int n = t.params.length;
                int maxPp = Math.max(0, (n - 1) / PAGE);
                ppage = Math.min(ppage, maxPp);

                addRenderableWidget(new StringWidget(x1, top + 55, 100, 12,
                        Component.literal(t.label + " (" + (ppage + 1) + "/" + (maxPp + 1) + ")")
                                .withStyle(ChatFormatting.AQUA), this.font));
                if (t == BlockType.HITBOX) {
                    btn(Component.literal("ดูกล่อง"), x1 + 100, top + 52, 68, 14, this::previewBox);
                }
                if (n > PAGE) {
                    btn(Component.literal("^"), x1 + 172, top + 52, 18, 14, () -> {
                        ppage = Math.max(0, ppage - 1);
                        rebuildWidgets();
                    });
                    btn(Component.literal("v"), x1 + 190, top + 52, 18, 14, () -> {
                        ppage = Math.min(maxPp, ppage + 1);
                        rebuildWidgets();
                    });
                }

                for (int i = 0; i < PAGE; i++) {
                    final int pi = ppage * PAGE + i;
                    if (pi >= n) break;
                    BlockType.Param param = t.params[pi];
                    int y = top + 66 + i * 20;
                    addRenderableWidget(new StringWidget(x1, y + 5, 118, 12, Component.literal(param.label()), this.font));
                    if (param.kind() == BlockType.KIND_NUMBER) {
                        EditBox box = addRenderableWidget(new EditBox(this.font, x1 + 120, y, 88, 18,
                                Component.literal(param.label())));
                        box.setMaxLength(8);
                        box.setValue(fmt(b.get(pi)));
                        paramBoxes.put(pi, box);
                    } else {
                        btn(Component.literal(valueText(param, b.get(pi))), x1 + 120, y, 88, () -> cycle(t, pi));
                    }
                }
            }
        } else {
            addRenderableWidget(new StringWidget(x1, top + 55, 150, 12,
                    Component.literal("ค่าของบล็อก").withStyle(ChatFormatting.AQUA), this.font));
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
                    ppage = 0;
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

    /** ทุกปุ่มจะอ่านค่าที่พิมพ์ในช่องก่อนทำงาน เพื่อไม่ให้ค่าที่พิมพ์หายตอนหน้าจอถูกสร้างใหม่ */
    private Button btn(Component label, int x, int y, int w, int h, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> {
            commitFields();
            action.run();
        }).bounds(x, y, w, h).build());
    }

    private Button btn(Component label, int x, int y, int w, Runnable action) {
        return btn(label, x, y, w, 18, action);
    }

    private void commitFields() {
        if (cdBox != null) {
            Float v = parse(cdBox.getValue());
            if (v != null && !v.isNaN() && !v.isInfinite()) cdSec = Math.max(0.5f, Math.min(120f, v));
        }
        if (boxBlock >= 0 && boxBlock < blocks.size() && !paramBoxes.isEmpty()) {
            Skill.Block b = blocks.get(boxBlock);
            BlockType t = BlockType.byId(b.type());
            if (t == null) return;
            List<Float> nv = new ArrayList<>(b.v());
            for (Map.Entry<Integer, EditBox> en : paramBoxes.entrySet()) {
                int pi = en.getKey();
                Float v = parse(en.getValue().getValue());
                if (v == null) continue;
                while (nv.size() <= pi) nv.add(t.params[pi].def());
                nv.set(pi, t.clamp(pi, v));
            }
            blocks.set(boxBlock, new Skill.Block(b.type(), nv));
        }
    }

    private static Float parse(String s) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cycle(BlockType t, int pi) {
        if (selected < 0 || selected >= blocks.size()) return;
        Skill.Block b = blocks.get(selected);
        List<Float> nv = new ArrayList<>(b.v());
        while (nv.size() <= pi) nv.add(t.params[pi].def());
        float v = nv.get(pi) + 1;
        if (v > t.params[pi].max()) v = 0;
        nv.set(pi, v);
        blocks.set(selected, new Skill.Block(b.type(), nv));
        rebuildWidgets();
    }

    /** ส่งขนาด/ตำแหน่งของฮิตบ็อกซ์ที่เลือกไปให้เซิร์ฟเวอร์วาดกล่องอนุภาค (เริ่ม=ไฟส้ม, จบ=ไฟฟ้า) */
    private void previewBox() {
        if (selected < 0 || selected >= blocks.size()) return;
        Skill.Block b = blocks.get(selected);
        if (!BlockType.HITBOX.id.equals(b.type())) return;
        int[] idx = {0, 1, 2, 5, 6, 7, 8, 9, 10};
        StringBuilder sb = new StringBuilder("skill preview");
        for (int i : idx) sb.append(' ').append(b.get(i));
        send(sb.toString());
    }

    /** ข้อความกึ่งกลาง (คำนวณตำแหน่งเอง เพราะ StringWidget.alignCenter ถูกเอาออกใน 26.x) */
    private StringWidget centered(int centerX, int y, Component text) {
        int w = this.font.width(text);
        return new StringWidget(centerX - w / 2, y, w, 12, text, this.font);
    }

    private void loadSlot(int s) {
        slot = s;
        selected = -1;
        page = 0;
        ppage = 0;
        blocks.clear();
        Skill sk = skillAt(slot);
        if (sk != null) {
            name = sk.name();
            blocks.addAll(sk.blocks());
            cdSec = sk.cooldownTicks() / 20f;
        } else {
            name = "";
            cdSec = 5f;
        }
        nameBox = null; // ให้ init() ใช้ค่า name ที่เพิ่งโหลด ไม่ใช่ข้อความเก่าในกล่อง
    }

    private void save() {
        String clean = nameBox.getValue().trim().replaceAll("[|;:\"\\\\]", "").replace(' ', '_');
        if (clean.isEmpty()) clean = "Skill" + slot;
        int cd = Math.round(cdSec * 20f);
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
        return BlockType.SOUND_NAMES[Math.max(0, Math.min(BlockType.SOUND_NAMES.length - 1, i))];
    }

    private static Skill skillAt(int slot) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : SkillAttachment.find(mc.player, slot);
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

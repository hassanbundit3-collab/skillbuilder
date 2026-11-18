package com.skillforge;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /skill upload <slot> <idx> <total> <data>   (เมนูสร้างสกิลใช้ส่งสกิลเป็นชิ้นๆ ไม่ต้องพิมพ์เอง)
 * /skill preview <w> <h> <l> <f1> <r1> <u1> <f2> <r2> <u2>   (ปุ่ม "ดูกล่อง" ในเมนูใช้)
 * /skill cast <slot>
 * /skill delete <slot>
 * /skill list
 *
 * เมนู GUI และปุ่มลัดใช้คำสั่งพวกนี้ ทำให้เซิร์ฟเวอร์เป็นผู้ตัดสินทุกอย่างและไม่ต้องมี packet แบบกำหนดเอง
 */
public final class SkillCommands {
    private static final Map<String, StringBuilder> BUFFERS = new HashMap<>();
    private static final int MAX_TOTAL_CHARS = 8000;

    private SkillCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("skill")
                        .then(Commands.literal("upload")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Skill.SLOTS))
                                .then(Commands.argument("idx", IntegerArgumentType.integer(0, 99))
                                .then(Commands.argument("total", IntegerArgumentType.integer(1, 100))
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .executes(SkillCommands::upload))))))
                        .then(Commands.literal("preview").then(previewArgs()))
                        .then(Commands.literal("cast")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Skill.SLOTS))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            SkillCaster.cast(player, IntegerArgumentType.getInteger(ctx, "slot"));
                                            return 1;
                                        })))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Skill.SLOTS))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                            boolean ok = SkillAttachment.remove(player, slot);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    ok ? "ลบสกิลช่อง " + slot + " แล้ว" : "ช่อง " + slot + " ว่างอยู่แล้ว"), false);
                                            return ok ? 1 : 0;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    List<Skill> skills = SkillAttachment.get(player);
                                    if (skills.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("ยังไม่มีสกิล กดปุ่ม G เพื่อสร้าง"), false);
                                        return 0;
                                    }
                                    for (Skill s : skills) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[" + s.slot() + "] " + s.name() + " | " + s.blocks().size()
                                                        + " บล็อก | cd " + (s.cooldownTicks() / 20f) + "s"), false);
                                    }
                                    return skills.size();
                                }))
                ));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> previewArgs() {
        String[] names = {"w", "h", "l", "f1", "r1", "u1", "f2", "r2", "u2"};
        ArgumentBuilder<CommandSourceStack, ?> node = null;
        for (int i = names.length - 1; i >= 0; i--) {
            float min = i < 3 ? 0.1f : -60f;
            float max = i < 3 ? 20f : 60f;
            RequiredArgumentBuilder<CommandSourceStack, Float> arg =
                    Commands.argument(names[i], FloatArgumentType.floatArg(min, max));
            if (node == null) {
                arg.executes(SkillCommands::preview);
            } else {
                arg.then(node);
            }
            node = arg;
        }
        return node;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String[] names = {"w", "h", "l", "f1", "r1", "u1", "f2", "r2", "u2"};
        float[] v = new float[names.length];
        for (int i = 0; i < names.length; i++) {
            v[i] = FloatArgumentType.getFloat(ctx, names[i]);
        }
        SkillCaster.preview(player, v);
        return 1;
    }

    private static int upload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        int idx = IntegerArgumentType.getInteger(ctx, "idx");
        int total = IntegerArgumentType.getInteger(ctx, "total");
        String data = StringArgumentType.getString(ctx, "data");

        String key = player.getUUID() + ":" + slot;
        if (idx == 0) BUFFERS.put(key, new StringBuilder());
        StringBuilder sb = BUFFERS.get(key);
        if (sb == null) return 0;
        if (sb.length() + data.length() > MAX_TOTAL_CHARS) {
            BUFFERS.remove(key);
            src.sendFailure(Component.literal("สกิลใหญ่เกินไป"));
            return 0;
        }
        sb.append(data);

        if (idx == total - 1) {
            BUFFERS.remove(key);
            Skill skill = Skill.decode(slot, sb.toString());
            if (skill == null) {
                src.sendFailure(Component.literal("ข้อมูลสกิลไม่ถูกต้อง"));
                return 0;
            }
            SkillAttachment.put(player, skill);
            final String name = skill.name();
            src.sendSuccess(() -> Component.literal("บันทึกสกิล \"" + name + "\" ลงช่อง " + slot + " แล้ว"), false);
        }
        return 1;
    }
}

package com.skillforge;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * /skill create <slot> <name> <type> <damage> <range> <cooldownSeconds> <effect>
 * /skill cast <slot>
 * /skill delete <slot>
 * /skill list
 *
 * เมนู GUI และปุ่มลัดก็ใช้คำสั่งพวกนี้เหมือนกัน ทำให้เซิร์ฟเวอร์เป็นผู้ตัดสินทุกอย่าง
 */
public final class SkillCommands {
    private SkillCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("skill")
                        .then(Commands.literal("create")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Skill.SLOTS))
                                .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SkillType.ids(), b))
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0f, Skill.MAX_DAMAGE))
                                .then(Commands.argument("range", FloatArgumentType.floatArg(1f, Skill.MAX_RANGE))
                                .then(Commands.argument("cooldown", FloatArgumentType.floatArg(0.5f, Skill.MAX_COOLDOWN_SECONDS))
                                .then(Commands.argument("effect", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SkillEffect.ids(), b))
                                        .executes(SkillCommands::create))))))))) 
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
                                        ctx.getSource().sendSuccess(() -> Component.literal("ยังไม่มีสกิล ใช้ /skill create หรือกด G เพื่อสร้าง"), false);
                                        return 0;
                                    }
                                    for (Skill s : skills) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[" + s.slot() + "] " + s.name() + " | " + s.type().id()
                                                        + " | dmg " + s.damage() + " | range " + s.range()
                                                        + " | cd " + (s.cooldownTicks() / 20f) + "s | " + s.effect().id()), false);
                                    }
                                    return skills.size();
                                }))
                ));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        String rawName = StringArgumentType.getString(ctx, "name").trim();
        final String name = rawName.length() > Skill.MAX_NAME ? rawName.substring(0, Skill.MAX_NAME) : rawName;

        Optional<SkillType> type = SkillType.byId(StringArgumentType.getString(ctx, "type"));
        Optional<SkillEffect> effect = SkillEffect.byId(StringArgumentType.getString(ctx, "effect"));
        if (type.isEmpty() || effect.isEmpty()) {
            src.sendFailure(Component.literal("ประเภทหรือเอฟเฟกต์ไม่ถูกต้อง ลองกด Tab เพื่อดูตัวเลือก"));
            return 0;
        }

        float damage = FloatArgumentType.getFloat(ctx, "damage");
        float range = FloatArgumentType.getFloat(ctx, "range");
        int cooldownTicks = Math.max(Skill.MIN_COOLDOWN_TICKS,
                Math.round(FloatArgumentType.getFloat(ctx, "cooldown") * 20f));

        SkillAttachment.put(player, new Skill(slot, name, type.get(), damage, range, cooldownTicks, effect.get()));
        src.sendSuccess(() -> Component.literal("สร้างสกิล \"" + name + "\" ลงช่อง " + slot + " แล้ว"), false);
        return 1;
    }
}

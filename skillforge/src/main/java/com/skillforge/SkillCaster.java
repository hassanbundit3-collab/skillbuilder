package com.skillforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ตรรกะการใช้สกิล ทำงานฝั่งเซิร์ฟเวอร์เท่านั้น */
public final class SkillCaster {
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    private SkillCaster() {
    }

    public static void cast(ServerPlayer player, int slot) {
        Skill skill = SkillAttachment.find(player, slot);
        if (skill == null) {
            player.displayClientMessage(Component.literal("ช่อง " + slot + " ยังว่างอยู่"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        long[] cd = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[Skill.SLOTS]);
        if (now < cd[slot - 1]) {
            double left = (cd[slot - 1] - now) / 20.0;
            player.displayClientMessage(Component.literal(String.format("%s คูลดาวน์ %.1f วิ", skill.name(), left)), true);
            return;
        }
        cd[slot - 1] = now + skill.cooldownTicks();

        switch (skill.type()) {
            case PROJECTILE -> projectile(level, player, skill);
            case DASH -> dash(level, player, skill);
            case AREA -> area(level, player, skill);
            case HEAL -> heal(level, player, skill);
        }
        player.displayClientMessage(Component.literal(skill.name()), true);
    }

    // ---------- ประเภทสกิล ----------

    private static void projectile(ServerLevel level, ServerPlayer p, Skill s) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        ParticleOptions trail = particle(s.effect());
        Vec3 impact = eye.add(dir.scale(s.range()));
        LivingEntity hit = null;

        for (double d = 1.0; d <= s.range(); d += 0.5) {
            Vec3 pos = eye.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(pos);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                impact = pos;
                break;
            }
            level.sendParticles(trail, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            AABB box = new AABB(pos, pos).inflate(0.6);
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != p && e.isAlive() && !e.isSpectator());
            if (!found.isEmpty()) {
                hit = found.get(0);
                impact = pos;
                break;
            }
        }

        if (hit != null) applyHit(level, p, hit, s);
        burst(level, trail, impact, 15, 0.3);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void dash(ServerLevel level, ServerPlayer p, Skill s) {
        Vec3 look = p.getLookAngle();
        double power = Math.min(4.0, Math.max(0.8, s.range() / 8.0));

        if (s.damage() > 0) {
            AABB sweep = p.getBoundingBox().expandTowards(look.scale(s.range() / 2.0)).inflate(1.0);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != p && e.isAlive())) {
                applyHit(level, p, t, s);
            }
        }

        p.setDeltaMovement(look.scale(power));
        p.resetFallDistance();
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        burst(level, particle(s.effect()), p.position().add(0, 1, 0), 25, 0.4);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 1.4f);
    }

    private static void area(ServerLevel level, ServerPlayer p, Skill s) {
        double r = s.range();
        AABB box = p.getBoundingBox().inflate(r);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && e.distanceTo(p) <= r)) {
            applyHit(level, p, t, s);
        }

        ParticleOptions particle = particle(s.effect());
        for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2 / 48) * i;
            level.sendParticles(particle, p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r,
                    1, 0, 0.1, 0, 0);
        }
        burst(level, particle, p.position().add(0, 1, 0), 30, 0.8);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void heal(ServerLevel level, ServerPlayer p, Skill s) {
        p.heal(s.damage());
        burst(level, ParticleTypes.HEART, p.position().add(0, 1.2, 0), 8, 0.5);
    }

    // ---------- ตัวช่วย ----------

    private static void applyHit(ServerLevel level, ServerPlayer p, LivingEntity target, Skill s) {
        if (s.damage() > 0) {
            target.hurtServer(level, level.damageSources().playerAttack(p), s.damage());
        }
        switch (s.effect()) {
            case FIRE -> target.igniteForSeconds(5);
            case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            case KNOCKBACK -> target.knockback(1.5, p.getX() - target.getX(), p.getZ() - target.getZ());
            case NONE -> {
            }
        }
    }

    private static ParticleOptions particle(SkillEffect effect) {
        return switch (effect) {
            case NONE -> ParticleTypes.CRIT;
            case FIRE -> ParticleTypes.FLAME;
            case SLOW -> ParticleTypes.SNOWFLAKE;
            case POISON -> ParticleTypes.WITCH;
            case WITHER -> ParticleTypes.SMOKE;
            case KNOCKBACK -> ParticleTypes.CLOUD;
        };
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
    }
}

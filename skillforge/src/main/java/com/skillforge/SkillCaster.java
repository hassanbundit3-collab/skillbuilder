package com.skillforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ตัวรันสกิล ทำงานฝั่งเซิร์ฟเวอร์เท่านั้น
 * สกิลคือลิสต์ของบล็อก รันเรียงจากบนลงล่าง บล็อก "รอ" จะหยุดรอตามจำนวน tick แล้วรันต่อ
 */
public final class SkillCaster {
    // ลำดับต้องตรงกับ BlockType.PARTICLE_NAMES
    private static final ParticleOptions[] PARTICLES = {
            ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.ELECTRIC_SPARK,
            ParticleTypes.END_ROD, ParticleTypes.CRIT, ParticleTypes.SNOWFLAKE,
            ParticleTypes.WITCH, ParticleTypes.SMOKE, ParticleTypes.CLOUD, ParticleTypes.HEART
    };

    // ลำดับต้องตรงกับ BlockType.SOUND_NAMES
    private static final SoundEvent[] SOUNDS = {
            SoundEvents.BLAZE_SHOOT, SoundEvents.GENERIC_EXPLODE.value(), SoundEvents.ENDERMAN_TELEPORT
    };

    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<Proj> PROJECTILES = new ArrayList<>();

    private SkillCaster() {
    }

    private static final class Running {
        final ServerPlayer player;
        final Skill skill;
        int index = 0;
        int wait = 0;

        Running(ServerPlayer player, Skill skill) {
            this.player = player;
            this.skill = skill;
        }
    }

    private static final class Proj {
        final ServerLevel level;
        final ServerPlayer owner;
        Vec3 pos;
        final Vec3 dir;
        final double speed;
        double left;
        final float damage;
        final float knockback;
        final ParticleOptions fx;

        Proj(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, double speed, double range,
             float damage, float knockback, ParticleOptions fx) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
            this.speed = speed;
            this.left = range;
            this.damage = damage;
            this.knockback = knockback;
            this.fx = fx;
        }
    }

    // ---------- เริ่มใช้สกิล ----------

    public static void cast(ServerPlayer player, int slot) {
        Skill skill = SkillAttachment.find(player, slot);
        if (skill == null) {
            overlay(player, Component.literal("ช่อง " + slot + " ยังว่างอยู่"));
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        long[] cd = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[Skill.SLOTS]);
        if (now < cd[slot - 1]) {
            double left = (cd[slot - 1] - now) / 20.0;
            overlay(player, Component.literal(String.format("%s คูลดาวน์ %.1f วิ", skill.name(), left)));
            return;
        }
        cd[slot - 1] = now + skill.cooldownTicks();

        Running r = new Running(player, skill);
        RUNNING.add(r);
        if (advance(r)) RUNNING.remove(r);
        overlay(player, Component.literal(skill.name()));
    }

    // ---------- เรียกทุก tick ของเซิร์ฟเวอร์ ----------

    public static void tick() {
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            Running r = it.next();
            if (r.player.isRemoved() || !r.player.isAlive()) {
                it.remove();
                continue;
            }
            if (r.wait > 0) r.wait--;
            if (advance(r)) it.remove();
        }
        for (Iterator<Proj> it = PROJECTILES.iterator(); it.hasNext(); ) {
            if (stepProjectile(it.next())) it.remove();
        }
    }

    /** รันบล็อกต่อจากจุดเดิมจนเจอบล็อกรอหรือหมดลิสต์ คืน true ถ้ารันจบแล้ว */
    private static boolean advance(Running r) {
        List<Skill.Block> blocks = r.skill.blocks();
        while (r.wait <= 0 && r.index < blocks.size()) {
            Skill.Block b = blocks.get(r.index++);
            BlockType t = BlockType.byId(b.type());
            if (t == null) continue;
            if (t == BlockType.WAIT) {
                r.wait = Math.round(b.get(0));
                continue;
            }
            exec(r.player, t, b);
        }
        return r.index >= blocks.size() && r.wait <= 0;
    }

    // ---------- บล็อกแต่ละชนิด ----------

    private static void exec(ServerPlayer p, BlockType t, Skill.Block b) {
        ServerLevel level = (ServerLevel) p.level();
        switch (t) {
            case PARTICLE -> particle(level, p, b);
            case HITBOX -> hitbox(level, p, b);
            case PROJECTILE -> projectile(level, p, b);
            case VELOCITY -> velocity(p, b);
            case TELEPORT -> teleport(level, p, b);
            case SOUND -> sound(level, p, b);
            default -> {
            }
        }
    }

    private static void particle(ServerLevel level, ServerPlayer p, Skill.Block b) {
        ParticleOptions fx = PARTICLES[clampIndex((int) b.get(0), PARTICLES.length)];
        int count = (int) b.get(1);
        double spread = b.get(2);
        Vec3 pos = p.getEyePosition().add(p.getLookAngle().scale(b.get(3)));
        level.sendParticles(fx, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
    }

    private static void hitbox(ServerLevel level, ServerPlayer p, Skill.Block b) {
        double size = b.get(0);
        Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(b.get(1)));
        float damage = b.get(2);
        float knockback = b.get(3);

        AABB box = new AABB(center, center).inflate(size);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : found) {
            hit(level, p, target, damage, knockback);
        }
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 8, size / 2, size / 2, size / 2, 0.05);
    }

    private static void projectile(ServerLevel level, ServerPlayer p, Skill.Block b) {
        ParticleOptions fx = PARTICLES[clampIndex((int) b.get(4), PARTICLES.length)];
        PROJECTILES.add(new Proj(level, p, p.getEyePosition(), p.getLookAngle(),
                b.get(0), b.get(1), b.get(2), b.get(3), fx));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    private static void velocity(ServerPlayer p, Skill.Block b) {
        Vec3 look = p.getLookAngle();
        double s = b.get(0);
        p.setDeltaMovement(look.x * s, look.y * s + b.get(1), look.z * s);
        p.resetFallDistance();
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    private static void teleport(ServerLevel level, ServerPlayer p, Skill.Block b) {
        Vec3 start = p.position();
        Vec3 look = p.getLookAngle();
        double best = 0;
        for (double d = 0.5; d <= b.get(0); d += 0.5) {
            Vec3 feet = start.add(look.scale(d));
            if (solid(level, feet) || solid(level, feet.add(0, 1, 0))) break;
            best = d;
        }
        if (best <= 0) return;
        Vec3 dest = start.add(look.scale(best));
        level.sendParticles(ParticleTypes.PORTAL, start.x, start.y + 1, start.z, 25, 0.3, 0.6, 0.3, 0.2);
        p.teleportTo(dest.x, dest.y, dest.z);
        level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 25, 0.3, 0.6, 0.3, 0.2);
    }

    private static void sound(ServerLevel level, ServerPlayer p, Skill.Block b) {
        SoundEvent s = SOUNDS[clampIndex((int) b.get(0), SOUNDS.length)];
        level.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, b.get(1), b.get(2));
    }

    // ---------- กระสุน ----------

    /** เดินกระสุนหนึ่ง tick คืน true ถ้ากระสุนหมดอายุ (ชนหรือสุดระยะ) */
    private static boolean stepProjectile(Proj pr) {
        int steps = Math.max(1, (int) Math.ceil(pr.speed / 0.5));
        double len = pr.speed / steps;
        for (int i = 0; i < steps; i++) {
            pr.pos = pr.pos.add(pr.dir.scale(len));
            pr.left -= len;

            if (solid(pr.level, pr.pos)) {
                burst(pr.level, pr.fx, pr.pos);
                return true;
            }
            pr.level.sendParticles(pr.fx, pr.pos.x, pr.pos.y, pr.pos.z, 2, 0.05, 0.05, 0.05, 0.0);

            AABB box = new AABB(pr.pos, pr.pos).inflate(0.6);
            List<LivingEntity> found = pr.level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != pr.owner && e.isAlive() && !e.isSpectator());
            if (!found.isEmpty()) {
                hit(pr.level, pr.owner, found.get(0), pr.damage, pr.knockback);
                burst(pr.level, pr.fx, pr.pos);
                return true;
            }
            if (pr.left <= 0) return true;
        }
        return false;
    }

    // ---------- ตัวช่วย ----------

    private static void hit(ServerLevel level, ServerPlayer p, LivingEntity target, float damage, float knockback) {
        if (damage > 0) {
            target.hurtServer(level, level.damageSources().playerAttack(p), damage);
        }
        if (knockback > 0) pushAway(p, target, knockback);
    }

    private static void burst(ServerLevel level, ParticleOptions fx, Vec3 pos) {
        level.sendParticles(fx, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.03);
    }

    private static boolean solid(ServerLevel level, Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos);
        return !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
    }

    private static int clampIndex(int i, int length) {
        return Math.max(0, Math.min(length - 1, i));
    }

    /** แสดงข้อความบน action bar (ใช้ packet ตรงๆ เพราะ displayClientMessage ถูกเอาออกใน 26.x) */
    private static void overlay(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSystemChatPacket(text, true));
    }

    /** ผลักเป้าหมายให้ห่างจากผู้ใช้สกิล (เขียนเองเพราะ LivingEntity.knockback เปลี่ยน signature) */
    private static void pushAway(ServerPlayer p, LivingEntity target, double strength) {
        Vec3 dir = new Vec3(target.getX() - p.getX(), 0, target.getZ() - p.getZ());
        if (dir.lengthSqr() < 1.0E-4) {
            dir = p.getLookAngle().multiply(1, 0, 1);
        }
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(dir.x, 0.4, dir.z));
        if (target instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }
}

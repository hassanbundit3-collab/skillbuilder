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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ตัวรันสกิล ทำงานฝั่งเซิร์ฟเวอร์เท่านั้น
 * สกิลคือลิสต์ของบล็อก รันเรียงจากบนลงล่าง บล็อก "รอ" จะหยุดรอตามจำนวน tick แล้วรันต่อ
 * บล็อก อนุภาค/ฮิตบ็อกซ์ ที่มี "ใช้เวลา" จะทำงานต่อเนื่องไปเรื่อยๆ โดยไม่บล็อกลำดับ (ซ้อนกันได้เหมือน JJS)
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
    private static final List<Active> ACTIVES = new ArrayList<>();
    private static final List<Proj> PROJECTILES = new ArrayList<>();
    private static final List<Preview> PREVIEWS = new ArrayList<>();

    private SkillCaster() {
    }

    /** กรอบอ้างอิงของผู้เล่น: จุดตา ทิศมอง ทิศหน้าแนวนอน และทิศขวา */
    private record Frame(Vec3 eye, Vec3 look, Vec3 fwdH, Vec3 right) {
        static Frame of(ServerPlayer p) {
            Vec3 look = p.getLookAngle();
            Vec3 h = new Vec3(look.x, 0, look.z);
            h = h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
            return new Frame(p.getEyePosition(), look, h, new Vec3(-h.z, 0, h.x));
        }

        /** จุดที่ หน้า f / ขวา r / สูง u จากระดับตา (หน้า = ตามทิศที่มอง) */
        Vec3 at(double f, double r, double u) {
            return eye.add(look.scale(f)).add(right.scale(r)).add(0, u, 0);
        }
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

    /** เอฟเฟกต์ที่ทำงานต่อเนื่องหลายtick (อนุภาค/ฮิตบ็อกซ์) */
    private static final class Active {
        final BlockType type;
        final ServerPlayer player;
        final Skill.Block block;
        final Frame frame;
        final Set<Integer> hit = new HashSet<>();
        int age = 0;

        Active(BlockType type, ServerPlayer player, Skill.Block block) {
            this.type = type;
            this.player = player;
            this.block = block;
            this.frame = Frame.of(player);
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
        final double radius;

        Proj(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, double speed, double range,
             float damage, float knockback, ParticleOptions fx, double radius) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
            this.speed = speed;
            this.left = range;
            this.damage = damage;
            this.knockback = knockback;
            this.fx = fx;
            this.radius = radius;
        }
    }

    private static final class Preview {
        final ServerPlayer player;
        final float[] v;
        int ttl = 80;

        Preview(ServerPlayer player, float[] v) {
            this.player = player;
            this.v = v;
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

    /** แสดงกล่องฮิตบ็อกซ์ตัวอย่างด้วยอนุภาค (v = กว้าง สูง ยาว เริ่มหน้า ขวา สูง จบหน้า ขวา สูง) */
    public static void preview(ServerPlayer player, float[] v) {
        PREVIEWS.removeIf(p -> p.player == player);
        Preview pv = new Preview(player, v);
        PREVIEWS.add(pv);
        drawPreview(pv);
    }

    // ---------- เรียกทุก tick ของเซิร์ฟเวอร์ ----------

    public static void tick() {
        for (Iterator<Active> it = ACTIVES.iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (a.player.isRemoved() || stepActive(a)) it.remove();
        }
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
        for (Iterator<Preview> it = PREVIEWS.iterator(); it.hasNext(); ) {
            Preview pv = it.next();
            pv.ttl--;
            if (pv.ttl <= 0 || pv.player.isRemoved()) {
                it.remove();
            } else if (pv.ttl % 4 == 0) {
                drawPreview(pv);
            }
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
            case PARTICLE, HITBOX -> {
                Active a = new Active(t, p, b);
                if (!stepActive(a)) ACTIVES.add(a);
            }
            case PROJECTILE -> projectile(level, p, b);
            case VELOCITY -> velocity(p, b);
            case TELEPORT -> teleport(level, p, b);
            case SOUND -> sound(level, p, b);
            default -> {
            }
        }
    }

    /** ทำงาน 1 tick ของบล็อกที่ต่อเนื่อง คืน true เมื่อครบเวลา */
    private static boolean stepActive(Active a) {
        Skill.Block b = a.block;
        boolean isParticle = a.type == BlockType.PARTICLE;
        int startIdx = isParticle ? 3 : 5;
        int durIdx = isParticle ? 9 : 11;
        int duration = Math.round(b.get(durIdx));
        boolean follow = b.get(durIdx + 1) >= 0.5f;

        Frame fr = follow ? Frame.of(a.player) : a.frame;
        double t = duration <= 0 ? 0.0 : Math.min(1.0, a.age / (double) duration);
        double f = lerp(b.get(startIdx), b.get(startIdx + 3), t);
        double r = lerp(b.get(startIdx + 1), b.get(startIdx + 4), t);
        double u = lerp(b.get(startIdx + 2), b.get(startIdx + 5), t);
        Vec3 pos = fr.at(f, r, u);

        ServerLevel level = (ServerLevel) a.player.level();
        if (isParticle) {
            ParticleOptions fx = PARTICLES[clampIndex((int) b.get(0), PARTICLES.length)];
            double spread = b.get(2);
            level.sendParticles(fx, pos.x, pos.y, pos.z, (int) b.get(1), spread, spread, spread, 0.02);
        } else {
            checkHitbox(level, a, fr, pos);
        }

        a.age++;
        return a.age > duration;
    }

    private static void checkHitbox(ServerLevel level, Active a, Frame fr, Vec3 pos) {
        Skill.Block b = a.block;
        double w = b.get(0);
        double h = b.get(1);
        double l = b.get(2);
        float damage = b.get(3);
        float knockback = b.get(4);
        ServerPlayer p = a.player;

        double reach = Math.max(Math.max(w, l), h) + 2;
        AABB search = new AABB(pos, pos).inflate(reach);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, search,
                e -> e != p && e.isAlive() && !e.isSpectator());
        for (LivingEntity e : found) {
            if (a.hit.contains(e.getId())) continue;
            if (inside(e, pos, fr, w, h, l)) {
                a.hit.add(e.getId());
                hit(level, p, e, damage, knockback);
            }
        }
    }

    /** เช็กว่าเป้าหมายอยู่ในกล่อง (หมุนตามทิศที่ผู้เล่นหันในแนวนอน) */
    private static boolean inside(LivingEntity e, Vec3 pos, Frame fr, double w, double h, double l) {
        AABB bb = e.getBoundingBox();
        Vec3 c = bb.getCenter();
        double dx = c.x - pos.x;
        double dy = c.y - pos.y;
        double dz = c.z - pos.z;
        double lr = dx * fr.right().x + dz * fr.right().z;
        double lf = dx * fr.fwdH().x + dz * fr.fwdH().z;
        double ew = Math.max(bb.getXsize(), bb.getZsize()) / 2;
        return Math.abs(lr) <= w / 2 + ew
                && Math.abs(lf) <= l / 2 + ew
                && Math.abs(dy) <= h / 2 + bb.getYsize() / 2;
    }

    private static void projectile(ServerLevel level, ServerPlayer p, Skill.Block b) {
        Frame fr = Frame.of(p);
        ParticleOptions fx = PARTICLES[clampIndex((int) b.get(4), PARTICLES.length)];
        Vec3 start = fr.at(b.get(6), b.get(7), b.get(8));
        PROJECTILES.add(new Proj(level, p, start, fr.look(),
                b.get(0), b.get(1), b.get(2), b.get(3), fx, b.get(5)));
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

            AABB box = new AABB(pr.pos, pr.pos).inflate(pr.radius);
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

    // ---------- กล่องตัวอย่าง ----------

    private static void drawPreview(Preview pv) {
        float[] v = pv.v;
        ServerLevel level = (ServerLevel) pv.player.level();
        Frame fr = Frame.of(pv.player);
        double w = Math.min(20, v[0]);
        double h = Math.min(20, v[1]);
        double l = Math.min(20, v[2]);
        Vec3 start = fr.at(v[3], v[4], v[5]);
        Vec3 end = fr.at(v[6], v[7], v[8]);
        box(level, fr, start, w, h, l, ParticleTypes.FLAME);
        if (start.distanceTo(end) > 0.05) {
            box(level, fr, end, w, h, l, ParticleTypes.SOUL_FIRE_FLAME);
        }
    }

    /** วาดเส้นขอบกล่องด้วยอนุภาค (หมุนตามทิศที่ผู้เล่นหัน) */
    private static void box(ServerLevel level, Frame fr, Vec3 c, double w, double h, double l, ParticleOptions fx) {
        double[] half = {w / 2, h / 2, l / 2};
        for (int axis = 0; axis < 3; axis++) {
            int o1 = (axis + 1) % 3;
            int o2 = (axis + 2) % 3;
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    double[] a = new double[3];
                    double[] b = new double[3];
                    a[axis] = -half[axis];
                    b[axis] = half[axis];
                    a[o1] = s1 * half[o1];
                    b[o1] = a[o1];
                    a[o2] = s2 * half[o2];
                    b[o2] = a[o2];
                    Vec3 pa = local(fr, c, a);
                    Vec3 pb = local(fr, c, b);
                    int n = Math.max(1, (int) Math.ceil(pa.distanceTo(pb) / 0.4));
                    for (int i = 0; i <= n; i++) {
                        Vec3 q = pa.lerp(pb, i / (double) n);
                        level.sendParticles(fx, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** แปลงพิกัดในกล่อง (กว้าง=ขวา, สูง=ขึ้น, ยาว=หน้าแนวนอน) เป็นพิกัดโลก */
    private static Vec3 local(Frame fr, Vec3 c, double[] a) {
        return c.add(fr.right().scale(a[0])).add(0, a[1], 0).add(fr.fwdH().scale(a[2]));
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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

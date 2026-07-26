package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.registry.AlchemyCriteria;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.effect.AlchemyMobEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PigManureInteractions {
    private static final int STINK_DURATION_TICKS = 20 * 20;
    private static final int REPEL_SCAN_INTERVAL_TICKS = 10;
    private static final double REPEL_RADIUS = 8.0D;
    private static final double REPEL_RADIUS_SQR = REPEL_RADIUS * REPEL_RADIUS;
    private static final double REPEL_SPEED = 1.35D;

    private PigManureInteractions() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PigManureInteractions::tickStinkyEntities);
    }

    public static boolean spreadToGrass(ServerLevel level, BlockHitResult hitResult) {
        BlockState state = level.getBlockState(hitResult.getBlockPos());
        if (!state.is(Blocks.GRASS_BLOCK)) {
            return false;
        }

        BlockState manureState = AlchemyBlocks.getPigManureState(state);
        if (manureState == null) {
            return false;
        }

        level.setBlock(hitResult.getBlockPos(), manureState, 3);
        level.playSound(null, hitResult.getBlockPos(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.8F, 0.9F);
        return true;
    }

    public static void applyStink(ServerLevel level, LivingEntity target, Entity source) {
        target.addEffect(new MobEffectInstance(AlchemyMobEffects.STINKY, STINK_DURATION_TICKS, 0), source);
        if (source instanceof ServerPlayer player) {
            AlchemyCriteria.PIG_MANURE_HIT_ENTITY.trigger(player);
        }
        if (target instanceof ServerPlayer player) {
            AlchemyCriteria.PIG_MANURE_GOT_HIT.trigger(player);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    public static boolean hasStink(LivingEntity entity) {
        return entity.hasEffect(AlchemyMobEffects.STINKY);
    }

    private static void tickStinkyEntities(MinecraftServer server) {
        if (server.getTickCount() % REPEL_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && living.isAlive() && hasStink(living)) {
                    repelFriendlyMobs(level, living);
                }
            }
        }
    }

    private static void repelFriendlyMobs(ServerLevel level, LivingEntity stinkyEntity) {
        AABB area = stinkyEntity.getBoundingBox().inflate(REPEL_RADIUS);
        for (PathfinderMob mob : level.getEntities(
                EntityTypeTest.forClass(PathfinderMob.class),
                area,
                mob -> isRepelledFriendlyMob(mob)
                        && mob.isAlive()
                        && mob != stinkyEntity
                        && mob.distanceToSqr(stinkyEntity) <= REPEL_RADIUS_SQR)) {
            Vec3 awayPos = DefaultRandomPos.getPosAway(mob, 8, 4, stinkyEntity.position());
            if (awayPos != null) {
                mob.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, REPEL_SPEED);
            }
        }
    }

    private static boolean isRepelledFriendlyMob(PathfinderMob mob) {
        return mob instanceof Animal || mob instanceof AbstractVillager;
    }
}

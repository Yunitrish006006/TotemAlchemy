package dev.totem.alchemy.gametest;

import dev.totem.alchemy.item.FlaskEnchantments;
import dev.totem.alchemy.mixture.*;
import dev.totem.alchemy.registry.AlchemyItems;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FlaskEnchantmentsGameTest {
    @GameTest(maxTicks = 40)
    public void nativeEnchantmentsAndExpandedDosePersistence(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var capacity = registry.getOrThrow(FlaskEnchantments.CAPACITY);
        var bottomless = registry.getOrThrow(FlaskEnchantments.BOTTOMLESS);
        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        require(h, flask.isEnchantable() && capacity.value().canEnchant(flask) && bottomless.value().canEnchant(flask), "Flask is not a native enchantment target");
        var tag = registry.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        boolean foundCapacity = false, foundBottomless = false;
        for(int seed=0;seed<128;seed++) {
            var selected=EnchantmentHelper.selectEnchantment(RandomSource.create(seed),flask,40,tag.stream());
            foundCapacity |= selected.stream().anyMatch(e -> e.enchantment().is(FlaskEnchantments.CAPACITY));
            foundBottomless |= selected.stream().anyMatch(e -> e.enchantment().is(FlaskEnchantments.BOTTOMLESS));
        }
        require(h,foundCapacity && foundBottomless,"Native enchanting table cannot roll both flask enchantments");
        for(int level=1;level<=5;level++) {
            flask.enchant(capacity,level);
            require(h,FlaskEnchantments.capacity(flask)==3+level,"Each capacity level must add one dose");
        }
        var mixture=new AlchemyMixtureState(8,8);
        mixture.setBaseActivated(true); mixture.putEffect("minecraft:speed",3600D*8,0);
        AlchemyMixtureBottle.writeState(flask,mixture);
        var loaded=AlchemyMixtureBottle.storedMixture(flask.copy());
        require(h,loaded.volumeUnits()==8 && loaded.copy().volumeUnits()==8,"Eight doses were truncated on reload/copy");
        require(h,new AlchemyMixtureState(3).mergeFrom(loaded)==false,"Cauldron limit changed");
        var player=h.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        for(int remaining=7;remaining>=0;remaining--) {
            flask=flask.finishUsingItem(h.getLevel(),player);
            require(h,AlchemyMixtureBottle.storedMixture(flask).volumeUnits()==remaining,"Normal drink lost or duplicated volume");
            if(remaining>0) require(h,Math.abs(AlchemyMixtureBottle.storedMixture(flask).effects().get("minecraft:speed").potencyTicks()-3600D*remaining)<.01,"Drink changed remaining concentration");
        }
        player.discard();h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void bottomlessDrinkKeepsMixtureAndEmptyFlaskStaysEmpty(GameTestHelper h) {
        ItemStack flask=new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        flask.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(FlaskEnchantments.BOTTOMLESS),1);
        var mixture=new AlchemyMixtureState(2); mixture.setBaseActivated(true); mixture.putEffect("minecraft:speed",7200D,0);
        AlchemyMixtureBottle.writeState(flask,mixture);
        String before=AlchemyMixtureBottle.storedMixture(flask).encode();
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        for(int i=0;i<6;i++) {
            flask=flask.finishUsingItem(h.getLevel(),player);
            require(h,AlchemyMixtureBottle.storedMixture(flask).encode().equals(before),"Bottomless drink consumed or changed mixture");
        }
        require(h,player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED),"Bottomless drink did not apply potion effect");
        AlchemyMixtureBottle.clearState(flask); flask.finishUsingItem(h.getLevel(),player);
        require(h,AlchemyMixtureBottle.storedMixture(flask).isEmpty(),"Empty bottomless flask generated potion");
        player.discard();h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void expandedRefillAndPartialPourPreserveEnchantments(GameTestHelper h) {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        ItemStack flask=new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        var registry=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        flask.enchant(registry.getOrThrow(FlaskEnchantments.CAPACITY),5);
        flask.enchant(registry.getOrThrow(FlaskEnchantments.BOTTOMLESS),1);
        flask.set(DataComponents.CUSTOM_NAME,Component.literal("Travel Flask"));
        player.setItemInHand(InteractionHand.MAIN_HAND,flask);
        for(int expected : new int[]{3,6,8}) {
            level.setBlockAndUpdate(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));
            player.setShiftKeyDown(expected!=3);
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            flask=player.getMainHandItem();
            require(h,AlchemyMixtureBottle.storedMixture(flask).volumeUnits()==expected,"Expanded flask did not top up correctly");
        }
        require(h,level.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL)==1,"Top up destroyed excess source liquid");
        require(h,FlaskEnchantments.capacity(flask)==8 && FlaskEnchantments.isBottomless(flask)
                && flask.getHoverName().getString().equals("Travel Flask"),"Filling removed flask components");
        level.setBlockAndUpdate(pos,Blocks.CAULDRON.defaultBlockState());player.setShiftKeyDown(false);
        UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        flask=player.getMainHandItem();
        require(h,AlchemyMixtureBottle.storedMixture(flask).volumeUnits()==5,"Bottomless pouring failed to debit three transferred doses");
        require(h,level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity c && c.mixtureSnapshot().volumeUnits()==3,"Pour overflowed the cauldron");
        flask.remove(DataComponents.ENCHANTMENTS);
        require(h,AlchemyMixtureBottle.storedMixture(flask).volumeUnits()==5 && flask.getBarWidth()<=13,"Removing capacity destroyed existing liquid");
        player.discard();h.succeed();
    }
    @GameTest(maxTicks = 40)
    public void emptyFillPreservesSourceMixtureExactly(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState());
        var cauldron = (AlchemyCauldronBlockEntity) level.getBlockEntity(pos);
        var source = new AlchemyMixtureState(3);
        source.setBaseActivated(true);
        source.setStability(93);
        source.setCanonicalPotionId("minecraft:swiftness");
        source.setDeliveryForm(AlchemyMixtureState.DeliveryForm.SPLASH);
        source.putEffect("minecraft:speed", 10800D, 0);
        source.lockHeatIfFinished();
        require(h, cauldron.initializeMixture(source), "Source mixture was rejected");
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack flask = new ItemStack(AlchemyItems.LARGE_POTION_FLASK);
        flask.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(FlaskEnchantments.CAPACITY), 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, flask);
        UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(h, AlchemyMixtureBottle.storedMixture(player.getMainHandItem()).encode().equals(source.encode()),
                "Filling an empty flask changed source metadata or concentration");
        require(h, level.getBlockState(pos).is(Blocks.CAULDRON), "Empty fill duplicated the source");
        player.discard();
        h.succeed();
    }
    private static void require(GameTestHelper h,boolean value,String message) { if(!value) h.fail(message); }
}

package com.duncanjones.apneoforge.mixin;

import com.duncanjones.apneoforge.AudioPlayer;
import com.duncanjones.apneoforge.CustomJukeboxSongPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity {

    @Shadow
    private ItemStack item;

    @Shadow
    @Final
    private JukeboxSongPlayer jukeboxSongPlayer;

    public JukeboxBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        AudioPlayer.LOGGER.info("[MIXIN-ALIVE] JukeboxBlockEntityMixin constructor ran for blockEntityType={} at {} -- if you see this, the mixin merged successfully", blockEntityType, blockPos);
    }

    @Redirect(method = "setTheItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/JukeboxSongPlayer;play(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/Holder;)V"))
    public void play(JukeboxSongPlayer instance, LevelAccessor levelAccessor, Holder<JukeboxSong> holder) {
        AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] play redirect triggered at {}, item={}, levelAccessor={}", this.getBlockPos(), item, levelAccessor);
        if (!(levelAccessor instanceof ServerLevel serverLevel)) {
            AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] play: levelAccessor is not a ServerLevel ({}), falling back to vanilla", levelAccessor.getClass());
            instance.play(levelAccessor, holder);
            return;
        }
        if (!(jukeboxSongPlayer instanceof CustomJukeboxSongPlayer customJukeboxSongPlayer)) {
            AudioPlayer.LOGGER.warn("[JukeboxBlockEntityMixin] play: jukeboxSongPlayer is not a CustomJukeboxSongPlayer -- JukeboxSongPlayerMixin did not apply!");
            instance.play(levelAccessor, holder);
            return;
        }
        boolean custom = customJukeboxSongPlayer.audioplayer$customPlay(serverLevel, item);
        AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] play: audioplayer$customPlay returned {} for item={}", custom, item);
        if (!custom) {
            AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] play: falling back to vanilla jukebox play");
            instance.play(levelAccessor, holder);
        }
    }

    @Redirect(method = "setTheItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/JukeboxSongPlayer;stop(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    public void stop(JukeboxSongPlayer instance, LevelAccessor levelAccessor, BlockState blockState) {
        AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] stop redirect triggered at {}", this.getBlockPos());
        if (!(jukeboxSongPlayer instanceof CustomJukeboxSongPlayer customJukeboxSongPlayer)) {
            instance.stop(levelAccessor, blockState);
            return;
        }
        boolean custom = customJukeboxSongPlayer.audioplayer$customStop();
        AudioPlayer.LOGGER.info("[JukeboxBlockEntityMixin] stop: audioplayer$customStop returned {}", custom);
        if (!custom) {
            instance.stop(levelAccessor, blockState);
        }
    }

    @Inject(method = "loadAdditional", at = @At(value = "RETURN"))
    public void load(CompoundTag compound, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!(jukeboxSongPlayer instanceof CustomJukeboxSongPlayer customJukeboxSongPlayer)) {
            return;
        }
        customJukeboxSongPlayer.audioplayer$onLoad(item, compound, provider);
    }

    @Inject(method = "saveAdditional", at = @At(value = "RETURN"))
    public void save(CompoundTag compound, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!(jukeboxSongPlayer instanceof CustomJukeboxSongPlayer customJukeboxSongPlayer)) {
            return;
        }
        customJukeboxSongPlayer.audioplayer$onSave(item, compound, provider);
    }
}

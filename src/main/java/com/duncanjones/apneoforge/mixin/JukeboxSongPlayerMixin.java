package com.duncanjones.apneoforge.mixin;

import com.duncanjones.apneoforge.AudioManager;
import com.duncanjones.apneoforge.AudioPlayer;
import com.duncanjones.apneoforge.CustomJukeboxSongPlayer;
import com.duncanjones.apneoforge.CustomSound;
import com.duncanjones.apneoforge.PlayerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(JukeboxSongPlayer.class)
public abstract class JukeboxSongPlayerMixin implements CustomJukeboxSongPlayer {

    @Shadow
    @Nullable
    private Holder<JukeboxSong> song;
    @Shadow
    @Final
    private JukeboxSongPlayer.OnSongChanged onSongChanged;

    @Shadow
    private long ticksSinceSongStarted;

    @Shadow
    @Final
    private BlockPos blockPos;

    @Unique
    @Nullable
    private UUID audioplayer$customSoundId;

    @Unique
    private long audioplayer$expectedEndTicks;

    @Unique
    @Nullable
    private ServerLevel audioplayer$lastLevel;

    @Unique
    private boolean audioplayer$tickAliveLogged;

    @Override
    public boolean audioplayer$customPlay(ServerLevel level, ItemStack item) {
        CustomSound customSound = CustomSound.of(item);
        if (customSound == null) {
            AudioPlayer.LOGGER.info("[JukeboxSongPlayerMixin] customPlay: CustomSound.of(item) returned null for item={} at {}, no custom audio on this item", item, blockPos);
            return false;
        }
        UUID soundId = AudioManager.playVanillaJukebox(level, blockPos, customSound);
        if (soundId == null) {
            AudioPlayer.LOGGER.warn("[JukeboxSongPlayerMixin] customPlay: AudioManager.playVanillaJukebox returned null at {}", blockPos);
            return false;
        }
        AudioPlayer.LOGGER.info("[JukeboxSongPlayerMixin] customPlay: soundId {} playing at {}", soundId, blockPos);
        audioplayer$customSoundId = soundId;
        audioplayer$lastLevel = level;
        audioplayer$expectedEndTicks = audioplayer$computeExpectedEndTicks(level, soundId);
        song = null;
        ticksSinceSongStarted = 0L;
        onSongChanged.notifyChange();
        return true;
    }

    @Unique
    private long audioplayer$computeExpectedEndTicks(ServerLevel level, UUID soundId) {
        int maxLengthSeconds = PlayerType.MUSIC_DISC.getMaxDuration().get();
        try {
            float lengthSeconds = AudioManager.getLengthSeconds(AudioManager.getSound(level.getServer(), soundId));
            float clampedLength = Math.min(lengthSeconds, maxLengthSeconds);
            return (long) (clampedLength * 20L);
        } catch (Exception e) {
            return (long) maxLengthSeconds * 20L;
        }
    }

    @Override
    public boolean audioplayer$customStop() {
        if (audioplayer$customSoundId == null) {
            return false;
        }
        AudioPlayer.LOGGER.info("[JukeboxSongPlayerMixin] customStop: stopping soundId {}", audioplayer$customSoundId);
        if (audioplayer$lastLevel != null) {
            AudioManager.stopVanillaJukebox(audioplayer$lastLevel, blockPos, audioplayer$customSoundId);
        }
        audioplayer$customSoundId = null;
        song = null;
        ticksSinceSongStarted = 0L;
        onSongChanged.notifyChange();
        return true;
    }

    @Inject(method = "isPlaying", at = @At(value = "HEAD"), cancellable = true)
    public void isPlaying(CallbackInfoReturnable<Boolean> cir) {
        if (audioplayer$customSoundId == null) {
            return;
        }
        cir.setReturnValue(ticksSinceSongStarted < audioplayer$expectedEndTicks);
    }

    @Inject(method = "tick", at = @At(value = "HEAD"), cancellable = true)
    public void tick(LevelAccessor levelAccessor, BlockState blockState, CallbackInfo ci) {
        if (!audioplayer$tickAliveLogged) {
            audioplayer$tickAliveLogged = true;
            AudioPlayer.LOGGER.info("[MIXIN-ALIVE] JukeboxSongPlayerMixin.tick() running at {} -- if you see this, the mixin merged successfully", blockPos);
        }
        if (audioplayer$customSoundId == null) {
            return;
        }
        ci.cancel();
        if (!isPlaying()) {
            audioplayer$customStop();
            return;
        }

        if (shouldEmitJukeboxPlayingEvent()) {
            spawnMusicParticles(levelAccessor, blockPos);
        }
        ticksSinceSongStarted++;
    }

    @Override
    public void audioplayer$onSave(ItemStack item, CompoundTag compound, HolderLookup.Provider provider) {
        if (audioplayer$customSoundId != null && !item.isEmpty()) {
            compound.putUUID("CustomSoundId", audioplayer$customSoundId);
        }
    }

    @Override
    public void audioplayer$onLoad(ItemStack item, CompoundTag compound, HolderLookup.Provider provider) {
        if (compound.hasUUID("CustomSoundId") && !item.isEmpty()) {
            audioplayer$customSoundId = compound.getUUID("CustomSoundId");
            song = null;
        } else {
            audioplayer$customSoundId = null;
        }
    }

    @Shadow
    public abstract boolean isPlaying();

    @Shadow
    protected abstract boolean shouldEmitJukeboxPlayingEvent();

    @Shadow
    private static void spawnMusicParticles(LevelAccessor levelAccessor, BlockPos blockPos) {
    }

}

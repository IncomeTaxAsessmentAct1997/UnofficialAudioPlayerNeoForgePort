package com.duncanjones.apneoforge.mixin;

import com.duncanjones.apneoforge.AudioNetworkingClient;
import com.duncanjones.apneoforge.AudioPlayer;
import com.duncanjones.apneoforge.CustomSoundLocations;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void audioplayer$getCompleteBuffer(ResourceLocation resourceLocation, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        UUID soundId = CustomSoundLocations.getSoundId(resourceLocation);
        if (soundId == null) {
            return;
        }
        CompletableFuture<SoundBuffer> pending = AudioNetworkingClient.PENDING_BUFFERS.get(soundId);
        if (pending == null) {
            AudioPlayer.LOGGER.warn("[SoundBufferLibraryMixin] No pending buffer registered for custom sound {}", soundId);
            return;
        }
        cir.setReturnValue(pending);
    }

}

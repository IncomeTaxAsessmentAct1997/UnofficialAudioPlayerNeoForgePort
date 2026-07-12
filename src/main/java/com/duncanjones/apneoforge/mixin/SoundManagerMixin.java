package com.duncanjones.apneoforge.mixin;

import com.duncanjones.apneoforge.CustomSoundLocations;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "getSoundEvent", at = @At("HEAD"), cancellable = true)
    private void audioplayer$getSoundEvent(ResourceLocation location, CallbackInfoReturnable<WeighedSoundEvents> cir) {
        UUID soundId = CustomSoundLocations.getSoundIdFromEventLocation(location);
        if (soundId == null) {
            return;
        }
        WeighedSoundEvents events = new WeighedSoundEvents(location, null);
        events.addSound(new Sound(location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16));
        cir.setReturnValue(events);
    }

}
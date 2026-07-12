package com.duncanjones.apneoforge;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.UUID;

public class CustomSoundLocations {

    private static final String LOCATION_PREFIX = "custom/";
    private static final String PATH_PREFIX = "sounds/" + LOCATION_PREFIX;
    private static final String PATH_SUFFIX = ".ogg";

    public static ResourceLocation getSoundLocation(UUID soundId) {
        return ResourceLocation.fromNamespaceAndPath(AudioPlayer.MODID, LOCATION_PREFIX + soundId);
    }

    public static SoundEvent getSoundEvent(UUID soundId) {
        return SoundEvent.createVariableRangeEvent(getSoundLocation(soundId));
    }

    public static Holder<SoundEvent> getSoundEventHolder(UUID soundId) {
        return Holder.direct(getSoundEvent(soundId));
    }

    @Nullable
    public static UUID getSoundId(ResourceLocation resourceLocation) {
        if (!resourceLocation.getNamespace().equals(AudioPlayer.MODID)) {
            return null;
        }
        String path = resourceLocation.getPath();
        if (!path.startsWith(PATH_PREFIX) || !path.endsWith(PATH_SUFFIX)) {
            return null;
        }
        String uuidPart = path.substring(PATH_PREFIX.length(), path.length() - PATH_SUFFIX.length());
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static UUID getSoundIdFromEventLocation(ResourceLocation resourceLocation) {
        if (!resourceLocation.getNamespace().equals(AudioPlayer.MODID)) {
            return null;
        }
        String path = resourceLocation.getPath();
        if (!path.startsWith(LOCATION_PREFIX)) {
            return null;
        }
        String uuidPart = path.substring(LOCATION_PREFIX.length());
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}

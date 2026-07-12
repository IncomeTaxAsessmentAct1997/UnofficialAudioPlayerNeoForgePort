package com.duncanjones.apneoforge.mixin;

import com.duncanjones.apneoforge.ChannelHolder;
import com.duncanjones.apneoforge.CustomSound;
import com.duncanjones.apneoforge.CustomSoundHolder;
import com.duncanjones.apneoforge.PlayerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(SkullBlockEntity.class)
public class SkullBlockEntityMixin extends BlockEntity implements CustomSoundHolder, ChannelHolder {

    @Unique
    @Nullable
    private UUID channelID;

    @Unique
    @Nullable
    private CustomSound customSound;

    public SkullBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Nullable
    @Override
    public UUID audioplayer$getChannelID() {
        return channelID;
    }

    @Override
    public void audioplayer$setChannelID(@Nullable UUID channelID) {
        this.channelID = channelID;
        setChanged();
    }

    @Nullable
    @Override
    public CustomSound audioplayer$getCustomSound() {
        return customSound;
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void saveAdditional(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (channelID != null) {
            tag.putUUID("ChannelID", channelID);
        }
        if (customSound != null) {
            customSound.saveToNbt(tag);
        }
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (tag.contains("ChannelID")) {
            channelID = tag.getUUID("ChannelID");
        } else {
            channelID = null;
        }
        customSound = CustomSound.of(tag);
    }

    @Override
    public void setRemoved() {
        if (channelID != null) {
            PlayerManager.instance().stop(channelID);
        }
        super.setRemoved();
    }
}

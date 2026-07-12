package com.duncanjones.apneoforge;

import javax.annotation.Nullable;
import java.util.UUID;

public interface ChannelHolder {

    @Nullable
    UUID audioplayer$getChannelID();

    void audioplayer$setChannelID(@Nullable UUID channelID);

}



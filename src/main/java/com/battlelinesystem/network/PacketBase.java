package com.battlelinesystem.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public abstract class PacketBase {
    public abstract void write(FriendlyByteBuf buf);
    public abstract boolean handle(NetworkEvent.Context context);
}

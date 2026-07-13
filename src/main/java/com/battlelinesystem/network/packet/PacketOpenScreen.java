package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class PacketOpenScreen extends PacketBase {
    public final int screenType;
    public final boolean isOp;
    public final int[] modeCounts;
    public final int countdownSeconds;
    public final int countA;
    public final int countB;

    public PacketOpenScreen() {
        this.screenType = 0;
        this.isOp = false;
        this.modeCounts = new int[4];
        this.countdownSeconds = 0;
        this.countA = 0;
        this.countB = 0;
    }

    public PacketOpenScreen(FriendlyByteBuf buf) {
        this.screenType = buf.readVarInt();
        this.isOp = buf.readBoolean();
        this.modeCounts = new int[4];
        for (int i = 0; i < 4; i++) this.modeCounts[i] = buf.readVarInt();
        this.countdownSeconds = buf.readVarInt();
        this.countA = buf.readVarInt();
        this.countB = buf.readVarInt();
    }

    public PacketOpenScreen(int screenType, boolean isOp, int[] modeCounts, int countdownSeconds) {
        this(screenType, isOp, modeCounts, countdownSeconds, 0, 0);
    }

    public PacketOpenScreen(int screenType, boolean isOp, int[] modeCounts, int countdownSeconds, int countA, int countB) {
        this.screenType = screenType;
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
        this.countA = countA;
        this.countB = countB;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(screenType);
        buf.writeBoolean(isOp);
        for (int i = 0; i < 4; i++) buf.writeVarInt(modeCounts[i]);
        buf.writeVarInt(countdownSeconds);
        buf.writeVarInt(countA);
        buf.writeVarInt(countB);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(0, this));
        return true;
    }
}

package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.PacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

public class PacketCapturePointViewTeleportRaw extends PacketBase {
    public final int x, y, z;

    public PacketCapturePointViewTeleportRaw() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public PacketCapturePointViewTeleportRaw(FriendlyByteBuf buf) {
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
    }

    public PacketCapturePointViewTeleportRaw(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public PacketCapturePointViewTeleportRaw(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(player.serverLevel(),
                    x + 0.5, 319, z + 0.5, 180, 90);
        });
        return true;
    }
}

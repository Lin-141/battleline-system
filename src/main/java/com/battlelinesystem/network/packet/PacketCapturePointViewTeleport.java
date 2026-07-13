package com.battlelinesystem.network.packet;

import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

public class PacketCapturePointViewTeleport extends PacketBase {
    public final String capturePointName;

    public PacketCapturePointViewTeleport() {
        this.capturePointName = null;
    }

    public PacketCapturePointViewTeleport(FriendlyByteBuf buf) {
        String n = buf.readUtf();
        this.capturePointName = n.isEmpty() ? null : n;
    }

    public PacketCapturePointViewTeleport(String capturePointName) {
        this.capturePointName = capturePointName;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(capturePointName != null ? capturePointName : "");
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            if (capturePointName == null) return;
            MapConfig mapConfig = FactionManager.getInstance().getActiveMapConfig();
            if (mapConfig == null || mapConfig.capturePoints == null) return;
            for (MapConfig.CapturePoint cp : mapConfig.capturePoints) {
                if (capturePointName.equals(cp.name)) {
                    net.minecraft.core.BlockPos center = cp.getDisplayCenter();
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(player.serverLevel(),
                            center.getX() + 0.5, 319, center.getZ() + 0.5,
                            180, 90);
                    break;
                }
            }
        });
        return true;
    }
}

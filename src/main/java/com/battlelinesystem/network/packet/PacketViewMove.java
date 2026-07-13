package com.battlelinesystem.network.packet;

import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：RTS风格视角移动请求
 * 在旁观者模式下，鼠标推到屏幕边缘时发送此包来移动高空视角
 */
public class PacketViewMove extends PacketBase {
    public final double dx, dz;
    public boolean exit;

    public PacketViewMove() { dx = 0; dz = 0; exit = false; }

    public PacketViewMove(FriendlyByteBuf buf) {
        dx = buf.readDouble();
        dz = buf.readDouble();
        exit = buf.readBoolean();
    }

    public PacketViewMove(double dx, double dz) {
        this.dx = dx; this.dz = dz; this.exit = false;
    }

    public static PacketViewMove exitView() {
        PacketViewMove p = new PacketViewMove();
        p.exit = true; return p;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(dx);
        buf.writeDouble(dz);
        buf.writeBoolean(exit);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) return true;
        context.enqueueWork(() -> {
            if (exit) {
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                return;
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) return;
            if (dx == 0 && dz == 0) return;

            double newX = player.getX() + dx;
            double newZ = player.getZ() + dz;

            // 检查目标位置是否在战场边界内
            MapConfig cfg = FactionManager.getInstance().getActiveMapConfig();
            if (cfg != null && !cfg.isInsideBattlefield(newX, newZ)) return;

            player.teleportTo(player.serverLevel(),
                    newX, player.getY(), newZ,
                    player.getYRot(), player.getXRot());
        });
        return true;
    }
}

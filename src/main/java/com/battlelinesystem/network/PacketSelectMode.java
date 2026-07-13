package com.battlelinesystem.network;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.game.GameModeManager;
import com.battlelinesystem.game.ModeCountdownManager;
import com.battlelinesystem.network.packet.PacketOpenScreen;
import com.battlelinesystem.world.GameWorldManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class PacketSelectMode extends PacketBase {

    private String modeName;

    public PacketSelectMode() {}

    public PacketSelectMode(FriendlyByteBuf buf) {
        this.modeName = buf.readUtf();
    }

    public PacketSelectMode(String modeName) {
        this.modeName = modeName;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(modeName);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            GameModeManager gmm = GameModeManager.getInstance();
            ModeCountdownManager cdm = ModeCountdownManager.getInstance();

            if (cdm.isFinished()) {
                player.sendSystemMessage(Component.literal("模式投票已结束！"));
                return;
            }

            String prev = gmm.selectMode(player.getUUID(), modeName);
            if (prev == null) {
                player.sendSystemMessage(Component.literal("你选择了 " + modeName + " 模式"));
            } else {
                player.sendSystemMessage(Component.literal("已切换至 " + modeName + " 模式"));
            }

            cdm.startIfNeeded();

            net.minecraft.server.MinecraftServer server = player.getServer();
            int onlineCount = server.getPlayerList().getPlayerCount();
            int votedCount = gmm.getTotalCount();

            if (votedCount >= onlineCount) {
                cdm.forceFinish();

                String winner = gmm.getWinningMode();
                BattleLineSystem.LOGGER.info("全票通过，胜出模式：{}", winner);

                List<PacketTimeUp.MapEntry> mapEntries = new java.util.ArrayList<>();
                for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, winner)) {
                    mapEntries.add(PacketTimeUp.MapEntry.from(info.config, info.id));
                }

                BattleLineSystem.LOGGER.info("全票通过，胜出模式：{}，找到 {} 个地图", winner, mapEntries.size());

                PacketTimeUp timeUpPacket = new PacketTimeUp(winner, mapEntries);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p), timeUpPacket);
                }
            } else {
                broadcastRefresh(player);
            }
        });
        return true;
    }

    public static void broadcastRefresh(ServerPlayer triggerPlayer) {
        GameModeManager gmm = GameModeManager.getInstance();
        ModeCountdownManager cdm = ModeCountdownManager.getInstance();

        int[] counts = new int[GameModeManager.MODE_NAMES.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = gmm.getPlayerCount(GameModeManager.MODE_NAMES[i]);
        }
        int sec = cdm.getRemainingSeconds();

        for (ServerPlayer p : triggerPlayer.getServer().getPlayerList().getPlayers()) {
            boolean isOp = p.hasPermissions(2);
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                    new PacketOpenScreen(0, isOp, counts, sec));
        }
    }
}

package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class PacketTeamSelect extends PacketBase {
    public final String team; // "A" or "B"

    public PacketTeamSelect() {
        this.team = "";
    }

    public PacketTeamSelect(FriendlyByteBuf buf) {
        this.team = buf.readUtf();
    }

    public PacketTeamSelect(String team) {
        this.team = team;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(team);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            try {
                MapConfig config = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
                if (config == null) {
                    BattleLineSystem.LOGGER.warn("PacketTeamSelect: no active map config");
                    return;
                }
                String teamName = "A".equals(team) ? "A" : "B";

                BattleLineSystem.LOGGER.info("Player {} selected team {}",
                        player.getName().getString(), teamName);

                // === 人数平衡检查：该队人数不能比另一队多2人及以上 ===
                com.battlelinesystem.game.CapturePointManager cpm =
                        com.battlelinesystem.game.CapturePointManager.getInstance();
                int[] counts = countTeamPlayers(player.getServer());
                int otherCount = "A".equals(teamName) ? counts[1] : counts[0];
                int thisCount = "A".equals(teamName) ? counts[0] : counts[1];
                if (thisCount >= otherCount + 2) {
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§c该队伍人数已满，请加入另一队"));
                    return;
                }

                // 记录/更新玩家队伍
                cpm.setPlayerTeam(player.getUUID(), teamName);
                cpm.syncToVanillaScoreboard(player);

                // 向所有玩家广播最新的队伍人数
                broadcastTeamCounts(player.getServer());
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketTeamSelect error", e);
            }
        });
        return true;
    }

    /** 统计AB队当前人数，返回 [countA, countB] */
    private int[] countTeamPlayers(MinecraftServer server) {
        int countA = 0, countB = 0;
        if (server == null) return new int[]{0, 0};
        com.battlelinesystem.game.CapturePointManager cpm =
                com.battlelinesystem.game.CapturePointManager.getInstance();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String team = cpm.getPlayerTeam(sp.getUUID());
            if ("A".equals(team)) countA++;
            else if ("B".equals(team)) countB++;
        }
        return new int[]{countA, countB};
    }

    /** 向所有在游戏维度的玩家广播最新的队伍人数 */
    private void broadcastTeamCounts(MinecraftServer server) {
        if (server == null) return;
        int[] counts = countTeamPlayers(server);
        int cd = com.battlelinesystem.game.SelectionCountdownManager.getInstance().getTeamCountdown();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.level().dimension().location().getNamespace().equals(
                    com.battlelinesystem.BattleLineSystem.MOD_ID)) continue;
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp),
                    new PacketOpenScreen(1, false, new int[]{0,0,0,0}, cd, counts[0], counts[1]));
        }
    }
}

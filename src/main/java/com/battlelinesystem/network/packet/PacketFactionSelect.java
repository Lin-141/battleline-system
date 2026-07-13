package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;

public class PacketFactionSelect extends PacketBase {
    public final String factionId;

    public PacketFactionSelect() {
        this.factionId = "";
    }

    public PacketFactionSelect(FriendlyByteBuf buf) {
        this.factionId = buf.readUtf();
    }

    public PacketFactionSelect(String factionId) {
        this.factionId = factionId;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionId);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            try {
                BattleLineSystem.LOGGER.info("PacketFactionSelect: player {} chose faction {}",
                        player.getName().getString(), factionId);
                com.battlelinesystem.game.CapturePointManager cpm =
                        com.battlelinesystem.game.CapturePointManager.getInstance();
                FactionManager mgr = FactionManager.getInstance();
                FactionConfig fc = mgr.getFaction(factionId);
                if (fc == null) {
                    BattleLineSystem.LOGGER.warn("PacketFactionSelect: faction not found: {}", factionId);
                    return;
                }

                // 记录该队伍的阵营投票（不倒计时终止，等30秒到期后统一处理）
                String team = cpm.getPlayerTeam(player.getUUID());
                if (team == null) return;
                cpm.setTeamFaction(team, factionId);

                // 通知投票者
                String teamColor = "A".equals(team) ? "§b" : "§c";
                String teamName = "A".equals(team) ? "A队" : "B队";
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        teamColor + "[" + teamName + "] §e你投票了阵营: " + fc.name +
                        " §7（等待倒计时结束）"));

                // 通知同队其他玩家投票状态
                MinecraftServer srv = player.getServer();
                if (srv != null) {
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        String pt = cpm.getPlayerTeam(p.getUUID());
                        if (team.equals(pt) && p != player) {
                            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    teamColor + "[" + teamName + "] §e" +
                                    player.getName().getString() + " 投票了阵营: " + fc.name));
                        }
                    }
                }
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketFactionSelect error", e);
            }
        });
        return true;
    }
}

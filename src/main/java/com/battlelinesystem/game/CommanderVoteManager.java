package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketOpenClassVote;
import com.battlelinesystem.network.packet.PacketOpenCommanderVote;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * 指挥官投票管理器 — 每队30秒投票选出指挥官
 *
 * 流程：
 *   1. teamVoting 初始化为 false，当本队第一个玩家选队后激活
 *   2. 托管30秒倒计时（每秒运行一次），投票阶段允许任意玩家投票
 *   3. 0秒到期后选定得票最多的玩家为指挥官，关闭投票阶段
 *   4. 投票结束 → 发送 PacketOpenClassVote 进入下一阶段
 */
public class CommanderVoteManager {

    private static final CommanderVoteManager INSTANCE = new CommanderVoteManager();
    public static final int VOTE_DURATION = 10;

    /** 队伍级别的投票阶段，key=team */
    private final Map<String, TeamVote> teamVotes = new HashMap<>();

    private CommanderVoteManager() {}

    public static CommanderVoteManager getInstance() { return INSTANCE; }

    // ---- 对外接口 ----

    /**
     * 某队伍第一个玩家选队完成后调用，此时开始收集指挥官投票。
     * 向该队伍所有在线玩家打开 CommanderVoteScreen
     */
    public void startVote(String team, MinecraftServer server) {
        TeamVote tv = teamVotes.get(team);
        if (tv == null) {
            tv = new TeamVote(team, VOTE_DURATION);
            teamVotes.put(team, tv);
            BattleLineSystem.LOGGER.info("[指挥官投票] {}队投票启动 {}秒", team, VOTE_DURATION);
        }
        // 向该队所有玩家广播
        sendVoteScreenToTeam(team, server, tv.remaining);
    }

    /** 单个玩家加入时，补发投票屏幕 */
    public void sendVoteScreenToPlayer(String team, ServerPlayer player) {
        TeamVote tv = teamVotes.get(team);
        if (tv == null || tv.commanderName != null) return;
        List<String> names = getTeamPlayerNames(team, player.getServer());
        AllPackets.getChannel().send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenCommanderVote(names, team, tv.remaining));
    }

    /** 收到玩家投票（PacketCommanderVote handle调用） */
    public void receiveVote(String team, UUID voterUuid, String targetName) {
        TeamVote tv = teamVotes.get(team);
        if (tv == null) return;
        tv.votes.put(voterUuid, targetName);
        BattleLineSystem.LOGGER.info("[指挥官投票] {}队 {} 投给 {}", team,
                voterUuid.toString().substring(0, 8), targetName);
    }

    /** 检查投票是否还在进行中 */
    public boolean isVoting(String team) {
        TeamVote tv = teamVotes.get(team);
        return tv != null && tv.commanderName == null && tv.remaining > 0;
    }

    /** 检查指挥官的PlayerName，null=未选举 */
    public String getCommander(String team) {
        TeamVote tv = teamVotes.get(team);
        return tv != null ? tv.commanderName : null;
    }

    /** 强制设置指挥官（弹劾替换等场景） */
    public void setCommander(String team, String commanderName) {
        TeamVote tv = teamVotes.get(team);
        if (tv == null) {
            tv = new TeamVote(team, 0);
            teamVotes.put(team, tv);
        }
        tv.commanderName = commanderName;
        tv.remaining = 0;
        BattleLineSystem.LOGGER.info("[指挥官] {}队指挥官已设置为: {}", team, commanderName);
    }

    /**
     * 每tick调用 — 内部自行1秒控速
     */
    public void tick(MinecraftServer server) {
        List<String> finishedTeams = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var entry : teamVotes.entrySet()) {
            TeamVote tv = entry.getValue();
            if (tv.commanderName != null) continue; // 已选定
            if (now - tv.lastTick < 1000) continue;
            tv.lastTick = now;
            tv.remaining--;
            if (tv.remaining <= 0) {
                tv.remaining = 0;
                tv.commanderName = tallyWinner(tv);
                finishedTeams.add(entry.getKey());
            } else {
                broadcastRemaining(entry.getKey(), server, tv.remaining);
            }
        }
        for (String team : finishedTeams) {
            onVoteFinished(team, server);
        }
    }

    /** 向该队所有在线玩家广播当前剩余秒数 */
    private void broadcastRemaining(String team, MinecraftServer server, int remaining) {
        List<String> names = getTeamPlayerNames(team, server);
        var pkt = new PacketOpenCommanderVote(names, team, remaining);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = com.battlelinesystem.game.CapturePointManager.getInstance()
                    .getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp), pkt);
            }
        }
    }

    public void reset() {
        teamVotes.clear();
    }

    // ---- 内部 ----

    private String tallyWinner(TeamVote tv) {
        if (tv.votes.isEmpty()) return null;
        Map<String, Integer> counts = new HashMap<>();
        for (String target : tv.votes.values()) {
            counts.merge(target, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void sendVoteScreenToTeam(String team, MinecraftServer server, int remaining) {
        List<String> names = getTeamPlayerNames(team, server);
        var pkt = new PacketOpenCommanderVote(names, team, remaining);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = com.battlelinesystem.game.CapturePointManager.getInstance()
                    .getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp), pkt);
            }
        }
    }

    private List<String> getTeamPlayerNames(String team, MinecraftServer server) {
        List<String> names = new ArrayList<>();
        if (server == null) return names;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = com.battlelinesystem.game.CapturePointManager.getInstance()
                    .getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                names.add(sp.getName().getString());
            }
        }
        return names;
    }

    /** 投票结束 → 向队伍玩家广播指挥官 + 发送职业选择 */
    private void onVoteFinished(String team, MinecraftServer server) {
        TeamVote tv = teamVotes.get(team);
        String winner = tv != null ? tv.commanderName : null;

        // 如果没人投票，从该队在线玩家中随机选一个当指挥官
        if (winner == null) {
            List<String> names = getTeamPlayerNames(team, server);
            if (!names.isEmpty()) {
                winner = names.get(new Random().nextInt(names.size()));
                if (tv != null) tv.commanderName = winner;
            }
        }
        String announce = winner != null ? winner : "无人";
        BattleLineSystem.LOGGER.info("[指挥官投票] {}队指挥官已选定: {}", team, announce);

        // 获取当前阵营配置
        com.battlelinesystem.game.CapturePointManager cpm =
                com.battlelinesystem.game.CapturePointManager.getInstance();
        String factionId = cpm.getTeamFaction(team);
        com.battlelinesystem.faction.FactionConfig fc = null;
        if (factionId != null) {
            fc = com.battlelinesystem.faction.FactionManager.getInstance().getFaction(factionId);
        }

        // 广播结果 + 发送职业选择
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = cpm.getPlayerTeam(sp.getUUID());
            if (!team.equals(pt)) continue;

            sp.sendSystemMessage(Component.literal(
                    "§6[指挥官] §f" + announce + " §7当选为 §f" + team + "队 §7指挥官！"));

            // 发送职业选择包
            if (fc != null && fc.classes != null && !fc.classes.isEmpty()) {
                PacketOpenClassVote pkt = new PacketOpenClassVote(
                        fc.id, fc.name, fc.displayColor,
                        (byte)("A".equals(team) ? 0 : 1),
                        new ArrayList<>(fc.classes), fc.vehicles);
                pkt.totalPlayers = CapturePointManager.countTeamPlayers(server, team);
                pkt.looseSpawn = fc.looseSpawn;
                // 设置双方阵营名称
                {
                    com.battlelinesystem.faction.FactionManager fmg = com.battlelinesystem.faction.FactionManager.getInstance();
                    com.battlelinesystem.faction.FactionConfig ta = fmg.getFaction(cpm.getTeamFaction("A"));
                    com.battlelinesystem.faction.FactionConfig tb = fmg.getFaction(cpm.getTeamFaction("B"));
                    pkt.teamAName = ta != null ? ta.name : "A队";
                    pkt.teamBName = tb != null ? tb.name : "B队";
                }
                for (ServerPlayer ssp : server.getPlayerList().getPlayers()) {
                    String spt = cpm.getPlayerTeam(ssp.getUUID());
                    if (team.equals(spt)) pkt.sameTeamUUIDs.add(ssp.getUUID());
                }
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp), pkt);
            } else {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp),
                        new PacketOpenClassVote("", "", "",
                                (byte)("A".equals(team) ? 0 : 1), new ArrayList<>(), null));
            }
        }
    }

    // ---- 内部数据结构 ----

    private static class TeamVote {
        final String team;
        int remaining;
        long lastTick = System.currentTimeMillis();
        /** voterUuid → targetPlayerName */
        final Map<UUID, String> votes = new LinkedHashMap<>();
        String commanderName; // null=未选定

        TeamVote(String team, int remaining) {
            this.team = team;
            this.remaining = remaining;
        }
    }
}

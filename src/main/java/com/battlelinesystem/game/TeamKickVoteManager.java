package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketOpenClassVote;
import com.battlelinesystem.network.packet.PacketOpenFactionVote;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * 踢出队伍投票管理器 — 同队玩家投票将某个队友踢到对面队伍。
 * 投票期 30 秒，同意 > 反对即踢出。结束后冷却 3 分钟。
 */
public class TeamKickVoteManager {

    private static final TeamKickVoteManager INSTANCE = new TeamKickVoteManager();
    private static final int VOTE_DURATION = 30;
    private static final long COOLDOWN_MS = 180_000; // 3 分钟

    private final Map<String, KickVote> activeVotes = new HashMap<>();
    /** 队伍 -> 冷却结束时间戳 */
    private final Map<String, Long> cooldowns = new HashMap<>();
    private long lastTick;

    private TeamKickVoteManager() {}

    public static TeamKickVoteManager getInstance() { return INSTANCE; }

    // ---- 查询 ----

    public boolean hasActive(String team) {
        KickVote kv = activeVotes.get(team);
        return kv != null && kv.remaining > 0 && !kv.finished;
    }

    public int getCooldownSeconds(String team) {
        Long end = cooldowns.get(team);
        if (end == null) return -1;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(team);
            return -1;
        }
        return (int) (remaining / 1000);
    }

    // ---- 发起投票 ----

    public boolean startKickVote(String team, UUID initiatorUuid, String initiatorName,
                                  UUID targetUuid, String targetName, String reason,
                                  MinecraftServer server) {
        if (hasActive(team)) return false;
        if (initiatorName.equals(targetName)) return false;

        int cd = getCooldownSeconds(team);
        if (cd > 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt) && sp.getUUID().equals(initiatorUuid)) {
                    sp.sendSystemMessage(Component.literal("§c踢出投票冷却中，请等待 " + cd + " 秒"));
                }
            }
            return false;
        }

        KickVote kv = new KickVote(team, initiatorUuid, initiatorName,
                targetUuid, targetName, VOTE_DURATION);
        activeVotes.put(team, kv);

        String reasonLine = (reason != null && !reason.isEmpty())
                ? " §7理由: §f" + reason : "";
        Component msg = Component.literal("")
                .append(Component.literal("§6[踢出投票] §f" + initiatorName
                        + " §7发起投票，将 §c" + targetName
                        + " §7踢到对面队伍。" + reasonLine + " "))
                .append(Component.literal("[§a同意§f]")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/bls kickplayervote agree"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§a点击投票同意踢出")))))
                .append(Component.literal(" "))
                .append(Component.literal("[§c反对§f]")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/bls kickplayervote disagree"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§c点击投票反对踢出")))))
                .append(Component.literal(" §7(" + VOTE_DURATION + "秒)"));

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                sp.sendSystemMessage(msg);
            }
        }

        BattleLineSystem.LOGGER.info("[踢出投票] {}队 {} 发起踢出 {}",
                team, initiatorName, targetName);
        return true;
    }

    // ---- 投票 ----

    public VoteResult vote(String team, UUID voterUuid, String voterName, boolean agree) {
        KickVote kv = activeVotes.get(team);
        if (kv == null || kv.finished) return VoteResult.NOT_ACTIVE;

        // 被踢目标不能投票
        if (voterName.equals(kv.targetName)) return VoteResult.NOT_IN_TEAM;

        if (kv.votes.containsKey(voterUuid)) return VoteResult.ALREADY_VOTED;
        kv.votes.put(voterUuid, agree);

        BattleLineSystem.LOGGER.info("[踢出投票] {}队 {} 投票: {}",
                team, voterName, agree ? "同意" : "反对");
        return VoteResult.OK;
    }

    // ---- tick ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 1000) return;
        lastTick = now;

        List<String> finished = new ArrayList<>();
        for (var entry : activeVotes.entrySet()) {
            KickVote kv = entry.getValue();
            if (kv.finished) continue;
            kv.remaining--;
            if (kv.remaining <= 0) {
                kv.remaining = 0;
                kv.finished = true;
                finished.add(entry.getKey());
            }
        }

        for (String team : finished) {
            finishKickVote(team, server);
        }
    }

    public void reset() {
        activeVotes.clear();
        cooldowns.clear();
        lastTick = 0;
    }

    // ---- 内部 ----

    private void finishKickVote(String team, MinecraftServer server) {
        KickVote kv = activeVotes.get(team);
        if (kv == null) return;

        int agree = 0, disagree = 0;
        for (boolean v : kv.votes.values()) {
            if (v) agree++; else disagree++;
        }

        cooldowns.put(team, System.currentTimeMillis() + COOLDOWN_MS);

        ServerPlayer target = server.getPlayerList().getPlayer(kv.targetUuid);
        if (agree > disagree && agree > 0 && target != null) {
            // 踢出成功 → 跳到对面队伍
            switchToOppositeTeam(target, team);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt)) {
                    sp.sendSystemMessage(Component.literal(
                            "§6[踢出投票] §a投票通过！§c" + kv.targetName
                                    + " §f被踢到对面队伍。(同意 " + agree + " 反对 " + disagree + ")"));
                }
            }
            if (target != null) {
                target.sendSystemMessage(Component.literal(
                        "§c你被队友投票踢到了对面队伍！"));
            }
            BattleLineSystem.LOGGER.info("[踢出投票] {}队踢出 {} 成功 (同意{} 反对{})",
                    team, kv.targetName, agree, disagree);
        } else {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt)) {
                    sp.sendSystemMessage(Component.literal(
                            "§6[踢出投票] §c投票未通过！§f" + kv.targetName
                                    + " §7留在本队。(同意 " + agree + " 反对 " + disagree + ")"));
                }
            }
            BattleLineSystem.LOGGER.info("[踢出投票] {}队踢出 {} 失败 (同意{} 反对{})",
                    team, kv.targetName, agree, disagree);
        }
    }

    /** 将玩家切换到对面队伍，清除装备并发配对应界面 */
    static void switchToOppositeTeam(ServerPlayer target, String oldTeam) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        FactionManager fm = FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();
        if (config == null) return;

        String newTeam = "A".equals(oldTeam) ? "B" : "A";

        // 人数平衡检查
        int countA = 0, countB = 0;
        for (net.minecraft.server.level.ServerPlayer sp :
                target.getServer().getPlayerList().getPlayers()) {
            String t = cpm.getPlayerTeam(sp.getUUID());
            if ("A".equals(t)) countA++;
            else if ("B".equals(t)) countB++;
        }
        int thisCount = "A".equals(newTeam) ? countA : countB;
        int otherCount = "A".equals(newTeam) ? countB : countA;
        if (thisCount >= otherCount + 2) {
            target.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c目标队伍人数已满，跳边取消"));
            return;
        }

        // 释放限定职业槽
        com.battlelinesystem.network.NetworkManager.removePlayerFromClassSelections(target.getUUID());
        cpm.setPlayerTeam(target.getUUID(), newTeam);
        cpm.setPlayerFaction(target.getUUID(), null);
        cpm.setPlayerClass(target.getUUID(), null);
        cpm.syncToVanillaScoreboard(target);

        // 更新战绩统计
        var statsMap = cpm.getWorldPlayerStats(target.serverLevel().dimension());
        if (statsMap != null) {
            PlayerGameStats s = statsMap.get(target.getUUID());
            if (s != null) s.team = newTeam;
        }

        // 清空背包
        target.getInventory().clearContent();
        target.inventoryMenu.broadcastChanges();

        // 设为观察者传送到高处
        target.setGameMode(GameType.SPECTATOR);
        ServerLevel gameWorld = target.serverLevel();
        target.teleportTo(gameWorld,
                config.getTeamASpawn().getX() + 0.5, 319,
                config.getTeamASpawn().getZ() + 0.5, 180, 90);

        MinecraftServer server = target.getServer();
        if (server == null) return;

        // 检查目标队伍是否已有阵营
        String teamFactionId = cpm.getTeamFaction(newTeam);
        if (teamFactionId != null) {
            FactionConfig teamFc = fm.getFaction(teamFactionId);
            if (teamFc != null) {
                cpm.setPlayerFaction(target.getUUID(), teamFactionId);
                cpm.syncToVanillaScoreboard(target);
                if (teamFc.classes != null && !teamFc.classes.isEmpty()) {
                    PacketOpenClassVote pkt = new PacketOpenClassVote(
                            teamFactionId, teamFc.name, teamFc.displayColor,
                            (byte) ("A".equals(newTeam) ? 0 : 1),
                            new ArrayList<>(teamFc.classes), teamFc.vehicles);
                    pkt.totalPlayers = CapturePointManager.countTeamPlayers(server, newTeam);
                    pkt.looseSpawn = teamFc.looseSpawn;
                    // 设置双方阵营名称
                    com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(cpm.getTeamFaction("A"));
                    com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(cpm.getTeamFaction("B"));
                    pkt.teamAName = (ta != null) ? ta.name : "A队";
                    pkt.teamBName = (tb != null) ? tb.name : "B队";
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        if (newTeam.equals(cpm.getPlayerTeam(sp.getUUID())))
                            pkt.sameTeamUUIDs.add(sp.getUUID());
                    }
                    AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> target), pkt);
                    CapturePointManager.playSoundToPlayer(target, "battlelinesystem:waiting_01");
                }
                return;
            }
        }

        // 打开阵营选择界面
        List<String> rawPool = "A".equals(newTeam)
                ? config.factionPoolA : config.factionPoolB;
        List<String> pool = rawPool != null ? rawPool : new ArrayList<>();
        List<FactionConfig> allActive = new ArrayList<>(fm.getActiveMapFactions());
        if (!pool.isEmpty()) allActive.removeIf(fc -> !pool.contains(fc.id));
        AllPackets.getChannel().send(
                PacketDistributor.PLAYER.with(() -> target),
                new PacketOpenFactionVote(allActive,
                        "A".equals(newTeam) ? pool : new ArrayList<>(),
                        "B".equals(newTeam) ? pool : new ArrayList<>()));
    }

    // ---- 数据结构 ----

    public enum VoteResult { OK, NOT_ACTIVE, NOT_IN_TEAM, ALREADY_VOTED }

    private static class KickVote {
        final String team;
        final UUID initiatorUuid;
        final String initiatorName;
        final UUID targetUuid;
        final String targetName;
        int remaining;
        final Map<UUID, Boolean> votes = new LinkedHashMap<>();
        boolean finished;

        KickVote(String team, UUID initiatorUuid, String initiatorName,
                 UUID targetUuid, String targetName, int remaining) {
            this.team = team;
            this.initiatorUuid = initiatorUuid;
            this.initiatorName = initiatorName;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.remaining = remaining;
        }
    }
}

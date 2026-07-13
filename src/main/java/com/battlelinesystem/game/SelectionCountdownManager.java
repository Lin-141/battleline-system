package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * 选择阶段倒计时管理器 — 队伍选择和阵营选择各10秒，到期自动分配。
 */
public class SelectionCountdownManager {

    private static final SelectionCountdownManager INSTANCE = new SelectionCountdownManager();

    public static final int TEAM_COUNTDOWN = 10;
    public static final int FACTION_COUNTDOWN = 10;

    /** 队伍选择倒计时剩余秒数，-1=未激活 */
    private int teamCountdown = -1;
    /** 阵营选择倒计时（按队伍），-1=未激活 */
    private final Map<String, Integer> factionCountdown = new HashMap<>();

    private boolean teamFinished = false;
    /** 内部tick计数器，每20tick（=1秒）执行一次倒计时减1 */
    private int internalTick = 0;

    private SelectionCountdownManager() {}

    public static SelectionCountdownManager getInstance() { return INSTANCE; }

    public void startTeamCountdown() {
        teamCountdown = TEAM_COUNTDOWN;
        teamFinished = false;
        BattleLineSystem.LOGGER.info("[SelectionCD] 队伍选择倒计时启动 {}秒", TEAM_COUNTDOWN);
    }

    public void startFactionCountdown(String team) {
        factionCountdown.put(team, FACTION_COUNTDOWN);
        BattleLineSystem.LOGGER.info("[SelectionCD] 阵营选择倒计时启动 team={} {}秒", team, FACTION_COUNTDOWN);
    }

    public void stopFactionCountdown(String team) {
        factionCountdown.remove(team);
    }

    public int getTeamCountdown() { return teamCountdown; }
    public int getFactionCountdown(String team) {
        return factionCountdown.getOrDefault(team, -1);
    }

    public void reset() {
        teamCountdown = -1;
        teamFinished = false;
        factionCountdown.clear();
        internalTick = 0;
    }

    /**
     * 每 tick 调用。内部自行控速为每秒一次。返回需要处理的事件列表。
     */
    public TickResult tick(MinecraftServer server) {
        internalTick++;
        if (internalTick % 20 != 0) return new TickResult();

        TickResult result = new TickResult();

        // === 队伍选择倒计时 ===
        if (teamCountdown > 0) {
            teamCountdown--;
            if (teamCountdown <= 0) {
                teamCountdown = 0;
                teamFinished = true;
                result.teamExpired = true;
            }
        }

        // === 阵营选择倒计时 ===
        List<String> expiredFactions = new ArrayList<>();
        for (var entry : factionCountdown.entrySet()) {
            int newVal = entry.getValue() - 1;
            if (newVal <= 0) {
                expiredFactions.add(entry.getKey());
            } else {
                entry.setValue(newVal);
            }
        }
        for (String t : expiredFactions) {
            factionCountdown.remove(t);
            result.expiredFactions.add(t);
        }

        return result;
    }

    public static class TickResult {
        public boolean teamExpired = false;
        public final List<String> expiredFactions = new ArrayList<>();
    }
}

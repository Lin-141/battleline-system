package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;

import java.util.*;

/**
 * 游戏模式管理器 - 追踪每个模式有多少玩家选择（服务端单例）
 */
public class GameModeManager {

    /** 预定义的四种模式名称及描述 */
    public static final String[] MODE_NAMES = {"征服", "突破", "生存", "大规模行动"};
    public static final String[] MODE_DESCS = {
            "征服模式：两支队伍进行据点争夺，同时占领多个据点的一方获胜。",
            "突破模式：进攻方逐个推进据点，防守方层层阻击，战线逐步推进。",
            "生存模式：在固定时间内存活，时间结束时存活人数最多的队伍获胜。",
            "大规模行动：多阶段大型战役，融合征服与突破模式于一体，跨越多张地图展开全面战争。"
    };

    private static final GameModeManager INSTANCE = new GameModeManager();

    /** 每个模式对应的已选择玩家UUID集合 */
    private final Map<String, Set<UUID>> modePlayers = new LinkedHashMap<>();

    private GameModeManager() {
        for (String name : MODE_NAMES) {
            modePlayers.put(name, new LinkedHashSet<>());
        }
    }

    public static GameModeManager getInstance() {
        return INSTANCE;
    }

    /**
     * 玩家选择一个模式。返回之前选择的模式名（null 表示首次选择）。
     */
    public String selectMode(UUID playerId, String modeName) {
        // 从旧模式中移除
        String prev = null;
        for (Map.Entry<String, Set<UUID>> entry : modePlayers.entrySet()) {
            if (entry.getValue().remove(playerId)) {
                prev = entry.getKey();
                break;
            }
        }
        // 加入新模式
        Set<UUID> set = modePlayers.get(modeName);
        if (set != null) {
            set.add(playerId);
            BattleLineSystem.LOGGER.debug("{} chose mode: {}", playerId, modeName);
        }
        return prev;
    }

    /**
     * 获取指定模式的已选择玩家数
     */
    public int getPlayerCount(String modeName) {
        Set<UUID> set = modePlayers.get(modeName);
        return set != null ? set.size() : 0;
    }

    /**
     * 获取总选择人数
     */
    public int getTotalCount() {
        int total = 0;
        for (Set<UUID> set : modePlayers.values()) {
            total += set.size();
        }
        return total;
    }

    /**
     * 玩家离开时清除其选择
     */
    public void removePlayer(UUID playerId) {
        for (Set<UUID> set : modePlayers.values()) {
            set.remove(playerId);
        }
    }

    /**
     * 重置所有选择
     */
    public void resetAll() {
        for (Set<UUID> set : modePlayers.values()) {
            set.clear();
        }
    }

    /**
     * 获取所有模式名称（按顺序）
     */
    public String[] getModeNames() {
        return MODE_NAMES;
    }

    /**
     * 返回得票最多的模式名称。平票时选排在 MODE_NAMES 数组中靠前的。
     * 无人投票时返回第一个模式。
     */
    public String getWinningMode() {
        String best = MODE_NAMES[0];
        int bestCount = 0;
        for (String name : MODE_NAMES) {
            int c = getPlayerCount(name);
            if (c > bestCount) {
                bestCount = c;
                best = name;
            }
        }
        return best;
    }
}

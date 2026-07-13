package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * 据点占领逻辑（服务端单例）
 */
public class CapturePointManager {

    private static final CapturePointManager INSTANCE = new CapturePointManager();

    /** 玩家 -> 队伍 ("A" or "B") */
    private final Map<UUID, String> playerTeams = new HashMap<>();

    /** 玩家 -> 职业ID（用于查死亡扣费） */
    private final Map<UUID, String> playerClasses = new HashMap<>();

    /** 玩家 -> 阵营ID（用于死亡后重开职业选择） */
    private final Map<UUID, String> playerFactions = new HashMap<>();

    /** 队伍 -> 阵营ID（队伍级别的阵营选择，一人选全队用） */
    private final Map<String, String> teamFaction = new HashMap<>();

    /** 当前在据点区域内的玩家（用于占领音效触发） */
    private final Set<UUID> playersInCaptureZone = new HashSet<>();

    /** 当前正在听占领音效的玩家（离开时发送停止包） */
    private final Set<UUID> hearingCaptureSound = new HashSet<>();

    /** 世界维度 -> 据点状态列表 */
    private final Map<ResourceKey<Level>, List<CaptureState>> worldStates = new HashMap<>();

    /** 世界维度 -> 上次tick时间(ms)，用于计算delta */
    private final Map<ResourceKey<Level>, Long> lastTick = new HashMap<>();

    /** 世界维度 -> A队当前分数 */
    private final Map<ResourceKey<Level>, Integer> scoreA = new HashMap<>();
    /** 世界维度 -> B队当前分数 */
    private final Map<ResourceKey<Level>, Integer> scoreB = new HashMap<>();

    /** 同步tick计数器 */
    private int syncTick = 0;

    /** 世界维度 -> 全局脚本列表 */
    private final Map<ResourceKey<Level>, List<MapConfig.GlobalScript>> worldGlobalScripts = new HashMap<>();
    /** 世界维度 -> 已执行的全局脚本索引集合 */
    private final Map<ResourceKey<Level>, Set<Integer>> executedGlobalScripts = new HashMap<>();
    /** 世界维度 -> 游戏tick计数（用于时间类触发器，20tick=1秒） */
    private final Map<ResourceKey<Level>, Integer> worldTickCount = new HashMap<>();

    /** 世界维度 -> 首次部署是否已发生（用于 first_deploy 全局脚本触发器） */
    private final Map<ResourceKey<Level>, Boolean> firstDeployHappened = new HashMap<>();

    /** 世界维度 -> 近结束音乐是否已播放 */
    private final Map<ResourceKey<Level>, Boolean> nearEndMusicPlayed = new HashMap<>();

    /** 世界维度 -> 已注册的信标实体UUID集合（避免每次全量扫描） */
    private final Map<ResourceKey<Level>, Set<UUID>> worldBeaconUUIDs = new HashMap<>();

    /** 世界维度 -> 已揭示给客户端的据点名称集合（用于渐进式解锁显示） */
    private final Map<ResourceKey<Level>, java.util.Set<String>> revealedPointNames = new HashMap<>();

    // ---- 玩家战绩统计 ----
    /** 世界维度 -> (玩家UUID -> 战绩) */
    private final Map<ResourceKey<Level>, Map<UUID, PlayerGameStats>> worldPlayerStats = new HashMap<>();

    /** 游戏结束标志 */
    private static volatile String gameOverWinner = null;

    /** 玩家死亡时间戳（ms），用于部署冷却（5秒） */
    private final Map<UUID, Long> deathTimestamps = new HashMap<>();
    public static final int DEPLOY_COOLDOWN_MS = 5000;
    private static volatile ResourceKey<Level> gameOverWorldKey = null;
    /** 游戏已进入结算流程，阻止新的 gameOver 触发 */
    private static volatile boolean gameFinalized = false;

    /** 队伍名称（由客户端 HUD 设置，服务端用于结算） */
    private static String teamAName = "A队";
    private static String teamBName = "B队";

    public static String getTeamAName() { return teamAName; }
    public static String getTeamBName() { return teamBName; }
    public static void setTeamNames(String a, String b) { teamAName = a; teamBName = b; }

    public static CapturePointManager getInstance() { return INSTANCE; }

    private CapturePointManager() {}

    /** 标记结算流程开始，阻止新的 gameOver */
    public static void markGameFinalized() {
        gameFinalized = true;
        BattleLineSystem.LOGGER.info("[GameOver] 标记结算流程开始 gameFinalized=true");
    }

    /** 结算完成后重置 */
    public static void resetGameFinalized() {
        gameFinalized = false;
        BattleLineSystem.LOGGER.info("[GameOver] 重置结算标记 gameFinalized=false");
    }

    /** 检查是否游戏结束，返回胜者队伍("A"或"B")或null */
    public static String pollGameOverWinner() {
        String w = gameOverWinner;
        gameOverWinner = null;
        return w;
    }

    /** 获取游戏结束的世界key */
    public static ResourceKey<Level> pollGameOverWorldKey() {
        ResourceKey<Level> k = gameOverWorldKey;
        gameOverWorldKey = null;
        return k;
    }

    // ---- 队伍分配 ----

    public void setPlayerTeam(UUID uuid, String team) {
        playerTeams.put(uuid, team);
    }

    public String getPlayerTeam(UUID uuid) {
        return playerTeams.get(uuid);
    }

    public String getPlayerFaction(UUID uuid) {
        return playerFactions.get(uuid);
    }

    /** 记录玩家选择的职业ID */
    public void setPlayerClass(UUID uuid, String classId) {
        playerClasses.put(uuid, classId);
    }

    /** 获取玩家当前职业ID（可能为 null） */
    public String getPlayerClass(UUID uuid) {
        return playerClasses.get(uuid);
    }

    /** 记录玩家选择的阵营ID */
    public void setPlayerFaction(UUID uuid, String factionId) {
        playerFactions.put(uuid, factionId);
    }

    /** 记录玩家死亡时间，用于部署冷却 */
    public void recordDeathTimestamp(UUID uuid) {
        deathTimestamps.put(uuid, System.currentTimeMillis());
    }

    /** 获取玩家部署冷却剩余毫秒数，0=冷却已过或无死亡记录 */
    public int getDeployCooldownRemainingMs(UUID uuid) {
        Long deathTime = deathTimestamps.get(uuid);
        if (deathTime == null) return 0;
        long remaining = DEPLOY_COOLDOWN_MS - (System.currentTimeMillis() - deathTime);
        return (int) Math.max(0, remaining);
    }

    /** 注册信标实体UUID，创建信标实体时调用 */
    public void registerBeaconUUID(ResourceKey<Level> worldKey, UUID uuid) {
        worldBeaconUUIDs.computeIfAbsent(worldKey, k -> new HashSet<>()).add(uuid);
    }

    /** 注销信标实体UUID，移除信标时调用 */
    public void unregisterBeaconUUID(ResourceKey<Level> worldKey, UUID uuid) {
        Set<UUID> set = worldBeaconUUIDs.get(worldKey);
        if (set != null) set.remove(uuid);
    }

    public void removePlayer(UUID uuid) {
        playerTeams.remove(uuid);
        playerClasses.remove(uuid);
        playerFactions.remove(uuid);
        deathTimestamps.remove(uuid);
    }

    /** 清除玩家的原版计分板队伍和阵营tag（用于玩家离开或游戏重置） */
    public void clearVanillaScoreboard(ServerPlayer sp) {
        net.minecraft.world.scores.Scoreboard sb = sp.serverLevel().getScoreboard();
        String name = sp.getGameProfile().getName();
        PlayerTeam currentTeam = sb.getPlayersTeam(name);
        if (currentTeam != null) {
            sb.removePlayerFromTeam(name, currentTeam);
        }
        sp.getTags().removeIf(t -> t.startsWith("faction_"));
    }

    /** 同步玩家队伍和阵营到原版计分板，使 @a[team=A]、@a[tag=faction_xxx] 可用 */
    public void syncToVanillaScoreboard(ServerPlayer sp) {
        String team = playerTeams.get(sp.getUUID());
        String factionId = playerFactions.get(sp.getUUID());
        net.minecraft.world.scores.Scoreboard sb = sp.serverLevel().getScoreboard();
        String name = sp.getGameProfile().getName();

        // 只从当前所在队伍移除
        PlayerTeam currentTeam = sb.getPlayersTeam(name);
        if (currentTeam != null) {
            sb.removePlayerFromTeam(name, currentTeam);
        }
        // 加入对应队伍
        if (team != null) {
            PlayerTeam pt = sb.getPlayerTeam(team);
            if (pt == null) pt = sb.addPlayerTeam(team);
            sb.addPlayerToTeam(name, pt);
        }

        // 清理旧阵营tag，设为当前阵营
        sp.getTags().removeIf(t -> t.startsWith("faction_"));
        if (factionId != null) {
            sp.getTags().add("faction_" + factionId);
        }
    }

    /** 设置队伍级别的阵营 */
    public void setTeamFaction(String team, String factionId) {
        teamFaction.put(team, factionId);
    }

    /** 获取队伍级别的阵营 */
    public String getTeamFaction(String team) {
        return teamFaction.get(team);
    }

    /**
     * 获取指定世界当前已揭示的据点列表（渐进式解锁显示）。
     * 供 PacketSelectMap 等使用，确保新部署的玩家只看到当前已解锁的据点。
     */
    public List<MapConfig.CapturePoint> getRevealedPointsForWorld(ResourceKey<Level> worldKey) {
        List<CaptureState> states = worldStates.get(worldKey);
        if (states == null || states.isEmpty()) return java.util.Collections.emptyList();
        return getRevealedPoints(states);
    }

    // ---- 据点初始化 ----

    public void init(ResourceKey<Level> worldKey, MapConfig config) {
        List<CaptureState> states = new ArrayList<>();
        if (config.capturePoints != null) {
            for (MapConfig.CapturePoint cp : config.capturePoints) {
                CaptureState st = new CaptureState(cp);
                // 读取初始归属配置
                if ("A".equals(cp.initialOwner)) {
                    st.owner = "A";
                    st.progress = -1f;
                } else if ("B".equals(cp.initialOwner)) {
                    st.owner = "B";
                    st.progress = 1f;
                }
                states.add(st);
            }
        }
        worldStates.put(worldKey, states);
        lastTick.put(worldKey, System.currentTimeMillis());
        int initScore = config.initialScore > 0 ? config.initialScore : 200;
        scoreA.put(worldKey, initScore);
        scoreB.put(worldKey, initScore);
        // 重置揭示状态
        revealedPointNames.remove(worldKey);
        // deep copy global scripts for execution tracking
        if (config.globalScripts != null && !config.globalScripts.isEmpty()) {
            List<MapConfig.GlobalScript> gsList = new ArrayList<>();
            for (MapConfig.GlobalScript orig : config.globalScripts) {
                MapConfig.GlobalScript gs = new MapConfig.GlobalScript();
                gs.trigger = orig.trigger;
                gs.value = orig.value;
                gs.team = orig.team;
                gs.commands = new ArrayList<>(orig.commands);
                gsList.add(gs);
            }
            worldGlobalScripts.put(worldKey, gsList);
            executedGlobalScripts.put(worldKey, new HashSet<>());
        }
        worldTickCount.put(worldKey, 0);
        firstDeployHappened.put(worldKey, false);
        BattleLineSystem.LOGGER.info("[据点初始化] worldKey={}, 据点数={}, 初始分数={}",
                worldKey.location(), states.size(), initScore);
        for (CaptureState s : states) {
            StringBuilder sb = new StringBuilder();
            for (int[][] z : s.cp.zones) {
                sb.append(" [").append(z[0][0]).append(",").append(z[0][1]).append(",").append(z[0][2])
                  .append(" ~ ").append(z[1][0]).append(",").append(z[1][1]).append(",").append(z[1][2]).append("]");
            }
            BattleLineSystem.LOGGER.info("[据点] name={}, zones={}", s.cp.name, sb.toString());
        }
    }

    public void cleanup(ResourceKey<Level> worldKey) {
        worldStates.remove(worldKey);
        lastTick.remove(worldKey);
        scoreA.remove(worldKey);
        scoreB.remove(worldKey);
        worldGlobalScripts.remove(worldKey);
        executedGlobalScripts.remove(worldKey);
        worldTickCount.remove(worldKey);
        firstDeployHappened.remove(worldKey);
        nearEndMusicPlayed.remove(worldKey);
        worldBeaconUUIDs.remove(worldKey);
        worldPlayerStats.remove(worldKey);
        teamFaction.clear();
    }

    /** 获取指定世界的 A 队分数 */
    public int getScoreA(ResourceKey<Level> worldKey) {
        return scoreA.getOrDefault(worldKey, 0);
    }

    /** 获取指定世界的 B 队分数 */
    public int getScoreB(ResourceKey<Level> worldKey) {
        return scoreB.getOrDefault(worldKey, 0);
    }

    /** 设置指定队伍分数 */
    public void setScore(ResourceKey<Level> worldKey, String team, int value) {
        if ("A".equals(team)) {
            scoreA.put(worldKey, Math.max(0, value));
        } else if ("B".equals(team)) {
            scoreB.put(worldKey, Math.max(0, value));
        }
    }

    /** 增减指定队伍分数 */
    public void addScore(ResourceKey<Level> worldKey, String team, int delta) {
        if ("A".equals(team)) {
            int cur = scoreA.getOrDefault(worldKey, 200);
            scoreA.put(worldKey, Math.max(0, cur + delta));
        } else if ("B".equals(team)) {
            int cur = scoreB.getOrDefault(worldKey, 200);
            scoreB.put(worldKey, Math.max(0, cur + delta));
        }
    }

    /** 标记首次部署已发生（由 PacketClassSelect 在玩家部署时调用） */
    public void markFirstDeploy(ResourceKey<Level> worldKey) {
        firstDeployHappened.put(worldKey, true);
    }

    /** 强制指定队伍获胜（将对方分数设为0） */
    public void forceWin(ResourceKey<Level> worldKey, String team) {
        if ("A".equals(team)) {
            scoreB.put(worldKey, 0);
            gameOverWinner = "A";
        } else if ("B".equals(team)) {
            scoreA.put(worldKey, 0);
            gameOverWinner = "B";
        }
        gameOverWorldKey = worldKey;
        BattleLineSystem.LOGGER.info("强制结束游戏！胜者: {}", team);
    }

    /** 解析 ResourceLocation 格式的音效路径，无效时返回 null */
    private static net.minecraft.resources.ResourceLocation parseSound(String soundPath) {
        if (soundPath == null || soundPath.isEmpty()) return null;
        int colon = soundPath.indexOf(':');
        if (colon < 0) return null;
        return new net.minecraft.resources.ResourceLocation(
                soundPath.substring(0, colon), soundPath.substring(colon + 1));
    }

    /** 向世界内所有玩家播放音效 */
    public static void playSoundToWorld(ServerLevel world, String soundPath) {
        net.minecraft.resources.ResourceLocation rl = parseSound(soundPath);
        if (rl == null) return;
        try {
            net.minecraft.sounds.SoundEvent se = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(rl);
            if (se == null) {
                BattleLineSystem.LOGGER.warn("[音乐调试] SoundEvent未注册: {}", soundPath);
                return;
            }
            int played = 0;
            for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
                if (p.level() == world) {
                    world.playSeededSound(null,
                            p.getX(), p.getY(), p.getZ(),
                            se, net.minecraft.sounds.SoundSource.RECORDS,
                            1.0f, 1.0f,
                            world.random.nextLong());
                    played++;
                }
            }
            BattleLineSystem.LOGGER.info("[音乐调试] playSoundToWorld 完毕 path={} 播放人数={}",
                    soundPath, played);
        } catch (Exception e) {
            BattleLineSystem.LOGGER.warn("播放音效失败: {} {}", soundPath, e.getMessage());
        }
    }

    /** 停止全世界的指定音效 */
    public static void stopSoundToWorld(ServerLevel world, String soundPath) {
        net.minecraft.resources.ResourceLocation rl = parseSound(soundPath);
        if (rl == null) return;
        try {
            for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
                if (p.level() == world) {
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                            rl, net.minecraft.sounds.SoundSource.RECORDS));
                }
            }
        } catch (Exception e) {
            BattleLineSystem.LOGGER.warn("停止音效失败: {} {}", soundPath, e.getMessage());
        }
    }

    /** 统计指定队伍在当前服务器上的玩家人数 */
    public static int countTeamPlayers(net.minecraft.server.MinecraftServer server, String team) {
        int count = 0;
        if (team == null) return 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (team.equals(getInstance().getPlayerTeam(p.getUUID()))) {
                count++;
            }
        }
        return count;
    }

    /** 同步队友发光效果 — 统计每队人数，队伍人数>1则发光 */
    private void syncPlayerGlowing(ServerLevel world, List<ServerPlayer> allPlayers) {
        Map<String, Integer> teamCount = new HashMap<>();
        for (ServerPlayer p : allPlayers) {
            if (p.level() != world) continue;
            String t = playerTeams.get(p.getUUID());
            if (t != null) teamCount.merge(t, 1, Integer::sum);
        }
        for (ServerPlayer p : allPlayers) {
            if (p.level() != world) continue;
            String myTeam = playerTeams.get(p.getUUID());
            p.setGlowingTag(myTeam != null && teamCount.getOrDefault(myTeam, 0) > 1);
        }
    }

    /** 向指定玩家播放音效 */
    public static void playSoundToPlayer(ServerPlayer player, String soundPath) {
        net.minecraft.resources.ResourceLocation rl = parseSound(soundPath);
        if (rl == null) return;
        try {
            var holder = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getHolder(rl);
            if (holder.isEmpty()) {
                BattleLineSystem.LOGGER.warn("[音乐调试] playSoundToPlayer SoundEvent未注册: {}", soundPath);
                return;
            }
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    holder.get(), net.minecraft.sounds.SoundSource.RECORDS,
                    player.getX(), player.getY(), player.getZ(),
                    1.0f, 1.0f, player.level().random.nextLong()));
        } catch (Exception e) {
            BattleLineSystem.LOGGER.warn("播放音效失败: {} {}", soundPath, e.getMessage());
        }
    }

    // ---- 主逻辑：服务端 tick 调用 ----

    public void tick(ServerLevel world) {
        ResourceKey<Level> key = world.dimension();
        List<CaptureState> states = worldStates.get(key);
        if (states == null || states.isEmpty()) return;

        long now = System.currentTimeMillis();
        Long prev = lastTick.get(key);
        if (prev == null) { lastTick.put(key, now); return; }
        float dt = (now - prev) / 1000f;
        if (dt <= 0 || dt > 1f) dt = 0.05f;
        lastTick.put(key, now);

        List<ServerPlayer> players = world.getServer().getPlayerList().getPlayers();
        syncPlayerGlowing(world, players);

        boolean shouldLog = syncTick % 40 == 0;
        int playersInWorld = 0, playersWithTeam = 0;
        for (ServerPlayer p : players) {
            if (p.level() != world) continue;
            playersInWorld++;
            if (playerTeams.get(p.getUUID()) != null) playersWithTeam++;
        }
        if (shouldLog) {
            BattleLineSystem.LOGGER.info("[据点tick] states={}, 本世界玩家={}, 有队伍={}",
                    states.size(), playersInWorld, playersWithTeam);
        }

        Set<UUID> currentCapturePlayers = tickCaptureStates(world, key, states, players, dt, shouldLog);
        tickCaptureZoneSound(world, currentCapturePlayers);

        syncTick++;
        if (syncTick % 5 == 0) broadcastProgress(world, states);
        if (syncTick == 1 || syncTick % 20 == 0) syncBeaconEntities(world);

        tickGameOverCheck(world, key);
        int sa = scoreA.getOrDefault(key, 200);
        int sb = scoreB.getOrDefault(key, 200);
        tickNearEndMusic(world, key, sa, sb);
        tickGlobalScripts(world, key, sa, sb, gameOverWinner, gameOverWorldKey);
    }

    /** 处理所有据点占领进度（返回需要播放音效的玩家集合） */
    private Set<UUID> tickCaptureStates(ServerLevel world, ResourceKey<Level> key,
                                         List<CaptureState> states, List<ServerPlayer> players,
                                         float dt, boolean shouldLog) {
        Set<UUID> currentCapturePlayers = new HashSet<>();
        for (CaptureState state : states) {
            String prevOwner = state.owner;
            int teamA = 0, teamB = 0;
            Set<UUID> statePlayers = new HashSet<>();

            for (ServerPlayer p : players) {
                if (p.level() != world) continue;
                String team = playerTeams.get(p.getUUID());
                if (team == null) continue;

                if (isInside(p.blockPosition(), state.cp)) {
                    statePlayers.add(p.getUUID());
                    if ("A".equals(team)) teamA++;
                    else if ("B".equals(team)) teamB++;
                }
            }

            // 统计据点区域内带team=A或team=B的实体（非玩家）
            if (state.cp.zones != null) {
                for (int[][] z : state.cp.zones) {
                    int x1 = z[0][0], y1 = z[0][1], z1 = z[0][2];
                    int x2 = z[1][0], y2 = z[1][1], z2 = z[1][2];
                    AABB box = new AABB(
                            Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                            Math.max(x1, x2) + 1.0, Math.max(y1, y2) + 1.0, Math.max(z1, z2) + 1.0);
                    for (Entity e : world.getEntitiesOfClass(Entity.class, box,
                            e -> !(e instanceof ServerPlayer) && e.getTeam() != null)) {
                        PlayerTeam pt = (PlayerTeam) e.getTeam();
                        if ("A".equals(pt.getName())) teamA++;
                        else if ("B".equals(pt.getName())) teamB++;
                    }
                }
            }

            if (shouldLog && (teamA > 0 || teamB > 0)) {
                BattleLineSystem.LOGGER.info("[据点tick] {} A={} B={} progress={:.2f} owner={}",
                        state.cp.name, teamA, teamB, state.progress, state.owner);
            }

            // 记录当前据点内的玩家人数（用于广播到客户端）
            state.teamACount = teamA;
            state.teamBCount = teamB;

            // 检查前置据点：当前占领方可防守（不受前置限制），进攻方需逐个夺回前置
            boolean aCanCapture = allPrerequisitesMetForTeam(state.cp, states, "A");
            boolean bCanCapture = allPrerequisitesMetForTeam(state.cp, states, "B");
            if (!"A".equals(state.owner) && !aCanCapture) teamA = 0;
            if (!"B".equals(state.owner) && !bCanCapture) teamB = 0;
            if (teamA == 0 && teamB == 0) {
                state.capturingTeam = null;
                continue;
            }

            // 已被占领且是其他据点的前置据点 → 检查进攻方权限
            if (state.owner != null && isPrerequisite(state.cp.name, states)) {
                String attacker = state.cp.attackerTeam;
                if (attacker == null || attacker.isEmpty() || attacker.equals(state.owner)) {
                    // 无进攻方或进攻方=占领方 → 完全锁定，不可夺回
                    continue;
                }
                // 有进攻方且进攻方≠占领方 → 仅允许进攻方参与占领，占领方仍可防守
                if ("A".equals(attacker) && !"B".equals(state.owner)) teamB = 0;
                else if ("B".equals(attacker) && !"A".equals(state.owner)) teamA = 0;
            }

            // 计算占领速度
            if (teamA > 0 && teamB == 0) {
                // A队占领
                float rate = captureRate(teamA);
                state.progress -= rate * dt;
                if (state.progress < -1f) state.progress = -1f;
                state.capturingTeam = "A";
            } else if (teamB > 0 && teamA == 0) {
                // B队占领
                float rate = captureRate(teamB);
                state.progress += rate * dt;
                if (state.progress > 1f) state.progress = 1f;
                state.capturingTeam = "B";
            } else if (teamA > 0 && teamB > 0) {
                // 双方争抢 → 进度向人数多的一方偏移
                float netRate = captureRate(teamB) - captureRate(teamA);
                state.progress += netRate * dt * 0.5f;  // 减半，避免太快
                if (state.progress > 1f) state.progress = 1f;
                if (state.progress < -1f) state.progress = -1f;
                state.capturingTeam = "contested";
            } else if (state.owner == null) {
                // 无人在区域内且未被完全占领 → 进度缓慢回退
                if (state.progress > 0) {
                    state.progress = Math.max(0, state.progress - 0.02f * dt);
                } else if (state.progress < 0) {
                    state.progress = Math.min(0, state.progress + 0.02f * dt);
                }
                if (Math.abs(state.progress) < 0.001f) {
                    state.progress = 0;
                }
                state.capturingTeam = null;
            }
            // 已完全占领 + 无人在内 → 保持不动

            // 活跃占领中的据点 → 收集内部玩家用于播放占领音效
            if (state.capturingTeam != null) {
                currentCapturePlayers.addAll(statePlayers);
            }

            // 标记占领完成
            if (state.progress <= -1f) {
                state.owner = "A";
                state.progress = -1f;
                state.capturingTeam = null;
            } else if (state.progress >= 1f) {
                state.owner = "B";
                state.progress = 1f;
                state.capturingTeam = null;
            }

            // 占领完成时播放音效
            if (state.owner != null && !state.owner.equals(prevOwner)) {
                playCaptureSound(world, state.owner, players);
                // 执行脚本 + 摧毁区域
                runScripts(world, state, state.owner.equals("A") ? state.cp.scripts.onCaptureA : state.cp.scripts.onCaptureB);
                destroyRegions(world, state);
                // 记录占点贡献
                for (ServerPlayer p : players) {
                    if (p.level() != world) continue;
                    if (isInside(p.blockPosition(), state.cp)) {
                        recordCapture(key, p.getUUID());
                    }
                }
            }
            // 失去占领时执行脚本
            if (state.owner == null && prevOwner != null) {
                runScripts(world, state, state.cp.scripts.onUncapture);
            }
        }
        return currentCapturePlayers;
    }

    /** 管理占领区域内播放/停止占领等待音效 */
    private void tickCaptureZoneSound(ServerLevel world, Set<UUID> currentCapturePlayers) {
        net.minecraft.resources.ResourceLocation stopRl =
                new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "pointwaiting");
        for (UUID uuid : currentCapturePlayers) {
            if (!hearingCaptureSound.contains(uuid)) {
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(uuid);
                if (sp != null) {
                    playSoundToPlayer(sp, "battlelinesystem:pointwaiting");
                }
            }
        }
        for (UUID uuid : hearingCaptureSound) {
            if (!currentCapturePlayers.contains(uuid)) {
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(uuid);
                if (sp != null) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                            stopRl, net.minecraft.sounds.SoundSource.RECORDS));
                }
            }
        }
        hearingCaptureSound.clear();
        hearingCaptureSound.addAll(currentCapturePlayers);
        playersInCaptureZone.clear();
        playersInCaptureZone.addAll(currentCapturePlayers);
    }

    /** 检查游戏结束条件（分数归零和时限到达） */
    private void tickGameOverCheck(ServerLevel world, ResourceKey<Level> key) {
        int sa = scoreA.getOrDefault(key, 200);
        int sb = scoreB.getOrDefault(key, 200);
        if (!gameFinalized && gameOverWinner == null) {
            if (sa <= 0 || sb <= 0) {
                gameOverWinner = sa <= 0 ? "B" : "A";
                gameOverWorldKey = key;
                BattleLineSystem.LOGGER.info("[GameOver] 游戏结束！胜者: {} world={}", gameOverWinner, key.location());
                return;
            }
            MapConfig cfg = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
            if (cfg != null && cfg.timeLimitMinutes > 0 && cfg.timeUpRule != null && !cfg.timeUpRule.isEmpty()) {
                int ticks = worldTickCount.getOrDefault(key, 0);
                int elapsed = ticks / 20;
                if (elapsed >= cfg.timeLimitMinutes * 60) {
                    if ("score".equals(cfg.timeUpRule)) {
                        if (sa > sb) gameOverWinner = "A";
                        else if (sb > sa) gameOverWinner = "B";
                        else gameOverWinner = "draw";
                    } else {
                        gameOverWinner = cfg.timeUpRule;
                    }
                    gameOverWorldKey = key;
                    BattleLineSystem.LOGGER.info("[GameOver] 时间到！胜者: {} rule={} world={}",
                            gameOverWinner, cfg.timeUpRule, key.location());
                }
            }
        }
    }

    /** 检查并播放濒临结束音乐 */
    private void tickNearEndMusic(ServerLevel world, ResourceKey<Level> key, int sa, int sb) {
        MapConfig config = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
        if (config == null || config.nearEndThreshold <= 0 || config.nearEndMusic == null || config.nearEndMusic.isEmpty())
            return;

        boolean alreadyPlayed = nearEndMusicPlayed.getOrDefault(key, false);
        if (alreadyPlayed) return;

        if (sa <= config.nearEndThreshold || sb <= config.nearEndThreshold) {
            nearEndMusicPlayed.put(key, true);
            playSoundToWorld(world, config.nearEndMusic);
            BattleLineSystem.LOGGER.info("[音乐] 濒临结束: {}", config.nearEndMusic);
        }
    }

    /** 检查并执行符合条件的全局脚本 */
    private void tickGlobalScripts(ServerLevel world, ResourceKey<Level> key, int scoreA, int scoreB,
                                    String gameOverWinner, ResourceKey<Level> gameOverWorldKey) {
        List<MapConfig.GlobalScript> gsList = worldGlobalScripts.get(key);
        if (gsList == null || gsList.isEmpty()) return;

        Set<Integer> executed = executedGlobalScripts.computeIfAbsent(key, k -> new HashSet<>());
        int ticks = worldTickCount.merge(key, 1, Integer::sum);
        int gameTimeSeconds = ticks / 20;
        boolean hasDeploy = firstDeployHappened.getOrDefault(key, false);

        MinecraftServer server = world.getServer();
        CommandSourceStack source = server.createCommandSourceStack();

        for (int i = 0; i < gsList.size(); i++) {
            if (executed.contains(i)) continue;
            MapConfig.GlobalScript gs = gsList.get(i);

            boolean trigger = false;
            switch (gs.trigger) {
                case "game_start" -> {
                    if (ticks == 1) trigger = true;
                }
                case "game_end" -> {
                    if (gameOverWinner != null && key.equals(gameOverWorldKey)) trigger = true;
                }
                case "first_deploy" -> {
                    if (hasDeploy) trigger = true;
                }
                case "score_ge" -> {
                    int s = "A".equals(gs.team) ? scoreA : scoreB;
                    if (s >= gs.value) trigger = true;
                }
                case "score_le" -> {
                    int s = "A".equals(gs.team) ? scoreA : scoreB;
                    if (s <= gs.value) trigger = true;
                }
                case "time_ge" -> {
                    if (gameTimeSeconds >= gs.value) trigger = true;
                }
            }

            if (trigger) {
                executed.add(i);
                BattleLineSystem.LOGGER.info("[全局脚本] 触发 #{}: {} {} {}", i, gs.trigger, gs.value, gs.team);
                for (String cmd : gs.commands) {
                    if (cmd.isBlank()) continue;
                    server.getCommands().performPrefixedCommand(source, cmd);
                }
            }
        }
    }

    /** 检查某据点对指定队伍的前置据点是否全部被该队占领（防止跳点） */
    private boolean allPrerequisitesMetForTeam(MapConfig.CapturePoint cp, List<CaptureState> states, String team) {
        if (cp.prerequisites == null || cp.prerequisites.isEmpty()) return true;
        for (String prereqName : cp.prerequisites) {
            CaptureState s = findStateByName(prereqName, states);
            if (s == null || !team.equals(s.owner)) return false;
        }
        return true;
    }

    /**
     * 计算「已揭示」的据点：所有前置据点都是根据点（无前置）或已被占领的据点。
     * 用于客户端渐进式显示，未揭示的据点不会发送给客户端。
     */
    private List<MapConfig.CapturePoint> getRevealedPoints(List<CaptureState> states) {
        List<MapConfig.CapturePoint> revealed = new ArrayList<>();
        for (CaptureState s : states) {
            if (s.cp.prerequisites == null || s.cp.prerequisites.isEmpty()) {
                revealed.add(s.cp);
                continue;
            }
            // 所有前置据点必须满足：已被占领 或 是根据点
            boolean allClear = true;
            for (String prereqName : s.cp.prerequisites) {
                CaptureState prereq = findStateByName(prereqName, states);
                if (prereq == null) { allClear = false; break; }
                if (prereq.owner != null) continue; // 已被占领
                if (prereq.cp.prerequisites == null || prereq.cp.prerequisites.isEmpty()) continue; // 根据点
                allClear = false;
                break;
            }
            if (allClear) {
                revealed.add(s.cp);
            }
        }
        return revealed;
    }

    private CaptureState findStateByName(String name, List<CaptureState> states) {
        for (CaptureState s : states) {
            if (s.cp.name.equals(name)) return s;
        }
        return null;
    }

    /** 检查据点是否被其他据点设为前置据点（即占领后不可夺回） */
    private boolean isPrerequisite(String cpName, List<CaptureState> states) {
        for (CaptureState s : states) {
            if (s.cp.prerequisites != null && s.cp.prerequisites.contains(cpName)) {
                return true;
            }
        }
        return false;
    }

    /** 玩家死亡扣分（由死亡事件调用） */
    public void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        ServerLevel world = player.serverLevel();
        ResourceKey<Level> key = world.dimension();
        String team = playerTeams.get(player.getUUID());
        if (team == null) return;

        // 查职业配置获取死亡扣费
        int cost = 1;
        String classId = playerClasses.get(player.getUUID());
        if (classId != null) {
            ClassConfig cls = findClassConfig(classId);
            if (cls != null) cost = cls.deathCost;
        }
        if (cost <= 0) return;

        if ("A".equals(team)) {
            int s = scoreA.getOrDefault(key, 200) - cost;
            scoreA.put(key, Math.max(0, s));
        } else if ("B".equals(team)) {
            int s = scoreB.getOrDefault(key, 200) - cost;
            scoreB.put(key, Math.max(0, s));
        }
    }

    private ClassConfig findClassConfig(String classId) {
        var factions = FactionManager.getInstance().getActiveMapFactions();
        for (var fc : factions) {
            if (fc.classes != null) {
                for (ClassConfig c : fc.classes) {
                    if (c.id.equals(classId)) return c;
                }
            }
        }
        return null;
    }

    /** 执行脚本命令 */
    private void runScripts(ServerLevel world, CaptureState state, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        MinecraftServer server = world.getServer();
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack();
        for (String cmd : commands) {
            if (cmd == null || cmd.trim().isEmpty()) continue;
            try {
                server.getCommands().performPrefixedCommand(source, cmd.trim());
            } catch (Exception e) {
                BattleLineSystem.LOGGER.warn("据点脚本执行失败: {}", e.getMessage());
            }
        }
    }

    /** 摧毁配置的删除区域 */
    private void destroyRegions(ServerLevel world, CaptureState state) {
        if (state.cp.destroyRegions == null || state.cp.destroyRegions.isEmpty()) return;
        for (int[][] region : state.cp.destroyRegions) {
            if (region.length < 2) continue;
            int[] min = region[0], max = region[1];
            for (int x = min[0]; x <= max[0]; x++) {
                for (int y = min[1]; y <= max[1]; y++) {
                    for (int z = min[2]; z <= max[2]; z++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        net.minecraft.world.level.block.state.BlockState bs = world.getBlockState(bp);
                        if (!bs.isAir()) {
                            world.destroyBlock(bp, false);
                        }
                    }
                }
            }
        }
    }

    private void broadcastProgress(ServerLevel world, List<CaptureState> states) {
        ResourceKey<Level> key = world.dimension();

        // 检测是否有新据点被揭示（渐进式解锁显示）
        java.util.Set<String> revealed = revealedPointNames.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>());
        List<MapConfig.CapturePoint> currentRevealed = getRevealedPoints(states);
        boolean hasNewReveal = false;
        for (MapConfig.CapturePoint cp : currentRevealed) {
            if (revealed.add(cp.name)) {
                hasNewReveal = true;
            }
        }

        // 有新解锁的据点 → 完整同步据点列表给所有玩家
        if (hasNewReveal) {
            var syncPacket = new com.battlelinesystem.network.packet.PacketSyncCapturePoints(currentRevealed);
            for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
                if (p.level() == world) {
                    com.battlelinesystem.network.AllPackets.getChannel().send(
                            PacketDistributor.PLAYER.with(() -> p), syncPacket);
                }
            }
        }

        List<com.battlelinesystem.network.packet.PacketCapturePointProgress.CaptureEntry> entries = new ArrayList<>();
        for (CaptureState s : states) {
            byte owner = 0;
            if ("A".equals(s.owner)) owner = 1;
            else if ("B".equals(s.owner)) owner = 2;
            byte capturing = 0;
            if ("A".equals(s.capturingTeam)) capturing = 1;
            else if ("B".equals(s.capturingTeam)) capturing = 2;
            else if ("contested".equals(s.capturingTeam)) capturing = 3;
            boolean locked = !allPrerequisitesMetForTeam(s.cp, states, "A") 
                          && !allPrerequisitesMetForTeam(s.cp, states, "B");
            entries.add(new com.battlelinesystem.network.packet.PacketCapturePointProgress.CaptureEntry(
                    s.cp.name, s.progress, owner, capturing, s.teamACount, s.teamBCount, locked));
        }
        int sa = scoreA.getOrDefault(key, 200);
        int sb = scoreB.getOrDefault(key, 200);
        MapConfig activeCfg = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
        int timeLimit = activeCfg != null ? activeCfg.timeLimitMinutes : 0;
        int elapsed = worldTickCount.getOrDefault(key, 0) / 20;
        String upRule = activeCfg != null && activeCfg.timeUpRule != null ? activeCfg.timeUpRule : "";
        var pkt = new com.battlelinesystem.network.packet.PacketCapturePointProgress(entries, sa, sb,
                timeLimit, elapsed, upRule);
        for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
            if (p.level() == world) {
                com.battlelinesystem.network.AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> p), pkt);
            }
        }
    }

    /** 占领速率：1人=20秒 → rate=0.05/秒，每多一人+10% */
    private float captureRate(int playerCount) {
        return 0.05f * (1f + 0.1f * (playerCount - 1));
    }

    /** 同步已注册的信标实体到客户端（只遍历注册集合，不扫描全量实体） */
    private void syncBeaconEntities(ServerLevel world) {
        ResourceKey<Level> key = world.dimension();
        Set<UUID> uuidSet = worldBeaconUUIDs.get(key);
        List<com.battlelinesystem.network.packet.PacketSyncBeaconEntities.BeaconEntry> entries = new ArrayList<>();
        Set<UUID> dead = new HashSet<>();

        if (uuidSet != null && !uuidSet.isEmpty()) {
            for (UUID uid : uuidSet) {
                net.minecraft.world.entity.Entity e = world.getEntity(uid);
                if (e == null || !e.isAlive()) {
                    dead.add(uid);
                    continue;
                }
                var data = e.getPersistentData();
                if (data.getBoolean("spawn_beacon")) {
                    String team = data.contains("spawn_beacon_team") ? data.getString("spawn_beacon_team") : null;
                    entries.add(new com.battlelinesystem.network.packet.PacketSyncBeaconEntities.BeaconEntry(
                            uid, e.getX(), e.getY(), e.getZ(), team));
                } else {
                    dead.add(uid);
                }
            }
            uuidSet.removeAll(dead);
        }

        BattleLineSystem.LOGGER.info("[信标调试-S] 同步完成 world={} beacons={} dead={}",
                world.dimension().location(), entries.size(), dead.size());
        var pkt = new com.battlelinesystem.network.packet.PacketSyncBeaconEntities(entries);
        for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
            if (p.level() == world) {
                com.battlelinesystem.network.AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> p), pkt);
            }
        }
    }

    private void playCaptureSound(ServerLevel world, String owner, List<ServerPlayer> players) {
        // 获取占领方和被占方的阵营音效配置
        String winTeam = owner; // "A" or "B"
        String loseTeam = "A".equals(owner) ? "B" : "A";
        String winFactionId = getTeamFaction(winTeam);
        String loseFactionId = getTeamFaction(loseTeam);
        com.battlelinesystem.faction.FactionManager fmgr = com.battlelinesystem.faction.FactionManager.getInstance();
        com.battlelinesystem.faction.FactionConfig winFc = (winFactionId != null) ? fmgr.getFaction(winFactionId) : null;
        com.battlelinesystem.faction.FactionConfig loseFc = (loseFactionId != null) ? fmgr.getFaction(loseFactionId) : null;

        for (ServerPlayer p : players) {
            if (p.level() != world) continue;
            String team = playerTeams.get(p.getUUID());
            if (team != null && team.equals(owner)) {
                // 占领方：播放阵营胜利音效
                if (winFc != null && winFc.captureSound != null && !winFc.captureSound.isEmpty()) {
                    playSoundToPlayer(p, winFc.captureSound);
                } else {
                    p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
                }
            } else {
                // 敌方/中立方：播放本阵营失败音效
                if (team != null && loseFc != null && loseFc.loseSound != null && !loseFc.loseSound.isEmpty()) {
                    playSoundToPlayer(p, loseFc.loseSound);
                } else {
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.8f, 0.6f);
                }
            }
        }
    }

    private boolean isInside(BlockPos pos, MapConfig.CapturePoint cp) {
        return cp.isInside(pos.getX(), pos.getY(), pos.getZ());
    }

    // ---- 查询 ----

    public List<CaptureState> getStates(ResourceKey<Level> key) {
        return worldStates.get(key);
    }

    // ---- 玩家战绩统计 ----

    public void initPlayerStats(ResourceKey<Level> worldKey, UUID uuid, String name, String team) {
        Map<UUID, PlayerGameStats> statsMap = worldPlayerStats.computeIfAbsent(worldKey, k -> new HashMap<>());
        // 确保 team 不为 null，回退到 playerTeams 或 "?"
        String safeTeam = team;
        if (safeTeam == null || !("A".equals(safeTeam) || "B".equals(safeTeam))) {
            safeTeam = playerTeams.get(uuid);
            if (safeTeam == null || !("A".equals(safeTeam) || "B".equals(safeTeam))) {
                safeTeam = "?";
            }
        }
        PlayerGameStats existing = statsMap.get(uuid);
        if (existing != null) {
            existing.name = name;
            existing.team = safeTeam; // 部署时同步最新的队伍
        } else {
            statsMap.put(uuid, new PlayerGameStats(uuid, name, safeTeam));
        }
    }

    public void recordCapture(ResourceKey<Level> worldKey, UUID uuid) {
        Map<UUID, PlayerGameStats> statsMap = worldPlayerStats.get(worldKey);
        if (statsMap != null) {
            PlayerGameStats s = statsMap.get(uuid);
            if (s != null) s.captures++;
        }
    }

    public void recordKill(ResourceKey<Level> worldKey, UUID killerUuid) {
        Map<UUID, PlayerGameStats> statsMap = worldPlayerStats.get(worldKey);
        if (statsMap != null) {
            PlayerGameStats s = statsMap.get(killerUuid);
            if (s != null) s.kills++;
        }
    }

    public void recordDeath(ResourceKey<Level> worldKey, UUID victimUuid) {
        Map<UUID, PlayerGameStats> statsMap = worldPlayerStats.get(worldKey);
        if (statsMap != null) {
            PlayerGameStats s = statsMap.get(victimUuid);
            if (s != null) s.deaths++;
        }
    }

    public Map<UUID, PlayerGameStats> getWorldPlayerStats(ResourceKey<Level> key) {
        return worldPlayerStats.get(key);
    }

    // ---- 据点状态 DTO ----

    public static class CaptureState {
        public final MapConfig.CapturePoint cp;
        public float progress;  // -1(A满) ~ 0(中立) ~ 1(B满)
        public String owner;    // "A" / "B" / null
        public String capturingTeam; // "A" / "B" / "contested" / null
        public int teamACount;  // A队在该据点内的玩家数
        public int teamBCount;  // B队在该据点内的玩家数

        public CaptureState(MapConfig.CapturePoint cp) {
            this.cp = cp;
            this.progress = 0;
            this.owner = null;
            this.capturingTeam = null;
            this.teamACount = 0;
            this.teamBCount = 0;
        }
    }
}

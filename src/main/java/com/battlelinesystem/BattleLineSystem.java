package com.battlelinesystem;

import com.battlelinesystem.command.OpenMapCommand;
import com.battlelinesystem.event.GameEventHandler;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.game.GameModeManager;
import com.battlelinesystem.game.ModeCountdownManager;
import com.battlelinesystem.game.PlayerGameStats;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketTimeUp;
import com.battlelinesystem.network.packet.PacketGameOverResult;
import com.battlelinesystem.network.packet.PacketOpenFactionVote;
import com.battlelinesystem.network.packet.PacketOpenScreen;
import com.battlelinesystem.network.packet.PacketSaveGunMod;
import com.battlelinesystem.network.packet.PacketSyncCapturePoints;
import com.battlelinesystem.network.packet.PacketSyncForbiddenZones;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BattleLine System 主类
 * 一个战地风格的 Minecraft 游戏系统模组
 */
@Mod(BattleLineSystem.MOD_ID)
public class BattleLineSystem {

    public static final String MOD_ID = "battlelinesystem";
    public static final Logger LOGGER = LogManager.getLogger();

    /** 当前服务端实例（由 MinecraftServerMixin 注入） */
    private static MinecraftServer server;

    private static BattleLineSystem INSTANCE;

    private int tickCounter = 0;

    /** 已打印过 tick 日志的世界 */
    private final java.util.Set<ResourceKey<Level>> tickLoggedWorlds = new java.util.HashSet<>();

    /** 游戏结束后重启投票的倒计时（秒），-1=未激活 */
    private int restartCountdown = -1;

    /** 结算画面等待倒计时（秒），-1=未激活 */
    private int settlementCountdown = -1;
    private String pendingEndWinner;
    private ResourceKey<Level> pendingEndWorldKey;
    private MinecraftServer pendingEndServer;

    /** 战场边界外死亡倒计时: 玩家UUID -> 剩余秒数 */
    private final java.util.Map<UUID, Integer> outOfBoundsTimer = new java.util.HashMap<>();
    private static final int OUT_OF_BOUNDS_MAX = 10; // 10秒死亡倒计时

    /** 禁区死亡倒计时: 玩家UUID -> 剩余秒数 */
    private final java.util.Map<UUID, Integer> forbiddenZoneTimer = new java.util.HashMap<>();
    private static final int FORBIDDEN_ZONE_MAX = 5; // 5秒死亡倒计时

    public BattleLineSystem() {
        INSTANCE = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // 注册物品
        com.battlelinesystem.items.ModItems.init(modEventBus);

        // 注册音效
        com.battlelinesystem.sound.ModSounds.init(modEventBus);

        // 客户端专用：注册按键和 HUD 叠加层（反射避免服务端编译引用 client 类）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class.forName("com.battlelinesystem.client.ClientSetup")
                        .getMethod("init", IEventBus.class)
                        .invoke(null, modEventBus);
            } catch (Exception e) {
                LOGGER.error("ClientSetup init failed", e);
            }
        });

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GameEventHandler());

        LOGGER.info("BattleLine System 模组已初始化！");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AllPackets.registerPackets();
            // 预初始化 FactionManager 确保阵营配置在服务端启动前加载
            try {
                com.battlelinesystem.faction.FactionManager.getInstance();
            } catch (Exception e) {
                LOGGER.error("FactionManager init failed", e);
            }
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        OpenMapCommand.register(event.getDispatcher());
        com.battlelinesystem.command.BeaconCommand.register(event.getDispatcher());
        com.battlelinesystem.command.LooseSpawnTestCommand.register(event.getDispatcher());
        LOGGER.info("指令已注册");
    }

    /**
     * 手动 tick 所有活跃游戏世界（参考 Multiworld FantasyInitializer 的做法）
     */
    @SubscribeEvent
    public void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer srv = event.getServer();
        if (srv.getPlayerCount() == 0) return;

        for (ResourceKey<Level> key : GameWorldManager.getActiveGameWorlds()) {
            ServerLevel world = srv.getLevel(key);
            if (world != null) {
                if (tickLoggedWorlds.add(key)) {
                    LOGGER.info("[音乐调试] onServerTickStart 首次tick世界: {}", key.location());
                }
                world.tick(() -> true);
                // 据点占领 tick
                CapturePointManager.getInstance().tick(world);
            } else {
                LOGGER.warn("[音乐调试] onServerTickStart getLevel返回null: {}", key.location());
            }
        }

        // 检查游戏结束（结算期间不重复触发）
        if (settlementCountdown <= 0) {
            String winner = CapturePointManager.pollGameOverWinner();
            ResourceKey<Level> overKey = CapturePointManager.pollGameOverWorldKey();
            if (winner != null && overKey != null) {
                endGame(srv, winner, overKey);
            }
        }

        // 每秒检查战场边界
        if (tickCounter % 20 == 0) {
            tickBoundaryCheck(srv);
            tickForbiddenZoneCheck(srv);
        }
    }

    /** 检查玩家是否在战场多边形边界内，出界开始死亡倒计时 */
    private void tickBoundaryCheck(MinecraftServer server) {
        com.battlelinesystem.faction.FactionManager fm =
                com.battlelinesystem.faction.FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();
        if (config == null || config.battlefieldBoundary.isEmpty()) {
            outOfBoundsTimer.clear();
            return;
        }
        tickZoneTimer(server, outOfBoundsTimer, OUT_OF_BOUNDS_MAX,
                "§4⚠ 请在 %d 秒内返回战场！", "§c你已脱离战场区域！",
                p -> !config.isInsideBattlefield(p.getX(), p.getZ()));
    }

    /** 检查玩家是否在禁区内，进入禁区开始死亡倒计时 */
    private void tickForbiddenZoneCheck(MinecraftServer server) {
        com.battlelinesystem.faction.FactionManager fm =
                com.battlelinesystem.faction.FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();
        if (config == null || config.forbiddenZones.isEmpty()) {
            forbiddenZoneTimer.clear();
            return;
        }
        tickZoneTimer(server, forbiddenZoneTimer, FORBIDDEN_ZONE_MAX,
                "§4☠ 禁区！请在 %d 秒内离开！", "§c你已进入禁区！",
                p -> {
                    String team = CapturePointManager.getInstance().getPlayerTeam(p.getUUID());
                    if (team == null) return false;
                    for (MapConfig.ForbiddenZone zone : config.forbiddenZones) {
                        if (zone.boundary.isEmpty()) continue;
                        String ft = zone.forbiddenTeam;
                        if ("BOTH".equals(ft) || team.equals(ft)) {
                            if (zone.isInside(p.getX(), p.getZ())) return true;
                        }
                    }
                    return false;
                });
    }

    /** 通用区域计时器：检测玩家是否在危险区域，倒计时结束则击杀 */
    private void tickZoneTimer(MinecraftServer server, java.util.Map<UUID, Integer> timerMap,
                                int maxSeconds, String warningMsg, String killMsg,
                                java.util.function.Predicate<ServerPlayer> inDangerZone) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().location().getNamespace().equals(MOD_ID)) continue;
            UUID uuid = player.getUUID();
            if (!inDangerZone.test(player)) {
                timerMap.remove(uuid);
            } else {
                int remaining = timerMap.getOrDefault(uuid, maxSeconds);
                remaining--;
                if (remaining <= 0) {
                    timerMap.remove(uuid);
                    player.hurt(player.damageSources().outOfBorder(), Float.MAX_VALUE);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(killMsg));
                } else {
                    timerMap.put(uuid, remaining);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(String.format(warningMsg, remaining)), true);
                }
            }
        }
    }

    private void endGame(MinecraftServer server, String winner, ResourceKey<Level> worldKey) {
        LOGGER.info("[GameOver] endGame 开始 winner={} worldKey={}", winner, worldKey.location());
        // 标记结算中，阻止其他世界触发新的 gameOver
        CapturePointManager.markGameFinalized();

        // 公告胜者
        String winnerName = "A".equals(winner) ? CapturePointManager.getTeamAName() : CapturePointManager.getTeamBName();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6§l=== 游戏结束 ===§r\n§e获胜方: " + winnerName));
        }

        // 播放胜利/失败音乐，同时停止濒临结束音乐
        MapConfig cfg = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
        if (cfg != null) {
            // 停止濒临结束音乐
            if (cfg.nearEndMusic != null && !cfg.nearEndMusic.isEmpty()) {
                ServerLevel world = server.getLevel(worldKey);
                if (world != null) {
                    CapturePointManager.stopSoundToWorld(world, cfg.nearEndMusic);
                }
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)) continue;
                String team = CapturePointManager.getInstance().getPlayerTeam(p.getUUID());
                if (team != null && team.equals(winner)) {
                    CapturePointManager.playSoundToPlayer(p, cfg.victoryMusic);
                } else {
                    CapturePointManager.playSoundToPlayer(p, cfg.defeatMusic);
                }
            }
        }

        // 游戏结束 → 所有人切成观察者模式（结算期间无法移动）
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)) {
                p.setGameMode(GameType.SPECTATOR);
            }
        }

        // 发送结算数据到所有客户端
        CapturePointManager cpm = CapturePointManager.getInstance();
        Map<UUID, PlayerGameStats> statsMap = cpm.getWorldPlayerStats(worldKey);
        List<PacketGameOverResult.PlayerStatEntry> statEntries = new ArrayList<>();
        // 从阵营配置获取队伍名称（兼容独立服务端无 CapturePointRenderer 的情况）
        String teamAName = resolveTeamName(cpm, "A");
        String teamBName = resolveTeamName(cpm, "B");
        int sa = cpm.getScoreA(worldKey);
        int sb = cpm.getScoreB(worldKey);
        if (statsMap != null) {
            for (PlayerGameStats s : statsMap.values()) {
                statEntries.add(new PacketGameOverResult.PlayerStatEntry(
                        s.uuid, s.name, s.team, s.captures, s.kills, s.deaths));
            }
        }
        PacketGameOverResult overPkt = new PacketGameOverResult(
                winner, sa, sb, teamAName, teamBName, statEntries);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p), overPkt);
        }

        // 启动30秒结算倒计时，之后传送并清理
        settlementCountdown = 30;
        restartCountdown = -1; // 清除旧重启倒计时
        pendingEndWinner = winner;
        pendingEndWorldKey = worldKey;
        pendingEndServer = server;
        PacketSaveGunMod.GunModStorage.clear(); // 清除所有改装缓存
        LOGGER.info("[GameOver] settlementCountdown=30, restartCountdown重置为-1");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7结算中... " + settlementCountdown + "秒后传送回主世界"));
        }
    }

    /** 结算计时结束后执行：传送、清理、销毁世界 */
    private void finishGame(MinecraftServer server, String winner, ResourceKey<Level> worldKey) {
        LOGGER.info("[GameOver] finishGame 开始 winner={} worldKey={}", winner, worldKey.location());
        CapturePointManager.resetGameFinalized();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)) {
                p.setGameMode(GameType.SURVIVAL);
                try {
                    p.teleportTo(overworld,
                            spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            p.getYRot(), p.getXRot());
                } catch (Exception e) {
                    BattleLineSystem.LOGGER.error("onGameEnd 传送 {} 失败: {}", p.getName().getString(), e.toString());
                }
            }
        }

        // 清理据点状态
        CapturePointManager.getInstance().cleanup(worldKey);

        // 销毁游戏世界
        GameWorldManager.destroyGameWorld(server, worldKey);

        // 通知所有客户端清空据点（自动关闭部署界面）
        PacketSyncCapturePoints emptyPoints =
                new PacketSyncCapturePoints(new java.util.ArrayList<>());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> p), emptyPoints);
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> p),
                    new PacketSyncForbiddenZones(new java.util.ArrayList<>()));
        }

        // 清空倒计时
        outOfBoundsTimer.clear();
        forbiddenZoneTimer.clear();

        // 启动60秒重启倒计时
        restartCountdown = 60;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7下一轮投票将在 " + restartCountdown + " 秒后开始..."));
        }

        pendingEndWinner = null;
        pendingEndWorldKey = null;
        pendingEndServer = null;

        // 清除旧地图配置，确保 doJoin 判定正确
        com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);
        settlementCountdown = -1;
    }

    /**
     * 服务端 tick：每秒更新倒计时
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getPlayerCount() == 0) return;

        com.battlelinesystem.event.GameEventHandler.tickVehicleBoosts(event.getServer());

        tickCounter++;
        tickSelectionCountdown(event.getServer());
        com.battlelinesystem.game.CommanderVoteManager.getInstance().tick(event.getServer());
        com.battlelinesystem.game.CommanderImpeachmentManager.getInstance().tick(event.getServer());
        com.battlelinesystem.game.TeamKickVoteManager.getInstance().tick(event.getServer());

        if (tickCounter % 20 != 0) return;

        tickVehicleRespawn(event.getServer());
        tickSettlementCountdown();
        tickRestartCountdown(event.getServer());
        tickModeCountdown(event.getServer());
    }

    private void tickVehicleRespawn(MinecraftServer server) {
        com.battlelinesystem.game.VehicleRespawnManager vrm = com.battlelinesystem.game.VehicleRespawnManager.getInstance();
        java.util.Set<String> dirtied = vrm.tick(server);
        for (String fid : dirtied) {
            NetworkManager.broadcastVehicleState(fid);
        }
    }

    private void tickSettlementCountdown() {
        if (settlementCountdown <= 0) return;
        settlementCountdown--;
        if (settlementCountdown <= 0) {
            finishGame(pendingEndServer, pendingEndWinner, pendingEndWorldKey);
        } else if (settlementCountdown % 10 == 0) {
            for (ServerPlayer p : pendingEndServer.getPlayerList().getPlayers()) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7" + settlementCountdown + "秒后传送回主世界..."));
            }
        }
    }

    private void tickRestartCountdown(MinecraftServer server) {
        if (restartCountdown <= 0) return;
        restartCountdown--;
        if (restartCountdown <= 0) {
            restartCountdown = -1;
            startNewVote(server);
            return;
        }
        if (restartCountdown % 10 == 0 && restartCountdown <= 30) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7距离下一轮投票还有 §e" + restartCountdown + " §7秒"));
            }
        }
    }

    private void tickModeCountdown(MinecraftServer server) {
        ModeCountdownManager cdm = ModeCountdownManager.getInstance();
        if (cdm.getRemainingSeconds() < 0) return;
        if (cdm.isFinished()) return;

        boolean justFinished = cdm.tick();

        if (justFinished) {
            GameModeManager gmm = GameModeManager.getInstance();
            String winner = gmm.getWinningMode();
            List<PacketTimeUp.MapEntry> mapEntries = new java.util.ArrayList<>();
            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, winner)) {
                mapEntries.add(PacketTimeUp.MapEntry.from(info.config, info.id));
            }
            BattleLineSystem.LOGGER.info("[TimeUp] 发送 {} 个地图给 {} 个玩家", mapEntries.size(), server.getPlayerList().getPlayerCount());
            PacketTimeUp timeUpPacket = new PacketTimeUp(winner, mapEntries);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p), timeUpPacket);
            }
        } else {
            int sec = cdm.getRemainingSeconds();
            GameModeManager gmm = GameModeManager.getInstance();
            int[] counts = new int[GameModeManager.MODE_NAMES.length];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = gmm.getPlayerCount(GameModeManager.MODE_NAMES[i]);
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean isOp = p.hasPermissions(2);
                AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> p),
                        new PacketOpenScreen(0, isOp, counts, sec));
            }
        }
    }

    // ---- 由 MinecraftServerMixin 调用的入口 ----

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    /** 重置 tick 计数器和重启倒计时（/bls stopgame 用） */
    public static void resetTickState() {
        if (INSTANCE != null) {
            INSTANCE.tickCounter = 0;
            INSTANCE.restartCountdown = -1;
            INSTANCE.tickLoggedWorlds.clear();
            com.battlelinesystem.game.SelectionCountdownManager.getInstance().reset();
            com.battlelinesystem.game.CommanderVoteManager.getInstance().reset();
            com.battlelinesystem.game.CommanderImpeachmentManager.getInstance().reset();
            com.battlelinesystem.game.TeamKickVoteManager.getInstance().reset();
        }
    }

    /**
     * 游戏结束回调：清理游戏世界，传送所有人回主世界
     */
    public static void onGameEnd() {
        if (server == null) return;
        GameWorldManager.teleportAllToOverworld(server);

        // 延迟删除以确保传送完成
        server.execute(() -> {
            GameWorldManager.cleanupAll(server);
            // 重置投票状态，准备下一轮
            ModeCountdownManager.getInstance().reset();
            GameModeManager.getInstance().resetAll();
        });
    }

    /** 1分钟倒计时结束后，启动新一轮投票 */
    private void startNewVote(MinecraftServer server) {
        LOGGER.info("重启新一轮投票");
        ModeCountdownManager.getInstance().reset();
        GameModeManager.getInstance().resetAll();
        com.battlelinesystem.game.SelectionCountdownManager.getInstance().reset();
        com.battlelinesystem.game.CommanderVoteManager.getInstance().reset();
        com.battlelinesystem.game.CommanderImpeachmentManager.getInstance().reset();
        com.battlelinesystem.game.TeamKickVoteManager.getInstance().reset();
        // 清除旧地图配置
        com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);

        // 清除所有玩家的原版计分板队伍和阵营tag
        com.battlelinesystem.game.CapturePointManager cpm =
                com.battlelinesystem.game.CapturePointManager.getInstance();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            cpm.clearVanillaScoreboard(p);
        }

        int[] counts = new int[GameModeManager.MODE_NAMES.length];
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean isOp = p.hasPermissions(2);
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> p),
                    new PacketOpenScreen(0, isOp, counts, -1));
        }
    }

    /** 队伍/阵营选择倒计时 tick + 到期自动分配 */
    private void tickSelectionCountdown(MinecraftServer server) {
        com.battlelinesystem.game.SelectionCountdownManager scdm =
                com.battlelinesystem.game.SelectionCountdownManager.getInstance();
        if (scdm.getTeamCountdown() < 0) return;

        var result = scdm.tick(server);
        com.battlelinesystem.faction.FactionManager fm =
                com.battlelinesystem.faction.FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();
        if (config == null) return;

        com.battlelinesystem.game.CapturePointManager cpm =
                com.battlelinesystem.game.CapturePointManager.getInstance();

        if (result.teamExpired) {
            handleTeamExpiry(server, cpm, fm, config, scdm);
        }
        for (String team : result.expiredFactions) {
            handleFactionExpiry(server, team, cpm, fm, config);
        }
        broadcastCountdowns(server, cpm, fm, config, scdm);
    }

    private void handleTeamExpiry(MinecraftServer server,
                                   com.battlelinesystem.game.CapturePointManager cpm,
                                   com.battlelinesystem.faction.FactionManager fm,
                                   MapConfig config,
                                   com.battlelinesystem.game.SelectionCountdownManager scdm) {
        LOGGER.info("[SelectionCD] 队伍选择倒计时到期，开始自动分配");
        int countA = 0, countB = 0;
        List<ServerPlayer> unassigned = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.level().dimension().location().getNamespace().equals(MOD_ID)) continue;
            String team = cpm.getPlayerTeam(p.getUUID());
            if (team == null) unassigned.add(p);
            else if ("A".equals(team)) countA++;
            else if ("B".equals(team)) countB++;
        }
        LOGGER.info("[SelectionCD] 队伍到期统计: A={} B={} 未选={}", countA, countB, unassigned.size());
        for (ServerPlayer p : unassigned) {
            String team = countA <= countB ? "A" : "B";
            cpm.setPlayerTeam(p.getUUID(), team);
            cpm.syncToVanillaScoreboard(p);
            if ("A".equals(team)) countA++; else countB++;
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e时间到！系统已将你分配到 " + ("A".equals(team) ? "§bA队" : "§cB队")));
            LOGGER.info("[SelectionCD] 自动分配 {} → team={}", p.getName().getString(), team);
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.level().dimension().location().getNamespace().equals(MOD_ID)) continue;
            String team = cpm.getPlayerTeam(p.getUUID());
            if (team == null) continue;
            List<String> rawPool = "A".equals(team) ? config.factionPoolA : config.factionPoolB;
            List<com.battlelinesystem.faction.FactionConfig> factionList = buildFactionList(fm, rawPool);
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> p),
                    new PacketOpenFactionVote(factionList,
                            "A".equals(team) ? (rawPool != null ? rawPool : new ArrayList<>()) : new ArrayList<>(),
                            "B".equals(team) ? (rawPool != null ? rawPool : new ArrayList<>()) : new ArrayList<>()));
        }

        if (cpm.getTeamFaction("A") == null) scdm.startFactionCountdown("A");
        if (cpm.getTeamFaction("B") == null) scdm.startFactionCountdown("B");
    }

    private void handleFactionExpiry(MinecraftServer server, String team,
                                      com.battlelinesystem.game.CapturePointManager cpm,
                                      com.battlelinesystem.faction.FactionManager fm,
                                      MapConfig config) {
        LOGGER.info("[SelectionCD] 阵营选择倒计时到期 team={}", team);
        List<String> rawPool = "A".equals(team) ? config.factionPoolA : config.factionPoolB;
        List<com.battlelinesystem.faction.FactionConfig> allActive = buildFactionList(fm, rawPool);

        String votedFactionId = cpm.getTeamFaction(team);
        com.battlelinesystem.faction.FactionConfig chosen = null;
        if (votedFactionId != null) {
            chosen = fm.getFaction(votedFactionId);
            if (chosen != null && !allActive.isEmpty() && !allActive.contains(chosen)) {
                chosen = null;
            }
        }
        if (chosen == null && !allActive.isEmpty()) {
            chosen = allActive.get(new java.util.Random().nextInt(allActive.size()));
        }
        if (chosen == null) {
            LOGGER.warn("[SelectionCD] 阵营到期但无可选阵营 team={}", team);
            return;
        }

        cpm.setTeamFaction(team, chosen.id);
        LOGGER.info("[SelectionCD] 阵营确定 team={} faction={} (来源:{})",
                team, chosen.id, votedFactionId != null ? "投票" : "随机");

        String teamColor = "A".equals(team) ? "§b" : "§c";
        String teamName = "A".equals(team) ? "A队" : "B队";
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String pt = cpm.getPlayerTeam(p.getUUID());
            if (!team.equals(pt)) continue;
            cpm.setPlayerFaction(p.getUUID(), chosen.id);
            cpm.syncToVanillaScoreboard(p);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    teamColor + "[" + teamName + "] §e时间到！最终阵营: " + chosen.name));
        }

        com.battlelinesystem.game.CommanderVoteManager.getInstance().startVote(team, server);
    }

    /** 构建当前有效的阵营列表，若指定了池则过滤 */
    private static List<com.battlelinesystem.faction.FactionConfig> buildFactionList(
            com.battlelinesystem.faction.FactionManager fm, List<String> pool) {
        List<com.battlelinesystem.faction.FactionConfig> all = new ArrayList<>(fm.getActiveMapFactions());
        if (pool != null && !pool.isEmpty()) all.removeIf(fc -> !pool.contains(fc.id));
        return all;
    }

    /** 每秒向客户端广播队伍/阵营倒计时 */
    private void broadcastCountdowns(MinecraftServer server,
                                     com.battlelinesystem.game.CapturePointManager cpm,
                                     com.battlelinesystem.faction.FactionManager fm,
                                     MapConfig config,
                                     com.battlelinesystem.game.SelectionCountdownManager scdm) {
        int teamCd = scdm.getTeamCountdown();
        int[] counts = countTeamPlayers(server);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)) continue;
            String team = cpm.getPlayerTeam(p.getUUID());

            // 队伍选择阶段：广播队伍倒计时
            if (teamCd > 0) {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                        new PacketOpenScreen(1, false, new int[]{0, 0, 0, 0}, teamCd, counts[0], counts[1]));
            }

            // 阵营选择阶段：广播阵营倒计时
            if (team != null && scdm.getFactionCountdown(team) > 0) {
                List<String> rawPool = "A".equals(team) ? config.factionPoolA : config.factionPoolB;
                List<com.battlelinesystem.faction.FactionConfig> factionList = buildFactionList(fm, rawPool);
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                        new PacketOpenFactionVote(factionList,
                                "A".equals(team) ? (rawPool != null ? rawPool : new ArrayList<>()) : new ArrayList<>(),
                                "B".equals(team) ? (rawPool != null ? rawPool : new ArrayList<>()) : new ArrayList<>(),
                                scdm.getFactionCountdown(team)));
            }
        }
    }

    private int[] countTeamPlayers(MinecraftServer server) {
        int countA = 0, countB = 0;
        com.battlelinesystem.game.CapturePointManager cpm =
                com.battlelinesystem.game.CapturePointManager.getInstance();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String team = cpm.getPlayerTeam(sp.getUUID());
            if ("A".equals(team)) countA++;
            else if ("B".equals(team)) countB++;
        }
        return new int[]{countA, countB};
    }

    /** 从 faction 配置获取队伍名称（服务端没有 CapturePointRenderer 时回退用） */
    private static String resolveTeamName(CapturePointManager cpm, String team) {
        String factionId = cpm.getTeamFaction(team);
        if (factionId != null) {
            var fc = com.battlelinesystem.faction.FactionManager.getInstance().getFaction(factionId);
            if (fc != null) return fc.name;
        }
        // 回退：使用 CapturePointRenderer 设置的名称或默认值
        String cpName = "A".equals(team) ? CapturePointManager.getTeamAName() : CapturePointManager.getTeamBName();
        return cpName.isEmpty() ? (team + "队") : cpName;
    }
}

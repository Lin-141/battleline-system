package com.battlelinesystem.command;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.game.CommanderImpeachmentManager;
import com.battlelinesystem.game.CommanderVoteManager;
import com.battlelinesystem.game.GameModeManager;
import com.battlelinesystem.game.ModeCountdownManager;
import com.battlelinesystem.game.PlayerGameStats;
import com.battlelinesystem.game.PlayerProgressionManager;
import com.battlelinesystem.game.SelectionCountdownManager;
import com.battlelinesystem.game.TeamKickVoteManager;
import com.battlelinesystem.items.PolygonWandItem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketSelectMap;
import com.battlelinesystem.network.PacketTimeUp;
import com.battlelinesystem.network.packet.PacketOpenClassVote;
import com.battlelinesystem.network.packet.PacketOpenCommanderVote;
import com.battlelinesystem.network.packet.PacketOpenFactionVote;
import com.battlelinesystem.network.packet.PacketOpenScreen;
import com.battlelinesystem.network.packet.PacketSyncCapturePoints;
import com.battlelinesystem.network.packet.PacketSyncForbiddenZones;
import com.battlelinesystem.network.packet.PacketSyncSpawnPoints;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BLS 指令集
 */
public class OpenMapCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bls")
                        // ===== /bls help =====
                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    src.sendSystemMessage(net.minecraft.network.chat.Component.literal(
"""
§6========== §e§lBattleLineSystem 帮助 §6==========

§b[§f游戏控制§b]
§a/bls help §7- 显示此帮助
§a/bls settings §7- 打开管理设置界面
§a/bls openmap §7- 打开模式/地图投票界面
§a/bls join §7- 加入当前战局/重新部署
§a/bls leave §7- 离开当前战局
§a/bls stopgame §7- 停止当前游戏
§a/bls startmap <模板ID> §7- 跳过投票直接开始指定地图
§a/bls switchteam [player] §7- 跳边到对面队伍

§b[§f计分§b]
§a/bls score set <A|B> <值> §7- 设置队伍分数
§a/bls score add <A|B> <差值> §7- 增加队伍分数
§a/bls score sub <A|B> <差值> §7- 减少队伍分数
§a/bls forcewin <A|B> §7- 强制某队胜利

§b[§f地图编辑§b]
§a/bls edit <模板ID> §7- 打开地图编辑界面
§a/bls saveedit <模板ID> §7- 保存地图编辑
§a/bls forbiddenzone <模板> add <名称> [队伍] §7- 添加禁区(队伍默认BOTH)
§a/bls forbiddenzone <模板> remove <名称> §7- 删除禁区
§a/bls boundary <模板> add §7- 设置战场边界(需多边形棒选点)
§a/bls boundary <模板> remove §7- 清除战场边界
§a/bls spawnpoint add <模板ID> <A|B> §7- 添加出生点
§a/bls spawnpoint remove <模板ID> <编号> §7- 删除出生点
§a/bls spawnpoint list <模板ID> §7- 列出出生点
§a/bls spawnpoint clear <模板ID> §7- 清空出生点
§a/bls spawnpoint vehicle add <模板ID> <载具ID> §7- 添加载具出生点
§a/bls spawnpoint vehicle remove <模板ID> <编号> §7- 删除载具出生点
§a/bls spawnpoint vehicle list <模板ID> §7- 列出载具出生点
§a/bls spawnpoint vehicle clear <模板ID> §7- 清空载具出生点
§a/bls capturepoint add <模板ID> <名称> §7- 添加据点(需选区棒选范围)
§a/bls capturepoint list <模板ID> §7- 列出据点
§a/bls capturepoint remove <模板ID> <名称> §7- 删除据点

§b[§f投票§b]
§a/bls kickcommander <替补玩家> [理由] §7- 发起弹劾指挥官
§a/bls kickcommandervote agree|disagree §7- 弹劾投票
§a/bls kickplayer <目标玩家> [理由] §7- 发起踢出玩家
§a/bls kickplayervote agree|disagree §7- 踢出投票

§b[§f变体/信标§b]
§a/bls unlockvariant <玩家> <变体ID> §7- 解锁变体
§a/bls revokevariant <玩家> <变体ID> §7- 撤销变体
§a/bls listpurchases [玩家] §7- 列出已解锁内容
§a/bls beacon test §7- 信标测试
§a/bls beacon scan §7- 信标扫描
§a/bls loosetest spawn|clear §7- 宽松重生测试

§6=========================================
"""
                                    ));
                                    return 1;
                                })
                        )
                        // ===== /bls openmap =====
                        .then(Commands.literal("openmap")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    boolean isOp = ctx.getSource().hasPermission(2);

                                    ModeCountdownManager cdm = ModeCountdownManager.getInstance();

                                    // 倒计时已结束 → 直接打开地图选择界面
                                    if (cdm.isFinished()) {
                                        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                                        GameModeManager gmm = GameModeManager.getInstance();
                                        String winner = gmm.getWinningMode();

                                        // 重新获取该模式的地图列表
                                        List<PacketTimeUp.MapEntry> mapEntries = new java.util.ArrayList<>();
                                        for (GameWorldManager.MapInfo info :
                                                GameWorldManager.getMapsForMode(server, winner)) {
                                            mapEntries.add(PacketTimeUp.MapEntry.from(info.config, info.id));
                                        }

                                        AllPackets.getChannel().send(
                                                PacketDistributor.PLAYER.with(() -> player),
                                                new PacketTimeUp(winner, mapEntries));
                                        return 1;
                                    }

                                    // 投票进行中 → 打开模式选择界面
                                    GameModeManager gmm = GameModeManager.getInstance();
                                    int[] counts = new int[GameModeManager.MODE_NAMES.length];
                                    for (int i = 0; i < counts.length; i++) {
                                        counts[i] = gmm.getPlayerCount(GameModeManager.MODE_NAMES[i]);
                                    }
                                    int sec = cdm.getRemainingSeconds();

                                    AllPackets.getChannel().send(
                                            PacketDistributor.PLAYER.with(() -> player),
                                            new PacketOpenScreen(0, isOp, counts, sec));
                                    return 1;
                                })
                        )
                        // ===== /bls settings =====
                        .then(Commands.literal("settings")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    boolean isOp = ctx.getSource().hasPermission(2);
                                    GameModeManager gmm = GameModeManager.getInstance();
                                    int[] counts = new int[GameModeManager.MODE_NAMES.length];
                                    for (int i = 0; i < counts.length; i++) {
                                        counts[i] = gmm.getPlayerCount(GameModeManager.MODE_NAMES[i]);
                                    }
                                    ModeCountdownManager cdm = ModeCountdownManager.getInstance();
                                    int sec = cdm.isFinished() ? -1 : cdm.getRemainingSeconds();
                                    AllPackets.getChannel().send(
                                            PacketDistributor.PLAYER.with(() -> player),
                                            new PacketOpenScreen(2, isOp, counts, sec));
                                    return 1;
                                })
                        )
                        // ===== /bls stopgame =====
                        .then(Commands.literal("stopgame")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    try {
                                    net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                                    ServerLevel overworld = server.overworld();
                                    var spawn = overworld.getSharedSpawnPos();

                                    // 1. 清理据点状态（每个活跃游戏世界）
                                    for (var worldKey : GameWorldManager.getActiveGameWorlds()) {
                                        CapturePointManager.getInstance().cleanup(worldKey);
                                    }

                                    // 2. 清理所有游戏世界
                                    GameWorldManager.cleanupAll(server);

                                    // 3. 清除载具满油门注册
                                    com.battlelinesystem.event.GameEventHandler.clearVehicleBoosts();

                                    // 4. 所有人设为生存并传送回主世界
                                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        p.setGameMode(GameType.SURVIVAL);
                                        try {
                                            p.teleportTo(overworld,
                                                    spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                                                    p.getYRot(), p.getXRot());
                                        } catch (Exception e) {
                                            BattleLineSystem.LOGGER.error("stopgame 传送 {} 失败: {}", p.getName().getString(), e.toString());
                                        }
                                    }

                                    // 5. 清空客户端据点显示
                                    var emptyPoints = new PacketSyncCapturePoints(new java.util.ArrayList<>());
                                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        AllPackets.getChannel().send(
                                                PacketDistributor.PLAYER.with(() -> p), emptyPoints);
                                    }

                                    // 6. 重置投票状态 & tick 计数器 & 重启倒计时
                                    ModeCountdownManager.getInstance().reset();
                                    GameModeManager.getInstance().resetAll();
                                    BattleLineSystem.resetTickState();
                                    com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);

                                    BattleLineSystem.LOGGER.info("游戏已中断，所有人传送回主世界，投票已重置");

                                    // 7. 广播模式选择界面给所有玩家
                                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        boolean isOp = p.hasPermissions(2);
                                        int[] counts = new int[GameModeManager.MODE_NAMES.length];
                                        int sec = -1;
                                        AllPackets.getChannel().send(
                                            PacketDistributor.PLAYER.with(() -> p),
                                            new PacketOpenScreen(0, isOp, counts, sec));
                                    }

                                    return 1;
                                    } catch (Exception e) {
                                        BattleLineSystem.LOGGER.error("stopgame 执行失败", e);
                                        ctx.getSource().sendFailure(
                                                net.minecraft.network.chat.Component.literal("§c停止游戏失败: " + e.getMessage()));
                                        return 0;
                                    }
                                })
                        )
                        // ===== /bls startmap <template> =====
                        .then(Commands.literal("startmap")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            net.minecraft.server.MinecraftServer server =
                                                    ctx.getSource().getServer();
                                            for (GameWorldManager.MapInfo info :
                                                    GameWorldManager.getMapsForMode(server, "")) {
                                                builder.suggest(info.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();

                                            // 先停止当前游戏（如果有）
                                            var activeWorlds = GameWorldManager.getActiveGameWorlds();
                                            if (!activeWorlds.isEmpty()) {
                                                for (var wk : activeWorlds) {
                                                    CapturePointManager.getInstance().cleanup(wk);
                                                }
                                                GameWorldManager.cleanupAll(server);
                                                com.battlelinesystem.event.GameEventHandler.clearVehicleBoosts();
                                                com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);
                                            }

                                            // 重置投票状态
                                            ModeCountdownManager.getInstance().reset();
                                            GameModeManager.getInstance().resetAll();
                                            BattleLineSystem.resetTickState();

                                            // 启动游戏
                                            boolean ok = PacketSelectMap.startGameWithMap(server, templateId);
                                            if (ok) {
                                                ctx.getSource().sendSuccess(
                                                        () -> net.minecraft.network.chat.Component.literal(
                                                                "§a已强制开始地图: " + templateId), true);
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal(
                                                                "§c启动地图失败: " + templateId));
                                                return 0;
                                            }
                                        })
                                )
                        )
                        // ===== /bls score set <A|B> <value> =====
                        .then(Commands.literal("score")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("A");
                                                    builder.suggest("B");
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> {
                                                            String team = StringArgumentType.getString(ctx, "team").toUpperCase();
                                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                                            if (!"A".equals(team) && !"B".equals(team)) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("队伍必须是 A 或 B"));
                                                                return 0;
                                                            }
                                                            var worldKeys = GameWorldManager.getActiveGameWorlds();
                                                            if (worldKeys.isEmpty()) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("没有活跃的游戏世界"));
                                                                return 0;
                                                            }
                                                            CapturePointManager cpm = CapturePointManager.getInstance();
                                                            for (var key : worldKeys) {
                                                                cpm.setScore(key, team, value);
                                                            }
                                                            ctx.getSource().sendSuccess(
                                                                    () -> net.minecraft.network.chat.Component.literal(
                                                                            "已将 " + team + " 队分数设为 " + value), true);
                                                            return 1;
                                                        }))))
                                // ===== /bls score add <A|B> <delta> =====
                                .then(Commands.literal("add")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("A");
                                                    builder.suggest("B");
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            String team = StringArgumentType.getString(ctx, "team").toUpperCase();
                                                            int delta = IntegerArgumentType.getInteger(ctx, "delta");
                                                            if (!"A".equals(team) && !"B".equals(team)) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("队伍必须是 A 或 B"));
                                                                return 0;
                                                            }
                                                            var worldKeys = GameWorldManager.getActiveGameWorlds();
                                                            if (worldKeys.isEmpty()) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("没有活跃的游戏世界"));
                                                                return 0;
                                                            }
                                                            CapturePointManager cpm = CapturePointManager.getInstance();
                                                            for (var key : worldKeys) {
                                                                cpm.addScore(key, team, delta);
                                                            }
                                                            final int cur = "A".equals(team) ? cpm.getScoreA(worldKeys.get(0)) : cpm.getScoreB(worldKeys.get(0));
                                                            ctx.getSource().sendSuccess(
                                                                    () -> net.minecraft.network.chat.Component.literal(
                                                                            team + " 队分数 " + (delta >= 0 ? "+" : "") + delta
                                                                                    + " → 当前 " + cur), true);
                                                            return 1;
                                                        }))))
                                // ===== /bls score sub <A|B> <delta> =====
                                .then(Commands.literal("sub")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("A");
                                                    builder.suggest("B");
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("delta", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            String team = StringArgumentType.getString(ctx, "team").toUpperCase();
                                                            int delta = IntegerArgumentType.getInteger(ctx, "delta");
                                                            if (!"A".equals(team) && !"B".equals(team)) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("队伍必须是 A 或 B"));
                                                                return 0;
                                                            }
                                                            var worldKeys = GameWorldManager.getActiveGameWorlds();
                                                            if (worldKeys.isEmpty()) {
                                                                ctx.getSource().sendFailure(
                                                                        net.minecraft.network.chat.Component.literal("没有活跃的游戏世界"));
                                                                return 0;
                                                            }
                                                            CapturePointManager cpm = CapturePointManager.getInstance();
                                                            for (var key : worldKeys) {
                                                                cpm.addScore(key, team, -delta);
                                                            }
                                                            final int cur = "A".equals(team) ? cpm.getScoreA(worldKeys.get(0)) : cpm.getScoreB(worldKeys.get(0));
                                                            ctx.getSource().sendSuccess(
                                                                    () -> net.minecraft.network.chat.Component.literal(
                                                                            team + " 队分数 -" + delta + " → 当前 " + cur), true);
                                                            return 1;
                                                        }))))
                        )
                        // ===== /bls forcewin <A|B> =====
                        .then(Commands.literal("forcewin")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("A");
                                            builder.suggest("B");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String team = StringArgumentType.getString(ctx, "team").toUpperCase();
                                            if (!"A".equals(team) && !"B".equals(team)) {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal("队伍必须是 A 或 B"));
                                                return 0;
                                            }
                                            var worldKeys = GameWorldManager.getActiveGameWorlds();
                                            if (worldKeys.isEmpty()) {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal("没有活跃的游戏世界"));
                                                return 0;
                                            }
                                            for (var key : worldKeys) {
                                                CapturePointManager.getInstance().forceWin(key, team);
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> net.minecraft.network.chat.Component.literal(
                                                            "已强制 " + team + " 队获胜！"), true);
                                            return 1;
                                        }))
                        )
                        // ===== /bls forbiddenzone <template> =====
                        .then(Commands.literal("forbiddenzone")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            var server = ctx.getSource().getServer();
                                            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                                builder.suggest(info.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        // /bls forbiddenzone <template> add <name> [team]
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(ctx -> doAddZone(ctx, "BOTH"))
                                                        .then(Commands.argument("team", StringArgumentType.word())
                                                                .suggests((ctx2, b) -> {
                                                                    b.suggest("A"); b.suggest("B"); b.suggest("BOTH");
                                                                    return b.buildFuture();
                                                                })
                                                                .executes(ctx -> {
                                                                    String team = StringArgumentType.getString(ctx, "team").toUpperCase();
                                                                    if (!team.equals("A") && !team.equals("B") && !team.equals("BOTH")) {
                                                                        ctx.getSource().sendFailure(
                                                                                 net.minecraft.network.chat.Component.literal("§c队伍必须是 A、B 或 BOTH"));
                                                                        return 0;
                                                                    }
                                                                    return doAddZone(ctx, team);
                                                                }))
                                                )
                                        )
                                        // /bls forbiddenzone <template> remove <name>
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> doRemoveZone(ctx)))
                                        )
                                )
                        )
                        // ===== /bls boundary <template> =====
                        .then(Commands.literal("boundary")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            var server = ctx.getSource().getServer();
                                            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                                builder.suggest(info.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        // /bls boundary <template> add
                                        .then(Commands.literal("add")
                                                .executes(ctx -> doSetBoundary(ctx)))
                                        // /bls boundary <template> remove
                                        .then(Commands.literal("remove")
                                                .executes(ctx -> doRemoveBoundary(ctx)))
                                )
                        )
                        // ===== /bls edit <模板ID> =====
                        .then(Commands.literal("edit")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            // 补全所有可用的模板 ID
                                            net.minecraft.server.MinecraftServer server =
                                                    ctx.getSource().getServer();
                                            for (GameWorldManager.MapInfo info :
                                                    GameWorldManager.getMapsForMode(server, "")) {
                                                builder.suggest(info.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();

                                            // 查找模板配置
                                            MapConfig config = GameWorldManager.getMapsForMode(server, "")
                                                    .stream()
                                                    .filter(m -> m.id.equals(templateId))
                                                    .findFirst()
                                                    .map(m -> m.config)
                                                    .orElse(null);

                                            if (config == null) {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal("找不到模板: " + templateId));
                                                return 0;
                                            }

                                            // 固定 worldKey（同一模板始终用同一世界，支持反复编辑）
                                            ResourceKey<Level> worldKey = ResourceKey.create(
                                                    net.minecraft.core.registries.Registries.DIMENSION,
                                                    new net.minecraft.resources.ResourceLocation(
                                                            BattleLineSystem.MOD_ID, "edit_" + templateId));

                                            // 如果世界已存在则直接传送
                                            ServerLevel world = server.getLevel(worldKey);
                                            if (world == null) {
                                                world = GameWorldManager.createAndLoadWorld(
                                                        server, templateId, worldKey, config);
                                            }

                                            if (world == null) {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal("创建编辑世界失败"));
                                                return 0;
                                            }

                                            // 设为创造模式并传送
                                            player.setGameMode(GameType.CREATIVE);
                                            BlockPos spawn = config.getTeamASpawn();
                                            try {
                                                player.teleportTo(world,
                                                        spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                                                        player.getYRot(), player.getXRot());
                                            } catch (Exception e) {
                                                BattleLineSystem.LOGGER.error("编辑模式传送失败: {}", e.toString());
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> net.minecraft.network.chat.Component.literal(
                                                            "已进入编辑模式: " + config.name), true);
                                            BattleLineSystem.LOGGER.info("{} 进入编辑模式: {}", player.getGameProfile().getName(), templateId);
                                            return 1;
                                        })
                                )
                        )
                        // ===== /bls saveedit <模板ID> =====
                        .then(Commands.literal("saveedit")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("template", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            net.minecraft.server.MinecraftServer server =
                                                    ctx.getSource().getServer();
                                            for (GameWorldManager.MapInfo info :
                                                    GameWorldManager.getMapsForMode(server, "")) {
                                                builder.suggest(info.id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();

                                            boolean ok = GameWorldManager.saveTemplateBack(server, templateId);
                                            if (ok) {
                                                ctx.getSource().sendSuccess(
                                                        () -> net.minecraft.network.chat.Component.literal(
                                                                "模板已保存: " + templateId), true);
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        net.minecraft.network.chat.Component.literal("保存失败: " + templateId));
                                            }
                                            return ok ? 1 : 0;
                                        })
                                )
                        )
                        // ===== /bls leave =====
                        .then(Commands.literal("leave")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CapturePointManager cpm =
                                            CapturePointManager.getInstance();

                                    String team = cpm.getPlayerTeam(player.getUUID());
                                    if (team == null) {
                                        ctx.getSource().sendFailure(
                                                net.minecraft.network.chat.Component.literal("§c你当前不在对局中"));
                                        return 0;
                                    }

                                    // 释放限定职业槽
                                    NetworkManager.removePlayerFromClassSelections(player.getUUID());
                                    // 清除玩家在据点管理器中的所有状态
                                    cpm.removePlayer(player.getUUID());

                                    // 清空背包
                                    player.getInventory().clearContent();
                                    player.inventoryMenu.broadcastChanges();

                                    // 设为观察者并传回主世界出生点
                                    player.setGameMode(GameType.SPECTATOR);
                                    net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                                    ServerLevel overworld = server.overworld();
                                    BlockPos spawn = overworld.getSharedSpawnPos();
                                    try {
                                        player.teleportTo(overworld,
                                                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                                                player.getYRot(), player.getXRot());
                                    } catch (Exception e) {
                                        BattleLineSystem.LOGGER.error("切换旁观模式传送失败: {}", e.toString());
                                    }

                                    // 重新打开模式选择界面
                                    boolean isOp = ctx.getSource().hasPermission(2);
                                    int[] counts = new int[GameModeManager.MODE_NAMES.length];
                                    int sec = ModeCountdownManager.getInstance().getRemainingSeconds();
                                    if (sec <= 0) sec = -1;
                                    AllPackets.getChannel().send(
                                            PacketDistributor.PLAYER.with(() -> player),
                                            new PacketOpenScreen(0, isOp, counts, sec));

                                    ctx.getSource().sendSuccess(
                                            () -> net.minecraft.network.chat.Component.literal(
                                                    "§a已退出对局，返回大厅"), false);
                                    BattleLineSystem.LOGGER.info("{} 退出了对局", player.getName().getString());
                                    return 1;
                                })
                        )
                        // ===== /bls join =====
                        .then(Commands.literal("join")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return doJoin(player);
                                })
                        )
                        // ===== /bls switchteam [player] =====
                        .then(Commands.literal("switchteam")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return doSwitchTeam(ctx.getSource(), player);
                                })
                                .then(Commands.argument("target", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            return doSwitchTeam(ctx.getSource(), target);
                                        }))
                        )
                        // ===== /bls capturepoint =====
                        .then(CapturePointCommand.build())
                        // ===== /bls spawnpoint =====
                        .then(SpawnPointCommand.build())
                        // ===== /bls unlockvariant <player> <variantId> =====
                        .then(Commands.literal("unlockvariant")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("variantId", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    String variantId = StringArgumentType.getString(ctx, "variantId");
                                                    com.battlelinesystem.game.PlayerProgressionManager.getInstance()
                                                            .addPurchase(target.getUUID(), variantId);
                                                    ctx.getSource().sendSuccess(
                                                            () -> net.minecraft.network.chat.Component.literal(
                                                                    "§a已授予 " + target.getName().getString() + " 变体: " + variantId),
                                                            true);
                                                    return 1;
                                                }))))
                        // ===== /bls revokevariant <player> <variantId> =====
                        .then(Commands.literal("revokevariant")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("variantId", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    String variantId = StringArgumentType.getString(ctx, "variantId");
                                                    com.battlelinesystem.game.PlayerProgressionManager.getInstance()
                                                            .removePurchase(target.getUUID(), variantId);
                                                    ctx.getSource().sendSuccess(
                                                            () -> net.minecraft.network.chat.Component.literal(
                                                                    "§c已撤销 " + target.getName().getString() + " 变体: " + variantId),
                                                            true);
                                                    return 1;
                                                }))))
                        // ===== /bls listpurchases [player] =====
                        .then(Commands.literal("listpurchases")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return listPurchases(ctx.getSource(), player);
                                })
                                .then(Commands.argument("target", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            return listPurchases(ctx.getSource(), target);
                                        }))
                        )
                        // ===== /bls kickcommander <player> [reason] =====
                        .then(Commands.literal("kickcommander")
                                .then(Commands.argument("replacement", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerPlayer replacement = EntityArgument.getPlayer(ctx, "replacement");
                                            return doImpeach(player, replacement, "");
                                        })
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer replacement = EntityArgument.getPlayer(ctx, "replacement");
                                                    String reason = StringArgumentType.getString(ctx, "reason");
                                                    return doImpeach(player, replacement, reason);
                                                }))
                                )
                        )
                        // ===== /bls kickcommandervote <agree|disagree> =====
                        .then(Commands.literal("kickcommandervote")
                                .then(Commands.argument("vote", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("agree");
                                            builder.suggest("disagree");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String vote = StringArgumentType.getString(ctx, "vote");
                                            return doImpeachVote(player, vote);
                                        }))
                        )
                        // ===== /bls kickplayer <player> [reason] =====
                        .then(Commands.literal("kickplayer")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            return doKickVote(player, target, "");
                                        })
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    String reason = StringArgumentType.getString(ctx, "reason");
                                                    return doKickVote(player, target, reason);
                                                }))
                                )
                        )
                        // ===== /bls kickplayervote <agree|disagree> =====
                        .then(Commands.literal("kickplayervote")
                                .then(Commands.argument("vote", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("agree");
                                            builder.suggest("disagree");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String vote = StringArgumentType.getString(ctx, "vote");
                                            return doKickVoteVote(player, vote);
                                        }))
                        )
        );
    }

    /** 执行跳边逻辑（被 /bls switchteam 和 /bls switchteam <player> 共用） */
    private static int doSwitchTeam(CommandSourceStack source, ServerPlayer player) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        com.battlelinesystem.faction.FactionManager fm =
                com.battlelinesystem.faction.FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();

        if (config == null) {
            source.sendFailure(
                    net.minecraft.network.chat.Component.literal("§c当前没有活跃的游戏"));
            return 0;
        }

        String currentTeam = cpm.getPlayerTeam(player.getUUID());
        if (currentTeam == null) {
            source.sendFailure(
                    net.minecraft.network.chat.Component.literal("§c玩家还没有选择队伍"));
            return 0;
        }

        String newTeam = "A".equals(currentTeam) ? "B" : "A";

        // 人数平衡检查（与 PacketTeamSelect 一致）
        int[] counts = countTeamPlayers(player.getServer());
        int thisCount = "A".equals(newTeam) ? counts[0] : counts[1];
        int otherCount = "A".equals(newTeam) ? counts[1] : counts[0];
        if (thisCount >= otherCount + 2) {
            source.sendFailure(
                    net.minecraft.network.chat.Component.literal("§c目标队伍人数已满（两队差距最多2人），无法跳边"));
            return 0;
        }
        List<String> teamPool = "A".equals(newTeam)
                ? config.factionPoolA : config.factionPoolB;
        List<String> pool = teamPool != null ? teamPool : new java.util.ArrayList<>();

        // 释放限定职业槽（先记下旧阵营ID，释放后广播计数更新给旧阵营玩家）
        String oldFactionId = cpm.getPlayerFaction(player.getUUID());
        NetworkManager.removePlayerFromClassSelections(player.getUUID());
        if (oldFactionId != null) {
            NetworkManager.broadcastClassCounts(oldFactionId);
        }
        // 清除旧的阵营/职业选择
        cpm.setPlayerTeam(player.getUUID(), newTeam);
        cpm.setPlayerFaction(player.getUUID(), null);
        cpm.setPlayerClass(player.getUUID(), null);
        cpm.syncToVanillaScoreboard(player);

        // 更新战绩统计中的队伍（否则结算时仍显示原队伍）
        Map<UUID, PlayerGameStats> statsMap =
                cpm.getWorldPlayerStats(player.serverLevel().dimension());
        if (statsMap != null) {
            PlayerGameStats s = statsMap.get(player.getUUID());
            if (s != null) s.team = newTeam;
        }

        // 清空背包
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();

        // 设为观察者并传送到高处
        player.setGameMode(GameType.SPECTATOR);
        ServerLevel gameWorld = player.serverLevel();
        player.teleportTo(gameWorld,
                config.getTeamASpawn().getX() + 0.5, 319,
                config.getTeamASpawn().getZ() + 0.5, 180, 90);

        // 检查目标队伍是否已有阵营选择
        String teamFactionId = cpm.getTeamFaction(newTeam);
        if (teamFactionId != null) {
            com.battlelinesystem.faction.FactionConfig teamFc =
                    fm.getFaction(teamFactionId);
            if (teamFc != null) {
                cpm.setPlayerFaction(player.getUUID(), teamFactionId);
                cpm.syncToVanillaScoreboard(player);
                String teamColor = "A".equals(newTeam) ? "§b" : "§c";
                String tn = "A".equals(newTeam) ? "A队" : "B队";
                source.sendSuccess(
                        () -> net.minecraft.network.chat.Component.literal(
                                teamColor + player.getName().getString() + " 已跳转到 " + tn + "（阵营: " + teamFc.name + "）"), true);
                net.minecraft.server.MinecraftServer srv = player.getServer();
                if (teamFc.classes != null && !teamFc.classes.isEmpty()) {
                    // 直接打开跳边玩家的职业选择界面
                    PacketOpenClassVote pkt =
                            new PacketOpenClassVote(
                                    teamFactionId, teamFc.name, teamFc.displayColor,
                                    (byte)("A".equals(newTeam) ? 0 : 1),
                                    new java.util.ArrayList<>(teamFc.classes), teamFc.vehicles);
                    if (srv != null) {
                        pkt.totalPlayers = CapturePointManager.countTeamPlayers(srv, newTeam);
                        pkt.looseSpawn = teamFc.looseSpawn;
                        // 设置双方实际阵营名称
                        com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(cpm.getTeamFaction("A"));
                        com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(cpm.getTeamFaction("B"));
                        pkt.teamAName = ta != null ? ta.name : "A队";
                        pkt.teamBName = tb != null ? tb.name : "B队";
                        for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
                            if (newTeam.equals(cpm.getPlayerTeam(sp.getUUID()))) {
                                pkt.sameTeamUUIDs.add(sp.getUUID());
                            }
                        }
                        AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player), pkt);
                        com.battlelinesystem.game.CapturePointManager.playSoundToPlayer(player, "battlelinesystem:waiting_01");
                    }
                } else {
                    if (srv != null) {
                        AllPackets.getChannel().send(
                                PacketDistributor.PLAYER.with(() -> player),
                                new PacketOpenClassVote("", "", "", (byte)("A".equals(newTeam) ? 0 : 1), new java.util.ArrayList<>(), null));
                    }
                }
                return 1;
            }
        }

        // 打开阵营选择界面
        List<com.battlelinesystem.faction.FactionConfig> allActive =
                new java.util.ArrayList<>(fm.getActiveMapFactions());
        if (!pool.isEmpty()) {
            allActive.removeIf(fc -> !pool.contains(fc.id));
        }
        List<String> poolA = "A".equals(newTeam) ? pool : new java.util.ArrayList<>();
        List<String> poolB = "B".equals(newTeam) ? pool : new java.util.ArrayList<>();
        AllPackets.getChannel().send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenFactionVote(allActive, poolA, poolB));

        source.sendSuccess(
                () -> net.minecraft.network.chat.Component.literal(
                        "§a" + player.getName().getString() + " 已跳转到 " + ("A".equals(newTeam) ? "§bA队" : "§cB队")), true);
        return 1;
    }

    /** 已在战局中的玩家重新部署 */
    private static int doRejoin(ServerPlayer player) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        FactionManager fm = FactionManager.getInstance();
        MapConfig config = fm.getActiveMapConfig();
        if (config == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前没有活跃的游戏"));
            return 0;
        }

        String team = cpm.getPlayerTeam(player.getUUID());
        if (team == null) return doJoin(player);

        // 清空背包
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();

        // 设为观察者传送到高处
        player.setGameMode(GameType.SPECTATOR);
        ServerLevel gameWorld = player.serverLevel();
        player.teleportTo(gameWorld,
                config.getTeamASpawn().getX() + 0.5, 319,
                config.getTeamASpawn().getZ() + 0.5, 180, 90);

        // 根据当前阶段发送对应部署界面
        String teamFactionId = cpm.getTeamFaction(team);
        if (teamFactionId != null) {
            cpm.setPlayerFaction(player.getUUID(), teamFactionId);
            cpm.syncToVanillaScoreboard(player);
            FactionConfig teamFc = fm.getFaction(teamFactionId);
            if (teamFc != null && teamFc.classes != null && !teamFc.classes.isEmpty()) {
                PacketOpenClassVote pkt = new PacketOpenClassVote(
                        teamFactionId, teamFc.name, teamFc.displayColor,
                        (byte) ("A".equals(team) ? 0 : 1),
                        new java.util.ArrayList<>(teamFc.classes), teamFc.vehicles);
                net.minecraft.server.MinecraftServer srv = player.getServer();
                if (srv != null) {
                    pkt.totalPlayers = CapturePointManager.countTeamPlayers(srv, team);
                    pkt.looseSpawn = teamFc.looseSpawn;
                    // 设置双方实际阵营名称
                    setTeamNamesOnPacket(pkt, cpm, fm);
                    for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
                        if (team.equals(cpm.getPlayerTeam(sp.getUUID())))
                            pkt.sameTeamUUIDs.add(sp.getUUID());
                    }
                }
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player), pkt);
                CapturePointManager.playSoundToPlayer(player, "battlelinesystem:waiting_01");
                return 1;
            }
        }

        // 打开阵营选择界面
        List<FactionConfig> allActive = new java.util.ArrayList<>(fm.getActiveMapFactions());
        List<String> pool = "A".equals(team)
                ? (config.factionPoolA != null ? config.factionPoolA : new java.util.ArrayList<>())
                : (config.factionPoolB != null ? config.factionPoolB : new java.util.ArrayList<>());
        if (!pool.isEmpty()) allActive.removeIf(fc -> !pool.contains(fc.id));
        List<String> poolA = "A".equals(team) ? pool : new java.util.ArrayList<>();
        List<String> poolB = "B".equals(team) ? pool : new java.util.ArrayList<>();
        AllPackets.getChannel().send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenFactionVote(allActive, poolA, poolB));
        return 1;
    }

    /** 普通玩家加入当前战局，若无战局则从模式投票开始 */
    private static int doJoin(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return 0;

        CapturePointManager cpm = CapturePointManager.getInstance();
        FactionManager fm = FactionManager.getInstance();
        ModeCountdownManager cdm = ModeCountdownManager.getInstance();
        SelectionCountdownManager scdm = SelectionCountdownManager.getInstance();
        CommanderVoteManager cvm = CommanderVoteManager.getInstance();

        // 已在战局中 → 重新部署
        if (cpm.getPlayerTeam(player.getUUID()) != null) {
            return doRejoin(player);
        }

        MapConfig config = fm.getActiveMapConfig();
        List<ResourceKey<Level>> activeWorlds = GameWorldManager.getActiveGameWorlds();

        // ---- 无活跃战局 ----
        if (activeWorlds.isEmpty() && config == null) {
            if (cdm.getRemainingSeconds() >= 0) {
                // 模式投票进行中 → 补发投票界面
                GameModeManager gmm = GameModeManager.getInstance();
                int[] counts = new int[GameModeManager.MODE_NAMES.length];
                for (int i = 0; i < counts.length; i++)
                    counts[i] = gmm.getPlayerCount(GameModeManager.MODE_NAMES[i]);
                AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketOpenScreen(0, player.hasPermissions(2), counts, cdm.getRemainingSeconds()));
                return 1;
            }

            if (cdm.isFinished()) {
                // 模式投票已结束，地图选择阶段
                GameModeManager gmm = GameModeManager.getInstance();
                String winner = gmm.getWinningMode();
                List<PacketTimeUp.MapEntry> mapEntries = new java.util.ArrayList<>();
                for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, winner))
                    mapEntries.add(PacketTimeUp.MapEntry.from(info.config, info.id));
                AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketTimeUp(winner, mapEntries));
                return 1;
            }

            // 完全无战局 → 启动模式投票
            cdm.reset();
            GameModeManager.getInstance().resetAll();
            scdm.reset();
            cvm.reset();
            int[] counts = new int[GameModeManager.MODE_NAMES.length];
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                AllPackets.getChannel().send(
                        PacketDistributor.PLAYER.with(() -> p),
                        new PacketOpenScreen(0, p.hasPermissions(2), counts, -1));
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已开始新的模式投票！"));
            return 1;
        }

        // ---- 活跃战局 → 按阶段加入 ----
        if (activeWorlds.isEmpty()) {
            // config 存在但世界未加载（地图加载中）
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e地图正在加载中，请稍后再试"));
            return 0;
        }

        ResourceKey<Level> gameWorldKey = activeWorlds.get(0);
        ServerLevel gameWorld = server.getLevel(gameWorldKey);
        if (gameWorld == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c游戏世界未加载"));
            return 0;
        }

        // 传送到游戏世界（观战视角 + 空中俯视）
        if (!player.level().dimension().equals(gameWorldKey)) {
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
            player.setGameMode(GameType.SPECTATOR);
            BlockPos tpPos = config != null ? config.getTeamASpawn() : new BlockPos(0, 100, 0);
            player.teleportTo(gameWorld, tpPos.getX() + 0.5, 319, tpPos.getZ() + 0.5, 180, 90);
        }

        // 队伍选择进行中 → 发送队伍选择界面
        int teamCd = scdm.getTeamCountdown();
        if (teamCd > 0) {
            int[] cnts = countTeamPlayers(server);
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenScreen(1, false, new int[]{0, 0, 0, 0}, teamCd, cnts[0], cnts[1]));
            return 1;
        }

        // 自动分配到人数较少的队伍
        int countA = 0, countB = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String pt = cpm.getPlayerTeam(p.getUUID());
            if ("A".equals(pt)) countA++;
            else if ("B".equals(pt)) countB++;
        }
        String team = countA <= countB ? "A" : "B";
        cpm.setPlayerTeam(player.getUUID(), team);
        cpm.syncToVanillaScoreboard(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a你已加入 " + ("A".equals(team) ? "§bA队" : "§cB队")));

        // 同步据点、禁区、出生点给新玩家（这些只在开局时广播一次，新加入的玩家需要补发）
        if (config != null) {
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncCapturePoints(cpm.getRevealedPointsForWorld(gameWorldKey)));
            java.util.List<PacketSyncForbiddenZones.ZoneEntry> forbiddenEntries = new java.util.ArrayList<>();
            for (MapConfig.ForbiddenZone fz : config.forbiddenZones) {
                forbiddenEntries.add(new PacketSyncForbiddenZones.ZoneEntry(
                        fz.name, fz.forbiddenTeam, fz.boundary));
            }
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncForbiddenZones(forbiddenEntries));
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncSpawnPoints(
                            config.spawnPoints.team_a, config.spawnPoints.team_b,
                            PacketSelectMap.buildTeamVehicleTypes(config, true),
                            PacketSelectMap.buildTeamVehicleTypes(config, false)));
        }

        // 检查阵营是否已确定
        String teamFactionId = cpm.getTeamFaction(team);
        if (teamFactionId != null) {
            cpm.setPlayerFaction(player.getUUID(), teamFactionId);
            cpm.syncToVanillaScoreboard(player);
            FactionConfig fc = fm.getFaction(teamFactionId);

            // 检查指挥官选举状态
            String commander = cvm.getCommander(team);
            if (commander != null) {
                // 指挥官已选出 → 职业选择
                sendClassScreen(player, team, teamFactionId, fc, server);
            } else if (cvm.isVoting(team)) {
                // 指挥官投票进行中
                cvm.sendVoteScreenToPlayer(team, player);
            } else {
                // 无指挥官投票 → 直接职业选择
                sendClassScreen(player, team, teamFactionId, fc, server);
            }
        } else {
            // 阵营未确定 → 发送阵营选择界面
            int factionCd = scdm.getFactionCountdown(team);
            List<String> rawPool = "A".equals(team) ? config.factionPoolA : config.factionPoolB;
            List<String> teamPool = rawPool != null ? rawPool : new java.util.ArrayList<>();
            List<FactionConfig> allActive = new java.util.ArrayList<>(fm.getActiveMapFactions());
            if (!teamPool.isEmpty()) allActive.removeIf(fc -> !teamPool.contains(fc.id));
            AllPackets.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenFactionVote(allActive,
                            "A".equals(team) ? teamPool : new java.util.ArrayList<>(),
                            "B".equals(team) ? teamPool : new java.util.ArrayList<>(),
                            factionCd > 0 ? factionCd : 0));
        }

        return 1;
    }

    private static void sendClassScreen(ServerPlayer player, String team, String factionId,
                                         FactionConfig fc, net.minecraft.server.MinecraftServer server) {
        if (fc != null && fc.classes != null && !fc.classes.isEmpty()) {
            PacketOpenClassVote pkt = new PacketOpenClassVote(
                    factionId, fc.name, fc.displayColor,
                    (byte) ("A".equals(team) ? 0 : 1),
                    new java.util.ArrayList<>(fc.classes), fc.vehicles);
            pkt.totalPlayers = CapturePointManager.countTeamPlayers(server, team);
            pkt.looseSpawn = fc.looseSpawn;
            // 设置双方实际阵营名称（从服务端确定，确保所有客户端一致）
            com.battlelinesystem.faction.FactionManager fm = com.battlelinesystem.faction.FactionManager.getInstance();
            CapturePointManager cpm = CapturePointManager.getInstance();
            com.battlelinesystem.faction.FactionConfig teamA = fm.getFaction(cpm.getTeamFaction("A"));
            com.battlelinesystem.faction.FactionConfig teamB = fm.getFaction(cpm.getTeamFaction("B"));
            pkt.teamAName = teamA != null ? teamA.name : "A队";
            pkt.teamBName = teamB != null ? teamB.name : "B队";
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (team.equals(CapturePointManager.getInstance().getPlayerTeam(sp.getUUID())))
                    pkt.sameTeamUUIDs.add(sp.getUUID());
            }
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player), pkt);
        } else {
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenClassVote("", "", "", (byte) ("A".equals(team) ? 0 : 1),
                            new java.util.ArrayList<>(), null));
        }
    }

    private static int[] countTeamPlayers(net.minecraft.server.MinecraftServer server) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        int countA = 0, countB = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String t = cpm.getPlayerTeam(sp.getUUID());
            if ("A".equals(t)) countA++;
            else if ("B".equals(t)) countB++;
        }
        return new int[]{countA, countB};
    }

    /** 将双方阵营名称写入 PacketOpenClassVote */
    private static void setTeamNamesOnPacket(PacketOpenClassVote pkt, CapturePointManager cpm,
                                              com.battlelinesystem.faction.FactionManager fm) {
        if (pkt.teamAName == null || pkt.teamAName.isEmpty()) {
            String taFid = cpm.getTeamFaction("A");
            com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(taFid);
            pkt.teamAName = ta != null ? ta.name : "A队";
        }
        if (pkt.teamBName == null || pkt.teamBName.isEmpty()) {
            String tbFid = cpm.getTeamFaction("B");
            com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(tbFid);
            pkt.teamBName = tb != null ? tb.name : "B队";
        }
    }

    /** /bls listpurchases [player] 的输出逻辑 */
    private static int listPurchases(CommandSourceStack source, ServerPlayer target) {
        var ppm = com.battlelinesystem.game.PlayerProgressionManager.getInstance();
        java.util.Set<String> purchases = ppm.getPurchases(target.getUUID());
        if (purchases.isEmpty()) {
            source.sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                            "§7" + target.getName().getString() + " 没有已购买的变体"),
                    false);
        } else {
            source.sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                            "§a" + target.getName().getString() + " 已购买变体 (" + purchases.size() + "): §f"
                                    + String.join(", ", purchases)),
                    false);
        }
        return 1;
    }

    /** /bls kickcommander <replacementPlayer> [reason] */
    private static int doImpeach(ServerPlayer player, ServerPlayer replacement, String reason) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        CommanderVoteManager cvm = CommanderVoteManager.getInstance();
        com.battlelinesystem.game.CommanderImpeachmentManager cim =
                com.battlelinesystem.game.CommanderImpeachmentManager.getInstance();

        String team = cpm.getPlayerTeam(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不在任何队伍中"));
            return 0;
        }

        String currentCommander = cvm.getCommander(team);
        if (currentCommander == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前队伍没有指挥官"));
            return 0;
        }

        String repTeam = cpm.getPlayerTeam(replacement.getUUID());
        if (!team.equals(repTeam)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c替补指挥官必须和你同队"));
            return 0;
        }

        if (replacement.getName().getString().equals(currentCommander)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c替补不能是当前指挥官本人"));
            return 0;
        }

        if (cim.hasActive(team)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前已有弹劾进行中"));
            return 0;
        }

        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return 0;

        boolean ok = cim.startImpeachment(team, player.getUUID(), player.getName().getString(),
                currentCommander, replacement.getUUID(), replacement.getName().getString(),
                reason, server);
        if (!ok) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c发起弹劾失败"));
            return 0;
        }
        return 1;
    }

    /** /bls impeachvote <agree|disagree> */
    private static int doImpeachVote(ServerPlayer player, String vote) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        com.battlelinesystem.game.CommanderImpeachmentManager cim =
                com.battlelinesystem.game.CommanderImpeachmentManager.getInstance();

        String team = cpm.getPlayerTeam(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不在任何队伍中"));
            return 0;
        }

        if (!cim.hasActive(team)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前没有进行中的弹劾"));
            return 0;
        }

        boolean agree = "agree".equalsIgnoreCase(vote);
        CommanderImpeachmentManager.VoteResult result = cim.vote(
                team, player.getUUID(), player.getName().getString(), agree);

        switch (result) {
            case OK:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a你已投票: " + (agree ? "同意弹劾" : "反对弹劾")));
                break;
            case ALREADY_VOTED:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你已经投过票了"));
                break;
            case NOT_IN_TEAM:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不在弹劾的队伍中"));
                break;
            default:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c投票失败"));
                break;
        }
        return 1;
    }

    /** /bls kickplayer <targetPlayer> [reason] */
    private static int doKickVote(ServerPlayer player, ServerPlayer target, String reason) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        com.battlelinesystem.game.TeamKickVoteManager tkvm =
                com.battlelinesystem.game.TeamKickVoteManager.getInstance();

        String team = cpm.getPlayerTeam(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不在任何队伍中"));
            return 0;
        }

        String targetTeam = cpm.getPlayerTeam(target.getUUID());
        if (!team.equals(targetTeam)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c目标玩家和你不在同一队伍"));
            return 0;
        }

        if (player.getUUID().equals(target.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c不能踢自己"));
            return 0;
        }

        if (tkvm.hasActive(team)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前已有踢出投票进行中"));
            return 0;
        }

        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return 0;

        boolean ok = tkvm.startKickVote(team, player.getUUID(), player.getName().getString(),
                target.getUUID(), target.getName().getString(), reason, server);
        if (!ok) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c发起踢出投票失败"));
            return 0;
        }
        return 1;
    }

    /** /bls kickvote_vote <agree|disagree> */
    private static int doKickVoteVote(ServerPlayer player, String vote) {
        CapturePointManager cpm = CapturePointManager.getInstance();
        com.battlelinesystem.game.TeamKickVoteManager tkvm =
                com.battlelinesystem.game.TeamKickVoteManager.getInstance();

        String team = cpm.getPlayerTeam(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不在任何队伍中"));
            return 0;
        }

        if (!tkvm.hasActive(team)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c当前没有进行中的踢出投票"));
            return 0;
        }

        boolean agree = "agree".equalsIgnoreCase(vote);
        com.battlelinesystem.game.TeamKickVoteManager.VoteResult result = tkvm.vote(
                team, player.getUUID(), player.getName().getString(), agree);

        switch (result) {
            case OK:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a你已投票: " + (agree ? "同意踢出" : "反对踢出")));
                break;
            case ALREADY_VOTED:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你已经投过票了"));
                break;
            default:
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c投票失败"));
                break;
        }
        return 1;
    }

    // ==================== forbiddenzone / boundary 子命令实现 ====================

    private static int doAddZone(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String team)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        java.util.List<net.minecraft.core.BlockPos> points = PolygonWandItem.getPoints(player.getUUID());
        if (points.size() < 3) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c需要至少3个顶点，请先用多边形棒选点"));
            return 0;
        }
        java.nio.file.Path mapJson = java.nio.file.Path.of("templates", templateId, "map.json");
        if (!java.nio.file.Files.exists(mapJson)) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c找不到地图: " + templateId));
            return 0;
        }
        try {
            MapConfig cfg = MapConfig.GSON.fromJson(
                    java.nio.file.Files.readString(mapJson), MapConfig.class);
            MapConfig.ForbiddenZone zone = new MapConfig.ForbiddenZone();
            zone.name = name;
            zone.forbiddenTeam = team;
            for (net.minecraft.core.BlockPos p : points) {
                zone.boundary.add(new int[]{p.getX(), p.getY(), p.getZ()});
            }
            cfg.forbiddenZones.add(zone);
            java.nio.file.Files.writeString(mapJson, MapConfig.GSON.toJson(cfg));
            ctx.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                            "§a禁区已添加: " + name + " (队伍: " + team + ", " + points.size() + "顶点)"),
                    true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c保存失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int doRemoveZone(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        java.nio.file.Path mapJson = java.nio.file.Path.of("templates", templateId, "map.json");
        if (!java.nio.file.Files.exists(mapJson)) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c找不到地图: " + templateId));
            return 0;
        }
        try {
            MapConfig cfg = MapConfig.GSON.fromJson(
                    java.nio.file.Files.readString(mapJson), MapConfig.class);
            boolean removed = cfg.forbiddenZones.removeIf(z -> z.name.equals(name));
            if (!removed) {
                ctx.getSource().sendFailure(
                        net.minecraft.network.chat.Component.literal("§c未找到禁区: " + name));
                return 0;
            }
            java.nio.file.Files.writeString(mapJson, MapConfig.GSON.toJson(cfg));
            ctx.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal("§a禁区已删除: " + name), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c保存失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int doSetBoundary(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String templateId = StringArgumentType.getString(ctx, "template");
        java.util.List<net.minecraft.core.BlockPos> points = PolygonWandItem.getPoints(player.getUUID());
        if (points.size() < 3) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c需要至少3个顶点，请先用多边形棒选点"));
            return 0;
        }
        java.nio.file.Path mapJson = java.nio.file.Path.of("templates", templateId, "map.json");
        if (!java.nio.file.Files.exists(mapJson)) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c找不到地图: " + templateId));
            return 0;
        }
        try {
            MapConfig cfg = MapConfig.GSON.fromJson(
                    java.nio.file.Files.readString(mapJson), MapConfig.class);
            cfg.battlefieldBoundary.clear();
            for (net.minecraft.core.BlockPos p : points) {
                cfg.battlefieldBoundary.add(new int[]{p.getX(), p.getY(), p.getZ()});
            }
            java.nio.file.Files.writeString(mapJson, MapConfig.GSON.toJson(cfg));
            ctx.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal(
                            "§a战场边界已设置: " + points.size() + "顶点"), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c保存失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int doRemoveBoundary(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String templateId = StringArgumentType.getString(ctx, "template");
        java.nio.file.Path mapJson = java.nio.file.Path.of("templates", templateId, "map.json");
        if (!java.nio.file.Files.exists(mapJson)) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c找不到地图: " + templateId));
            return 0;
        }
        try {
            MapConfig cfg = MapConfig.GSON.fromJson(
                    java.nio.file.Files.readString(mapJson), MapConfig.class);
            cfg.battlefieldBoundary.clear();
            java.nio.file.Files.writeString(mapJson, MapConfig.GSON.toJson(cfg));
            ctx.getSource().sendSuccess(
                    () -> net.minecraft.network.chat.Component.literal("§a战场边界已删除"), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal("§c保存失败: " + e.getMessage()));
        }
        return 1;
    }
}

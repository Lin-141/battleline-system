package com.battlelinesystem.command;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.items.SelectionWandItem;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /bls capturepoint 子命令
 * add <templateId> <name>  — 将当前选区棒范围添加为指定地图的据点
 * list <templateId>       — 列出指定地图的所有据点
 * remove <templateId> <name> — 移除指定据点
 */
public class CapturePointCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("capturepoint")
                .requires(src -> src.hasPermission(2))
                // /bls capturepoint add <template> <name>
                .then(literal("add")
                        .then(argument("template", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var server = ctx.getSource().getServer();
                                    for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                        builder.suggest(info.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            String name = StringArgumentType.getString(ctx, "name");

                                            BlockPos pos1 = SelectionWandItem.getPos1(player);
                                            BlockPos pos2 = SelectionWandItem.getPos2(player);

                                            if (pos1 == null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c请先用选区棒左键选择点A"));
                                                return 0;
                                            }
                                            if (pos2 == null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c请先用选区棒右键选择点B"));
                                                return 0;
                                            }

                                            MapConfig.CapturePoint cp = new MapConfig.CapturePoint(
                                                    name,
                                                    pos1.getX(), pos1.getY(), pos1.getZ(),
                                                    pos2.getX(), pos2.getY(), pos2.getZ());

                                            int result = saveCapturePoint(templateId, cp, ctx.getSource());
                                            if (result > 0) {
                                                int dx = Math.abs(pos2.getX() - pos1.getX()) + 1;
                                                int dy = Math.abs(pos2.getY() - pos1.getY()) + 1;
                                                int dz = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
                                                String msg = result == 2
                                                        ? "§a据点区域已追加: §f" + name + " §7" + dx + "x" + dy + "x" + dz + " §7→ " + templateId
                                                        : "§a据点已创建: §f" + name + " §7" + dx + "x" + dy + "x" + dz + " §7→ " + templateId;
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal(msg), true);
                                                BattleLineSystem.LOGGER.info("据点区域{}已追加到地图{}: {}", name, templateId,
                                                        result == 2 ? "新区间" : "新建");
                                            }
                                            return result > 0 ? 1 : 0;
                                        })
                                )
                        )
                )
                // /bls capturepoint list <template>
                .then(literal("list")
                        .then(argument("template", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var server = ctx.getSource().getServer();
                                    for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                        builder.suggest(info.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String templateId = StringArgumentType.getString(ctx, "template");
                                    Path mapJson = Path.of("templates", templateId, "map.json");
                                    if (!Files.exists(mapJson)) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c找不到地图: " + templateId));
                                        return 0;
                                    }
                                    try {
                                        MapConfig config = MapConfig.GSON.fromJson(
                                                Files.readString(mapJson), MapConfig.class);
                                        List<MapConfig.CapturePoint> cps = config.capturePoints;
                                        if (cps == null || cps.isEmpty()) {
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("§7地图 " + templateId + " 暂无据点"), false);
                                        } else {
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("§6=== 地图 " + templateId + " 据点 ==="), false);
                                            for (MapConfig.CapturePoint cp : cps) {
                                                String info = cp.isRegion()
                                                        ? " §7区域[" + cp.blockPos().toShortString()
                                                          + " ~ " + cp.blockPos2().toShortString() + "]"
                                                        : " §7单点[" + cp.blockPos().toShortString() + "]";
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("  §e" + cp.name + info), false);
                                            }
                                        }
                                    } catch (Exception e) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c读取地图配置失败: " + e.getMessage()));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                // /bls capturepoint remove <template> <name>
                .then(literal("remove")
                        .then(argument("template", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var server = ctx.getSource().getServer();
                                    for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                        builder.suggest(info.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            Path mapJson = Path.of("templates", templateId, "map.json");
                                            if (!Files.exists(mapJson)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c找不到地图: " + templateId));
                                                return 0;
                                            }
                                            try {
                                                MapConfig config = MapConfig.GSON.fromJson(
                                                        Files.readString(mapJson), MapConfig.class);
                                                boolean removed = config.capturePoints.removeIf(
                                                        cp -> cp.name.equalsIgnoreCase(name));
                                                if (removed) {
                                                    Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("§a据点已删除: §f" + name),
                                                            true);
                                                } else {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("§c未找到该名称的据点: " + name));
                                                }
                                            } catch (Exception e) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c操作失败: " + e.getMessage()));
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                // /bls capturepoint spawn add <template> <name> <team> fixed|random
                .then(literal("spawn")
                        .then(literal("add")
                                .then(argument("template", StringArgumentType.word())
                                        .suggests(mapSuggest())
                                        .then(argument("name", StringArgumentType.word())
                                                .then(argument("team", StringArgumentType.word())
                                                        .suggests((ctx, b) -> { b.suggest("a"); b.suggest("b"); return b.buildFuture(); })
                                                        .then(literal("fixed")
                                                                .executes(ctx -> addCpSpawn(ctx, true, false))
                                                        )
                                                        .then(literal("random")
                                                                .then(argument("radius", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> addCpSpawn(ctx, false, false))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        // /bls capturepoint spawn list <template> <name>
                        .then(literal("list")
                                .then(argument("template", StringArgumentType.word())
                                        .suggests(mapSuggest())
                                        .then(argument("name", StringArgumentType.word())
                                                .executes(ctx -> listCpSpawns(ctx))
                                        )
                                )
                        )
                        // /bls capturepoint spawn remove <template> <name> <team> <index>
                        .then(literal("remove")
                                .then(argument("template", StringArgumentType.word())
                                        .suggests(mapSuggest())
                                        .then(argument("name", StringArgumentType.word())
                                                .then(argument("team", StringArgumentType.word())
                                                        .suggests((ctx, b) -> { b.suggest("a"); b.suggest("b"); return b.buildFuture(); })
                                                        .then(argument("index", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> removeCpSpawn(ctx))
                                                        )
                                                )
                                        )
                                )
                        )
                        // /bls capturepoint spawn clear <template> <name> <team>
                        .then(literal("clear")
                                .then(argument("template", StringArgumentType.word())
                                        .suggests(mapSuggest())
                                        .then(argument("name", StringArgumentType.word())
                                                .then(argument("team", StringArgumentType.word())
                                                        .suggests((ctx, b) -> { b.suggest("a"); b.suggest("b"); return b.buildFuture(); })
                                                        .executes(ctx -> clearCpSpawns(ctx))
                                                )
                                        )
                                )
                        )
                )
        ;
    }

    /** @return 0=失败, 1=新建据点, 2=追加区域到已有据点 */
    private static int saveCapturePoint(String templateId, MapConfig.CapturePoint cp,
                                             CommandSourceStack source) {
        try {
            Path mapJson = Path.of("templates", templateId, "map.json");
            if (!Files.exists(mapJson)) {
                source.sendFailure(Component.literal("§c找不到地图: " + templateId));
                return 0;
            }
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            if (config.capturePoints == null) {
                config.capturePoints = new java.util.ArrayList<>();
            }
            // 检查是否已存在同名据点：存在则叠加区域，否则新建
            MapConfig.CapturePoint existing = null;
            for (MapConfig.CapturePoint c : config.capturePoints) {
                if (c.name.equalsIgnoreCase(cp.name)) {
                    existing = c;
                    break;
                }
            }
            int result;
            if (existing != null) {
                // 同名据点已存在，把新区间追加进去
                if (cp.zones != null) {
                    if (existing.zones == null) existing.zones = new java.util.ArrayList<>();
                    existing.zones.addAll(cp.zones);
                }
                result = 2;
            } else {
                config.capturePoints.add(cp);
                result = 1;
            }
            Files.writeString(mapJson, MapConfig.GSON.toJson(config));
            return result;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c保存失败: " + e.getMessage()));
            BattleLineSystem.LOGGER.error("CapturePoint save error", e);
            return 0;
        }
    }

    // === 据点重生点子命令 ===

    private static com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> mapSuggest() {
        return (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                builder.suggest(info.id);
            }
            return builder.buildFuture();
        };
    }

    private static int addCpSpawn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                   boolean fixed, boolean unused) throws CommandSyntaxException {
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        String team = StringArgumentType.getString(ctx, "team").toLowerCase();
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!"a".equals(team) && !"b".equals(team)) {
            ctx.getSource().sendFailure(Component.literal("§c队伍只能是 a 或 b"));
            return 0;
        }
        try {
            Path mapJson = Path.of("templates", templateId, "map.json");
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            MapConfig.CapturePoint cp = findCp(config, name);
            if (cp == null) {
                ctx.getSource().sendFailure(Component.literal("§c找不到据点: " + name));
                return 0;
            }

            MapConfig.CapturePoint.CpSpawnEntry entry = new MapConfig.CapturePoint.CpSpawnEntry();
            if (fixed) {
                BlockPos p = player.blockPosition();
                entry.pos = new int[]{p.getX(), p.getY(), p.getZ(),
                        (int) player.getYRot(), (int) player.getXRot()};
            } else {
                int radius = IntegerArgumentType.getInteger(ctx, "radius");
                BlockPos p = player.blockPosition();
                entry.center = new int[]{p.getX(), p.getY(), p.getZ()};
                entry.radius = radius;
            }

            MapConfig.CapturePoint.CpSpawns spawns = "a".equals(team) ? cp.spawnsA : cp.spawnsB;
            spawns.entries.add(entry);
            Files.writeString(mapJson, MapConfig.GSON.toJson(config));
            String teamLabel = "a".equals(team) ? "A队" : "B队";
            String modeLabel = fixed ? "固定" : ("随机半径" + entry.radius);
            ctx.getSource().sendSuccess(() -> Component.literal("§a已添加 " + teamLabel + " " + modeLabel
                    + " 据点重生点 §e#" + (spawns.entries.size() - 1)), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int listCpSpawns(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        try {
            Path mapJson = Path.of("templates", templateId, "map.json");
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            MapConfig.CapturePoint cp = findCp(config, name);
            if (cp == null) {
                ctx.getSource().sendFailure(Component.literal("§c找不到据点: " + name));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§e=== " + name + " 据点重生点 ==="), false);
            listTeamSpawns(ctx, cp.spawnsA, "A队");
            listTeamSpawns(ctx, cp.spawnsB, "B队");
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
            return 0;
        }
    }

    private static void listTeamSpawns(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                        MapConfig.CapturePoint.CpSpawns spawns, String label) {
        if (spawns.entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + label + " §7(无)"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("  §b" + label + ":"), false);
            for (int i = 0; i < spawns.entries.size(); i++) {
                MapConfig.CapturePoint.CpSpawnEntry e = spawns.entries.get(i);
                final String desc;
                if (e.isFixed()) {
                    desc = "固定 [§e" + e.pos[0] + "," + e.pos[1] + "," + e.pos[2] + "§f]";
                } else {
                    desc = "随机 中心§e" + e.center[0] + "," + e.center[1] + "," + e.center[2]
                            + "§f 半径§e" + e.radius;
                }
                final String msg = "    #" + i + " " + desc;
                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            }
        }
    }

    private static int removeCpSpawn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        String team = StringArgumentType.getString(ctx, "team").toLowerCase();
        int index = IntegerArgumentType.getInteger(ctx, "index");
        try {
            Path mapJson = Path.of("templates", templateId, "map.json");
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            MapConfig.CapturePoint cp = findCp(config, name);
            if (cp == null) {
                ctx.getSource().sendFailure(Component.literal("§c找不到据点: " + name));
                return 0;
            }
            MapConfig.CapturePoint.CpSpawns spawns = "a".equals(team) ? cp.spawnsA : cp.spawnsB;
            if (index < 0 || index >= spawns.entries.size()) {
                ctx.getSource().sendFailure(Component.literal("§c索引超出范围"));
                return 0;
            }
            spawns.entries.remove(index);
            Files.writeString(mapJson, MapConfig.GSON.toJson(config));
            String teamLabel = "a".equals(team) ? "A队" : "B队";
            ctx.getSource().sendSuccess(() -> Component.literal("§a已移除" + teamLabel
                    + " 据点重生点 #" + index + " §7(剩余" + spawns.entries.size() + "个)"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearCpSpawns(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String templateId = StringArgumentType.getString(ctx, "template");
        String name = StringArgumentType.getString(ctx, "name");
        String team = StringArgumentType.getString(ctx, "team").toLowerCase();
        try {
            Path mapJson = Path.of("templates", templateId, "map.json");
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            MapConfig.CapturePoint cp = findCp(config, name);
            if (cp == null) {
                ctx.getSource().sendFailure(Component.literal("§c找不到据点: " + name));
                return 0;
            }
            MapConfig.CapturePoint.CpSpawns spawns = "a".equals(team) ? cp.spawnsA : cp.spawnsB;
            int count = spawns.entries.size();
            spawns.entries.clear();
            Files.writeString(mapJson, MapConfig.GSON.toJson(config));
            String teamLabel = "a".equals(team) ? "A队" : "B队";
            ctx.getSource().sendSuccess(() -> Component.literal("§a已清除" + teamLabel
                    + " 所有据点重生点 §7(" + count + "个)"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
            return 0;
        }
    }

    private static MapConfig.CapturePoint findCp(MapConfig config, String name) {
        if (config.capturePoints == null) return null;
        return config.capturePoints.stream()
                .filter(cp -> cp.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
}

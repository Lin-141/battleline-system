package com.battlelinesystem.command;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * /bls spawnpoint 子命令 — 管理地图出生点
 * add <template> <team>      — 将当前位置添加为出生点
 * remove <template> <team> <index> — 移除指定索引的出生点
 * list <template>            — 列出生点
 * clear <template> <team>    — 清除某队所有出生点
 *
 * 载具出生点:
 * vehicle add <template> <type> <team>
 * vehicle remove <template> <type> <team> <index>
 * vehicle list <template>
 * vehicle clear <template> <type> <team>
 */
public class SpawnPointCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> spawnpoint = Commands.literal("spawnpoint")
                .requires(src -> src.hasPermission(2));

        // === 普通出生点 ===

        // /bls spawnpoint add <template> <team>
        spawnpoint.then(Commands.literal("add")
                .then(Commands.argument("template", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            var server = ctx.getSource().getServer();
                            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                builder.suggest(info.id);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("a");
                                    builder.suggest("b");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String templateId = StringArgumentType.getString(ctx, "template");
                                    String team = StringArgumentType.getString(ctx, "team").toLowerCase();

                                    if (!"a".equals(team) && !"b".equals(team)) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c队伍必须是 a 或 b"));
                                        return 0;
                                    }

                                    Path mapJson = Path.of("templates", templateId, "map.json");
                                    if (!Files.exists(mapJson)) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c找不到地图: " + templateId));
                                        return 0;
                                    }

                                    try {
                                        MapConfig config = MapConfig.GSON.fromJson(
                                                Files.readString(mapJson), MapConfig.class);
                                        BlockPos pos = player.blockPosition();
                                        float yaw = player.getYRot();
                                        float pitch = player.getXRot();
                                        config.addSpawnPoint(team, pos, yaw, pitch);
                                        Files.writeString(mapJson, MapConfig.GSON.toJson(config));

                                        int count = team.equals("a")
                                                ? config.spawnPoints.team_a.length
                                                : config.spawnPoints.team_b.length;
                                        String teamLabel = team.equals("a") ? "A队" : "B队";
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("§a" + teamLabel
                                                        + " 出生点已添加: " + pos.toShortString()
                                                        + " §7(共" + count + "个) → " + templateId),
                                                true);
                                        BattleLineSystem.LOGGER.info("出生点已添加: {} {} {} {}",
                                                templateId, team, pos.toShortString(), count);
                                    } catch (Exception e) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c保存失败: " + e.getMessage()));
                                    }
                                    return 1;
                                })
                )
        ));

        // /bls spawnpoint remove <template> <team> <index>
        spawnpoint.then(Commands.literal("remove")
                .then(Commands.argument("template", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            var server = ctx.getSource().getServer();
                            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                builder.suggest(info.id);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("a");
                                    builder.suggest("b");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            String templateId = StringArgumentType.getString(ctx, "template");
                                            String team = StringArgumentType.getString(ctx, "team").toLowerCase();
                                            int index = IntegerArgumentType.getInteger(ctx, "index");

                                            if (!"a".equals(team) && !"b".equals(team)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c队伍必须是 a 或 b"));
                                                return 0;
                                            }

                                            Path mapJson = Path.of("templates", templateId, "map.json");
                                            if (!Files.exists(mapJson)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c找不到地图: " + templateId));
                                                return 0;
                                            }

                                            try {
                                                MapConfig config = MapConfig.GSON.fromJson(
                                                        Files.readString(mapJson), MapConfig.class);
                                                boolean ok = config.removeSpawnPoint(team, index);
                                                if (!ok) {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("§c无效的索引或队伍"));
                                                    return 0;
                                                }
                                                Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                                String teamLabel = team.equals("a") ? "A队" : "B队";
                                                int remain = team.equals("a")
                                                        ? config.spawnPoints.team_a.length
                                                        : config.spawnPoints.team_b.length;
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("§a已移除" + teamLabel
                                                                + " 出生点 #" + index
                                                                + " §7(剩余" + remain + "个)"),
                                                        true);
                                            } catch (Exception e) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c操作失败: " + e.getMessage()));
                                            }
                                            return 1;
                                        })
                                )
                )
        ));

        // /bls spawnpoint list <template>
        spawnpoint.then(Commands.literal("list")
                .then(Commands.argument("template", StringArgumentType.word())
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
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§6=== " + templateId + " 出生点 ==="), false);

                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("  §bA队 (" + config.spawnPoints.team_a.length + "个):"), false);
                                for (int i = 0; i < config.spawnPoints.team_a.length; i++) {
                                    float[] s = config.spawnPoints.team_a[i];
                                    final BlockPos p = new BlockPos((int) s[0], (int) s[1], (int) s[2]);
                                    final int idx = i;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("    #" + idx + " " + p.toShortString()), false);
                                }

                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("  §cB队 (" + config.spawnPoints.team_b.length + "个):"), false);
                                for (int i = 0; i < config.spawnPoints.team_b.length; i++) {
                                    float[] s = config.spawnPoints.team_b[i];
                                    final BlockPos p = new BlockPos((int) s[0], (int) s[1], (int) s[2]);
                                    final int idx = i;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("    #" + idx + " " + p.toShortString()), false);
                                }

                                // 载具出生点
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("  §e载具出生点:"), false);
                                if (config.vehicleSpawnPoints.types.isEmpty()) {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("    §7(无)"), false);
                                } else {
                                    for (java.util.Map.Entry<String, MapConfig.TypeSpawns> e : config.vehicleSpawnPoints.types.entrySet()) {
                                        String vt = e.getKey();
                                        MapConfig.TypeSpawns ts = e.getValue();
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("    §e[" + vt + "] §bA(" + ts.team_a.length + ") §cB(" + ts.team_b.length + ")"), false);
                                    }
                                }
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§c读取地图配置失败: " + e.getMessage()));
                            }
                            return 1;
                        })
        ));

        // /bls spawnpoint clear <template> <team>
        spawnpoint.then(Commands.literal("clear")
                .then(Commands.argument("template", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            var server = ctx.getSource().getServer();
                            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                                builder.suggest(info.id);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("a");
                                    builder.suggest("b");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String templateId = StringArgumentType.getString(ctx, "template");
                                    String team = StringArgumentType.getString(ctx, "team").toLowerCase();

                                    if (!"a".equals(team) && !"b".equals(team)) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c队伍必须是 a 或 b"));
                                        return 0;
                                    }

                                    Path mapJson = Path.of("templates", templateId, "map.json");
                                    if (!Files.exists(mapJson)) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c找不到地图: " + templateId));
                                        return 0;
                                    }

                                    try {
                                        MapConfig config = MapConfig.GSON.fromJson(
                                                Files.readString(mapJson), MapConfig.class);
                                        if (team.equals("a")) {
                                            config.spawnPoints.team_a = new float[0][];
                                        } else {
                                            config.spawnPoints.team_b = new float[0][];
                                        }
                                        Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                        String teamLabel = team.equals("a") ? "A队" : "B队";
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("§a已清除" + teamLabel
                                                        + " 所有出生点"),
                                                true);
                                    } catch (Exception e) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§c操作失败: " + e.getMessage()));
                                    }
                                    return 1;
                                })
                )
        ));

        // === 载具出生点 (vehicle) ===

        // /bls spawnpoint vehicle add <template> <type> <team>
        spawnpoint.then(Commands.literal("vehicle")
                .then(Commands.literal("add")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(mapSuggestions())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(vehicleTypeSuggestions())
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(teamSuggestions())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    String templateId = StringArgumentType.getString(ctx, "template");
                                                    String type = StringArgumentType.getString(ctx, "type").toLowerCase();
                                                    String team = StringArgumentType.getString(ctx, "team").toLowerCase();
                                                    if (!"a".equals(team) && !"b".equals(team)) {
                                                        ctx.getSource().sendFailure(Component.literal("§c队伍必须是 a 或 b"));
                                                        return 0;
                                                    }
                                                    if (!VALID_VEHICLE_TYPES.contains(type)) {
                                                        ctx.getSource().sendFailure(Component.literal("§c无效类型，可选: " + String.join(", ", VALID_VEHICLE_TYPES)));
                                                        return 0;
                                                    }
                                                    Path mapJson = Path.of("templates", templateId, "map.json");
                                                    if (!Files.exists(mapJson)) {
                                                        ctx.getSource().sendFailure(Component.literal("§c找不到地图: " + templateId));
                                                        return 0;
                                                    }
                                                    try {
                                                        MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
                                                        BlockPos pos = player.blockPosition();
                                                        float yaw = player.getYRot();
                                                        float pitch = player.getXRot();
                                                        config.addVehicleSpawnPoint(type, team, pos, yaw, pitch);
                                                        Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                                        MapConfig.TypeSpawns ts = config.vehicleSpawnPoints.types.get(type);
                                                        String teamLabel = "a".equals(team) ? "A队" : "B队";
                                                        int count = ts != null ? ("a".equals(team) ? ts.team_a.length : ts.team_b.length) : 0;
                                                        ctx.getSource().sendSuccess(() -> Component.literal("§a" + teamLabel
                                                                + " " + type + " 载具出生点已添加: " + pos.toShortString()
                                                                + " §7(共" + count + "个) → " + templateId), true);
                                                    } catch (Exception e) {
                                                        ctx.getSource().sendFailure(Component.literal("§c保存失败: " + e.getMessage()));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                // /bls spawnpoint vehicle remove <template> <type> <team> <index>
                .then(Commands.literal("remove")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(mapSuggestions())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(vehicleTypeSuggestions())
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(teamSuggestions())
                                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> {
                                                            String templateId = StringArgumentType.getString(ctx, "template");
                                                            String type = StringArgumentType.getString(ctx, "type").toLowerCase();
                                                            String team = StringArgumentType.getString(ctx, "team").toLowerCase();
                                                            int index = IntegerArgumentType.getInteger(ctx, "index");
                                                            if (!"a".equals(team) && !"b".equals(team)) {
                                                                ctx.getSource().sendFailure(Component.literal("§c队伍必须是 a 或 b"));
                                                                return 0;
                                                            }
                                                            Path mapJson = Path.of("templates", templateId, "map.json");
                                                            if (!Files.exists(mapJson)) {
                                                                ctx.getSource().sendFailure(Component.literal("§c找不到地图: " + templateId));
                                                                return 0;
                                                            }
                                                            try {
                                                                MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
                                                                if (!config.removeVehicleSpawnPoint(type, team, index)) {
                                                                    ctx.getSource().sendFailure(Component.literal("§c无效的索引或队伍/类型"));
                                                                    return 0;
                                                                }
                                                                Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                                                MapConfig.TypeSpawns ts = config.vehicleSpawnPoints.types.get(type);
                                                                int remain = ts != null ? ("a".equals(team) ? ts.team_a.length : ts.team_b.length) : 0;
                                                                String teamLabel = "a".equals(team) ? "A队" : "B队";
                                                                ctx.getSource().sendSuccess(() -> Component.literal("§a已移除" + teamLabel
                                                                        + " " + type + " 载具出生点 #" + index + " §7(剩余" + remain + "个)"), true);
                                                            } catch (Exception e) {
                                                                ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
                                                            }
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )
                // /bls spawnpoint vehicle list <template>
                .then(Commands.literal("list")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(mapSuggestions())
                                .executes(ctx -> listVehicleSpawns(ctx, StringArgumentType.getString(ctx, "template")))
                        )
                )
                // /bls spawnpoint vehicle clear <template> <type> <team>
                .then(Commands.literal("clear")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(mapSuggestions())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(vehicleTypeSuggestions())
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(teamSuggestions())
                                                .executes(ctx -> {
                                                    String templateId = StringArgumentType.getString(ctx, "template");
                                                    String type = StringArgumentType.getString(ctx, "type").toLowerCase();
                                                    String team = StringArgumentType.getString(ctx, "team").toLowerCase();
                                                    if (!"a".equals(team) && !"b".equals(team)) {
                                                        ctx.getSource().sendFailure(Component.literal("§c队伍必须是 a 或 b"));
                                                        return 0;
                                                    }
                                                    Path mapJson = Path.of("templates", templateId, "map.json");
                                                    if (!Files.exists(mapJson)) {
                                                        ctx.getSource().sendFailure(Component.literal("§c找不到地图: " + templateId));
                                                        return 0;
                                                    }
                                                    try {
                                                        MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
                                                        config.clearVehicleSpawnPoints(type, team);
                                                        Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                                                        String teamLabel = "a".equals(team) ? "A队" : "B队";
                                                        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除" + teamLabel
                                                                + " " + type + " 所有载具出生点"), true);
                                                    } catch (Exception e) {
                                                        ctx.getSource().sendFailure(Component.literal("§c操作失败: " + e.getMessage()));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );

        BattleLineSystem.LOGGER.info("[SpawnPointCommand] build() completed, vehicle branch registered");
        return spawnpoint;
    }

    // === 有效载具类型 ===
    private static final java.util.Set<String> VALID_VEHICLE_TYPES = java.util.Set.of(
            "plane", "tank", "apc", "helicopter", "boat", "car", "land");

    // === 辅助方法 ===

    private static SuggestionProvider<CommandSourceStack> mapSuggestions() {
        return (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                builder.suggest(info.id);
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> vehicleTypeSuggestions() {
        return (ctx, builder) -> {
            for (String t : VALID_VEHICLE_TYPES) {
                builder.suggest(t);
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> teamSuggestions() {
        return (ctx, builder) -> {
            builder.suggest("a");
            builder.suggest("b");
            return builder.buildFuture();
        };
    }

    private static int listVehicleSpawns(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String templateId) {
        Path mapJson = Path.of("templates", templateId, "map.json");
        if (!Files.exists(mapJson)) {
            ctx.getSource().sendFailure(Component.literal("§c找不到地图: " + templateId));
            return 0;
        }
        try {
            MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
            ctx.getSource().sendSuccess(() -> Component.literal("§e=== " + templateId + " 载具出生点 ==="), false);
            if (config.vehicleSpawnPoints.types.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §7(无)"), false);
            } else {
                for (java.util.Map.Entry<String, MapConfig.TypeSpawns> e : config.vehicleSpawnPoints.types.entrySet()) {
                    String vt = e.getKey();
                    MapConfig.TypeSpawns ts = e.getValue();
                    ctx.getSource().sendSuccess(() -> Component.literal("  §e[" + vt + "]"), false);
                    if (ts.team_a.length > 0) {
                        for (int i = 0; i < ts.team_a.length; i++) {
                            float[] s = ts.team_a[i];
                            final BlockPos p = new BlockPos((int) s[0], (int) s[1], (int) s[2]);
                            final int idx = i;
                            ctx.getSource().sendSuccess(() -> Component.literal("    §bA #" + idx + " " + p.toShortString()), false);
                        }
                    }
                    if (ts.team_b.length > 0) {
                        for (int i = 0; i < ts.team_b.length; i++) {
                            float[] s = ts.team_b[i];
                            final BlockPos p = new BlockPos((int) s[0], (int) s[1], (int) s[2]);
                            final int idx = i;
                            ctx.getSource().sendSuccess(() -> Component.literal("    §cB #" + idx + " " + p.toShortString()), false);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c读取地图配置失败: " + e.getMessage()));
        }
        return 1;
    }
}
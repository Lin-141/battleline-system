package com.battlelinesystem.world;

import com.battlelinesystem.BattleLineSystem;
import com.google.common.collect.ImmutableList;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 游戏世界管理器 — 管理地图模板加载、游戏副本创建和清理
 *
 * 流程：
 *   模板目录: run/templates/<map_id>/
 *   游戏副本: run/saves/<世界名>/dimensions/battlelinesystem/game_<uuid>/
 *
 *   创建: 异步复制模板 → 主线程加载维度 → 传送玩家
 *   清理: 传送回主世界 → 卸载维度 → 删除文件夹
 */
public class GameWorldManager {

    public static final String TEMPLATES_DIR = "templates";
    public static final String GAME_WORLD_PREFIX = "game_";

    /** 当前活跃的游戏副本 */
    private static final List<ResourceKey<Level>> activeGameWorlds = new ArrayList<>();

    /**
     * 获取所有活跃游戏世界的 key 列表（线程安全）
     */
    public static List<ResourceKey<Level>> getActiveGameWorlds() {
        synchronized (activeGameWorlds) {
            return new ArrayList<>(activeGameWorlds);
        }
    }

    /**
     * 扫描 templates/ 下所有已安装的地图
     */
    public static List<MapInfo> getMapsForMode(MinecraftServer server, String mode) {
        Path templatesDir = getTemplatesDir(server);
        List<MapInfo> maps = new ArrayList<>();

        if (!Files.isDirectory(templatesDir)) return maps;

        try {
            Files.list(templatesDir).filter(Files::isDirectory).forEach(dir -> {
                MapConfig config = MapConfig.load(dir);
                if (config.mode.equals(mode) || mode == null || mode.isEmpty()) {
                    maps.add(new MapInfo(dir.getFileName().toString(), config));
                }
            });
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("扫描模板目录失败", e);
        }

        return maps;
    }

    /**
     * 复制模板并加载游戏世界（必须在服务端线程调用）
     */
    public static ServerLevel createAndLoadWorld(
            MinecraftServer server, String templateId, ResourceKey<Level> worldKey, MapConfig config) {

        try {
            Path templatesDir = getTemplatesDir(server);
            Path sourceDir = templatesDir.resolve(templateId);
            Path gameWorldDir = getGameWorldDir(server, worldKey);

            if (!Files.isDirectory(sourceDir)) {
                BattleLineSystem.LOGGER.error("模板不存在: {}", sourceDir);
                return null;
            }

            // 复制模板文件（排除 level.dat 避免维度冲突）
            Files.createDirectories(gameWorldDir);
            FileUtils.copyDirectory(sourceDir.toFile(), gameWorldDir.toFile(), f -> {
                String name = f.getName();
                return !name.equals("map.json")
                        && !name.equals("level.dat")
                        && !name.equals("level.dat_old")
                        && !name.equals("session.lock");
            });
            BattleLineSystem.LOGGER.info("模板复制完成: {} → {}", templateId, gameWorldDir);

            // 加载世界
            ServerLevel world = loadGameWorld(server, worldKey);
            if (world == null) return null;

            synchronized (activeGameWorlds) {
                activeGameWorlds.add(worldKey);
            }

            // 标记出生点区块为强制加载 + tick 初始化世界（不阻塞等待 FULL）
            initSpawnChunks(world, config);
            BattleLineSystem.LOGGER.info("游戏世界已创建: {}", worldKey.location());
            return world;
        } catch (Throwable t) {
            BattleLineSystem.LOGGER.error("createAndLoadWorld 失败", t);
            return null;
        }
    }

    /**
     * 标记出生点区块为强制加载 + tick 初始化世界（不阻塞等待 FULL，避免死锁）
     */
    private static void initSpawnChunks(ServerLevel world, MapConfig config) {
        int radius = 3;
        int[] spawnX = {config.getTeamASpawn().getX(), config.getTeamBSpawn().getX()};
        int[] spawnZ = {config.getTeamASpawn().getZ(), config.getTeamBSpawn().getZ()};

        // 标记所有强制加载区块
        for (int s = 0; s < 2; s++) {
            int cx = spawnX[s] >> 4;
            int cz = spawnZ[s] >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    world.setChunkForced(cx + dx, cz + dz, true);
                }
            }
        }

        // tick 世界若干次让 chunk system 初始化（不阻塞等待 FULL 状态）
        for (int i = 0; i < 10; i++) {
            world.tick(() -> true);
        }

        BattleLineSystem.LOGGER.info("已标记出生点强制区块并初始化");
    }

    // === 反射缓存（绕过 Mixin 模块限制） ===

    private static Field levelsField;
    private static Field storageSourceField;
    private static Field worldDataField;

    static {
        initReflection();
    }

    private static void initReflection() {
        levelsField = findField(MinecraftServer.class, "f_129762_", "levels");
        storageSourceField = findField(MinecraftServer.class, "f_129744_", "storageSource");
        worldDataField = findField(MinecraftServer.class, "f_129749_", "worldData");
        if (levelsField == null || storageSourceField == null || worldDataField == null) {
            BattleLineSystem.LOGGER.error("反射初始化失败: levels={}, storageSource={}, worldData={}",
                    levelsField != null, storageSourceField != null, worldDataField != null);
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        BattleLineSystem.LOGGER.warn("反射找不到字段: {} 候选名={}", clazz.getSimpleName(), java.util.Arrays.toString(names));
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> getLevelsMap(MinecraftServer server) {
        try {
            return (Map<ResourceKey<Level>, ServerLevel>) levelsField.get(server);
        } catch (Exception e) {
            throw new RuntimeException("无法访问 levels", e);
        }
    }

    private static LevelStorageSource.LevelStorageAccess getStorageSource(MinecraftServer server) {
        try {
            return (LevelStorageSource.LevelStorageAccess) storageSourceField.get(server);
        } catch (Exception e) {
            throw new RuntimeException("无法访问 storageSource", e);
        }
    }

    private static WorldData getWorldData(MinecraftServer server) {
        try {
            return (WorldData) worldDataField.get(server);
        } catch (Exception e) {
            throw new RuntimeException("无法访问 worldData", e);
        }
    }

    // === 世界创建与销毁 ===

    /**
     * 直接构造 ServerLevel 并注册到 MinecraftServer（不使用 Mixin）
     * 使用 Util.getMainWorkerExecutor() 避免 ioPool 死锁
     */
    private static ServerLevel loadGameWorld(MinecraftServer server, ResourceKey<Level> worldKey) {
        LevelStem stem = getOverworldStem(server);

        try {
            WorldData wd = getWorldData(server);
            boolean debugWorld = wd.isDebugWorld();
            long seed = BiomeManager.obfuscateSeed(wd.worldGenOptions().seed());
            DerivedLevelData derivedData = new DerivedLevelData(wd, wd.overworldData());

            // 无操作进度监听器（避免 StoringChunkProgressListener 阻塞）
            net.minecraft.server.level.progress.ChunkProgressListener noopListener =
                    new net.minecraft.server.level.progress.ChunkProgressListener() {
                        public void updateSpawnPos(ChunkPos c) {}
                        public void onStatusChange(ChunkPos c, ChunkStatus s) {}
                        public void start() {}
                        public void stop() {}
                    };

            // 用 ForkJoinPool 而不是 server.executor(ioPool)，避免运行时死锁
            ServerLevel world = new ServerLevel(
                    server,
                    Util.backgroundExecutor(),
                    getStorageSource(server),
                    derivedData,
                    worldKey,
                    stem,
                    noopListener,
                    debugWorld,
                    seed,
                    ImmutableList.of(),
                    true,
                    null
            );

            getLevelsMap(server).put(worldKey, world);

            // 立即 tick 初始化 chunk source 调度器
            world.tick(() -> true);

            BattleLineSystem.LOGGER.info("游戏世界已加载: {}", worldKey.location());
            return world;
        } catch (Exception e) {
            BattleLineSystem.LOGGER.error("创建游戏世界失败: {}", worldKey.location(), e);
            return null;
        }
    }

    /**
     * 从 MinecraftServer 卸载世界
     */
    private static void removeWorldFromServer(MinecraftServer server, ResourceKey<Level> worldKey) {
        ServerLevel removed = getLevelsMap(server).remove(worldKey);
        if (removed != null) {
            BattleLineSystem.LOGGER.info("游戏世界已卸载: {}", worldKey.location());
        }
    }

    /**
     * 传送所有玩家到游戏世界
     */
    public static void teleportAllPlayers(ServerLevel gameWorld, MapConfig config) {
        List<ServerPlayer> players = gameWorld.getServer().getPlayerList().getPlayers();
        int teamA = 0;
        for (ServerPlayer player : players) {
            if (teamA < players.size() / 2 + players.size() % 2) {
                // 队伍A
                player.teleportTo(gameWorld,
                        config.getTeamASpawn().getX() + 0.5,
                        config.getTeamASpawn().getY(),
                        config.getTeamASpawn().getZ() + 0.5,
                        MapConfig.getSpawnYaw(config.spawnPoints.team_a, 0),
                        MapConfig.getSpawnPitch(config.spawnPoints.team_a, 0));
            } else {
                // 队伍B
                player.teleportTo(gameWorld,
                        config.getTeamBSpawn().getX() + 0.5,
                        config.getTeamBSpawn().getY(),
                        config.getTeamBSpawn().getZ() + 0.5,
                        MapConfig.getSpawnYaw(config.spawnPoints.team_b, 0),
                        MapConfig.getSpawnPitch(config.spawnPoints.team_b, 0));
            }
            teamA++;
        }
    }

    /**
     * 传送所有玩家回主世界
     */
    public static void teleportAllToOverworld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (ServerPlayer player : players) {
            // 只传送在游戏世界中的玩家
            ResourceLocation loc = player.level().dimension().location();
            if (loc.getNamespace().equals(BattleLineSystem.MOD_ID)) {
                player.setGameMode(GameType.SURVIVAL);
                try {
                    player.teleportTo(overworld,
                            spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                } catch (Exception e) {
                    BattleLineSystem.LOGGER.error("传送 {} 回主世界失败: {}", player.getName().getString(), e.toString());
                }
            }
        }
    }

    /**
     * 清理指定游戏世界：踢出玩家 → 卸载 → 删除文件
     */
    public static void destroyGameWorld(MinecraftServer server, ResourceKey<Level> worldKey) {
        ServerLevel world = server.getLevel(worldKey);
        if (world != null) {
            // 标记不保存，阻止后续写入
            world.noSave = true;
            // 卸载
            removeWorldFromServer(server, worldKey);
            BattleLineSystem.LOGGER.info("[清理] 已标记不保存并卸载: {}", worldKey.location());
        } else {
            BattleLineSystem.LOGGER.warn("[清理] getLevel 返回 null: {}", worldKey.location());
        }

        // 从活跃列表移除（立即，防止 tick 循环继续访问）
        synchronized (activeGameWorlds) {
            activeGameWorlds.remove(worldKey);
        }

        // 延迟删除文件：给 MC 一点时间释放文件句柄
        Path worldDir = getGameWorldDir(server, worldKey);
        server.execute(() -> {
            server.execute(() -> {
                try {
                    if (Files.isDirectory(worldDir)) {
                        FileUtils.deleteDirectory(worldDir.toFile());
                        BattleLineSystem.LOGGER.info("[清理] 游戏世界文件已删除: {}", worldDir);
                    } else {
                        BattleLineSystem.LOGGER.info("[清理] worldDir 不存在，跳过删除: {}", worldDir);
                    }
                } catch (IOException e) {
                    BattleLineSystem.LOGGER.error("[清理] 删除失败 (将重试一次): {} — {}", worldDir, e.getMessage());
                    // 再等一个 tick 重试
                    server.execute(() -> {
                        try {
                            if (Files.isDirectory(worldDir)) {
                                FileUtils.deleteDirectory(worldDir.toFile());
                                BattleLineSystem.LOGGER.info("[清理] 重试成功: {}", worldDir);
                            }
                        } catch (IOException e2) {
                            BattleLineSystem.LOGGER.error("[清理] 重试仍然失败，文件可能残留: {} — {}", worldDir, e2.getMessage());
                        }
                    });
                }
            });
        });
    }

    /**
     * 清理所有活跃游戏世界（服务端关闭时调用）
     */
    public static void cleanupAll(MinecraftServer server) {
        List<ResourceKey<Level>> copy;
        synchronized (activeGameWorlds) {
            copy = new ArrayList<>(activeGameWorlds);
        }
        for (ResourceKey<Level> key : copy) {
            // 跳过编辑世界
            if (key.location().getPath().startsWith("edit_")) continue;
            destroyGameWorld(server, key);
        }
    }

    /**
     * 编辑模式：将游戏世界区块保存回模板目录
     */
    public static boolean saveTemplateBack(MinecraftServer server, String templateId) {
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                new ResourceLocation(BattleLineSystem.MOD_ID, "edit_" + templateId));

        ServerLevel world = server.getLevel(worldKey);
        if (world == null) {
            BattleLineSystem.LOGGER.error("编辑世界不存在: {}", templateId);
            return false;
        }

        try {
            // 强制保存所有区块
            world.save(null, true, false);

            // 复制 region/ 回模板
            Path worldDir = getGameWorldDir(server, worldKey);
            Path templateDir = getTemplatesDir(server).resolve(templateId);

            Path worldRegion = worldDir.resolve("region");
            Path templateRegion = templateDir.resolve("region");
            if (Files.isDirectory(templateRegion)) {
                FileUtils.cleanDirectory(templateRegion.toFile());
            }
            Files.createDirectories(templateRegion);
            FileUtils.copyDirectory(worldRegion.toFile(), templateRegion.toFile());

            // 复制 entities/ 回模板
            Path worldEntities = worldDir.resolve("entities");
            Path templateEntities = templateDir.resolve("entities");
            if (Files.isDirectory(worldEntities)) {
                if (Files.isDirectory(templateEntities)) {
                    FileUtils.cleanDirectory(templateEntities.toFile());
                }
                Files.createDirectories(templateEntities);
                FileUtils.copyDirectory(worldEntities.toFile(), templateEntities.toFile());
            }

            // 复制 poi/ 回模板
            Path worldPoi = worldDir.resolve("poi");
            Path templatePoi = templateDir.resolve("poi");
            if (Files.isDirectory(worldPoi)) {
                if (Files.isDirectory(templatePoi)) {
                    FileUtils.cleanDirectory(templatePoi.toFile());
                }
                Files.createDirectories(templatePoi);
                FileUtils.copyDirectory(worldPoi.toFile(), templatePoi.toFile());
            }

            BattleLineSystem.LOGGER.info("模板已保存: {}", templateId);
            return true;
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("保存模板失败: {}", templateId, e);
            return false;
        }
    }

    /**
     * 生成唯一的世界 key
     */
    public static ResourceKey<Level> generateWorldKey() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(BattleLineSystem.MOD_ID, GAME_WORLD_PREFIX + uuid));
    }

    // ---- 路径工具方法 ----

    static Path getTemplatesDir(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve(TEMPLATES_DIR);
    }

    static Path getGameWorldDir(MinecraftServer server, ResourceKey<Level> worldKey) {
        ResourceLocation loc = worldKey.location();
        return server.getWorldPath(
                new net.minecraft.world.level.storage.LevelResource(
                        "dimensions/" + loc.getNamespace() + "/" + loc.getPath()));
    }

    private static LevelStem getOverworldStem(MinecraftServer server) {
        return server.registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM)
                .get(LevelStem.OVERWORLD);
    }

    // ---- 数据类 ----

    public static class MapInfo {
        public final String id;
        public final MapConfig config;

        MapInfo(String id, MapConfig config) {
            this.id = id;
            this.config = config;
        }
    }
}

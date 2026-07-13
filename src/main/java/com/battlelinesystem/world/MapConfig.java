package com.battlelinesystem.world;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 地图模板配置（从 templates/xxx/map.json 读取）
 */
public class MapConfig {

    public static final Gson GSON = new Gson();

    /** 显示名称 */
    public String name = "未命名地图";

    /** 所属模式 */
    public String mode = "征服";

    /** 描述文字 */
    public String description = "";

    /** 最少玩家数 */
    @SerializedName("min_players")
    public int minPlayers = 2;

    /** 最多玩家数 */
    @SerializedName("max_players")
    public int maxPlayers = 32;

    /** 初始分数 */
    @SerializedName("initial_score")
    public int initialScore = 200;

    /** 是否启用跨队平衡 */
    @SerializedName("team_auto_balance")
    public boolean teamAutoBalance = true;

    /** 本图参战阵营（含描述） */
    public List<FactionEntry> factions = new ArrayList<>();

    /** A队可用阵营池 */
    @SerializedName("faction_pool_a")
    public List<String> factionPoolA = new ArrayList<>();

    /** B队可用阵营池 */
    @SerializedName("faction_pool_b")
    public List<String> factionPoolB = new ArrayList<>();

    /** 据点列表 */
    @SerializedName("capture_points")
    public List<CapturePoint> capturePoints = new ArrayList<>();

    /** 获胜条件分数（需小于0才结束，保留兼容） */
    @SerializedName("win_score")
    public int winScore = 0;

    /** 出生点 */
    public SpawnPoints spawnPoints = new SpawnPoints();

    /** 载具出生点（不显示，独立于玩家出生点） */
    @SerializedName("vehicle_spawn_points")
    public VehicleSpawnPoints vehicleSpawnPoints = new VehicleSpawnPoints();

    /** 全局脚本 */
    @SerializedName("global_scripts")
    public List<GlobalScript> globalScripts = new ArrayList<>();

    /** 战场边界（多边形顶点，玩家出界会死亡倒计时） */
    @SerializedName("battlefield_boundary")
    public List<int[]> battlefieldBoundary = new ArrayList<>();

    /** 禁区列表（多边形区域，指定队伍进入后死亡倒计时） */
    @SerializedName("forbidden_zones")
    public List<ForbiddenZone> forbiddenZones = new ArrayList<>();

    // === 背景音乐 ===
    /** 开局音乐（选完地图后播放，ResourceLocation 格式如 "minecraft:music.creative"） */
    @SerializedName("start_music")
    public String startMusic = "";
    /** 胜利音乐（游戏结束时胜方听到的） */
    @SerializedName("victory_music")
    public String victoryMusic = "";
    /** 失败音乐（游戏结束时败方听到的） */
    @SerializedName("defeat_music")
    public String defeatMusic = "";
    /** 濒临结束音乐（任一方分数低于此阈值时循环播放） */
    @SerializedName("near_end_music")
    public String nearEndMusic = "";
    /** 濒临结束阈值（分数低于此值时触发 near_end_music，0=禁用） */
    @SerializedName("near_end_threshold")
    public int nearEndThreshold = 0;

    // === 游戏时限 ===
    /** 游戏时间限制（分钟），0=不限时 */
    @SerializedName("time_limit_minutes")
    public int timeLimitMinutes = 0;
    /** 时间到后的胜利规则: ""=不限时, "score"=比分高者胜, "A"=A队胜, "B"=B队胜 */
    @SerializedName("time_up_rule")
    public String timeUpRule = "";

    public static class GlobalScript {
        /** 触发类型: "game_start"(开局), "game_end"(结束), "first_deploy"(首次部署), "score_ge"(分数>=), "score_le"(分数<=), "time_ge"(时间>=) */
        public String trigger = "score_ge";
        /** 触发阈值（分数或秒数） */
        public int value = 0;
        /** 比较的分数队伍: "A" 或 "B"（仅分数类触发有效） */
        public String team = "A";
        /** 执行的命令列表 */
        public List<String> commands = new ArrayList<>();
        /** 是否已执行过（运行时字段，不序列化） */
        public transient boolean executed = false;
    }

    public static class SpawnPoints {
        public float[][] team_a = {{0, 65, 0}};
        public float[][] team_b = {{0, 65, 0}};
    }

    /** 载具出生点（按类型索引，不渲染） */
    public static class VehicleSpawnPoints {
        /** 按载具类型的出生点: "plane", "tank", "apc", "helicopter", "boat", "car", "land" */
        public java.util.LinkedHashMap<String, TypeSpawns> types = new java.util.LinkedHashMap<>();
    }

    /** 单类型出生点 */
    public static class TypeSpawns {
        public float[][] team_a = {};
        public float[][] team_b = {};
    }

    /** ground 类型集：这些类型没有专属出生点时回退到 "land" */
    private static final java.util.Set<String> GROUND_TYPES = java.util.Set.of("tank", "apc", "car");

    /** 据点定义 */
    public static class CapturePoint {
        public String name;
        /** HUD 显示名称，为 null 时使用 name */
        @SerializedName("display_name")
        public String displayName;
        /** 前置据点名称列表：这些据点必须被完全占领后，才能开始占领本据点 */
        @SerializedName("prerequisites")
        public List<String> prerequisites = new ArrayList<>();
        /** 进攻方：指定哪个队伍不受据点锁定影响，可以占领已被完全占领的据点（null/"A"/"B"） */
        @SerializedName("attacker_team")
        public String attackerTeam;
        /** 初始归属：开局时据点属于哪一方（null=中立 / "A" / "B"），用于防守方初始占点 */
        @SerializedName("initial_owner")
        public String initialOwner;
        /** 多区域：每个区域是两个BlockPos [min, max] */
        @SerializedName("zones")
        public List<int[][]> zones = new ArrayList<>();

        /** 占领后需删除的区域（方块区域） */
        @SerializedName("destroy_regions")
        public List<int[][]> destroyRegions = new ArrayList<>();

        /** 据点脚本 */
        @SerializedName("scripts")
        public Scripts scripts = new Scripts();

        /** A队重生点（为空时使用 zones 随机） */
        @SerializedName("spawns_a")
        public CpSpawns spawnsA = new CpSpawns();
        /** B队重生点（为空时使用 zones 随机） */
        @SerializedName("spawns_b")
        public CpSpawns spawnsB = new CpSpawns();

        public static class Scripts {
            /** A队占领时执行的命令列表 */
            @SerializedName("on_capture_a")
            public List<String> onCaptureA = new ArrayList<>();
            /** B队占领时执行的命令列表 */
            @SerializedName("on_capture_b")
            public List<String> onCaptureB = new ArrayList<>();
            /** 被争夺时执行的命令列表（持续） */
            @SerializedName("on_contest")
            public List<String> onContest = new ArrayList<>();
            /** 失去占领时执行的命令列表 */
            @SerializedName("on_uncapture")
            public List<String> onUncapture = new ArrayList<>();
        }

        public CapturePoint() {}

        /** 单区域构造器（兼容旧命令，自动排序min/max） */
        public CapturePoint(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.name = name;
            this.zones = new ArrayList<>();
            this.zones.add(new int[][]{
                {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)},
                {Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)}
            });
        }

        public void addZone(int x1, int y1, int z1, int x2, int y2, int z2) {
            zones.add(new int[][]{
                {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)},
                {Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)}
            });
        }

        public int zoneCount() { return zones.size(); }

        /** 检查一个坐标是否在该据点内 */
        public boolean isInside(int x, int y, int z) {
            for (int[][] zone : zones) {
                int[] min = zone[0], max = zone[1];
                if (x >= min[0] && x <= max[0] &&
                    y >= min[1] && y <= max[1] &&
                    z >= min[2] && z <= max[2]) {
                    return true;
                }
            }
            return false;
        }

        /** 是否区域（多格）而非单点 */
        public boolean isRegion() {
            return zones.size() > 1 || (zones.size() == 1 && (
                zones.get(0)[0][0] != zones.get(0)[1][0] ||
                zones.get(0)[0][1] != zones.get(0)[1][1] ||
                zones.get(0)[0][2] != zones.get(0)[1][2]));
        }

        /** 获取 HUD 显示名称：displayName 优先，否则 name */
        public String getDisplayName() {
            return (displayName != null && !displayName.isEmpty()) ? displayName : name;
        }

        /** 获取3D悬浮框中心位置（与渲染使用的相同坐标） */
        public BlockPos getDisplayCenter() {
            if (zones == null || zones.isEmpty()) return BlockPos.ZERO;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int[][] z : zones) {
                int x1 = z[0][0], x2 = z[1][0];
                int y1 = z[0][1], y2 = z[1][1];
                int z1 = z[0][2], z2 = z[1][2];
                minX = Math.min(minX, Math.min(x1, x2));
                maxX = Math.max(maxX, Math.max(x1, x2) + 1);
                minZ = Math.min(minZ, Math.min(z1, z2));
                maxZ = Math.max(maxZ, Math.max(z1, z2) + 1);
                maxY = Math.max(maxY, Math.max(y1, y2) + 1);
            }
            return new BlockPos((int)((minX + maxX) / 2.0), maxY + 16, (int)((minZ + maxZ) / 2.0));
        }

        public BlockPos blockPos() {
            if (zones.isEmpty()) return BlockPos.ZERO;
            int[] min = zones.get(0)[0];
            return new BlockPos(min[0], min[1], min[2]);
        }

        public BlockPos blockPos2() {
            if (zones.isEmpty()) return BlockPos.ZERO;
            int[] max = zones.get(0)[1];
            return new BlockPos(max[0], max[1], max[2]);
        }

        /** 检查一个坐标是否在该据点的任何区域内 */
        public boolean contains(BlockPos pos) {
            return isInside(pos.getX(), pos.getY(), pos.getZ());
        }

        /** 在所有区域内随机选一个坐标作为重生点（旧版兼容） */
        public BlockPos getRandomPos() {
            if (zones.isEmpty()) return BlockPos.ZERO;
            int[][] z = zones.get((int)(Math.random() * zones.size()));
            int x1 = z[0][0], x2 = z[1][0];
            int y1 = z[0][1], y2 = z[1][1];
            int z1 = z[0][2], z2 = z[1][2];
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            int rx = minX + (int)(Math.random() * (maxX - minX + 1));
            int rz = minZ + (int)(Math.random() * (maxZ - minZ + 1));
            return new BlockPos(rx, minY + 1, rz);
        }

        /** 根据队伍获取重生点：优先 spawns_a/spawns_b，否则回退到 zones 随机 */
        public BlockPos getSpawnPos(String team) {
            CpSpawns spawns = "A".equals(team) ? spawnsA : spawnsB;
            if (spawns != null && !spawns.entries.isEmpty()) {
                lastSpawnTeam = team;
                int idx = spawnSeqIdx.getOrDefault(team, -1);
                idx = (idx + 1) % spawns.entries.size();
                spawnSeqIdx.put(team, idx);
                CpSpawnEntry e = spawns.entries.get(idx);
                lastSpawnEntry = e;
                if (e.isFixed()) {
                    return new BlockPos(e.pos[0], e.pos[1], e.pos[2]);
                } else if (e.center != null && e.radius > 0) {
                    int r = e.radius;
                    int rx = e.center[0] + (int)(Math.random() * (r * 2 + 1)) - r;
                    int rz = e.center[2] + (int)(Math.random() * (r * 2 + 1)) - r;
                    return new BlockPos(rx, e.center[1], rz);
                } else {
                    return getRandomPos();
                }
            }
            return getRandomPos();
        }

        /** 获取最近一次 getSpawnPos 所选条目的 yaw */
        public float getLastSpawnYaw() {
            if (lastSpawnEntry != null && lastSpawnEntry.isFixed() && lastSpawnEntry.pos.length >= 5)
                return lastSpawnEntry.pos[3];
            return 0f;
        }

        /** 获取最近一次 getSpawnPos 所选条目的 pitch */
        public float getLastSpawnPitch() {
            if (lastSpawnEntry != null && lastSpawnEntry.isFixed() && lastSpawnEntry.pos.length >= 5)
                return lastSpawnEntry.pos[4];
            return 0f;
        }

        private transient String lastSpawnTeam;
        private transient CpSpawnEntry lastSpawnEntry;
        private transient java.util.Map<String, Integer> spawnSeqIdx = new java.util.HashMap<>();

        /** 单条重生点配置 */
        public static class CpSpawnEntry {
            /** 固定坐标 [x,y,z,yaw,pitch]（yaw/pitch 可选） */
            public int[] pos;
            /** 随机重生：中心坐标 [x,y,z] */
            public int[] center;
            /** 随机重生：半径（方块数），0=不使用 */
            public int radius;

            public boolean isFixed() { return pos != null; }
        }

        /** 重生点列表 */
        public static class CpSpawns {
            @SerializedName("entries")
            public List<CpSpawnEntry> entries = new ArrayList<>();
        }
    }

    public static class FactionEntry {
        public String id;
        public String description = "";

        public FactionEntry() {}

        public FactionEntry(String id, String description) {
            this.id = id;
            this.description = description;
        }
    }

    /**
     * 从 path 目录下的 map.json 读取配置
     */
    public static MapConfig load(Path templateDir) {
        Path configFile = templateDir.resolve("map.json");
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                return GSON.fromJson(json, MapConfig.class);
            } catch (Exception e) {
                // 读取失败，返回默认配置
            }
        }
        // 没有 map.json，用文件夹名作为地图名
        MapConfig fallback = new MapConfig();
        fallback.name = templateDir.getFileName().toString();
        return fallback;
    }

    public BlockPos getTeamASpawn() {
        if (spawnPoints.team_a == null || spawnPoints.team_a.length == 0) return BlockPos.ZERO;
        return toBlockPos(spawnPoints.team_a[0]);
    }

    public BlockPos getTeamBSpawn() {
        if (spawnPoints.team_b == null || spawnPoints.team_b.length == 0) return BlockPos.ZERO;
        return toBlockPos(spawnPoints.team_b[0]);
    }

    /** 随机A队出生点（仅取位置） */
    public BlockPos getRandomTeamASpawn() {
        if (spawnPoints.team_a == null || spawnPoints.team_a.length == 0) return BlockPos.ZERO;
        int idx = (int)(Math.random() * spawnPoints.team_a.length);
        return toBlockPos(spawnPoints.team_a[idx]);
    }

    /** 随机B队出生点（仅取位置） */
    public BlockPos getRandomTeamBSpawn() {
        if (spawnPoints.team_b == null || spawnPoints.team_b.length == 0) return BlockPos.ZERO;
        int idx = (int)(Math.random() * spawnPoints.team_b.length);
        return toBlockPos(spawnPoints.team_b[idx]);
    }

    /** 根据索引获取出生点位置 */
    public static BlockPos getSpawnPos(float[][] spawns, int index) {
        if (spawns == null || spawns.length == 0) return BlockPos.ZERO;
        if (index < 0 || index >= spawns.length)
            return toBlockPos(spawns[(int)(Math.random() * spawns.length)]);
        return toBlockPos(spawns[index]);
    }

    /** 根据索引获取出生点yaw（水平视角），旧格式（无yaw）返回0 */
    public static float getSpawnYaw(float[][] spawns, int index) {
        if (spawns == null || spawns.length == 0) return 0f;
        int i = (index >= 0 && index < spawns.length) ? index : (int)(Math.random() * spawns.length);
        float[] s = spawns[i];
        return s.length >= 5 ? s[3] : 0f;
    }

    /** 根据索引获取出生点pitch（垂直视角），旧格式（无pitch）返回0 */
    public static float getSpawnPitch(float[][] spawns, int index) {
        if (spawns == null || spawns.length == 0) return 0f;
        int i = (index >= 0 && index < spawns.length) ? index : (int)(Math.random() * spawns.length);
        float[] s = spawns[i];
        return s.length >= 5 ? s[4] : 0f;
    }

    /** 向队伍添加出生点（含视角） */
    public void addSpawnPoint(String team, BlockPos pos, float yaw, float pitch) {
        if ("a".equalsIgnoreCase(team)) {
            float[][] arr = new float[spawnPoints.team_a.length + 1][];
            System.arraycopy(spawnPoints.team_a, 0, arr, 0, spawnPoints.team_a.length);
            arr[spawnPoints.team_a.length] = new float[]{pos.getX(), pos.getY(), pos.getZ(), yaw, pitch};
            spawnPoints.team_a = arr;
        } else if ("b".equalsIgnoreCase(team)) {
            float[][] arr = new float[spawnPoints.team_b.length + 1][];
            System.arraycopy(spawnPoints.team_b, 0, arr, 0, spawnPoints.team_b.length);
            arr[spawnPoints.team_b.length] = new float[]{pos.getX(), pos.getY(), pos.getZ(), yaw, pitch};
            spawnPoints.team_b = arr;
        }
    }

    /** 移除队伍指定索引的出生点 */
    public boolean removeSpawnPoint(String team, int index) {
        float[][] arr;
        if ("a".equalsIgnoreCase(team)) {
            arr = spawnPoints.team_a;
        } else if ("b".equalsIgnoreCase(team)) {
            arr = spawnPoints.team_b;
        } else {
            return false;
        }
        if (arr == null || index < 0 || index >= arr.length) return false;
        float[][] newArr = new float[arr.length - 1][];
        for (int i = 0, j = 0; i < arr.length; i++) {
            if (i != index) { newArr[j] = arr[i]; j++; }
        }
        if ("a".equalsIgnoreCase(team)) {
            spawnPoints.team_a = newArr;
        } else {
            spawnPoints.team_b = newArr;
        }
        return true;
    }

    // === 载具出生点 ===

    /** 添加载具出生点（含部署朝向） */
    public void addVehicleSpawnPoint(String type, String team, BlockPos pos, float yaw, float pitch) {
        TypeSpawns ts = vehicleSpawnPoints.types.computeIfAbsent(type, k -> new TypeSpawns());
        if ("a".equalsIgnoreCase(team)) {
            float[][] arr = new float[ts.team_a.length + 1][];
            System.arraycopy(ts.team_a, 0, arr, 0, ts.team_a.length);
            arr[ts.team_a.length] = new float[]{pos.getX(), pos.getY(), pos.getZ(), yaw, pitch};
            ts.team_a = arr;
        } else if ("b".equalsIgnoreCase(team)) {
            float[][] arr = new float[ts.team_b.length + 1][];
            System.arraycopy(ts.team_b, 0, arr, 0, ts.team_b.length);
            arr[ts.team_b.length] = new float[]{pos.getX(), pos.getY(), pos.getZ(), yaw, pitch};
            ts.team_b = arr;
        }
    }

    /** 移除载具出生点 */
    public boolean removeVehicleSpawnPoint(String type, String team, int index) {
        TypeSpawns ts = vehicleSpawnPoints.types.get(type);
        if (ts == null) return false;
        float[][] arr = "a".equalsIgnoreCase(team) ? ts.team_a : ts.team_b;
        if (arr == null || index < 0 || index >= arr.length) return false;
        float[][] newArr = new float[arr.length - 1][];
        for (int i = 0, j = 0; i < arr.length; i++) {
            if (i != index) { newArr[j] = arr[i]; j++; }
        }
        if ("a".equalsIgnoreCase(team)) {
            ts.team_a = newArr;
        } else {
            ts.team_b = newArr;
        }
        // 两队都空了就删除整个类型
        removeTypeIfEmpty(type);
        return true;
    }

    /** 清除某类型某队的载具出生点 */
    public void clearVehicleSpawnPoints(String type, String team) {
        TypeSpawns ts = vehicleSpawnPoints.types.get(type);
        if (ts == null) return;
        if ("a".equalsIgnoreCase(team)) ts.team_a = new float[0][];
        else if ("b".equalsIgnoreCase(team)) ts.team_b = new float[0][];
        // 两队都空了就删除整个类型
        removeTypeIfEmpty(type);
    }

    private void removeTypeIfEmpty(String type) {
        TypeSpawns ts = vehicleSpawnPoints.types.get(type);
        if (ts != null && ts.team_a.length == 0 && ts.team_b.length == 0) {
            vehicleSpawnPoints.types.remove(type);
        }
    }

    /**
     * 按载具类型获取顺序出生点（轮询）。
     * 匹配顺序: 1. 精确类型匹配  2. 地面载具回退到 "land"  3. null=使用玩家出生点
     */
    public BlockPos getRandomVehicleSpawn(String team, String vehicleType) {
        float[][] arr = resolveVehicleSpawns(team, vehicleType);
        if (arr == null || arr.length == 0) return null;
        String key = lastVehicleResolvedTeam + ":" + lastVehicleResolvedType;
        int idx = vehicleSpawnSeqIdx.getOrDefault(key, -1);
        idx = (idx + 1) % arr.length;
        vehicleSpawnSeqIdx.put(key, idx);
        lastVehicleSpawnIdx = idx;
        return toBlockPos(arr[idx]);
    }

    /** 获取最近一次 getRandomVehicleSpawn 所选出生点的 yaw */
    public float getLastVehicleSpawnYaw() {
        return getVehicleSpawnYaw(lastVehicleResolvedTeam, lastVehicleResolvedType, lastVehicleSpawnIdx);
    }

    /** 获取最近一次 getRandomVehicleSpawn 所选出生点的 pitch */
    public float getLastVehicleSpawnPitch() {
        return getVehicleSpawnPitch(lastVehicleResolvedTeam, lastVehicleResolvedType, lastVehicleSpawnIdx);
    }

    private transient int lastVehicleSpawnIdx = -1;
    private transient String lastVehicleResolvedTeam;
    private transient String lastVehicleResolvedType;
    /** 轮询索引: "team:resolvedType" -> 上次使用的索引 */
    private transient java.util.Map<String, Integer> vehicleSpawnSeqIdx = new java.util.HashMap<>();

    /** 根据载具类型和索引获取出生点 yaw（旧格式无yaw返回0） */
    public float getVehicleSpawnYaw(String team, String vehicleType, int index) {
        float[][] arr = resolveVehicleSpawns(team, vehicleType);
        if (arr == null || index < 0 || index >= arr.length) return 0f;
        float[] s = arr[index];
        return s.length >= 5 ? s[3] : 0f;
    }

    /** 根据载具类型和索引获取出生点 pitch（旧格式无pitch返回0） */
    public float getVehicleSpawnPitch(String team, String vehicleType, int index) {
        float[][] arr = resolveVehicleSpawns(team, vehicleType);
        if (arr == null || index < 0 || index >= arr.length) return 0f;
        float[] s = arr[index];
        return s.length >= 5 ? s[4] : 0f;
    }

    /** 解析某队某载具类型的出生点数组，null=不存在 */
    private float[][] resolveVehicleSpawns(String team, String vehicleType) {
        lastVehicleResolvedTeam = team;
        lastVehicleResolvedType = vehicleType;
        if (vehicleType == null || vehicleType.isEmpty()) return null;
        // 1. 精确匹配
        TypeSpawns ts = vehicleSpawnPoints.types.get(vehicleType);
        float[][] arr = getTeamArray(ts, team);
        if (arr != null && arr.length > 0) return arr;
        // 2. 地面载具回退到 land
        if (GROUND_TYPES.contains(vehicleType)) {
            ts = vehicleSpawnPoints.types.get("land");
            arr = getTeamArray(ts, team);
            if (arr != null && arr.length > 0) return arr;
        }
        return null;
    }

    private static float[][] getTeamArray(TypeSpawns ts, String team) {
        if (ts == null) return null;
        return "a".equalsIgnoreCase(team) ? ts.team_a : ts.team_b;
    }

    private static BlockPos toBlockPos(float[] arr) {
        return new BlockPos((int)arr[0], (int)arr[1], (int)arr[2]);
    }

    /** 判断玩家 XZ 坐标是否在战场多边形边界内（射线法） */
    public boolean isInsideBattlefield(double x, double z) {
        if (battlefieldBoundary.isEmpty()) return true; // 未设置边界，默认在界内
        return isInsidePolygon(battlefieldBoundary, x, z);
    }

    /** 判断点是否在多边形内（射线法，仅XZ平面） */
    private static boolean isInsidePolygon(List<int[]> polygon, double x, double z) {
        int n = polygon.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] a = polygon.get(i);
            int[] b = polygon.get(j);
            double xi = a[0] + 0.5, zi = a[2] + 0.5;
            double xj = b[0] + 0.5, zj = b[2] + 0.5;
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 禁区定义 */
    public static class ForbiddenZone {
        /** 禁区名称 */
        public String name;
        /** 多边形顶点 */
        @SerializedName("boundary")
        public List<int[]> boundary = new ArrayList<>();
        /** 禁止进入的队伍: "A" / "B" / "BOTH" */
        @SerializedName("forbidden_team")
        public String forbiddenTeam = "BOTH";

        /** 判断玩家 XZ 坐标是否在此禁区内 */
        public boolean isInside(double x, double z) {
            if (boundary.isEmpty()) return false;
            return isInsidePolygon(boundary, x, z);
        }
    }
}

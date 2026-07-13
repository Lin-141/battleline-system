package com.battlelinesystem.faction;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.world.MapConfig;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Faction pool manager - server singleton
 */
public class FactionManager {

    private static final Path CONFIG_PATH = Path.of("config", "battlelinesystem", "factions.json");
    private static final Path THUMB_DIR = Path.of("config", "battlelinesystem", "faction_thumbnails");
    private static final FactionManager INSTANCE = new FactionManager();

    private final Map<String, FactionConfig> factions = new LinkedHashMap<>();

    /** 当前活跃的地图配置（选图后设置，用于提供地图专属阵营描述） */
    private MapConfig activeMapConfig;

    private FactionManager() {
        load();
        BattleLineSystem.LOGGER.info("FactionManager init: {} factions loaded", factions.size());
    }

    public static FactionManager getInstance() {
        return INSTANCE;
    }

    public boolean addFaction(FactionConfig faction) {
        BattleLineSystem.LOGGER.info("FactionManager.addFaction: id={}, name={}", faction.id, faction.name);
        if (faction.id == null || !faction.id.matches("[a-zA-Z0-9_-]+") || faction.id.length() > 16) {
            BattleLineSystem.LOGGER.warn("FactionManager: invalid id '{}'", faction.id);
            return false;
        }
        if (factions.containsKey(faction.id)) {
            BattleLineSystem.LOGGER.warn("FactionManager: duplicate id {}", faction.id);
            return false;
        }
        factions.put(faction.id, faction);
        refreshThumbnailFlag(faction);
        save();
        BattleLineSystem.LOGGER.info("FactionManager: added {}, now {} factions", faction.id, factions.size());
        return true;
    }

    public boolean removeFaction(String id) {
        if (factions.remove(id) != null) {
            // 同时删除缩略图
            try { Files.deleteIfExists(thumbnailPath(id)); } catch (IOException ignored) {}
            save();
            BattleLineSystem.LOGGER.info("FactionManager: removed {}", id);
            return true;
        }
        return false;
    }

    public boolean updateFaction(String id, String name, String displayColor, String description) {
        FactionConfig fc = factions.get(id);
        if (fc == null) return false;
        fc.name = name;
        fc.displayColor = displayColor;
        fc.description = description;
        refreshThumbnailFlag(fc);
        save();
        BattleLineSystem.LOGGER.info("FactionManager: updated {}", id);
        return true;
    }

    /** 更新阵营（含职业列表） */
    public boolean updateFactionFull(FactionConfig updated) {
        FactionConfig fc = factions.get(updated.id);
        if (fc == null) return false;
        fc.name = updated.name;
        fc.displayColor = updated.displayColor;
        fc.description = updated.description;
        fc.classes = updated.classes;
        fc.vehicles = updated.vehicles;
        fc.commanderExtraItems = updated.commanderExtraItems;
        fc.looseSpawn = updated.looseSpawn;
        fc.captureSound = updated.captureSound;
        fc.loseSound = updated.loseSound;
        refreshThumbnailFlag(fc);
        save();
        BattleLineSystem.LOGGER.info("FactionManager: updated full {}", updated.id);
        return true;
    }

    public List<FactionConfig> getAllFactions() {
        List<FactionConfig> list = new ArrayList<>();
        for (FactionConfig fc : factions.values()) {
            list.add(new FactionConfig(fc));
        }
        return list;
    }

    /**
     * 获取当前地图激活时可选阵营（按阵营池过滤）
     */
    public List<FactionConfig> getActiveMapFactions() {
        List<FactionConfig> list = new ArrayList<>();
        for (FactionConfig fc : factions.values()) {
            list.add(new FactionConfig(fc));
        }
        if (activeMapConfig != null) {
            java.util.Set<String> poolIds = new java.util.LinkedHashSet<>();
            if (activeMapConfig.factionPoolA != null) poolIds.addAll(activeMapConfig.factionPoolA);
            if (activeMapConfig.factionPoolB != null) poolIds.addAll(activeMapConfig.factionPoolB);
            if (!poolIds.isEmpty()) {
                list.removeIf(fc -> !poolIds.contains(fc.id));
            }
        }
        return list;
    }

    public FactionConfig getFaction(String id) {
        return factions.get(id);
    }

    /** 设置当前活跃的地图配置 */
    public void setActiveMapConfig(MapConfig config) {
        this.activeMapConfig = config;
    }

    /** 获取当前活跃的地图配置 */
    public MapConfig getActiveMapConfig() {
        return this.activeMapConfig;
    }

    /** 获取阵营缩略图文件路径 */
    public static Path thumbnailPath(String factionId) {
        return THUMB_DIR.resolve(factionId + ".png");
    }

    private void refreshThumbnailFlag(FactionConfig f) {
        f.hasThumbnail = Files.isRegularFile(thumbnailPath(f.id));
    }

    private void refreshThumbnailFlag(String id) {
        FactionConfig f = factions.get(id);
        if (f != null) refreshThumbnailFlag(f);
    }

    private void load() {
        BattleLineSystem.LOGGER.info("FactionManager.load: path={}", CONFIG_PATH.toAbsolutePath());
        if (!Files.exists(CONFIG_PATH)) {
            factions.put("nato", new FactionConfig("nato", "NATO", "#4488FF", "NATO Coalition"));
            factions.get("nato").classes.add(
                    new ClassConfig("assault", "突击兵", "minecraft:iron_helmet",
                            "minecraft:iron_chestplate", "minecraft:iron_leggings",
                            "minecraft:iron_boots", "minecraft:shield"));
            factions.get("nato").classes.add(
                    new ClassConfig("sniper", "狙击手", null, "minecraft:leather_chestplate",
                            "minecraft:leather_leggings", "minecraft:leather_boots",
                            null));
            factions.put("eastern", new FactionConfig("eastern", "Eastern", "#FF4444", "Eastern Forces"));
            factions.get("eastern").classes.add(
                    new ClassConfig("assault", "突击兵", "minecraft:chainmail_helmet",
                            "minecraft:chainmail_chestplate", "minecraft:chainmail_leggings",
                            "minecraft:chainmail_boots", "minecraft:shield"));
            // 复制默认缩略图
            copyDefaultThumbnail("nato", "../美术资产/team1.png");
            copyDefaultThumbnail("eastern", "../美术资产/team2.png");
            for (FactionConfig f : factions.values()) refreshThumbnailFlag(f);
            save();
            BattleLineSystem.LOGGER.info("FactionManager: created default factions");
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            Map<String, FactionConfig> loaded = FactionConfig.GSON.fromJson(json,
                    new TypeToken<LinkedHashMap<String, FactionConfig>>() {}.getType());
            if (loaded != null) {
                factions.clear();
                factions.putAll(loaded);
            }
            for (FactionConfig f : factions.values()) refreshThumbnailFlag(f);
            // 补充缺失的默认缩略图
            ensureDefaultThumbnails();
            BattleLineSystem.LOGGER.info("FactionManager: loaded {} factions from file", factions.size());
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("FactionManager: failed to load config", e);
        }
    }

    private void copyDefaultThumbnail(String factionId, String sourceRelative) {
        try {
            Path source = Path.of(sourceRelative);
            if (Files.isRegularFile(source)) {
                Files.createDirectories(THUMB_DIR);
                Files.copy(source, thumbnailPath(factionId), StandardCopyOption.REPLACE_EXISTING);
                refreshThumbnailFlag(factionId);
                BattleLineSystem.LOGGER.info("FactionManager: copied default thumbnail for {}", factionId);
            } else {
                BattleLineSystem.LOGGER.warn("FactionManager: default thumbnail not found: {}", source.toAbsolutePath());
            }
        } catch (IOException e) {
            BattleLineSystem.LOGGER.warn("FactionManager: failed to copy default thumbnail for {}", factionId, e);
        }
    }

    /** 确保默认阵营的缩略图存在（幂等，不会覆盖已有文件） */
    private void ensureDefaultThumbnails() {
        if (!Files.isRegularFile(thumbnailPath("nato"))) {
            copyDefaultThumbnail("nato", "../美术资产/team1.png");
        }
        if (!Files.isRegularFile(thumbnailPath("eastern"))) {
            copyDefaultThumbnail("eastern", "../美术资产/team2.png");
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, FactionConfig.GSON.toJson(factions));
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("FactionManager: failed to save config", e);
        }
    }
}

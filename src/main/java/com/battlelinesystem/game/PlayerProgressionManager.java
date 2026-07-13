package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 玩家养成管理器 — 管理变体购买记录，JSON 持久化。
 * 解锁条件格式: "purchase:variantId" → 检查此玩家的购买记录。
 */
public class PlayerProgressionManager {

    private static final PlayerProgressionManager INSTANCE = new PlayerProgressionManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_DIR = Path.of("config", "battlelinesystem", "unlocks");

    private final Map<UUID, PurchasedData> dataMap = new HashMap<>();

    private PlayerProgressionManager() {}

    public static PlayerProgressionManager getInstance() { return INSTANCE; }

    // ---- 查询 ----

    public boolean isPurchased(UUID uuid, String variantId) {
        PurchasedData d = dataMap.get(uuid);
        if (d == null) {
            d = load(uuid);
            if (d == null) return false;
            dataMap.put(uuid, d);
        }
        return d.purchasedVariants.contains(variantId);
    }

    public Set<String> getPurchases(UUID uuid) {
        PurchasedData d = dataMap.get(uuid);
        if (d == null) {
            d = load(uuid);
            if (d == null) return Collections.emptySet();
            dataMap.put(uuid, d);
        }
        return Collections.unmodifiableSet(d.purchasedVariants);
    }

    // ---- 修改 ----

    public void addPurchase(UUID uuid, String variantId) {
        PurchasedData d = dataMap.computeIfAbsent(uuid, k -> {
            PurchasedData loaded = load(k);
            return loaded != null ? loaded : new PurchasedData(k);
        });
        d.purchasedVariants.add(variantId);
        save(d);
        BattleLineSystem.LOGGER.info("[Progression] {} 购买变体: {}", uuid.toString().substring(0, 8), variantId);
    }

    public void removePurchase(UUID uuid, String variantId) {
        PurchasedData d = dataMap.get(uuid);
        if (d == null) {
            d = load(uuid);
            if (d == null) return;
            dataMap.put(uuid, d);
        }
        d.purchasedVariants.remove(variantId);
        save(d);
        BattleLineSystem.LOGGER.info("[Progression] {} 撤销变体: {}", uuid.toString().substring(0, 8), variantId);
    }

    // ---- 持久化 ----

    private PurchasedData load(UUID uuid) {
        Path file = DATA_DIR.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try {
            return GSON.fromJson(Files.readString(file), PurchasedData.class);
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("[Progression] 加载购买记录失败 {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    private void save(PurchasedData d) {
        try {
            Files.createDirectories(DATA_DIR);
            Files.writeString(DATA_DIR.resolve(d.uuid + ".json"), GSON.toJson(d));
        } catch (IOException e) {
            BattleLineSystem.LOGGER.error("[Progression] 保存购买记录失败 {}: {}", d.uuid, e.getMessage());
        }
    }

    // ---- 数据结构 ----

    private static class PurchasedData {
        public String uuid;
        @SerializedName("purchased_variants")
        public Set<String> purchasedVariants = new LinkedHashSet<>();

        PurchasedData() {}

        PurchasedData(UUID uuid) {
            this.uuid = uuid.toString();
        }
    }
}

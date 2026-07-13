package com.battlelinesystem.game;

import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.faction.VehicleConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 载具重生管理器 — 每个阵营的每个载具池条目独立追踪存活和冷却。
 *
 * 数据结构: factionId -> slotIndex -> SlotState
 * 每个 SlotState 拥有自己的 aliveUUIDs 和 cooldownEndMs，同类型载具互不干扰。
 */
public class VehicleRespawnManager {

    private static final VehicleRespawnManager INSTANCE = new VehicleRespawnManager();

    public static VehicleRespawnManager getInstance() { return INSTANCE; }

    // === 内部状态 ===

    /** 每个 slot 的独立状态 */
    public static class SlotState {
        /** 同时存活的最大数量限制（复制自 VehicleConfig.maxCount，0=无限） */
        public int maxCount;
        /** 冷却秒数（复制自 VehicleConfig.cooldownSeconds，0=无冷却） */
        public int cooldownSeconds;
        /** 当前存活的实体 UUID → 所在维度 */
        public final Map<UUID, ResourceLocation> aliveUUIDs = new ConcurrentHashMap<>();
        /** 冷却结束时间戳(ms)，0 表示不在冷却中 */
        public volatile long cooldownEndMs = 0;

        public boolean isOnCooldown(long now) {
            return cooldownEndMs > 0 && cooldownEndMs > now;
        }

        public boolean hasAlive() {
            return !aliveUUIDs.isEmpty();
        }

        public int aliveCount() {
            return aliveUUIDs.size();
        }

        public boolean canDeploy(long now) {
            // 1. 不在冷却中
            if (cooldownEndMs > 0 && cooldownEndMs > now) return false;
            // 2. 存活数未达上限
            if (maxCount > 0 && aliveUUIDs.size() >= maxCount) return false;
            return true;
        }

        /** 剩余冷却秒数（客户端显示用），0=无冷却 */
        public int cooldownRemainingSec(long now) {
            if (cooldownEndMs <= 0 || cooldownEndMs <= now) return 0;
            long ms = cooldownEndMs - now;
            return (int) ((ms + 999) / 1000); // 向上取整
        }
    }

    /** factionId -> slotIndex -> SlotState */
    private final Map<String, Map<Integer, SlotState>> factionSlots = new ConcurrentHashMap<>();

    // === 插槽生命周期 ===

    /** 确保 factionId 的插槽已初始化（从 FactionConfig 创建），返回该阵营的所有插槽 */
    private Map<Integer, SlotState> ensureSlots(String factionId) {
        return factionSlots.computeIfAbsent(factionId, fid -> {
            Map<Integer, SlotState> map = new ConcurrentHashMap<>();
            FactionConfig fc = FactionManager.getInstance().getFaction(fid);
            if (fc != null && fc.vehicles != null) {
                for (int i = 0; i < fc.vehicles.size(); i++) {
                    VehicleConfig vc = fc.vehicles.get(i);
                    SlotState st = new SlotState();
                    st.maxCount = (vc.maxCount > 0) ? vc.maxCount : Integer.MAX_VALUE;
                    st.cooldownSeconds = vc.cooldownSeconds;
                    map.put(i, st);
                }
            }
            return map;
        });
    }

    /** 同步 FactionConfig 更新（重新加载时调用） */
    public void syncFromConfig(String factionId) {
        Map<Integer, SlotState> map = factionSlots.get(factionId);
        FactionConfig fc = FactionManager.getInstance().getFaction(factionId);
        if (fc == null || fc.vehicles == null) {
            if (map != null) map.clear();
            return;
        }
        if (map == null) {
            ensureSlots(factionId);
            return;
        }
        // 保留已有插槽状态，更新/新增/删除
        for (int i = 0; i < fc.vehicles.size(); i++) {
            VehicleConfig vc = fc.vehicles.get(i);
            SlotState st = map.computeIfAbsent(i, k -> new SlotState());
            st.maxCount = (vc.maxCount > 0) ? vc.maxCount : Integer.MAX_VALUE;
            st.cooldownSeconds = vc.cooldownSeconds;
        }
    }

    // === 部署操作 ===

    /**
     * 检查指定插槽是否可部署。
     * @return null 表示可部署，否则返回错误消息
     */
    public String checkDeploy(String factionId, int slotIndex, long now) {
        Map<Integer, SlotState> slots = ensureSlots(factionId);
        SlotState st = slots.get(slotIndex);
        if (st == null) return "§c载具配置不存在";
        if (st.isOnCooldown(now)) {
            int remaining = st.cooldownRemainingSec(now);
            return "§c载具冷却中，还需 " + remaining + " 秒";
        }
        if (!st.canDeploy(now)) {
            return "§c该载具已达数量上限！(" + st.aliveCount() + "/" + (st.maxCount == Integer.MAX_VALUE ? "∞" : st.maxCount) + ")";
        }
        return null;
    }

    /** 部署成功后注册实体 UUID 及其所在维度 */
    public void onDeploy(String factionId, int slotIndex, UUID entityUuid, ResourceLocation dimension) {
        Map<Integer, SlotState> slots = ensureSlots(factionId);
        SlotState st = slots.get(slotIndex);
        if (st == null) return;
        st.aliveUUIDs.put(entityUuid, dimension);
        st.cooldownEndMs = 0;
        com.battlelinesystem.BattleLineSystem.LOGGER.debug(
            "[VRM] onDeploy faction={} slot={} uuid={} dim={} aliveNow={} cdEnd=0",
            factionId, slotIndex, entityUuid, dimension, st.aliveUUIDs.size());
    }

    // === 死亡/摧毁处理 ===

    /**
     * 通过实体 UUID 查找并移除，触发冷却。
     * @return 触发了冷却的 {factionId, slotIndex}，未找到则 null
     */
    public String[] onEntityRemoved(UUID entityUuid) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<Integer, SlotState>> fe : factionSlots.entrySet()) {
            for (Map.Entry<Integer, SlotState> se : fe.getValue().entrySet()) {
                if (se.getValue().aliveUUIDs.remove(entityUuid) != null) {
                    startCooldown(fe.getKey(), se.getKey(), se.getValue(), now);
                    return new String[]{ fe.getKey(), String.valueOf(se.getKey()) };
                }
            }
        }
        return null;
    }

    private void startCooldown(SlotState st, long now) {
        if (st.cooldownSeconds <= 0) return;
        st.cooldownEndMs = now + st.cooldownSeconds * 1000L;
    }

    private void startCooldown(String factionId, int slotIndex, SlotState st, long now) {
        if (st.cooldownSeconds <= 0) return;
        st.cooldownEndMs = now + st.cooldownSeconds * 1000L;
        com.battlelinesystem.BattleLineSystem.LOGGER.debug(
            "[VRM] startCooldown faction={} slot={} cdSec={} endMs={} aliveRemain={}",
            factionId, slotIndex, st.cooldownSeconds, st.cooldownEndMs, st.aliveUUIDs.size());
    }

    // === 服务端 Tick：验证存活状态 ===

    /**
     * 遍历所有插槽，验证实体存活（跨所有 level 查找），清理已死实体并触发冷却。
     * @return 发生变化的阵营集合（需要向客户端推送更新）
     */
    private int tickDebugCounter = 0;

    public Set<String> tick(MinecraftServer server) {
        Set<String> dirtied = new HashSet<>();
        long now = System.currentTimeMillis();
        tickDebugCounter++;
        boolean debug = (tickDebugCounter % 5 == 0);
        FactionManager fm = FactionManager.getInstance();
        for (FactionConfig fc : fm.getAllFactions()) {
            if (fc.id != null && !fc.id.isEmpty()) ensureSlots(fc.id);
        }
        for (Map.Entry<String, Map<Integer, SlotState>> fe : factionSlots.entrySet()) {
            for (Map.Entry<Integer, SlotState> se : fe.getValue().entrySet()) {
                SlotState st = se.getValue();
                if (st.aliveUUIDs.isEmpty()) continue;
                Iterator<Map.Entry<UUID, ResourceLocation>> it = st.aliveUUIDs.entrySet().iterator();
                boolean removed = false;
                while (it.hasNext()) {
                    Map.Entry<UUID, ResourceLocation> entry = it.next();
                    UUID uuid = entry.getKey();
                    ResourceLocation dim = entry.getValue();
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dim);
                    ServerLevel lv = server.getLevel(dimKey);
                    Entity entity = (lv != null) ? lv.getEntity(uuid) : null;
                    if (debug && entity != null) {
                        com.battlelinesystem.BattleLineSystem.LOGGER.info(
                            "[VRM] tick-check faction={} slot={} uuid={} dim={} lv={} alive={} type={}",
                            fe.getKey(), se.getKey(), uuid, dim, lv != null, entity.isAlive(), entity.getType().toShortString());
                    }
                    if (entity == null || !entity.isAlive()) {
                        it.remove();
                        removed = true;
                        if (debug) {
                            com.battlelinesystem.BattleLineSystem.LOGGER.info(
                                "[VRM] tick-remove faction={} slot={} uuid={} entity={} alive={}",
                                fe.getKey(), se.getKey(), uuid, entity, entity != null ? entity.isAlive() : "null");
                        }
                    }
                }
                if (removed) {
                    startCooldown(fe.getKey(), se.getKey(), st, now);
                    dirtied.add(fe.getKey());
                }
            }
        }
        return dirtied;
    }

    // === 状态查询（给客户端数据包用） ===

    /** 获取某阵营某插槽的状态，不存在则初始化，永不返回 null */
    public SlotState getSlot(String factionId, int slotIndex) {
        Map<Integer, SlotState> slots = ensureSlots(factionId);
        return slots.computeIfAbsent(slotIndex, k -> new SlotState());
    }

    /** 获取某阵营所有插槽状态 */
    public Map<Integer, SlotState> getSlots(String factionId) {
        Map<Integer, SlotState> slots = factionSlots.get(factionId);
        if (slots == null) return Collections.emptyMap();
        return new HashMap<>(slots);
    }

    // === 重置 ===

    /** 清理所有数据（游戏结束/重置时调用） */
    public void clear() {
        factionSlots.clear();
    }

    /** 清理某阵营的数据 */
    public void clearFaction(String factionId) {
        Map<Integer, SlotState> slots = factionSlots.get(factionId);
        if (slots != null) slots.clear();
    }
}

package com.battlelinesystem.network;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.faction.VehicleConfig;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.network.packet.PacketClassCountUpdate;
import com.battlelinesystem.network.packet.PacketOpenClassVote;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络工具类 — 职业计数、载具追踪等公共服务
 * 数据包注册已迁移至 {@link AllPackets}
 */
public class NetworkManager {

    // === 职业选取计数：factionId -> classId -> 玩家UUID集合 ===
    private static final Map<String, Map<String, Set<UUID>>> classSelections = new ConcurrentHashMap<>();

    /** 清空所有职业选取记录（新一局开始时调用） */
    public static void resetClassSelections() {
        classSelections.clear();
        com.battlelinesystem.game.VehicleRespawnManager.getInstance().clear();
    }

    /** 获取某职业当前选取人数 */
    public static int getClassCount(String factionId, String classId) {
        Map<String, Set<UUID>> fc = classSelections.get(factionId);
        if (fc == null) return 0;
        Set<UUID> s = fc.get(classId);
        return s != null ? s.size() : 0;
    }

    /** 从所有职业选取记录中移除某玩家 */
    public static void removePlayerFromClassSelections(UUID uuid) {
        for (Map<String, Set<UUID>> fc : classSelections.values()) {
            for (Set<UUID> s : fc.values()) {
                s.remove(uuid);
            }
        }
    }

    /** 包内访问：供 PacketClassSelect 使用 */
    public static Map<String, Map<String, Set<UUID>>> classSelectionsMap() {
        return classSelections;
    }

    // === 载具重生 ===

    /**
     * 职业/载具计数变更后，向同阵营所有在线玩家推送轻量更新（不重开界面）。
     */
    public static void broadcastClassCounts(String factionId) {
        FactionConfig fc = FactionManager.getInstance().getFaction(factionId);
        if (fc == null || fc.classes == null || fc.classes.isEmpty()) return;
        MinecraftServer srv = BattleLineSystem.getServer();
        if (srv == null) return;
        CapturePointManager cpm = CapturePointManager.getInstance();

        // 构建职业计数
        int[] classCounts = new int[fc.classes.size()];
        for (int i = 0; i < fc.classes.size(); i++) {
            ClassConfig cc = fc.classes.get(i);
            if (cc.maxPlayers > 0) {
                classCounts[i] = getClassCount(factionId, cc.id);
            }
        }

        // 构建载具状态
        com.battlelinesystem.game.VehicleRespawnManager vrm = com.battlelinesystem.game.VehicleRespawnManager.getInstance();
        List<VehicleConfig> vlist = fc.vehicles != null ? fc.vehicles : new java.util.ArrayList<>();
        int[] vCounts = new int[vlist.size()];
        boolean[] vAlive = new boolean[vlist.size()];
        int[] vCooldowns = new int[vlist.size()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < vlist.size(); i++) {
            com.battlelinesystem.game.VehicleRespawnManager.SlotState st = vrm.getSlot(factionId, i);
            if (st != null) {
                vCounts[i] = st.aliveCount();
                vAlive[i] = st.hasAlive();
                vCooldowns[i] = st.cooldownRemainingSec(now);
            }
        }

        PacketClassCountUpdate pkt = new PacketClassCountUpdate();
        pkt.factionId = factionId;
        pkt.classCounts = classCounts;
        pkt.vehicleCounts = vCounts;
        pkt.vehicleAlive = vAlive;
        pkt.vehicleCooldownsData = vCooldowns;

        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            String pfid = cpm.getPlayerFaction(p.getUUID());
            if (factionId.equals(pfid)) {
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p), pkt);
            }
        }
    }

    /** @deprecated 使用 VehicleRespawnManager */
    public static void removeVehicleByEntity(UUID entityUuid) {
        String[] result = com.battlelinesystem.game.VehicleRespawnManager.getInstance().onEntityRemoved(entityUuid);
        if (result != null) {
            broadcastVehicleState(result[0]);
        }
    }

    /** 载具状态变更后向该阵营所有在线玩家推送最新 PacketOpenClassVote */
    public static void broadcastVehicleState(String factionId) {
        FactionConfig fc = FactionManager.getInstance().getFaction(factionId);
        if (fc == null || fc.classes == null || fc.classes.isEmpty()) return;
        MinecraftServer srv = BattleLineSystem.getServer();
        if (srv == null) return;
        CapturePointManager cpm = CapturePointManager.getInstance();

        com.battlelinesystem.game.VehicleRespawnManager vrm = com.battlelinesystem.game.VehicleRespawnManager.getInstance();
        long now = System.currentTimeMillis();
        Map<Integer, com.battlelinesystem.game.VehicleRespawnManager.SlotState> slots = vrm.getSlots(factionId);
        if (!slots.isEmpty()) {
            Set<net.minecraft.server.level.ServerLevel> levels = new LinkedHashSet<>();
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                if (factionId.equals(cpm.getPlayerFaction(p.getUUID()))) {
                    levels.add(p.serverLevel());
                }
            }
            for (Map.Entry<Integer, com.battlelinesystem.game.VehicleRespawnManager.SlotState> se : slots.entrySet()) {
                com.battlelinesystem.game.VehicleRespawnManager.SlotState st = se.getValue();
                if (st.aliveUUIDs.isEmpty()) continue;
                boolean anyDead = false;
                Iterator<Map.Entry<UUID, net.minecraft.resources.ResourceLocation>> it = st.aliveUUIDs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, net.minecraft.resources.ResourceLocation> e = it.next();
                    boolean found = false;
                    for (net.minecraft.server.level.ServerLevel lv : levels) {
                        if (lv.dimension().location().equals(e.getValue())) {
                            net.minecraft.world.entity.Entity ent = lv.getEntity(e.getKey());
                            if (ent != null && ent.isAlive()) { found = true; break; }
                        }
                    }
                    if (!found) {
                        it.remove();
                        anyDead = true;
                        BattleLineSystem.LOGGER.debug("[VRM] broadcast-detect dead faction={} slot={} uuid={}", factionId, se.getKey(), e.getKey());
                    }
                }
                if (anyDead && st.cooldownSeconds > 0 && st.aliveUUIDs.isEmpty()) {
                    st.cooldownEndMs = now + st.cooldownSeconds * 1000L;
                    BattleLineSystem.LOGGER.debug("[VRM] broadcast-startCooldown faction={} slot={} cdSec={} endMs={}", factionId, se.getKey(), st.cooldownSeconds, st.cooldownEndMs);
                }
            }
        }

        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            String pt = cpm.getPlayerTeam(p.getUUID());
            if (pt == null) continue;
            String pfid = cpm.getPlayerFaction(p.getUUID());
            if (!factionId.equals(pfid)) continue;
            // 不向已部署（非观察者）的玩家发送职业选择包，防止重开部署界面
            if (!p.isSpectator()) continue;
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p), buildClassVotePacket(factionId, fc, pt, srv));
        }
    }

    private static PacketOpenClassVote buildClassVotePacket(String factionId, FactionConfig fc, String team, MinecraftServer srv) {
        PacketOpenClassVote pkt = new PacketOpenClassVote(
                factionId, fc.name, fc.displayColor,
                (byte) ("A".equals(team) ? 0 : 1),
                new ArrayList<>(fc.classes), fc.vehicles);
        pkt.totalPlayers = CapturePointManager.countTeamPlayers(srv, team);
        pkt.looseSpawn = fc.looseSpawn;
        for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
            String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                pkt.sameTeamUUIDs.add(sp.getUUID());
            }
        }
        // 设置双方阵营名称
        com.battlelinesystem.game.CapturePointManager cpm = CapturePointManager.getInstance();
        com.battlelinesystem.faction.FactionManager fm = com.battlelinesystem.faction.FactionManager.getInstance();
        String taFid = cpm.getTeamFaction("A");
        String tbFid = cpm.getTeamFaction("B");
        com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(taFid);
        com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(tbFid);
        pkt.teamAName = ta != null ? ta.name : "A队";
        pkt.teamBName = tb != null ? tb.name : "B队";
        return pkt;
    }

    /**
     * 通过反射调用客户端专用处理方法（避免编译期字节码引用客户端类）
     */
    public static void dispatchClient(int packetId, Object msg) {
        try {
            Class.forName("com.battlelinesystem.network.ClientPacketHandler")
                    .getMethod("dispatch", int.class, Object.class)
                    .invoke(null, packetId, msg);
        } catch (Exception ignored) {
            // 服务端没有 ClientPacketHandler，静默忽略
        }
    }
}

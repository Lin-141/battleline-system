package com.battlelinesystem.event;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.game.GameModeManager;
import com.battlelinesystem.items.SelectionWandItem;
import com.battlelinesystem.items.PolygonWandItem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.packet.PacketOpenClassVote;
import com.battlelinesystem.network.packet.PacketSaveGunMod;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏事件处理器
 */
public class GameEventHandler {

    /** 需要持续满油门的载具 UUID 集合 */
    private static final Set<UUID> vehicleBoosts = ConcurrentHashMap.newKeySet();

    /** 注册载具满油门（仅 plane 类型载具出生点） */
    public static void registerVehicleBoost(Entity vehicle) {
        vehicleBoosts.add(vehicle.getUUID());
        BattleLineSystem.LOGGER.info("[Booster] registered uuid={} class={}", vehicle.getUUID(), vehicle.getClass().getName());
    }

    /** 清除所有满油门注册（/bls stopgame 用） */
    public static void clearVehicleBoosts() {
        vehicleBoosts.clear();
    }

    /**
     * 玩家断开连接时，清除其模式选择和队伍
     */
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            GameModeManager.getInstance().removePlayer(event.getEntity().getUUID());
            CapturePointManager.getInstance().removePlayer(event.getEntity().getUUID());
            NetworkManager.removePlayerFromClassSelections(event.getEntity().getUUID());
            SelectionWandItem.clearSelection(event.getEntity());
            PacketSaveGunMod.GunModStorage.remove(event.getEntity().getUUID());
        }
    }

    /**
     * 左键点方块 → 选区棒设点A
     */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        ItemStack stack = event.getEntity().getItemInHand(event.getHand());
        if (stack.getItem() instanceof SelectionWandItem) {
            event.setCanceled(true);
            SelectionWandItem.setPos1(event.getEntity(), event.getPos());
        }
        if (stack.getItem() instanceof PolygonWandItem) {
            event.setCanceled(true);
            PolygonWandItem.serverAddPoint(event.getEntity(), event.getPos());
        }
    }

    /**
     * 玩家死亡 → 扣队伍分数 + 复活到战区上方观察者视角并重开职业选择
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity entity = event.getEntity();

        // 非玩家实体死亡：清理载具追踪
        if (!(entity instanceof ServerPlayer)) {
            NetworkManager.removeVehicleByEntity(entity.getUUID());
            return;
        }

        ServerPlayer player = (ServerPlayer) entity;

        // 只在 BLS 游戏世界处理死亡
        if (!player.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)) return;

        // 记录 K/D 数据
        CapturePointManager cpm = CapturePointManager.getInstance();
        cpm.recordDeath(player.serverLevel().dimension(), player.getUUID());
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && !killer.getUUID().equals(player.getUUID())) {
            cpm.recordKill(killer.serverLevel().dimension(), killer.getUUID());
        }

        // 记录死亡时间（用于部署5秒冷却）
        cpm.recordDeathTimestamp(player.getUUID());

        // 扣分
        cpm.onPlayerDeath(player);

        // 取消原版死亡（不掉落、不重生到出生点）
        event.setCanceled(true);

        // 恢复血量
        player.setHealth(player.getMaxHealth());

        // 清空背包
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();

        // 设为观察者模式
        player.setGameMode(GameType.SPECTATOR);

        // 传送到战区上方 Y=319，头朝下
        MapConfig mapConfig = FactionManager.getInstance().getActiveMapConfig();
        double tx, tz;
        if (mapConfig != null) {
            tx = mapConfig.getTeamASpawn().getX() + 0.5;
            tz = mapConfig.getTeamASpawn().getZ() + 0.5;
        } else {
            tx = player.getX();
            tz = player.getZ();
        }
        player.teleportTo(player.serverLevel(), tx, 319, tz, 180, 90);

        // 重新打开职业选择界面（死亡不释放职业槽，保持持久化）
        String factionId = CapturePointManager.getInstance().getPlayerFaction(player.getUUID());
        if (factionId != null) {
            FactionConfig fc = FactionManager.getInstance().getFaction(factionId);
            if (fc != null && fc.classes != null && !fc.classes.isEmpty()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(player.getUUID());
                byte team = (byte)("A".equals(pt) ? 0 : 1);
                PacketOpenClassVote pkt = new PacketOpenClassVote(
                        factionId, fc.name, fc.displayColor, team, new java.util.ArrayList<>(fc.classes), fc.vehicles);
                net.minecraft.server.MinecraftServer srv = player.getServer();
                if (srv != null) {
                    pkt.totalPlayers = CapturePointManager.countTeamPlayers(srv, pt);
                    pkt.looseSpawn = fc.looseSpawn;
                    // 设置双方实际阵营名称
                    com.battlelinesystem.faction.FactionManager fm = com.battlelinesystem.faction.FactionManager.getInstance();
                    com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(cpm.getTeamFaction("A"));
                    com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(cpm.getTeamFaction("B"));
                    pkt.teamAName = ta != null ? ta.name : "A队";
                    pkt.teamBName = tb != null ? tb.name : "B队";
                    for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
                        if (pt.equals(CapturePointManager.getInstance().getPlayerTeam(sp.getUUID()))) {
                            pkt.sameTeamUUIDs.add(sp.getUUID());
                        }
                    }
                    // 部署冷却（死亡后5秒内不可部署）
                    pkt.deployCooldownMs = cpm.getDeployCooldownRemainingMs(player.getUUID());
                }
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player), pkt);
                // 播放等待部署音乐
                CapturePointManager.playSoundToPlayer(player, "battlelinesystem:waiting_01");
            }
        }
    }

    /** 公开的每 tick 载具满油门入口，由 BattleLineSystem.onServerTick 调用 */
    public static void tickVehicleBoosts(net.minecraft.server.MinecraftServer server) {
        if (vehicleBoosts.isEmpty()) return;
        java.util.List<UUID> done = new java.util.ArrayList<>();
        for (UUID uid : vehicleBoosts) {
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(uid);
                if (e != null && e.isAlive()) {
                    trySetVehiclePower(e, uid);
                    done.add(uid);
                    break;
                }
            }
        }
        if (!done.isEmpty()) {
            vehicleBoosts.removeAll(done);
        }
        // 清理已死亡实体
        vehicleBoosts.removeIf(uid -> {
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(uid) != null) return false;
            }
            BattleLineSystem.LOGGER.info("[Booster] removed dead uuid={}", uid);
            return true;
        });
    }

    private static int boosterDebugTick = 0;

    /** 反射设置 VehicleEntity.power = 1.25f + forwardInputDown = true（满油门） */
    private static void trySetVehiclePower(Entity e, UUID uid) {
        try {
            Class<?> clazz = e.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    var setPower = clazz.getDeclaredMethod("setPower", float.class);
                    setPower.invoke(e, 1.25f);
                    try {
                        var setFwd = clazz.getDeclaredMethod("setForwardInputDown", boolean.class);
                        setFwd.invoke(e, true);
                    } catch (NoSuchMethodException ignored) {}
                    // 回读确认
                    if (boosterDebugTick < 3) {
                        boosterDebugTick++;
                        var getPower = clazz.getDeclaredMethod("getPower");
                        float actual = (float) getPower.invoke(e);
                        var getFwd = clazz.getDeclaredMethod("forwardInputDown");
                        boolean fwd = (boolean) getFwd.invoke(e);
                        BattleLineSystem.LOGGER.info("[Booster] setPower uuid={} class={} power={} fwd={}", uid, e.getClass().getName(), actual, fwd);
                    }
                    return;
                } catch (NoSuchMethodException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
            BattleLineSystem.LOGGER.warn("[Booster] setPower not found on class hierarchy of {}", e.getClass().getName());
        } catch (Exception ex) {
            BattleLineSystem.LOGGER.warn("[Booster] setPower failed on {}: {}", e.getClass().getName(), ex.toString());
        }
    }
}

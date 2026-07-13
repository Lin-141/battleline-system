package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.ClassVariant;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PacketClassSelect extends PacketBase {
    public final String factionId;
    public final String classId;
    public final String variantId;
    public final String helmet, chestplate, leggings, boots, offHand;
    public final List<String> extraItems;
    public final int spawnIndex; // -1=随机, >=0=指定索引
    public final String captureSpawnName; // 据点名称（据点重生），null=常规出生点
    public final String vehicleNbt; // 载具容器NBT，null=不使用载具
    public final int vehicleSlotIndex; // 载具池插槽索引，-1=不使用
    public final UUID beaconUuid; // 信标实体UUID（信标重生），null=不使用
    public final UUID looseSpawnId; // 宽松重生点目标队友UUID，null=不使用

    public PacketClassSelect() {
        this.factionId = "";
        this.classId = "";
        this.variantId = "";
        this.helmet = null;
        this.chestplate = null;
        this.leggings = null;
        this.boots = null;
        this.offHand = null;
        this.extraItems = null;
        this.spawnIndex = -1;
        this.captureSpawnName = null;
        this.vehicleNbt = null;
        this.vehicleSlotIndex = -1;
        this.beaconUuid = null;
        this.looseSpawnId = null;
    }

    public PacketClassSelect(FriendlyByteBuf buf) {
        this.factionId = buf.readUtf();
        this.classId = buf.readUtf();
        String vid = buf.readUtf();
        this.variantId = vid.isEmpty() ? "" : vid;
        this.helmet = emptyToNull(buf.readUtf());
        this.chestplate = emptyToNull(buf.readUtf());
        this.leggings = emptyToNull(buf.readUtf());
        this.boots = emptyToNull(buf.readUtf());
        this.offHand = emptyToNull(buf.readUtf());
        int ec = buf.readVarInt();
        if (ec > 0) {
            this.extraItems = new ArrayList<>(ec);
            for (int i = 0; i < ec; i++) extraItems.add(buf.readUtf());
        } else {
            this.extraItems = null;
        }
        this.spawnIndex = buf.readVarInt();
        String csn = buf.readUtf();
        this.captureSpawnName = csn.isEmpty() ? null : csn;
        String vn = buf.readUtf();
        this.vehicleNbt = vn.isEmpty() ? null : vn;
        this.vehicleSlotIndex = buf.readVarInt();
        this.beaconUuid = buf.readBoolean() ? buf.readUUID() : null;
        this.looseSpawnId = buf.readBoolean() ? buf.readUUID() : null;
    }

    public PacketClassSelect(String factionId, String classId, String variantId,
                              String helmet, String chestplate, String leggings,
                              String boots, String offHand, List<String> extraItems,
                              int spawnIndex, String captureSpawnName,
                              String vehicleNbt, int vehicleSlotIndex,
                              UUID beaconUuid,
                              UUID looseSpawnId) {
        this.factionId = factionId;
        this.classId = classId;
        this.variantId = variantId;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.offHand = offHand;
        this.extraItems = extraItems;
        this.spawnIndex = spawnIndex;
        this.captureSpawnName = (captureSpawnName != null && !captureSpawnName.isEmpty()) ? captureSpawnName : null;
        this.vehicleNbt = (vehicleNbt != null && !vehicleNbt.isEmpty()) ? vehicleNbt : null;
        this.vehicleSlotIndex = vehicleSlotIndex;
        this.beaconUuid = beaconUuid;
        this.looseSpawnId = looseSpawnId;
    }

    private static String emptyToNull(String s) { return s.isEmpty() ? null : s; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionId);
        buf.writeUtf(classId);
        buf.writeUtf(variantId != null ? variantId : "");
        buf.writeUtf(helmet != null ? helmet : "");
        buf.writeUtf(chestplate != null ? chestplate : "");
        buf.writeUtf(leggings != null ? leggings : "");
        buf.writeUtf(boots != null ? boots : "");
        buf.writeUtf(offHand != null ? offHand : "");
        int ec = extraItems != null ? extraItems.size() : 0;
        buf.writeVarInt(ec);
        if (extraItems != null) {
            for (String s : extraItems) buf.writeUtf(s != null ? s : "");
        }
        buf.writeVarInt(spawnIndex);
        buf.writeUtf(captureSpawnName != null ? captureSpawnName : "");
        buf.writeUtf(vehicleNbt != null ? vehicleNbt : "");
        buf.writeVarInt(vehicleSlotIndex);
        buf.writeBoolean(beaconUuid != null);
        if (beaconUuid != null) buf.writeUUID(beaconUuid);
        buf.writeBoolean(looseSpawnId != null);
        if (looseSpawnId != null) buf.writeUUID(looseSpawnId);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            try {
                String pn = player.getName().getString();
                BattleLineSystem.LOGGER.info("PacketClassSelect: player={} faction={} class={} variant={}",
                        pn, factionId, classId, variantId);
                FactionConfig fc = FactionManager.getInstance().getFaction(factionId);
                if (fc == null) {
                    BattleLineSystem.LOGGER.warn("PacketClassSelect: faction not found: {}", factionId);
                    return;
                }
                BattleLineSystem.LOGGER.info("PacketClassSelect: faction {} has {} classes",
                        fc.id, fc.classes != null ? fc.classes.size() : 0);
                // === 部署冷却检查 ===
                int cd = com.battlelinesystem.game.CapturePointManager.getInstance()
                        .getDeployCooldownRemainingMs(player.getUUID());
                if (cd > 0) {
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "§c请在 " + (cd / 1000 + 1) + " 秒后再部署"));
                    reopenClassVote(player, factionId, fc);
                    return;
                }
                if (fc.classes != null) {
                    for (ClassConfig c : fc.classes) {
                        if (c.id.equals(classId)) {
                            // === 人数上限检查 ===
                            if (c.maxPlayers > 0) {
                                MinecraftServer srv = player.getServer();
                                if (srv != null) {
                                    String myTeam = com.battlelinesystem.game.CapturePointManager.getInstance()
                                            .getPlayerTeam(player.getUUID());
                                    int teamCount = com.battlelinesystem.game.CapturePointManager
                                            .countTeamPlayers(srv, myTeam);
                                    int limit = Math.max(1, (int) Math.ceil(teamCount * c.maxPlayers / 100.0));
                                    int current = NetworkManager.getClassCount(factionId, classId);
                                    // 如果玩家自己已选此职业，不计入限制（死亡后持久化职业槽）
                                    String prevClass = com.battlelinesystem.game.CapturePointManager.getInstance()
                                            .getPlayerClass(player.getUUID());
                                    if (factionId.equals(com.battlelinesystem.game.CapturePointManager.getInstance()
                                            .getPlayerFaction(player.getUUID()))
                                            && classId.equals(prevClass)) {
                                        current = Math.max(0, current - 1);
                                    }
                                    if (current >= limit) {
                                        player.sendSystemMessage(
                                                net.minecraft.network.chat.Component.literal(
                                                        "§c该职业人数已满！(" + current + "/" + limit + ")"));
                                        // 重新打开职业选择界面（客户端已经清空了classOptions）
                                        reopenClassVote(player, factionId, fc);
                                        return;
                                    }
                                }
                            }
                            // 始终操作副本，避免变种装备污染共享的 FactionConfig
                            ClassConfig equipCfg = new ClassConfig(c);
                            if (!variantId.isEmpty() && c.variants != null) {
                                for (ClassVariant v : c.variants) {
                                    if (v.id.equals(variantId)) {
                                        if (!isUnlocked(player, v)) {
                                            player.sendSystemMessage(
                                                    net.minecraft.network.chat.Component.literal(
                                                            "§c该变体未解锁！需要: " +
                                                            (v.unlockCondition != null ? v.unlockCondition : "未知")));
                                            // 重新打开职业选择界面（客户端已经清空了classOptions）
                                            reopenClassVote(player, factionId, fc);
                                            return;
                                        }
                                        v.copyTo(equipCfg); // 复制变体装备到副本
                                        break;
                                    }
                                }
                            }
                            // === 释放旧职业（换职时才释放，死亡不释放） ===
                            com.battlelinesystem.game.CapturePointManager cpmSel = com.battlelinesystem.game.CapturePointManager.getInstance();
                            String oldClassId = cpmSel.getPlayerClass(player.getUUID());
                            String oldFactionId = cpmSel.getPlayerFaction(player.getUUID());
                            if (oldClassId != null && oldFactionId != null
                                    && !oldClassId.equals(classId)) {
                                // 从旧职业的 classSelections 中移除
                                var oldFcMap = NetworkManager.classSelectionsMap().get(oldFactionId);
                                if (oldFcMap != null) {
                                    var oldSet = oldFcMap.get(oldClassId);
                                    if (oldSet != null) {
                                        oldSet.remove(player.getUUID());
                                    }
                                }
                            }
                            // === 记录选取 ===
                            NetworkManager.classSelectionsMap()
                                    .computeIfAbsent(factionId, k -> new ConcurrentHashMap<>())
                                    .computeIfAbsent(classId, k -> ConcurrentHashMap.newKeySet())
                                    .add(player.getUUID());

                            // 实时同步职业人数给同阵营玩家
                            NetworkManager.broadcastClassCounts(factionId);

                            // 记录玩家职业（用于死亡扣费）
                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .setPlayerClass(player.getUUID(), classId);
                            // 用客户端修改后的装备 NBT 覆写副本（枪械改装后的数据）
                            if (helmet != null || chestplate != null || leggings != null
                                    || boots != null || offHand != null || extraItems != null) {
                                if (helmet != null) equipCfg.helmet = helmet;
                                if (chestplate != null) equipCfg.chestplate = chestplate;
                                if (leggings != null) equipCfg.leggings = leggings;
                                if (boots != null) equipCfg.boots = boots;
                                if (offHand != null) equipCfg.offHand = offHand;
                                if (extraItems != null) equipCfg.extraItems = extraItems;
                            }
                            equipPlayer(player, equipCfg);

                            // === 指挥官额外物品 ===
                            String playerTeam = com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .getPlayerTeam(player.getUUID());
                            if (playerTeam != null) {
                                String cmdr = com.battlelinesystem.game.CommanderVoteManager.getInstance()
                                        .getCommander(playerTeam);
                                if (cmdr != null && cmdr.equals(player.getName().getString())) {
                                    FactionConfig cmdrFc = FactionManager.getInstance().getFaction(factionId);
                                    if (cmdrFc != null && cmdrFc.commanderExtraItems != null
                                            && !cmdrFc.commanderExtraItems.isEmpty()) {
                                        BattleLineSystem.LOGGER.info("[指挥官物品] {} 获得指挥官额外物品 x{}",
                                                player.getName().getString(), cmdrFc.commanderExtraItems.size());
                                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                                "§6[指挥官] §7你获得了指挥官额外物品！"));
                                        for (String nbt : cmdrFc.commanderExtraItems) {
                                            giveEquip(player, nbt, "inv");
                                        }
                                    }
                                }
                            }

                            // 记录玩家职业（用于死亡扣费）
                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .setPlayerClass(player.getUUID(), classId);

                            // 设置为生存模式并传送到对应队伍出生点
                            player.setGameMode(GameType.SURVIVAL);
                            // 停止部署界面音乐 (RECORDS 声道)
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                                    new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "waiting_01"),
                                    net.minecraft.sounds.SoundSource.RECORDS));
                            // 标记首次部署（触发全局脚本 first_deploy）
                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .markFirstDeploy(player.serverLevel().dimension());
                            // 初始化玩家战绩统计
                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .initPlayerStats(player.serverLevel().dimension(),
                                            player.getUUID(), player.getName().getString(),
                                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                                    .getPlayerTeam(player.getUUID()));
                            MapConfig mapConfig = FactionManager.getInstance().getActiveMapConfig();
                            String team = com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .getPlayerTeam(player.getUUID());
                            ServerLevel gameWorld = player.serverLevel();
                            if (mapConfig != null && team != null) {
                                BlockPos spawnPos;
                                float yaw = 0, pitch = 0;
                                Entity vehicleToRide = null; // 部署后需乘坐的载具
                                // 优先检查宽松重生点（部署到队友位置）
                                if (looseSpawnId != null) {
                                    Entity target = gameWorld.getEntity(looseSpawnId);
                                    if (target != null && target.isAlive()
                                            && !(target instanceof ServerPlayer sp && sp.gameMode.getGameModeForPlayer() == GameType.SPECTATOR)) {
                                        // 队友在载具上：固定翼回退，地面/直升机部署到载具
                                        Entity root = target.getRootVehicle();
                                        if (root instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity ve) {
                                            com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType vt = ve.getVehicleType();
                                            if (vt == com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType.AIRPLANE) {
                                                // 固定翼不部署，回退到常规出生点
                                                if ("A".equals(team)) {
                                                    spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                                    yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                                    pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                                } else {
                                                    spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                                    yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                                    pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                                }
                                            } else {
                                                // 地面载具/直升机 → 尝试让玩家乘坐载具
                                                if (ve.getPassengers().size() < ve.getMaxPassengers()) {
                                                    vehicleToRide = root;
                                                    spawnPos = root.blockPosition();
                                                } else {
                                                    // 载具已满，回退到常规出生点
                                                    if ("A".equals(team)) {
                                                        spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                                        yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                                        pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                                    } else {
                                                        spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                                        yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                                        pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                                    }
                                                }
                                            }
                                        } else {
                                            spawnPos = target.blockPosition();
                                        }
                                    } else {
                                        // 队友已消失，回退到常规出生点
                                        if ("A".equals(team)) {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                        } else {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                        }
                                    }
                                } else if (beaconUuid != null) {
                                    Entity beaconEntity = gameWorld.getEntity(beaconUuid);
                                    if (beaconEntity != null && beaconEntity.isAlive()) {
                                        spawnPos = beaconEntity.blockPosition();
                                    } else {
                                        // 信标实体已消失，回退到常规出生点
                                        if ("A".equals(team)) {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                        } else {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                        }
                                    }
                                } else if (captureSpawnName != null) {
                                    MapConfig.CapturePoint cp = null;
                                    if (mapConfig.capturePoints != null) {
                                        for (MapConfig.CapturePoint ccp : mapConfig.capturePoints) {
                                            if (captureSpawnName.equals(ccp.name)) { cp = ccp; break; }
                                        }
                                    }
                                    // 服务端验证：据点必须被本队完全占领且进度未偏向敌方
                                    boolean canSpawn = false;
                                    if (cp != null) {
                                        var states = com.battlelinesystem.game.CapturePointManager.getInstance()
                                                .getStates(player.serverLevel().dimension());
                                        if (states != null) {
                                            for (var s : states) {
                                                if (s.cp.name.equals(cp.name)) {
                                                    if (team.equals(s.owner)) {
                                                        if ("A".equals(team) && s.progress < 0) canSpawn = true;
                                                        else if ("B".equals(team) && s.progress > 0) canSpawn = true;
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (cp != null && canSpawn) {
                                        spawnPos = cp.getSpawnPos(team);
                                        yaw = cp.getLastSpawnYaw();
                                        pitch = cp.getLastSpawnPitch();
                                    } else {
                                        // 据点未找到，回退到常规出生点
                                        if ("A".equals(team)) {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                        } else {
                                            spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                            yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                            pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                        }
                                    }
                                } else if ("A".equals(team)) {
                                    spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                    yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_a, spawnIndex);
                                    pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_a, spawnIndex);
                                } else {
                                    spawnPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                    yaw = MapConfig.getSpawnYaw(mapConfig.spawnPoints.team_b, spawnIndex);
                                    pitch = MapConfig.getSpawnPitch(mapConfig.spawnPoints.team_b, spawnIndex);
                                }
                                try {
                                    player.teleportTo(gameWorld,
                                            spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                                            yaw, pitch);
                                } catch (Exception e) {
                                    BattleLineSystem.LOGGER.error("部署传送失败 (可能与其他模组冲突): {}", e.toString());
                                }
                                // 部署后乘坐载具
                                if (vehicleToRide != null && vehicleToRide.isAlive()) {
                                    try {
                                        player.startRiding(vehicleToRide, true);
                                    } catch (Exception e) {
                                        BattleLineSystem.LOGGER.error("乘坐载具失败: {}", e.toString());
                                    }
                                }
                            }
                            // 载具部署
                            if (vehicleNbt != null && mapConfig != null && team != null) {
                                int slotIndex = vehicleSlotIndex;
                                ServerLevel spawnWorld = player.serverLevel();
                                net.minecraft.resources.ResourceLocation spawnDim = spawnWorld.dimension().location();
                                if (slotIndex >= 0 && slotIndex < fc.vehicles.size()) {
                                    com.battlelinesystem.game.VehicleRespawnManager vrm = com.battlelinesystem.game.VehicleRespawnManager.getInstance();
                                    long now = System.currentTimeMillis();

                                    // === 用玩家所在 level 直接验证该插槽的实体是否死亡 ===
                                    com.battlelinesystem.game.VehicleRespawnManager.SlotState stCheck = vrm.getSlot(factionId, slotIndex);
                                    if (stCheck != null && !stCheck.aliveUUIDs.isEmpty()) {
                                        boolean anyDead = false;
                                        java.util.Iterator<java.util.Map.Entry<UUID, net.minecraft.resources.ResourceLocation>> it = stCheck.aliveUUIDs.entrySet().iterator();
                                        while (it.hasNext()) {
                                            java.util.Map.Entry<UUID, net.minecraft.resources.ResourceLocation> e = it.next();
                                            if (e.getValue().equals(spawnDim)) {
                                                Entity ent = spawnWorld.getEntity(e.getKey());
                                                if (ent == null || !ent.isAlive()) {
                                                    it.remove();
                                                    anyDead = true;
                                                    BattleLineSystem.LOGGER.debug("[VRM] deploy-validate removed dead faction={} slot={} uuid={}", factionId, slotIndex, e.getKey());
                                                }
                                            }
                                        }
                                        if (anyDead) {
                                            if (stCheck.cooldownSeconds > 0) {
                                                stCheck.cooldownEndMs = now + stCheck.cooldownSeconds * 1000L;
                                                BattleLineSystem.LOGGER.debug("[VRM] deploy-validate startCooldown faction={} slot={} cdSec={} endMs={}", factionId, slotIndex, stCheck.cooldownSeconds, stCheck.cooldownEndMs);
                                            }
                                        }
                                    }

                                    String checkErr = vrm.checkDeploy(factionId, slotIndex, now);
                                    if (checkErr != null) {
                                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(checkErr));
                                        NetworkManager.broadcastVehicleState(factionId);
                                        return;
                                    }
                                }

                                try {
                                    BattleLineSystem.LOGGER.info("PacketClassSelect: spawning vehicle, nbtLen={}", vehicleNbt.length());
                                    CompoundTag itemTag = TagParser.parseTag(vehicleNbt);
                                    if (!itemTag.contains("BlockEntityTag")) {
                                        BattleLineSystem.LOGGER.warn("PacketClassSelect: vehicle NBT missing BlockEntityTag");
                                    } else {
                                        CompoundTag beTag = itemTag.getCompound("BlockEntityTag");
                                        String entityTypeStr = beTag.getString("EntityType");
                                        BattleLineSystem.LOGGER.info("PacketClassSelect: vehicle EntityType={}", entityTypeStr);
                                        EntityType<?> etype = EntityType.byString(entityTypeStr).orElse(null);
                                        if (etype == null) {
                                            BattleLineSystem.LOGGER.warn("PacketClassSelect: unknown EntityType: {}", entityTypeStr);
                                        } else {
                                            BlockPos vehPos;
                                            String vType = (slotIndex >= 0 && slotIndex < fc.vehicles.size())
                                                    ? fc.vehicles.get(slotIndex).type : null;
                                            BattleLineSystem.LOGGER.info("PacketClassSelect: vehicleSpawn lookup vType={} team={} slotIndex={}", vType, team, slotIndex);
                                            BlockPos vehicleSpawn = mapConfig.getRandomVehicleSpawn(team, vType);
                                            float vehicleYaw = mapConfig.getLastVehicleSpawnYaw();
                                            float vehiclePitch = mapConfig.getLastVehicleSpawnPitch();
                                            BattleLineSystem.LOGGER.info("PacketClassSelect: vehicleSpawn result={} yaw={} pitch={}", vehicleSpawn, vehicleYaw, vehiclePitch);
                                            if (vehicleSpawn != null) {
                                                vehPos = vehicleSpawn;
                                            } else if ("A".equals(team)) {
                                                vehPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_a, spawnIndex);
                                            } else {
                                                vehPos = MapConfig.getSpawnPos(mapConfig.spawnPoints.team_b, spawnIndex);
                                            }

                                            if (beTag.contains("Entity", 10)) {
                                                CompoundTag entityTag = beTag.getCompound("Entity");
                                                entityTag.remove("UUID");
                                                entityTag.remove("Pos");
                                                entityTag.remove("Rotation");
                                                entityTag.remove("Motion");
                                                entityTag.putString("id", entityTypeStr);
                                                Entity vehicle = etype.create(spawnWorld);
                                                if (vehicle != null) {
                                                    vehicle.load(entityTag);
                                                    vehicle.setPos(vehPos.getX() + 0.5, vehPos.getY() + 1, vehPos.getZ() + 0.5);
                                                    if (vehicleSpawn != null) {
                                                        vehicle.setYRot(vehicleYaw);
                                                        vehicle.setXRot(vehiclePitch);
                                                        vehicle.yRotO = vehicleYaw;
                                                        vehicle.xRotO = vehiclePitch;
                                                    }
                                                    spawnAndMount(spawnWorld, vehicle, player, entityTypeStr);
                                                    if (vehicle.isAlive() && vehicleSpawn != null) {
                                                        applySpawnVelocity(vehicle, vehicleSpawn, vType);
                                                    }
                                                    if (vehicle.isAlive()) {
                                                        com.battlelinesystem.game.VehicleRespawnManager.getInstance()
                                                                .onDeploy(factionId, slotIndex, vehicle.getUUID(), spawnDim);
                                                    }
                                                    runVehicleDeployScripts(spawnWorld, player, fc, slotIndex);
                                                }
                                            } else {
                                                Entity vehicle = etype.create(spawnWorld);
                                                if (vehicle != null) {
                                                    vehicle.setPos(vehPos.getX() + 0.5, vehPos.getY() + 1, vehPos.getZ() + 0.5);
                                                    if (vehicleSpawn != null) {
                                                        vehicle.setYRot(vehicleYaw);
                                                        vehicle.setXRot(vehiclePitch);
                                                        vehicle.yRotO = vehicleYaw;
                                                        vehicle.xRotO = vehiclePitch;
                                                    }
                                                    spawnAndMount(spawnWorld, vehicle, player, entityTypeStr);
                                                    if (vehicle.isAlive() && vehicleSpawn != null) {
                                                        applySpawnVelocity(vehicle, vehicleSpawn, vType);
                                                    }
                                                    if (vehicle.isAlive()) {
                                                        com.battlelinesystem.game.VehicleRespawnManager.getInstance()
                                                                .onDeploy(factionId, slotIndex, vehicle.getUUID(), spawnDim);
                                                    }
                                                    runVehicleDeployScripts(spawnWorld, player, fc, slotIndex);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ve) {
                                    BattleLineSystem.LOGGER.warn("PacketClassSelect: vehicle spawn failed", ve);
                                }
                                // 部署音效
                                int soundSlot = vehicleSlotIndex;
                                if (soundSlot >= 0 && soundSlot < fc.vehicles.size()) {
                                    com.battlelinesystem.faction.VehicleConfig vc = fc.vehicles.get(soundSlot);
                                    if (vc.deploySound != null && !vc.deploySound.isEmpty()) {
                                        com.battlelinesystem.game.CapturePointManager cpm = com.battlelinesystem.game.CapturePointManager.getInstance();
                                        String soundTeam = cpm.getPlayerTeam(player.getUUID());
                                        for (ServerPlayer sp : spawnWorld.getServer().getPlayerList().getPlayers()) {
                                            if (sp.level() != spawnWorld) continue;
                                            String t = cpm.getPlayerTeam(sp.getUUID());
                                            boolean play = false;
                                            switch (vc.deploySoundTarget) {
                                                case "all": play = true; break;
                                                case "team": play = soundTeam != null && soundTeam.equals(t); break;
                                                case "enemy": play = t != null && !t.equals(soundTeam); break;
                                            }
                                            if (play) {
                                                cpm.playSoundToPlayer(sp, vc.deploySound);
                                            }
                                        }
                                    }
                                }
                                NetworkManager.broadcastVehicleState(factionId);
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketClassSelect error", e);
            }
        });
        return true;
    }

    private static boolean spawnAndMount(ServerLevel world, Entity vehicle, ServerPlayer player, String typeName) {
        boolean added = world.addFreshEntity(vehicle);
        BattleLineSystem.LOGGER.info("PacketClassSelect: vehicle addFreshEntity={} type={}", added, typeName);
        if (added) {
            boolean mounted = player.startRiding(vehicle, true);
            BattleLineSystem.LOGGER.info("PacketClassSelect: vehicle startRiding={}", mounted);
            return mounted;
        }
        return false;
    }

    /** 如果载具从 plane 出生点部署，注册满油门 */
    private static void applySpawnVelocity(Entity vehicle, BlockPos vehicleSpawn, String vehicleType) {
        if (vehicleSpawn != null && "plane".equals(vehicleType)) {
            com.battlelinesystem.event.GameEventHandler.registerVehicleBoost(vehicle);
        }
    }

    /** 执行载具部署脚本 */
    private static void runVehicleDeployScripts(ServerLevel world, ServerPlayer player,
                                                 FactionConfig fc, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= fc.vehicles.size()) return;
        com.battlelinesystem.faction.VehicleConfig vc = fc.vehicles.get(slotIndex);
        if (vc.deployScripts == null || vc.deployScripts.isEmpty()) return;
        MinecraftServer server = world.getServer();
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack();
        for (String cmd : vc.deployScripts) {
            if (cmd == null || cmd.trim().isEmpty()) continue;
            try {
                String processed = cmd.replace("@p", player.getName().getString());
                server.getCommands().performPrefixedCommand(source, processed);
            } catch (Exception e) {
                BattleLineSystem.LOGGER.warn("载具部署脚本执行失败: {}", e.getMessage());
            }
        }
    }

    private static boolean isUnlocked(ServerPlayer player, ClassVariant v) {
        if (v.unlockCondition == null || v.unlockCondition.isEmpty()) return true;
        String cond = v.unlockCondition.trim();
        if (cond.startsWith("permission:")) {
            String perm = cond.substring("permission:".length());
            return player.hasPermissions(2);
        }
        if (cond.startsWith("level:")) {
            try {
                int lv = Integer.parseInt(cond.substring("level:".length()));
                return player.experienceLevel >= lv;
            } catch (NumberFormatException e) { return false; }
        }
        if (cond.startsWith("purchase:")) {
            String variantId = cond.substring("purchase:".length());
            return com.battlelinesystem.game.PlayerProgressionManager.getInstance()
                    .isPurchased(player.getUUID(), variantId);
        }
        return true;
    }

    private static void equipPlayer(ServerPlayer player, ClassConfig cls) {
        BattleLineSystem.LOGGER.info("equipPlayer: {} helmet={} chest={} legs={} boots={} off={} extra={}",
                player.getName().getString(),
                cls.helmet, cls.chestplate, cls.leggings, cls.boots, cls.offHand,
                cls.extraItems != null ? cls.extraItems.size() : 0);
        if (cls.helmet != null) giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), cls.helmet), "head");
        if (cls.chestplate != null) giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), cls.chestplate), "chest");
        if (cls.leggings != null) giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), cls.leggings), "legs");
        if (cls.boots != null) giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), cls.boots), "feet");
        if (cls.offHand != null) giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), cls.offHand), "offhand");
        if (cls.extraItems != null) {
            for (String nbt : cls.extraItems) {
                giveEquip(player, PacketSaveGunMod.GunModStorage.apply(player.getUUID(), nbt), "inv");
            }
        }
    }

    private static void giveEquip(ServerPlayer player, String stored, String slot) {
        BattleLineSystem.LOGGER.info("giveEquip: player={} stored={} slot={}",
                player.getName().getString(),
                stored != null && stored.length() > 60 ? stored.substring(0, 60) + "..." : stored,
                slot);
        net.minecraft.world.item.ItemStack st;
        if (stored != null && stored.startsWith("{")) {
            try {
                st = net.minecraft.world.item.ItemStack.of(
                        net.minecraft.nbt.TagParser.parseTag(stored));
                BattleLineSystem.LOGGER.info("giveEquip: parsed NBT -> {}", st);
            } catch (Exception e) {
                BattleLineSystem.LOGGER.warn("giveEquip: NBT parse failed for {}", stored, e);
                return;
            }
        } else {
            net.minecraft.resources.ResourceLocation rl;
            int colon = stored.indexOf(':');
            if (colon >= 0) {
                rl = new net.minecraft.resources.ResourceLocation(
                        stored.substring(0, colon), stored.substring(colon + 1));
            } else {
                rl = new net.minecraft.resources.ResourceLocation(stored);
            }
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                BattleLineSystem.LOGGER.warn("giveEquip: item not found for {}", stored);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c职业装备未找到: " + stored));
                return;
            }
            BattleLineSystem.LOGGER.info("giveEquip: registry -> {}", item);
            st = new net.minecraft.world.item.ItemStack(item);
        }
        boolean equipped = false;
        switch (slot) {
            case "head" -> {
                if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, st);
                    equipped = true;
                }
            }
            case "chest" -> {
                if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, st);
                    equipped = true;
                }
            }
            case "legs" -> {
                if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, st);
                    equipped = true;
                }
            }
            case "feet" -> {
                if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, st);
                    equipped = true;
                }
            }
            case "mainhand" -> {
                if (player.getMainHandItem().isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, st);
                    equipped = true;
                }
            }
            case "offhand" -> {
                if (player.getOffhandItem().isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, st);
                    equipped = true;
                }
            }
            case "inv" -> {
            }
        }
        if (equipped) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a已装备: " + st.getHoverName().getString()));
        } else {
            if (!player.addItem(st)) {
                player.drop(st, false);
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e已放入背包: " + st.getHoverName().getString()));
        }
    }

    private static void setTeamNames(PacketOpenClassVote pkt, MinecraftServer srv) {
        com.battlelinesystem.game.CapturePointManager cpm = com.battlelinesystem.game.CapturePointManager.getInstance();
        com.battlelinesystem.faction.FactionManager fm = com.battlelinesystem.faction.FactionManager.getInstance();
        com.battlelinesystem.faction.FactionConfig ta = fm.getFaction(cpm.getTeamFaction("A"));
        com.battlelinesystem.faction.FactionConfig tb = fm.getFaction(cpm.getTeamFaction("B"));
        pkt.teamAName = ta != null ? ta.name : "A队";
        pkt.teamBName = tb != null ? tb.name : "B队";
    }

    /** 部署校验失败时重新打开职业选择界面 */
    private static void reopenClassVote(ServerPlayer player, String factionId, FactionConfig fc) {
        MinecraftServer srv = player.getServer();
        if (srv == null) return;
        String pt = CapturePointManager.getInstance().getPlayerTeam(player.getUUID());
        PacketOpenClassVote pkt = new PacketOpenClassVote(
                factionId, fc.name, fc.displayColor,
                (byte) ("A".equals(pt) ? 0 : 1),
                new java.util.ArrayList<>(fc.classes), fc.vehicles);
        pkt.totalPlayers = CapturePointManager.countTeamPlayers(srv, pt);
        pkt.looseSpawn = fc.looseSpawn;
        // 设置双方阵营名称
        setTeamNames(pkt, srv);
        for (ServerPlayer sp : srv.getPlayerList().getPlayers()) {
            if (pt.equals(CapturePointManager.getInstance().getPlayerTeam(sp.getUUID()))) {
                pkt.sameTeamUUIDs.add(sp.getUUID());
            }
        }
        AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}

package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.VehicleConfig;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.network.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketOpenClassVote extends PacketBase {
    public final String factionId;
    public final String factionName;
    public final String factionColor;
    public final byte team; // 0=A, 1=B
    public String teamAName = "A队";
    public String teamBName = "B队";
    public final List<ClassConfig> classes;
    public int[] classCounts;
    public int totalPlayers;
    public final List<VehicleConfig> vehicleList;
    public int[] vehicleCounts;
    public boolean[] vehicleAlive;
    public int[] vehicleCooldownsData;
    public final List<UUID> sameTeamUUIDs;
    public boolean looseSpawn;
    public int deployCooldownMs;

    public PacketOpenClassVote() {
        this.factionId = "";
        this.factionName = "";
        this.factionColor = "";
        this.team = 0;
        this.classes = new ArrayList<>();
        this.classCounts = new int[0];
        this.totalPlayers = 0;
        this.vehicleList = new ArrayList<>();
        this.vehicleCounts = new int[0];
        this.vehicleAlive = new boolean[0];
        this.vehicleCooldownsData = new int[0];
        this.sameTeamUUIDs = new ArrayList<>();
        this.looseSpawn = false;
    }

    public PacketOpenClassVote(FriendlyByteBuf buf) {
        this.factionId = buf.readUtf();
        this.factionName = buf.readUtf();
        this.factionColor = buf.readUtf();
        this.team = buf.readByte();
        int size = buf.readVarInt();
        this.totalPlayers = buf.readVarInt();
        this.classes = new ArrayList<>(size);
        this.classCounts = new int[size];
        for (int i = 0; i < size; i++) {
            ClassConfig c = new ClassConfig(buf.readUtf(), buf.readUtf());
            c.helmet = buf.readUtf(); if (c.helmet.isEmpty()) c.helmet = null;
            c.chestplate = buf.readUtf(); if (c.chestplate.isEmpty()) c.chestplate = null;
            c.leggings = buf.readUtf(); if (c.leggings.isEmpty()) c.leggings = null;
            c.boots = buf.readUtf(); if (c.boots.isEmpty()) c.boots = null;
            c.offHand = buf.readUtf(); if (c.offHand.isEmpty()) c.offHand = null;
            PacketFactionList.readExtraItems(buf, c);
            PacketFactionList.readVariants(buf, c);
            PacketFactionList.readHiddenItems(buf, c);
            PacketFactionList.readGunPools(buf, c);
            classes.add(c);
            classCounts[i] = buf.readVarInt();
        }
        int vc = buf.readVarInt();
        this.vehicleList = new ArrayList<>(vc);
        for (int i = 0; i < vc; i++) {
            VehicleConfig v = new VehicleConfig(buf.readUtf(), buf.readUtf(), buf.readUtf());
            v.type = buf.readUtf();
            v.maxCount = buf.readVarInt();
            v.cooldownSeconds = buf.readVarInt();
            int dsc = buf.readVarInt();
            if (dsc > 0) {
                v.deployScripts = new ArrayList<>(dsc);
                for (int j = 0; j < dsc; j++) v.deployScripts.add(buf.readUtf());
            }
            vehicleList.add(v);
        }
        this.vehicleCounts = new int[vc];
        this.vehicleAlive = new boolean[vc];
        this.vehicleCooldownsData = new int[vc];
        for (int i = 0; i < vc; i++) vehicleCounts[i] = buf.readVarInt();
        for (int i = 0; i < vc; i++) vehicleAlive[i] = buf.readBoolean();
        for (int i = 0; i < vc; i++) vehicleCooldownsData[i] = buf.readVarInt();
        int teamCount = buf.readVarInt();
        this.sameTeamUUIDs = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) sameTeamUUIDs.add(buf.readUUID());
        this.looseSpawn = buf.readBoolean();
        this.teamAName = buf.readUtf();
        this.teamBName = buf.readUtf();
        this.deployCooldownMs = buf.readVarInt();
    }

    public PacketOpenClassVote(String factionId, String factionName,
                               String factionColor, byte team,
                               List<ClassConfig> classes,
                               List<VehicleConfig> vehicles) {
        this.factionId = factionId;
        this.factionName = factionName;
        this.factionColor = factionColor;
        this.team = team;
        this.classes = classes;
        this.classCounts = new int[classes.size()];
        this.vehicleList = (vehicles != null) ? new ArrayList<>(vehicles) : new ArrayList<>();
        this.vehicleCounts = new int[this.vehicleList.size()];
        this.vehicleAlive = new boolean[this.vehicleList.size()];
        this.vehicleCooldownsData = new int[this.vehicleList.size()];
        this.sameTeamUUIDs = new ArrayList<>();
        this.looseSpawn = false;
        // 填充载具当前计数、存活状态、冷却剩余（委托给 VehicleRespawnManager）
        com.battlelinesystem.game.VehicleRespawnManager vrm = com.battlelinesystem.game.VehicleRespawnManager.getInstance();
        long now = System.currentTimeMillis();
        for (int i = 0; i < this.vehicleList.size(); i++) {
            com.battlelinesystem.game.VehicleRespawnManager.SlotState st = vrm.getSlot(factionId, i);
            if (st != null) {
                this.vehicleCounts[i] = st.aliveCount();
                this.vehicleAlive[i] = st.hasAlive();
                this.vehicleCooldownsData[i] = st.cooldownRemainingSec(now);
            }
        }
        if (this.vehicleList.size() > 0) {
            BattleLineSystem.LOGGER.debug("[VRM] PacketOpenClassVote faction={} vCount={} alive={} cdData={}",
                factionId, this.vehicleList.size(),
                java.util.Arrays.toString(this.vehicleAlive),
                java.util.Arrays.toString(this.vehicleCooldownsData));
        }
        for (int i = 0; i < classes.size(); i++) {
            ClassConfig c = classes.get(i);
            if (c.maxPlayers > 0) {
                this.classCounts[i] = com.battlelinesystem.network.NetworkManager.getClassCount(factionId, c.id);
            }
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionId);
        buf.writeUtf(factionName);
        buf.writeUtf(factionColor);
        buf.writeByte(team);
        buf.writeVarInt(classes.size());
        buf.writeVarInt(totalPlayers);
        for (int i = 0; i < classes.size(); i++) {
            ClassConfig c = classes.get(i);
            buf.writeUtf(c.id);
            buf.writeUtf(c.name);
            buf.writeUtf(c.helmet != null ? c.helmet : "");
            buf.writeUtf(c.chestplate != null ? c.chestplate : "");
            buf.writeUtf(c.leggings != null ? c.leggings : "");
            buf.writeUtf(c.boots != null ? c.boots : "");
            buf.writeUtf(c.offHand != null ? c.offHand : "");
            PacketFactionList.writeExtraItems(buf, c);
            PacketFactionList.writeVariants(buf, c);
            PacketFactionList.writeHiddenItems(buf, c);
            PacketFactionList.writeGunPools(buf, c);
            buf.writeVarInt(classCounts[i]);
        }
        buf.writeVarInt(vehicleList.size());
        for (VehicleConfig v : vehicleList) {
            buf.writeUtf(v.id != null ? v.id : "");
            buf.writeUtf(v.name != null ? v.name : "");
            buf.writeUtf(v.itemNbt != null ? v.itemNbt : "");
            buf.writeUtf(v.type != null ? v.type : "tank");
            buf.writeVarInt(v.maxCount);
            buf.writeVarInt(v.cooldownSeconds);
            if (v.deployScripts != null) {
                buf.writeVarInt(v.deployScripts.size());
                for (String s : v.deployScripts) buf.writeUtf(s);
            } else {
                buf.writeVarInt(0);
            }
        }
        for (int c : vehicleCounts) buf.writeVarInt(c);
        for (boolean a : vehicleAlive) buf.writeBoolean(a);
        for (int cd : vehicleCooldownsData) buf.writeVarInt(cd);
        buf.writeVarInt(sameTeamUUIDs.size());
        for (UUID u : sameTeamUUIDs) buf.writeUUID(u);
        buf.writeBoolean(looseSpawn);
        buf.writeUtf(teamAName != null ? teamAName : "A队");
        buf.writeUtf(teamBName != null ? teamBName : "B队");
        buf.writeVarInt(deployCooldownMs);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(4, this));
        return true;
    }
}

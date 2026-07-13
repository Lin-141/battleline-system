package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PacketMapConfigSave extends PacketBase {
    public final String mapId;
    public final String name;
    public final String description;
    public final int minPlayers;
    public final int maxPlayers;
    public final int initialScore;
    public final List<String> factionPoolA;
    public final List<String> factionPoolB;
    public final List<MapConfig.CapturePoint> capturePoints;
    public final List<MapConfig.GlobalScript> globalScripts;
    public final String startMusic;
    public final String victoryMusic;
    public final String defeatMusic;
    public final String nearEndMusic;
    public final int nearEndThreshold;
    public final int timeLimitMinutes;
    public final String timeUpRule;

    public PacketMapConfigSave() {
        this.mapId = "";
        this.name = "";
        this.description = "";
        this.minPlayers = 0;
        this.maxPlayers = 0;
        this.initialScore = 0;
        this.factionPoolA = new ArrayList<>();
        this.factionPoolB = new ArrayList<>();
        this.capturePoints = new ArrayList<>();
        this.globalScripts = new ArrayList<>();
        this.startMusic = "";
        this.victoryMusic = "";
        this.defeatMusic = "";
        this.nearEndMusic = "";
        this.nearEndThreshold = 0;
        this.timeLimitMinutes = 0;
        this.timeUpRule = "";
    }

    public PacketMapConfigSave(FriendlyByteBuf buf) {
        this.mapId = buf.readUtf();
        this.name = buf.readUtf();
        this.description = buf.readUtf();
        this.minPlayers = buf.readVarInt();
        this.maxPlayers = buf.readVarInt();
        this.initialScore = buf.readVarInt();
        int poolASize = buf.readVarInt();
        this.factionPoolA = new ArrayList<>(poolASize);
        for (int i = 0; i < poolASize; i++) factionPoolA.add(buf.readUtf());
        int poolBSize = buf.readVarInt();
        this.factionPoolB = new ArrayList<>(poolBSize);
        for (int i = 0; i < poolBSize; i++) factionPoolB.add(buf.readUtf());
        int cpSize = buf.readVarInt();
        this.capturePoints = new ArrayList<>(cpSize);
        for (int i = 0; i < cpSize; i++) {
            MapConfig.CapturePoint cp = new MapConfig.CapturePoint();
            cp.name = buf.readUtf();
            String dn = buf.readUtf();
            if (!dn.isEmpty()) cp.displayName = dn;
            int zoneCount = buf.readVarInt();
            for (int j = 0; j < zoneCount; j++) {
                int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                int x2 = buf.readVarInt(), y2 = buf.readVarInt(), z2 = buf.readVarInt();
                cp.addZone(x, y, z, x2, y2, z2);
            }
            int drc = buf.readVarInt();
            cp.destroyRegions = new ArrayList<>();
            for (int j = 0; j < drc; j++) {
                int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                int x2 = buf.readVarInt(), y2 = buf.readVarInt(), z2 = buf.readVarInt();
                cp.destroyRegions.add(new int[][]{
                    {Math.min(x, x2), Math.min(y, y2), Math.min(z, z2)},
                    {Math.max(x, x2), Math.max(y, y2), Math.max(z, z2)}
                });
            }
            cp.scripts = new MapConfig.CapturePoint.Scripts();
            cp.scripts.onCaptureA = readScripts(buf);
            cp.scripts.onCaptureB = readScripts(buf);
            cp.scripts.onContest = readScripts(buf);
            cp.scripts.onUncapture = readScripts(buf);
            int prc = buf.readVarInt();
            cp.prerequisites = new ArrayList<>(prc);
            for (int j = 0; j < prc; j++) cp.prerequisites.add(buf.readUtf());
            String at2 = buf.readUtf();
            if (!at2.isEmpty()) cp.attackerTeam = at2;
            String io2 = buf.readUtf();
            if (!io2.isEmpty()) cp.initialOwner = io2;
            capturePoints.add(cp);
        }
        int gsCount = buf.readVarInt();
        this.globalScripts = new ArrayList<>(gsCount);
        for (int i = 0; i < gsCount; i++) {
            MapConfig.GlobalScript gs = new MapConfig.GlobalScript();
            gs.trigger = buf.readUtf();
            gs.value = buf.readVarInt();
            gs.team = buf.readUtf();
            int cmdCount = buf.readVarInt();
            gs.commands = new ArrayList<>(cmdCount);
            for (int j = 0; j < cmdCount; j++) gs.commands.add(buf.readUtf());
            globalScripts.add(gs);
        }
        this.startMusic = buf.readUtf();
        this.victoryMusic = buf.readUtf();
        this.defeatMusic = buf.readUtf();
        this.nearEndMusic = buf.readUtf();
        this.nearEndThreshold = buf.readVarInt();
        this.timeLimitMinutes = buf.readVarInt();
        this.timeUpRule = buf.readUtf();
    }

    public PacketMapConfigSave(String mapId, String name, String description,
                               int minPlayers, int maxPlayers, int initialScore,
                               List<String> factionPoolA, List<String> factionPoolB,
                               List<MapConfig.CapturePoint> capturePoints,
                               List<MapConfig.GlobalScript> globalScripts,
                               String startMusic, String victoryMusic, String defeatMusic,
                               String nearEndMusic, int nearEndThreshold,
                               int timeLimitMinutes, String timeUpRule) {
        this.mapId = mapId;
        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.initialScore = initialScore;
        this.factionPoolA = factionPoolA;
        this.factionPoolB = factionPoolB;
        this.capturePoints = capturePoints;
        this.globalScripts = globalScripts;
        this.startMusic = startMusic != null ? startMusic : "";
        this.victoryMusic = victoryMusic != null ? victoryMusic : "";
        this.defeatMusic = defeatMusic != null ? defeatMusic : "";
        this.nearEndMusic = nearEndMusic != null ? nearEndMusic : "";
        this.nearEndThreshold = nearEndThreshold;
        this.timeLimitMinutes = timeLimitMinutes;
        this.timeUpRule = timeUpRule != null ? timeUpRule : "";
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);
        buf.writeUtf(name);
        buf.writeUtf(description != null ? description : "");
        buf.writeVarInt(minPlayers);
        buf.writeVarInt(maxPlayers);
        buf.writeVarInt(initialScore);
        buf.writeVarInt(factionPoolA.size());
        for (String f : factionPoolA) buf.writeUtf(f);
        buf.writeVarInt(factionPoolB.size());
        for (String f : factionPoolB) buf.writeUtf(f);
        buf.writeVarInt(capturePoints.size());
        for (MapConfig.CapturePoint cp : capturePoints) {
            buf.writeUtf(cp.name != null ? cp.name : "");
            buf.writeUtf(cp.displayName != null ? cp.displayName : "");
            int zc = cp.zones != null ? cp.zones.size() : 0;
            buf.writeVarInt(zc);
            if (cp.zones != null) {
                for (int[][] z : cp.zones) {
                    buf.writeVarInt(z[0][0]); buf.writeVarInt(z[0][1]); buf.writeVarInt(z[0][2]);
                    buf.writeVarInt(z[1][0]); buf.writeVarInt(z[1][1]); buf.writeVarInt(z[1][2]);
                }
            }
            int drc = cp.destroyRegions != null ? cp.destroyRegions.size() : 0;
            buf.writeVarInt(drc);
            if (cp.destroyRegions != null) {
                for (int[][] z : cp.destroyRegions) {
                    buf.writeVarInt(z[0][0]); buf.writeVarInt(z[0][1]); buf.writeVarInt(z[0][2]);
                    buf.writeVarInt(z[1][0]); buf.writeVarInt(z[1][1]); buf.writeVarInt(z[1][2]);
                }
            }
            writeScripts(buf, cp.scripts.onCaptureA);
            writeScripts(buf, cp.scripts.onCaptureB);
            writeScripts(buf, cp.scripts.onContest);
            writeScripts(buf, cp.scripts.onUncapture);
            int prc = cp.prerequisites != null ? cp.prerequisites.size() : 0;
            buf.writeVarInt(prc);
            if (cp.prerequisites != null) {
                for (String pr : cp.prerequisites) buf.writeUtf(pr);
            }
            buf.writeUtf(cp.attackerTeam != null ? cp.attackerTeam : "");
            buf.writeUtf(cp.initialOwner != null ? cp.initialOwner : "");
        }
        int gsCount = globalScripts != null ? globalScripts.size() : 0;
        buf.writeVarInt(gsCount);
        if (globalScripts != null) {
            for (MapConfig.GlobalScript gs : globalScripts) {
                buf.writeUtf(gs.trigger);
                buf.writeVarInt(gs.value);
                buf.writeUtf(gs.team);
                buf.writeVarInt(gs.commands.size());
                for (String c : gs.commands) buf.writeUtf(c);
            }
        }
        buf.writeUtf(startMusic);
        buf.writeUtf(victoryMusic);
        buf.writeUtf(defeatMusic);
        buf.writeUtf(nearEndMusic);
        buf.writeVarInt(nearEndThreshold);
        buf.writeVarInt(timeLimitMinutes);
        buf.writeUtf(timeUpRule != null ? timeUpRule : "");
    }

    static void writeScripts(FriendlyByteBuf buf, List<String> cmds) {
        int count = cmds != null ? cmds.size() : 0;
        buf.writeVarInt(count);
        if (cmds != null) {
            for (String s : cmds) buf.writeUtf(s);
        }
    }

    static List<String> readScripts(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> cmds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) cmds.add(buf.readUtf());
        return cmds;
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            try {
                if (!context.getSender().hasPermissions(2)) {
                    BattleLineSystem.LOGGER.warn("PacketMapConfigSave: no permission");
                    return;
                }
                Path mapJson = Path.of("templates", mapId, "map.json");
                if (!Files.exists(mapJson)) {
                    BattleLineSystem.LOGGER.warn("PacketMapConfigSave: map.json not found for {}", mapId);
                    return;
                }
                MapConfig config = MapConfig.GSON.fromJson(Files.readString(mapJson), MapConfig.class);
                config.name = name;
                config.description = description;
                config.minPlayers = minPlayers;
                config.maxPlayers = maxPlayers;
                config.initialScore = initialScore;
                config.factionPoolA = factionPoolA;
                config.factionPoolB = factionPoolB;
                config.capturePoints = capturePoints;
                config.globalScripts = globalScripts;
                config.startMusic = startMusic;
                config.victoryMusic = victoryMusic;
                config.defeatMusic = defeatMusic;
                config.nearEndMusic = nearEndMusic;
                config.nearEndThreshold = nearEndThreshold;
                config.timeLimitMinutes = timeLimitMinutes;
                config.timeUpRule = timeUpRule;
                Files.writeString(mapJson, MapConfig.GSON.toJson(config));
                BattleLineSystem.LOGGER.info("PacketMapConfigSave: saved {}", mapId);
                context.getSender().sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§a地图配置已保存: " + name));
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketMapConfigSave error", e);
            }
        });
        return true;
    }
}

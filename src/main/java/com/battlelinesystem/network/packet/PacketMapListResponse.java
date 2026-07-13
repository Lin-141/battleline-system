package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PacketMapListResponse extends PacketBase {
    public final List<MapEntry> maps;

    public PacketMapListResponse() {
        this.maps = new ArrayList<>();
    }

    public PacketMapListResponse(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.maps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String mode = buf.readUtf();
            String desc = buf.readUtf();
            int minP = buf.readVarInt();
            int maxP = buf.readVarInt();
            int initScore = buf.readVarInt();
            boolean hasThumb = buf.readBoolean();
            int poolASize = buf.readVarInt();
            List<String> poolA = new ArrayList<>(poolASize);
            for (int j = 0; j < poolASize; j++) poolA.add(buf.readUtf());
            int poolBSize = buf.readVarInt();
            List<String> poolB = new ArrayList<>(poolBSize);
            for (int j = 0; j < poolBSize; j++) poolB.add(buf.readUtf());
            int cpSize = buf.readVarInt();
            List<MapConfig.CapturePoint> cps = new ArrayList<>(cpSize);
            for (int j = 0; j < cpSize; j++) {
                MapConfig.CapturePoint cp = new MapConfig.CapturePoint();
                cp.name = buf.readUtf();
                String dn = buf.readUtf();
                if (!dn.isEmpty()) cp.displayName = dn;
                int zoneCount = buf.readVarInt();
                for (int k = 0; k < zoneCount; k++) {
                    int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                    int x2 = buf.readVarInt(), y2 = buf.readVarInt(), z2 = buf.readVarInt();
                    cp.addZone(x, y, z, x2, y2, z2);
                }
                int drc = buf.readVarInt();
                cp.destroyRegions = new ArrayList<>();
                for (int k = 0; k < drc; k++) {
                    int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                    int x2 = buf.readVarInt(), y2 = buf.readVarInt(), z2 = buf.readVarInt();
                    cp.destroyRegions.add(new int[][]{
                        {Math.min(x, x2), Math.min(y, y2), Math.min(z, z2)},
                        {Math.max(x, x2), Math.max(y, y2), Math.max(z, z2)}
                    });
                }
                cp.scripts = new MapConfig.CapturePoint.Scripts();
                cp.scripts.onCaptureA = PacketMapConfigSave.readScripts(buf);
                cp.scripts.onCaptureB = PacketMapConfigSave.readScripts(buf);
                cp.scripts.onContest = PacketMapConfigSave.readScripts(buf);
                cp.scripts.onUncapture = PacketMapConfigSave.readScripts(buf);
                int prc = buf.readVarInt();
                cp.prerequisites = new ArrayList<>(prc);
                for (int k = 0; k < prc; k++) cp.prerequisites.add(buf.readUtf());
                String at = buf.readUtf();
                if (!at.isEmpty()) cp.attackerTeam = at;
                String io = buf.readUtf();
                if (!io.isEmpty()) cp.initialOwner = io;
                cps.add(cp);
            }
            int gsSize = buf.readVarInt();
            List<MapConfig.GlobalScript> gss = new ArrayList<>(gsSize);
            for (int j = 0; j < gsSize; j++) {
                MapConfig.GlobalScript gs = new MapConfig.GlobalScript();
                gs.trigger = buf.readUtf();
                gs.value = buf.readVarInt();
                gs.team = buf.readUtf();
                int cmdCount = buf.readVarInt();
                gs.commands = new ArrayList<>(cmdCount);
                for (int k = 0; k < cmdCount; k++) gs.commands.add(buf.readUtf());
                gss.add(gs);
            }
            String startMusic = buf.readUtf();
            String victoryMusic = buf.readUtf();
            String defeatMusic = buf.readUtf();
            String nearEndMusic = buf.readUtf();
            int nearEndThreshold = buf.readVarInt();
            int timeLimitMinutes = buf.readVarInt();
            String timeUpRule = buf.readUtf();
            maps.add(new MapEntry(id, name, mode, desc, minP, maxP, initScore, hasThumb, poolA, poolB, cps, gss,
                    startMusic, victoryMusic, defeatMusic, nearEndMusic, nearEndThreshold,
                    timeLimitMinutes, timeUpRule));
        }
    }

    public PacketMapListResponse(List<MapEntry> maps) {
        this.maps = maps;
    }

    public List<MapEntry> getMaps() { return maps; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(maps.size());
        for (MapEntry m : maps) {
            buf.writeUtf(m.id);
            buf.writeUtf(m.name);
            buf.writeUtf(m.mode);
            buf.writeUtf(m.description != null ? m.description : "");
            buf.writeVarInt(m.minPlayers);
            buf.writeVarInt(m.maxPlayers);
            buf.writeVarInt(m.initialScore);
            buf.writeBoolean(m.hasThumbnail);
            buf.writeVarInt(m.factionPoolA.size());
            for (String f : m.factionPoolA) buf.writeUtf(f);
            buf.writeVarInt(m.factionPoolB.size());
            for (String f : m.factionPoolB) buf.writeUtf(f);
            buf.writeVarInt(m.capturePoints.size());
            for (MapConfig.CapturePoint cp : m.capturePoints) {
                buf.writeUtf(cp.name);
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
                PacketMapConfigSave.writeScripts(buf, cp.scripts.onCaptureA);
                PacketMapConfigSave.writeScripts(buf, cp.scripts.onCaptureB);
                PacketMapConfigSave.writeScripts(buf, cp.scripts.onContest);
                PacketMapConfigSave.writeScripts(buf, cp.scripts.onUncapture);
                int prc = cp.prerequisites != null ? cp.prerequisites.size() : 0;
                buf.writeVarInt(prc);
                if (cp.prerequisites != null) {
                    for (String pr : cp.prerequisites) buf.writeUtf(pr);
                }
                buf.writeUtf(cp.attackerTeam != null ? cp.attackerTeam : "");
                buf.writeUtf(cp.initialOwner != null ? cp.initialOwner : "");
            }
            buf.writeVarInt(m.globalScripts.size());
            for (MapConfig.GlobalScript gs : m.globalScripts) {
                buf.writeUtf(gs.trigger);
                buf.writeVarInt(gs.value);
                buf.writeUtf(gs.team);
                buf.writeVarInt(gs.commands.size());
                for (String c : gs.commands) buf.writeUtf(c);
            }
            buf.writeUtf(m.startMusic != null ? m.startMusic : "");
            buf.writeUtf(m.victoryMusic != null ? m.victoryMusic : "");
            buf.writeUtf(m.defeatMusic != null ? m.defeatMusic : "");
            buf.writeUtf(m.nearEndMusic != null ? m.nearEndMusic : "");
            buf.writeVarInt(m.nearEndThreshold);
            buf.writeVarInt(m.timeLimitMinutes);
            buf.writeUtf(m.timeUpRule != null ? m.timeUpRule : "");
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(2, this));
        return true;
    }

    // ===== MapEntry inner class =====

    public static class MapEntry {
        public final String id;
        public final String name;
        public final String mode;
        public final String description;
        public final int minPlayers;
        public final int maxPlayers;
        public final int initialScore;
        public final boolean hasThumbnail;
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

        public MapEntry(String id, String name, String mode, String description,
                        int minPlayers, int maxPlayers, int initialScore, boolean hasThumbnail,
                        List<String> factionPoolA, List<String> factionPoolB,
                        List<MapConfig.CapturePoint> capturePoints,
                        List<MapConfig.GlobalScript> globalScripts,
                        String startMusic, String victoryMusic, String defeatMusic,
                        String nearEndMusic, int nearEndThreshold,
                        int timeLimitMinutes, String timeUpRule) {
            this.id = id;
            this.name = name;
            this.mode = mode;
            this.description = description;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.initialScore = initialScore;
            this.hasThumbnail = hasThumbnail;
            this.factionPoolA = factionPoolA;
            this.factionPoolB = factionPoolB;
            this.capturePoints = capturePoints;
            this.globalScripts = globalScripts;
            this.startMusic = startMusic;
            this.victoryMusic = victoryMusic;
            this.defeatMusic = defeatMusic;
            this.nearEndMusic = nearEndMusic;
            this.nearEndThreshold = nearEndThreshold;
            this.timeLimitMinutes = timeLimitMinutes;
            this.timeUpRule = timeUpRule;
        }

        static MapEntry from(GameWorldManager.MapInfo info) {
            return new MapEntry(info.id, info.config.name, info.config.mode,
                    info.config.description, info.config.minPlayers, info.config.maxPlayers,
                    info.config.initialScore,
                    hasThumbnailFile(info.id),
                    info.config.factionPoolA != null ? info.config.factionPoolA : new ArrayList<>(),
                    info.config.factionPoolB != null ? info.config.factionPoolB : new ArrayList<>(),
                    info.config.capturePoints != null ? info.config.capturePoints : new ArrayList<>(),
                    info.config.globalScripts != null ? info.config.globalScripts : new ArrayList<>(),
                    info.config.startMusic != null ? info.config.startMusic : "",
                    info.config.victoryMusic != null ? info.config.victoryMusic : "",
                    info.config.defeatMusic != null ? info.config.defeatMusic : "",
                    info.config.nearEndMusic != null ? info.config.nearEndMusic : "",
                    info.config.nearEndThreshold,
                    info.config.timeLimitMinutes,
                    info.config.timeUpRule != null ? info.config.timeUpRule : "");
        }

        private static boolean hasThumbnailFile(String templateId) {
            Path thumbPath = Path.of("templates", templateId, "thumbnail.png");
            return Files.isRegularFile(thumbPath);
        }
    }
}

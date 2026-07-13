package com.battlelinesystem.network;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：倒计时结束，携带模式名和可选地图列表
 */
public class PacketTimeUp extends PacketBase {

    public String winningMode;
    public List<MapEntry> maps;

    public PacketTimeUp() {}

    public PacketTimeUp(FriendlyByteBuf buf) {
        this.winningMode = buf.readUtf();
        int count = buf.readVarInt();
        this.maps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            maps.add(new MapEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean()));
        }
    }

    public PacketTimeUp(String winningMode, List<MapEntry> maps) {
        this.winningMode = winningMode;
        this.maps = maps;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(winningMode);
        buf.writeVarInt(maps.size());
        for (MapEntry m : maps) {
            buf.writeUtf(m.id);
            buf.writeUtf(m.name);
            buf.writeUtf(m.description != null ? m.description : "");
            buf.writeVarInt(m.minPlayers);
            buf.writeVarInt(m.maxPlayers);
            buf.writeBoolean(m.hasThumbnail);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(9, this));
        return true;
    }

    public static class MapEntry {
        public final String id;
        public final String name;
        public final String description;
        public final int minPlayers;
        public final int maxPlayers;
        public final boolean hasThumbnail;

        public MapEntry(String id, String name, String description,
                        int minPlayers, int maxPlayers, boolean hasThumbnail) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.hasThumbnail = hasThumbnail;
        }

        public static MapEntry from(MapConfig config, String mapId) {
            return new MapEntry(mapId, config.name, config.description,
                    config.minPlayers, config.maxPlayers,
                    hasThumbnailFile(mapId));
        }

        private static boolean hasThumbnailFile(String templateId) {
            Path thumbPath = Path.of("templates", templateId, "thumbnail.png");
            return Files.isRegularFile(thumbPath);
        }
    }
}

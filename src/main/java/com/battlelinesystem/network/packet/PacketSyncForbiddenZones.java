package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步禁区列表
 */
public class PacketSyncForbiddenZones extends PacketBase {
    public final List<ZoneEntry> zones;

    public PacketSyncForbiddenZones() {
        this.zones = new ArrayList<>();
    }

    public PacketSyncForbiddenZones(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.zones = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            String forbiddenTeam = buf.readUtf();
            int vc = buf.readVarInt();
            List<int[]> boundary = new ArrayList<>(vc);
            for (int j = 0; j < vc; j++) {
                boundary.add(new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
            }
            zones.add(new ZoneEntry(name, forbiddenTeam, boundary));
        }
    }

    public PacketSyncForbiddenZones(List<ZoneEntry> zones) {
        this.zones = zones;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(zones.size());
        for (ZoneEntry z : zones) {
            buf.writeUtf(z.name);
            buf.writeUtf(z.forbiddenTeam);
            buf.writeVarInt(z.boundary.size());
            for (int[] v : z.boundary) {
                buf.writeVarInt(v[0]);
                buf.writeVarInt(v[1]);
                buf.writeVarInt(v[2]);
            }
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(13, this));
        return true;
    }

    public static class ZoneEntry {
        public final String name;
        public final String forbiddenTeam;
        public final List<int[]> boundary;

        public ZoneEntry(String name, String forbiddenTeam, List<int[]> boundary) {
            this.name = name;
            this.forbiddenTeam = forbiddenTeam;
            this.boundary = boundary;
        }
    }
}

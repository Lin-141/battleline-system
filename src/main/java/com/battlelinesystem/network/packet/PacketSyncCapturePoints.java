package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import com.battlelinesystem.network.NetworkManager;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketSyncCapturePoints extends PacketBase {
    public final List<MapConfig.CapturePoint> points;

    public PacketSyncCapturePoints() {
        this.points = new ArrayList<>();
    }

    public PacketSyncCapturePoints(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            String displayName = buf.readUtf();
            MapConfig.CapturePoint cp = new MapConfig.CapturePoint();
            cp.name = name;
            if (!displayName.isEmpty()) cp.displayName = displayName;
            int zoneCount = buf.readVarInt();
            for (int j = 0; j < zoneCount; j++) {
                int x = buf.readVarInt(), y = buf.readVarInt(), z = buf.readVarInt();
                int x2 = buf.readVarInt(), y2 = buf.readVarInt(), z2 = buf.readVarInt();
                cp.addZone(x, y, z, x2, y2, z2);
            }
            points.add(cp);
        }
    }

    public PacketSyncCapturePoints(List<MapConfig.CapturePoint> points) {
        this.points = points;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(points.size());
        for (MapConfig.CapturePoint cp : points) {
            buf.writeUtf(cp.name);
            buf.writeUtf(cp.displayName != null ? cp.displayName : "");
            int zc = cp.zoneCount();
            buf.writeVarInt(zc);
            if (cp.zones != null) {
                for (int[][] z : cp.zones) {
                    buf.writeVarInt(z[0][0]); buf.writeVarInt(z[0][1]); buf.writeVarInt(z[0][2]);
                    buf.writeVarInt(z[1][0]); buf.writeVarInt(z[1][1]); buf.writeVarInt(z[1][2]);
                }
            }
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(5, this));
        return true;
    }
}

package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketSyncBeaconEntities extends PacketBase {
    public final List<BeaconEntry> entries;

    public PacketSyncBeaconEntities() {
        this.entries = new ArrayList<>();
    }

    public PacketSyncBeaconEntities(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uid = buf.readUUID();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            String t = buf.readUtf();
            entries.add(new BeaconEntry(uid, x, y, z, t.isEmpty() ? null : t));
        }
    }

    public PacketSyncBeaconEntities(List<BeaconEntry> entries) {
        this.entries = entries;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (BeaconEntry e : entries) {
            buf.writeUUID(e.uuid);
            buf.writeDouble(e.x);
            buf.writeDouble(e.y);
            buf.writeDouble(e.z);
            buf.writeUtf(e.team != null ? e.team : "");
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(10, this));
        return true;
    }

    public static class BeaconEntry {
        public final UUID uuid;
        public final double x, y, z;
        public final String team; // "A"/"B"/null=双方
        public BeaconEntry(UUID uuid, double x, double y, double z, String team) {
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.team = team;
        }
    }
}

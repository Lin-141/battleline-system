package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashSet;
import java.util.Set;

public class PacketSyncSpawnPoints extends PacketBase {
    public final float[][] spawnA;
    public final float[][] spawnB;
    public final java.util.Set<String> vehicleSpawnTypesA;
    public final java.util.Set<String> vehicleSpawnTypesB;

    public PacketSyncSpawnPoints() {
        this.spawnA = new float[0][];
        this.spawnB = new float[0][];
        this.vehicleSpawnTypesA = new LinkedHashSet<>();
        this.vehicleSpawnTypesB = new LinkedHashSet<>();
    }

    public PacketSyncSpawnPoints(FriendlyByteBuf buf) {
        int aCount = buf.readVarInt();
        this.spawnA = new float[aCount][];
        for (int i = 0; i < aCount; i++) {
            spawnA[i] = new float[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat()};
        }
        int bCount = buf.readVarInt();
        this.spawnB = new float[bCount][];
        for (int i = 0; i < bCount; i++) {
            spawnB[i] = new float[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat()};
        }
        this.vehicleSpawnTypesA = readTypeSet(buf);
        this.vehicleSpawnTypesB = readTypeSet(buf);
    }

    public PacketSyncSpawnPoints(float[][] spawnA, float[][] spawnB,
                                  Set<String> vehicleSpawnTypesA, Set<String> vehicleSpawnTypesB) {
        this.spawnA = spawnA;
        this.spawnB = spawnB;
        this.vehicleSpawnTypesA = vehicleSpawnTypesA;
        this.vehicleSpawnTypesB = vehicleSpawnTypesB;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(spawnA != null ? spawnA.length : 0);
        if (spawnA != null) {
            for (float[] s : spawnA) {
                buf.writeVarInt((int) s[0]); buf.writeVarInt((int) s[1]); buf.writeVarInt((int) s[2]);
                float yaw = s.length >= 5 ? s[3] : 0f;
                float pitch = s.length >= 5 ? s[4] : 0f;
                buf.writeFloat(yaw); buf.writeFloat(pitch);
            }
        }
        buf.writeVarInt(spawnB != null ? spawnB.length : 0);
        if (spawnB != null) {
            for (float[] s : spawnB) {
                buf.writeVarInt((int) s[0]); buf.writeVarInt((int) s[1]); buf.writeVarInt((int) s[2]);
                float yaw = s.length >= 5 ? s[3] : 0f;
                float pitch = s.length >= 5 ? s[4] : 0f;
                buf.writeFloat(yaw); buf.writeFloat(pitch);
            }
        }
        writeTypeSet(buf, vehicleSpawnTypesA);
        writeTypeSet(buf, vehicleSpawnTypesB);
    }

    private static java.util.Set<String> readTypeSet(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        java.util.Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) set.add(buf.readUtf());
        return set;
    }

    private static void writeTypeSet(FriendlyByteBuf buf, java.util.Set<String> set) {
        buf.writeVarInt(set != null ? set.size() : 0);
        if (set != null) {
            for (String t : set) buf.writeUtf(t);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(7, this));
        return true;
    }
}

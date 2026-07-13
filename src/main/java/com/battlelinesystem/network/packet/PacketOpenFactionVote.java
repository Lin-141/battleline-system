package com.battlelinesystem.network.packet;

import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.ClassVariant;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.GunAttachmentConfig;
import com.battlelinesystem.faction.GunAttachmentOption;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketOpenFactionVote extends PacketBase {
    public final List<FactionConfig> factions;
    public final List<String> poolA;
    public final List<String> poolB;
    public int countdownSeconds;

    public PacketOpenFactionVote() {
        this.factions = new ArrayList<>();
        this.poolA = new ArrayList<>();
        this.poolB = new ArrayList<>();
    }

    public PacketOpenFactionVote(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.factions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            FactionConfig f = new FactionConfig(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
            f.hasThumbnail = buf.readBoolean();
            int classCount = buf.readVarInt();
            f.classes = new ArrayList<>(classCount);
            for (int j = 0; j < classCount; j++) {
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
                f.classes.add(c);
            }
            factions.add(f);
        }
        int paSize = buf.readVarInt();
        this.poolA = new ArrayList<>(paSize);
        for (int i = 0; i < paSize; i++) poolA.add(buf.readUtf());
        int pbSize = buf.readVarInt();
        this.poolB = new ArrayList<>(pbSize);
        for (int i = 0; i < pbSize; i++) poolB.add(buf.readUtf());
        this.countdownSeconds = buf.readVarInt();
    }

    public PacketOpenFactionVote(List<FactionConfig> factions, List<String> poolA, List<String> poolB) {
        this(factions, poolA, poolB, -1);
    }

    public PacketOpenFactionVote(List<FactionConfig> factions, List<String> poolA, List<String> poolB,
                                 int countdownSeconds) {
        this.factions = factions;
        this.poolA = poolA;
        this.poolB = poolB;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(factions.size());
        for (FactionConfig f : factions) {
            buf.writeUtf(f.id);
            buf.writeUtf(f.name);
            buf.writeUtf(f.displayColor);
            buf.writeUtf(f.description != null ? f.description : "");
            buf.writeBoolean(f.hasThumbnail);
            List<ClassConfig> classes = f.classes;
            buf.writeVarInt(classes != null ? classes.size() : 0);
            if (classes != null) {
                for (ClassConfig c : classes) {
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
                }
            }
        }
        buf.writeVarInt(poolA.size());
        for (String id : poolA) buf.writeUtf(id);
        buf.writeVarInt(poolB.size());
        for (String id : poolB) buf.writeUtf(id);
        buf.writeVarInt(countdownSeconds);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(3, this));
        return true;
    }
}

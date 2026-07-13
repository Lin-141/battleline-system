package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;

public class PacketFactionAction extends PacketBase {
    public enum Action { ADD, REMOVE, UPDATE }

    public final Action action;
    public final FactionConfig faction;
    public final String factionId;

    public PacketFactionAction() {
        this.action = null;
        this.faction = null;
        this.factionId = null;
    }

    public PacketFactionAction(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        if (action == Action.ADD || action == Action.UPDATE) {
            FactionConfig fc = new FactionConfig(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
            int classCount = buf.readVarInt();
            fc.classes = new ArrayList<>(classCount);
            for (int i = 0; i < classCount; i++) {
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
                fc.classes.add(c);
            }
            PacketFactionList.readVehicles(buf, fc);
            int ceiCnt = buf.readVarInt();
            if (ceiCnt > 0) {
                fc.commanderExtraItems = new ArrayList<>(ceiCnt);
                for (int j = 0; j < ceiCnt; j++) fc.commanderExtraItems.add(buf.readUtf());
            }
            fc.looseSpawn = buf.readBoolean();
            String cs = buf.readUtf(); if (!cs.isEmpty()) fc.captureSound = cs;
            String ls = buf.readUtf(); if (!ls.isEmpty()) fc.loseSound = ls;
            this.faction = fc;
            this.factionId = null;
        } else {
            this.factionId = buf.readUtf();
            this.faction = null;
        }
    }

    private PacketFactionAction(Action action, FactionConfig faction, String factionId) {
        this.action = action;
        this.faction = faction;
        this.factionId = factionId;
    }

    public static PacketFactionAction add(FactionConfig faction) {
        return new PacketFactionAction(Action.ADD, faction, null);
    }

    public static PacketFactionAction remove(String id) {
        return new PacketFactionAction(Action.REMOVE, null, id);
    }

    public static PacketFactionAction update(FactionConfig faction) {
        return new PacketFactionAction(Action.UPDATE, faction, null);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        if (action == Action.ADD || action == Action.UPDATE) {
            buf.writeUtf(faction.id);
            buf.writeUtf(faction.name);
            buf.writeUtf(faction.displayColor);
            buf.writeUtf(faction.description != null ? faction.description : "");
            java.util.List<ClassConfig> classes = faction.classes;
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
            PacketFactionList.writeVehicles(buf, faction);
            java.util.List<String> cei = faction.commanderExtraItems;
            buf.writeVarInt(cei != null ? cei.size() : 0);
            if (cei != null) {
                for (String s : cei) buf.writeUtf(s != null ? s : "");
            }
            buf.writeBoolean(faction.looseSpawn);
            buf.writeUtf(faction.captureSound != null ? faction.captureSound : "");
            buf.writeUtf(faction.loseSound != null ? faction.loseSound : "");
        } else {
            buf.writeUtf(factionId);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            try {
                if (!context.getSender().hasPermissions(2)) return;
                FactionManager mgr = FactionManager.getInstance();
                boolean ok;
                if (action == Action.ADD) {
                    ok = mgr.addFaction(faction);
                } else if (action == Action.UPDATE) {
                    ok = mgr.updateFactionFull(faction);
                } else {
                    ok = mgr.removeFaction(factionId);
                }
                if (ok) {
                    AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> context.getSender()),
                            new PacketFactionList(mgr.getAllFactions()));
                }
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketFactionAction error", e);
            }
        });
        return true;
    }
}

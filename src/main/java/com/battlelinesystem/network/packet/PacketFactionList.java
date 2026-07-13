package com.battlelinesystem.network.packet;

import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.ClassVariant;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.GunAttachmentConfig;
import com.battlelinesystem.faction.GunAttachmentOption;
import com.battlelinesystem.faction.VehicleConfig;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketFactionList extends PacketBase {
    public final List<FactionConfig> factions;

    public PacketFactionList() {
        this.factions = new ArrayList<>();
    }

    public PacketFactionList(FriendlyByteBuf buf) {
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
                readExtraItems(buf, c);
                readVariants(buf, c);
                readHiddenItems(buf, c);
                readGunPools(buf, c);
                f.classes.add(c);
            }
            readVehicles(buf, f);
            int ceiCnt = buf.readVarInt();
            if (ceiCnt > 0) {
                f.commanderExtraItems = new ArrayList<>(ceiCnt);
                for (int j = 0; j < ceiCnt; j++) f.commanderExtraItems.add(buf.readUtf());
            }
            f.looseSpawn = buf.readBoolean();
            String cs = buf.readUtf(); if (!cs.isEmpty()) f.captureSound = cs;
            String ls = buf.readUtf(); if (!ls.isEmpty()) f.loseSound = ls;
            factions.add(f);
        }
    }

    public PacketFactionList(List<FactionConfig> factions) {
        this.factions = factions;
    }

    public List<FactionConfig> getFactions() { return factions; }

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
                    writeExtraItems(buf, c);
                    writeVariants(buf, c);
                    writeHiddenItems(buf, c);
                    writeGunPools(buf, c);
                }
            }
            writeVehicles(buf, f);
            List<String> cei = f.commanderExtraItems;
            buf.writeVarInt(cei != null ? cei.size() : 0);
            if (cei != null) {
                for (String s : cei) buf.writeUtf(s != null ? s : "");
            }
            buf.writeBoolean(f.looseSpawn);
            buf.writeUtf(f.captureSound != null ? f.captureSound : "");
            buf.writeUtf(f.loseSound != null ? f.loseSound : "");
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(1, this));
        return true;
    }

    // ===== helper static methods =====

    static void writeExtraItems(FriendlyByteBuf buf, ClassConfig c) {
        List<String> extra = c.extraItems;
        buf.writeVarInt(extra != null ? extra.size() : 0);
        if (extra != null) {
            for (String s : extra) buf.writeUtf(s);
        }
    }

    static void readExtraItems(FriendlyByteBuf buf, ClassConfig c) {
        int count = buf.readVarInt();
        if (count > 0) {
            c.extraItems = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                c.extraItems.add(buf.readUtf());
            }
        }
    }

    static void writeVariants(FriendlyByteBuf buf, ClassConfig c) {
        List<ClassVariant> vars = c.variants;
        buf.writeVarInt(vars != null ? vars.size() : 0);
        if (vars != null) {
            for (ClassVariant v : vars) {
                buf.writeUtf(v.id);
                buf.writeUtf(v.name);
                buf.writeUtf(v.helmet != null ? v.helmet : "");
                buf.writeUtf(v.chestplate != null ? v.chestplate : "");
                buf.writeUtf(v.leggings != null ? v.leggings : "");
                buf.writeUtf(v.boots != null ? v.boots : "");
                buf.writeUtf(v.offHand != null ? v.offHand : "");
                List<String> ext = v.extraItems;
                buf.writeVarInt(ext != null ? ext.size() : 0);
                if (ext != null) { for (String s : ext) buf.writeUtf(s); }
                buf.writeUtf(v.unlockCondition != null ? v.unlockCondition : "");
            }
        }
    }

    static void readVariants(FriendlyByteBuf buf, ClassConfig c) {
        int count = buf.readVarInt();
        if (count > 0) {
            c.variants = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ClassVariant v = new ClassVariant(buf.readUtf(), buf.readUtf());
                v.helmet = buf.readUtf(); if (v.helmet.isEmpty()) v.helmet = null;
                v.chestplate = buf.readUtf(); if (v.chestplate.isEmpty()) v.chestplate = null;
                v.leggings = buf.readUtf(); if (v.leggings.isEmpty()) v.leggings = null;
                v.boots = buf.readUtf(); if (v.boots.isEmpty()) v.boots = null;
                v.offHand = buf.readUtf(); if (v.offHand.isEmpty()) v.offHand = null;
                int extCount = buf.readVarInt();
                if (extCount > 0) {
                    v.extraItems = new ArrayList<>(extCount);
                    for (int j = 0; j < extCount; j++) v.extraItems.add(buf.readUtf());
                }
                String uc = buf.readUtf();
                v.unlockCondition = uc.isEmpty() ? null : uc;
                c.variants.add(v);
            }
        }
    }

    static void writeHiddenItems(FriendlyByteBuf buf, ClassConfig c) {
        List<String> h = c.hiddenDisplayItems;
        buf.writeVarInt(h != null ? h.size() : 0);
        if (h != null) { for (String s : h) buf.writeUtf(s); }
        buf.writeBoolean(c.hideArmorIcons);
        buf.writeVarInt(c.maxPlayers);
        buf.writeVarInt(c.deathCost);
    }

    static void readHiddenItems(FriendlyByteBuf buf, ClassConfig c) {
        int count = buf.readVarInt();
        if (count > 0) {
            c.hiddenDisplayItems = new ArrayList<>(count);
            for (int i = 0; i < count; i++) c.hiddenDisplayItems.add(buf.readUtf());
        }
        c.hideArmorIcons = buf.readBoolean();
        c.maxPlayers = buf.readVarInt();
        c.deathCost = buf.readVarInt();
    }

    static void writeGunPools(FriendlyByteBuf buf, ClassConfig c) {
        List<GunAttachmentConfig> pools = c.gunAttachmentPools;
        buf.writeVarInt(pools != null ? pools.size() : 0);
        if (pools != null) {
            for (GunAttachmentConfig g : pools) {
                buf.writeUtf(g.gunId != null ? g.gunId : "");
                writeOptList(buf, g.scopes);
                writeOptList(buf, g.muzzles);
                writeOptList(buf, g.grips);
                writeOptList(buf, g.stocks);
                writeOptList(buf, g.lasers);
            }
        }
    }

    static void readGunPools(FriendlyByteBuf buf, ClassConfig c) {
        int count = buf.readVarInt();
        if (count > 0) {
            c.gunAttachmentPools = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                GunAttachmentConfig g = new GunAttachmentConfig(buf.readUtf());
                g.scopes = readOptList(buf);
                g.muzzles = readOptList(buf);
                g.grips = readOptList(buf);
                g.stocks = readOptList(buf);
                g.lasers = readOptList(buf);
                c.gunAttachmentPools.add(g);
            }
        }
    }

    static void writeOptList(FriendlyByteBuf buf, List<GunAttachmentOption> opts) {
        buf.writeVarInt(opts != null ? opts.size() : 0);
        if (opts != null) {
            for (GunAttachmentOption o : opts) {
                buf.writeUtf(o.attachmentId != null ? o.attachmentId : "");
                buf.writeUtf(o.displayName != null ? o.displayName : "");
                buf.writeUtf(o.unlockCondition != null ? o.unlockCondition : "");
            }
        }
    }

    static List<GunAttachmentOption> readOptList(FriendlyByteBuf buf) {
        int cnt = buf.readVarInt();
        if (cnt <= 0) return null;
        List<GunAttachmentOption> list = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) {
            GunAttachmentOption o = new GunAttachmentOption(buf.readUtf(), buf.readUtf());
            String uc = buf.readUtf();
            o.unlockCondition = uc.isEmpty() ? null : uc;
            list.add(o);
        }
        return list;
    }

    static void writeVehicles(FriendlyByteBuf buf, FactionConfig f) {
        List<VehicleConfig> vehs = f.vehicles;
        buf.writeVarInt(vehs != null ? vehs.size() : 0);
        if (vehs != null) {
            for (VehicleConfig v : vehs) {
                buf.writeUtf(v.id != null ? v.id : "");
                buf.writeUtf(v.name != null ? v.name : "");
                buf.writeUtf(v.itemNbt != null ? v.itemNbt : "");
                buf.writeUtf(v.type != null ? v.type : "tank");
                buf.writeUtf(v.description != null ? v.description : "");
                buf.writeVarInt(v.maxCount);
                buf.writeVarInt(v.cooldownSeconds);
                if (v.deployScripts != null) {
                    buf.writeVarInt(v.deployScripts.size());
                    for (String s : v.deployScripts) buf.writeUtf(s);
                } else {
                    buf.writeVarInt(0);
                }
                buf.writeUtf(v.deploySound != null ? v.deploySound : "");
                buf.writeUtf(v.deploySoundTarget != null ? v.deploySoundTarget : "all");
            }
        }
    }

    static void readVehicles(FriendlyByteBuf buf, FactionConfig f) {
        int cnt = buf.readVarInt();
        if (cnt > 0) {
            f.vehicles = new ArrayList<>(cnt);
            for (int i = 0; i < cnt; i++) {
                VehicleConfig v = new VehicleConfig(buf.readUtf(), buf.readUtf(), buf.readUtf());
                v.type = buf.readUtf();
                String d = buf.readUtf();
                v.description = d.isEmpty() ? "" : d;
                v.maxCount = buf.readVarInt();
                v.cooldownSeconds = buf.readVarInt();
                int dsc = buf.readVarInt();
                if (dsc > 0) {
                    v.deployScripts = new ArrayList<>(dsc);
                    for (int j = 0; j < dsc; j++) v.deployScripts.add(buf.readUtf());
                }
                v.deploySound = buf.readUtf();
                v.deploySoundTarget = buf.readUtf();
                if (v.deploySound.isEmpty()) v.deploySound = "";
                if (v.deploySoundTarget.isEmpty()) v.deploySoundTarget = "all";
                f.vehicles.add(v);
            }
        }
    }
}

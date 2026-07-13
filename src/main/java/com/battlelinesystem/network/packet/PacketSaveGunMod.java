package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.game.CapturePointManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;

/**
 * 客户端 → 服务端：保存玩家对武器的改装（配件、涂装等），
 * 服务端存储后会在玩家下次部署时应用改装。
 */
public class PacketSaveGunMod extends PacketBase {

    public String factionId;
    public String classId;
    public String oldNbt;
    public String newNbt;

    public PacketSaveGunMod() {}

    public PacketSaveGunMod(FriendlyByteBuf buf) {
        this.factionId = buf.readUtf();
        this.classId = buf.readUtf();
        this.oldNbt = buf.readUtf();
        this.newNbt = buf.readUtf();
    }

    public PacketSaveGunMod(String factionId, String classId, String oldNbt, String newNbt) {
        this.factionId = factionId;
        this.classId = classId;
        this.oldNbt = oldNbt;
        this.newNbt = newNbt;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionId);
        buf.writeUtf(classId);
        buf.writeUtf(oldNbt);
        buf.writeUtf(newNbt);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) return false;
        context.enqueueWork(() -> {
            if (factionId == null || factionId.isEmpty() || oldNbt == null || newNbt == null) return;
            GunModStorage.save(player.getUUID(), factionId, classId, oldNbt, newNbt);
            BattleLineSystem.LOGGER.info("[GunMod] {} 保存改装 faction={} class={}" ,
                    player.getName().getString(), factionId, classId != null ? classId : "?");
        });
        return true;
    }

    /** 服务端存储：玩家 → (原武器NBT → 改装后NBT) */
    public static class GunModStorage {
        private static final Map<UUID, Map<String, String>> MODS = new HashMap<>();

        public static void save(UUID playerUuid, String factionId, String classId, String oldNbt, String newNbt) {
            Map<String, String> playerMods = MODS.computeIfAbsent(playerUuid, k -> new HashMap<>());
            playerMods.put(oldNbt, newNbt);
        }

        /** 应用已保存改装：将 ClassConfig 中的 NBT 字符串替换为玩家保存的版本 */
        public static String apply(UUID playerUuid, String originalNbt) {
            if (originalNbt == null) return null;
            Map<String, String> playerMods = MODS.get(playerUuid);
            if (playerMods == null) return originalNbt;
            String saved = playerMods.get(originalNbt);
            return saved != null ? saved : originalNbt;
        }

        /** 玩家离开时清除 */
        public static void remove(UUID playerUuid) {
            MODS.remove(playerUuid);
        }

        public static void clear() {
            MODS.clear();
        }
    }
}

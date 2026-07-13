package com.battlelinesystem.items;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.game.CapturePointManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 信标棒 — 右键实体切换 spawn_beacon NBT
 * 潜行+右键切换 spawn_beacon_team 队伍（null→A→B→null）
 */
public class BeaconWandItem extends Item {

    public BeaconWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            String info = buildNbtInfo(target);
            player.displayClientMessage(Component.literal("§e信标: " + info), true);
            return InteractionResult.SUCCESS;
        }

        var data = target.getPersistentData();
        if (player.isShiftKeyDown()) {
            // 潜行：切换队伍
            String currentTeam = data.contains("spawn_beacon_team")
                    ? data.getString("spawn_beacon_team") : null;
            if (currentTeam == null) {
                data.putString("spawn_beacon_team", "A");
                player.sendSystemMessage(Component.literal("§a信标队伍设为 A队"));
            } else if ("A".equals(currentTeam)) {
                data.putString("spawn_beacon_team", "B");
                player.sendSystemMessage(Component.literal("§a信标队伍设为 B队"));
            } else {
                data.remove("spawn_beacon_team");
                player.sendSystemMessage(Component.literal("§a信标队伍已移除（双方可见）"));
            }
            // 确保 spawn_beacon=true
            data.putBoolean("spawn_beacon", true);
            CapturePointManager.getInstance().registerBeaconUUID(
                    player.level().dimension(), target.getUUID());
        } else {
            // 普通右键：开关信标
            if (data.getBoolean("spawn_beacon")) {
                data.remove("spawn_beacon");
                data.remove("spawn_beacon_team");
                player.sendSystemMessage(Component.literal("§c信标已移除"));
                CapturePointManager.getInstance().unregisterBeaconUUID(
                        player.level().dimension(), target.getUUID());
            } else {
                data.putBoolean("spawn_beacon", true);
                player.sendSystemMessage(Component.literal("§a信标已添加（双方可见）"));
                CapturePointManager.getInstance().registerBeaconUUID(
                        player.level().dimension(), target.getUUID());
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static String buildNbtInfo(LivingEntity target) {
        var data = target.getPersistentData();
        boolean hasBeacon = data.getBoolean("spawn_beacon");
        String team = data.contains("spawn_beacon_team")
                ? data.getString("spawn_beacon_team") : "双方";
        return hasBeacon ? "ON | 队伍=" + team : "OFF";
    }
}

package com.battlelinesystem.items;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 选区棒 — 左键设A点，右键设B点
 */
public class SelectionWandItem extends Item {

    private static final Map<UUID, BlockPos> POS1 = new HashMap<>();
    private static final Map<UUID, BlockPos> POS2 = new HashMap<>();

    // 客户端镜像（用于选区线框渲染）
    private static final Map<UUID, BlockPos> CLIENT_POS1 = new HashMap<>();
    private static final Map<UUID, BlockPos> CLIENT_POS2 = new HashMap<>();

    public SelectionWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        BlockPos clicked = ctx.getClickedPos();
        UUID uuid = player.getUUID();

        if (level.isClientSide()) {
            // 客户端镜像
            CLIENT_POS2.put(uuid, clicked);
            // 切换A/B颜色闪烁提示
            BlockPos p1 = CLIENT_POS1.get(uuid);
            if (p1 != null) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§e选区: " + p1.toShortString() + " ~ " + clicked.toShortString()), true);
            } else {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§eB点: " + clicked.toShortString() + " (请先用左键选A点)"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // 服务端
        BlockPos p2 = POS2.get(uuid);
        if (p2 != null && p2.equals(clicked)) {
            POS2.remove(uuid);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eB点已取消"), false);
        } else {
            POS2.put(uuid, clicked);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§aB点: " + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ()), false);
        }
        return InteractionResult.SUCCESS;
    }

    public static void setPos1(Player player, BlockPos pos) {
        POS1.put(player.getUUID(), pos);
        // 客户端镜像
        CLIENT_POS1.put(player.getUUID(), pos);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§aA点: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
    }

    public static BlockPos getPos1(Player player) {
        return POS1.get(player.getUUID());
    }

    public static BlockPos getPos2(Player player) {
        return POS2.get(player.getUUID());
    }

    public static void clearSelection(Player player) {
        UUID uuid = player.getUUID();
        POS1.remove(uuid);
        POS2.remove(uuid);
        CLIENT_POS1.remove(uuid);
        CLIENT_POS2.remove(uuid);
    }

    /** 客户端获取本地玩家的选区坐标，用于渲染选区线框 */
    public static BlockPos getClientPos1(UUID uuid) { return CLIENT_POS1.get(uuid); }
    public static BlockPos getClientPos2(UUID uuid) { return CLIENT_POS2.get(uuid); }

    /** 客户端设置A点（供客户端事件调用） */
    public static void setClientPos1(UUID uuid, BlockPos pos) {
        CLIENT_POS1.put(uuid, pos);
    }
}

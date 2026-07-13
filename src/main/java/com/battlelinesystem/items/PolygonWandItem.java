package com.battlelinesystem.items;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 多边形选区棒 — 左键添加顶点，右键撤销上一个，潜行+右键清除
 * 使用调试棒材质。
 */
public class PolygonWandItem extends Item {

    /** 服务端: 玩家UUID → 多边形顶点列表 */
    private static final Map<UUID, List<BlockPos>> POINTS = new HashMap<>();

    /** 客户端镜像: 用于渲染 */
    private static final Map<UUID, List<BlockPos>> CLIENT_POINTS = new HashMap<>();

    public PolygonWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    // ---- 右键（UseOnContext） ----
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        UUID uuid = player.getUUID();
        boolean shifting = player.isShiftKeyDown();

        if (level.isClientSide()) {
            List<BlockPos> points = CLIENT_POINTS.get(uuid);
            if (shifting) {
                // 潜行+右键 = 清除全部
                if (points != null) points.clear();
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c多边形选区已清除"), true);
            } else {
                // 右键 = 撤销最后一个
                if (points != null && !points.isEmpty()) {
                    points.remove(points.size() - 1);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "§e已撤销，当前 " + points.size() + " 个顶点"), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 服务端
        List<BlockPos> sp = POINTS.get(uuid);
        if (shifting) {
            if (sp != null) sp.clear();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c多边形选区已清除"), false);
        } else {
            if (sp != null && !sp.isEmpty()) {
                BlockPos removed = sp.remove(sp.size() - 1);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§e已撤销: " + removed.toShortString()), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ---- 左键（外部调用） ----

    /** 服务端：左键添加顶点 */
    public static void serverAddPoint(Player player, BlockPos pos) {
        UUID uuid = player.getUUID();
        List<BlockPos> points = POINTS.computeIfAbsent(uuid, k -> new ArrayList<>());
        points.add(pos);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§a顶点" + points.size() + ": " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                false);
    }

    /** 客户端：左键添加顶点 */
    public static void clientAddPoint(UUID uuid, BlockPos pos) {
        List<BlockPos> points = CLIENT_POINTS.computeIfAbsent(uuid, k -> new ArrayList<>());
        points.add(pos);
    }

    // ---- Utils ----

    public static List<BlockPos> getPoints(UUID uuid) {
        List<BlockPos> p = POINTS.get(uuid);
        return p != null ? p : new ArrayList<>();
    }

    public static void clear(UUID uuid) {
        POINTS.remove(uuid);
        CLIENT_POINTS.remove(uuid);
    }

    public static List<BlockPos> getClientPoints(UUID uuid) {
        List<BlockPos> p = CLIENT_POINTS.get(uuid);
        return p != null ? p : new ArrayList<>();
    }
}

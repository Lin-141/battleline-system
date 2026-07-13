package com.battlelinesystem.command;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.AllPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /bls loosetest spawn  — 在脚底生成测试队友（用于单人验证宽松重生点）
 * /bls loosetest clear  — 清除所有测试队友
 */
public class LooseSpawnTestCommand {

    /** 记录生成的测试实体 UUID，用于清除 */
    private static final Set<UUID> TEST_UUIDS = new HashSet<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bls")
                .then(Commands.literal("loosetest")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawnTestTeammate(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearTestTeammates(ctx.getSource())))));
    }

    private static int spawnTestTeammate(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();

        // 获取玩家当前队伍
        String team = com.battlelinesystem.game.CapturePointManager.getInstance()
                .getPlayerTeam(player.getUUID());
        if (team == null) {
            source.sendFailure(Component.literal("§c你还没有选择队伍，请先选队！"));
            return 0;
        }

        // 创建伪装队友 ArmorStand
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, world);
        stand.setPos(player.getX() + 2, player.getY(), player.getZ());
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setCustomName(Component.literal("测试队友[" + team + "队]"));
        stand.setCustomNameVisible(true);

        // 不做 addTag（Tags 在 ArmorStand 上不一定同步到客户端）
        // 改用 CustomName 前缀识别（客户端通过解析名字判断队伍）

        world.addFreshEntity(stand);
        TEST_UUIDS.add(stand.getUUID());

        // 注册到 CapturePointManager
        com.battlelinesystem.game.CapturePointManager.getInstance()
                .setPlayerTeam(stand.getUUID(), team);

        // 发送网络包通知客户端（解决不同维度 Level 无法扫描到实体的问题）
        com.battlelinesystem.network.packet.PacketLooseSpawnTest pkt = new com.battlelinesystem.network.packet.PacketLooseSpawnTest(
                stand.getX(), stand.getY(), stand.getZ(), stand.getUUID(), team);
        AllPackets.getChannel().send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), pkt);

        source.sendSuccess(() -> Component.literal(
                "§a已生成测试队友 (UUID: " + stand.getUUID().toString().substring(0, 8) + "... 队伍: " + team + ")"), true);
        BattleLineSystem.LOGGER.info("[宽松测试] 生成测试队友 UUID={} team={}", stand.getUUID(), team);

        return 1;
    }

    private static int clearTestTeammates(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        int removed = 0;

        // 清除所有标记的测试实体
        for (net.minecraft.world.entity.Entity e : world.getAllEntities()) {
            if (e.getCustomName() != null && e.getCustomName().getString().startsWith("测试队友")) {
                TEST_UUIDS.remove(e.getUUID());
                com.battlelinesystem.game.CapturePointManager.getInstance().removePlayer(e.getUUID());
                e.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                removed++;
            }
        }
        TEST_UUIDS.clear();

        // 通知客户端清除测试队友
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            com.battlelinesystem.network.packet.PacketLooseSpawnTest pkt = new com.battlelinesystem.network.packet.PacketLooseSpawnTest();
            AllPackets.getChannel().send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), pkt);
        }

        final int count = removed;
        source.sendSuccess(() -> Component.literal("§a已清除 " + count + " 个测试队友"), true);
        BattleLineSystem.LOGGER.info("[宽松测试] 清除 {} 个测试队友", count);

        return removed;
    }
}

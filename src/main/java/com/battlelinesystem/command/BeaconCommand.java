package com.battlelinesystem.command;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.game.CapturePointManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * /bls beacon test [team] [name] — 在脚底生成测试信标实体
 * /bls beacon scan — 扫描当前世界所有信标实体
 */
public class BeaconCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bls")
                .then(Commands.literal("beacon")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("test")
                                .executes(ctx -> testBeacon(ctx.getSource(), null, null))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> testBeacon(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), null))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> testBeacon(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "team"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(Commands.literal("scan")
                                .executes(ctx -> scanBeacons(ctx.getSource())))));
    }

    private static int testBeacon(CommandSourceStack source, String team, String customName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = player.serverLevel();
        BlockPos pos = player.blockPosition();

        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, world);
        stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.getPersistentData().putBoolean("spawn_beacon", true);
        if (team != null && (team.equalsIgnoreCase("A") || team.equalsIgnoreCase("B"))) {
            stand.getPersistentData().putString("spawn_beacon_team", team.toUpperCase());
        }
        if (customName != null && !customName.isEmpty()) {
            stand.setCustomName(Component.literal(customName));
            stand.setCustomNameVisible(true);
        } else {
            stand.setCustomName(Component.literal("★ Beacon " + (team != null ? team : "N")));
            stand.setCustomNameVisible(true);
        }
        world.addFreshEntity(stand);
        CapturePointManager.getInstance().registerBeaconUUID(world.dimension(), stand.getUUID());

        String uuidStr = stand.getUUID().toString();
        String posStr = pos.toShortString();
        String worldStr = world.dimension().location().toString();
        String teamStr = team != null ? team : "双方可见";

        source.sendSuccess(() -> Component.literal("§a信标实体已创建！")
                .append("\n  UUID: " + uuidStr)
                .append("\n  坐标: " + posStr)
                .append("\n  世界: " + worldStr)
                .append("\n  队伍: " + teamStr)
                .append("\n  §e请等待1秒后查看部署界面"), false);

        BattleLineSystem.LOGGER.info("[信标命令] 创建测试信标 uuid={} pos={} world={} team={}",
                uuidStr, posStr, worldStr, teamStr);
        return 1;
    }

    private static int scanBeacons(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        final int[] total = {0};
        final int[] found = {0};
        final StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.entity.Entity e : world.getAllEntities()) {
            total[0]++;
            if (e.getPersistentData().getBoolean("spawn_beacon")) {
                found[0]++;
                String t = e.getPersistentData().contains("spawn_beacon_team")
                        ? e.getPersistentData().getString("spawn_beacon_team") : "null";
                sb.append(String.format("\n  [%s] %s | %s",
                        t, e.blockPosition().toShortString(), e.getUUID()));
            }
        }
        String worldStr = world.dimension().location().toString();
        String detailStr = sb.toString();
        source.sendSuccess(() -> Component.literal(
                String.format("§e信标扫描: world=%s total=%d found=%d%s",
                        worldStr, total[0], found[0],
                        found[0] > 0 ? detailStr : "")), false);
        return found[0];
    }
}

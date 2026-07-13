package com.battlelinesystem.network;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.packet.PacketOpenScreen;
import com.battlelinesystem.network.packet.PacketSyncCapturePoints;
import com.battlelinesystem.network.packet.PacketSyncForbiddenZones;
import com.battlelinesystem.network.packet.PacketSyncSpawnPoints;
import com.battlelinesystem.world.GameWorldManager;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class PacketSelectMap extends PacketBase {

    private String mapId;

    public PacketSelectMap() {}

    public PacketSelectMap(FriendlyByteBuf buf) {
        this.mapId = buf.readUtf();
    }

    public PacketSelectMap(String mapId) {
        this.mapId = mapId;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                BattleLineSystem.LOGGER.info("收到地图选择: player={}, mapId={}",
                        player != null ? player.getGameProfile().getName() : "null", mapId);

                if (player == null) {
                    BattleLineSystem.LOGGER.error("PacketSelectMap: sender is null");
                    return;
                }

                boolean isSinglePlayer = !player.getServer().isDedicatedServer();
                if (!isSinglePlayer && !player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("§c你没有权限选择地图"));
                    return;
                }

                boolean ok = startGameWithMap(player.getServer(), mapId);
                if (ok && player.getServer() != null) {
                    for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                        p.sendSystemMessage(Component.literal("§a管理员 " + player.getGameProfile().getName() + " 选择了地图: " + mapId));
                    }
                }
            } catch (Throwable t) {
                BattleLineSystem.LOGGER.error("PacketSelectMap handle 异常", t);
                com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);
            }
        });
        return true;
    }

    /** 跳过投票直接以指定地图开始游戏。成功返回 true。 */
    public static boolean startGameWithMap(net.minecraft.server.MinecraftServer server, String mapId) {
        if (server == null) {
            BattleLineSystem.LOGGER.error("startGameWithMap: server is null");
            return false;
        }

        MapConfig config = GameWorldManager.getMapsForMode(server, "")
                .stream()
                .filter(m -> m.id.equals(mapId))
                .findFirst()
                .map(m -> m.config)
                .orElse(null);

        if (config == null) {
            BattleLineSystem.LOGGER.error("找不到地图模板: {}", mapId);
            return false;
        }

        com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(config);
        BattleLineSystem.LOGGER.info("[音乐调试] setActiveMapConfig map={} startMusic='{}' victoryMusic='{}'",
                mapId, config.startMusic, config.victoryMusic);

        ResourceKey<Level> worldKey = GameWorldManager.generateWorldKey();
        BattleLineSystem.LOGGER.info("开始创建游戏世界: {} → {}", mapId, worldKey.location());

        ServerLevel gameWorld = GameWorldManager.createAndLoadWorld(server, mapId, worldKey, config);

        if (gameWorld == null) {
            BattleLineSystem.LOGGER.error("游戏世界创建失败");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("§c创建游戏世界失败"));
            }
            com.battlelinesystem.faction.FactionManager.getInstance().setActiveMapConfig(null);
            return false;
        }

        BattleLineSystem.LOGGER.info("游戏世界创建成功，传送玩家并打开阵营选择");

        com.battlelinesystem.game.CapturePointManager.getInstance().init(worldKey, config);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.getInventory().clearContent();
            p.inventoryMenu.broadcastChanges();
            p.setGameMode(GameType.SPECTATOR);
            try {
                p.teleportTo(gameWorld,
                        config.getTeamASpawn().getX() + 0.5,
                        319,
                        config.getTeamASpawn().getZ() + 0.5,
                        180, 90);
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("传送玩家到游戏世界失败: {}", e.toString());
            }

            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                    new PacketSyncCapturePoints(
                            com.battlelinesystem.game.CapturePointManager.getInstance()
                                    .getRevealedPointsForWorld(worldKey)));
            java.util.List<com.battlelinesystem.network.packet.PacketSyncForbiddenZones.ZoneEntry> forbiddenEntries = new java.util.ArrayList<>();
            for (MapConfig.ForbiddenZone fz : config.forbiddenZones) {
                forbiddenEntries.add(new com.battlelinesystem.network.packet.PacketSyncForbiddenZones.ZoneEntry(
                        fz.name, fz.forbiddenTeam, fz.boundary));
            }
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                    new PacketSyncForbiddenZones(forbiddenEntries));
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                    new PacketSyncSpawnPoints(
                            config.spawnPoints.team_a, config.spawnPoints.team_b,
                            buildTeamVehicleTypes(config, true),
                            buildTeamVehicleTypes(config, false)));
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> p),
                    new PacketOpenScreen(1, false, new int[]{0,0,0,0},
                            com.battlelinesystem.game.SelectionCountdownManager.TEAM_COUNTDOWN));

            com.battlelinesystem.game.CapturePointManager.playSoundToPlayer(p, "battlelinesystem:waiting_01");
        }

        // 选完地图，玩家传送完成后播放开局音乐
        if (config.startMusic != null && !config.startMusic.isEmpty()) {
            BattleLineSystem.LOGGER.info("[音乐] 开局: {}", config.startMusic);
            com.battlelinesystem.game.CapturePointManager.playSoundToWorld(gameWorld, config.startMusic);
        }

        com.battlelinesystem.game.SelectionCountdownManager.getInstance().startTeamCountdown();
        return true;
    }

    public static java.util.Set<String> buildTeamVehicleTypes(MapConfig config, boolean teamA) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (var e : config.vehicleSpawnPoints.types.entrySet()) {
            MapConfig.TypeSpawns ts = e.getValue();
            if (teamA ? ts.team_a.length > 0 : ts.team_b.length > 0)
                set.add(e.getKey());
        }
        return set;
    }
}

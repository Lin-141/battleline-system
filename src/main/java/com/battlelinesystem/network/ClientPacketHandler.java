package com.battlelinesystem.network;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.client.BeaconRenderer;
import com.battlelinesystem.client.CapturePointRenderer;
import com.battlelinesystem.client.LooseSpawnRenderer;
import com.battlelinesystem.client.SpawnPointRenderer;
import com.battlelinesystem.client.gui.*;
import com.battlelinesystem.network.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 仅在 CLIENT 端加载。处理所有 S->C 数据包的客户端渲染逻辑。
 * 通过服务端的反射调用，避免编译期字节码引用客户端类。
 */
@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void dispatch(int packetId, Object msg) {
        try {
            switch (packetId) {
                case 0: handleOpenScreen((PacketOpenScreen) msg); break;
                case 1: handleFactionList((PacketFactionList) msg); break;
                case 2: handleMapListResponse((PacketMapListResponse) msg); break;
                case 3: handleOpenFactionVote((PacketOpenFactionVote) msg); break;
                case 4: handleOpenClassVote((PacketOpenClassVote) msg); break;
                case 5: handleSyncCapturePoints((PacketSyncCapturePoints) msg); break;
                case 6: handleCapturePointProgress((PacketCapturePointProgress) msg); break;
                case 7: handleSyncSpawnPoints((PacketSyncSpawnPoints) msg); break;
                case 8: handleGameOver((PacketGameOverResult) msg); break;
                case 9: handleTimeUp((PacketTimeUp) msg); break;
                case 10: handleSyncBeaconEntities((PacketSyncBeaconEntities) msg); break;
                case 11: handleOpenCommanderVote((PacketOpenCommanderVote) msg); break;
                case 12: handleLooseSpawnTest((PacketLooseSpawnTest) msg); break;
                case 13: handleSyncForbiddenZones((PacketSyncForbiddenZones) msg); break;
                case 14: handleSyncClassCounts((PacketClassCountUpdate) msg); break;
            }
        } catch (Exception e) {
            BattleLineSystem.LOGGER.error("ClientPacketHandler error", e);
        }
    }

    // --- 0: OpenScreen ---
    private static void handleOpenScreen(PacketOpenScreen msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.screenType == 0) {
            if (mc.screen instanceof MapSelectScreen existing) {
                existing.refresh(msg.modeCounts, msg.countdownSeconds);
                return;
            }
            mc.setScreen(new MapSelectScreen(msg.isOp, msg.modeCounts, msg.countdownSeconds));
        } else if (msg.screenType == 1) {
            if (mc.screen instanceof TeamSelectScreen existing) {
                existing.refreshCountdown(msg.countdownSeconds);
                existing.refreshCounts(msg.countA, msg.countB);
                return;
            }
            mc.setScreen(new TeamSelectScreen(msg.countdownSeconds, msg.countA, msg.countB));
        } else if (msg.screenType == 2) {
            mc.setScreen(new SettingsScreen(msg.isOp, msg.modeCounts, msg.countdownSeconds));
        }
    }

    // --- 1: FactionList ---
    private static void handleFactionList(PacketFactionList msg) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s instanceof FactionSettingsScreen screen) screen.updateFactions(msg.factions);
        else if (s instanceof FactionVoteScreen screen) screen.updateFactions(msg.factions);
        else if (s instanceof MapEditScreen screen) screen.updateFactions(msg.factions);
    }

    // --- 2: MapListResponse ---
    private static void handleMapListResponse(PacketMapListResponse msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MapSettingsScreen screen) screen.updateMaps(msg.maps);
    }

    // --- 3: OpenFactionVote ---
    private static void handleOpenFactionVote(PacketOpenFactionVote msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FactionVoteScreen existing) {
            existing.refreshCountdown(msg.countdownSeconds);
            return;
        }
        FactionVoteScreen screen = new FactionVoteScreen();
        mc.setScreen(screen);
        screen.setFactions(msg.factions, msg.poolA, msg.poolB);
        screen.refreshCountdown(msg.countdownSeconds);
        CapturePointRenderer.cacheFactionPools(msg.factions, msg.poolA, msg.poolB);
    }

    // --- 4: OpenClassVote ---
    private static void handleOpenClassVote(PacketOpenClassVote msg) {
        SpawnPointRenderer.setMyTeam(msg.team);
        LooseSpawnRenderer.setSameTeamUUIDs(msg.sameTeamUUIDs);
        LooseSpawnRenderer.setEnabled(msg.looseSpawn);
        String teamStr = msg.team == 0 ? "A" : (msg.team == 1 ? "B" : null);
        LooseSpawnRenderer.setMyTeam(teamStr);
        WaitHudOverlay.resetDeployFade();
        WaitHudOverlay.setClassOptions(
                msg.factionId, msg.factionName, msg.factionColor, msg.classes,
                msg.classCounts, msg.totalPlayers, msg.vehicleList, msg.vehicleCounts,
                msg.vehicleAlive, msg.vehicleCooldownsData);
        // 部署冷却倒计时
        WaitHudOverlay.setDeployCooldownMs(msg.deployCooldownMs);
        // 同步双方阵营名称（服务端确定，替代客户端本地"池中第一个"的逻辑）
        CapturePointRenderer.setTeamNames(msg.teamAName, msg.teamBName);
        Minecraft mc = Minecraft.getInstance();
        WaitHudOverlay.closeScreen = false;
        if (mc.player != null && mc.player.isSpectator()) {
            mc.setScreen(new ClassSelectScreen());
        }
    }

    // --- 5: SyncCapturePoints ---
    private static void handleSyncCapturePoints(PacketSyncCapturePoints msg) {
        CapturePointRenderer.setCapturePoints(msg.points);
    }

    // --- 6: CapturePointProgress ---
    private static void handleCapturePointProgress(PacketCapturePointProgress msg) {
        CapturePointRenderer.setCaptureProgress(msg.entries, msg.scoreA, msg.scoreB,
                msg.timeLimitMinutes, msg.elapsedSeconds, msg.timeUpRule);
    }

    // --- 7: SyncSpawnPoints ---
    private static void handleSyncSpawnPoints(PacketSyncSpawnPoints msg) {
        BlockPos[] a = new BlockPos[msg.spawnA.length];
        for (int i = 0; i < a.length; i++)
            a[i] = new BlockPos((int) msg.spawnA[i][0], (int) msg.spawnA[i][1], (int) msg.spawnA[i][2]);
        BlockPos[] b = new BlockPos[msg.spawnB.length];
        for (int i = 0; i < b.length; i++)
            b[i] = new BlockPos((int) msg.spawnB[i][0], (int) msg.spawnB[i][1], (int) msg.spawnB[i][2]);
        SpawnPointRenderer.setSpawnPoints(a, b);
        SpawnPointRenderer.setVehicleSpawnTypes(msg.vehicleSpawnTypesA, msg.vehicleSpawnTypesB);
    }

    // --- 8: GameOver ---
    private static void handleGameOver(PacketGameOverResult msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GameOverScreen) {
            BattleLineSystem.LOGGER.info("[GameOver] 客户端已显示结算画面，忽略重复数据包");
            return;
        }
        BattleLineSystem.LOGGER.info("[GameOver] 客户端打开结算画面 winner={}", msg.winner);
        mc.setScreen(new GameOverScreen(msg));
    }

    // --- 9: TimeUp ---
    private static void handleTimeUp(PacketTimeUp msg) {
        Minecraft mc = Minecraft.getInstance();
        BattleLineSystem.LOGGER.info("[TimeUp-C] 收到地图投票 mode={} maps={}", msg.winningMode, msg.maps.size());
        mc.setScreen(new MapVoteScreen(msg.winningMode, msg.maps));
    }

    // --- 10: SyncBeaconEntities ---
    private static void handleSyncBeaconEntities(PacketSyncBeaconEntities msg) {
        BattleLineSystem.LOGGER.info("[信标调试-C] 收到 PacketSyncBeaconEntities entries={}",
                msg.entries != null ? msg.entries.size() : 0);
        if (msg.entries != null) {
            for (var e : msg.entries) {
                BattleLineSystem.LOGGER.info("[信标调试-C] - uuid={} pos=({},{},{}) team={}",
                        e.uuid, e.x, e.y, e.z, e.team);
            }
        }
        BeaconRenderer.setBeacons(msg.entries);
    }

    // --- 11: OpenCommanderVote ---
    private static void handleOpenCommanderVote(PacketOpenCommanderVote msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CommanderVoteScreen existing) {
            existing.refreshCountdown(msg.countdownSeconds);
            return;
        }
        mc.setScreen(new CommanderVoteScreen(msg.playerNames, msg.team, msg.countdownSeconds));
    }

    // --- 12: LooseSpawnTest ---
    private static void handleLooseSpawnTest(PacketLooseSpawnTest msg) {
        if (msg.clear) {
            LooseSpawnRenderer.clearTestTeammates();
        } else {
            LooseSpawnRenderer.addTestTeammate(msg.x, msg.y, msg.z, msg.uuid, msg.team);
        }
    }

    // --- 13: SyncForbiddenZones ---
    private static void handleSyncForbiddenZones(PacketSyncForbiddenZones msg) {
        com.battlelinesystem.client.CapturePointRenderer.setForbiddenZones(msg.zones);
    }

    // --- 14: SyncClassCounts ---
    private static void handleSyncClassCounts(PacketClassCountUpdate msg) {
        WaitHudOverlay.updateClassCounts(msg.factionId, msg.classCounts,
                msg.vehicleCounts, msg.vehicleAlive, msg.vehicleCooldownsData);
    }
}

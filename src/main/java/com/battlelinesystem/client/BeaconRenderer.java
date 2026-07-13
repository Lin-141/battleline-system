package com.battlelinesystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 信标实体 3D 悬浮框渲染 + 点击检测（客户端）
 * NBT "spawn_beacon"=1  → 在部署界面显示为可选重生点
 * NBT "spawn_beacon_team"="A"/"B" → 限定队伍可见
 */
public class BeaconRenderer {

    /** UUID → (x, y, z, team) */
    private static final List<BeaconData> beacons = new ArrayList<>();

    /** 选中的信标 UUID，null=未选 */
    private static java.util.UUID selectedBeaconUuid = null;

    /** 清空所有信标 */
    public static void setBeacons(List<com.battlelinesystem.network.packet.PacketSyncBeaconEntities.BeaconEntry> entries) {
        beacons.clear();
        if (entries != null) {
            for (var e : entries) {
                beacons.add(new BeaconData(e.uuid, e.x, e.y, e.z, e.team));
            }
        }
        com.battlelinesystem.BattleLineSystem.LOGGER.info("[信标调试-R] setBeacons count={}", beacons.size());
        if (selectedBeaconUuid != null) {
            boolean found = false;
            for (var b : beacons) {
                if (b.uuid.equals(selectedBeaconUuid)) { found = true; break; }
            }
            if (!found) selectedBeaconUuid = null;
        }
    }

    /** 获取本地玩家所属队伍索引（0=A, 1=B, -1=未知） */
    private static int myTeam() {
        return com.battlelinesystem.client.SpawnPointRenderer.getMyTeam();
    }

    /** 该信标对当前玩家是否可见 */
    private static boolean isVisible(BeaconData b) {
        if (b.team == null) return true;
        int mt = myTeam();
        return (b.team.equals("A") && mt == 0) || (b.team.equals("B") && mt == 1);
    }

    /** 获取当前选中的信标 UUID */
    public static java.util.UUID getSelectedBeaconUuid() {
        return selectedBeaconUuid;
    }

    /** 重置选中 */
    public static void resetSelection() {
        selectedBeaconUuid = null;
    }

    /** 处理屏幕点击 — 射线检测本队可见信标。
     *  未命中时不清空选择（保持上次选中），需要取消请用 resetSelection()。 */
    public static boolean handleClick(int mouseX, int mouseY) {
        if (beacons.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return false;

        WorldHudUtils.CamParams cp = WorldHudUtils.getCamParams(mc);

        double bestDist = Double.MAX_VALUE;
        java.util.UUID bestUuid = null;

        for (BeaconData b : beacons) {
            if (!isVisible(b)) continue;
            int[] sp = WorldHudUtils.project(b.x, b.y, b.z, cp);
            if (sp == null) continue;
            double d = WorldHudUtils.sqDist(mouseX, mouseY, sp[0], sp[1]);
            if (d < bestDist) { bestDist = d; bestUuid = b.uuid; }
        }

        if (bestDist < 15 * 15 && bestUuid != null) {
            selectedBeaconUuid = bestUuid;
            return true;
        }
        return false;
    }

    /** 渲染信标悬浮框（按队伍颜色：A蓝/B红，双方可见=青绿） */
    private static int renderDebugCounter = 0;
    public static void render(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               MultiBufferSource bufferSource, Camera camera) {
        if (beacons.isEmpty()) {
            if (renderDebugCounter++ % 40 == 0) {
                com.battlelinesystem.BattleLineSystem.LOGGER.info("[信标调试-R] render跳过: beacons为空");
            }
            return;
        }
        boolean hudActive = com.battlelinesystem.client.gui.WaitHudOverlay.active;
        if (!hudActive) {
            if (renderDebugCounter++ % 40 == 0) {
                com.battlelinesystem.BattleLineSystem.LOGGER.info("[信标调试-R] render跳过: WaitHudOverlay.active=false (beacons={})",
                        beacons.size());
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 camPos = camera.getPosition();

        int visibleCount = 0;
        for (BeaconData b : beacons) {
            if (!isVisible(b)) continue;
            visibleCount++;
            boolean isSel = b.uuid.equals(selectedBeaconUuid);
            int boxColor, bgColor;
            if (isSel) {
                boxColor = 0xFFFFFF00;
                bgColor = 0xCCFFAA00;
            } else if ("A".equals(b.team)) {
                boxColor = 0xFF4488FF;
                bgColor = 0xCC4488FF;
            } else if ("B".equals(b.team)) {
                boxColor = 0xFFFF4444;
                bgColor = 0xCCFF4444;
            } else {
                boxColor = 0xFF00FF88;
                bgColor = 0xCC008844;
            }
            WorldHudUtils.renderBillboardBox(poseStack, bufferSource, camera, font,
                    b.x, b.y + 2.5, b.z, camPos, "★", boxColor, bgColor);
        }

        if (visibleCount == 0 && renderDebugCounter++ % 40 == 0) {
            com.battlelinesystem.BattleLineSystem.LOGGER.info("[信标调试-R] render跳过: isVisible全false myTeam={} beacons={}",
                    myTeam(), beacons.size());
        }
    }

    private static class BeaconData {
        final java.util.UUID uuid;
        final double x, y, z;
        final String team; // "A"/"B"/null=双方可见
        BeaconData(java.util.UUID uuid, double x, double y, double z, String team) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z; this.team = team;
        }
    }
}

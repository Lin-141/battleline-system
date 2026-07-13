package com.battlelinesystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 宽松重生点渲染器 — 部署界面中在同阵营队友头顶显示可点击的部署框
 */
public class LooseSpawnRenderer {

    /** 当前选中的队友 UUID，null=未选 */
    private static UUID selectedUUID = null;

    /** 本地玩家的队伍名（由 WaitHudOverlay 设置） */
    private static String myTeam = null;

    /** 是否启用宽松重生 */
    private static boolean enabled = false;

    /** 同队玩家的 UUID 集合（由 PacketOpenClassVote 同步，兼容远程客户端） */
    private static final Set<UUID> sameTeamUUIDs = new HashSet<>();

    /** 测试队友数据（由网络包填充，不依赖世界实体扫描） */
    private static final List<TestTeammate> testTeammates = new ArrayList<>();

    public static class TestTeammate {
        public final double x, y, z;
        public final UUID uuid;
        public final String team;
        public final String name;
        TestTeammate(double x, double y, double z, UUID uuid, String team) {
            this.x = x; this.y = y; this.z = z;
            this.uuid = uuid;
            this.team = team;
            this.name = "测试队友[" + team + "队]";
        }
    }

    public static void setMyTeam(String team) { myTeam = team; }
    public static String getMyTeam() { return myTeam; }

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    public static UUID getSelectedUUID() { return selectedUUID; }

    public static void resetSelection() { selectedUUID = null; }

    public static void setSameTeamUUIDs(java.util.List<UUID> uuids) {
        if (uuids != null && !uuids.isEmpty()) {
            sameTeamUUIDs.clear();
            sameTeamUUIDs.addAll(uuids);
        }
    }

    public static void addTestTeammate(double x, double y, double z, UUID uuid, String team) {
        testTeammates.add(new TestTeammate(x, y, z, uuid, team));
        com.battlelinesystem.BattleLineSystem.LOGGER.info("[LOOSE] 收到测试队友 pos=({},{},{}) team={}", x, y, z, team);
    }

    public static void clearTestTeammates() {
        testTeammates.clear();
        com.battlelinesystem.BattleLineSystem.LOGGER.info("[LOOSE] 已清除所有测试队友");
    }

    public static void clear() {
        selectedUUID = null;
        myTeam = null;
        enabled = false;
        testTeammates.clear();
        sameTeamUUIDs.clear();
    }

    /** 获取玩家皮肤纹理 */
    private static ResourceLocation getSkinTexture(Player player) {
        return ((AbstractClientPlayer) player).getSkinTextureLocation();
    }

    /** 检测玩家是否驾驶固定翼飞机（无法部署，灰显） */
    private static boolean isInFixedWing(Player player) {
        if (player.getRootVehicle() instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity ve) {
            com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType vt = ve.getVehicleType();
            return vt == com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType.AIRPLANE;
        }
        return false;
    }

    /** 检测玩家所在载具是否已满（无空位让新玩家乘坐） */
    private static boolean isInFullVehicle(Player player) {
        net.minecraft.world.entity.Entity root = player.getRootVehicle();
        if (root instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity ve) {
            return ve.getPassengers().size() >= ve.getMaxPassengers();
        }
        return false;
    }

    /** 检测队友是否为灰色不可选中状态（固定翼 或 载具已满） */
    private static boolean isGrayedOut(Player player) {
        return isInFixedWing(player) || isInFullVehicle(player);
    }

    /** 队友框 scale */
    private static final float BOX_SCALE = 0.29f;

    // ---- 3D渲染（在 RenderLevelStageEvent 中调用） ----

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        if (!enabled || myTeam == null) return;
        if (!com.battlelinesystem.client.gui.WaitHudOverlay.isClassSelectActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Font font = mc.font;
        Vec3 camPos = camera.getPosition();

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.isSpectator()) continue;
            if (!sameTeamUUIDs.contains(p.getUUID())) continue;

            boolean isSel = selectedUUID != null && selectedUUID.equals(p.getUUID());
            boolean grayed = isGrayedOut(p);
            int boxColor, bgColor;
            if (grayed) {
                boxColor = 0xFF555555;
                bgColor   = 0xCC333333;
            } else {
                boxColor = isSel ? 0xFFFFFF00 : 0xFF00FF88;
                bgColor  = isSel ? 0xCCFFAA00 : 0xCC00FF44;
            }
            ResourceLocation skin = getSkinTexture(p);
            WorldHudUtils.renderBillboardFace(poseStack, bufferSource, camera, font,
                    p.getX(), p.getY() + p.getBbHeight() + 0.5, p.getZ(),
                    camPos, p.getName().getString(), boxColor, bgColor, BOX_SCALE, skin);
        }

        // 渲染测试队友（数据来自网络包，不扫描世界实体）
        for (TestTeammate t : testTeammates) {
            if (!myTeam.equals(t.team)) continue;
            boolean isSel = selectedUUID != null && selectedUUID.equals(t.uuid);
            int boxColor = isSel ? 0xFFFFFF00 : 0xFF00FF88;
            int bgColor = isSel ? 0xCCFFAA00 : 0xCC00FF44;
            WorldHudUtils.renderBillboardBox(poseStack, bufferSource, camera, font,
                    t.x, t.y + 1.85, t.z, camPos, t.name, boxColor, bgColor, BOX_SCALE);
        }
    }

    // ---- 点击检测（在 WaitHudOverlay.handleClick 中调用） ----

    public static boolean handleClick(int screenX, int screenY) {
        if (!enabled || myTeam == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        WorldHudUtils.CamParams cp = WorldHudUtils.getCamParams(mc);

        UUID best = null;
        double bestDist = Double.MAX_VALUE;

        // 真实玩家
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.isSpectator()) continue;
            if (isGrayedOut(p)) continue; // 固定翼/载具已满 → 不可选
            if (!sameTeamUUIDs.contains(p.getUUID())) continue;

            int[] proj = WorldHudUtils.project(p.getX(), p.getY() + p.getBbHeight() + 0.5, p.getZ(), cp);
            if (proj == null) continue;
            if (hitTest(screenX, screenY, proj[0], proj[1], p.getName().getString(), mc)) {
                double cur = WorldHudUtils.sqDist(screenX, screenY, proj[0], proj[1]);
                if (cur < bestDist) { bestDist = cur; best = p.getUUID(); }
            }
        }

        // 测试队友
        for (TestTeammate t : testTeammates) {
            if (!myTeam.equals(t.team)) continue;
            int[] proj = WorldHudUtils.project(t.x, t.y + 1.85, t.z, cp);
            if (proj == null) continue;
            if (hitTest(screenX, screenY, proj[0], proj[1], t.name, mc)) {
                double cur = WorldHudUtils.sqDist(screenX, screenY, proj[0], proj[1]);
                if (cur < bestDist) { bestDist = cur; best = t.uuid; }
            }
        }

        if (best != null) {
            selectedUUID = best;
            com.battlelinesystem.client.SpawnPointRenderer.resetSelection();
            com.battlelinesystem.client.BeaconRenderer.resetSelection();
            com.battlelinesystem.client.SpawnPointRenderer.setSelectedCapturePointName(null);
            return true;
        }
        return false;
    }

    private static boolean hitTest(int sx, int sy, int px, int py, String name, Minecraft mc) {
        int nameW = mc.font.width(name);
        int halfW = nameW / 2 + 6;
        int halfH = 8;
        return sx >= px - halfW && sx <= px + halfW && sy >= py - halfH && sy <= py + halfH;
    }
}

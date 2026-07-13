package com.battlelinesystem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 出生点上方悬浮框渲染（客户端）— A队蓝色框，B队红色框，显示编号
 * 支持点击选中出生点（3D射线检测）
 */
public class SpawnPointRenderer {

    /** A队出生点列表 */
    private static BlockPos[] spawnA = new BlockPos[0];
    /** B队出生点列表 */
    private static BlockPos[] spawnB = new BlockPos[0];
    /** A队载具出生点类型 */
    private static java.util.Set<String> vehicleSpawnTypesA = new java.util.LinkedHashSet<>();
    /** B队载具出生点类型 */
    private static java.util.Set<String> vehicleSpawnTypesB = new java.util.LinkedHashSet<>();

    // ---- 选中状态 ----
    /** 0=A队, 1=B队, -1=未选 */
    private static int selectedTeam = -1;
    /** 选中出生点在数组中的索引, -1=未选（常规出生点） */
    private static int selectedIndex = -1;
    /** 选中的据点名称（据点出生），null=未选据点 */
    private static String selectedCapturePointName = null;

    /** 本地玩家所属队伍: 0=A, 1=B, -1=未知 */
    private static int myTeam = -1;

    /** 设置本地玩家的队伍 */
    public static void setMyTeam(int team) { myTeam = team; }

    /** 获取本地玩家的队伍: 0=A, 1=B, -1=未知 */
    public static int getMyTeam() { return myTeam; }

    public static void setSpawnPoints(BlockPos[] a, BlockPos[] b) {
        spawnA = a != null ? a : new BlockPos[0];
        spawnB = b != null ? b : new BlockPos[0];
        selectedTeam = -1;
        selectedIndex = -1;
        selectedCapturePointName = null;
    }

    /** 仅重置选中状态，不清理出生点数组（部署后恢复界面时保留出生点数据） */
    public static void resetSelection() {
        selectedTeam = -1;
        selectedIndex = -1;
        selectedCapturePointName = null;
    }

    public static void clear() {
        spawnA = new BlockPos[0];
        spawnB = new BlockPos[0];
        selectedTeam = -1;
        selectedIndex = -1;
        selectedCapturePointName = null;
        myTeam = -1;
        vehicleSpawnTypesA = new java.util.LinkedHashSet<>();
        vehicleSpawnTypesB = new java.util.LinkedHashSet<>();
    }

    public static void setVehicleSpawnTypes(java.util.Set<String> typesA, java.util.Set<String> typesB) {
        vehicleSpawnTypesA = typesA != null ? typesA : new java.util.LinkedHashSet<>();
        vehicleSpawnTypesB = typesB != null ? typesB : new java.util.LinkedHashSet<>();
    }

    public static java.util.Set<String> getVehicleSpawnTypes() {
        return myTeam == 0 ? vehicleSpawnTypesA : myTeam == 1 ? vehicleSpawnTypesB : new java.util.LinkedHashSet<>();
    }

    /** 查询某载具类型是否有对应的出生点（HUD显示用，仅精确匹配，不做land回退） */
    public static boolean hasVehicleSpawnForType(String type) {
        if (type == null) return false;
        java.util.Set<String> types = myTeam == 0 ? vehicleSpawnTypesA : myTeam == 1 ? vehicleSpawnTypesB : null;
        if (types == null || types.isEmpty()) return false;
        return types.contains(type);
    }

    /** 载具是否有可用出生点（含 land 回退 — 用于渲染/部署判断） */
    public static boolean hasUsableVehicleSpawn(String type) {
        if (type == null || myTeam < 0) return false;
        java.util.Set<String> types = myTeam == 0 ? vehicleSpawnTypesA : vehicleSpawnTypesB;
        if (types.contains(type)) return true;
        // 地面载具回退 land
        if (GROUND_TYPES.contains(type) && types.contains("land")) return true;
        return false;
    }

    private static final java.util.Set<String> GROUND_TYPES = java.util.Set.of("tank", "apc", "car");

    /** 获取选中的出生点BlockPos，null=随机 */
    public static BlockPos getSelectedSpawnPos() {
        if (selectedTeam == 0 && selectedIndex >= 0 && selectedIndex < spawnA.length)
            return spawnA[selectedIndex];
        if (selectedTeam == 1 && selectedIndex >= 0 && selectedIndex < spawnB.length)
            return spawnB[selectedIndex];
        return null;
    }

    /** 获取选中的出生点索引，-1=随机 */
    public static int getSelectedSpawnIndex() {
        return selectedCapturePointName != null ? -1 : selectedIndex;
    }

    /** 获取选中的据点名称，null=使用常规出生点 */
    public static String getSelectedCapturePointName() {
        return selectedCapturePointName;
    }

    /** 直接设置选中的据点名称（供底部选择栏使用） */
    public static void setSelectedCapturePointName(String name) {
        selectedCapturePointName = name;
        selectedTeam = -1;
        selectedIndex = -1;
        com.battlelinesystem.client.BeaconRenderer.resetSelection();
    }

    /** 直接设置选中出生点索引（供底部选择栏使用） */
    public static void setSelectedSpawnIndex(int team, int index) {
        selectedTeam = team;
        selectedIndex = index;
        selectedCapturePointName = null;
        com.battlelinesystem.client.BeaconRenderer.resetSelection();
    }

    /** 获取本队出生点列表 */
    public static BlockPos[] getMySpawnPoints() {
        if (myTeam == 0) return spawnA;
        if (myTeam == 1) return spawnB;
        return new BlockPos[0];
    }

    /** 获取选中的出生点团队 */
    public static int getSelectedTeam() { return selectedTeam; }

    /** 处理屏幕点击 — 将出生点3D投影到屏幕2D，再对比点击坐标。
     *  未命中时不清空选择（保持上次选中），需要取消请用 resetSelection()。 */
    public static boolean handleClick(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return false;

        WorldHudUtils.CamParams cp = WorldHudUtils.getCamParams(mc);

        double bestScreenDist = Double.MAX_VALUE;
        int bestTeam = -1;
        int bestIdx = -1;
        String bestCpName = null;

        // 检测A队出生点（仅当玩家在A队时）
        if (myTeam == 0) {
            for (int i = 0; i < spawnA.length; i++) {
                int[] sp = WorldHudUtils.project(spawnA[i], cp);
                if (sp == null) continue;
                double d = WorldHudUtils.sqDist(mouseX, mouseY, sp[0], sp[1]);
                if (d < bestScreenDist) { bestScreenDist = d; bestTeam = 0; bestIdx = i; bestCpName = null; }
            }
        }

        // 检测B队出生点（仅当玩家在B队时）
        if (myTeam == 1) {
            for (int i = 0; i < spawnB.length; i++) {
                int[] sp = WorldHudUtils.project(spawnB[i], cp);
                if (sp == null) continue;
                double d = WorldHudUtils.sqDist(mouseX, mouseY, sp[0], sp[1]);
                if (d < bestScreenDist) { bestScreenDist = d; bestTeam = 1; bestIdx = i; bestCpName = null; }
            }
        }

        // 检测已占领据点（本队拥有的据点可作重生点）
        if (myTeam == 0 || myTeam == 1) {
            List<com.battlelinesystem.world.MapConfig.CapturePoint> cps =
                    com.battlelinesystem.client.CapturePointRenderer.getCapturePoints();
            for (com.battlelinesystem.world.MapConfig.CapturePoint ccp : cps) {
                com.battlelinesystem.client.CapturePointRenderer.CaptureProgressData prog =
                        com.battlelinesystem.client.CapturePointRenderer.getProgress(ccp.name);
                if (prog == null) continue;
                // 本队完全占领 + 进度仍在己方一侧才能传送
                boolean ownedByMe = (myTeam == 0 && prog.owner == 1 && prog.progress < 0)
                        || (myTeam == 1 && prog.owner == 2 && prog.progress > 0);
                if (!ownedByMe) continue;

                BlockPos center = ccp.getDisplayCenter();
                int[] sp = WorldHudUtils.project(center, cp);
                if (sp == null) continue;
                double d = WorldHudUtils.sqDist(mouseX, mouseY, sp[0], sp[1]);
                if (d < bestScreenDist) { bestScreenDist = d; bestTeam = -1; bestIdx = -1; bestCpName = ccp.name; }
            }
        }

        // 命中阈值：投影点距点击位置在 15 像素以内
        if (bestScreenDist < 15 * 15) {
            if (bestCpName != null) {
                selectedCapturePointName = bestCpName;
                selectedTeam = -1;
                selectedIndex = -1;
                return true;
            }
            if (bestTeam >= 0) {
                selectedTeam = bestTeam;
                selectedIndex = bestIdx;
                selectedCapturePointName = null;
                return true;
            }
        }

        return false;
    }

    /** 客户端获取选中状态，供HUD显示 */
    public static boolean hasSelection() { return (selectedTeam >= 0 && selectedIndex >= 0) || selectedCapturePointName != null; }
    public static String getSelectedLabel() {
        if (selectedCapturePointName != null) return "已选据点: " + selectedCapturePointName;
        if (!hasSelection()) return "";
        BlockPos[] arr = selectedTeam == 0 ? spawnA : spawnB;
        if (selectedIndex >= arr.length) return "";
        String team = selectedTeam == 0 ? "A" : "B";
        return "已选" + team + "队#" + (selectedIndex + 1) + " " + arr[selectedIndex].toShortString();
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        if (spawnA.length == 0 && spawnB.length == 0) return;
        if (!com.battlelinesystem.client.gui.WaitHudOverlay.active) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 camPos = camera.getPosition();

        // A队出生点（己方蓝/对方红）
        for (int i = 0; i < spawnA.length; i++) {
            BlockPos pos = spawnA[i];
            boolean isSel = (selectedTeam == 0 && selectedIndex == i);
            int boxColor = isSel ? 0xFFFFFF00 : (myTeam == 0 ? 0xFF4488FF : 0xFFFF4444);
            int bgColor = isSel ? 0xCCFFAA00 : (myTeam == 0 ? 0xCC4488FF : 0xCCFF4444);
            renderSpawnBox(poseStack, bufferSource, camera, font, pos, camPos,
                    i + 1, boxColor, bgColor);
        }

        // B队出生点（己方蓝/对方红）
        for (int i = 0; i < spawnB.length; i++) {
            BlockPos pos = spawnB[i];
            boolean isSel = (selectedTeam == 1 && selectedIndex == i);
            int boxColor = isSel ? 0xFFFFFF00 : (myTeam == 1 ? 0xFF4488FF : 0xFFFF4444);
            int bgColor = isSel ? 0xCCFFAA00 : (myTeam == 1 ? 0xCC4488FF : 0xCCFF4444);
            renderSpawnBox(poseStack, bufferSource, camera, font, pos, camPos,
                    i + 1, boxColor, bgColor);
        }
    }

    private static void renderSpawnBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                        Camera camera, Font font, BlockPos pos, Vec3 camPos,
                                        int number, int boxColor, int bgColor) {
        WorldHudUtils.renderBillboardBox(poseStack, bufferSource, camera, font,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                camPos, String.valueOf(number), boxColor, bgColor);
    }
}

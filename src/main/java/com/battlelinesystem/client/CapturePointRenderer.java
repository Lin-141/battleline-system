package com.battlelinesystem.client;

import com.battlelinesystem.world.MapConfig;
import com.battlelinesystem.network.packet.PacketCapturePointProgress;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 据点区域上方字母渲染 + 粒子选区环绕 + 占领进度条（客户端）
 */
public class CapturePointRenderer {

    private static final List<MapConfig.CapturePoint> capturePoints = new ArrayList<>();

    /** 客户端缓存的禁区列表 */
    private static final List<ForbiddenZoneData> forbiddenZones = new ArrayList<>();

    /** 据点名 -> 占领进度信息 */
    private static final Map<String, CaptureProgressData> progressMap = new LinkedHashMap<>();

    /** 服务端同步的队伍分数 */
    private static int syncedScoreA = 200;
    private static int syncedScoreB = 200;
    /** 游戏时限（分钟）0=不限时 */
    private static int syncedTimeLimit = 0;
    /** 已流逝秒数 */
    private static int syncedElapsed = 0;

    public static void setCapturePoints(List<MapConfig.CapturePoint> points) {
        capturePoints.clear();
        if (points != null) capturePoints.addAll(points);
        // 仅在游戏结束（空列表）时清空进度和分数，正常同步不干扰 progressMap
        if (capturePoints.isEmpty()) {
            progressMap.clear();
            syncedScoreA = 200;
            syncedScoreB = 200;
            syncedTimeLimit = 0;
            syncedElapsed = 0;
            com.battlelinesystem.client.gui.WaitHudOverlay.disable();
            com.battlelinesystem.client.SpawnPointRenderer.clear();
            forbiddenZones.clear();
        } else {
            // 预填充初始进度（initialOwner），确保首包到达前 HUD 能正确显示
            // 后续 setCaptureProgress 包会覆盖这些默认值
            for (MapConfig.CapturePoint cp : capturePoints) {
                if (!progressMap.containsKey(cp.name)) {
                    float prog = 0f;
                    byte owner = 0;
                    if ("A".equals(cp.initialOwner)) { prog = -1f; owner = 1; }
                    else if ("B".equals(cp.initialOwner)) { prog = 1f; owner = 2; }
                    progressMap.put(cp.name, new CaptureProgressData(prog, owner, (byte)0, 0, 0, false));
                }
            }
        }
    }

    public static void setCaptureProgress(List<PacketCapturePointProgress.CaptureEntry> entries,
                                           int scoreA, int scoreB,
                                           int timeLimitMinutes, int elapsedSeconds, String timeUpRule) {
        progressMap.clear();
        if (entries != null) {
            for (var e : entries) {
                progressMap.put(e.name, new CaptureProgressData(e.progress, e.owner, e.capturing,
                        e.teamACount, e.teamBCount, e.locked));
            }
        }
        syncedScoreA = scoreA;
        syncedScoreB = scoreB;
        syncedTimeLimit = timeLimitMinutes;
        syncedElapsed = elapsedSeconds;
        if (entries != null && !entries.isEmpty()) {
            var e = entries.get(0);
            com.battlelinesystem.BattleLineSystem.LOGGER.info("[CapturePoint] setCaptureProgress entries={} A={} B={}  first=[name={} prog={:.2f} owner={} capt={} tA={} tB={}]",
                    entries.size(), scoreA, scoreB, e.name, e.progress, e.owner, e.capturing, e.teamACount, e.teamBCount);
        } else {
            com.battlelinesystem.BattleLineSystem.LOGGER.info("[CapturePoint] setCaptureProgress entries={} A={} B={}",
                    entries != null ? entries.size() : 0, scoreA, scoreB);
        }
    }

    public static class CaptureProgressData {
        public float progress; // -1(A满) ~ 0(中立) ~ 1(B满)
        public byte owner;     // 0=none, 1=A, 2=B
        public byte capturing; // 0=none, 1=A, 2=B, 3=contested
        public int teamACount;
        public int teamBCount;
        public boolean locked; // 前置据点未满足
        public CaptureProgressData(float p, byte o, byte c, int a, int b, boolean locked) {
            progress = p; owner = o; capturing = c; teamACount = a; teamBCount = b;
            this.locked = locked;
        }
    }

    public static class ForbiddenZoneData {
        public final String name;
        public final String forbiddenTeam;
        public final List<net.minecraft.core.BlockPos> boundary;

        public ForbiddenZoneData(String name, String forbiddenTeam, List<net.minecraft.core.BlockPos> boundary) {
            this.name = name;
            this.forbiddenTeam = forbiddenTeam;
            this.boundary = boundary;
        }
    }

    public static void setForbiddenZones(
            java.util.List<com.battlelinesystem.network.packet.PacketSyncForbiddenZones.ZoneEntry> entries) {
        forbiddenZones.clear();
        if (entries != null) {
            for (var e : entries) {
                List<net.minecraft.core.BlockPos> bp = new java.util.ArrayList<>();
                for (int[] v : e.boundary) {
                    bp.add(new net.minecraft.core.BlockPos(v[0], v[1], v[2]));
                }
                forbiddenZones.add(new ForbiddenZoneData(e.name, e.forbiddenTeam, bp));
            }
        }
    }

    public static List<MapConfig.CapturePoint> getCapturePoints() {
        return Collections.unmodifiableList(capturePoints);
    }

    public static List<ForbiddenZoneData> getForbiddenZones() {
        return Collections.unmodifiableList(forbiddenZones);
    }

    public static CaptureProgressData getProgress(String name) {
        return progressMap.get(name);
    }

    /** 粒子效果已移除 */
    public static void tickParticles() {
    }

    /** 3D 世界空间渲染：区域线框 + 部署后标签。GL 状态统一管理，消除每据点冗余切换。 */
    public static void renderWorld(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        if (capturePoints.isEmpty()) return;
        boolean isDeploy = com.battlelinesystem.client.gui.WaitHudOverlay.active;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 camPos = camera.getPosition();

        // 收集所有可见据点
        int visibleCount = 0;
        for (int i = 0; i < capturePoints.size(); i++) {
            MapConfig.CapturePoint cp = capturePoints.get(i);
            if (cp.zones == null || cp.zones.isEmpty()) continue;
            CaptureProgressData prog = progressMap.get(cp.name);
            if (prog != null && prog.locked) continue;
            double dcx = cp.getDisplayCenter().getX() - camPos.x;
            double dcy = cp.getDisplayCenter().getY() + 16.0 - camPos.y;
            double dcz = cp.getDisplayCenter().getZ() - camPos.z;
            double distSq = dcx * dcx + dcy * dcy + dcz * dcz;
            if (distSq > 400 * 400) continue;
            visibleCount++;
        }
        if (visibleCount == 0) return;

        // === Phase 1: 所有 zone 线框（共享 GL 状态，合并为单批次 DEBUG_LINES） ===
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(6.0f);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < capturePoints.size(); i++) {
            MapConfig.CapturePoint cp = capturePoints.get(i);
            if (cp.zones == null || cp.zones.isEmpty()) continue;
            CaptureProgressData prog = progressMap.get(cp.name);
            if (prog != null && prog.locked) continue;
            double dx = cp.getDisplayCenter().getX() - camPos.x;
            double dy = cp.getDisplayCenter().getY() + 16.0 - camPos.y;
            double dz = cp.getDisplayCenter().getZ() - camPos.z;
            if (dx * dx + dy * dy + dz * dz > 400 * 400) continue;

            int wireColor = getWireColor(prog);
            for (int[][] z : cp.zones) {
                addWireRect(builder, matrix, z, camPos, wireColor);
            }
        }
        tess.end();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();

        // === Phase 2: 部署后 3D 标签（合并所有 billboard 到共享批次） ===
        if (!isDeploy) {
            // 收集所有要渲染的标签数据
            List<Double> tagXs = new java.util.ArrayList<>(), tagYs = new java.util.ArrayList<>(), tagZs = new java.util.ArrayList<>();
            List<CaptureProgressData> tagProgs = new java.util.ArrayList<>();
            List<String> tagTexts = new java.util.ArrayList<>();

            for (int i = 0; i < capturePoints.size(); i++) {
                MapConfig.CapturePoint cp = capturePoints.get(i);
                if (cp.zones == null || cp.zones.isEmpty()) continue;
                CaptureProgressData prog = progressMap.get(cp.name);
                if (prog != null && prog.locked) continue;
                double dx = cp.getDisplayCenter().getX() - camPos.x;
                double dy = cp.getDisplayCenter().getY() + 16.0 - camPos.y;
                double dz = cp.getDisplayCenter().getZ() - camPos.z;
                if (dx * dx + dy * dy + dz * dz > 400 * 400) continue;
                String text = String.valueOf((char)('A' + i));
                tagXs.add(dx); tagYs.add(dy); tagZs.add(dz);
                tagProgs.add(prog);
                tagTexts.add(text);
            }

            if (!tagTexts.isEmpty()) {
                RenderSystem.enableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                float scale = 0.5f;

                // 子阶段 2a: 所有背景 QUADS 合并为单批次
                BufferBuilder bb = tess.getBuilder();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int t = 0; t < tagTexts.size(); t++) {
                    CaptureProgressData prog = tagProgs.get(t);
                    float textW = (font.width(tagTexts.get(t)) / 2f + 3f) * 2f / 3f;
                    float boxSize = Math.max(textW, 5f);
                    float halfW = boxSize, halfH = boxSize;
                    int bg = getBgColor(prog);

                    poseStack.pushPose();
                    poseStack.translate(tagXs.get(t), tagYs.get(t), tagZs.get(t));
                    poseStack.mulPose(camera.rotation());
                    poseStack.scale(-scale, -scale, scale);
                    Matrix4f m = poseStack.last().pose();
                    int r = bg >> 16 & 0xFF, g = bg >> 8 & 0xFF, b = bg & 0xFF, a = bg >> 24 & 0xFF;
                    // 背景
                    bb.vertex(m, -halfW, -halfH, 0).color(r, g, b, a).endVertex();
                    bb.vertex(m, -halfW, halfH, 0).color(r, g, b, a).endVertex();
                    bb.vertex(m, halfW, halfH, 0).color(r, g, b, a).endVertex();
                    bb.vertex(m, halfW, -halfH, 0).color(r, g, b, a).endVertex();
                    // 占领进度条（从底部向上填充，与HUD逻辑一致）
                    if (prog != null) {
                        float ratio;
                        int fillColor;
                        if (prog.progress < 0) {
                            ratio = Math.abs(prog.progress);
                            fillColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                        } else if (prog.progress > 0) {
                            ratio = Math.abs(prog.progress);
                            fillColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                        } else if (prog.owner == 1) {
                            ratio = 1f;
                            fillColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                        } else if (prog.owner == 2) {
                            ratio = 1f;
                            fillColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                        } else {
                            ratio = 0f;
                            fillColor = 0;
                        }
                        if (ratio > 0.001f) {
                            float fillH = ratio * halfH * 2f;
                            int fr = fillColor >> 16 & 0xFF, fg = fillColor >> 8 & 0xFF, fb = fillColor & 0xFF, fa = fillColor >> 24 & 0xFF;
                            float bottom = halfH;
                            bb.vertex(m, -halfW, bottom, 0).color(fr, fg, fb, fa).endVertex();
                            bb.vertex(m, -halfW, bottom - fillH, 0).color(fr, fg, fb, fa).endVertex();
                            bb.vertex(m, halfW, bottom - fillH, 0).color(fr, fg, fb, fa).endVertex();
                            bb.vertex(m, halfW, bottom, 0).color(fr, fg, fb, fa).endVertex();
                        }
                    }
                    poseStack.popPose();
                }
                tess.end();

                // 子阶段 2b: 所有边框 DEBUG_LINES（4条线段，避免 STRIP 跨框连线）
                RenderSystem.lineWidth(3.5f);
                bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                for (int t = 0; t < tagTexts.size(); t++) {
                    CaptureProgressData prog = tagProgs.get(t);
                    float textW = (font.width(tagTexts.get(t)) / 2f + 3f) * 2f / 3f;
                    float boxSize = Math.max(textW, 5f);
                    float halfW = boxSize, halfH = boxSize;
                    int line = (prog != null && prog.capturing != 0 && prog.owner == 0) ? 0xFFFF8800
                            : (prog == null ? 0xFF888888 : (prog.owner == 1 ? (isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE) : prog.owner == 2 ? (isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE) : 0xFFAAAAAA));
                    int lr = line >> 16 & 0xFF, lg = line >> 8 & 0xFF, lb = line & 0xFF, la = line >> 24 & 0xFF;

                    poseStack.pushPose();
                    poseStack.translate(tagXs.get(t), tagYs.get(t), tagZs.get(t));
                    poseStack.mulPose(camera.rotation());
                    poseStack.scale(-scale, -scale, scale);
                    Matrix4f m = poseStack.last().pose();
                    float x0 = -halfW, y0 = -halfH, x1 = halfW, y1 = halfH;
                    // 上边
                    bb.vertex(m, x0, y0, 0).color(lr, lg, lb, la).endVertex();
                    bb.vertex(m, x1, y0, 0).color(lr, lg, lb, la).endVertex();
                    // 右边
                    bb.vertex(m, x1, y0, 0).color(lr, lg, lb, la).endVertex();
                    bb.vertex(m, x1, y1, 0).color(lr, lg, lb, la).endVertex();
                    // 下边
                    bb.vertex(m, x1, y1, 0).color(lr, lg, lb, la).endVertex();
                    bb.vertex(m, x0, y1, 0).color(lr, lg, lb, la).endVertex();
                    // 左边
                    bb.vertex(m, x0, y1, 0).color(lr, lg, lb, la).endVertex();
                    bb.vertex(m, x0, y0, 0).color(lr, lg, lb, la).endVertex();
                    poseStack.popPose();
                }
                tess.end();
                RenderSystem.lineWidth(1.0f);

                // 子阶段 2c: 所有文本
                for (int t = 0; t < tagTexts.size(); t++) {
                    float hw = font.width(tagTexts.get(t)) / 2f;
                    poseStack.pushPose();
                    poseStack.translate(tagXs.get(t), tagYs.get(t), tagZs.get(t));
                    poseStack.mulPose(camera.rotation());
                    poseStack.scale(-scale, -scale, scale);
                    font.drawInBatch(tagTexts.get(t), -hw, -4f, 0xFFFFFFFF, false, poseStack.last().pose(),
                            bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                    poseStack.popPose();
                }

                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }
        }
    }

    /** owner=1(A) 或 owner=2(B) 是否属于本地玩家阵营 */
    private static boolean isOwnTeam(int owner) {
        int mt = SpawnPointRenderer.getMyTeam();
        return (mt == 0 && owner == 1) || (mt == 1 && owner == 2);
    }

    /** 己方阵营颜色（蓝），对方阵营颜色（红） */
    private static final int OWN_COLOR = 0x994488FF;
    private static final int ENEMY_COLOR = 0x99FF4444;
    private static final int OWN_COLOR_OPAQUE = 0xFF4488FF;
    private static final int ENEMY_COLOR_OPAQUE = 0xFFFF4444;
    private static final int OWN_BG = 0xCC4488FF;
    private static final int ENEMY_BG = 0xCCFF4444;

    private static int getWireColor(CaptureProgressData prog) {
        if (prog == null) return 0x99FFFFFF;
        if (prog.owner == 1) return isOwnTeam(1) ? OWN_COLOR : ENEMY_COLOR;
        if (prog.owner == 2) return isOwnTeam(2) ? OWN_COLOR : ENEMY_COLOR;
        if (prog.capturing != 0) return 0x99FF8800;
        return 0x99FFFFFF;
    }

    /** 获取背景色：owner已确定时用己方/敌方色，正在占领中时用占领方色，否则灰色 */
    private static int getBgColor(CaptureProgressData prog) {
        if (prog == null) return 0xCC444444;
        if (prog.owner == 1) return isOwnTeam(1) ? OWN_BG : ENEMY_BG;
        if (prog.owner == 2) return isOwnTeam(2) ? OWN_BG : ENEMY_BG;
        // owner未确定但正在被占领 → 显示占领方颜色
        if (prog.capturing == 1) return isOwnTeam(1) ? OWN_BG : ENEMY_BG;
        if (prog.capturing == 2) return isOwnTeam(2) ? OWN_BG : ENEMY_BG;
        if (prog.capturing == 3) return 0xCC666666; // contested → 灰色
        return 0xCC666666;
    }

    /** 将单个 zone 的矩形线框顶点加入共享 BufferBuilder（使用 DEBUG_LINES 代替 LINE_STRIP） */
    private static void addWireRect(BufferBuilder builder, Matrix4f matrix, int[][] z, Vec3 camPos, int color) {
        double minX = Math.min(z[0][0], z[1][0]) - camPos.x;
        double maxX = Math.max(z[0][0], z[1][0]) + 1.0 - camPos.x;
        double minY = Math.min(z[0][1], z[1][1]) - camPos.y + 0.05;
        double minZ = Math.min(z[0][2], z[1][2]) - camPos.z;
        double maxZ = Math.max(z[0][2], z[1][2]) + 1.0 - camPos.z;
        float fx1 = (float) minX, fx2 = (float) maxX;
        float fy = (float) minY;
        float fz1 = (float) minZ, fz2 = (float) maxZ;
        // 4条线段代替 LINE_STRIP 矩形
        builder.vertex(matrix, fx1, fy, fz1).color(color).endVertex();
        builder.vertex(matrix, fx2, fy, fz1).color(color).endVertex();
        builder.vertex(matrix, fx2, fy, fz1).color(color).endVertex();
        builder.vertex(matrix, fx2, fy, fz2).color(color).endVertex();
        builder.vertex(matrix, fx2, fy, fz2).color(color).endVertex();
        builder.vertex(matrix, fx1, fy, fz2).color(color).endVertex();
        builder.vertex(matrix, fx1, fy, fz2).color(color).endVertex();
        builder.vertex(matrix, fx1, fy, fz1).color(color).endVertex();
    }

    /** renderPostDeployTag3D 的轻量版本：不自行管理 GL 状态，由外层统一控制 */
    private static void renderPostDeployTag3DRaw(PoseStack poseStack, MultiBufferSource bufferSource,
                                                  Font font, double dx, double dy, double dz,
                                                  Camera camera, CaptureProgressData prog, String text) {
        float scale = 0.5f;
        int w = font.width(text);
        float halfW = w / 2f + 3f;
        float halfH = 6f;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        drawBillboardQuadRaw(matrix, -halfW, -halfH, halfW, halfH, postDeployBg(prog), postDeployLine(prog));
        font.drawInBatch(text, -w / 2f, -4f, 0xFFFFFFFF, false, matrix, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        poseStack.popPose();
    }

    /** drawBillboardQuad 的轻量版本：不管理 blend/depth/shader/lineWidth（外层已设） */
    private static void drawBillboardQuadRaw(Matrix4f matrix, float x0, float y0, float x1, float y1,
                                              int bgColor, int boxColor) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, x0, y0, 0).color(bgColor).endVertex();
        builder.vertex(matrix, x0, y1, 0).color(bgColor).endVertex();
        builder.vertex(matrix, x1, y1, 0).color(bgColor).endVertex();
        builder.vertex(matrix, x1, y0, 0).color(bgColor).endVertex();
        tess.end();

        RenderSystem.lineWidth(3.5f);
        builder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, x0, y0, 0).color(boxColor).endVertex();
        builder.vertex(matrix, x1, y0, 0).color(boxColor).endVertex();
        builder.vertex(matrix, x1, y1, 0).color(boxColor).endVertex();
        builder.vertex(matrix, x0, y1, 0).color(boxColor).endVertex();
        builder.vertex(matrix, x0, y0, 0).color(boxColor).endVertex();
        tess.end();
        RenderSystem.lineWidth(1.0f);
    }

    /** 部署界面据点框渲染（屏幕空间正交投影）。调用前需已调用 beginScreenSpace。 */
    public static void renderDeployHud(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        if (capturePoints.isEmpty()) return;
        if (!com.battlelinesystem.client.gui.WaitHudOverlay.active) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        for (int i = 0; i < capturePoints.size(); i++) {
            MapConfig.CapturePoint cp = capturePoints.get(i);
            if (cp.zones == null || cp.zones.isEmpty()) continue;
            CaptureProgressData prog = progressMap.get(cp.name);
            if (prog != null && prog.locked) continue;

            BlockPos center = cp.getDisplayCenter();
            int[] screen = WorldHudUtils.project(center.getX() + 0.5, center.getY() + 16.0,
                    center.getZ() + 0.5, WorldHudUtils.getCamParams(mc));
            if (screen == null) continue;

            String text = String.valueOf((char)('A' + i));
            renderDeployBoxScreen(poseStack, bufferSource, font,
                    screen[0], screen[1], text, cp, prog);
        }
    }

    /** @deprecated 保留旧 render() 兼容性 */
    @Deprecated
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        renderWorld(poseStack, bufferSource, camera);
        if (com.battlelinesystem.client.gui.WaitHudOverlay.active) {
            WorldHudUtils.beginScreenSpace(Minecraft.getInstance(), bufferSource);
            renderDeployHud(poseStack, bufferSource, camera);
            WorldHudUtils.endScreenSpace(Minecraft.getInstance(), bufferSource);
        }
    }

    /** 部署界面据点框（屏幕空间） */
    private static void renderDeployBoxScreen(PoseStack poseStack, MultiBufferSource bufferSource,
                                               Font font, int sx, int sy,
                                               String text, MapConfig.CapturePoint cp,
                                               CaptureProgressData prog) {
        boolean isSelected = cp.name.equals(
                com.battlelinesystem.client.SpawnPointRenderer.getSelectedCapturePointName());
        int boxColor = 0xFFFFFFFF, bgColor = 0xCC888888;
        if (isSelected) {
            boxColor = 0xFFFFFF00;
            bgColor = 0xCCFFAA00;
        } else if (prog != null) {
            if (prog.owner == 1) { boxColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE; bgColor = isOwnTeam(1) ? 0xCC4488FF : 0xCCFF4444; }
            else if (prog.owner == 2) { boxColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE; bgColor = isOwnTeam(2) ? 0xCC4488FF : 0xCCFF4444; }
            else if (prog.capturing != 0) { boxColor = 0xFFFF8800; bgColor = 0xCCCC6600; }
        }

        float scale = 0.7f;
        int w = font.width(text);
        float halfW = w / 2f;
        float textH = 8f;
        float pad = 4f;
        float halfBox = Math.max(halfW, textH / 2f) + pad;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        matrix.identity();
        matrix.translate(sx, sy, 0);
        matrix.scale(scale, scale, 1);

        // 背景
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, -halfBox, -halfBox, 0).color(bgColor).endVertex();
        builder.vertex(matrix, -halfBox, halfBox, 0).color(bgColor).endVertex();
        builder.vertex(matrix, halfBox, halfBox, 0).color(bgColor).endVertex();
        builder.vertex(matrix, halfBox, -halfBox, 0).color(bgColor).endVertex();
        tess.end();

        // 占领进度条（从底部向上填充，与HUD逻辑一致）
        if (prog != null) {
            float ratio;
            int fillColor;
            if (prog.progress < 0) {
                ratio = Math.abs(prog.progress);
                fillColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            } else if (prog.progress > 0) {
                ratio = Math.abs(prog.progress);
                fillColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            } else if (prog.owner == 1) {
                ratio = 1f;
                fillColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            } else if (prog.owner == 2) {
                ratio = 1f;
                fillColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            } else {
                ratio = 0f;
                fillColor = 0;
            }
            if (ratio > 0.001f) {
                float fillH = ratio * halfBox * 2f;
                float bottom = halfBox;
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                builder.vertex(matrix, -halfBox, bottom, 0).color(fillColor).endVertex();
                builder.vertex(matrix, -halfBox, bottom - fillH, 0).color(fillColor).endVertex();
                builder.vertex(matrix, halfBox, bottom - fillH, 0).color(fillColor).endVertex();
                builder.vertex(matrix, halfBox, bottom, 0).color(fillColor).endVertex();
                tess.end();
            }
        }

        // 边框
        RenderSystem.lineWidth(3.5f);
        builder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, -halfBox, -halfBox, 0).color(boxColor).endVertex();
        builder.vertex(matrix, halfBox, -halfBox, 0).color(boxColor).endVertex();
        builder.vertex(matrix, halfBox, halfBox, 0).color(boxColor).endVertex();
        builder.vertex(matrix, -halfBox, halfBox, 0).color(boxColor).endVertex();
        builder.vertex(matrix, -halfBox, -halfBox, 0).color(boxColor).endVertex();
        tess.end();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();

        font.drawInBatch(text, -halfW, -textH / 2f, 0xFFFFFFFF, false, matrix, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        poseStack.popPose();
    }

    private static int postDeployBg(CaptureProgressData prog) {
        return getBgColor(prog);
    }

    private static int postDeployLine(CaptureProgressData prog) {
        if (prog == null) return 0xFF888888;
        if (prog.owner == 1) return 0xFF4488FF;
        if (prog.owner == 2) return 0xFFFF4444;
        if (prog.capturing != 0) return 0xFFFF8800;
        return 0xFFAAAAAA;
    }

    // ====== 选区棒可视化线框 ======

    /**
     * 渲染选区棒选中的区域线框（半透明绿色立方体）
     */
    public static void renderSelectionBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Camera camera, net.minecraft.core.BlockPos pos1,
                                           net.minecraft.core.BlockPos pos2) {
        if (pos1 == null || pos2 == null) return;
        Vec3 camPos = camera.getPosition();

        int x1 = Math.min(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ());
        int x2 = Math.max(pos1.getX(), pos2.getX()) + 1;
        int y2 = Math.max(pos1.getY(), pos2.getY()) + 1;
        int z2 = Math.max(pos1.getZ(), pos2.getZ()) + 1;

        double dx = x1 - camPos.x;
        double dy = y1 - camPos.y;
        double dz = z1 - camPos.z;
        double sx = x2 - x1;
        double sy = y2 - y1;
        double sz = z2 - z1;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);

        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        // 半透明绿色填充面
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int fillColor = 0x3000FF00; // 低透明度绿色
        // 底面
        builder.vertex(matrix, 0, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, 0, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, 0, 0, (float)sz).color(fillColor).endVertex();
        // 顶面
        builder.vertex(matrix, 0, (float)sy, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, 0, (float)sy, (float)sz).color(fillColor).endVertex();
        // 前面
        builder.vertex(matrix, 0, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, 0).color(fillColor).endVertex();
        builder.vertex(matrix, 0, (float)sy, 0).color(fillColor).endVertex();
        // 后面
        builder.vertex(matrix, 0, 0, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, 0, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, 0, (float)sy, (float)sz).color(fillColor).endVertex();
        // 左面
        builder.vertex(matrix, 0, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, 0, 0, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, 0, (float)sy, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, 0, (float)sy, 0).color(fillColor).endVertex();
        // 右面
        builder.vertex(matrix, (float)sx, 0, 0).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, 0, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, (float)sz).color(fillColor).endVertex();
        builder.vertex(matrix, (float)sx, (float)sy, 0).color(fillColor).endVertex();
        tesselator.end();

        // 绿色线框
        RenderSystem.lineWidth(2.5f);
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int lineColor = 0xFF00FF00;
        float fx = (float)sx, fy = (float)sy, fz = (float)sz;
        // 底框
        builder.vertex(matrix, 0, 0, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, 0, 0).color(lineColor).endVertex();
        // 顶框
        builder.vertex(matrix, 0, fy, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, fz).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, fy, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, fy, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, fy, 0).color(lineColor).endVertex();
        // 竖线
        builder.vertex(matrix, 0, 0, 0).color(lineColor).endVertex();
        builder.vertex(matrix, 0, fy, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, 0).color(lineColor).endVertex();
        builder.vertex(matrix, fx, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, fx, fy, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, 0, fz).color(lineColor).endVertex();
        builder.vertex(matrix, 0, fy, fz).color(lineColor).endVertex();
        tesselator.end();

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /** 渲染多边形选区棒的顶点和连线 */
    public static void renderPolygonWand(PoseStack poseStack, MultiBufferSource bufferSource,
                                          Camera camera, java.util.List<net.minecraft.core.BlockPos> points) {
        renderPolygonWand(poseStack, bufferSource, camera, points, 0xFFFF8800);
    }

    /** 渲染多边形，省略顶点连线，常用于禁区等闭合区域 */
    public static void renderPolygonWand(PoseStack poseStack, MultiBufferSource bufferSource,
                                          Camera camera, java.util.List<net.minecraft.core.BlockPos> points,
                                          int lineColor) {
        if (points == null || points.size() < 2) return;
        Vec3 camPos = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        // 连线
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f lineMatrix = poseStack.last().pose();

        RenderSystem.lineWidth(3.0f);
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < points.size(); i++) {
            BlockPos p1 = points.get(i);
            BlockPos p2 = points.get((i + 1) % points.size());
            float y1 = p1.getY() + 1.5f;
            float y2 = p2.getY() + 1.5f;
            builder.vertex(lineMatrix, p1.getX() + 0.5f, y1, p1.getZ() + 0.5f).color(lineColor).endVertex();
            builder.vertex(lineMatrix, p2.getX() + 0.5f, y2, p2.getZ() + 0.5f).color(lineColor).endVertex();
            builder.vertex(lineMatrix, p1.getX() + 0.5f, p1.getY(), p1.getZ() + 0.5f).color(lineColor).endVertex();
            builder.vertex(lineMatrix, p1.getX() + 0.5f, y1, p1.getZ() + 0.5f).color(lineColor).endVertex();
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    /** 渲染多个多边形的顶点和连线，共享单次 GL 状态 */
    public static void renderPolygonWands(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Camera camera, java.util.List<java.util.List<net.minecraft.core.BlockPos>> polygonList,
                                           int lineColor) {
        if (polygonList == null || polygonList.isEmpty()) return;
        Vec3 camPos = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f lineMatrix = poseStack.last().pose();

        RenderSystem.lineWidth(3.0f);
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (java.util.List<net.minecraft.core.BlockPos> points : polygonList) {
            if (points == null || points.size() < 2) continue;
            for (int i = 0; i < points.size(); i++) {
                BlockPos p1 = points.get(i);
                BlockPos p2 = points.get((i + 1) % points.size());
                float y1 = p1.getY() + 1.5f;
                float y2 = p2.getY() + 1.5f;
                builder.vertex(lineMatrix, p1.getX() + 0.5f, y1, p1.getZ() + 0.5f).color(lineColor).endVertex();
                builder.vertex(lineMatrix, p2.getX() + 0.5f, y2, p2.getZ() + 0.5f).color(lineColor).endVertex();
                builder.vertex(lineMatrix, p1.getX() + 0.5f, p1.getY(), p1.getZ() + 0.5f).color(lineColor).endVertex();
                builder.vertex(lineMatrix, p1.getX() + 0.5f, y1, p1.getZ() + 0.5f).color(lineColor).endVertex();
            }
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    // ====== 屏幕上方据点状态 HUD ======

    private static final int HUD_BOX_SIZE = 14;
    private static final int HUD_GAP = 10;
    private static final int HUD_TOP_MARGIN = 4;
    private static final int SCORE_GAP = 6;

    private static String teamAName = "A队";
    private static String teamBName = "B队";

    public static String getTeamAName() { return teamAName; }
    public static String getTeamBName() { return teamBName; }

    /** 阵营池缓存：阵营ID列表 */
    private static Set<String> cachedPoolA = new LinkedHashSet<>();
    private static Set<String> cachedPoolB = new LinkedHashSet<>();
    /** 阵营ID → 阵营名称 */
    private static final Map<String, String> factionNameMap = new LinkedHashMap<>();
    /** 战场边界缓存：只在 MapConfig 变更时重建，避免每帧 new ArrayList + new BlockPos */
    private static java.util.List<BlockPos> cachedBattlefieldBoundary = null;
    private static MapConfig lastBoundaryConfig = null;

    /** 获取战场边界点列表（带缓存，只在 MapConfig 变更时重建） */
    public static java.util.List<BlockPos> getCachedBattlefieldBoundary() {
        MapConfig cfg = com.battlelinesystem.faction.FactionManager.getInstance().getActiveMapConfig();
        if (cfg == null) { cachedBattlefieldBoundary = null; lastBoundaryConfig = null; return null; }
        if (cfg == lastBoundaryConfig) return cachedBattlefieldBoundary;
        lastBoundaryConfig = cfg;
        if (cfg.battlefieldBoundary.isEmpty()) { cachedBattlefieldBoundary = null; return null; }
        java.util.List<BlockPos> list = new java.util.ArrayList<>(cfg.battlefieldBoundary.size());
        for (int[] v : cfg.battlefieldBoundary) {
            list.add(new BlockPos(v[0], v[1], v[2]));
        }
        cachedBattlefieldBoundary = list;
        return list;
    }

    public static void setTeamNames(String a, String b) {
        teamAName = a;
        teamBName = b;
    }

    /** 缓存阵营池数据（PacketOpenFactionVote 收到时调用） */
    public static void cacheFactionPools(java.util.List<com.battlelinesystem.faction.FactionConfig> factions,
                                          java.util.List<String> poolA, java.util.List<String> poolB) {
        cachedPoolA.clear();
        cachedPoolB.clear();
        factionNameMap.clear();
        if (poolA != null) cachedPoolA.addAll(poolA);
        if (poolB != null) cachedPoolB.addAll(poolB);
        if (factions != null) {
            for (var f : factions) {
                factionNameMap.put(f.id, f.name);
            }
        }
        // 刷新队伍名称
        refreshTeamNames();
    }

    /** 当玩家选择了阵营后调用，根据阵营ID确定队伍名称 */
    public static void onLocalFactionSelected(String factionId) {
        refreshTeamNames();
    }

    /** 判断阵营属于哪个队伍: 0=A, 1=B, -1=未知 */
    public static int getTeamForFaction(String factionId) {
        if (cachedPoolA.contains(factionId)) return 0;
        if (cachedPoolB.contains(factionId)) return 1;
        return -1;
    }

    private static void refreshTeamNames() {
        // A队名称：取第一个阵营池中的第一个阵营名
        for (String id : cachedPoolA) {
            String name = factionNameMap.get(id);
            if (name != null) { teamAName = name; break; }
        }
        // B队名称
        for (String id : cachedPoolB) {
            String name = factionNameMap.get(id);
            if (name != null) { teamBName = name; break; }
        }
        // 同步到 CapturePointManager 供服务端读取
        com.battlelinesystem.game.CapturePointManager.setTeamNames(teamAName, teamBName);
    }

    /**
     * 在屏幕正上方渲染：分数 → 据点状态条 + 剩余时间 → 菱形框占领进度。
     */
    public static void renderCapturePointHud(net.minecraft.client.gui.GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int scoreA = syncedScoreA;
        int scoreB = syncedScoreB;

        if (capturePoints.isEmpty()) return;
        int count = capturePoints.size();
        int totalW = count * HUD_BOX_SIZE + (count - 1) * HUD_GAP;
        int startX = (screenW - totalW) / 2;

        int scoreY = HUD_TOP_MARGIN + 3;      // 分数行文字基线
        int boxY = HUD_TOP_MARGIN + 16;       // 据点框 Y（分数下方）

        // 收集每个据点的渲染参数
        float[] cpRatios = new float[count];
        int[] cpColors = new int[count];
        for (int i = 0; i < count; i++) {
            CaptureProgressData prog = progressMap.get(capturePoints.get(i).name);
            if (prog != null) {
                cpRatios[i] = Math.abs(prog.progress);
                if (prog.progress < 0) cpColors[i] = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                else if (prog.progress > 0) cpColors[i] = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
                else if (prog.owner == 1) { cpColors[i] = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE; cpRatios[i] = 1f; }
                else if (prog.owner == 2) { cpColors[i] = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE; cpRatios[i] = 1f; }
            }
        }

        int activeIndex = -1;
        CaptureProgressData activeProg = null;
        for (int i = 0; i < count; i++) {
            CaptureProgressData p = progressMap.get(capturePoints.get(i).name);
            if (p != null && p.capturing > 0 && isLocalPlayerInZone(capturePoints.get(i))) {
                activeIndex = i; activeProg = p; break;
            }
        }

        // 时间文本
        String timeText = "";
        int timeColor = 0xFFFFFF;
        if (syncedTimeLimit > 0) {
            int remaining = Math.max(0, syncedTimeLimit * 60 - syncedElapsed);
            int mm = remaining / 60, ss = remaining % 60;
            timeText = String.format("%d:%02d", mm, ss);
            timeColor = remaining < 10 ? 0xFFFF4444 : (remaining < 30 ? 0xFFFFAA00 : 0xFFFFFFFF);
        }

        // ====== Phase 1: fill quads ======
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f guiMatrix = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int bx = startX;
        for (int i = 0; i < count; i++) {
            addFillQuad(buf, guiMatrix, bx, boxY, bx + HUD_BOX_SIZE, boxY + HUD_BOX_SIZE, 0x80000000);
            if (cpRatios[i] > 0.001f && cpColors[i] != 0) {
                int fh = (int)(HUD_BOX_SIZE * cpRatios[i]);
                addFillQuad(buf, guiMatrix, bx, boxY + HUD_BOX_SIZE - fh, bx + HUD_BOX_SIZE, boxY + HUD_BOX_SIZE, cpColors[i]);
            }
            bx += HUD_BOX_SIZE + HUD_GAP;
        }

        int boxesY = boxY + HUD_BOX_SIZE + 8;
        if (activeIndex >= 0) {
            String dn = String.valueOf((char)('A' + activeIndex));
            int boxSize = Math.max(font.width(dn) + 8, 25);
            int boxLeft = screenW / 2 - boxSize / 2;
            int fbColor;
            if (activeProg.progress < 0) fbColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            else if (activeProg.progress > 0) fbColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            else if (activeProg.owner == 1) fbColor = isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            else if (activeProg.owner == 2) fbColor = isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE;
            else fbColor = 0xFFFF8800;
            addFillQuad(buf, guiMatrix, boxLeft, boxesY, boxLeft + boxSize, boxesY + boxSize, 0x80000000);
            float fr = Math.abs(activeProg.progress);
            if (fr > 0.001f) {
                int fh = (int)(boxSize * fr);
                addFillQuad(buf, guiMatrix, boxLeft, boxesY + boxSize - fh, boxLeft + boxSize, boxesY + boxSize, fbColor);
            }
        }

        BufferBuilder.RenderedBuffer rendered = buf.end();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(rendered);

        // ====== Phase 2: text ======
        // 分数行
        int scoreBaseline = scoreY;
        String aText = teamAName + "  " + scoreA;
        g.drawString(font, aText, startX - font.width(aText) - SCORE_GAP, scoreBaseline,
                isOwnTeam(1) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE);
        String bText = scoreB + "  " + teamBName;
        g.drawString(font, bText, startX + totalW + SCORE_GAP, scoreBaseline,
                isOwnTeam(2) ? OWN_COLOR_OPAQUE : ENEMY_COLOR_OPAQUE);

        // 据点框标签
        bx = startX;
        for (int i = 0; i < count; i++) {
            String label = String.valueOf((char)('A' + i));
            int tw = font.width(label);
            g.drawString(font, label, bx + (HUD_BOX_SIZE - tw) / 2, boxY + (HUD_BOX_SIZE - 8) / 2, 0xFFFFFFFF);
            bx += HUD_BOX_SIZE + HUD_GAP;
        }

        // 剩余时间（居中）
        if (!timeText.isEmpty()) {
            int tw = font.width(timeText);
            g.drawString(font, timeText, screenW / 2 - tw / 2, scoreBaseline, timeColor);
        }

        // 活跃菱形框
        if (activeIndex >= 0) {
            String dn = String.valueOf((char)('A' + activeIndex));
            int boxSize = Math.max(font.width(dn) + 8, 25);
            int textW2 = font.width(dn);
            g.drawString(font, dn, screenW / 2 - textW2 / 2, boxesY + (boxSize - 8) / 2, 0xFFFFFFFF);
            String countText = activeProg.teamACount + " : " + activeProg.teamBCount;
            int ctW = font.width(countText);
            g.drawString(font, countText, screenW / 2 - ctW / 2, boxesY + boxSize + 4, 0xFFFFFFFF);
        }
    }

    /** 检查本地玩家是否在指定据点的任意区域内 */
    private static boolean isLocalPlayerInZone(MapConfig.CapturePoint cp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cp.zones == null) return false;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (int[][] z : cp.zones) {
            int x1 = Math.min(z[0][0], z[1][0]);
            int x2 = Math.max(z[0][0], z[1][0]) + 1;
            int y1 = Math.min(z[0][1], z[1][1]);
            int y2 = Math.max(z[0][1], z[1][1]) + 1;
            int z1 = Math.min(z[0][2], z[1][2]);
            int z2 = Math.max(z[0][2], z[1][2]) + 1;
            if (px >= x1 && px <= x2 && py >= y1 && py <= y2 && pz >= z1 && pz <= z2) {
                return true;
            }
        }
        return false;
    }

    /** 向 BufferBuilder 中添加一个填充矩形（顶点顺序与 GuiGraphics.fill 一致） */
    private static void addFillQuad(BufferBuilder buf, Matrix4f matrix, int x1, int y1, int x2, int y2, int color) {
        if (x1 > x2) { int t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { int t = y1; y1 = y2; y2 = t; }
        float fx1 = (float) x1, fy1 = (float) y1, fx2 = (float) x2, fy2 = (float) y2;
        buf.vertex(matrix, fx1, fy2, 0).color(color).endVertex();
        buf.vertex(matrix, fx2, fy2, 0).color(color).endVertex();
        buf.vertex(matrix, fx2, fy1, 0).color(color).endVertex();
        buf.vertex(matrix, fx1, fy1, 0).color(color).endVertex();
    }
}

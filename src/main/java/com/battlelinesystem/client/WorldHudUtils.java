package com.battlelinesystem.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 世界坐标 3D投影 + 悬浮框渲染 共用工具。
 *
 * 所有悬浮框渲染使用屏幕空间正交投影，与 hitTest 共用同一套 project() 坐标。
 * 各渲染器在 render() 入口调用 beginScreenSpace/endScreenSpace 包围整帧渲染。
 */
public final class WorldHudUtils {

    private WorldHudUtils() {}

    /** 复用的投影结果数组，避免每帧数十次 new int[2] */
    private static final int[] PROJECT_BUF = new int[2];
    /** 复用的正交投影矩阵，避免每帧 new Matrix4f */
    private static final Matrix4f ORTHO_BUF = new Matrix4f();

    // ======================== 摄像机参数 ========================

    public record CamParams(
            Vec3 eye,
            double lx, double ly, double lz,
            double lex, double ley, double lez,
            double ux, double uy, double uz,
            double tanHalfFov, double aspect,
            int screenW, int screenH
    ) {}

    public static CamParams getCamParams(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        org.joml.Vector3f lookF = camera.getLookVector();
        org.joml.Vector3f leftF = camera.getLeftVector();
        org.joml.Vector3f upF = camera.getUpVector();

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double fovR = Math.toRadians(mc.options.fov().get());
        double tanHalfFov = Math.tan(fovR / 2.0);
        double aspect = (double) sw / sh;

        return new CamParams(eye,
                lookF.x(), lookF.y(), lookF.z(),
                leftF.x(), leftF.y(), leftF.z(),
                upF.x(), upF.y(), upF.z(),
                tanHalfFov, aspect, sw, sh);
    }

    // ======================== 3D → 2D 投影 ========================

    public static int[] project(double wx, double wy, double wz, CamParams cp) {
        double dx = wx - cp.eye.x, dy = wy - cp.eye.y, dz = wz - cp.eye.z;
        double xv = -(dx * cp.lex + dy * cp.ley + dz * cp.lez);
        double yv =  dx * cp.ux + dy * cp.uy + dz * cp.uz;
        double zv = -(dx * cp.lx  + dy * cp.ly  + dz * cp.lz);
        if (zv >= -0.01) return null;

        double xNdc = xv / (-zv * cp.tanHalfFov * cp.aspect);
        double yNdc = yv / (-zv * cp.tanHalfFov);
        int sx = (int) ((xNdc + 1.0) / 2.0 * cp.screenW);
        int sy = (int) ((1.0 - yNdc) / 2.0 * cp.screenH);
        if (sx < -100 || sx > cp.screenW + 100 || sy < -100 || sy > cp.screenH + 100) return null;
        PROJECT_BUF[0] = sx;
        PROJECT_BUF[1] = sy;
        return PROJECT_BUF;
    }

    public static int[] project(BlockPos pos, CamParams cp) {
        return project(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cp);
    }

    public static double sqDist(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    // ======================== 投影切换（保护共享 BufferSource） ========================

    /** 切换到屏幕空间正交投影。先 flush 已有的排队绘制（保护其他渲染器的内容）。 */
    public static void beginScreenSpace(Minecraft mc, MultiBufferSource bufferSource) {
        flushBuffer(bufferSource);
        RenderSystem.backupProjectionMatrix();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        ORTHO_BUF.setOrtho(0, sw, sh, 0, -100, 100);
        RenderSystem.setProjectionMatrix(ORTHO_BUF, VertexSorting.ORTHOGRAPHIC_Z);
    }

    /** 恢复透视投影。先 flush 正交投影下的文字，再恢复。 */
    public static void endScreenSpace(Minecraft mc, MultiBufferSource bufferSource) {
        flushBuffer(bufferSource);
        RenderSystem.restoreProjectionMatrix();
    }

    public static void flushBuffer(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    // ======================== 屏幕空间悬浮框渲染 ========================

    /** 渲染文字悬浮框。调用前需已进入屏幕空间正交投影（beginScreenSpace）。 */
    public static int[] renderBillboardBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Camera camera, Font font,
                                           double worldX, double worldY, double worldZ,
                                           Vec3 camPos, String text,
                                           int boxColor, int bgColor) {
        return renderBillboardBox(poseStack, bufferSource, camera, font,
                worldX, worldY, worldZ, camPos, text, boxColor, bgColor, 0.7f);
    }

    public static int[] renderBillboardBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Camera camera, Font font,
                                           double worldX, double worldY, double worldZ,
                                           Vec3 camPos, String text,
                                           int boxColor, int bgColor, float scale) {
        Minecraft mc = Minecraft.getInstance();
        int[] screen = project(worldX, worldY, worldZ, getCamParams(mc));
        if (screen == null) return null;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        matrix.identity();
        matrix.translate(screen[0], screen[1], 0);
        matrix.scale(scale, scale, 1);

        int w = font.width(text);
        float halfW = w / 2f;
        float textH = 8f;
        float pad = 4f;
        float halfBox = Math.max(halfW, textH / 2f) + pad;
        drawBillboardQuad(matrix, -halfBox, -halfBox, halfBox, halfBox, bgColor, boxColor);
        font.drawInBatch(text, -halfW, -textH / 2f, 0xFFFFFFFF, false, matrix, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        poseStack.popPose();
        return screen;
    }

    // ======================== 屏幕空间头像框渲染 ========================

    private static final float FACE_U0 = 8f / 64f, FACE_V0 = 8f / 64f;
    private static final float FACE_U1 = 16f / 64f, FACE_V1 = 16f / 64f;

    public static int[] renderBillboardFace(PoseStack poseStack, MultiBufferSource bufferSource,
                                             Camera camera, Font font,
                                             double worldX, double worldY, double worldZ,
                                             Vec3 camPos, String name,
                                             int boxColor, int bgColor, float scale,
                                             ResourceLocation skinTexture) {
        Minecraft mc = Minecraft.getInstance();
        int[] screen = project(worldX, worldY, worldZ, getCamParams(mc));
        if (screen == null) return null;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        matrix.identity();
        matrix.translate(screen[0], screen[1], 0);
        matrix.scale(scale, scale, 1);

        float faceHalf = 8f, pad = 3f;
        float halfBox = faceHalf + pad;
        drawBillboardQuad(matrix, -halfBox, -halfBox, halfBox, halfBox, bgColor, boxColor);

        // 头像纹理
        RenderSystem.setShaderTexture(0, skinTexture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, -faceHalf, -faceHalf, 0).uv(FACE_U0, FACE_V0).endVertex();
        builder.vertex(matrix, -faceHalf,  faceHalf, 0).uv(FACE_U0, FACE_V1).endVertex();
        builder.vertex(matrix,  faceHalf,  faceHalf, 0).uv(FACE_U1, FACE_V1).endVertex();
        builder.vertex(matrix,  faceHalf, -faceHalf, 0).uv(FACE_U1, FACE_V0).endVertex();
        tess.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        int nameW = font.width(name);
        font.drawInBatch(name, -nameW / 2f, faceHalf + pad + 1, 0xFFFFFFFF, false,
                matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);

        poseStack.popPose();
        return screen;
    }

    // ======================== 通用框绘制 ========================

    public static void drawBillboardQuad(Matrix4f matrix,
                                           float x0, float y0, float x1, float y1,
                                           int bgColor, int boxColor) {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
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
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}

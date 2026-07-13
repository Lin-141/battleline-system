package com.battlelinesystem.client;

import com.battlelinesystem.client.gui.ClassSelectScreen;
import com.battlelinesystem.client.gui.WaitHudOverlay;
import com.battlelinesystem.items.PolygonWandItem;
import com.battlelinesystem.items.SelectionWandItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端专用初始化与事件处理。仅在物理客户端上加载。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(Dist.CLIENT)
public class ClientSetup {

    public static final KeyMapping KEY_OPEN_WAIT = new KeyMapping(
            "key.battlelinesystem.wait",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.battlelinesystem");

    /**
     * 由 BattleLineSystem 构造器在 Dist.CLIENT 上调用
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(KEY_OPEN_WAIT);
        });
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
    }

    public static class ClientEventHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (KEY_OPEN_WAIT.consumeClick()) {
                if (WaitHudOverlay.allowManualToggle) {
                    WaitHudOverlay.active = !WaitHudOverlay.active;
                    if (WaitHudOverlay.active && mc.screen == null) {
                        if (WaitHudOverlay.hasClassOptions()) {
                            WaitHudOverlay.closeScreen = false;
                            mc.setScreen(new ClassSelectScreen());
                        }
                    }
                }
            }

            if (WaitHudOverlay.active) {
                WaitHudOverlay.tick();
            }
            WaitHudOverlay.tickFade();
            CapturePointRenderer.tickParticles();
            ViewCameraControl.tick(mc);
        }

        @SubscribeEvent
        public void onRenderHud(RenderGuiOverlayEvent.Post event) {
            // 只在 hotbar overlay 渲染一次，避免每个 overlay 类型都触发一次（hotbar、血量、护甲等十几种，会重复渲染10+次/帧）
            if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.screen instanceof ClassSelectScreen) return;
            WaitHudOverlay.render(event.getGuiGraphics(),
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            if (mc.screen == null) {
                CapturePointRenderer.renderCapturePointHud(event.getGuiGraphics(),
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
            }
        }

        @SubscribeEvent
        public void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.cameraEntity == null || mc.level == null) return;
            com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            boolean isGameDim = mc.level.dimension().location().getNamespace()
                    .equals(com.battlelinesystem.BattleLineSystem.MOD_ID);
            if (isGameDim) {
                // 3D 世界空间渲染（透视投影）
                CapturePointRenderer.renderWorld(poseStack, bufferSource, event.getCamera());
                // 屏幕空间 HUD 渲染（共用一次正交投影切换，减少 GL 状态变更）
                WorldHudUtils.beginScreenSpace(mc, bufferSource);
                CapturePointRenderer.renderDeployHud(poseStack, bufferSource, event.getCamera());
                SpawnPointRenderer.render(poseStack, bufferSource, event.getCamera());
                BeaconRenderer.render(poseStack, bufferSource, event.getCamera());
                LooseSpawnRenderer.render(poseStack, bufferSource, event.getCamera());
                WorldHudUtils.endScreenSpace(mc, bufferSource);
                // 渲染当前地图的战场边界（缓存 BlockPos 列表，避免每帧重建）
                java.util.List<net.minecraft.core.BlockPos> boundaryPoints =
                        CapturePointRenderer.getCachedBattlefieldBoundary();
                if (boundaryPoints != null) {
                    CapturePointRenderer.renderPolygonWand(poseStack, bufferSource, event.getCamera(), boundaryPoints);
                }
                // 渲染禁区（合并为单次 GL 状态）
                java.util.List<java.util.List<net.minecraft.core.BlockPos>> allFzPoints = new java.util.ArrayList<>();
                for (CapturePointRenderer.ForbiddenZoneData fz : CapturePointRenderer.getForbiddenZones()) {
                    if (!fz.boundary.isEmpty()) {
                        allFzPoints.add(fz.boundary);
                    }
                }
                if (!allFzPoints.isEmpty()) {
                    CapturePointRenderer.renderPolygonWands(poseStack, bufferSource, event.getCamera(),
                            allFzPoints, 0xFFFF0000);
                }
            }
            CapturePointRenderer.renderSelectionBox(poseStack, bufferSource, event.getCamera(),
                    SelectionWandItem.getClientPos1(mc.player.getUUID()),
                    SelectionWandItem.getClientPos2(mc.player.getUUID()));
            CapturePointRenderer.renderPolygonWand(poseStack, bufferSource, event.getCamera(),
                    PolygonWandItem.getClientPoints(mc.player.getUUID()));
            bufferSource.endBatch();
        }

        @SubscribeEvent
        public void onClientLeftClick(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getLevel().isClientSide() && event.getEntity() != null) {
                var stack = event.getEntity().getItemInHand(event.getHand());
                if (stack.getItem() instanceof SelectionWandItem) {
                    event.setCanceled(true);
                    SelectionWandItem.setClientPos1(event.getEntity().getUUID(), event.getPos());
                }
                if (stack.getItem() instanceof PolygonWandItem) {
                    event.setCanceled(true);
                    PolygonWandItem.clientAddPoint(event.getEntity().getUUID(), event.getPos());
                }
            }
        }
    }
}

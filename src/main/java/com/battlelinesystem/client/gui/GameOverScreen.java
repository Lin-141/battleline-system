package com.battlelinesystem.client.gui;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.client.CapturePointRenderer;
import com.battlelinesystem.network.packet.PacketGameOverResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 游戏结算画面 — 黑屏渐变淡入，展示两队 MVP（含玩家模型）、战绩统计
 */
public class GameOverScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;
    private static final int TEAM_A_COLOR = 0xFF4488FF;
    private static final int TEAM_B_COLOR = 0xFFFF4444;

    private final String winner;
    private final int scoreA, scoreB;
    private final String teamAName, teamBName;
    private final List<PacketGameOverResult.PlayerStatEntry> stats;

    // MVP: 按 captures*3 + kills - deaths 算总分最高的
    private PacketGameOverResult.PlayerStatEntry mvpA;
    private PacketGameOverResult.PlayerStatEntry mvpB;

    private final ScreenFadeUtil fade = new ScreenFadeUtil();
    private boolean statsVisible = false;

    public GameOverScreen(PacketGameOverResult data) {
        super(Component.literal("游戏结算"));
        this.winner = data.getWinner();
        this.scoreA = data.getScoreA();
        this.scoreB = data.getScoreB();
        this.teamAName = data.getTeamAName();
        this.teamBName = data.getTeamBName();
        this.stats = data.getStats();

        // 计算两队 MVP（取各队总分最高的玩家，含负分）
        PacketGameOverResult.PlayerStatEntry bestA = null, bestB = null;
        int bestAScore = Integer.MIN_VALUE, bestBScore = Integer.MIN_VALUE;
        for (PacketGameOverResult.PlayerStatEntry s : this.stats) {
            int total = s.captures * 3 + s.kills - s.deaths;
            if ("A".equals(s.team) && total > bestAScore) {
                bestAScore = total;
                bestA = s;
            }
            if ("B".equals(s.team) && total > bestBScore) {
                bestBScore = total;
                bestB = s;
            }
        }
        this.mvpA = bestA;
        this.mvpB = bestB;

        BattleLineSystem.LOGGER.info("[GameOver] 客户端创建 GameOverScreen winner={} statsCount={}",
                winner, stats.size());

        // 启动黑屏淡入
        fade.start(30, 30, 0, () -> statsVisible = true, () -> {});
    }

    public GameOverScreen(String winner, int scoreA, int scoreB) {
        super(Component.literal("游戏结算"));
        this.winner = winner;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.teamAName = CapturePointRenderer.getTeamAName();
        this.teamBName = CapturePointRenderer.getTeamBName();
        this.stats = new ArrayList<>();
    }

    @Override
    protected void init() {
        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(
                Component.literal("返回"), btn -> {
                    if (this.minecraft != null) this.minecraft.setScreen(null);
                })
                .pos(this.width / 2 - 30, btnY)
                .size(60, 20)
                .build());
    }

    @Override
    public void tick() {
        fade.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        if (!statsVisible) {
            fade.render(g, this.width, this.height);
            return;
        }

        int cx = this.width / 2;
        int y = 20;

        // ---- 胜利标题 ----
        String winLabel = "获胜方: " + teamNameFor(winner) + "  " + scoreA + ":" + scoreB;
        g.drawCenteredString(this.font, winLabel, cx, y, TITLE_COLOR);
        y += 24;

        // ---- MVP 行 ----
        int mvpY = y;
        int leftX = cx - 120;
        int rightX = cx + 120;

        // A 队 MVP
        if (mvpA != null) {
            g.drawCenteredString(this.font, teamAName + " MVP", leftX, mvpY - 12, TEAM_A_COLOR);
            renderPlayerModel(g, leftX, mvpY + 60, 40, mvpA.uuid);
            g.drawCenteredString(this.font, mvpA.name, leftX, mvpY + 82, 0xFFFFFF);
            g.drawCenteredString(this.font, "占点:" + mvpA.captures + " 击杀:" + mvpA.kills
                    + " 死亡:" + mvpA.deaths, leftX, mvpY + 94, 0xAAAAAA);
        } else {
            g.drawCenteredString(this.font, teamAName + " (无玩家)", leftX, mvpY + 30, 0x555555);
        }

        // B 队 MVP
        if (mvpB != null) {
            g.drawCenteredString(this.font, teamBName + " MVP", rightX, mvpY - 12, TEAM_B_COLOR);
            renderPlayerModel(g, rightX, mvpY + 60, 40, mvpB.uuid);
            g.drawCenteredString(this.font, mvpB.name, rightX, mvpY + 82, 0xFFFFFF);
            g.drawCenteredString(this.font, "占点:" + mvpB.captures + " 击杀:" + mvpB.kills
                    + " 死亡:" + mvpB.deaths, rightX, mvpY + 94, 0xAAAAAA);
        } else {
            g.drawCenteredString(this.font, teamBName + " (无玩家)", rightX, mvpY + 30, 0x555555);
        }

        y = mvpY + 120;

        // ---- 当前玩家战绩 ----
        g.drawCenteredString(this.font, "--- 我的战绩 ---", cx, y, 0xFFAA00);
        y += 18;

        UUID myUuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        PacketGameOverResult.PlayerStatEntry myStats = null;
        if (myUuid != null) {
            for (PacketGameOverResult.PlayerStatEntry s : stats) {
                if (myUuid.equals(s.uuid)) { myStats = s; break; }
            }
        }

        int tableX = cx - 130;
        if (myStats != null) {
            int teamColor = "A".equals(myStats.team) ? TEAM_A_COLOR : TEAM_B_COLOR;
            double kd = myStats.deaths == 0 ? myStats.kills : (double) myStats.kills / myStats.deaths;
            String kdStr = String.format("%.1f", kd);

            g.drawString(this.font, "队伍: " + ("A".equals(myStats.team) ? teamAName : teamBName),
                    tableX, y, teamColor);
            g.drawString(this.font, "占点: " + myStats.captures + "  击杀: " + myStats.kills
                    + "  死亡: " + myStats.deaths + "  K/D: " + kdStr,
                    tableX, y + 14, 0xFFFFFF);
        } else {
            g.drawCenteredString(this.font, "无记录", cx, y, 0x555555);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 渲染玩家 3D 模型（使用 EntityRenderDispatcher 绘制到 GUI）。
     *  旁观者模式下玩家 invisible，渲染前临时取消隐身。 */
    private void renderPlayerModel(GuiGraphics g, int centerX, int bottomY, int size, UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Player player = mc.level.getPlayerByUUID(uuid);
        if (player == null) return;

        float scale = size / 1.8f;

        g.pose().pushPose();
        g.pose().translate(centerX, bottomY, 105f);
        g.pose().scale(scale, -scale, scale);

        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(160));
        g.pose().mulPose(rot);

        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        // 旁观者/观察者的玩家被标记为 invisible，渲染前临时取消，避免模型不显示
        boolean wasInvisible = player.isInvisible();
        if (wasInvisible) player.setInvisible(false);

        try {
            MultiBufferSource.BufferSource bufSource = g.bufferSource();
            dispatcher.render(player, 0, 0, 0, 0, 1f, g.pose(), bufSource, 0xF000F0);
            g.flush();
        } catch (Exception e) {
            BattleLineSystem.LOGGER.warn("Failed to render MVP model for {}", player.getName().getString(), e);
        } finally {
            if (wasInvisible) player.setInvisible(true);
        }

        g.pose().popPose();
    }

    private String teamNameFor(String team) {
        return "A".equals(team) ? teamAName : teamBName;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        super.onClose();
        // 确保淡入完成时正常关闭
    }
}

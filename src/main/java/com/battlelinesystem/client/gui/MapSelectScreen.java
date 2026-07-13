package com.battlelinesystem.client.gui;

import com.battlelinesystem.game.GameModeManager;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketSelectMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图选择界面 - 游戏模式选择
 * 首次有人选择模式后启动30秒倒计时，
 * 倒计时结束后自动跳转到胜出模式的地图选择界面。
 */
public class MapSelectScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;

    /** 模式按钮区域参数 */
    private static final int BTN_WIDTH = 100;
    private static final int BTN_HEIGHT = 22;
    private static final int BTN_GAP = 8;
    private static final int BTN_LEFT = 18;

    /** 右侧面板参数 */
    private static final int PANEL_LEFT = 175;
    private static final int PANEL_TOP = 50;
    private static final int PANEL_RIGHT_PAD = 18;
    private static final int PANEL_BOTTOM = 55;

    /** 设置按钮参数 */
    private static final int SETTINGS_BTN_WIDTH = 50;
    private static final int SETTINGS_BTN_HEIGHT = 20;
    private static final int SETTINGS_BTN_RIGHT_PAD = 12;
    private static final int SETTINGS_BTN_BOTTOM_PAD = 12;

    /** 是否为OP玩家 */
    private final boolean isOp;

    /** 各模式当前选择人数 */
    private int[] modeCounts;

    /** 倒计时剩余秒数（服务端传入），-1=未启动 */
    private int countdownSeconds;

    /** 存储所有模式按钮及对应描述 */
    private final List<ModeButton> modeButtons = new ArrayList<>();

    /** 当前鼠标悬停的模式按钮 */
    private ModeButton hoveredMode = null;

    public MapSelectScreen(boolean isOp, int[] modeCounts, int countdownSeconds) {
        super(Component.literal("地图选择"));
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
    }

    /** 刷新人数和倒计时，不重建整个界面 */
    public void refresh(int[] counts, int newCountdown) {
        this.modeCounts = counts;
        this.countdownSeconds = newCountdown;
    }

    @Override
    protected void init() {
        super.init();
        this.modeButtons.clear();

        int btnStartY = 55;

        addModeButton(btnStartY, 0, "征服", GameModeManager.MODE_DESCS[0]);
        addModeButton(btnStartY, 1, "突破", GameModeManager.MODE_DESCS[1]);
        addModeButton(btnStartY, 2, "生存", GameModeManager.MODE_DESCS[2]);
        addModeButton(btnStartY, 3, "大规模行动", GameModeManager.MODE_DESCS[3]);

        // 设置按钮（仅OP可见）
        if (this.isOp) {
            int btnX = this.width - SETTINGS_BTN_RIGHT_PAD - SETTINGS_BTN_WIDTH;
            int btnY = this.height - SETTINGS_BTN_BOTTOM_PAD - SETTINGS_BTN_HEIGHT;
            this.addRenderableWidget(Button.builder(Component.literal("设置"), btn -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new SettingsScreen(
                                    this.isOp, this.modeCounts, this.countdownSeconds));
                        }
                    })
                    .pos(btnX, btnY)
                    .size(SETTINGS_BTN_WIDTH, SETTINGS_BTN_HEIGHT)
                    .build());
        }
    }

    private void addModeButton(int startY, int index, String label, String desc) {
        int btnY = startY + index * (BTN_HEIGHT + BTN_GAP);
        Button btn = Button.builder(Component.literal(label), b -> {
                    AllPackets.getChannel().sendToServer(new PacketSelectMode(label));
                })
                .pos(BTN_LEFT, btnY)
                .size(BTN_WIDTH, BTN_HEIGHT)
                .build();
        this.modeButtons.add(new ModeButton(btn, desc));
        this.addRenderableWidget(btn);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // ---- 标题 ----
        graphics.drawCenteredString(this.font, "选择游戏模式", this.width / 2, 15, TITLE_COLOR);

        // ---- 副标题：总人数 + 倒计时 ----
        int total = 0;
        for (int c : this.modeCounts) total += c;

        String subtitle;
        if (this.countdownSeconds >= 0) {
            subtitle = "参与: " + total + "人  剩余 " + this.countdownSeconds + " 秒";
        } else if (total > 0) {
            subtitle = "参与: " + total + "人  等待倒计时...";
        } else {
            subtitle = "当前参与人数: " + total;
        }
        graphics.drawCenteredString(this.font, subtitle, this.width / 2, 32, 0xAAAAAA);

        // ---- 左侧按钮区域背景 ----
        graphics.fill(BTN_LEFT - 4, BTN_LEFT - 4,
                BTN_LEFT + BTN_WIDTH + 48, BTN_LEFT - 4 + 4 * BTN_HEIGHT + 3 * BTN_GAP + 4,
                0x30000000);

        // ---- 右侧描述面板背景 ----
        int panelRight = this.width - PANEL_RIGHT_PAD;
        int panelBottom = this.height - PANEL_BOTTOM;
        graphics.fill(PANEL_LEFT, PANEL_TOP, panelRight, panelBottom, 0x40000000);

        // ---- 鼠标悬停检测 ----
        this.hoveredMode = null;
        for (ModeButton mb : this.modeButtons) {
            if (isMouseOver(mb.button.getX(), mb.button.getY(),
                    mb.button.getWidth(), mb.button.getHeight(), mouseX, mouseY)) {
                this.hoveredMode = mb;
                break;
            }
        }

        // ---- 右侧面板内容 ----
        if (this.hoveredMode != null) {
            drawWrappedString(graphics, this.hoveredMode.description,
                    PANEL_LEFT + 10, PANEL_TOP + 10, panelRight - PANEL_LEFT - 20, 0xFFDD88);
        } else {
            graphics.drawCenteredString(this.font, "← 将鼠标移到左侧按钮查看模式详情",
                    (PANEL_LEFT + panelRight) / 2, PANEL_TOP + 30, 0x666666);
        }

        // ---- 按钮右侧显示人数 ----
        int btnStartY = 55;
        for (int i = 0; i < modeButtons.size(); i++) {
            int btnY = btnStartY + i * (BTN_HEIGHT + BTN_GAP);
            int countX = BTN_LEFT + BTN_WIDTH + 8;
            int countY = btnY + (BTN_HEIGHT - this.font.lineHeight) / 2;
            String countStr = "(" + this.modeCounts[i] + "/" + total + ")";
            int countColor = this.modeCounts[i] > 0 ? 0xFF55FF55 : 0xFF888888;
            graphics.drawString(this.font, countStr, countX, countY, countColor, false);
        }

        // ---- 大号倒计时数字（倒计时中时居中显示） ----
        if (this.countdownSeconds > 0 && this.countdownSeconds <= 10) {
            String bigNum = String.valueOf(this.countdownSeconds);
            int numColor = this.countdownSeconds <= 3 ? 0xFFFF4444 : 0xFFFFFF44;
            // 用 textRenderer 放大效果：绘制阴影叠字
            float scale = 3.0f;
            int tx = (int) ((this.width / 2f) / scale);
            int ty = (int) (((this.height / 2f + 20) / scale));
            graphics.pose().pushPose();
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawCenteredString(this.font, bigNum, tx, ty, numColor);
            graphics.pose().popPose();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isMouseOver(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawWrappedString(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), x, y + i * 12, color, false);
        }
    }

    private static class ModeButton {
        final Button button;
        final String description;

        ModeButton(Button button, String description) {
            this.button = button;
            this.description = description;
        }
    }
}

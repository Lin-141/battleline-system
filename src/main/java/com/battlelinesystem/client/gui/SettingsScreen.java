package com.battlelinesystem.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 设置界面 - 管理员编辑配置的二级界面
 */
public class SettingsScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;

    private static final int BTN_WIDTH = 120;
    private static final int BTN_HEIGHT = 20;
    private static final int BTN_GAP = 8;

    private final boolean isOp;
    private final int[] modeCounts;
    private final int countdownSeconds;

    public SettingsScreen(boolean isOp, int[] modeCounts, int countdownSeconds) {
        super(Component.literal("管理设置"));
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 55;

        // 地图设置按钮
        this.addRenderableWidget(Button.builder(Component.literal("地图设置"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MapSettingsScreen(
                                this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(centerX - BTN_WIDTH / 2, startY)
                .size(BTN_WIDTH, BTN_HEIGHT)
                .build());

        // 阵营设置按钮
        this.addRenderableWidget(Button.builder(Component.literal("阵营设置"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new FactionSettingsScreen(
                                this.isOp, this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(centerX - BTN_WIDTH / 2, startY + BTN_HEIGHT + BTN_GAP)
                .size(BTN_WIDTH, BTN_HEIGHT)
                .build());

        // 返回按钮
        int btnX = this.width - 62;
        int btnY = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MapSelectScreen(
                                this.isOp, this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(btnX, btnY)
                .size(50, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, "管理设置", this.width / 2, 15, TITLE_COLOR);
        graphics.drawCenteredString(this.font, "在此处管理地图和游戏配置",
                this.width / 2, this.height / 2 + 40, 0x666666);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

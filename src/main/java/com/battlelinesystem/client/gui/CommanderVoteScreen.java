package com.battlelinesystem.client.gui;

import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketCommanderVote;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 指挥官投票界面 — 显示同队所有玩家，每人投一票
 * 30秒倒计时，票数最高者成为本局指挥官
 */
public class CommanderVoteScreen extends Screen {

    private static final int CARD_W = 180;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int MAX_VISIBLE = 12;

    private List<String> playerNames = List.of();
    private String team; // "A" or "B"
    /** 倒计时由服务端推送，客户端不自行递减 */
    private int countdownSeconds = 10;
    private String votedFor = null; // 已投给的玩家名
    private boolean hasVoted = false;

    /** 各卡片判定框: [x0, y0, x1, y1]  */
    private final int[][] cardRects = new int[MAX_VISIBLE][4];
    private int listStartX, listStartY, totalListH;

    public CommanderVoteScreen(List<String> playerNames, String team, int countdownSeconds) {
        super(Component.literal("指挥官投票"));
        this.playerNames = playerNames;
        this.team = team;
        this.countdownSeconds = countdownSeconds;
    }

    /** 刷新倒计时（服务端推送） */
    public void refreshCountdown(int sec) { this.countdownSeconds = sec; }

    @Override
    protected void init() {
        super.init();
        int visible = Math.min(playerNames.size(), MAX_VISIBLE);
        totalListH = visible * CARD_H + (visible - 1) * CARD_GAP;
        listStartX = (this.width - CARD_W) / 2;
        listStartY = (this.height - totalListH) / 2 + 20;
        for (int i = 0; i < visible; i++) {
            int y = listStartY + i * (CARD_H + CARD_GAP);
            cardRects[i][0] = listStartX;
            cardRects[i][1] = y;
            cardRects[i][2] = listStartX + CARD_W;
            cardRects[i][3] = y + CARD_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasVoted) return true;

        int visible = Math.min(playerNames.size(), MAX_VISIBLE);
        for (int i = 0; i < visible; i++) {
            if (mouseX >= cardRects[i][0] && mouseX <= cardRects[i][2]
                    && mouseY >= cardRects[i][1] && mouseY <= cardRects[i][3]) {
                voteFor(playerNames.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void voteFor(String targetName) {
        hasVoted = true;
        votedFor = targetName;
        AllPackets.getChannel().sendToServer(new PacketCommanderVote(targetName));
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int centerX = this.width / 2;

        // 标题
        String teamName = "A".equals(team) ? "§bA队" : "§cB队";
        g.drawCenteredString(this.font, teamName + " §f— 指挥官投票",
                centerX, 20, 0xFFFFFF);

        // 副标题
        g.drawCenteredString(this.font, "§7请投票选出本局指挥官",
                centerX, 36, 0xAAAAAA);

        // 玩家列表
        if (playerNames.isEmpty()) {
            g.drawCenteredString(this.font, "§7暂无队友",
                    centerX, this.height / 2, 0x666666);
        } else {
            int visible = Math.min(playerNames.size(), MAX_VISIBLE);
            for (int i = 0; i < visible; i++) {
                String name = playerNames.get(i);
                int x0 = cardRects[i][0], y0 = cardRects[i][1];
                int x1 = cardRects[i][2], y1 = cardRects[i][3];

                boolean hovered = !hasVoted && mouseX >= x0 && mouseX <= x1
                        && mouseY >= y0 && mouseY <= y1;
                boolean isVoted = name.equals(votedFor);

                int bgColor;
                if (isVoted) {
                    bgColor = 0xCC44AA44; // 已投 - 绿色
                } else if (hovered) {
                    bgColor = 0xCC555555; // 悬停 - 浅灰
                } else {
                    bgColor = 0xCC333333; // 默认 - 深灰
                }

                g.fill(x0, y0, x1, y1, bgColor);
                // 左边色条
                int barColor = "A".equals(team) ? 0xFF4488FF : 0xFFFF4444;
                g.fill(x0, y0, x0 + 4, y1, barColor);

                // 序号 + 名字
                int textX = x0 + 14;
                int textY = y0 + (CARD_H - 8) / 2;
                String label = (i + 1) + ". " + name;
                if (isVoted) {
                    label += "  §a✓";
                }
                g.drawString(this.font, label, textX, textY, 0xFFFFFF);

                // 悬停提示
                if (hovered) {
                    g.drawCenteredString(this.font, "§e点击投票",
                            centerX, y1 + 2, 0xFFFF44);
                }
            }
        }

        // 底部倒计时
        int cdColor = countdownSeconds <= 5 ? 0xFFFF4444 : 0xFFFFFF44;
        String cdText = hasVoted
                ? "§7已投票，等待" + countdownSeconds + "秒..."
                : "剩余 " + countdownSeconds + " 秒";
        g.drawCenteredString(this.font, cdText, centerX, this.height - 30, cdColor);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

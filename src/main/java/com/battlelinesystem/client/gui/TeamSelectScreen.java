package com.battlelinesystem.client.gui;

import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketTeamSelect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 选择队伍界面 — 玩家选择加入 A 队或 B 队
 */
public class TeamSelectScreen extends Screen {

    private static final int CARD_W = 140;
    private static final int CARD_H = 80;
    private static final int CARD_GAP = 30;

    /** 当前已选的队伍: "A", "B", 或 null（未选） */
    private String selectedTeam = null;
    /** 倒计时由服务端推送，客户端不自行递减 */
    private int countdownSeconds = -1;

    /** A/B队当前人数 */
    private int countA = 0;
    private int countB = 0;

    /** A队卡片的判定框 */
    private int ax, ay;
    /** B队卡片的判定框 */
    private int bx, by;

    public TeamSelectScreen(int countdownSeconds) {
        this(countdownSeconds, 0, 0);
    }

    public TeamSelectScreen(int countdownSeconds, int countA, int countB) {
        super(Component.literal("选择队伍"));
        this.countdownSeconds = countdownSeconds;
        this.countA = countA;
        this.countB = countB;
    }

    /** 刷新倒计时（服务端推送） */
    public void refreshCountdown(int sec) { this.countdownSeconds = sec; }

    /** 刷新双方人数 */
    public void refreshCounts(int a, int b) { this.countA = a; this.countB = b; }

    @Override
    protected void init() {
        super.init();
        int totalW = CARD_W * 2 + CARD_GAP;
        int startX = (this.width - totalW) / 2;
        int cardY = (this.height - CARD_H) / 2;
        ax = startX;
        ay = cardY;
        bx = startX + CARD_W + CARD_GAP;
        by = cardY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= ax && mouseX <= ax + CARD_W && mouseY >= ay && mouseY <= ay + CARD_H) {
            if (!"A".equals(selectedTeam) && canJoin("A")) selectTeam("A");
            return true;
        }
        if (mouseX >= bx && mouseX <= bx + CARD_W && mouseY >= by && mouseY <= by + CARD_H) {
            if (!"B".equals(selectedTeam) && canJoin("B")) selectTeam("B");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 检查队伍人数是否平衡：该队人数不能比另一队多2人及以上 */
    private boolean canJoin(String team) {
        if ("A".equals(team)) {
            return countA < countB + 2;
        } else {
            return countB < countA + 2;
        }
    }

    private void selectTeam(String team) {
        selectedTeam = team;
        AllPackets.getChannel().sendToServer(new PacketTeamSelect(team));
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

        // 标题
        String title = selectedTeam != null
                ? "已选择 " + ("A".equals(selectedTeam) ? "A 队" : "B 队") + "（点击另一队可切换）"
                : "选择你的队伍";
        g.drawCenteredString(this.font, title,
                this.width / 2, this.height / 2 - 70, 0xFFFFFF);

        boolean hoverA = mouseX >= ax && mouseX <= ax + CARD_W
                && mouseY >= ay && mouseY <= ay + CARD_H;
        boolean hoverB = mouseX >= bx && mouseX <= bx + CARD_W
                && mouseY >= by && mouseY <= by + CARD_H;

        boolean blockedA = !canJoin("A");
        boolean blockedB = !canJoin("B");

        // A 队卡片
        renderTeamCard(g, ax, ay, "A 队", "进攻方", 0xFF4488FF, hoverA, blockedA, countA,
                "A".equals(selectedTeam));

        // B 队卡片
        renderTeamCard(g, bx, by, "B 队", "防守方", 0xFFFF4444, hoverB, blockedB, countB,
                "B".equals(selectedTeam));

        // 倒计时
        if (countdownSeconds > 0) {
            int cdColor = countdownSeconds <= 5 ? 0xFFFF4444 : 0xFFFFFF44;
            g.drawCenteredString(this.font, "剩余 " + countdownSeconds + " 秒",
                    this.width / 2, this.height - 30, cdColor);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTeamCard(GuiGraphics g, int x, int y,
                                String teamName, String teamDesc,
                                int color, boolean hovered, boolean blocked, int count,
                                boolean isSelected) {
        int bgColor;
        if (isSelected) {
            bgColor = 0xDD000000 | (color & 0x00FFFFFF);
        } else if (blocked) {
            bgColor = 0xAA222222;
        } else if (hovered) {
            bgColor = 0xCC000000 | (color & 0x00FFFFFF);
        } else {
            bgColor = 0xAA333333;
        }
        g.fill(x, y, x + CARD_W, y + CARD_H, bgColor);
        g.fill(x, y, x + CARD_W, y + 4, blocked ? 0xFF444444 : 0xFF000000 | color);

        int nameColor = blocked ? 0xFF888888 : 0xFFFFFF;
        g.drawCenteredString(this.font, teamName,
                x + CARD_W / 2, y + 12, nameColor);

        int countColor = blocked ? 0xFF888888 : 0xFFDDDDDD;
        g.drawCenteredString(this.font, count + " 人",
                x + CARD_W / 2, y + 30, countColor);

        g.drawCenteredString(this.font, teamDesc,
                x + CARD_W / 2, y + 48, 0xAAAAAA);

        if (isSelected) {
            g.drawCenteredString(this.font, "已选择",
                    x + CARD_W / 2, y + 64, 0xFF44FF44);
        } else if (blocked) {
            g.drawCenteredString(this.font, "人数已满",
                    x + CARD_W / 2, y + 64, 0xFF888888);
        } else if (hovered) {
            g.drawCenteredString(this.font, "点击加入",
                    x + CARD_W / 2, y + 64, color);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

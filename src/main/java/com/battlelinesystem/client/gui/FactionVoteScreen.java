package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketFactionSelect;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 阵营投票界面 — A队/B队阵营池分列，横向长方形卡片，竖直堆叠，底部居中描述
 */
public class FactionVoteScreen extends Screen {

    private static final int CARD_W = 112;
    private static final int CARD_H = 90;
    private static final int CARD_GAP = 6;
    private static final int THUMB_W = 92;
    private static final int THUMB_H = 69;
    private static final int GRID_COLS = 3;

    private List<FactionConfig> factions = new ArrayList<>();
    private final Map<String, ResourceLocation> thumbTextures = new LinkedHashMap<>();

    /** A阵营池ID列表 */
    private final Set<String> poolA = new LinkedHashSet<>();
    /** B阵营池ID列表 */
    private final Set<String> poolB = new LinkedHashSet<>();

    /** 按池分组的阵营 */
    private final List<FactionConfig> poolAFactions = new ArrayList<>();
    private final List<FactionConfig> poolBFactions = new ArrayList<>();

    /** id -> [x, y, w, h] 点击判定框 */
    private final Map<String, int[]> cardBounds = new LinkedHashMap<>();

    /** 排序后的扁平阵营列表（A池在前，B池在后） */
    private final List<FactionConfig> sortedFactions = new ArrayList<>();

    private boolean hasSelected = false;
    private int gridRows = 0;
    private int gridCols = 0;
    private int gridTotalW = 0; // 网格总宽度
    private int gridTotalH = 0; // 网格总高度
    private int gridStartX = 0;
    private int gridStartY = 0;

    /** 当前悬停的阵营描述（用于底部显示） */
    private String hoveredDescription = "";

    /** 倒计时由服务端推送，客户端不自行递减 */
    private int countdownSeconds = 10;

    public FactionVoteScreen() {
        super(Component.literal("选择阵营"));
    }

    /** 刷新倒计时（服务端推送） */
    public void refreshCountdown(int sec) { this.countdownSeconds = sec; }

    @Override
    protected void init() {
        super.init();
        calcGrid();
    }

    private void calcGrid() {
        sortedFactions.clear();
        sortedFactions.addAll(poolAFactions);
        sortedFactions.addAll(poolBFactions);
        int n = sortedFactions.size();
        if (n == 0) { gridRows = gridCols = 0; return; }

        gridCols = Math.min(n, GRID_COLS);
        gridRows = (int) Math.ceil((double) n / gridCols);

        gridTotalW = gridCols * CARD_W + Math.max(0, gridCols - 1) * CARD_GAP;
        gridTotalH = gridRows * CARD_H + Math.max(0, gridRows - 1) * CARD_GAP;
        gridStartX = (this.width - gridTotalW) / 2;
        gridStartY = (this.height - gridTotalH) / 2;

        updateCardBounds();
    }

    public void setFactions(List<FactionConfig> list, List<String> poolAList, List<String> poolBList) {
        this.factions = list;
        this.poolA.clear();
        this.poolB.clear();
        if (poolAList != null) this.poolA.addAll(poolAList);
        if (poolBList != null) this.poolB.addAll(poolBList);

        // 按池分组
        poolAFactions.clear();
        poolBFactions.clear();
        for (FactionConfig fc : factions) {
            if (poolA.contains(fc.id)) {
                poolAFactions.add(fc);
            } else if (poolB.contains(fc.id)) {
                poolBFactions.add(fc);
            } else {
                poolAFactions.add(fc);
            }
        }

        loadThumbnails();
        this.clearWidgets();
        calcGrid();
    }

    /** 兼容旧接口（无池信息时全部进A列） */
    public void updateFactions(List<FactionConfig> list) {
        setFactions(list, new ArrayList<>(), new ArrayList<>());
    }

    private void updateCardBounds() {
        cardBounds.clear();
        if (sortedFactions.isEmpty()) return;
        for (int i = 0; i < sortedFactions.size(); i++) {
            int col = i % gridCols;
            int row = i / gridCols;
            int cx = gridStartX + col * (CARD_W + CARD_GAP);
            int cy = gridStartY + row * (CARD_H + CARD_GAP);
            cardBounds.put(sortedFactions.get(i).id, new int[]{cx, cy, CARD_W, CARD_H});
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasSelected) return true;

        for (FactionConfig f : factions) {
            int[] b = cardBounds.get(f.id);
            if (b != null && mouseX >= b[0] && mouseX <= b[0] + b[2]
                    && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                selectFaction(f.id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectFaction(String factionId) {
        hasSelected = true;
        AllPackets.getChannel().sendToServer(new PacketFactionSelect(factionId));
        // 服务端会回复 PacketOpenClassVote：
        //   - 有职业 → 打开 ClassSelectScreen
        //   - 无职业 → 激活 WaitHudOverlay + 关闭界面
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() {
        if (hasSelected) {
            super.onClose();
        }
    }

    private void loadThumbnails() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (FactionConfig f : factions) {
            if (f.hasThumbnail && !thumbTextures.containsKey(f.id)) {
                Path thumbPath = Path.of("config", "battlelinesystem", "faction_thumbnails", f.id + ".png");
                try (InputStream in = Files.newInputStream(thumbPath)) {
                    NativeImage img = NativeImage.read(in);
                    ResourceLocation loc = new ResourceLocation("battlelinesystem", "fv_" + f.id);
                    tm.register(loc, new DynamicTexture(img));
                    thumbTextures.put(f.id, loc);
                } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        if (factions.isEmpty()) {
            g.drawCenteredString(this.font, "暂无可选阵营",
                    this.width / 2, this.height / 2, 0x666666);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        boolean hasA = !poolAFactions.isEmpty();
        boolean hasB = !poolBFactions.isEmpty();
        if (hasA && hasB) {
            g.drawCenteredString(this.font, "选择阵营", this.width / 2, gridStartY - 16, 0xFFFFFF);
        } else if (hasA) {
            g.drawCenteredString(this.font, "A 队 · 选择阵营", this.width / 2, gridStartY - 16, 0xFF4488FF);
        } else if (hasB) {
            g.drawCenteredString(this.font, "B 队 · 选择阵营", this.width / 2, gridStartY - 16, 0xFFFF4444);
        }

        String newHovered = "";
        for (FactionConfig fc : sortedFactions) {
            int[] b = cardBounds.get(fc.id);
            if (b == null) continue;
            boolean hovered = mouseX >= b[0] && mouseX <= b[0] + CARD_W
                    && mouseY >= b[1] && mouseY <= b[1] + CARD_H;
            if (hovered && fc.description != null && !fc.description.isEmpty()) {
                newHovered = fc.description;
            }
            renderCard(g, fc, b[0], b[1], hovered);
        }
        hoveredDescription = newHovered;

        // 网格正下方显示阵营描述
        if (!hoveredDescription.isEmpty()) {
            int descY = gridStartY + gridTotalH + 14;
            g.drawCenteredString(this.font, hoveredDescription,
                    this.width / 2, descY, 0xAAFFFF);
        }

        // 倒计时
        if (countdownSeconds > 0) {
            int cdColor = countdownSeconds <= 5 ? 0xFFFF4444 : 0xFFFFFF44;
            g.drawCenteredString(this.font, "剩余 " + countdownSeconds + " 秒",
                    this.width / 2, this.height - 30, cdColor);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics g, FactionConfig fc, int x, int y, boolean hovered) {
        // 半透明背景（悬停高亮）
        g.fill(x, y, x + CARD_W, y + CARD_H, hovered ? 0xAA444444 : 0x80000000);

        // 缩略图（顶部居中）
        int thumbX = x + (CARD_W - THUMB_W) / 2;
        int thumbY = y + 2;
        ResourceLocation tex = thumbTextures.get(fc.id);
        if (tex != null) {
            RenderSystem.setShaderTexture(0, tex);
            g.blit(tex, thumbX, thumbY, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
        } else {
            g.fill(thumbX, thumbY, thumbX + THUMB_W, thumbY + THUMB_H, 0xFF222222);
            g.drawCenteredString(this.font, "无图",
                    thumbX + THUMB_W / 2, thumbY + THUMB_H / 2 - 5, 0x555555);
        }

        // 阵营名称（缩略图下方居中）
        int nameY = thumbY + THUMB_H + 2;
        g.drawCenteredString(this.font, fc.name, x + CARD_W / 2, nameY, 0xFFFFFF);

        // 颜色指示条（卡片底部）
        int c = parseColor(fc.displayColor);
        g.fill(x + 4, y + CARD_H - 4, x + CARD_W - 4, y + CARD_H - 1, 0xFF000000 | c);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) { return 0xFFFFFF; }
    }
}
package com.battlelinesystem.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketMapListRequest;
import com.battlelinesystem.network.packet.PacketMapListResponse;
import com.battlelinesystem.network.packet.PacketMapListResponse.MapEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图设置界面 — 管理员浏览所有已安装地图（可滚动）
 */
public class MapSettingsScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;

    /** 缩略图 16:9 */
    private static final int THUMB_W = 96;
    private static final int THUMB_H = 54;

    /** 卡片尺寸 */
    private static final int CARD_W = 280;
    private static final int CARD_H = THUMB_H + 12;
    private static final int CARD_GAP = 6;

    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM = 42;

    /** 文本列起始 X（缩略图右侧） */
    private static final int INFO_LEFT = THUMB_W + 12;
    private static final int INFO_PAD_RIGHT = 8;

    private final int[] modeCounts;
    private final int countdownSeconds;

    private List<MapEntry> maps = new ArrayList<>();
    private final Map<String, ResourceLocation> thumbTextures = new LinkedHashMap<>();
    private boolean listRequested = false;

    /** 滚动偏移（像素），0=顶部 */
    private int scrollOffset = 0;

    public MapSettingsScreen(int[] modeCounts, int countdownSeconds) {
        super(Component.literal("地图设置"));
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    protected void init() {
        super.init();

        if (!listRequested) {
            AllPackets.getChannel().sendToServer(new PacketMapListRequest());
            listRequested = true;
        }

        this.addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SettingsScreen(
                                true, this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(this.width - 62, this.height - 32)
                .size(50, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("共 " + maps.size() + " 张地图"), btn -> {})
                .pos(12, this.height - 32)
                .size(80, 20)
                .build());
    }

    public void updateMaps(List<MapEntry> list) {
        this.maps = list;
        this.scrollOffset = 0;
        loadThumbnails();
        rebuildLayout();
    }

    private void loadThumbnails() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (MapEntry entry : maps) {
            if (entry.hasThumbnail && !thumbTextures.containsKey(entry.id)) {
                Path thumbPath = Path.of("templates", entry.id, "thumbnail.png");
                try (InputStream in = Files.newInputStream(thumbPath)) {
                    var img = com.mojang.blaze3d.platform.NativeImage.read(in);
                    ResourceLocation loc = new ResourceLocation(
                            "battlelinesystem", "thumb_" + entry.id);
                    tm.register(loc, new DynamicTexture(img));
                    thumbTextures.put(entry.id, loc);
                } catch (IOException ignored) {}
            }
        }
    }

    private void rebuildLayout() {
        this.clearWidgets();
        this.init();
    }

    // ===== 鼠标 =====

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int clipTop = LIST_TOP;
        int clipBot = this.height - LIST_BOTTOM;
        if (my < clipTop || my > clipBot) return super.mouseClicked(mx, my, button);

        int cardX = (this.width - CARD_W) / 2;
        for (int i = 0; i < maps.size(); i++) {
            int cardY = LIST_TOP + i * (CARD_H + CARD_GAP) - scrollOffset;
            if (mx >= cardX && mx <= cardX + CARD_W
                    && my >= cardY && my <= cardY + CARD_H) {
                if (this.minecraft != null && button == 0) {
                    this.minecraft.setScreen(new MapEditScreen(
                            maps.get(i), this.modeCounts, this.countdownSeconds));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listH = this.height - LIST_TOP - LIST_BOTTOM;
        int contentH = maps.size() * CARD_H + Math.max(0, maps.size() - 1) * CARD_GAP;
        int maxScroll = Math.max(0, contentH - listH);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(delta * 20), maxScroll));
        return true;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, "地图设置", this.width / 2, 15, TITLE_COLOR);

        if (maps.isEmpty()) {
            g.drawCenteredString(this.font, "暂未安装地图，请将地图放入 templates 目录",
                    this.width / 2, LIST_TOP + 60, 0x666666);
        } else {
            // 裁剪区域
            int clipTop = LIST_TOP;
            int clipBot = this.height - LIST_BOTTOM;
            g.enableScissor(0, clipTop, this.width, clipBot - clipTop);

            int cardX = (this.width - CARD_W) / 2;

            for (int i = 0; i < maps.size(); i++) {
                int cardY = LIST_TOP + i * (CARD_H + CARD_GAP) - scrollOffset;

                // 完全不可见则跳过
                if (cardY + CARD_H < clipTop || cardY > clipBot) continue;

                MapEntry entry = maps.get(i);

                // 卡片背景（悬停高亮）
                boolean hovered = mouseX >= cardX && mouseX <= cardX + CARD_W
                        && mouseY >= cardY && mouseY <= cardY + CARD_H
                        && mouseY >= clipTop && mouseY <= clipBot;
                g.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H,
                        hovered ? 0xFF3A3A3A : 0xFF2A2A2A);

                // 缩略图（垂直居中于卡片内）
                int thumbY = cardY + (CARD_H - THUMB_H) / 2;
                ResourceLocation tex = thumbTextures.get(entry.id);
                if (tex != null) {
                    RenderSystem.setShaderTexture(0, tex);
                    g.blit(tex, cardX + 2, thumbY, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
                } else {
                    g.fill(cardX + 2, thumbY, cardX + 2 + THUMB_W, thumbY + THUMB_H, 0xFF1A1A1A);
                    g.drawCenteredString(this.font, "无预览图",
                            cardX + 2 + THUMB_W / 2, thumbY + THUMB_H / 2 - 5, 0x555555);
                }

                // ---- 信息区域 ----
                int infoX = cardX + INFO_LEFT;
                int maxTextW = CARD_W - INFO_LEFT - INFO_PAD_RIGHT;
                int line1Y = cardY + 4;

                // 地图名 + ID（ID 在名称右侧）
                String title = truncateToWidth(entry.name, maxTextW - 50);
                g.drawString(this.font, title, infoX, line1Y, 0xFFCC44, false);
                int titleW = this.font.width(title);
                g.drawString(this.font, "[" + entry.id + "]",
                        infoX + titleW + 6, line1Y, 0x888888, false);

                // 模式 | 人数
                String sub = entry.mode + "  |  " + entry.minPlayers + "-" + entry.maxPlayers + "人";
                g.drawString(this.font, sub, infoX, line1Y + 12, 0xAAAAAA, false);

                // 描述（按像素宽度截断）
                String desc = truncateToWidth(entry.description, maxTextW);
                g.drawString(this.font, desc, infoX, line1Y + 24, 0x777777, false);
            }

            g.disableScissor();

            // 右侧滚动条
            int contentH = maps.size() * CARD_H + Math.max(0, maps.size() - 1) * CARD_GAP;
            int listH = clipBot - clipTop;
            if (contentH > listH) {
                int barH = Math.max(20, listH * listH / contentH);
                int barY = clipTop + scrollOffset * listH / contentH;
                g.fill(this.width - 4, barY, this.width - 2, barY + barH, 0x66AAAAAA);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 按像素宽度截断文本，超出部分用 ".." 替代 */
    private String truncateToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String suffix = "..";
        int suffixW = this.font.width(suffix);
        int cutoff = maxWidth - suffixW;
        if (cutoff <= 0) return suffix;
        // 逐字追加直到超出宽度
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            if (this.font.width(sb.toString() + ch) > cutoff) break;
            sb.append(ch);
        }
        return sb + suffix;
    }
}

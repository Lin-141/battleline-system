package com.battlelinesystem.client.gui;

import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketSelectMap;
import com.battlelinesystem.network.PacketTimeUp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模式锁定后的地图选择界面 — 井字棋网格排列，带缩略图
 */
public class MapVoteScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;

    /** 缩略图 16:9 */
    private static final int THUMB_WIDTH = 128;
    private static final int THUMB_HEIGHT = 72;

    /** 按钮适配图片宽度 */
    private static final int BTN_WIDTH = THUMB_WIDTH;
    private static final int BTN_HEIGHT = 18;

    /** 一字横排 */
    private static final int CARD_GAP = 10;
    private static final int TITLE_Y = 15;
    private static final int SUBTITLE_Y = 30;
    private static final int GRID_TOP = 48;

    private final String modeName;
    private final List<PacketTimeUp.MapEntry> maps;
    private final Map<String, ResourceLocation> thumbnailTextures = new LinkedHashMap<>();

    public MapVoteScreen(String modeName, List<PacketTimeUp.MapEntry> maps) {
        super(Component.literal(modeName + "模式地图选择"));
        this.modeName = modeName;
        this.maps = maps;

        // 从本地文件系统加载缩略图
        for (PacketTimeUp.MapEntry entry : maps) {
            if (entry.hasThumbnail) {
                Path thumbPath = Path.of("templates", entry.id, "thumbnail.png");
                try (InputStream in = Files.newInputStream(thumbPath)) {
                    NativeImage img = NativeImage.read(in);
                    ResourceLocation loc = new ResourceLocation(
                            "battlelinesystem", "thumb_" + entry.id);
                    Minecraft.getInstance().getTextureManager().register(loc,
                            new DynamicTexture(img));
                    thumbnailTextures.put(entry.id, loc);
                } catch (IOException e) {
                    // 忽略解析失败的缩略图
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        if (maps == null || maps.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("返回"), btn -> onClose())
                    .pos(this.width / 2 - 50, this.height - 35)
                    .size(100, 20)
                    .build());
            return;
        }

        // 一字横排居中
        int totalWidth = maps.size() * THUMB_WIDTH + (maps.size() - 1) * CARD_GAP;
        int startX = (this.width - totalWidth) / 2;

        for (int i = 0; i < maps.size(); i++) {
            PacketTimeUp.MapEntry entry = maps.get(i);
            int cardX = startX + i * (THUMB_WIDTH + CARD_GAP);
            int btnY = GRID_TOP + THUMB_HEIGHT + 4;

            String label = entry.name + " (" + entry.minPlayers + "-" + entry.maxPlayers + "人)";

            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                        AllPackets.getChannel().sendToServer(new PacketSelectMap(entry.id));
                    })
                    .pos(cardX, btnY)
                    .size(BTN_WIDTH, BTN_HEIGHT)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.drawCenteredString(this.font, modeName + "模式地图选择", this.width / 2, TITLE_Y, TITLE_COLOR);

        if (maps == null || maps.isEmpty()) {
            graphics.drawCenteredString(this.font, "没有可用的地图，请联系管理员添加",
                    this.width / 2, this.height / 2 - 10, 0xFF6666);
        } else {
            graphics.drawCenteredString(this.font, "请选择一张地图开始游戏",
                    this.width / 2, SUBTITLE_Y, 0xAAAAAA);

            int totalWidth = maps.size() * THUMB_WIDTH + (maps.size() - 1) * CARD_GAP;
            int startX = (this.width - totalWidth) / 2;

            for (int i = 0; i < maps.size(); i++) {
                PacketTimeUp.MapEntry entry = maps.get(i);
                ResourceLocation tex = thumbnailTextures.get(entry.id);

                int cardX = startX + i * (THUMB_WIDTH + CARD_GAP);
                int cardY = GRID_TOP;

                // 缩略图背景框
                graphics.fill(cardX - 2, cardY - 2,
                        cardX + THUMB_WIDTH + 2, cardY + THUMB_HEIGHT + 2, 0xFF555555);

                if (tex != null) {
                    RenderSystem.setShaderTexture(0, tex);
                    graphics.blit(tex, cardX, cardY,
                            0, 0, THUMB_WIDTH, THUMB_HEIGHT,
                            THUMB_WIDTH, THUMB_HEIGHT);
                } else {
                    // 无缩略图占位
                    graphics.fill(cardX, cardY, cardX + THUMB_WIDTH, cardY + THUMB_HEIGHT, 0xFF333333);
                    graphics.drawCenteredString(this.font, "无预览",
                            cardX + THUMB_WIDTH / 2, cardY + THUMB_HEIGHT / 2 - 5, 0x888888);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        for (ResourceLocation tex : thumbnailTextures.values()) {
            Minecraft.getInstance().getTextureManager().release(tex);
        }
    }
}

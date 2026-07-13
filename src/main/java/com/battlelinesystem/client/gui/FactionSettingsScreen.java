package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketFactionAction;
import com.battlelinesystem.network.packet.PacketFactionRequest;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阵营设置界面 — 管理员增删阵营（含色卡选色 + 缩略图）
 * 编辑操作打开独立的 FactionEditScreen
 */
public class FactionSettingsScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;
    private static final int LIST_TOP = 50;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 4;
    private static final int COLOR_BOX_SIZE = 14;
    private static final int THUMB_W = 32;
    private static final int THUMB_H = 22;

    // 色卡尺寸
    private static final int CP_SV_W = 80;
    private static final int CP_SV_H = 60;
    private static final int CP_HUE_H = 10;
    private static final int CP_PREVIEW_W = 24;
    private static final int CP_PREVIEW_H = 24;

    private final boolean isOp;
    private final int[] modeCounts;
    private final int countdownSeconds;

    private List<FactionConfig> factions = new ArrayList<>();
    private boolean showAddForm = false;
    private String pendingDeleteId = null;
    private boolean listRequested = false;
    private EditBox idInput;
    private EditBox nameInput;
    private EditBox colorInput;

    // 色卡状态
    private float currentHue = 0f;
    private float currentSat = 0f;
    private float currentVal = 1f;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean syncingFromPicker = false;
    private int cpSvX, cpSvY, cpSliderY, cpPreviewX, cpPreviewY;

    // 缩略图纹理缓存
    private final Map<String, ResourceLocation> thumbTextures = new LinkedHashMap<>();

    public FactionSettingsScreen(boolean isOp, int[] modeCounts, int countdownSeconds) {
        super(Component.literal("Faction Settings"));
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    protected void init() {
        super.init();

        if (!listRequested) {
            AllPackets.getChannel().sendToServer(new PacketFactionRequest());
            listRequested = true;
        }

        int btnY = this.height - 30;
        int btnW = 80;

        this.addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SettingsScreen(
                                this.isOp, this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(this.width - btnW - 12, btnY)
                .size(btnW, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal(showAddForm ? "取消" : "+ 添加阵营"), btn -> {
                    showAddForm = !showAddForm;
                    rebuildLayout();
                })
                .pos(12, btnY)
                .size(btnW, 20)
                .build());

        // 删除确认对话框按钮
        if (pendingDeleteId != null) {
            int dialogW = 200;
            int dialogH = 50;
            int dialogX = (this.width - dialogW) / 2;
            int dialogY = (this.height - dialogH) / 2 - 40;
            int confirmX = dialogX + 20;
            int cancelX = dialogX + dialogW - 80 - 20;

            this.addRenderableWidget(Button.builder(Component.literal("确认删除"), b -> confirmDelete())
                    .pos(confirmX, dialogY + dialogH - 24)
                    .size(70, 18)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal("取消"), b -> cancelDelete())
                    .pos(cancelX, dialogY + dialogH - 24)
                    .size(70, 18)
                    .build());
        }

        if (showAddForm) {
            buildAddForm();
        }

        // 阵营行
        int y = LIST_TOP;
        for (FactionConfig fc : factions) {
            final String fid = fc.id;

            // 编辑按钮 → 打开 FactionEditScreen
            this.addRenderableWidget(Button.builder(Component.literal("✎"), b -> openEditScreen(fid))
                    .pos(this.width - 56, y + 7)
                    .size(16, 16)
                    .build());

            // 删除按钮
            this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                        pendingDeleteId = fid;
                        rebuildLayout();
                    })
                    .pos(this.width - 35, y + 7)
                    .size(16, 16)
                    .build());
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private void buildAddForm() {
        int formY = this.height - 150;
        int inputW = 90;
        int gap = 6;

        this.idInput = this.addRenderableWidget(
                new EditBox(this.font, 12, formY, inputW, 18, Component.literal("ID")));
        idInput.setMaxLength(16);
        idInput.setHint(Component.literal("ID (a-z0-9_-)"));
        idInput.setFilter(s -> s.isEmpty() || s.matches("[a-zA-Z0-9_-]+"));

        this.nameInput = this.addRenderableWidget(
                new EditBox(this.font, 12 + inputW + gap, formY, inputW, 18, Component.literal("名称")));
        nameInput.setMaxLength(20);
        nameInput.setHint(Component.literal("显示名称"));

        this.colorInput = this.addRenderableWidget(
                new EditBox(this.font, 12 + (inputW + gap) * 2, formY, inputW, 18, Component.literal("颜色")));
        colorInput.setMaxLength(7);
        colorInput.setValue("#FFFFFF");
        colorInput.setResponder(this::onHexChanged);

        this.addRenderableWidget(Button.builder(Component.literal("确认"), btn -> {
                    String id = idInput.getValue().trim();
                    String name = nameInput.getValue().trim();
                    String color = colorInput.getValue().trim();
                    if (!id.isEmpty() && !name.isEmpty()) {
                        if (!color.startsWith("#")) color = "#" + color;
                        FactionConfig fc = new FactionConfig(id, name, color, "");
                        AllPackets.getChannel().sendToServer(PacketFactionAction.add(fc));
                        showAddForm = false;
                        rebuildLayout();
                    }
                })
                .pos(12 + (inputW + gap) * 3, formY)
                .size(40, 18)
                .build());

        cpSvX = 12;
        cpSvY = formY + 22 + 4;
        cpSliderY = cpSvY + CP_SV_H + 4;
        cpPreviewX = cpSvX + CP_SV_W + 10;
        cpPreviewY = cpSvY + (CP_SV_H + CP_HUE_H + 4 - CP_PREVIEW_H) / 2;
    }

    private void openEditScreen(String factionId) {
        for (FactionConfig fc : factions) {
            if (fc.id.equals(factionId)) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new FactionEditScreen(
                            new FactionConfig(fc), this.isOp, this.modeCounts, this.countdownSeconds));
                }
                return;
            }
        }
    }

    // ===== 缩略图纹理加载 =====

    private void loadThumbnails() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (FactionConfig f : factions) {
            if (f.hasThumbnail && !thumbTextures.containsKey(f.id)) {
                Path thumbPath = Path.of("config", "battlelinesystem", "faction_thumbnails", f.id + ".png");
                try (InputStream in = Files.newInputStream(thumbPath)) {
                    NativeImage img = NativeImage.read(in);
                    ResourceLocation loc = new ResourceLocation("battlelinesystem", "faction_thumb_" + f.id);
                    tm.register(loc, new DynamicTexture(img));
                    thumbTextures.put(f.id, loc);
                } catch (IOException ignored) {}
            }
        }
    }

    // ===== 删除确认 =====

    private void confirmDelete() {
        if (pendingDeleteId != null) {
            AllPackets.getChannel().sendToServer(PacketFactionAction.remove(pendingDeleteId));
        }
        pendingDeleteId = null;
        rebuildLayout();
    }

    private void cancelDelete() {
        pendingDeleteId = null;
        rebuildLayout();
    }

    // ===== 鼠标事件 =====

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (showAddForm && button == 0) {
            if (isInSV(mx, my)) { draggingSV = true; updateSVFromMouse(mx, my); return true; }
            if (isInHue(mx, my)) { draggingHue = true; updateHueFromMouse(mx, my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSV) { updateSVFromMouse(mx, my); return true; }
        if (draggingHue) { updateHueFromMouse(mx, my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSV = false;
        draggingHue = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean isInSV(double mx, double my) {
        return mx >= cpSvX && mx < cpSvX + CP_SV_W && my >= cpSvY && my < cpSvY + CP_SV_H;
    }

    private boolean isInHue(double mx, double my) {
        return mx >= cpSvX && mx < cpSvX + CP_SV_W && my >= cpSliderY && my < cpSliderY + CP_HUE_H;
    }

    private void updateSVFromMouse(double mx, double my) {
        currentSat = clamp01((float)(mx - cpSvX) / (CP_SV_W - 1));
        currentVal = clamp01(1f - (float)(my - cpSvY) / (CP_SV_H - 1));
        syncHexFromPicker();
    }

    private void updateHueFromMouse(double mx, double my) {
        currentHue = Math.max(0f, Math.min(360f, (float)(mx - cpSvX) / (CP_SV_W - 1) * 360f));
        syncHexFromPicker();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void onHexChanged(String text) {
        if (syncingFromPicker) return;
        int rgb = parseColor(text);
        float[] hsv = rgbToHsv((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        currentHue = hsv[0]; currentSat = hsv[1]; currentVal = hsv[2];
    }

    private void syncHexFromPicker() {
        syncingFromPicker = true;
        int rgb = hsvToRgb(currentHue, currentSat, currentVal);
        colorInput.setValue(String.format("#%06X", rgb));
        syncingFromPicker = false;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, "阵营设置", this.width / 2, 15, TITLE_COLOR);

        if (pendingDeleteId != null) {
            renderDeleteConfirm(g);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // 阵营列表
        int y = LIST_TOP;
        for (FactionConfig f : factions) {
            int rowCenterY = y + ROW_HEIGHT / 2;

            if (f.hasThumbnail) {
                ResourceLocation tex = thumbTextures.get(f.id);
                if (tex != null) {
                    RenderSystem.setShaderTexture(0, tex);
                    g.blit(tex, 12, y + 4, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
                } else {
                    g.fill(12, y + 4, 12 + THUMB_W, y + 4 + THUMB_H, 0xFF222222);
                }
            } else {
                g.fill(12, y + 4, 12 + THUMB_W, y + 4 + THUMB_H, 0xFF1A1A1A);
                g.drawCenteredString(this.font, "无图", 12 + THUMB_W / 2, y + 9, 0x555555);
            }

            int c = parseColor(f.displayColor);
            g.fill(12 + THUMB_W + 8, rowCenterY - COLOR_BOX_SIZE / 2,
                    12 + THUMB_W + 8 + COLOR_BOX_SIZE, rowCenterY + COLOR_BOX_SIZE / 2,
                    0xFF000000 | c);

            int textX = 12 + THUMB_W + 8 + COLOR_BOX_SIZE + 6;
            g.drawString(this.font, f.name, textX, rowCenterY - 4, 0xFFFFFF, false);
            String info = "[" + f.id + "]  " + f.displayColor;
            if (f.description != null && !f.description.isEmpty()) {
                info += "  " + f.description;
            }
            g.drawString(this.font, info, textX, rowCenterY + 8, 0x888888, false);

            y += ROW_HEIGHT + ROW_GAP;
        }

        if (factions.isEmpty()) {
            g.drawCenteredString(this.font, "暂无阵营，请点击下方按钮添加",
                    this.width / 2, LIST_TOP + 30, 0x666666);
        }

        if (showAddForm) {
            renderColorPicker(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderDeleteConfirm(GuiGraphics g) {
        int dialogW = 200;
        int dialogH = 50;
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = (this.height - dialogH) / 2 - 40;

        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xDD222222);
        g.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFFAA0000);

        g.drawCenteredString(this.font, "确认删除阵营?",
                this.width / 2, dialogY + 8, 0xFF4444);
        g.drawCenteredString(this.font, "\"" + pendingDeleteId + "\" 将被永久移除",
                this.width / 2, dialogY + 22, 0xAAAAAA);
    }

    private void renderColorPicker(GuiGraphics g) {
        for (int px = 0; px < CP_SV_W; px++) {
            float s = (float) px / (CP_SV_W - 1);
            int bright = 0xFF000000 | hsvToRgb(currentHue, s, 1f);
            int dark = 0xFF000000 | hsvToRgb(currentHue, s, 0f);
            g.fillGradient(cpSvX + px, cpSvY, cpSvX + px + 1, cpSvY + CP_SV_H, bright, dark);
        }
        int sx = cpSvX + Math.round(currentSat * (CP_SV_W - 1));
        int sy = cpSvY + Math.round((1f - currentVal) * (CP_SV_H - 1));
        g.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF000000);
        g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFFFF);

        for (int px = 0; px < CP_SV_W; px++) {
            float h = (float) px / (CP_SV_W - 1) * 360f;
            g.fill(cpSvX + px, cpSliderY, cpSvX + px + 1, cpSliderY + CP_HUE_H,
                    0xFF000000 | hsvToRgb(h, 1f, 1f));
        }
        int hx = cpSvX + Math.round(currentHue / 360f * (CP_SV_W - 1));
        g.fill(hx - 1, cpSliderY - 3, hx + 2, cpSliderY, 0xFFFFFFFF);
        g.fill(hx, cpSliderY - 2, hx + 1, cpSliderY, 0xFF000000);

        int previewRgb = hsvToRgb(currentHue, currentSat, currentVal);
        g.fill(cpPreviewX, cpPreviewY, cpPreviewX + CP_PREVIEW_W, cpPreviewY + CP_PREVIEW_H,
                0xFF000000 | previewRgb);
        g.renderOutline(cpPreviewX, cpPreviewY, CP_PREVIEW_W, CP_PREVIEW_H, 0xFFAAAAAA);
        g.drawString(this.font, "色相 →", cpSvX, cpSliderY + CP_HUE_H + 2, 0x888888, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ===== 工具方法 =====

    private void rebuildLayout() {
        this.clearWidgets();
        this.init();
    }

    public void updateFactions(List<FactionConfig> list) {
        this.factions = list;
        this.thumbTextures.clear();
        loadThumbnails();
        rebuildLayout();
    }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) { return 0xFFFFFF; }
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360) + 360) % 360;
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        return (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0, s = 0, v = max;
        if (delta > 0.0001f) {
            s = delta / max;
            if (max == rf)       h = 60 * (((gf - bf) / delta) % 6);
            else if (max == gf)  h = 60 * (((bf - rf) / delta) + 2);
            else                 h = 60 * (((rf - gf) / delta) + 4);
            if (h < 0) h += 360;
        }
        return new float[]{h, s, v};
    }
}

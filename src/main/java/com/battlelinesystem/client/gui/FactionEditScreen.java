package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.faction.VehicleConfig;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketFactionAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 阵营编辑界面 — 第1页：名称颜色+职业管理，第2页：载具池，第3页：留空
 */
public class FactionEditScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;

    private static final int CP_SV_W = 100;
    private static final int CP_SV_H = 70;
    private static final int CP_HUE_H = 12;
    private static final int CP_PREVIEW_W = 30;
    private static final int CP_PREVIEW_H = 30;

    private static final int LEFT_X = 16;
    private static final int LABEL_W = 52;
    private static final int INPUT_W = 180;
    private static final int CLASS_ROW_H = 22;
    private static final int VEHICLE_ROW_H = 22;

    private final FactionConfig faction;
    private final boolean isOp;
    private final int[] modeCounts;
    private final int countdownSeconds;

    private EditBox nameInput;
    private EditBox colorInput;
    private EditBox descInput;

    private float currentHue = 0f;
    private float currentSat = 1f;
    private float currentVal = 1f;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean syncingFromPicker = false;
    private int cpSvX, cpSvY, cpSliderY, cpPreviewX, cpPreviewY;

    private int currentPage = 0; // 0=信息+职业, 1=载具, 2=指挥官物品, 3=效果设置

    public FactionEditScreen(FactionConfig faction, boolean isOp,
                             int[] modeCounts, int countdownSeconds) {
        super(Component.literal("编辑阵营"));
        this.faction = faction;
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    protected void init() {
        super.init();
        int rightX = this.width / 2 + 20;
        int inputBoxX = LEFT_X + LABEL_W + 4;
        int y = 48;
        int bottomBarY = this.height - 36;

        // ===== 左上角翻页按钮 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("< 1"), btn -> { currentPage = 0; rebuild(); })
                .pos(12, 12).size(26, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("2"), btn -> { currentPage = 1; rebuild(); })
                .pos(40, 12).size(20, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("3"), btn -> { currentPage = 2; rebuild(); })
                .pos(62, 12).size(20, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("4 >"), btn -> { currentPage = 3; rebuild(); })
                .pos(84, 12).size(28, 16).build());

        String pageLabel = switch (currentPage) {
            case 0 -> "信息 + 职业管理";
            case 1 -> "载具池 (" + (faction.vehicles != null ? faction.vehicles.size() : 0) + ")";
            case 2 -> "指挥官额外物品";
            case 3 -> "效果设置";
            default -> "";
        };
        this.addRenderableWidget(Button.builder(
                Component.literal(pageLabel), btn -> {})
                .pos(116, 12).size(150, 16).build());

        switch (currentPage) {
            case 0 -> initPage1(inputBoxX, rightX, bottomBarY);
            case 1 -> initPage2(rightX, bottomBarY);
            case 2 -> initPage3(bottomBarY);
            case 3 -> initPage4(bottomBarY);
        }

        // 底部按钮
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .pos(this.width / 2 - 52, bottomBarY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> goBack())
                .pos(this.width / 2 + 8, bottomBarY).size(44, 20).build());
    }

    // ===== 第1页：信息 + 职业 =====
    private void initPage1(int inputBoxX, int rightX, int bottomBarY) {
        int y = 48;
        int gap = 24;

        this.nameInput = this.addRenderableWidget(
                new EditBox(this.font, inputBoxX, y, INPUT_W, 18, Component.literal("名称")));
        nameInput.setMaxLength(20);
        nameInput.setValue(faction.name);

        y += gap;
        this.colorInput = this.addRenderableWidget(
                new EditBox(this.font, inputBoxX, y, INPUT_W, 18, Component.literal("颜色")));
        colorInput.setMaxLength(7);
        colorInput.setValue(faction.displayColor);
        colorInput.setResponder(this::onHexChanged);

        int rgb = parseColor(faction.displayColor);
        float[] hsv = rgbToHsv((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        currentHue = hsv[0]; currentSat = hsv[1]; currentVal = hsv[2];

        y += gap;
        this.descInput = this.addRenderableWidget(
                new EditBox(this.font, inputBoxX, y, INPUT_W, 18, Component.literal("描述")));
        descInput.setMaxLength(80);
        descInput.setValue(faction.description != null ? faction.description : "");
        descInput.setHint(Component.literal("阵营描述"));

        // 宽松重生点开关
        y += gap + 6;
        this.addRenderableWidget(Button.builder(
                Component.literal(faction.looseSpawn ? "[√] 宽松重生点" : "[ ] 宽松重生点"),
                b -> {
                    faction.looseSpawn = !faction.looseSpawn;
                    b.setMessage(Component.literal(faction.looseSpawn ? "[√] 宽松重生点" : "[ ] 宽松重生点"));
                })
                .pos(LEFT_X, y).size(120, 18).build());
        y -= 6;

        y += gap + 4;
        cpSvX = LEFT_X;
        cpSvY = y;
        cpSliderY = cpSvY + CP_SV_H + 4;
        cpPreviewX = cpSvX + CP_SV_W + 10;
        cpPreviewY = cpSvY;

        // ===== 右栏：职业管理 =====
        if (faction.classes == null) faction.classes = new ArrayList<>();
        List<ClassConfig> classes = faction.classes;

        int classListTop = 48;
        this.addRenderableWidget(Button.builder(Component.literal("+ 添加职业"), b -> addClass())
                .pos(rightX + 56, classListTop - 16).size(80, 16).build());

        int ry = classListTop + 4;
        for (int i = 0; i < classes.size(); i++) {
            if (ry + CLASS_ROW_H > bottomBarY) break;
            final int idx = i;
            int btnW = this.width - rightX - 76;
            this.addRenderableWidget(Button.builder(
                    Component.literal(classes.get(i).name + " [" + classes.get(i).id + "]"),
                    b -> editClass(idx))
                    .pos(rightX, ry).size(btnW, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> deleteClass(idx))
                    .pos(this.width - 56, ry).size(16, 16).build());
            ry += CLASS_ROW_H;
        }
    }

    // ===== 第2页：载具池 =====
    private void initPage2(int rightX, int bottomBarY) {
        if (faction.vehicles == null) faction.vehicles = new ArrayList<>();
        List<VehicleConfig> vehicles = faction.vehicles;

        int vy = 48;
        this.addRenderableWidget(Button.builder(Component.literal("+ 添加载具"), b -> addVehicle())
                .pos(12, vy).size(80, 18).build());
        vy += 28;

        for (int i = 0; i < vehicles.size(); i++) {
            if (vy + VEHICLE_ROW_H > bottomBarY) break;
            final int idx = i;
            int btnW = this.width - 72;
            this.addRenderableWidget(Button.builder(
                    Component.literal(vehicles.get(i).name + " [" + vehicles.get(i).id + "] §7" + vehicles.get(i).type),
                    b -> editVehicle(idx))
                    .pos(12, vy).size(btnW, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> deleteVehicle(idx))
                    .pos(14 + btnW, vy).size(16, 16).build());
            vy += VEHICLE_ROW_H;
        }
    }

    // ===== 第4页：效果设置 =====
    private void initPage4(int bottomBarY) {
        int y = 48;
        int inputW = 220;
        int inputX = LEFT_X + 64;

        this.captureSoundInput = this.addRenderableWidget(
                new EditBox(this.font, inputX, y + 22, inputW, 16, Component.literal("占领音效")));
        captureSoundInput.setMaxLength(100);
        captureSoundInput.setValue(faction.captureSound != null ? faction.captureSound : "");
        captureSoundInput.setHint(Component.literal("battlelinesystem:cntake"));

        this.loseSoundInput = this.addRenderableWidget(
                new EditBox(this.font, inputX, y + 48, inputW, 16, Component.literal("失败音效")));
        loseSoundInput.setMaxLength(100);
        loseSoundInput.setValue(faction.loseSound != null ? faction.loseSound : "");
        loseSoundInput.setHint(Component.literal("battlelinesystem:cnlose"));

        // 快速填充推荐值
        String teamSuffix = faction.id.toLowerCase().contains("us") ? "us" :
                faction.id.toLowerCase().contains("ru") ? "ru" : "cn";
        this.addRenderableWidget(Button.builder(
                Component.literal("§7一键填入 " + teamSuffix.toUpperCase() + " 音效"),
                b -> {
                    if (captureSoundInput != null) captureSoundInput.setValue("battlelinesystem:" + teamSuffix + "take");
                    if (loseSoundInput != null) loseSoundInput.setValue("battlelinesystem:" + teamSuffix + "lose");
                })
                .pos(LEFT_X, y + 76).size(160, 18).build());
    }

    // 音效输入框引用
    private EditBox captureSoundInput;
    private EditBox loseSoundInput;

    private void playPreviewSound(String soundPath) {
        if (soundPath == null || soundPath.isEmpty() || this.minecraft == null) return;
        if (this.minecraft.player == null) return;
        this.minecraft.player.displayClientMessage(
                Component.literal("§e预览音效: " + soundPath + " §7(请在实际游戏中测试)"), false);
    }

    private void initPage3(int bottomBarY) {
        if (faction.commanderExtraItems == null) faction.commanderExtraItems = new ArrayList<>();

        int y = 48;
        this.addRenderableWidget(Button.builder(Component.literal("打开物品编辑"), b -> openCommanderItemsEditor())
                .pos(12, y).size(100, 18).build());
    }

    private void openCommanderItemsEditor() {
        if (this.minecraft == null) return;
        if (faction.commanderExtraItems == null) faction.commanderExtraItems = new ArrayList<>();
        this.minecraft.setScreen(new CommanderItemsEditScreen(
                faction.commanderExtraItems,
                (newItems) -> {
                    faction.commanderExtraItems = newItems;
                    rebuildAndShow();
                },
                this::rebuildAndShow));
    }

    // ===== 职业操作 =====

    private void addClass() {
        String newId = "class_" + (faction.classes.size() + 1);
        ClassConfig cc = new ClassConfig(newId, "新职业");
        editClassWith(cc, -1);
    }

    private void editClass(int idx) {
        editClassWith(faction.classes.get(idx), idx);
    }

    private void editClassWith(ClassConfig cc, int idx) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new ClassEditScreen(cc, faction.id, isOp, modeCounts, countdownSeconds,
                (result) -> {
                    if (idx >= 0 && idx < faction.classes.size()) {
                        faction.classes.set(idx, result);
                    } else if (idx < 0) {
                        faction.classes.add(result);
                    }
                    rebuildAndShow();
                },
                this::rebuildAndShow));
    }

    private void deleteClass(int idx) {
        faction.classes.remove(idx);
        rebuild();
    }

    // ===== 载具操作 =====

    private void addVehicle() {
        String newId = "vehicle_" + (faction.vehicles.size() + 1);
        VehicleConfig vc = new VehicleConfig(newId, "新载具", "");
        editVehicleWith(vc, -1);
    }

    private void editVehicle(int idx) {
        editVehicleWith(faction.vehicles.get(idx), idx);
    }

    private void editVehicleWith(VehicleConfig vc, int idx) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new VehicleEditScreen(vc, faction.id, isOp, modeCounts, countdownSeconds,
                (result) -> {
                    if (idx >= 0 && idx < faction.vehicles.size()) {
                        faction.vehicles.set(idx, result);
                    } else if (idx < 0) {
                        faction.vehicles.add(result);
                    }
                    rebuildAndShow();
                },
                this::rebuildAndShow));
    }

    private void deleteVehicle(int idx) {
        faction.vehicles.remove(idx);
        rebuild();
    }

    // ===== 通用 =====

    private void rebuildAndShow() {
        rebuild();
        if (this.minecraft != null) this.minecraft.setScreen(this);
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private void save() {
        String name = nameInput != null ? nameInput.getValue().trim() : faction.name;
        String color = colorInput != null ? colorInput.getValue().trim() : faction.displayColor;
        String desc = descInput != null ? descInput.getValue().trim() : (faction.description != null ? faction.description : "");
        if (!name.isEmpty()) {
            if (!color.startsWith("#")) color = "#" + color;
            FactionConfig updated = new FactionConfig(faction.id, name, color, desc);
            updated.classes = faction.classes;
            updated.vehicles = faction.vehicles;
            updated.commanderExtraItems = faction.commanderExtraItems;
            updated.looseSpawn = faction.looseSpawn;
            if (captureSoundInput != null) faction.captureSound = captureSoundInput.getValue().trim();
            updated.captureSound = faction.captureSound;
            if (loseSoundInput != null) faction.loseSound = loseSoundInput.getValue().trim();
            updated.loseSound = faction.loseSound;
            AllPackets.getChannel().sendToServer(PacketFactionAction.update(updated));
        }
        goBack();
    }

    private void goBack() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new FactionSettingsScreen(isOp, modeCounts, countdownSeconds));
        }
    }

    // ===== 鼠标 =====

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (currentPage != 0) return super.mouseClicked(mx, my, button);
        if (button == 0) {
            if (isInSV(mx, my)) { draggingSV = true; updateSVFromMouse(mx, my); return true; }
            if (isInHue(mx, my)) { draggingHue = true; updateHueFromMouse(mx, my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (currentPage != 0) return super.mouseDragged(mx, my, b, dx, dy);
        if (draggingSV) { updateSVFromMouse(mx, my); return true; }
        if (draggingHue) { updateHueFromMouse(mx, my); return true; }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        draggingSV = false; draggingHue = false;
        return super.mouseReleased(mx, my, b);
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
        if (colorInput != null) colorInput.setValue(String.format("#%06X", rgb));
        syncingFromPicker = false;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        String subtitle = switch (currentPage) {
            case 0 -> "";
            case 1 -> " [载具]";
            case 2 -> " [指挥官物品]";
            case 3 -> " [效果设置]";
            default -> "";
        };
        g.drawCenteredString(this.font, "编辑阵营: " + faction.id + subtitle,
                this.width / 2, 28, TITLE_COLOR);

        // 当前页高亮
        int hx = switch (currentPage) {
            case 0 -> 12; case 1 -> 40; case 2 -> 62; case 3 -> 84; default -> 12;
        };
        int hw = currentPage == 3 ? 28 : 20;
        g.fill(hx, 26, hx + hw, 28, 0x44FFAA00);

        if (currentPage == 0) renderPage1(g);
        else if (currentPage == 1) renderPage2(g);
        else if (currentPage == 2) renderPage3(g);
        else if (currentPage == 3) renderPage4(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPage1(GuiGraphics g) {
        int rightX = this.width / 2 + 20;
        int inputBoxX = LEFT_X + LABEL_W + 4;
        int gap = 24;
        int y = 48;

        g.drawString(this.font, "名称:", LEFT_X, y + 5, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "颜色:", LEFT_X, y + 5, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "描述:", LEFT_X, y + 5, 0xAAAAAA);

        y += gap + 4;
        renderColorPicker(g, y);

        // 右栏：职业列表
        int classListTop = 48;
        g.drawString(this.font, "职业列表:", rightX, classListTop - 8, 0xFFFFFF);

        int ry = classListTop + 4;
        if (faction.classes == null || faction.classes.isEmpty()) {
            g.drawString(this.font, "（暂无职业）", rightX, ry + 1, 0x555555);
        } else {
            int bottom = this.height - 36;
            for (ClassConfig c : faction.classes) {
                if (ry + CLASS_ROW_H > bottom) break;
                g.drawString(this.font, c.name + "  [" + c.id + "]", rightX, ry, 0xCCCCCC);
                StringBuilder gear = new StringBuilder();
                if (c.helmet != null) gear.append(c.helmet);
                if (c.chestplate != null) {
                    if (gear.length() > 0) gear.append(", ");
                    gear.append(c.chestplate);
                }
                if (gear.length() > 0) {
                    g.drawString(this.font, gear.toString(), rightX, ry + 10, 0x888888);
                }
                ry += CLASS_ROW_H;
            }
        }
    }

    private void renderPage2(GuiGraphics g) {
        int vy = 48 + 28;
        if (faction.vehicles == null || faction.vehicles.isEmpty()) {
            g.drawString(this.font, "（暂无载具）", 12, vy + 1, 0x555555);
        } else {
            for (VehicleConfig v : faction.vehicles) {
                g.drawString(this.font, v.name + "  [" + v.id + "]", 12, vy, 0xCCCCCC);
                if (v.itemNbt != null && !v.itemNbt.isEmpty()) {
                    String shortNbt = v.itemNbt.length() > 60
                            ? v.itemNbt.substring(0, 57) + "..."
                            : v.itemNbt;
                    g.drawString(this.font, shortNbt, 12, vy + 10, 0x666666);
                }
                vy += VEHICLE_ROW_H;
            }
        }
    }

    private void renderPage4(GuiGraphics g) {
        int y = 48;
        int inputX = LEFT_X + 64;

        g.drawString(this.font, "§l效果设置", this.width / 2 - 30, y - 10, TITLE_COLOR);

        g.drawString(this.font, "占领音效:", LEFT_X, y + 25, 0xAAAAAA);
        g.drawString(this.font, "§7阵营占领据点时播放", inputX + 224, y, 0x666666);

        g.drawString(this.font, "失败音效:", LEFT_X, y + 51, 0xAAAAAA);
        g.drawString(this.font, "§7据点被敌对阵营占领时播放", inputX + 224, y + 28, 0x666666);

        g.drawString(this.font, "§7提示: 音效路径格式为 命名空间:id，如 battlelinesystem:cntake",
                LEFT_X, y + 100, 0x555555);
        g.drawString(this.font, "§7留空则不播放音效",
                LEFT_X, y + 116, 0x555555);
    }

    private void renderPage3(GuiGraphics g) {
        List<String> items = faction.commanderExtraItems;
        if (items == null || items.isEmpty()) {
            g.drawCenteredString(this.font, "（暂无指挥官额外物品）",
                    this.width / 2, this.height / 2 - 10, 0x555555);
            g.drawCenteredString(this.font, "指挥官部署时会额外获得这些物品",
                    this.width / 2, this.height / 2 + 10, 0x555555);
        } else {
            int y = 72;
            int bottomBarY = this.height - 36;
            for (int i = 0; i < items.size(); i++) {
                if (y + 18 > bottomBarY) break;
                String nbt = items.get(i);
                String shortText = nbt.length() > 50 ? nbt.substring(0, 47) + "..." : nbt;
                g.drawString(this.font, "§7" + (i + 1) + ". " + shortText, 12, y, 0xCCCCCC);
                y += 18;
            }
        }
    }

    private void renderColorPicker(GuiGraphics g, int cpY) {
        int svX = LEFT_X;
        int svY = cpY;
        int sliderY = svY + CP_SV_H + 4;
        int prevX = svX + CP_SV_W + 10;

        for (int px = 0; px < CP_SV_W; px++) {
            float s = (float) px / (CP_SV_W - 1);
            int bright = 0xFF000000 | hsvToRgb(currentHue, s, 1f);
            int dark = 0xFF000000 | hsvToRgb(currentHue, s, 0f);
            g.fillGradient(svX + px, svY, svX + px + 1, svY + CP_SV_H, bright, dark);
        }
        int sx = svX + Math.round(currentSat * (CP_SV_W - 1));
        int sy = svY + Math.round((1f - currentVal) * (CP_SV_H - 1));
        g.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF000000);
        g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFFFF);

        for (int px = 0; px < CP_SV_W; px++) {
            float h = (float) px / (CP_SV_W - 1) * 360f;
            g.fill(svX + px, sliderY, svX + px + 1, sliderY + CP_HUE_H,
                    0xFF000000 | hsvToRgb(h, 1f, 1f));
        }
        int hx = svX + Math.round(currentHue / 360f * (CP_SV_W - 1));
        g.fill(hx - 1, sliderY - 3, hx + 2, sliderY, 0xFFFFFFFF);
        g.fill(hx, sliderY - 2, hx + 1, sliderY, 0xFF000000);

        int previewRgb = hsvToRgb(currentHue, currentSat, currentVal);
        g.fill(prevX, svY, prevX + CP_PREVIEW_W, svY + CP_PREVIEW_H, 0xFF000000 | previewRgb);
        g.renderOutline(prevX, svY, CP_PREVIEW_W, CP_PREVIEW_H, 0xFFAAAAAA);
        g.drawString(this.font, "色相", svX, sliderY + CP_HUE_H + 2, 0x888888);
    }

    @Override
    public boolean isPauseScreen() { return false; }

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

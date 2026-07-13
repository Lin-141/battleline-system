package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.VehicleConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 载具编辑界面 — 第1页：基础设置 + NBT，第2页：部署脚本
 */
public class VehicleEditScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;
    private static final int LABEL_X = 30;
    private static final int INPUT_X = LABEL_X + 52;
    private static final int INPUT_W = 180;
    private static final int FIELD_H = 18;

    private static final int SLOT_SIZE = 18;
    private static final int INV_COLS = 9;
    private static final int HOTBAR_Y_OFFSET = 22;

    private final VehicleConfig vehicle;
    private final String factionId;
    private final boolean isOp;
    private final int[] modeCounts;
    private final int countdownSeconds;
    private final Consumer<VehicleConfig> onSave;
    private final Runnable onBack;

    // ---- 第1页 ----
    private EditBox idInput;
    private EditBox nameInput;
    private EditBox nbtInput;
    private EditBox maxCountInput;
    private EditBox cooldownInput;
    private Button typeButton;
    private int typeIndex;

    /** 部署音频输入 */
    private EditBox deploySoundInput;
    private Button deploySoundTargetButton;
    private int deploySoundTargetIndex;

    /** 当前显示的容器NBT预览（从物品栏点击 container 后设置） */
    private CompoundTag pendingContainerTag = null;

    /** 未截断的原始 NBT 字符串 */
    private String rawNbt = null;

    // ---- 第2页：部署脚本 ----
    private final List<String> scriptEntries = new ArrayList<>();
    private final List<EditBox> scriptInputs = new ArrayList<>();
    private int scrollOffset = 0;
    private int listTop, listBot;

    // ---- 翻页 ----
    private int currentPage = 0;

    private static final String[] PAGE_NAMES = {"基础设置", "部署脚本"};

    private static final String[] VEHICLE_TYPES = {"plane", "tank", "apc", "helicopter", "boat", "car", "land"};
    private static final String[] TYPE_COLORS = {"§b", "§6", "§a", "§d", "§3", "§c", "§e"};

    private static final String[] SOUND_TARGETS = {"all", "team", "enemy"};
    private static final String[] SOUND_TARGET_LABELS = {"全体", "同队", "敌方"};

    public VehicleEditScreen(VehicleConfig vehicle, String factionId, boolean isOp,
                             int[] modeCounts, int countdownSeconds,
                             Consumer<VehicleConfig> onSave, Runnable onBack) {
        super(Component.literal("编辑载具"));
        this.vehicle = new VehicleConfig(vehicle);
        this.factionId = factionId;
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
        this.onSave = onSave;
        this.onBack = onBack;
        if (vehicle.deployScripts != null) {
            this.scriptEntries.addAll(vehicle.deployScripts);
        }
    }

    @Override
    protected void init() {
        super.init();

        // ---- 左上角翻页按钮 ----
        int pageX = 8;
        int pageY = 8;
        this.addRenderableWidget(Button.builder(
                Component.literal("◀"), btn -> {
                    savePage();
                    currentPage = Math.max(0, currentPage - 1);
                    rebuild();
                })
                .pos(pageX, pageY).size(20, 20).build());
        pageX += 22;

        // 页面标签（点击切换）
        for (int p = 0; p < PAGE_NAMES.length; p++) {
            final int pi = p;
            String label = (p == currentPage ? "§e[" : "") + PAGE_NAMES[p] + (p == currentPage ? "]" : "");
            int labelW = this.font.width(Component.literal(PAGE_NAMES[p]).getString()) + 8;
            this.addRenderableWidget(Button.builder(
                    Component.literal(label), btn -> {
                        savePage();
                        currentPage = pi;
                        rebuild();
                    })
                    .pos(pageX, pageY).size(labelW, 20).build());
            pageX += labelW + 2;
        }

        pageX += 2;
        this.addRenderableWidget(Button.builder(
                Component.literal("▶"), btn -> {
                    savePage();
                    currentPage = Math.min(PAGE_NAMES.length - 1, currentPage + 1);
                    rebuild();
                })
                .pos(pageX, pageY).size(20, 20).build());

        // ---- 当前页内容 ----
        if (currentPage == 0) {
            initPage1();
        } else {
            initPage2();
        }

        // 底部按钮
        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .pos(this.width / 2 - 52, btnY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            savePage();
            onBack.run();
        }).pos(this.width / 2 + 8, btnY).size(44, 20).build());
    }

    private void rebuild() {
        savePage();
        this.clearWidgets();
        this.init();
    }

    /** 保存当前页编辑内容到内存 */
    private void savePage() {
        if (currentPage == 0) {
            if (idInput != null) vehicle.id = idInput.getValue().trim();
            if (nameInput != null) vehicle.name = nameInput.getValue().trim();
            if (nbtInput != null) vehicle.itemNbt = rawNbt != null ? rawNbt : nbtInput.getValue().trim();
            vehicle.type = VEHICLE_TYPES[typeIndex];
            if (maxCountInput != null) {
                try { vehicle.maxCount = Integer.parseInt(maxCountInput.getValue().trim()); }
                catch (NumberFormatException e) { vehicle.maxCount = 0; }
            }
            if (cooldownInput != null) {
                try { vehicle.cooldownSeconds = Integer.parseInt(cooldownInput.getValue().trim()); }
                catch (NumberFormatException e) { vehicle.cooldownSeconds = 0; }
            }
        } else {
            for (int i = 0; i < scriptInputs.size() && i < scriptEntries.size(); i++) {
                scriptEntries.set(i, scriptInputs.get(i).getValue());
            }
            if (deploySoundInput != null) vehicle.deploySound = deploySoundInput.getValue().trim();
            vehicle.deploySoundTarget = SOUND_TARGETS[deploySoundTargetIndex];
        }
    }

    // ===== 第1页：基础设置 =====

    private void initPage1() {
        int y = 45;
        int gap = 24;

        this.idInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, y, 120, FIELD_H, Component.literal("ID")));
        idInput.setMaxLength(32);
        idInput.setValue(vehicle.id != null ? vehicle.id : "");
        idInput.setFilter(s -> s.matches("[a-zA-Z0-9_]*"));

        y += gap;
        this.nameInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, y, INPUT_W, FIELD_H, Component.literal("名称")));
        nameInput.setMaxLength(32);
        nameInput.setValue(vehicle.name != null ? vehicle.name : "");

        y += gap;
        this.nbtInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, y, INPUT_W, FIELD_H, Component.literal("物品NBT")));
        nbtInput.setMaxLength(100000);
        nbtInput.setValue(vehicle.itemNbt != null ? vehicle.itemNbt : "");
        rawNbt = vehicle.itemNbt != null && !vehicle.itemNbt.isEmpty() ? vehicle.itemNbt : null;
        nbtInput.setHint(Component.literal("点击下方容器物品自动填入"));

        y += gap;
        typeIndex = indexOfType(vehicle.type);
        this.typeButton = this.addRenderableWidget(Button.builder(
                        Component.literal(currentTypeLabel()),
                        b -> cycleType())
                .pos(INPUT_X, y).size(100, FIELD_H).build());

        y += gap;
        this.maxCountInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, y, 50, FIELD_H, Component.literal("数量上限")));
        maxCountInput.setMaxLength(5);
        maxCountInput.setValue(String.valueOf(vehicle.maxCount));
        maxCountInput.setFilter(s -> s.matches("[0-9]*"));
        maxCountInput.setHint(Component.literal("0=无限"));

        y += gap;
        this.cooldownInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, y, 50, FIELD_H, Component.literal("冷却(秒)")));
        cooldownInput.setMaxLength(5);
        cooldownInput.setValue(String.valueOf(vehicle.cooldownSeconds));
        cooldownInput.setFilter(s -> s.matches("[0-9]*"));
        cooldownInput.setHint(Component.literal("0=无冷却"));
    }

    // ===== 第2页：部署脚本 =====

    private void initPage2() {
        int y = 40;
        int bottomBarY = this.height - 36;

        this.addRenderableWidget(Button.builder(
                Component.literal("+ 添加部署脚本"), btn -> {
                    scriptEntries.add("");
                    rebuild();
                })
                .pos(INPUT_X, y)
                .size(110, 18)
                .build());
        y += 24;

        listTop = y;
        listBot = bottomBarY - 4;

        scriptInputs.clear();
        int ey = listTop - scrollOffset;
        for (int i = 0; i < scriptEntries.size(); i++) {
            if (ey + FIELD_H < listTop || ey > listBot) { ey += FIELD_H + 4; continue; }
            final int idx = i;
            int cmdW = INPUT_W + 30 - 24;

            EditBox cmdBox = this.addRenderableWidget(
                    new EditBox(this.font, INPUT_X, ey, cmdW, FIELD_H, Component.literal("cmd" + i)));
            cmdBox.setMaxLength(200);
            cmdBox.setValue(scriptEntries.get(i));
            cmdBox.setHint(Component.literal("命令（@p=玩家）"));
            scriptInputs.add(cmdBox);

            this.addRenderableWidget(Button.builder(
                    Component.literal("X"), btn -> {
                        scriptEntries.remove(idx);
                        rebuild();
                    })
                    .pos(INPUT_X + cmdW + 2, ey)
                    .size(20, FIELD_H)
                    .build());

            ey += FIELD_H + 4;
        }

        // 部署音频（底部固定区域）
        int audioBaseY = listBot - 4;
        this.deploySoundInput = this.addRenderableWidget(
                new EditBox(this.font, INPUT_X, audioBaseY, 130, FIELD_H, Component.literal("部署音频")));
        deploySoundInput.setMaxLength(64);
        deploySoundInput.setValue(vehicle.deploySound != null ? vehicle.deploySound : "");
        deploySoundInput.setHint(Component.literal("如: battlelinesystem:usair"));

        deploySoundTargetIndex = indexOfSoundTarget(vehicle.deploySoundTarget != null ? vehicle.deploySoundTarget : "all");
        this.deploySoundTargetButton = this.addRenderableWidget(Button.builder(
                        Component.literal(currentSoundTargetLabel()),
                        b -> cycleSoundTarget())
                .pos(INPUT_X, audioBaseY + FIELD_H + 2).size(60, FIELD_H).build());
    }

    // ===== 保存 =====

    private void save() {
        savePage();
        vehicle.deployScripts = new ArrayList<>();
        for (String s : scriptEntries) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) vehicle.deployScripts.add(trimmed);
        }
        if (vehicle.name.isEmpty()) vehicle.name = vehicle.id;
        // 确保音频从 page2 控件同步
        if (deploySoundInput != null) vehicle.deploySound = deploySoundInput.getValue().trim();
        vehicle.deploySoundTarget = SOUND_TARGETS[deploySoundTargetIndex];
        onSave.accept(vehicle);
    }

    // ===== 物品栏点击（仅第1页） =====

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (currentPage == 0 && button == 0) {
            int invX = invLeft();
            int invY = invTop();
            int rows = inventoryRows();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < INV_COLS; col++) {
                    int sx = invX + col * SLOT_SIZE;
                    int sy = invY + row * SLOT_SIZE + (row == rows - 1 ? HOTBAR_Y_OFFSET : 0);
                    if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                        int slot = row * INV_COLS + col;
                        Inventory inv = getPlayerInventory();
                        if (inv != null && slot >= 0 && slot < inv.items.size()) {
                            ItemStack stack = inv.items.get(slot);
                            if (!stack.isEmpty()) {
                                onInventorySlotClicked(stack);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void onInventorySlotClicked(ItemStack stack) {
        if (stack.isEmpty()) return;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        if ("superbwarfare".equals(id.getNamespace()) && "container".equals(id.getPath())) {
            CompoundTag fullTag = stack.getOrCreateTag();
            if (fullTag.contains("BlockEntityTag")) {
                CompoundTag beTag = fullTag.getCompound("BlockEntityTag");
                String entityType = beTag.getString("EntityType");
                if (!entityType.isEmpty()) {
                    rawNbt = fullTag.toString();
                    nbtInput.setValue(rawNbt);
                    pendingContainerTag = beTag;
                }
            }
        }
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 标题
        String title = "编辑载具: " + factionId + "  (" + PAGE_NAMES[currentPage] + ")";
        g.drawCenteredString(this.font, title, this.width / 2, 15, TITLE_COLOR);

        if (currentPage == 0) {
            renderPage1(g);
            renderStackPreview(g);
            renderInventory(g, mouseX, mouseY);
        } else {
            renderPage2(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPage1(GuiGraphics g) {
        int y = 45 + 5;
        int gap = 24;
        g.drawString(this.font, "ID:", LABEL_X, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "名称:", LABEL_X, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "NBT:", LABEL_X, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "类型:", LABEL_X, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "上限:", LABEL_X, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "冷却(秒):", LABEL_X, y, 0xAAAAAA);
    }

    private void renderPage2(GuiGraphics g) {
        String hint = "每行一条命令，@p 会被替换为部署该载具的玩家名";
        int hintY = 27;
        g.drawString(this.font, hint, INPUT_X, hintY, 0x888888);
        if (scriptEntries.isEmpty()) {
            g.drawString(this.font, "（暂无部署脚本）", INPUT_X, listTop + 4, 0x555555);
        }
        int audioBaseY = listBot - 4;
        g.drawString(this.font, "部署音频:", LABEL_X, audioBaseY + 5, 0xAAAAAA);
        g.drawString(this.font, "播放对象:", LABEL_X, audioBaseY + FIELD_H + 7, 0xAAAAAA);
    }

    // ===== 物品预览 =====

    private void renderStackPreview(GuiGraphics g) {
        if (nbtInput == null) return;
        String nbt = nbtInput.getValue().trim();
        if (nbt.isEmpty()) return;

        ItemStack stack = ItemStack.EMPTY;
        if (nbt.startsWith("{")) {
            try {
                CompoundTag parsed = TagParser.parseTag(nbt);
                stack = ItemStack.of(parsed);
            } catch (Exception ignored) {}
        } else {
            try {
                int colon = nbt.indexOf(':');
                ResourceLocation rl;
                if (colon >= 0)
                    rl = new ResourceLocation(nbt.substring(0, colon), nbt.substring(colon + 1));
                else
                    rl = new ResourceLocation(nbt);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                if (item != null) stack = new ItemStack(item);
            } catch (Exception ignored) {}
        }

        int iconX = this.width - 60;
        int iconY = 45 + 5 + 24;
        if (!stack.isEmpty()) {
            g.renderFakeItem(stack, iconX, iconY);
            g.drawString(this.font, stack.getDisplayName().getString(),
                    iconX - 100, iconY + 5, 0xCCCCCC);
        }
        if (pendingContainerTag != null && pendingContainerTag.contains("EntityType")) {
            String type = pendingContainerTag.getString("EntityType");
            g.drawString(this.font, "载具: " + type, iconX - 100, iconY - 10, 0xFFAA00);
        }
    }

    // ===== 物品栏渲染 =====

    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        Inventory inv = getPlayerInventory();
        if (inv == null) return;

        int invX = invLeft();
        int invY = invTop();
        int rows = inventoryRows();

        g.drawString(this.font, "点击下方 §e容器§r 物品自动填入载具 NBT",
                invX, invY - 14, 0xAAAAAA);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slot = row * INV_COLS + col;
                int sx = invX + col * SLOT_SIZE;
                int sy = invY + row * SLOT_SIZE;
                if (row == rows - 1) sy += HOTBAR_Y_OFFSET;

                ItemStack stack = inv.items.get(slot);
                boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;

                int bgColor = hovered ? 0x80FFFFFF : 0x30FFFFFF;
                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, bgColor);

                if (!stack.isEmpty()) {
                    ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && "superbwarfare".equals(id.getNamespace())
                            && "container".equals(id.getPath())) {
                        g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x8033AA33);
                    }
                    g.renderFakeItem(stack, sx + 1, sy + 1);
                    g.renderItemDecorations(this.font, stack, sx + 1, sy + 1);
                }
            }
        }
    }

    private int invTop() {
        return this.height - 4 - (inventoryRows() * SLOT_SIZE + HOTBAR_Y_OFFSET);
    }

    private int invLeft() {
        return (this.width - INV_COLS * SLOT_SIZE) / 2;
    }

    private int inventoryRows() {
        Inventory inv = getPlayerInventory();
        if (inv == null) return 0;
        return Math.max(1, (inv.items.size() + INV_COLS - 1) / INV_COLS);
    }

    private Inventory getPlayerInventory() {
        if (this.minecraft == null || this.minecraft.player == null) return null;
        return this.minecraft.player.getInventory();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ===== 第2页滚轮 =====

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentPage == 1) {
            int totalH = scriptEntries.size() * (FIELD_H + 4);
            int visibleH = listBot - listTop;
            int maxScroll = Math.max(0, totalH - visibleH);
            if (maxScroll > 0) {
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * 20)));
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ===== 类型 =====

    private int indexOfType(String type) {
        for (int i = 0; i < VEHICLE_TYPES.length; i++) {
            if (VEHICLE_TYPES[i].equals(type)) return i;
        }
        return 1;
    }

    private void cycleType() {
        typeIndex = (typeIndex + 1) % VEHICLE_TYPES.length;
        typeButton.setMessage(Component.literal(currentTypeLabel()));
    }

    private String currentTypeLabel() {
        return TYPE_COLORS[typeIndex] + "● " + VEHICLE_TYPES[typeIndex];
    }

    private int indexOfSoundTarget(String target) {
        for (int i = 0; i < SOUND_TARGETS.length; i++) {
            if (SOUND_TARGETS[i].equals(target)) return i;
        }
        return 0;
    }

    private void cycleSoundTarget() {
        deploySoundTargetIndex = (deploySoundTargetIndex + 1) % SOUND_TARGETS.length;
        deploySoundTargetButton.setMessage(Component.literal(currentSoundTargetLabel()));
    }

    private String currentSoundTargetLabel() {
        return SOUND_TARGET_LABELS[deploySoundTargetIndex];
    }

}

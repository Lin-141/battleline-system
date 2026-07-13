package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.ClassVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 变体编辑 — 名称 + 5个装备槽 + 额外物品 + 解锁条件
 */
public class ClassVariantEditScreen extends Screen {

    private static final int FORM_X = 10;
    private static final int LABEL_W = 55;
    private static final int DISP_W = 85;
    private static final int BTN_W = 30;
    private static final int ROW_GAP = 18;
    private static final String[] SLOT_LABELS = {"头盔:", "胸甲:", "护腿:", "靴子:", "副手:"};
    private static final int INV_COLS = 9, SLOT_SZ = 18;

    private final ClassVariant var;
    private final Consumer<ClassVariant> onSave;
    private final Runnable onBack;
    private final String factionId, classId;

    private EditBox nameInput, condInput;
    private int selectingSlot = -1;
    private int invGridX, invGridY;
    private int scrollY = 0;

    public ClassVariantEditScreen(ClassVariant var, Consumer<ClassVariant> onSave, Runnable onBack,
                                  String factionId, String classId, boolean isOp,
                                  int[] modeCounts, int countdownSeconds) {
        super(Component.literal("编辑变体"));
        this.var = new ClassVariant(var);
        this.onSave = onSave;
        this.onBack = onBack;
        this.factionId = factionId;
        this.classId = classId;
    }

    private List<String> extras() {
        if (var.extraItems == null) var.extraItems = new ArrayList<>();
        return var.extraItems;
    }

    private String getSlot(int idx) {
        return switch (idx) {
            case 0 -> var.helmet; case 1 -> var.chestplate;
            case 2 -> var.leggings; case 3 -> var.boots;
            case 4 -> var.offHand; default -> null;
        };
    }
    private void setSlot(int idx, String nbt) {
        switch (idx) {
            case 0 -> var.helmet = nbt; case 1 -> var.chestplate = nbt;
            case 2 -> var.leggings = nbt; case 3 -> var.boots = nbt;
            case 4 -> var.offHand = nbt;
        }
    }

    private static ItemStack parseItem(String s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (s.startsWith("{")) {
            try { return ItemStack.of(TagParser.parseTag(s)); }
            catch (Exception e) { return ItemStack.EMPTY; }
        }
        try {
            int colon = s.indexOf(':');
            net.minecraft.resources.ResourceLocation rl;
            if (colon >= 0) rl = new net.minecraft.resources.ResourceLocation(s.substring(0, colon), s.substring(colon + 1));
            else rl = new net.minecraft.resources.ResourceLocation(s);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception e) { return ItemStack.EMPTY; }
    }
    private static String itemToNbt(ItemStack st) {
        if (st.isEmpty()) return null;
        CompoundTag tag = new CompoundTag(); st.save(tag); return tag.toString();
    }

    @Override
    protected void init() {
        super.init();
        nameInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 120, 18, Component.literal("名称")));
        nameInput.setMaxLength(20);
        nameInput.setValue(var.name);
        condInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 120, 18, Component.literal("如: permission:xxx 或 留空")));
        condInput.setMaxLength(60);
        condInput.setValue(var.unlockCondition != null ? var.unlockCondition : "");

        int rightX = this.width / 2 + 20;
        invGridX = rightX + (this.width - rightX - 8 - INV_COLS * SLOT_SZ) / 2;
        invGridY = 48;

        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> saveAndClose())
                .pos(this.width / 2 - 102, btnY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("一键映射"), b -> snapshotEquip())
                .pos(this.width / 2 - 50, btnY).size(56, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            if (this.minecraft != null) onBack.run();
        }).pos(this.width / 2 + 14, btnY).size(44, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < this.width / 2) {
            this.scrollY += (int)(delta * 15);
            if (this.scrollY > 0) this.scrollY = 0;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 优先让底部按钮（保存/一键映射/返回）处理
        if (super.mouseClicked(mx, my, button)) return true;
        // 左侧内容区调整 Y 偏移
        if (mx < this.width / 2) my -= scrollY;
        int baseY = 60;
        int selBgX = FORM_X + LABEL_W + DISP_W + 6;
        for (int i = 0; i < 5; i++) {
            int ry = baseY + i * ROW_GAP;
            if (mx >= selBgX && mx <= selBgX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                selectingSlot = i; return true;
            }
            if (mx >= selBgX + BTN_W + 3 && mx <= selBgX + BTN_W * 2 + 3 && my >= ry - 1 && my <= ry + 17) {
                setSlot(i, null); selectingSlot = -1; return true;
            }
        }

        // 额外物品
        int extraTitleY = baseY + 5 * ROW_GAP + 20;
        List<String> ext = extras();
        int addBtnY = extraTitleY + ext.size() * ROW_GAP;
        if (mx >= selBgX && mx <= selBgX + 64 && my >= addBtnY && my <= addBtnY + 18) {
            selectingSlot = 99; return true;
        }
        for (int i = 0; i < ext.size(); i++) {
            int ry = extraTitleY + i * ROW_GAP;
            if (mx >= selBgX && mx <= selBgX + 30 && my >= ry - 1 && my <= ry + 17) {
                ext.remove(i); return true;
            }
        }

        if (selectingSlot >= 0 && this.minecraft != null && this.minecraft.player != null) {
            Inventory inv = this.minecraft.player.getInventory();
            for (int s = 0; s < inv.items.size(); s++) {
                int ix = invGridX + (s % INV_COLS) * SLOT_SZ;
                int iy = invGridY + (s / INV_COLS) * SLOT_SZ;
                if (mx >= ix && mx <= ix + 16 && my >= iy && my <= iy + 16) {
                    ItemStack st = inv.items.get(s);
                    if (!st.isEmpty()) {
                        if (selectingSlot == 99) extras().add(itemToNbt(st.copy()));
                        else setSlot(selectingSlot, itemToNbt(st.copy()));
                        selectingSlot = -1;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void saveAndClose() {
        var.name = nameInput.getValue().trim();
        if (var.name.isEmpty()) var.name = "变体";
        String cond = condInput.getValue().trim();
        var.unlockCondition = cond.isEmpty() ? null : cond;
        if (this.minecraft != null) onSave.accept(var);
    }

    private void snapshotEquip() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        var p = this.minecraft.player;
        var.helmet  = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        var.chestplate = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        var.leggings = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        var.boots    = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        var.offHand  = itemToNbt(p.getOffhandItem());
        if (var.extraItems == null) var.extraItems = new ArrayList<>();
        else var.extraItems.clear();
        Inventory inv = p.getInventory();
        for (int s = 0; s < inv.items.size(); s++) {
            ItemStack st = inv.items.get(s);
            if (!st.isEmpty()) var.extraItems.add(itemToNbt(st.copy()));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, "编辑变体: " + factionId + " / " + classId + " / " + var.id,
                this.width / 2, 14, 0xFFAA00);

        // === 可滚动区域 ===
        g.pose().pushPose();
        g.pose().translate(0, scrollY, 0);

        int mY = mouseY - scrollY;

        int baseY = 60;
        int selBgX = FORM_X + LABEL_W + DISP_W + 6;
        for (int i = 0; i < 5; i++) {
            int ry = baseY + i * ROW_GAP;
            ItemStack item = parseItem(getSlot(i));
            g.drawString(this.font, SLOT_LABELS[i], FORM_X, ry + 2, 0xAAAAAA);
            int dispX = FORM_X + LABEL_W;
            boolean sel = selectingSlot == i;
            g.fill(dispX, ry - 1, dispX + DISP_W, ry + 17, sel ? 0xAA40A040 : 0x30000000);
            if (sel) g.renderOutline(dispX, ry - 1, DISP_W, 18, 0xFF55FF55);
            if (!item.isEmpty())
                g.drawString(this.font, trimName(item.getHoverName().getString(), DISP_W - 4), dispX + 2, ry + 3, 0xFFFFFF);
            else g.drawString(this.font, "（无）", dispX + 2, ry + 3, 0x555555);

            boolean hS = mouseX >= selBgX && mouseX <= selBgX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(selBgX, ry - 1, selBgX + BTN_W, ry + 17, hS ? 0xAA0066CC : 0x80004488);
            g.drawCenteredString(this.font, "选", selBgX + BTN_W / 2, ry + 2, 0xFFFFFF);
            int clX = selBgX + BTN_W + 3;
            boolean hC = mouseX >= clX && mouseX <= clX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(clX, ry - 1, clX + BTN_W, ry + 17, hC ? 0xAA888888 : 0x80444444);
            g.drawCenteredString(this.font, "清", clX + BTN_W / 2, ry + 2, 0xCCCCCC);
        }

        int extraTitleY = baseY + 5 * ROW_GAP + 20;
        g.drawString(this.font, "额外物品:", FORM_X, extraTitleY, 0xFFAA00);
        List<String> ext = extras();
        for (int i = 0; i < ext.size(); i++) {
            int ry = extraTitleY + i * ROW_GAP;
            ItemStack item = parseItem(ext.get(i));
            g.fill(FORM_X + LABEL_W, ry - 1, FORM_X + LABEL_W + DISP_W, ry + 17, 0x30000000);
            g.drawString(this.font, !item.isEmpty() ? trimName(item.getHoverName().getString(), DISP_W - 4) : "?",
                    FORM_X + LABEL_W + 2, ry + 3, 0xCCCCCC);
            boolean hRm = mouseX >= selBgX && mouseX <= selBgX + 30 && mY >= ry - 1 && mY <= ry + 17;
            g.fill(selBgX, ry - 1, selBgX + 30, ry + 17, hRm ? 0xAA884444 : 0x80444444);
            g.drawCenteredString(this.font, "×", selBgX + 15, ry + 2, 0xFF8888);
        }
        int addBtnY = extraTitleY + ext.size() * ROW_GAP;
        boolean aH = mouseX >= selBgX && mouseX <= selBgX + 64 && mY >= addBtnY && mY <= addBtnY + 18;
        boolean aSel = selectingSlot == 99;
        g.fill(selBgX, addBtnY, selBgX + 64, addBtnY + 18, aSel ? 0xAA40A040 : (aH ? 0xAA0066CC : 0x80004488));
        if (aSel) g.renderOutline(selBgX, addBtnY, 64, 18, 0xFF55FF55);
        g.drawCenteredString(this.font, "+ 添加额外", selBgX + 32, addBtnY + 3, 0xFFFFFF);

        String hint = selectingSlot == 99 ? "从右侧背包选择物品添加" : selectingSlot >= 0 ? "从背包选物品 → " + SLOT_LABELS[selectingSlot] : "点「选」再点背包物品";
        g.drawString(this.font, hint, FORM_X, addBtnY + 22, 0x666666);

        // === 可滚动区域结束 ===
        g.pose().popPose();

        if (this.minecraft != null && this.minecraft.player != null)
            renderInventoryGrid(g, mouseX, mouseY);

        // 右侧下方：变体名 + 解锁条件
        int rightX = this.width / 2 + 20;
        int invBot = invGridY + 4 * SLOT_SZ + 8;
        int rightFieldY = invBot + 6;
        g.drawString(this.font, "变体名:", rightX, rightFieldY + 1, 0xFFFFFF);
        nameInput.setX(rightX + 40);
        nameInput.setY(rightFieldY);

        int condFieldY = rightFieldY + 22;
        g.drawString(this.font, "解锁条件:", rightX, condFieldY + 1, 0xFFCC00);
        condInput.setX(rightX + 52);
        condInput.setY(condFieldY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderInventoryGrid(GuiGraphics g, int mouseX, int mouseY) {
        Inventory inv = this.minecraft.player.getInventory();
        int rightX = this.width / 2 + 20;
        g.drawString(this.font, "背包 (" + inv.items.size() + "格)", rightX, invGridY - 14, 0xFFAA00);
        g.drawString(this.font, "热键栏", rightX, invGridY + 4 * SLOT_SZ + 4, 0x888888);
        for (int s = 0; s < inv.items.size(); s++) {
            int col = s % INV_COLS, row = s / INV_COLS;
            int ix = invGridX + col * SLOT_SZ, iy = invGridY + row * SLOT_SZ;
            ItemStack st = inv.items.get(s);
            boolean hov = mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16;
            int bg = row >= 3 ? 0x60000000 : 0x40000000;
            if (hov) bg = 0x80FFFFFF;
            g.fill(ix - 1, iy - 1, ix + 17, iy + 17, bg);
            if (!st.isEmpty()) { g.renderItem(st, ix, iy); g.renderItemDecorations(this.font, st, ix, iy); }
            if (row >= 3) g.drawString(this.font, String.valueOf((col + 1) % 10), ix + 1, iy + 9, 0x66FF66, false);
        }
    }

    private static String trimName(String name, int maxW) {
        if (name.length() <= maxW / 6) return name;
        return name.substring(0, maxW / 6 - 1) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}

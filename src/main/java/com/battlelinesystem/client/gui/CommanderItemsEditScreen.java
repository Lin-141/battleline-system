package com.battlelinesystem.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 指挥官额外物品编辑界面 — 仅保留添加额外物品功能（从背包选择物品）
 */
public class CommanderItemsEditScreen extends Screen {

    private static final int ITEM_ROW_H = 18;
    private static final int TITLE_COLOR = 0xFFAA00;

    private static final int INV_COLS = 9;
    private static final int SLOT_SZ = 18;

    private final List<String> items;
    private final Consumer<List<String>> onSave;
    private final Runnable onBack;

    private boolean selectingSlot = false;
    private int invGridX, invGridY;

    public CommanderItemsEditScreen(List<String> items, Consumer<List<String>> onSave, Runnable onBack) {
        super(Component.literal("编辑指挥官额外物品"));
        this.items = new ArrayList<>(items != null ? items : new ArrayList<>());
        this.onSave = onSave;
        this.onBack = onBack;
    }

    @Override
    protected void init() {
        super.init();
        int rightX = this.width / 2 + 20;
        invGridX = rightX + (this.width - rightX - 8 - INV_COLS * SLOT_SZ) / 2;
        invGridY = 48;

        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            onSave.accept(new ArrayList<>(items));
            if (this.minecraft != null) this.onBack.run();
        }).pos(this.width / 2 - 52, btnY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            if (this.minecraft != null) this.onBack.run();
        }).pos(this.width / 2 + 8, btnY).size(44, 20).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int listX = 10;
        int listY = 40;
        int displayW = 160;

        // "+ 添加额外" 按钮
        int addBtnY = listY + items.size() * ITEM_ROW_H;
        if (mx >= listX && mx <= listX + 70 && my >= addBtnY && my <= addBtnY + 18) {
            selectingSlot = true;
            return true;
        }

        // 删除按钮
        for (int i = 0; i < items.size(); i++) {
            int ry = listY + i * ITEM_ROW_H;
            int delX = listX + displayW + 4;
            if (mx >= delX && mx <= delX + 30 && my >= ry && my <= ry + 16) {
                items.remove(i);
                selectingSlot = false;
                return true;
            }
        }

        // 右侧背包物品点击
        if (selectingSlot && this.minecraft != null && this.minecraft.player != null) {
            Inventory inv = this.minecraft.player.getInventory();
            for (int s = 0; s < inv.items.size(); s++) {
                int ix = invGridX + (s % INV_COLS) * SLOT_SZ;
                int iy = invGridY + (s / INV_COLS) * SLOT_SZ;
                if (mx >= ix && mx <= ix + 16 && my >= iy && my <= iy + 16) {
                    ItemStack st = inv.items.get(s);
                    if (!st.isEmpty()) {
                        items.add(itemToNbt(st.copy()));
                        selectingSlot = false;
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int midX = this.width / 2;

        g.drawCenteredString(this.font, "编辑指挥官额外物品", midX, 14, TITLE_COLOR);

        int listX = 10;
        int listY = 40;
        int displayW = 160;
        int listBottom = this.height - 48;

        // 物品列表
        g.drawString(this.font, "武器/物品列表:", listX, listY - 12, 0xAAAAAA);
        for (int i = 0; i < items.size(); i++) {
            int ry = listY + i * ITEM_ROW_H;
            if (ry + ITEM_ROW_H > listBottom) break;

            ItemStack item = parseItem(items.get(i));
            String displayName = !item.isEmpty()
                    ? trimName(item.getHoverName().getString(), displayW - 4)
                    : "?无效?";
            int nameColor = !item.isEmpty() ? 0xCCCCCC : 0x884444;

            g.fill(listX, ry, listX + displayW, ry + 16, 0x30000000);
            g.drawString(this.font, displayName, listX + 2, ry + 3, nameColor);

            // 删除按钮
            int delX = listX + displayW + 4;
            boolean hRm = mouseX >= delX && mouseX <= delX + 30 && mouseY >= ry && mouseY <= ry + 16;
            g.fill(delX, ry, delX + 30, ry + 16, hRm ? 0xAA884444 : 0x80444444);
            g.drawCenteredString(this.font, "×", delX + 15, ry + 3, 0xFF8888);
        }

        // "+ 添加额外" 按钮
        int addBtnY = listY + items.size() * ITEM_ROW_H;
        if (addBtnY + 18 < listBottom) {
            boolean addHov = mouseX >= listX && mouseX <= listX + 70 && mouseY >= addBtnY && mouseY <= addBtnY + 18;
            g.fill(listX, addBtnY, listX + 70, addBtnY + 18,
                    selectingSlot ? 0xAA40A040 : (addHov ? 0xAA0066CC : 0x80004488));
            if (selectingSlot) g.renderOutline(listX, addBtnY, 70, 18, 0xFF55FF55);
            g.drawCenteredString(this.font, "+ 添加额外", listX + 35, addBtnY + 3, 0xFFFFFF);
        }

        // 提示
        int hintY = addBtnY + 24;
        if (hintY + 10 < listBottom) {
            String hint = selectingSlot ? "从右侧背包选择物品添加" : "点「+ 添加额外」,再从右侧背包点物品";
            g.drawString(this.font, hint, listX, hintY, 0x666666);
        }

        // 右侧背包
        int rightX = this.width / 2 + 20;
        g.drawString(this.font, "背包", rightX + (this.width - rightX - 40) / 2,
                invGridY - 12, 0xAAAAAA);
        if (this.minecraft != null && this.minecraft.player != null) {
            renderInventory(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        Inventory inv = this.minecraft.player.getInventory();
        for (int s = 0; s < inv.items.size(); s++) {
            int ix = invGridX + (s % INV_COLS) * SLOT_SZ;
            int iy = invGridY + (s / INV_COLS) * SLOT_SZ;
            ItemStack st = inv.items.get(s);
            g.fill(ix, iy, ix + 16, iy + 16, st.isEmpty() ? 0x40888888 : 0x80AAAAAA);
            g.renderOutline(ix, iy, 16, 16, 0xFF555555);
            if (!st.isEmpty()) {
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(this.font, st, ix, iy);
                if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) {
                    g.renderTooltip(this.font, st, mouseX, mouseY);
                }
            }
        }
    }

    // ===== 工具方法 =====

    private static ItemStack parseItem(String s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (s.startsWith("{")) {
            try {
                return ItemStack.of(TagParser.parseTag(s));
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
        try {
            int colon = s.indexOf(':');
            net.minecraft.resources.ResourceLocation rl;
            if (colon >= 0)
                rl = new net.minecraft.resources.ResourceLocation(s.substring(0, colon), s.substring(colon + 1));
            else
                rl = new net.minecraft.resources.ResourceLocation(s);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static String itemToNbt(ItemStack st) {
        if (st.isEmpty()) return null;
        CompoundTag tag = new CompoundTag();
        st.save(tag);
        return tag.toString();
    }

    private String trimName(String name, int maxWidth) {
        if (this.font.width(name) <= maxWidth) return name;
        while (this.font.width(name + "...") > maxWidth && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        return name + "...";
    }

    // ===== 无障碍/关闭 =====
}

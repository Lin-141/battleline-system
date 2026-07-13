package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.GunAttachmentConfig;
import com.battlelinesystem.faction.GunAttachmentOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 枪械配件池编辑 — 管理某个 GunId 下各槽位可选的配件列表。
 * 左侧：配件槽位管理  右侧：背包（把配件拖入对应槽位）
 */
public class GunAttachmentPoolEditScreen extends Screen {

    private static final int INV_COLS = 9;
    private static final int SLOT_SZ = 18;

    private final GunAttachmentConfig pool;
    private final Consumer<GunAttachmentConfig> onSave;
    private final Runnable onBack;
    private EditBox gunIdInput;
    private int editingSlot = -1; // -1=none, 0-4=slot

    private int invGridX, invGridY;

    public GunAttachmentPoolEditScreen(GunAttachmentConfig pool,
                                        Consumer<GunAttachmentConfig> onSave, Runnable onBack) {
        super(Component.literal("编辑配件池"));
        this.pool = new GunAttachmentConfig(pool);
        this.onSave = onSave;
        this.onBack = onBack;
    }

    @Override
    protected void init() {
        super.init();
        gunIdInput = this.addRenderableWidget(
                new EditBox(this.font, this.width / 2 - 120, 38, 120, 18, Component.literal("tacz:ak47")));
        gunIdInput.setMaxLength(60);
        gunIdInput.setValue(pool.gunId != null ? pool.gunId : "");
        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> { pool.gunId = gunIdInput.getValue().trim(); onSave.accept(pool); })
                .pos(this.width / 2 - 104, btnY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> onBack.run())
                .pos(this.width / 2 - 52, btnY).size(44, 20).build());

        // 右侧背包布局
        int rightX = this.width / 2 + 20;
        invGridX = rightX + (this.width - rightX - 6 - INV_COLS * SLOT_SZ) / 2;
        invGridY = 72;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 优先让底部按钮（保存/返回）处理
        if (super.mouseClicked(mx, my, button)) return true;
        int slotX = 10;
        int slotW = 160;
        int slotStartY = 72;

        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            int sy = slotStartY + i * 24;
            List<GunAttachmentOption> opts = pool.getBySlot(GunAttachmentConfig.SLOTS[i]);
            // Toggle slot
            if (mx >= slotX && mx <= slotX + slotW && my >= sy && my <= sy + 18) {
                editingSlot = (editingSlot == i) ? -1 : i;
                return true;
            }
            // Option delete buttons
            if (editingSlot == i && opts != null) {
                int optY = sy + 22;
                for (int oi = 0; oi < opts.size(); oi++) {
                    int oy = optY + oi * 16;
                    if (mx >= slotX + 4 && mx <= slotX + 20 && my >= oy && my <= oy + 14) {
                        opts.remove(oi); return true;
                    }
                }
            }
        }

        // 右侧背包：未展开槽位时点枪械 → 填入 GunId；展开槽位时点配件 → 添加配件
        if (this.minecraft != null && this.minecraft.player != null) {
            Inventory inv = this.minecraft.player.getInventory();
            for (int s = 0; s < inv.items.size(); s++) {
                int ix = invGridX + (s % INV_COLS) * SLOT_SZ;
                int iy = invGridY + (s / INV_COLS) * SLOT_SZ;
                if (mx >= ix && mx <= ix + 16 && my >= iy && my <= iy + 16) {
                    ItemStack st = inv.items.get(s);
                    if (!st.isEmpty()) {
                        if (editingSlot == -1) {
                            // 未展开任何槽位 → 点击枪械快速填入 GunId
                            String gunId = parseGunId(st);
                            if (gunId != null) {
                                pool.gunId = gunId;
                                gunIdInput.setValue(gunId);
                                return true;
                            }
                        } else {
                            // 展开了槽位 → 添加配件
                            GunAttachmentOption opt = parseAttachment(st);
                            if (opt != null) {
                                List<GunAttachmentOption> cur = pool.getBySlot(GunAttachmentConfig.SLOTS[editingSlot]);
                                if (cur == null) cur = new ArrayList<>();
                                boolean dup = false;
                                for (GunAttachmentOption o : cur) {
                                    if (o.attachmentId.equals(opt.attachmentId)) { dup = true; break; }
                                }
                                if (!dup) {
                                    cur.add(opt);
                                    switch (editingSlot) {
                                        case 0 -> pool.scopes = cur;
                                        case 1 -> pool.muzzles = cur;
                                        case 2 -> pool.grips = cur;
                                        case 3 -> pool.stocks = cur;
                                        case 4 -> pool.lasers = cur;
                                    }
                                }
                                return true;
                            }
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** 从 tacz 枪械 ItemStack 解析出 GunId */
    private String parseGunId(ItemStack st) {
        String regName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        if (!regName.contains("tacz")) return null;
        CompoundTag tag = st.getTag();
        if (tag == null) return null;
        // tacz 枪械格式: tag -> { GunId: "tacz:ak47" }
        String gunId = tag.getString("GunId");
        return gunId.isEmpty() ? null : gunId;
    }

    /** 从 tacz 配件 ItemStack 解析出 AttachmentId 和显示名 */
    private GunAttachmentOption parseAttachment(ItemStack st) {
        String regName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        if (!regName.contains("tacz")) return null;
        CompoundTag tag = st.getTag();
        if (tag == null) return null;
        // tacz 配件格式: tag -> { AttachmentId: "tacz:sight_exp3" }
        String attId = tag.getString("AttachmentId");
        if (attId.isEmpty()) {
            // 可能是物品本身注册名
            attId = regName;
        }
        String displayName = st.getHoverName().getString();
        return new GunAttachmentOption(attId, displayName);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, "编辑配件池", this.width / 2, 14, 0xFFAA00);
        g.drawString(this.font, "GunId:", 10, 43, 0xFFFFFF);

        int slotX = 10;
        int slotW = 160;
        int slotStartY = 72;

        // 左侧：槽位列表
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            String slot = GunAttachmentConfig.SLOTS[i];
            int sy = slotStartY + i * 24;
            List<GunAttachmentOption> opts = pool.getBySlot(slot);
            int count = opts != null ? opts.size() : 0;

            boolean hovS = mouseX >= slotX && mouseX <= slotX + slotW && mouseY >= sy && mouseY <= sy + 18;
            boolean sel = editingSlot == i;
            int bg = sel ? 0xAA40A040 : (hovS ? 0xAA3366AA : 0x80000000);
            g.fill(slotX, sy, slotX + slotW, sy + 18, bg);
            g.drawString(this.font, GunAttachmentConfig.SLOT_LABELS[i] + " (" + count + ")", slotX + 6, sy + 3, 0xFFFFFF);

            if (sel && opts != null) {
                int optY = sy + 22;
                for (int oi = 0; oi < opts.size(); oi++) {
                    int oy = optY + oi * 16;
                    boolean hRm = mouseX >= slotX + 4 && mouseX <= slotX + 20 && mouseY >= oy && mouseY <= oy + 14;
                    g.fill(slotX + 4, oy, slotX + 20, oy + 14, hRm ? 0xAA884444 : 0x80444444);
                    g.drawCenteredString(this.font, "×", slotX + 12, oy + 2, 0xFF8888);
                    g.drawString(this.font, opts.get(oi).displayName + " (" + opts.get(oi).attachmentId + ")",
                            slotX + 26, oy + 2, 0xCCCCCC);
                }
                int hintY = optY + opts.size() * 16 + 4;
                g.drawString(this.font, "> 从右侧背包点击配件即可添加", slotX + 4, hintY, 0x667744);
            }
        }

        // 右侧：背包
        if (this.minecraft != null && this.minecraft.player != null) {
            renderInventoryGrid(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderInventoryGrid(GuiGraphics g, int mouseX, int mouseY) {
        Inventory inv = this.minecraft.player.getInventory();
        int rightX = this.width / 2 + 20;
        g.drawString(this.font, "背包 (" + inv.items.size() + "格)", rightX, invGridY - 14, 0xFFAA00);

        if (editingSlot >= 0) {
            g.drawString(this.font, "点击配件添加到: " + GunAttachmentConfig.SLOT_LABELS[editingSlot], rightX, invGridY - 28, 0x55FF55);
        } else {
            g.drawString(this.font, "点击枪械填入 GunId", rightX, invGridY - 28, 0x55AAFF);
        }

        for (int s = 0; s < inv.items.size(); s++) {
            int ix = invGridX + (s % INV_COLS) * SLOT_SZ;
            int iy = invGridY + (s / INV_COLS) * SLOT_SZ;
            ItemStack st = inv.items.get(s);
            if (!st.isEmpty()) {
                boolean hov = mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16;
                if (hov) {
                    String regName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    if (regName.contains("tacz")) {
                        boolean isGun = st.getTag() != null && st.getTag().contains("GunId");
                        int hl = isGun ? 0xAA3366CC : 0xAA40A040;
                        g.fill(ix, iy, ix + 16, iy + 16, hl);
                    }
                }
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(this.font, st, ix, iy);
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}

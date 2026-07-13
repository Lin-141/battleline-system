package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.GunAttachmentConfig;
import com.battlelinesystem.faction.GunAttachmentOption;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * 枪械改装界面 — 自动确认模式。
 * 中间：3D geo 模型预览（可拖拽旋转 + 滚轮缩放）
 * 底部：5 个配件槽位，悬停向上展开列表，再次点击卸下
 */
public class GunModScreen extends Screen {

    private final String gunNbtStr;
    private final List<GunAttachmentConfig> pools;
    private final Consumer<String> onSave;
    private final Runnable onClose;

    private CompoundTag gunTag;
    private String workingNbt;
    private String gunId;
    private String gunDisplayName;
    private ItemStack previewGunStack = ItemStack.EMPTY;

    private int[] selectedOptIdx = {-1, -1, -1, -1, -1};

    private final ItemStack[][] attachmentIcons = new ItemStack[5][];
    private static final ResourceLocation TACZ_ATTACHMENT = new ResourceLocation("tacz", "attachment");

    private float modelRotY = 30;
    private float modelRotX = 15;
    private float modelZoom = 60f;
    private boolean isDragging = false;
    private double dragLastX, dragLastY;

    private int hoveredSlot = -1;

    // 布局参数
    private int prevCX, prevCY, prevW, prevH;
    private int panelX, panelY, panelW, colW;
    private static final int PREV_H = 120;
    private static final int SLOT_BAR_H = 40;
    private static final int SLOT_GAP = 4;   // 列间距
    private static final int CLOSE_BTN_SZ = 18;

    public GunModScreen(String gunNbtStr, List<GunAttachmentConfig> pools, Consumer<String> onSave, Runnable onClose) {
        super(Component.literal("枪械改装"));
        this.gunNbtStr = gunNbtStr;
        this.workingNbt = gunNbtStr;
        this.pools = pools;
        this.onSave = onSave;
        this.onClose = onClose;
    }

    @Override
    protected void init() {
        super.init();
        parseGun();
        readCurrentAttachments();
        loadAttachmentIcons();
        rebuildPreviewStack();

        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> saveAndClose())
                .pos(this.width - 50, this.height - 30).size(44, 20).build());
    }

    private void saveAndClose() {
        onSave.accept(workingNbt);
        if (this.minecraft != null) onClose.run();
    }

    private void calcLayout() {
        prevCX = this.width / 2;
        prevCY = this.height / 2;
        prevW = Math.min(this.width - 40, 500);
        prevH = PREV_H;

        panelW = Math.min(this.width - 40, 5 * 56 + SLOT_GAP * 4);
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height - SLOT_BAR_H - 10; // 与返回按钮底部对齐
        colW = (panelW - SLOT_GAP * 4) / 5;
    }

    /** 返回第 i 列的左边缘 X */
    private int slotColX(int i) {
        return panelX + i * (colW + SLOT_GAP);
    }

    private void parseGun() {
        try {
            gunTag = TagParser.parseTag(gunNbtStr);
            CompoundTag tag = gunTag.getCompound("tag");
            gunId = tag.getString("GunId");
            ItemStack base = ItemStack.of(gunTag);
            gunDisplayName = base.getHoverName().getString();
        } catch (Exception e) {
            gunDisplayName = "未知枪械";
        }
    }

    private void readCurrentAttachments() {
        if (gunTag == null) return;
        CompoundTag tag = gunTag.getCompound("tag");
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            String nbtKey = GunAttachmentConfig.nbtKey(GunAttachmentConfig.SLOTS[i]);
            if (tag.contains(nbtKey)) {
                CompoundTag att = tag.getCompound(nbtKey);
                if (att.contains("tag") && !att.isEmpty()) {
                    String attId = att.getCompound("tag").getString("AttachmentId");
                    List<GunAttachmentOption> opts = getSlotOptions(GunAttachmentConfig.SLOTS[i]);
                    if (opts != null) {
                        for (int oi = 0; oi < opts.size(); oi++) {
                            if (attId.equals(opts.get(oi).attachmentId)) {
                                selectedOptIdx[i] = oi; break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadAttachmentIcons() {
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            List<GunAttachmentOption> opts = getSlotOptions(GunAttachmentConfig.SLOTS[i]);
            if (opts == null) {
                attachmentIcons[i] = new ItemStack[0];
                continue;
            }
            attachmentIcons[i] = new ItemStack[opts.size()];
            for (int oi = 0; oi < opts.size(); oi++) {
                attachmentIcons[i][oi] = buildAttachmentStack(opts.get(oi).attachmentId);
            }
        }
    }

    private ItemStack buildAttachmentStack(String attId) {
        CompoundTag root = new CompoundTag();
        root.putString("id", TACZ_ATTACHMENT.toString());
        root.putByte("Count", (byte) 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("AttachmentId", attId);
        root.put("tag", tag);
        return ItemStack.of(root);
    }

    private void rebuildPreviewStack() {
        if (gunTag == null) return;
        CompoundTag preTag = gunTag.copy();
        CompoundTag tag = preTag.getCompound("tag");
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            String nbtKey = GunAttachmentConfig.nbtKey(GunAttachmentConfig.SLOTS[i]);
            int idx = selectedOptIdx[i];
            if (idx < 0) {
                tag.remove(nbtKey);
            } else {
                List<GunAttachmentOption> opts = getSlotOptions(GunAttachmentConfig.SLOTS[i]);
                if (opts != null && idx < opts.size()) {
                    String attId = opts.get(idx).attachmentId;
                    CompoundTag attTag = new CompoundTag();
                    attTag.putString("id", TACZ_ATTACHMENT.toString());
                    attTag.putByte("Count", (byte) 1);
                    CompoundTag inner = new CompoundTag();
                    inner.putString("AttachmentId", attId);
                    attTag.put("tag", inner);
                    tag.put(nbtKey, attTag);
                }
            }
        }
        preTag.put("tag", tag);
        workingNbt = preTag.toString();
        previewGunStack = ItemStack.of(preTag);
    }

    private GunAttachmentConfig findPool() {
        if (pools == null || gunId == null) return null;
        for (GunAttachmentConfig g : pools) if (gunId.equals(g.gunId)) return g;
        return null;
    }

    private List<GunAttachmentOption> getSlotOptions(String slot) {
        GunAttachmentConfig p = findPool();
        return p != null ? p.getBySlot(slot) : null;
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        // 左上角 X 按钮
        if (mx >= 6 && mx <= 6 + CLOSE_BTN_SZ && my >= 6 && my <= 6 + CLOSE_BTN_SZ) {
            saveAndClose();
            return true;
        }

        // 点击展开列表中的选项（再次点击已选中项 = 卸下）
        if (hoveredSlot >= 0) {
            int iconSz = 18, iconGap = 4;
            ItemStack[] icons = attachmentIcons[hoveredSlot];
            if (icons.length > 0) {
                int maxVisible = Math.min(icons.length, 6);
                int expandH = maxVisible * (iconSz + iconGap) + 10;
                int expandTop = panelY - expandH;
                int colX = slotColX(hoveredSlot);
                int iconX = colX + colW / 2 - iconSz / 2; // 居中

                for (int vi = 0; vi < maxVisible; vi++) {
                    int iy = expandTop + 4 + vi * (iconSz + iconGap);
                    if (mx >= iconX && mx <= iconX + iconSz && my >= iy && my <= iy + iconSz) {
                        // 再次点击已选中 → 卸下
                        selectedOptIdx[hoveredSlot] = (selectedOptIdx[hoveredSlot] == vi) ? -1 : vi;
                        rebuildPreviewStack();
                        return true;
                    }
                }
            }
        }

        // 全屏拖拽
        isDragging = true;
        dragLastX = mx;
        dragLastY = my;
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx, my);
        hoveredSlot = -1;
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY - 150 && my <= panelY + SLOT_BAR_H) {
            for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
                int colX = slotColX(i);
                if (mx >= colX && mx <= colX + colW) {
                    if (attachmentIcons[i].length > 0) {
                        hoveredSlot = i; break;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (isDragging) {
            modelRotY += (float)(mx - dragLastX) * 0.5f;
            modelRotX += (float)(my - dragLastY) * 0.5f;
            modelRotX = Math.max(-60, Math.min(60, modelRotX));
            dragLastX = mx;
            dragLastY = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        modelZoom += (float)delta * 2f;
        modelZoom = Math.max(10, Math.min(80, modelZoom));
        return true;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        markTaczGuiRender();
        calcLayout();

        g.drawCenteredString(this.font, "枪械改装", this.width / 2, 10, 0xFFAA00);
        g.drawCenteredString(this.font, gunDisplayName, this.width / 2, 24, 0xFFFFFF);
        // 当前配件汇总
        g.drawCenteredString(this.font, currentAttachmentsSummary(), this.width / 2, 38, 0x55FF55);

        renderCloseBtn(g, mouseX, mouseY);
        renderPreview(g);
        renderSlotBar(g, mouseX, mouseY);

        if (hoveredSlot >= 0) {
            renderExpandedList(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCloseBtn(GuiGraphics g, int mx, int my) {
        int x = 6, y = 6, sz = CLOSE_BTN_SZ;
        boolean hov = mx >= x && mx <= x + sz && my >= y && my <= y + sz;
        int bg = hov ? 0xCCAA3333 : 0xAA444444;
        g.fill(x, y, x + sz, y + sz, bg);
        g.renderOutline(x, y, sz, sz, 0xAA888888);
        g.drawCenteredString(this.font, "X", x + sz / 2, y + 4, 0xFFFFFF);
    }

    private void renderPreview(GuiGraphics g) {
        g.fill(prevCX - prevW / 2, prevCY - prevH / 2,
                prevCX + prevW / 2, prevCY + prevH / 2, 0x30000000);

        if (!previewGunStack.isEmpty() && this.minecraft != null && this.minecraft.level != null) {
            g.pose().pushPose();
            g.pose().translate(prevCX, prevCY, 150);
            g.pose().scale(modelZoom, modelZoom, modelZoom);
            g.pose().scale(1, -1, 1);
            g.pose().mulPose(Axis.YP.rotationDegrees(modelRotY));
            g.pose().mulPose(Axis.XP.rotationDegrees(modelRotX));

            MultiBufferSource.BufferSource buf = this.minecraft.renderBuffers().bufferSource();
            this.minecraft.getItemRenderer().renderStatic(
                    previewGunStack, ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    g.pose(), buf, this.minecraft.level, 0);
            buf.endBatch();
            g.pose().popPose();
        }
    }

    private void renderSlotBar(GuiGraphics g, int mx, int my) {
        int iconSz = 16;
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            int colX = slotColX(i);
            String label = GunAttachmentConfig.SLOT_LABELS[i];
            int idx = selectedOptIdx[i];
            ItemStack[] icons = attachmentIcons[i];
            boolean hov = hoveredSlot == i;

            int bgCol = hov ? 0xAA444444 : 0x80000000;
            g.fill(colX, panelY, colX + colW, panelY + SLOT_BAR_H, bgCol);
            int outlineCol = hov ? 0xAAFFCC66 : 0xAA666666;
            g.renderOutline(colX, panelY, colW, SLOT_BAR_H, outlineCol);

            // 标签（框内偏上）
            g.drawCenteredString(this.font, label, colX + colW / 2, panelY + 2, 0xFFCC66);

            // 配件图标（偏下居中）
            if (idx >= 0 && idx < icons.length) {
                g.renderItem(icons[idx], colX + colW / 2 - iconSz / 2, panelY + 12);
            }
        }
    }

    private void renderExpandedList(GuiGraphics g, int mx, int my) {
        int slotIdx = hoveredSlot;
        int colX = slotColX(slotIdx);
        int iconSz = 18, iconGap = 4;
        ItemStack[] icons = attachmentIcons[slotIdx];
        if (icons.length == 0) return;

        int maxVisible = Math.min(icons.length, 6);
        int expandH = maxVisible * (iconSz + iconGap) + 10;
        int expandTop = panelY - expandH;

        // 展开背景
        g.fill(colX, expandTop, colX + colW, panelY, 0xCC333333);
        g.renderOutline(colX, expandTop, colW, panelY - expandTop, 0xAAFFCC66);

        // 图标列表（居中，无文字）
        int iconX = colX + colW / 2 - iconSz / 2;
        int iconY = expandTop + 4;
        for (int vi = 0; vi < maxVisible; vi++) {
            int iy = iconY + vi * (iconSz + iconGap);
            boolean hov = mx >= iconX && mx <= iconX + iconSz && my >= iy && my <= iy + iconSz;

            // 悬停时轻微高亮底色
            if (hov) {
                g.fill(iconX - 1, iy - 1, iconX + iconSz + 1, iy + iconSz + 1, 0x403366AA);
            }

            g.renderItem(icons[vi], iconX, iy);
            g.renderItemDecorations(this.font, icons[vi], iconX, iy);

            if (hov) {
                List<GunAttachmentOption> opts = getSlotOptions(GunAttachmentConfig.SLOTS[slotIdx]);
                String tip = opts != null && vi < opts.size() ? opts.get(vi).displayName : "?";
                g.drawCenteredString(this.font, tip, this.width / 2, 52, 0xFFFFFF);
            }
        }
    }

    /** 汇总当前已装配的配件名称 */
    private String currentAttachmentsSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GunAttachmentConfig.SLOTS.length; i++) {
            int idx = selectedOptIdx[i];
            if (idx >= 0) {
                List<GunAttachmentOption> opts = getSlotOptions(GunAttachmentConfig.SLOTS[i]);
                if (opts != null && idx < opts.size()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(opts.get(idx).displayName);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "未装配配件";
    }

    /** 截断过长文字使其不超过指定宽度 */
    private String trimText(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String dots = "..";
        while (s.length() > 1 && this.font.width(s + dots) > maxW) s = s.substring(0, s.length() - 1);
        return s + dots;
    }

    private static void markTaczGuiRender() {
        try {
            Class<?> clz = Class.forName("com.tacz.guns.util.RenderDistance");
            clz.getMethod("markGuiRenderTimestamp").invoke(null);
        } catch (Exception ignored) {}
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        onSave.accept(workingNbt);
        if (this.minecraft != null) onClose.run();
    }
}

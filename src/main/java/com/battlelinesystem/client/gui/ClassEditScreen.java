package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.ClassVariant;
import com.battlelinesystem.faction.GunAttachmentConfig;
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
 * 职业编辑界面 — 装备槽位 + 额外物品（从背包选择） + 变体管理
 * 左：装备槽位 + 额外物品列表 + 变体列表  右：背包
 */
public class ClassEditScreen extends Screen {

    private static final int FORM_X = 10;
    private static final int LABEL_W = 52;
    private static final int DISP_W = 80;
    private static final int BTN_W = 30;
    private static final int ROW_GAP = 18;
    private static final int TITLE_COLOR = 0xFFAA00;
    private static final String[] SLOT_LABELS = {"头盔:", "胸甲:", "护腿:", "靴子:", "副手:"};

    private final ClassConfig cls;
    private final String factionId;
    private final boolean isOp;
    private final int[] modeCounts;
    private final int countdownSeconds;
    private final Consumer<ClassConfig> onSave;
    private final Runnable onBack;

    private EditBox nameInput;
    private EditBox hiddenItemsInput;
    private EditBox maxPlayersInput;
    private EditBox deathCostInput;
    /** -1=无选中, 0-4=装备槽位, 99=额外物品模式 */
    private int selectingSlot = -1;

    private static final int INV_COLS = 9;
    private static final int SLOT_SZ = 18;
    private int invGridX, invGridY;
    private int scrollY = 0;
    private int addVarY; // 计算在 render/mouseClicked 中复用

    public ClassEditScreen(ClassConfig cls, String factionId, boolean isOp,
                           int[] modeCounts, int countdownSeconds,
                           Consumer<ClassConfig> onSave, Runnable onBack) {
        super(Component.literal("编辑职业"));
        this.cls = new ClassConfig(cls);
        this.factionId = factionId;
        this.isOp = isOp;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
        this.onSave = onSave;
        this.onBack = onBack;
    }

    private List<String> extras() {
        if (cls.extraItems == null) cls.extraItems = new ArrayList<>();
        return cls.extraItems;
    }

    private List<ClassVariant> vars() {
        if (cls.variants == null) cls.variants = new ArrayList<>();
        return cls.variants;
    }

    private List<GunAttachmentConfig> pools() {
        if (cls.gunAttachmentPools == null) cls.gunAttachmentPools = new ArrayList<>();
        return cls.gunAttachmentPools;
    }

    // ===== 读写槽位 =====
    private String getSlot(int idx) {
        return switch (idx) {
            case 0 -> cls.helmet;
            case 1 -> cls.chestplate;
            case 2 -> cls.leggings;
            case 3 -> cls.boots;
            case 4 -> cls.offHand;
            default -> null;
        };
    }
    private void setSlot(int idx, String nbt) {
        switch (idx) {
            case 0 -> cls.helmet = nbt;
            case 1 -> cls.chestplate = nbt;
            case 2 -> cls.leggings = nbt;
            case 3 -> cls.boots = nbt;
            case 4 -> cls.offHand = nbt;
        }
    }

    // ===== NBT 工具 =====
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
        CompoundTag tag = new CompoundTag();
        st.save(tag);
        return tag.toString();
    }

    // ===== 初始化 =====
    @Override
    protected void init() {
        super.init();
        nameInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 120, 18, Component.literal("名称")));
        nameInput.setMaxLength(20);
        nameInput.setValue(cls.name);
        hiddenItemsInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 160, 16, Component.literal("如: tacz:ammo_box")));
        hiddenItemsInput.setMaxLength(200);
        hiddenItemsInput.setValue(cls.hiddenDisplayItems != null ? String.join(", ", cls.hiddenDisplayItems) : "");
        maxPlayersInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 40, 16, Component.literal("0")));
        maxPlayersInput.setMaxLength(3);
        maxPlayersInput.setFilter(s -> s.isEmpty() || s.matches("[0-9]*"));
        maxPlayersInput.setValue(String.valueOf(cls.maxPlayers));
        deathCostInput = this.addRenderableWidget(
                new EditBox(this.font, 0, 0, 40, 16, Component.literal("1")));
        deathCostInput.setMaxLength(3);
        deathCostInput.setFilter(s -> s.isEmpty() || s.matches("[0-9]*"));
        deathCostInput.setValue(String.valueOf(cls.deathCost));
        int rightX = this.width / 2 + 20;
        invGridX = rightX + (this.width - rightX - 8 - INV_COLS * SLOT_SZ) / 2;
        invGridY = 48;
        int btnY = this.height - 36;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> saveAndClose())
                .pos(this.width / 2 - 102, btnY).size(44, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("一键映射"), b -> snapshotEquip())
                .pos(this.width / 2 - 50, btnY).size(56, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
                    if (this.minecraft != null) onBack.run(); })
                .pos(this.width / 2 + 14, btnY).size(44, 20).build());
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

    // ===== 鼠标点击 =====
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 优先让底部按钮（保存/一键映射/返回）处理
        if (super.mouseClicked(mx, my, button)) return true;
        // 左侧内容区调整 Y 偏移
        if (mx < this.width / 2) my -= scrollY;
        int baseY = 50;
        int selBgX = FORM_X + LABEL_W + DISP_W + 6;
        int clrBgX = selBgX + BTN_W + 3;

        // 装备槽位
        for (int i = 0; i < 5; i++) {
            int ry = baseY + i * ROW_GAP;
            if (mx >= selBgX && mx <= selBgX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                selectingSlot = i; return true;
            }
            if (mx >= clrBgX && mx <= clrBgX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                setSlot(i, null); selectingSlot = -1; return true;
            }
        }

        // 额外物品
        int extraTitleY = baseY + 5 * ROW_GAP + 20;
        List<String> ext = extras();
        int addBtnX = FORM_X + LABEL_W + DISP_W + 6;
        int addBtnY = extraTitleY + ext.size() * ROW_GAP;
        if (mx >= addBtnX && mx <= addBtnX + 64 && my >= addBtnY && my <= addBtnY + 18) {
            selectingSlot = 99; return true;
        }
        for (int i = 0; i < ext.size(); i++) {
            int ry = extraTitleY + i * ROW_GAP;
            if (mx >= addBtnX && mx <= addBtnX + 30 && my >= ry - 1 && my <= ry + 17) {
                ext.remove(i); return true;
            }
        }

        // 变体列表
        int variantTitleY = addBtnY + 30;
        List<ClassVariant> vr = vars();
        for (int i = 0; i < vr.size(); i++) {
            int ry = variantTitleY + i * ROW_GAP;
            int editX = selBgX; // same column
            int delX = editX + BTN_W + 6;
            if (mx >= editX && mx <= editX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                openVariantEditor(i); return true;
            }
            if (mx >= delX && mx <= delX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                vr.remove(i); return true;
            }
        }
        addVarY = variantTitleY + vr.size() * ROW_GAP;
        if (mx >= addBtnX && mx <= addBtnX + 64 && my >= addVarY && my <= addVarY + 18) {
            addNewVariant(); return true;
        }

        // 右侧背包物品点击
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

        // 隐藏护甲图标开关（右侧下方）
        int rightClickX = this.width / 2 + 20;
        int invBot = invGridY + 4 * SLOT_SZ + 8;
        int rightFieldY = invBot + 6;
        int armorChkY = rightFieldY + 22 + 20;
        if (mx >= rightClickX && mx <= rightClickX + 90 && my >= armorChkY && my <= armorChkY + 16) {
            cls.hideArmorIcons = !cls.hideArmorIcons;
            return true;
        }

        // 枪械配件池 编辑/删除/添加
        int poolTitleY = addVarY + 46;
        List<GunAttachmentConfig> gp = pools();
        for (int pi = 0; pi < gp.size(); pi++) {
            int ry = poolTitleY + (pi + 1) * ROW_GAP;
            if (mx >= selBgX && mx <= selBgX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                // 编辑
                final int pidx = pi;
                if (this.minecraft != null) this.minecraft.setScreen(
                        new GunAttachmentPoolEditScreen(new GunAttachmentConfig(gp.get(pidx)), edited -> {
                            gp.set(pidx, edited);
                            if (this.minecraft != null) this.minecraft.setScreen(this);
                        }, () -> { if (this.minecraft != null) this.minecraft.setScreen(this); }));
                return true;
            }
            int dlX = selBgX + BTN_W + 3;
            if (mx >= dlX && mx <= dlX + BTN_W && my >= ry - 1 && my <= ry + 17) {
                gp.remove(pi);
                return true;
            }
        }
        int addPoolY = poolTitleY + (gp.size() + 1) * ROW_GAP;
        if (mx >= selBgX && mx <= selBgX + 76 && my >= addPoolY && my <= addPoolY + 18) {
            if (this.minecraft != null) this.minecraft.setScreen(
                    new GunAttachmentPoolEditScreen(new GunAttachmentConfig("tacz:ak47"), edited -> {
                        gp.add(edited);
                        if (this.minecraft != null) this.minecraft.setScreen(this);
                    }, () -> { if (this.minecraft != null) this.minecraft.setScreen(this); }));
            return true;
        }

        return false;
    }

    // ===== 变体操作 =====
    private void addNewVariant() {
        if (this.minecraft == null) return;
        ClassVariant v = new ClassVariant("v" + (vars().size() + 1), "新变体");
        this.minecraft.setScreen(new ClassVariantEditScreen(v,
                edited -> { vars().add(edited); this.minecraft.setScreen(this); },
                () -> this.minecraft.setScreen(this),
                factionId, cls.id, isOp, modeCounts, countdownSeconds));
    }
    private void openVariantEditor(int idx) {
        if (this.minecraft == null) return;
        ClassVariant v = new ClassVariant(vars().get(idx));
        int i = idx;
        this.minecraft.setScreen(new ClassVariantEditScreen(v,
                edited -> { vars().set(i, edited); this.minecraft.setScreen(this); },
                () -> this.minecraft.setScreen(this),
                factionId, cls.id, isOp, modeCounts, countdownSeconds));
    }

    // ===== 保存 =====
    private void saveAndClose() {
        cls.name = nameInput.getValue().trim();
        if (cls.name.isEmpty()) cls.name = "Unnamed";
        // 解析隐藏物品
        String raw = hiddenItemsInput.getValue().trim();
        if (raw.isEmpty()) {
            cls.hiddenDisplayItems = null;
        } else {
            cls.hiddenDisplayItems = new ArrayList<>();
            for (String part : raw.split("[,，\\n]+")) {
                String s = part.trim();
                if (!s.isEmpty()) cls.hiddenDisplayItems.add(s);
            }
        }
        // 保存 maxPlayers
        try { cls.maxPlayers = Integer.parseInt(maxPlayersInput.getValue().trim()); }
        catch (NumberFormatException e) { cls.maxPlayers = 0; }
        if (cls.maxPlayers < 0) cls.maxPlayers = 0;
        if (cls.maxPlayers > 100) cls.maxPlayers = 100;
        // 保存 deathCost
        try { cls.deathCost = Integer.parseInt(deathCostInput.getValue().trim()); }
        catch (NumberFormatException e) { cls.deathCost = 1; }
        if (cls.deathCost < 0) cls.deathCost = 0;
        if (this.minecraft != null) onSave.accept(cls);
    }

    private void snapshotEquip() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        var p = this.minecraft.player;
        cls.helmet  = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        cls.chestplate = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        cls.leggings = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        cls.boots    = itemToNbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        cls.offHand  = itemToNbt(p.getOffhandItem());
        if (cls.extraItems == null) cls.extraItems = new ArrayList<>();
        else cls.extraItems.clear();
        Inventory inv = p.getInventory();
        for (int s = 0; s < inv.items.size(); s++) {
            ItemStack st = inv.items.get(s);
            if (!st.isEmpty()) cls.extraItems.add(itemToNbt(st.copy()));
        }
    }

    // ===== 渲染 =====
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int midX = this.width / 2;
        g.drawCenteredString(this.font, "编辑职业: " + factionId + " / " + cls.id, midX, 14, TITLE_COLOR);

        // === 可滚动区域开始 ===
        g.pose().pushPose();
        g.pose().translate(0, scrollY, 0);

        // 滚动区鼠标 Y（poseStack 只影响渲染，需手动偏移 hover 判定）
        int mY = mouseY - scrollY;

        // 装备槽位
        int baseY = 50;
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
            else
                g.drawString(this.font, "（无）", dispX + 2, ry + 3, 0x555555);

            int selX = FORM_X + LABEL_W + DISP_W + 6;
            boolean hovS = mouseX >= selX && mouseX <= selX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(selX, ry - 1, selX + BTN_W, ry + 17, hovS ? 0xAA0066CC : 0x80004488);
            g.drawCenteredString(this.font, "选", selX + BTN_W / 2, ry + 2, 0xFFFFFF);
            int clrX = selX + BTN_W + 3;
            boolean hovC = mouseX >= clrX && mouseX <= clrX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(clrX, ry - 1, clrX + BTN_W, ry + 17, hovC ? 0xAA888888 : 0x80444444);
            g.drawCenteredString(this.font, "清", clrX + BTN_W / 2, ry + 2, 0xCCCCCC);
        }

        // 额外物品
        int extraTitleY = baseY + 5 * ROW_GAP + 20;
        g.drawString(this.font, "额外物品:", FORM_X, extraTitleY, 0xFFAA00);
        g.drawString(this.font, "(放入背包)", FORM_X + 52, extraTitleY, 0x666666);
        List<String> ext = extras();
        int addBtnX = FORM_X + LABEL_W + DISP_W + 6;
        for (int i = 0; i < ext.size(); i++) {
            int ry = extraTitleY + i * ROW_GAP;
            ItemStack item = parseItem(ext.get(i));
            g.fill(FORM_X + LABEL_W, ry - 1, FORM_X + LABEL_W + DISP_W, ry + 17, 0x30000000);
            if (!item.isEmpty())
                g.drawString(this.font, trimName(item.getHoverName().getString(), DISP_W - 4), FORM_X + LABEL_W + 2, ry + 3, 0xCCCCCC);
            else
                g.drawString(this.font, "?无效?", FORM_X + LABEL_W + 2, ry + 3, 0x884444);
            boolean hRm = mouseX >= addBtnX && mouseX <= addBtnX + 30 && mY >= ry - 1 && mY <= ry + 17;
            g.fill(addBtnX, ry - 1, addBtnX + 30, ry + 17, hRm ? 0xAA884444 : 0x80444444);
            g.drawCenteredString(this.font, "×", addBtnX + 15, ry + 2, 0xFF8888);
        }
        int addBtnY = extraTitleY + ext.size() * ROW_GAP;
        boolean addHov = mouseX >= addBtnX && mouseX <= addBtnX + 64 && mY >= addBtnY && mY <= addBtnY + 18;
        boolean addSel = selectingSlot == 99;
        g.fill(addBtnX, addBtnY, addBtnX + 64, addBtnY + 18, addSel ? 0xAA40A040 : (addHov ? 0xAA0066CC : 0x80004488));
        if (addSel) g.renderOutline(addBtnX, addBtnY, 64, 18, 0xFF55FF55);
        g.drawCenteredString(this.font, "+ 添加额外", addBtnX + 32, addBtnY + 3, 0xFFFFFF);

        // ===== 变体列表 =====
        int variantTitleY = addBtnY + 30;
        g.drawString(this.font, "职业变体:", FORM_X, variantTitleY, 0xFFCC00);
        g.drawString(this.font, "(多配置可选)", FORM_X + 52, variantTitleY, 0x666666);

        List<ClassVariant> vr = vars();
        int selBgX = FORM_X + LABEL_W + DISP_W + 6;
        for (int i = 0; i < vr.size(); i++) {
            int ry = variantTitleY + i * ROW_GAP;
            ClassVariant v = vr.get(i);
            String line = v.name + (v.unlockCondition != null && !v.unlockCondition.isEmpty()
                    ? "  [需要: " + v.unlockCondition + "]" : "");
            g.fill(FORM_X + LABEL_W, ry - 1, FORM_X + LABEL_W + DISP_W, ry + 17, 0x30000000);
            g.drawString(this.font, trimName(line, DISP_W - 4), FORM_X + LABEL_W + 2, ry + 3, 0xCCCC88);

            // 编辑按钮
            int edX = selBgX;
            boolean hEd = mouseX >= edX && mouseX <= edX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(edX, ry - 1, edX + BTN_W, ry + 17, hEd ? 0xAA0066CC : 0x80004488);
            g.drawCenteredString(this.font, "编", edX + BTN_W / 2, ry + 2, 0xFFFFFF);
            // 删除按钮
            int dlX = edX + BTN_W + 6;
            boolean hDl = mouseX >= dlX && mouseX <= dlX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(dlX, ry - 1, dlX + BTN_W, ry + 17, hDl ? 0xAA884444 : 0x80444444);
            g.drawCenteredString(this.font, "删", dlX + BTN_W / 2, ry + 2, 0xFF8888);
        }

        addVarY = variantTitleY + vr.size() * ROW_GAP;
        boolean avHov = mouseX >= addBtnX && mouseX <= addBtnX + 64 && mY >= addVarY && mY <= addVarY + 18;
        g.fill(addBtnX, addVarY, addBtnX + 64, addVarY + 18, avHov ? 0xAA0066CC : 0x80004488);
        g.drawCenteredString(this.font, "+ 添加变体", addBtnX + 32, addVarY + 3, 0xFFFFFF);

        // 提示
        String hint = selectingSlot == 99 ? "从右侧背包选择物品添加为额外物品"
                : selectingSlot >= 0 ? "从右侧背包选择物品 → " + SLOT_LABELS[selectingSlot]
                : "点「选」/「+ 添加额外」,再从右侧背包点物品";
        g.drawString(this.font, hint, FORM_X, addVarY + 22, 0x666666);

        // 枪械配件池
        int poolTitleY = addVarY + 46;
        g.drawString(this.font, "枪械配件池:", FORM_X, poolTitleY, 0xFFAA00);
        g.drawString(this.font, "(改装时可选的配件列表)", FORM_X + 66, poolTitleY, 0x666666);
        List<GunAttachmentConfig> gp = pools();
        int poolBgX = FORM_X + LABEL_W;
        for (int pi = 0; pi < gp.size(); pi++) {
            int ry = poolTitleY + (pi + 1) * ROW_GAP;
            g.fill(poolBgX, ry - 1, poolBgX + DISP_W + 10, ry + 17, 0x30000000);
            int totalOpts = (gp.get(pi).scopes != null ? gp.get(pi).scopes.size() : 0)
                    + (gp.get(pi).muzzles != null ? gp.get(pi).muzzles.size() : 0)
                    + (gp.get(pi).grips != null ? gp.get(pi).grips.size() : 0)
                    + (gp.get(pi).stocks != null ? gp.get(pi).stocks.size() : 0)
                    + (gp.get(pi).lasers != null ? gp.get(pi).lasers.size() : 0);
            g.drawString(this.font, gp.get(pi).gunId + " (" + totalOpts + "配件)", poolBgX + 2, ry + 3, 0xCCCCCC);
            boolean hEd = mouseX >= selBgX && mouseX <= selBgX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(selBgX, ry - 1, selBgX + BTN_W, ry + 17, hEd ? 0xAA0066CC : 0x80004488);
            g.drawCenteredString(this.font, "编", selBgX + BTN_W / 2, ry + 2, 0xFFFFFF);
            int dlX = selBgX + BTN_W + 3;
            boolean hDl = mouseX >= dlX && mouseX <= dlX + BTN_W && mY >= ry - 1 && mY <= ry + 17;
            g.fill(dlX, ry - 1, dlX + BTN_W, ry + 17, hDl ? 0xAA884444 : 0x80444444);
            g.drawCenteredString(this.font, "删", dlX + BTN_W / 2, ry + 2, 0xFF8888);
        }
        int addPoolY = poolTitleY + (gp.size() + 1) * ROW_GAP;
        boolean aph = mouseX >= selBgX && mouseX <= selBgX + 76 && mY >= addPoolY && mY <= addPoolY + 18;
        g.fill(selBgX, addPoolY, selBgX + 76, addPoolY + 18, aph ? 0xAA0066CC : 0x80004488);
        g.drawCenteredString(this.font, "+ 添加配件池", selBgX + 38, addPoolY + 3, 0xFFFFFF);

        // === 可滚动区域结束 ===
        g.pose().popPose();

        // 右侧背包
        if (this.minecraft != null && this.minecraft.player != null)
            renderInventoryGrid(g, mouseX, mouseY);

        // 右侧下方：名称 + 隐藏显示 + 护甲开关
        int rightX = this.width / 2 + 20;
        int invBot = invGridY + 4 * SLOT_SZ + 8;
        int rightFieldY = invBot + 6;
        g.drawString(this.font, "名称:", rightX, rightFieldY + 1, 0xFFFFFF);
        nameInput.setX(rightX + 32);
        nameInput.setY(rightFieldY);

        int hideFieldY = rightFieldY + 22;
        g.drawString(this.font, "隐藏显示:", rightX, hideFieldY + 1, 0xFF8888);
        hiddenItemsInput.setX(rightX + 52);
        hiddenItemsInput.setY(hideFieldY);

        int maxFieldY = hideFieldY + 20;
        g.drawString(this.font, "人数上限%:", rightX, maxFieldY + 1, 0xFFCC66);
        g.drawString(this.font, "(0=不限)", rightX + 52 + 44, maxFieldY + 1, 0x666666);
        maxPlayersInput.setX(rightX + 52);
        maxPlayersInput.setY(maxFieldY);

        int deathCostFieldY = maxFieldY + 20;
        g.drawString(this.font, "死亡扣分:", rightX, deathCostFieldY + 1, 0xFFCC66);
        deathCostInput.setX(rightX + 52);
        deathCostInput.setY(deathCostFieldY);

        int armorChkY = deathCostFieldY + 20;
        boolean hideA = cls.hideArmorIcons;
        boolean chkHov = mouseX >= rightX && mouseX <= rightX + 90 && mouseY >= armorChkY && mouseY <= armorChkY + 16;
        g.fill(rightX, armorChkY, rightX + 90, armorChkY + 16, chkHov ? 0xAA666666 : 0x80444444);
        String chkText = (hideA ? "[✓] " : "[  ] ") + "隐藏护甲图标";
        g.drawString(this.font, chkText, rightX + 3, armorChkY + 2, hideA ? 0xFFCC44 : 0x888888);

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

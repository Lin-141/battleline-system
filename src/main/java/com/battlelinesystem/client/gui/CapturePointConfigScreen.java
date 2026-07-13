package com.battlelinesystem.client.gui;

import com.battlelinesystem.world.MapConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 单个据点详情编辑 — 名称、前置据点、动态脚本列表
 */
public class CapturePointConfigScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;
    private static final int FIELD_H = 18;

    private final MapEditScreen parent;
    private final int cpIndex;
    private final MapConfig.CapturePoint cp;

    private EditBox nameInput;
    private EditBox displayNameInput;

    /** 进攻方：null/""=不限, "A"=A队, "B"=B队 */
    private String attackerTeam = null;

    /** 初始归属：null=中立, "A"=A队, "B"=B队 */
    private String initialOwner = null;

    /** 当前编辑副本中的前置据点名称集合 */
    private final Set<String> prerequisites = new LinkedHashSet<>();

    /** 脚本条目：{type(0=A占,1=B占,2=争夺,3=失占), command} */
    private final List<ScriptEntry> scriptEntries = new ArrayList<>();
    private final List<EditBox> entryInputs = new ArrayList<>();

    private int scrollOffset = 0;
    private int listTop, listBot;

    public CapturePointConfigScreen(MapEditScreen parent, int cpIndex) {
        super(Component.literal("据点配置"));
        this.parent = parent;
        this.cpIndex = cpIndex;
        this.cp = new MapConfig.CapturePoint();
        MapConfig.CapturePoint orig = parent.capturePoints.get(cpIndex);
        cp.name = orig.name;
        cp.displayName = orig.displayName;
        cp.attackerTeam = orig.attackerTeam;
        this.attackerTeam = orig.attackerTeam;
        cp.initialOwner = orig.initialOwner;
        this.initialOwner = orig.initialOwner;
        if (orig.zones != null) {
            for (int[][] z : orig.zones) cp.zones.add(new int[][]{
                {z[0][0], z[0][1], z[0][2]}, {z[1][0], z[1][1], z[1][2]}
            });
        }
        if (orig.destroyRegions != null) {
            for (int[][] z : orig.destroyRegions) cp.destroyRegions.add(new int[][]{
                {z[0][0], z[0][1], z[0][2]}, {z[1][0], z[1][1], z[1][2]}
            });
        }
        if (orig.prerequisites != null) {
            prerequisites.addAll(orig.prerequisites);
        }
        if (orig.scripts != null) {
            loadFrom(orig.scripts.onCaptureA, 0);
            loadFrom(orig.scripts.onCaptureB, 1);
            loadFrom(orig.scripts.onContest, 2);
            loadFrom(orig.scripts.onUncapture, 3);
        }
    }

    private void loadFrom(List<String> cmds, int type) {
        if (cmds != null) for (String c : cmds) scriptEntries.add(new ScriptEntry(type, c));
    }

    private static class ScriptEntry {
        int type; // 0=A占, 1=B占, 2=争夺, 3=失占
        String command;
        ScriptEntry(int type, String command) { this.type = type; this.command = command; }
    }

    private static final String[] TYPE_NAMES = {"A队占领", "B队占领", "争夺中", "失去占领"};
    private static final int[] TYPE_COLORS = {0xFF4488FF, 0xFFFF4444, 0xFFFF8800, 0xFF888888};

    @Override
    protected void init() {
        super.init();

        int y = 40;
        int bottomBarY = this.height - 30;

        // 据点名称
        nameInput = this.addRenderableWidget(
                new EditBox(this.font, this.width / 2 - 100, y, 200, FIELD_H, Component.literal("名称")));
        nameInput.setMaxLength(32);
        nameInput.setValue(cp.name);
        y += 24;

        // 显示名称（HUD 上显示的，为空则用名称）
        displayNameInput = this.addRenderableWidget(
                new EditBox(this.font, this.width / 2 - 100, y, 200, FIELD_H, Component.literal("显示名称（HUD抢占时显示）")));
        displayNameInput.setMaxLength(32);
        displayNameInput.setValue(cp.displayName != null ? cp.displayName : "");
        y += 24;

        // ===== 进攻方选择 =====
        String attackerLabel = attackerLabel();
        this.addRenderableWidget(Button.builder(
                        Component.literal("进攻方: " + attackerLabel),
                        btn -> cycleAttacker())
                .pos(12, y)
                .size(130, FIELD_H)
                .build());

        // ===== 初始归属 =====
        String ownerLabel = ownerLabel();
        this.addRenderableWidget(Button.builder(
                        Component.literal("初始归属: " + ownerLabel),
                        btn -> cycleOwner())
                .pos(148, y)
                .size(130, FIELD_H)
                .build());
        y += 24;

        // ===== 前置据点选择 =====
        List<MapConfig.CapturePoint> allCps = parent.capturePoints;
        List<MapConfig.CapturePoint> otherCps = new ArrayList<>();
        for (int i = 0; i < allCps.size(); i++) {
            if (i != cpIndex && allCps.get(i).name != null && !allCps.get(i).name.isEmpty()) {
                otherCps.add(allCps.get(i));
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("前置据点:"), btn -> {})
                .pos(12, y)
                .size(52, 16)
                .build());

        int px = 68;
        int maxX = this.width - 12;
        for (MapConfig.CapturePoint ocp : otherCps) {
            final String ocpName = ocp.name;
            boolean selected = prerequisites.contains(ocpName);

            int btnW = this.font.width(ocpName) + 18;
            if (px + btnW > maxX) { px = 68; y += 20; }
            if (y + 18 > bottomBarY - 80) break; // 留空间给脚本区域

            this.addRenderableWidget(Button.builder(
                    Component.literal((selected ? "\u2713 " : "") + ocpName), btn -> {
                        if (prerequisites.contains(ocpName)) {
                            prerequisites.remove(ocpName);
                        } else {
                            prerequisites.add(ocpName);
                        }
                        rebuild();
                    })
                    .pos(px, y)
                    .size(btnW, 16)
                    .build());

            px += btnW + 4;
        }
        y += 22;

        // ===== 脚本 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("+ 添加脚本"), btn -> {
                    scriptEntries.add(new ScriptEntry(0, ""));
                    rebuild();
                })
                .pos(12, y)
                .size(80, 18)
                .build());
        y += 22;

        listTop = y;
        listBot = bottomBarY - 2;

        entryInputs.clear();
        int ey = listTop - scrollOffset;
        for (int i = 0; i < scriptEntries.size(); i++) {
            ScriptEntry se = scriptEntries.get(i);
            if (ey + FIELD_H < listTop || ey > listBot) { ey += FIELD_H + 4; continue; }

            final int idx = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal("[" + TYPE_NAMES[se.type] + "]"), btn -> {
                        scriptEntries.get(idx).type = (scriptEntries.get(idx).type + 1) % 4;
                        rebuild();
                    })
                    .pos(12, ey)
                    .size(60, 16)
                    .build());

            int cmdW = this.width - 12 - 12 - 60 - 24 - 8;
            EditBox cmdBox = this.addRenderableWidget(
                    new EditBox(this.font, 12 + 60 + 4, ey, cmdW, FIELD_H, Component.literal("cmd")));
            cmdBox.setMaxLength(200);
            cmdBox.setValue(se.command);
            entryInputs.add(cmdBox);

            this.addRenderableWidget(Button.builder(
                    Component.literal("X"), btn -> {
                        scriptEntries.remove(idx);
                        rebuild();
                    })
                    .pos(12 + 60 + 4 + cmdW + 2, ey)
                    .size(20, 16)
                    .build());

            ey += FIELD_H + 4;
        }

        // 底部按钮
        this.addRenderableWidget(Button.builder(Component.literal("保存并返回"), btn -> {
                    cp.name = nameInput.getValue().trim();
                    if (cp.name.isEmpty()) cp.name = "未命名据点";
                    String dn = displayNameInput.getValue().trim();
                    cp.displayName = dn.isEmpty() ? null : dn;
                    cp.attackerTeam = attackerTeam;
                    cp.initialOwner = initialOwner;
                    saveScripts();
                    cp.prerequisites = new ArrayList<>(prerequisites);
                    parent.capturePoints.set(cpIndex, cp);
                    if (this.minecraft != null) this.minecraft.setScreen(parent);
                })
                .pos(this.width / 2 - 80, bottomBarY)
                .size(80, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("返回（不保存）"), btn -> {
                    if (this.minecraft != null) this.minecraft.setScreen(parent);
                })
                .pos(this.width / 2 + 10, bottomBarY)
                .size(80, 20)
                .build());
    }

    private void saveScripts() {
        for (int i = 0; i < scriptEntries.size() && i < entryInputs.size(); i++) {
            scriptEntries.get(i).command = entryInputs.get(i).getValue().trim();
        }
        cp.scripts.onCaptureA.clear();
        cp.scripts.onCaptureB.clear();
        cp.scripts.onContest.clear();
        cp.scripts.onUncapture.clear();
        for (ScriptEntry se : scriptEntries) {
            if (se.command.isEmpty()) continue;
            switch (se.type) {
                case 0 -> cp.scripts.onCaptureA.add(se.command);
                case 1 -> cp.scripts.onCaptureB.add(se.command);
                case 2 -> cp.scripts.onContest.add(se.command);
                case 3 -> cp.scripts.onUncapture.add(se.command);
            }
        }
    }

    private void rebuild() {
        for (int i = 0; i < scriptEntries.size() && i < entryInputs.size(); i++) {
            scriptEntries.get(i).command = entryInputs.get(i).getValue().trim();
        }
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my >= listTop && my <= listBot) {
            int listH = listBot - listTop;
            int contentH = scriptEntries.size() * (FIELD_H + 4);
            int maxScroll = Math.max(0, contentH - listH);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(delta * 20), maxScroll));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, "编辑据点: " + cp.name, this.width / 2, 15, TITLE_COLOR);

        // 前置据点高亮渲染
        List<MapConfig.CapturePoint> allCps = parent.capturePoints;
        List<MapConfig.CapturePoint> otherCps = new ArrayList<>();
        for (int i = 0; i < allCps.size(); i++) {
            if (i != cpIndex && allCps.get(i).name != null && !allCps.get(i).name.isEmpty()) {
                otherCps.add(allCps.get(i));
            }
        }

        int py = 64;
        int preqY = py;
        int px = 68;
        int maxX = this.width - 12;
        int preqEndY = py + 20;
        for (MapConfig.CapturePoint ocp : otherCps) {
            boolean selected = prerequisites.contains(ocp.name);
            int btnW = this.font.width(ocp.name) + 18;
            if (px + btnW > maxX) { px = 68; preqY += 20; preqEndY += 20; }
            if (preqY > listTop) break;
            if (selected) {
                g.fill(px, preqY, px + btnW, preqY + 16, 0x6633AA33);
            }
            px += btnW + 4;
        }
        preqEndY = preqY + 18;

        g.drawString(this.font, "点击切换前置据点（选中=绿色），清空前置才能直接占领",
                12, preqEndY + 2, 0x666666);

        // 脚本列表
        g.enableScissor(0, listTop, this.width, listBot - listTop);
        int ey = listTop - scrollOffset;
        for (int i = 0; i < scriptEntries.size(); i++) {
            ScriptEntry se = scriptEntries.get(i);
            if (ey + FIELD_H < listTop || ey > listBot) { ey += FIELD_H + 4; continue; }
            g.drawString(this.font, "[" + TYPE_NAMES[se.type] + "]",
                    12 + 3, ey + 2, TYPE_COLORS[se.type]);
            ey += FIELD_H + 4;
        }
        g.disableScissor();

        int listH = listBot - listTop;
        int contentH = scriptEntries.size() * (FIELD_H + 4);
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barY = listTop + scrollOffset * listH / contentH;
            g.fill(this.width - 4, barY, this.width - 2, barY + barH, 0x66AAAAAA);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private String attackerLabel() {
        if (attackerTeam == null || attackerTeam.isEmpty()) return "无(不限)";
        return attackerTeam.equals("A") ? "A队" : "B队";
    }

    private String ownerLabel() {
        if (initialOwner == null || initialOwner.isEmpty()) return "中立";
        return initialOwner.equals("A") ? "A队" : "B队";
    }

    private void cycleOwner() {
        if (initialOwner == null || initialOwner.isEmpty()) {
            initialOwner = "A";
        } else if ("A".equals(initialOwner)) {
            initialOwner = "B";
        } else {
            initialOwner = null;
        }
        rebuild();
    }

    private void cycleAttacker() {
        if (attackerTeam == null || attackerTeam.isEmpty()) {
            attackerTeam = "A";
        } else if ("A".equals(attackerTeam)) {
            attackerTeam = "B";
        } else {
            attackerTeam = null;
        }
        rebuild();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

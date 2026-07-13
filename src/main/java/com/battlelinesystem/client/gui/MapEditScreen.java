package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.FactionConfig;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketFactionRequest;
import com.battlelinesystem.network.packet.PacketMapConfigSave;
import com.battlelinesystem.network.packet.PacketMapListResponse;
import com.battlelinesystem.world.MapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 地图编辑界面 — 第1页：基本信息+阵营池，第2页：据点列表，第3页：全局脚本
 */
public class MapEditScreen extends Screen {

    private static final int TITLE_COLOR = 0xFFAA00;
    private static final int LABEL_X = 12;
    private static final int FIELD_W = 150;
    private static final int FIELD_H = 18;
    private static final int POOL_ROW_H = 22;

    public final PacketMapListResponse.MapEntry mapEntry;
    public final int[] modeCounts;
    public final int countdownSeconds;

    private EditBox nameInput;
    private EditBox descInput;
    private EditBox minPlayersInput;
    private EditBox maxPlayersInput;
    private EditBox initialScoreInput;

    private List<FactionConfig> allFactions = new ArrayList<>();
    private boolean factionsRequested = false;

    public final Set<String> poolA = new LinkedHashSet<>();
    public final Set<String> poolB = new LinkedHashSet<>();

    private int scrollOffset = 0;
    private int poolListTop;
    private int poolListBot;
    private int rightPanelX;

    /** 可编辑的据点列表 */
    public final List<MapConfig.CapturePoint> capturePoints;

    // ---- 翻页 ----
    private int currentPage = 0; // 0=基本信息, 1=据点, 2=全局脚本, 3=音乐
    /** 据点列表滚动 */
    private int cpScrollOffset = 0;
    /** 全局脚本滚动 */
    private int gsScrollOffset = 0;
    /** 全局脚本列表 */
    public final List<MapConfig.GlobalScript> globalScripts = new ArrayList<>();
    /** 音乐滚动 */
    private int musicScrollOffset = 0;

    private String startMusic;
    private String victoryMusic;
    private String defeatMusic;
    private String nearEndMusic;
    private int nearEndThreshold;
    private int timeLimitMinutes;
    private String timeUpRule;

    public MapEditScreen(PacketMapListResponse.MapEntry mapEntry, int[] modeCounts, int countdownSeconds) {
        super(Component.literal("编辑地图"));
        this.mapEntry = mapEntry;
        this.modeCounts = modeCounts;
        this.countdownSeconds = countdownSeconds;
        this.poolA.addAll(mapEntry.factionPoolA);
        this.poolB.addAll(mapEntry.factionPoolB);
        this.capturePoints = new ArrayList<>(mapEntry.capturePoints != null ? mapEntry.capturePoints : Collections.emptyList());
        // deep copy globalScripts
        if (mapEntry.globalScripts != null) {
            for (MapConfig.GlobalScript orig : mapEntry.globalScripts) {
                MapConfig.GlobalScript gs = new MapConfig.GlobalScript();
                gs.trigger = orig.trigger;
                gs.value = orig.value;
                gs.team = orig.team;
                gs.commands = new ArrayList<>(orig.commands);
                globalScripts.add(gs);
            }
        }
        this.startMusic = mapEntry.startMusic != null ? mapEntry.startMusic : "";
        this.victoryMusic = mapEntry.victoryMusic != null ? mapEntry.victoryMusic : "";
        this.defeatMusic = mapEntry.defeatMusic != null ? mapEntry.defeatMusic : "";
        this.nearEndMusic = mapEntry.nearEndMusic != null ? mapEntry.nearEndMusic : "";
        this.nearEndThreshold = mapEntry.nearEndThreshold;
        this.timeLimitMinutes = mapEntry.timeLimitMinutes;
        this.timeUpRule = mapEntry.timeUpRule != null ? mapEntry.timeUpRule : "";
    }

    @Override
    protected void init() {
        super.init();

        if (!factionsRequested) {
            AllPackets.getChannel().sendToServer(new PacketFactionRequest());
            factionsRequested = true;
        }

        int bottomBarY = this.height - 32;

        // ===== 左上角翻页按钮 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("< 1"), btn -> { currentPage = 0; rebuild(); })
                .pos(12, 12)
                .size(26, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("2"), btn -> { currentPage = 1; rebuild(); })
                .pos(40, 12)
                .size(20, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("3"), btn -> { currentPage = 2; rebuild(); })
                .pos(62, 12)
                .size(20, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("4 >"), btn -> { currentPage = 3; rebuild(); })
                .pos(84, 12)
                .size(28, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("5"), btn -> { currentPage = 4; rebuild(); })
                .pos(114, 12)
                .size(20, 16)
                .build());

        String pageLabel = switch (currentPage) {
            case 0 -> "基本信息 + 阵营池";
            case 1 -> "据点列表 (" + capturePoints.size() + ")";
            case 2 -> "全局脚本";
            case 3 -> "背景音乐";
            case 4 -> "时限设置";
            default -> "";
        };
        this.addRenderableWidget(Button.builder(
                Component.literal(pageLabel), btn -> {})
                .pos(95, 12)
                .size(130, 16)
                .build());

        switch (currentPage) {
            case 0 -> initPage1(bottomBarY);
            case 1 -> initPage2(bottomBarY);
            case 2 -> initPage3(bottomBarY);
            case 3 -> initPage4(bottomBarY);
            case 4 -> initPage5(bottomBarY);
        }

        // ===== 底部：保存 / 返回 =====
        this.addRenderableWidget(Button.builder(Component.literal("保存"), btn -> save())
                .pos(this.width / 2 - 80, bottomBarY)
                .size(60, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MapSettingsScreen(
                                this.modeCounts, this.countdownSeconds));
                    }
                })
                .pos(this.width / 2 - 15, bottomBarY)
                .size(60, 20)
                .build());
    }

    // ===== 第1页：基本信息 + 阵营池 =====
    private void initPage1(int bottomBarY) {
        int leftFieldX = LABEL_X + 60;
        int leftY = 48;
        int gap = 22;

        // 地图ID / 模式
        this.addRenderableWidget(Button.builder(
                Component.literal("ID: " + mapEntry.id), btn -> {})
                .pos(LABEL_X, leftY - 2)
                .size(90, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("模式: " + mapEntry.mode), btn -> {})
                .pos(LABEL_X + 100, leftY - 2)
                .size(90, 18)
                .build());

        leftY += gap + 6;

        this.nameInput = this.addRenderableWidget(
                new EditBox(this.font, leftFieldX, leftY, FIELD_W - 30, FIELD_H, Component.literal("名称")));
        nameInput.setMaxLength(32);
        nameInput.setValue(mapEntry.name);

        leftY += gap;
        this.descInput = this.addRenderableWidget(
                new EditBox(this.font, leftFieldX, leftY, FIELD_W - 30, FIELD_H, Component.literal("介绍")));
        descInput.setMaxLength(100);
        descInput.setValue(mapEntry.description != null ? mapEntry.description : "");

        leftY += gap;
        this.minPlayersInput = this.addRenderableWidget(
                new EditBox(this.font, leftFieldX, leftY, 40, FIELD_H, Component.literal("最少")));
        minPlayersInput.setMaxLength(3);
        minPlayersInput.setValue(String.valueOf(mapEntry.minPlayers));
        minPlayersInput.setFilter(s -> s.isEmpty() || s.matches("\\d{0,3}"));

        this.maxPlayersInput = this.addRenderableWidget(
                new EditBox(this.font, leftFieldX + 55, leftY, 40, FIELD_H, Component.literal("最多")));
        maxPlayersInput.setMaxLength(3);
        maxPlayersInput.setValue(String.valueOf(mapEntry.maxPlayers));
        maxPlayersInput.setFilter(s -> s.isEmpty() || s.matches("\\d{0,3}"));

        leftY += gap;
        this.initialScoreInput = this.addRenderableWidget(
                new EditBox(this.font, leftFieldX, leftY, 40, FIELD_H, Component.literal("分数")));
        initialScoreInput.setMaxLength(5);
        initialScoreInput.setValue(String.valueOf(mapEntry.initialScore));
        initialScoreInput.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));

        // ===== 阵营池 — 右侧面板 =====
        rightPanelX = this.width / 2 + 10;
        poolListTop = 48;
        poolListBot = bottomBarY - 10;

        int py = poolListTop;
        for (FactionConfig fc : allFactions) {
            final String fid = fc.id;
            int rowY = py - scrollOffset;
            boolean inA = poolA.contains(fid);
            boolean inB = poolB.contains(fid);

            Button btnA = this.addRenderableWidget(Button.builder(
                    Component.literal(inA ? "\u2713" : "A"),
                    btn -> toggleFaction(fid, true))
                    .pos(rightPanelX + 14, rowY - 1)
                    .size(inA ? 18 : 14, 16)
                    .build());
            if (inB && !inA) btnA.active = false;

            Button btnB = this.addRenderableWidget(Button.builder(
                    Component.literal(inB ? "\u2713" : "B"),
                    btn -> toggleFaction(fid, false))
                    .pos(rightPanelX + 32, rowY - 1)
                    .size(inB ? 18 : 14, 16)
                    .build());
            if (inA && !inB) btnB.active = false;

            this.addRenderableWidget(Button.builder(
                    Component.literal(fc.name), btn -> {})
                    .pos(rightPanelX + 50, rowY - 1)
                    .size(60, 16)
                    .build());

            py += POOL_ROW_H;
        }
    }

    // ===== 第2页：据点列表 =====
    private void initPage2(int bottomBarY) {
        int y = 48;

        this.addRenderableWidget(Button.builder(
                Component.literal("+ 添加据点"), btn -> {
                    MapConfig.CapturePoint newCp = new MapConfig.CapturePoint();
                    newCp.name = "新据点" + (capturePoints.size() + 1);
                    capturePoints.add(newCp);
                    rebuild();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CapturePointConfigScreen(this, capturePoints.size() - 1));
                    }
                })
                .pos(12, y)
                .size(80, 18)
                .build());
        y += 24;
        int listTop = y;
        int listBot = bottomBarY;

        int cpY = listTop - cpScrollOffset;
        for (int i = 0; i < capturePoints.size(); i++) {
            MapConfig.CapturePoint cp = capturePoints.get(i);
            if (cpY + 18 < listTop || cpY > listBot) { cpY += 22; continue; }

            String zoneInfo = cp.zones.isEmpty() ? "0区域" : cp.zones.size() + "区域";
            String delInfo = (cp.destroyRegions != null && !cp.destroyRegions.isEmpty())
                    ? " 删" + cp.destroyRegions.size() : "";
            String scriptInfo = (!cp.scripts.onCaptureA.isEmpty() || !cp.scripts.onCaptureB.isEmpty()
                    || !cp.scripts.onContest.isEmpty() || !cp.scripts.onUncapture.isEmpty())
                    ? " 脚" : "";
            int btnW = this.width - 50;

            final int editIdx = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal(cp.name + " [" + zoneInfo + delInfo + scriptInfo + "]"),
                    btn -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new CapturePointConfigScreen(this, editIdx));
                        }
                    })
                    .pos(12, cpY)
                    .size(btnW, 18)
                    .build());

            final int delIdx = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal("X"), btn -> {
                        capturePoints.remove(delIdx);
                        cpScrollOffset = Math.max(0, cpScrollOffset);
                        rebuild();
                    })
                    .pos(14 + btnW, cpY)
                    .size(20, 18)
                    .build());

            cpY += 22;
        }
    }

    // ===== 第3页：全局脚本 =====
    private void initPage3(int bottomBarY) {
        int y = 48;
        int listBot = bottomBarY;

        this.addRenderableWidget(Button.builder(
                Component.literal("+ 添加全局脚本"), btn -> {
                    MapConfig.GlobalScript gs = new MapConfig.GlobalScript();
                    gs.trigger = "score_ge";
                    gs.value = 100;
                    gs.team = "A";
                    gs.commands = new ArrayList<>();
                    globalScripts.add(gs);
                    rebuild();
                })
                .pos(12, y)
                .size(100, 18)
                .build());
        y += 24;
        int listTop = y;

        int gy = listTop - gsScrollOffset;
        for (int i = 0; i < globalScripts.size(); i++) {
            MapConfig.GlobalScript gs = globalScripts.get(i);
            if (gy + FIELD_H * 2 + 8 < listTop || gy > listBot) { gy += FIELD_H * 2 + 14; continue; }

            final int idx = i;

            this.addRenderableWidget(Button.builder(
                    Component.literal(triggerLabel(gs.trigger)), btn -> {
                        cycleTrigger(globalScripts.get(idx));
                        rebuild();
                    })
                    .pos(12, gy)
                    .size(80, 16)
                    .build());

            int cmdX = 96;
            final EditBox valRef;
            if (hasValueEditor(gs.trigger)) {
                EditBox valBox = this.addRenderableWidget(
                        new EditBox(this.font, 96, gy, 50, FIELD_H, Component.literal("值")));
                valBox.setMaxLength(6);
                valBox.setValue(String.valueOf(gs.value));
                valBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,6}"));
                valRef = valBox;
                cmdX = 150;
                if (hasTeamSelector(gs.trigger)) {
                    this.addRenderableWidget(Button.builder(
                            Component.literal(gs.team), btn -> {
                                globalScripts.get(idx).team = "A".equals(globalScripts.get(idx).team) ? "B" : "A";
                                rebuild();
                            })
                            .pos(150, gy)
                            .size(20, 16)
                            .build());
                    cmdX = 174;
                }
            } else {
                valRef = null;
            }

            int cmdW = this.width - cmdX - 20;
            EditBox cmdBox = this.addRenderableWidget(
                    new EditBox(this.font, cmdX, gy, cmdW, FIELD_H * 2, Component.literal("cmd")));
            cmdBox.setMaxLength(500);
            cmdBox.setValue(String.join("\n", gs.commands));
            final EditBox cmdRef = cmdBox;

            this.addRenderableWidget(Button.builder(
                    Component.literal("X"), btn -> {
                        saveGsInputs(valRef, cmdRef, idx);
                        globalScripts.remove(idx);
                        gsScrollOffset = Math.max(0, gsScrollOffset);
                        rebuild();
                    })
                    .pos(this.width - 18, gy)
                    .size(16, 16)
                    .build());

            gy += FIELD_H * 2 + 14;
        }
    }

    // ===== 第4页：背景音乐 =====
    private void initPage4(int bottomBarY) {
        int y = 48;
        int gap = 24;
        int fieldX = LABEL_X + 70;

        this.addRenderableWidget(
                new EditBox(this.font, fieldX, y, this.width - fieldX - 20, FIELD_H, Component.literal("开局音乐")));
        var startBox = (EditBox) this.children().get(this.children().size() - 1);
        startBox.setMaxLength(100);
        startBox.setValue(startMusic);
        startBox.setResponder(v -> startMusic = v);
        y += gap;

        this.addRenderableWidget(
                new EditBox(this.font, fieldX, y, this.width - fieldX - 20, FIELD_H, Component.literal("胜利音乐")));
        var victoryBox = (EditBox) this.children().get(this.children().size() - 1);
        victoryBox.setMaxLength(100);
        victoryBox.setValue(victoryMusic);
        victoryBox.setResponder(v -> victoryMusic = v);
        y += gap;

        this.addRenderableWidget(
                new EditBox(this.font, fieldX, y, this.width - fieldX - 20, FIELD_H, Component.literal("失败音乐")));
        var defeatBox = (EditBox) this.children().get(this.children().size() - 1);
        defeatBox.setMaxLength(100);
        defeatBox.setValue(defeatMusic);
        defeatBox.setResponder(v -> defeatMusic = v);
        y += gap;

        this.addRenderableWidget(
                new EditBox(this.font, fieldX, y, this.width - fieldX - 20, FIELD_H, Component.literal("濒临结束音乐")));
        var nearEndBox = (EditBox) this.children().get(this.children().size() - 1);
        nearEndBox.setMaxLength(100);
        nearEndBox.setValue(nearEndMusic);
        nearEndBox.setResponder(v -> nearEndMusic = v);
        y += gap + 6;

        var thresholdBox = new EditBox(this.font, fieldX, y, 50, FIELD_H, Component.literal("濒临阈值"));
        thresholdBox.setMaxLength(5);
        thresholdBox.setValue(String.valueOf(nearEndThreshold));
        thresholdBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));
        thresholdBox.setResponder(v -> {
            try { nearEndThreshold = v.isEmpty() ? 0 : Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(thresholdBox);
    }

    // ===== 第5页：游戏时限 =====
    private void initPage5(int bottomBarY) {
        int y = 48;
        int gap = 24;
        int fieldX = LABEL_X + 70;

        // 时限分钟输入
        var limitBox = new EditBox(this.font, fieldX, y, 50, FIELD_H, Component.literal("时限(分钟)"));
        limitBox.setMaxLength(5);
        limitBox.setValue(String.valueOf(timeLimitMinutes));
        limitBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));
        limitBox.setResponder(v -> {
            try { timeLimitMinutes = v.isEmpty() ? 0 : Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(limitBox);
        y += gap + 6;

        // 时间到后的胜利规则按钮
        String[] ruleLabels = {"不限时", "比分高者胜", "A队获胜", "B队获胜"};
        String[] ruleValues = {"", "score", "A", "B"};
        int[] ruleColors = {0xCC666666, 0xCCFFAA00, 0xCC4488FF, 0xCCFF4444};

        for (int i = 0; i < ruleValues.length; i++) {
            boolean selected = ruleValues[i].equals(timeUpRule);
            int btnColor = selected ? ruleColors[i] | 0x44000000 : 0xFF444444;
            final String val = ruleValues[i];
            this.addRenderableWidget(Button.builder(
                    Component.literal((selected ? "> " : "") + ruleLabels[i]),
                    btn -> {
                        timeUpRule = val;
                        rebuild();
                    })
                    .pos(fieldX, y)
                    .size(90, 18)
                    .build());
            y += gap;
        }
    }

    private void saveGsInputs(EditBox valBox, EditBox cmdBox, int idx) {
        if (idx < 0 || idx >= globalScripts.size()) return;
        if (valBox != null) {
            try {
                globalScripts.get(idx).value = Integer.parseInt(valBox.getValue().trim());
            } catch (NumberFormatException ignored) {}
        }
        globalScripts.get(idx).commands.clear();
        for (String line : cmdBox.getValue().split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) globalScripts.get(idx).commands.add(t);
        }
    }

    private void cycleTrigger(MapConfig.GlobalScript gs) {
        switch (gs.trigger) {
            case "game_start" -> gs.trigger = "game_end";
            case "game_end" -> gs.trigger = "first_deploy";
            case "first_deploy" -> gs.trigger = "score_ge";
            case "score_ge" -> gs.trigger = "score_le";
            case "score_le" -> gs.trigger = "time_ge";
            case "time_ge" -> gs.trigger = "game_start";
            default -> gs.trigger = "game_start";
        }
    }

    /** 遍历子控件，把全局脚本的 EditBox 内容同步回 globalScripts */
    private void syncAllGsInputs() {
        int listTop = 72;
        int rowH = FIELD_H * 2 + 14;
        for (int i = 0; i < globalScripts.size(); i++) {
            int gy = listTop - gsScrollOffset + i * rowH;
            globalScripts.get(i).commands.clear();
            // 找到该行对应的命令 EditBox（高度 FIELD_H*2，x>=96）
            for (var child : children()) {
                if (!(child instanceof EditBox box)) continue;
                if (box.getHeight() != FIELD_H * 2) continue;
                if (box.getX() < 90) continue;
                if (Math.abs(box.getY() - gy) <= 4) {
                    for (String line : box.getValue().split("\n")) {
                        String t = line.trim();
                        if (!t.isEmpty()) globalScripts.get(i).commands.add(t);
                    }
                    break;
                }
            }
            // 找到值 EditBox（高度 FIELD_H，x~=96 且不是命令框）
            for (var child : children()) {
                if (!(child instanceof EditBox box)) continue;
                if (box.getHeight() != FIELD_H) continue;
                if (Math.abs(box.getX() - 96) > 4) continue;
                if (Math.abs(box.getY() - gy) <= 4) {
                    try {
                        globalScripts.get(i).value = Integer.parseInt(box.getValue().trim());
                    } catch (NumberFormatException ignored) {}
                    break;
                }
            }
        }
    }

    private static boolean isScoreTrigger(String trigger) {
        return "score_ge".equals(trigger) || "score_le".equals(trigger);
    }

    private static boolean hasTeamSelector(String trigger) {
        return isScoreTrigger(trigger);
    }

    private static boolean hasValueEditor(String trigger) {
        return !"game_start".equals(trigger) && !"game_end".equals(trigger) && !"first_deploy".equals(trigger);
    }

    private static String triggerLabel(String trigger) {
        return switch (trigger) {
            case "game_start" -> "开局";
            case "game_end" -> "结束";
            case "first_deploy" -> "首次部署";
            case "score_ge" -> "分数>=";
            case "score_le" -> "分数<=";
            case "time_ge" -> "时间>=";
            default -> trigger;
        };
    }

    private void toggleFaction(String factionId, boolean isPoolA) {
        if (isPoolA) {
            if (poolA.contains(factionId)) { poolA.remove(factionId); }
            else { poolA.add(factionId); poolB.remove(factionId); }
        } else {
            if (poolB.contains(factionId)) { poolB.remove(factionId); }
            else { poolB.add(factionId); poolA.remove(factionId); }
        }
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentPage == 0) {
            // 只在右侧阵营池区域响应滚动
            int listH = poolListBot - poolListTop;
            if (my < poolListTop || my > poolListBot || mx < rightPanelX || mx > this.width) return super.mouseScrolled(mx, my, delta);
            int contentH = allFactions.size() * POOL_ROW_H;
            int maxScroll = Math.max(0, contentH - listH);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(delta * 20), maxScroll));
        } else if (currentPage == 1 || currentPage == 2) {
            int listTop = 72, listBot = this.height - 32;
            if (my < listTop || my > listBot) return super.mouseScrolled(mx, my, delta);

            int contentH, maxScroll;
            if (currentPage == 1) {
                contentH = capturePoints.size() * 22;
                maxScroll = Math.max(0, contentH - (listBot - listTop));
                cpScrollOffset = Math.max(0, Math.min(cpScrollOffset - (int)(delta * 20), maxScroll));
            } else {
                contentH = globalScripts.size() * (FIELD_H * 2 + 14);
                maxScroll = Math.max(0, contentH - (listBot - listTop));
                gsScrollOffset = Math.max(0, Math.min(gsScrollOffset - (int)(delta * 20), maxScroll));
            }
        }
        rebuild();
        return true;
    }

    public void save() {
        // 先把全局脚本输入框内容同步回 globalScripts
        syncAllGsInputs();

        String name = nameInput.getValue().trim();
        String desc = descInput.getValue().trim();
        int minP = parseIntSafe(minPlayersInput != null ? minPlayersInput.getValue() : "2", 2);
        int maxP = parseIntSafe(maxPlayersInput != null ? maxPlayersInput.getValue() : "32", 32);
        int initScore = parseIntSafe(initialScoreInput != null ? initialScoreInput.getValue() : "200", 200);
        if (minP < 1) minP = 1;
        if (maxP < minP) maxP = minP;
        if (initScore < 1) initScore = 1;

        AllPackets.getChannel().sendToServer(new PacketMapConfigSave(
                mapEntry.id, name, desc, minP, maxP, initScore,
                new ArrayList<>(poolA), new ArrayList<>(poolB),
                capturePoints, new ArrayList<>(globalScripts),
                startMusic != null ? startMusic : "",
                victoryMusic != null ? victoryMusic : "",
                defeatMusic != null ? defeatMusic : "",
                nearEndMusic != null ? nearEndMusic : "",
                nearEndThreshold,
                timeLimitMinutes,
                timeUpRule != null ? timeUpRule : ""));

        if (this.minecraft != null) {
            this.minecraft.setScreen(new MapSettingsScreen(this.modeCounts, this.countdownSeconds));
        }
    }

    public void rebuild() {
        this.clearWidgets();
        this.init();
    }

    public void updateFactions(List<FactionConfig> list) {
        this.allFactions = list;
        this.scrollOffset = 0;
        rebuild();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        String subtitle = switch (currentPage) {
            case 0 -> "";
            case 1 -> " [据点]";
            case 2 -> " [全局脚本]";
            case 3 -> " [背景音乐]";
            case 4 -> " [时限]";
            default -> "";
        };
        g.drawCenteredString(this.font, "编辑地图: " + mapEntry.name + subtitle,
                this.width / 2, 28, TITLE_COLOR);

        if (currentPage == 0) renderPage1(g);
        else if (currentPage == 1) renderPage2(g);
        else if (currentPage == 2) renderPage3(g);
        else if (currentPage == 3) renderPage4(g);
        else if (currentPage == 4) renderPage5(g);

        // 当前页高亮
        int[] hxTable = {12, 40, 62, 84, 114};
        int[] hwTable = {26, 20, 20, 28, 20};
        int hp = Math.min(currentPage, 4);
        int hx = hxTable[hp];
        g.fill(hx, 26, hx + hwTable[hp], 28, 0x44FFAA00);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPage1(GuiGraphics g) {
        int labelX = LABEL_X;
        int y = 68;
        int gap = 22;

        g.drawString(this.font, "名称:", labelX, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "介绍:", labelX, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "人数:", labelX, y, 0xAAAAAA);
        y += gap;
        g.drawString(this.font, "分数:", labelX, y, 0xAAAAAA);

        // ===== 右侧阵营池 =====
        int rX = rightPanelX;
        int rW = this.width - rX - 6;
        g.drawString(this.font, "阵营池  A/B  名称", rX, poolListTop - 12, 0xFFAA00);

        g.enableScissor(rX, poolListTop, rW, poolListBot - poolListTop);

        int ry = poolListTop;
        for (FactionConfig fc : allFactions) {
            int rowY = ry - scrollOffset;
            if (rowY + POOL_ROW_H < poolListTop || rowY > poolListBot) { ry += POOL_ROW_H; continue; }

            boolean inA = poolA.contains(fc.id);
            boolean inB = poolB.contains(fc.id);
            int c = parseColor(fc.displayColor);

            g.fill(rX, rowY + 2, rX + 8, rowY + 12, 0xFF000000 | c);

            int aBg = inA ? (0xFF000000 | c) : (inB ? 0xFF222222 : 0xFF444444);
            String aText = inA ? "\u2713" : "A";
            int aTc = inA ? 0xFFFFFF : (inB ? 0x444444 : 0xAAAAAA);
            g.fill(rX + 14, rowY + 2, rX + (inA ? 30 : 26), rowY + 12, aBg);
            g.drawString(this.font, aText, rX + (inA ? 20 : 18), rowY + 4, aTc);

            int bX = rX + (inA ? 34 : 30);
            int bBg = inB ? (0xFF000000 | c) : (inA ? 0xFF222222 : 0xFF444444);
            String bText = inB ? "\u2713" : "B";
            int bTc = inB ? 0xFFFFFF : (inA ? 0x444444 : 0xAAAAAA);
            g.fill(bX, rowY + 2, bX + (inB ? 18 : 14), rowY + 12, bBg);
            g.drawString(this.font, bText, bX + (inB ? 5 : 3), rowY + 4, bTc);

            g.drawString(this.font, fc.name, rX + 52, rowY + 4, 0xCCCCCC);
            ry += POOL_ROW_H;
        }
        g.disableScissor();

        int lContentH = allFactions.size() * POOL_ROW_H;
        int lListH = poolListBot - poolListTop;
        if (lContentH > lListH) {
            int barH = Math.max(16, lListH * lListH / lContentH);
            int barY = poolListTop + scrollOffset * lListH / lContentH;
            g.fill(this.width - 4, barY, this.width - 2, barY + barH, 0x66AAAAAA);
        }
    }

    private void renderPage2(GuiGraphics g) {
        int listTop = 72, listBot = this.height - 32;

        g.enableScissor(0, listTop, this.width, listBot - listTop);
        int cpY = listTop - cpScrollOffset;
        for (MapConfig.CapturePoint cp : capturePoints) {
            if (cpY + 18 < listTop || cpY > listBot) { cpY += 22; continue; }

            String zoneInfo = cp.zones.isEmpty() ? "0区域" : cp.zones.size() + "区域";
            String delInfo = (cp.destroyRegions != null && !cp.destroyRegions.isEmpty()) ? " 删" + cp.destroyRegions.size() : "";
            String scriptInfo = (!cp.scripts.onCaptureA.isEmpty() || !cp.scripts.onCaptureB.isEmpty()
                    || !cp.scripts.onContest.isEmpty() || !cp.scripts.onUncapture.isEmpty()) ? " 脚" : "";
            g.drawString(this.font, cp.name + " [" + zoneInfo + delInfo + scriptInfo + "]", 12, cpY + 2, 0xCCCCCC);
            cpY += 22;
        }
        g.disableScissor();

        int contentH = capturePoints.size() * 22;
        int listH = listBot - listTop;
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barY = listTop + cpScrollOffset * listH / contentH;
            g.fill(this.width - 4, barY, this.width - 2, barY + barH, 0x66AAAAAA);
        }
    }

    private void renderPage3(GuiGraphics g) {
        int listTop = 72, listBot = this.height - 32;

        g.enableScissor(0, listTop, this.width, listBot - listTop);
        int gy = listTop - gsScrollOffset;
        for (MapConfig.GlobalScript gs : globalScripts) {
            if (gy + FIELD_H * 2 + 8 < listTop || gy > listBot) { gy += FIELD_H * 2 + 14; continue; }

            String typeLabel = triggerLabel(gs.trigger);
            String extra;
            if ("game_start".equals(gs.trigger) || "game_end".equals(gs.trigger) || "first_deploy".equals(gs.trigger)) {
                extra = "";
            } else if (isScoreTrigger(gs.trigger)) {
                extra = " " + gs.value + " 队伍:" + gs.team;
            } else {
                extra = " " + gs.value + " 秒";
            }
            g.drawString(this.font, typeLabel + extra, 12, gy, 0xFFAA00);

            String cmdPreview = String.join("; ", gs.commands);
            if (cmdPreview.length() > 80) cmdPreview = cmdPreview.substring(0, 77) + "...";
            g.drawString(this.font, cmdPreview.isEmpty() ? "(空)" : cmdPreview, 12, gy + 14, 0xAAAAAA);

            gy += FIELD_H * 2 + 14;
        }
        g.disableScissor();

        int contentH = globalScripts.size() * (FIELD_H * 2 + 14);
        int listH = listBot - listTop;
        if (contentH > listH) {
            int barH = Math.max(16, listH * listH / contentH);
            int barY = listTop + gsScrollOffset * listH / contentH;
            g.fill(this.width - 4, barY, this.width - 2, barY + barH, 0x66AAAAAA);
        }

        g.drawString(this.font, "每行一条命令，支持@a/@p/@r选择器", 12, listBot + 4, 0x666666);
    }

    private void renderPage4(GuiGraphics g) {
        int y = 68;
        int gap = 24;
        int labelX = LABEL_X;

        g.drawString(this.font, "开局音乐:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, notEmpty(startMusic), labelX + 70, y, 0xFFFFAA);
        y += gap;
        g.drawString(this.font, "胜利音乐:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, notEmpty(victoryMusic), labelX + 70, y, 0x55FF55);
        y += gap;
        g.drawString(this.font, "失败音乐:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, notEmpty(defeatMusic), labelX + 70, y, 0xFF5555);
        y += gap;
        g.drawString(this.font, "濒临结束音乐:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, notEmpty(nearEndMusic), labelX + 70, y, 0xFFAA00);
        y += gap + 6;
        g.drawString(this.font, "濒临阈值:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, nearEndThreshold > 0 ? "分数 < " + nearEndThreshold + " 时播放" : "禁用", labelX + 70, y, 0xCCCCCC);

        y += gap + 8;
        g.drawString(this.font, "§7格式: namespace:path", labelX, y, 0x666666);
        y += 12;
        g.drawString(this.font, "§7例: minecraft:music.creative", labelX, y, 0x666666);
        y += 12;
        g.drawString(this.font, "§7留空 = 不播放", labelX, y, 0x666666);
    }

    private void renderPage5(GuiGraphics g) {
        int y = 68;
        int gap = 24;
        int labelX = LABEL_X;

        g.drawString(this.font, "游戏时限:", labelX, y, 0xAAAAAA);
        g.drawString(this.font, timeLimitMinutes > 0 ? timeLimitMinutes + " 分钟" : "不限时",
                labelX + 70, y, timeLimitMinutes > 0 ? 0xFFFFAA : 0x666666);
        y += gap + 6;

        g.drawString(this.font, "时间到后:", labelX, y, 0xAAAAAA);
        String ruleText = switch (timeUpRule) {
            case "score" -> "比分高者获胜";
            case "A" -> "A队获胜";
            case "B" -> "B队获胜";
            default -> "未设置(不限时)";
        };
        int ruleColor = switch (timeUpRule) {
            case "score" -> 0xFFFFAA00;
            case "A" -> 0xFF4488FF;
            case "B" -> 0xFFFF4444;
            default -> 0xFF666666;
        };
        g.drawString(this.font, ruleText, labelX + 70, y, ruleColor);
        y += gap;
        g.drawString(this.font, "§7设0分钟+不限时 = 关闭时限", labelX, y, 0x666666);
    }

    private static String notEmpty(String s) {
        return s != null && !s.isEmpty() ? s : "§7(未设置)";
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) { return 0xFFFFFF; }
    }
}

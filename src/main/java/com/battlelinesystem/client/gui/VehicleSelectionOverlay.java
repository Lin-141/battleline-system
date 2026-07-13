package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.VehicleConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/**
 * 载具选择面板（从 WaitHudOverlay 提取）
 * 全部静态字段——客户端单例。
 */
public class VehicleSelectionOverlay {

    private static VehicleConfig[] configs = null;
    private static int[] countsData = null;
    private static boolean[] aliveData = null;
    private static long[] cdTimestamps = null;
    private static int[] cdTotals = null;
    private static int selectedIdx = -1;
    private static final int[] rects = new int[32 * 4];

    public static int getSelectedIdx() { return selectedIdx; }

    public static void setConfigs(VehicleConfig[] vehicles, int[] counts,
                                   boolean[] alive, int[] cooldowns) {
        if (vehicles != null) {
            configs = vehicles.clone();
            countsData = (counts != null) ? counts.clone() : new int[configs.length];
            aliveData = (alive != null) ? alive.clone() : new boolean[configs.length];
            cdTimestamps = new long[configs.length];
            cdTotals = new int[configs.length];
            long now = System.currentTimeMillis();
            for (int i = 0; i < configs.length; i++) {
                cdTotals[i] = (cooldowns != null && i < cooldowns.length) ? cooldowns[i] : 0;
                cdTimestamps[i] = now;
            }
        } else {
            configs = null;
            countsData = null;
            aliveData = null;
            cdTimestamps = null;
            cdTotals = null;
        }
        selectedIdx = -1;
    }

    public static void updateStatus(int[] newCounts, boolean[] newAlive, int[] newCooldowns) {
        if (newCounts != null && countsData != null && newCounts.length == countsData.length) {
            System.arraycopy(newCounts, 0, countsData, 0, newCounts.length);
        }
        if (newAlive != null && aliveData != null && newAlive.length == aliveData.length) {
            System.arraycopy(newAlive, 0, aliveData, 0, newAlive.length);
        }
        if (newCooldowns != null && cdTotals != null && newCooldowns.length == cdTotals.length) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < newCooldowns.length; i++) {
                cdTotals[i] = newCooldowns[i];
                cdTimestamps[i] = now;
            }
        }
    }

    public static void resetSelection() { selectedIdx = -1; }

    public static void startLocalCooldown(int vi) {
        if (vi < 0 || configs == null || vi >= configs.length) return;
        if (aliveData != null && vi < aliveData.length) aliveData[vi] = true;
    }

    public static boolean hasConfigs() { return configs != null && configs.length > 0; }

    public static VehicleConfig getSelectedConfig() {
        if (selectedIdx < 0 || configs == null || selectedIdx >= configs.length) return null;
        return configs[selectedIdx];
    }

    public static String getSelectedVehicleNbt() {
        VehicleConfig vc = getSelectedConfig();
        return vc != null ? vc.itemNbt : null;
    }

    public static boolean hasUsableSpawn() {
        VehicleConfig vc = getSelectedConfig();
        if (vc == null) return false;
        String vType = vc.type != null ? vc.type : "tank";
        return com.battlelinesystem.client.SpawnPointRenderer.hasUsableVehicleSpawn(vType);
    }

    /** 处理载具按钮点击，返回 true 表示已消费 */
    public static boolean handleClick(double guiX, double guiY) {
        if (configs == null || configs.length == 0) return false;
        for (int i = 0; i < configs.length && i < 32; i++) {
            String vType = configs[i].type != null ? configs[i].type : "tank";
            if (!com.battlelinesystem.client.SpawnPointRenderer.hasUsableVehicleSpawn(vType)) continue;
            int bi = i * 4;
            if (guiX >= rects[bi] && guiX <= rects[bi + 2]
                    && guiY >= rects[bi + 1] && guiY <= rects[bi + 3]) {
                boolean alive = aliveData != null && i < aliveData.length && aliveData[i];
                boolean onCooldown = (getCdRemaining(i) > 0);
                if (alive || onCooldown) return true;
                selectedIdx = (selectedIdx == i) ? -1 : i;
                com.battlelinesystem.client.SpawnPointRenderer.resetSelection();
                com.battlelinesystem.client.BeaconRenderer.resetSelection();
                com.battlelinesystem.client.LooseSpawnRenderer.resetSelection();
                return true;
            }
        }
        return false;
    }

    public static void render(GuiGraphics g, int screenW, int screenH, Minecraft mc) {
        if (configs == null || configs.length == 0) return;

        int validCount = 0;
        for (VehicleConfig config : configs) {
            if (config.itemNbt != null && !config.itemNbt.isEmpty()
                    && com.battlelinesystem.client.SpawnPointRenderer.hasUsableVehicleSpawn(
                            config.type != null ? config.type : "tank"))
                validCount++;
        }
        if (validCount == 0) return;

        int btnW = 34, btnH = 14, gap = 4;
        int totalW = validCount * btnW + (validCount - 1) * gap;
        int barY = screenH - 28 - 4 - btnH - gap;
        int startX = (screenW - totalW) / 2;

        for (int vi = 0, pos = 0; vi < configs.length; vi++) {
            VehicleConfig vc = configs[vi];
            if (vc.itemNbt == null || vc.itemNbt.isEmpty()) continue;
            String vType = vc.type != null ? vc.type : "tank";
            if (!com.battlelinesystem.client.SpawnPointRenderer.hasUsableVehicleSpawn(vType)) continue;
            int idx = pos++;

            int bx = startX + idx * (btnW + gap);
            rects[vi * 4] = bx;
            rects[vi * 4 + 1] = barY;
            rects[vi * 4 + 2] = bx + btnW;
            rects[vi * 4 + 3] = barY + btnH;

            boolean sel = selectedIdx == vi;
            boolean alive = aliveData != null && vi < aliveData.length && aliveData[vi];
            boolean onCooldown = (getCdRemaining(vi) > 0);
            boolean disabled = alive || onCooldown;
            String label = WaitHudOverlay.trimStr(mc, getLabel(vc, vi), btnW - 4);

            int bgColor, borderColor, textColor;
            if (sel) {
                bgColor = 0xCCAAAA00; borderColor = 0xFFAAAAAA; textColor = 0xFFFFFF;
            } else if (disabled) {
                bgColor = 0x40333333; borderColor = 0xFFFFFFFF; textColor = 0x88666666;
            } else {
                bgColor = 0x80448844; borderColor = 0xFFFFFFFF; textColor = 0xFFFFFF;
            }

            g.fill(bx, barY, bx + btnW, barY + btnH, bgColor);
            g.fill(bx, barY, bx + btnW, barY + 1, borderColor);
            g.fill(bx, barY + btnH - 1, bx + btnW, barY + btnH, borderColor);
            g.fill(bx, barY, bx + 1, barY + btnH, borderColor);
            g.fill(bx + btnW - 1, barY, bx + btnW, barY + btnH, borderColor);

            if (onCooldown && vc.cooldownSeconds > 0) {
                int remaining = getCdRemaining(vi);
                float elapsedRatio = 1f - (float) remaining / vc.cooldownSeconds;
                int fillH = (int)(btnH * elapsedRatio);
                g.fill(bx, barY + btnH - fillH, bx + btnW, barY + btnH, 0x8822AA44);
            }

            g.drawCenteredString(mc.font, label, bx + btnW / 2, barY + btnH / 2 - 4, textColor);
        }
    }

    private static int getCdRemaining(int vi) {
        if (cdTimestamps == null || cdTotals == null || vi >= cdTotals.length) return 0;
        int total = cdTotals[vi];
        if (total <= 0) return 0;
        long elapsed = (System.currentTimeMillis() - cdTimestamps[vi]) / 1000;
        int remaining = total - (int) elapsed;
        if (remaining <= 0) {
            cdTotals[vi] = 0;
            if (aliveData != null && vi < aliveData.length) aliveData[vi] = false;
            return 0;
        }
        return remaining;
    }

    private static String getLabel(VehicleConfig vc, int idx) {
        if (vc.name != null && !vc.name.isEmpty() && !vc.name.equals("新载具")) return vc.name;
        String nbt = vc.itemNbt;
        if (nbt != null && nbt.startsWith("{")) {
            try {
                CompoundTag root = TagParser.parseTag(nbt);
                if (root.contains("BlockEntityTag")) {
                    CompoundTag beTag = root.getCompound("BlockEntityTag");
                    if (beTag.contains("EntityType")) {
                        String et = beTag.getString("EntityType");
                        int slash = et.lastIndexOf('/');
                        int colon = et.indexOf(':');
                        if (slash >= 0) return et.substring(slash + 1);
                        if (colon >= 0) return et.substring(colon + 1);
                        return et;
                    }
                }
            } catch (Exception ignored) {}
        }
        return String.valueOf(idx + 1);
    }
}

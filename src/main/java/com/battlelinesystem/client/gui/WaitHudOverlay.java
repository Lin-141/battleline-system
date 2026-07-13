package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.ClassConfig;
import com.battlelinesystem.faction.ClassVariant;
import com.battlelinesystem.faction.GunAttachmentConfig;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketCapturePointViewTeleport;
import com.battlelinesystem.network.packet.PacketCapturePointViewTeleportRaw;
import com.battlelinesystem.network.packet.PacketClassSelect;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 等待 HUD — 右侧小地图 + 左侧职业选择（如阵营配了职业）
 */
public class WaitHudOverlay {

    public static boolean active = false;
    /** M 键手动切换是否允许（只有服务端下发后，即地图已选/阵营已选时才允许） */
    public static boolean allowManualToggle = false;
    /** 选完职业后 ClassSelectScreen 应该关闭 */
    public static boolean closeScreen = false;
    private static int tick = 0;

    /** 部署冷却结束时间戳（System.currentTimeMillis），0=无冷却 */
    private static long deployCooldownUntil = 0;

    // ---- 职业选择数据 ----
    private static String classFactionId = "";
    private static String classFactionName = "";
    private static String classFactionColor = "#FFFFFF";
    private static List<ClassConfig> classOptions = null;
    // 职业当前选取人数（由服务端下发）
    private static int[] classCounts = new int[0];
    // 总在线人数（由服务端下发，用于百分比上限计算）
    private static int totalPlayers = 0;
    // 按钮命中区域（screenW/4 居中，在左边渲染）
    private static final int CLASS_CARD_W = 147;
    private static final int CLASS_CARD_H = 30;
    private static final int CLASS_CARD_GAP = 4;
    private static final float ICON_SCALE = 1.6f;
    private static final int ICON_RENDER_SZ = (int)(16 * ICON_SCALE);
    private static final int ICON_GAP = 3;
    private static final int ICON_ROW_H = ICON_RENDER_SZ + 4;
    private static final int MAX_ICONS_PER_ROW = 9;
    private static final int[] classCardRects = new int[64]; // x0,y0,x1,y1 每组4个int，最多16个按钮

    // 每个职业当前选中的变体索引（-1=无需/无变体, 0=默认装配, 1+=variants[vi]）
    private static int[] selectedVariantIdx = new int[16];
    // 当前选中的职业卡片索引（-1=未选）
    private static int selectedClassIdx = -1;
    // 部署按钮命中区域
    private static int deployBtnX0, deployBtnY0, deployBtnX1, deployBtnY1;
    // 变体按钮命中区域: [classIdx*16 + variantIdx*4 + 0..3] = x0,y0,x1,y1
    private static final int[] variantBtnRects = new int[256];
    // 装备图标命中区域: [classIdx*MAX_ICONS_PER_ROW*4 + iconIdx*4 + 0..3] = x0,y0,x1,y1
    private static final int[] iconRects = new int[16 * MAX_ICONS_PER_ROW * 4];

    // ---- 部署淡入淡出 ----
    private static final ScreenFadeUtil deployFade = new ScreenFadeUtil();
    private static ClassConfig pendingDeployClass = null; // 淡入前缓存，避免 classOptions=null 后丢失
    private static String pendingCaptureSpawnName = null; // 同上，缓存据点出生选择
    private static java.util.UUID pendingBeaconUuid = null; // 同上，缓存信标选择
    private static java.util.UUID pendingLooseSpawnId = null; // 同上，缓存宽松重生点队友UUID
    private static int pendingSpawnIndex = -1; // 同上，缓存出生点索引

    // ---- 调试开关 ----
    public static class Debug {
        /** 在载具按钮上方显示状态信息: A=存活, C+数字=冷却剩余秒, OK=可用 */
        public static boolean showVehicleState = true;
    }

    public static boolean isFading() { return deployFade.isFading(); }

    /** 重置部署淡入淡出状态（取消进行中的动画） */
    public static void resetDeployFade() { deployFade.reset(); }
    // 每个图标对应的 NBT 字符串（用于改装写回）
    private static final String[][] iconNbtRefs = new String[16][MAX_ICONS_PER_ROW];
    // 每个图标对应的源类型: 0=cls装备槽, 1=cls额外, 2=variant装备槽, 3=variant额外
    // slotIdx: 0-4=装备槽, 100+idx=extra
    private static final int[][] iconNbtSource = new int[16][MAX_ICONS_PER_ROW];
    // 预解析缓存的装备图标 ItemStack（避免每帧 parseTag）
    private static final ItemStack[][] cachedIconStacks = new ItemStack[16][MAX_ICONS_PER_ROW];

    // ---- 载具选择（委托到 VehicleSelectionOverlay） ----
    private static com.battlelinesystem.faction.VehicleConfig[] vehicleConfigs = null;


    // ---- 据点快捷视角跳转 ----
    private static final int[] cpBtnRects = new int[26 * 4]; // 最多26个据点 A-Z
    private static int cpBtnCount = 0;    // 据点按钮数量
    private static final int[] spBtnRects = new int[32 * 4]; // 出生点按钮
    private static int spBtnCount = 0;    // 出生点按钮数量

    public static void tick() { tick++; }

    /** 仅关闭部署界面，不清理地图纹理 */
    public static void disable() {
        active = false;
        allowManualToggle = false;
    }

    public static void reset() {
        active = false;
        allowManualToggle = false;
        tick = 0;
        classOptions = null;
        selectedClassIdx = -1;
        for (int i = 0; i < selectedVariantIdx.length; i++) selectedVariantIdx[i] = -1;
        VehicleSelectionOverlay.resetSelection();
        vehicleConfigs = null;
        classFactionId = "";
        deployCooldownUntil = 0;
        com.battlelinesystem.client.LooseSpawnRenderer.clear();
    }

    public static void setClassOptions(String factionId, String factionName,
                                        String factionColor, List<ClassConfig> classes,
                                        int[] counts, int total,
                                        List<com.battlelinesystem.faction.VehicleConfig> vehicles,
                                        int[] vehicleCounts,
                                        boolean[] vehicleAlive, int[] vehicleCooldowns) {
        classFactionId = factionId;
        classFactionName = factionName;
        classFactionColor = factionColor;
        classOptions = classes != null ? classes : new ArrayList<>();
        classCounts = counts != null ? counts : new int[0];
        totalPlayers = total;
        // 更新据点HUD上的队伍名称
        com.battlelinesystem.client.CapturePointRenderer.onLocalFactionSelected(factionId);
        // myTeam 已在 PacketOpenClassVote.handle 中直接设置
        for (int i = 0; i < selectedVariantIdx.length; i++) selectedVariantIdx[i] = -1;
        VehicleSelectionOverlay.resetSelection();
        if (vehicles != null) {
            vehicleConfigs = vehicles.toArray(new com.battlelinesystem.faction.VehicleConfig[0]);
            VehicleSelectionOverlay.setConfigs(vehicleConfigs, vehicleCounts,
                    vehicleAlive, vehicleCooldowns);
        } else {
            vehicleConfigs = null;
            VehicleSelectionOverlay.setConfigs(null, null, null, null);
        }
        if (classOptions != null) {
            for (int i = 0; i < classOptions.size(); i++) {
                ClassConfig c = classOptions.get(i);
                if (c.variants != null && !c.variants.isEmpty()) {
                    selectedVariantIdx[i] = 0; // 默认选装配0
                }
                buildIconCache(i); // 预解析 NBT → ItemStack 缓存
            }
        }
        active = true;
        allowManualToggle = true;
    }

    /** 设置部署冷却倒计时（毫秒），由服务端下发 */
    public static void setDeployCooldownMs(int ms) {
        if (ms > 0) {
            deployCooldownUntil = System.currentTimeMillis() + ms;
        } else {
            deployCooldownUntil = 0;
        }
    }

    /** 实时更新职业人数和载具状态（不重新打开界面） */
    public static void updateClassCounts(String factionId, int[] newClassCounts,
                                         int[] newVehicleCounts,
                                         boolean[] newVehicleAlive,
                                         int[] newVehicleCooldowns) {
        if (!factionId.equals(classFactionId)) return;
        if (newClassCounts != null && classCounts != null && newClassCounts.length == classCounts.length) {
            System.arraycopy(newClassCounts, 0, classCounts, 0, newClassCounts.length);
        }
        VehicleSelectionOverlay.updateStatus(newVehicleCounts, newVehicleAlive, newVehicleCooldowns);
    }

    public static boolean hasClassOptions() {
        return classOptions != null && !classOptions.isEmpty();
    }

    public static boolean isClassSelectActive() {
        return active && classOptions != null && !classOptions.isEmpty() && !deployFade.isFading();
    }

    /** 处理鼠标点击，返回 true 表示已消费 */
    public static boolean handleClick(double guiX, double guiY) {
        if (!active || classOptions == null || classOptions.isEmpty() || deployFade.isFading()) return false;

        for (int i = 0; i < classOptions.size() && i < 16; i++) {
            ClassConfig c = classOptions.get(i);

            // 点击变体按钮 → 只切换选择（0=默认, 1+=真实变体），不能取消
            if (c.variants != null && !c.variants.isEmpty()) {
                int totalBtns = 1 + c.variants.size(); // 按钮0=默认装配
                for (int vi = 0; vi < totalBtns; vi++) {
                    int base = i * 16 + vi * 4;
                    int bx0 = variantBtnRects[base];
                    int by0 = variantBtnRects[base + 1];
                    int bx1 = variantBtnRects[base + 2];
                    int by1 = variantBtnRects[base + 3];
                    if (guiX >= bx0 && guiX <= bx1 && guiY >= by0 && guiY <= by1) {
                        if (vi > 0) {
                            ClassVariant v = c.variants.get(vi - 1);
                            if (!isUnlocked(v)) return false;
                        }
                        selectedVariantIdx[i] = vi;
                        buildIconCache(i); // 变体切换后重建图标缓存
                        return true;
                    }
                }
            }

            // 点击装备图标 → 枪械打开改装界面（仅已选中职业）
            if (selectedClassIdx == i) {
                for (int j = 0; j < MAX_ICONS_PER_ROW; j++) {
                    int ir = i * MAX_ICONS_PER_ROW * 4 + j * 4;
                    int ix0 = iconRects[ir], iy0 = iconRects[ir + 1], ix1 = iconRects[ir + 2], iy1 = iconRects[ir + 3];
                    if (ix0 == 0 && iy0 == 0 && ix1 == 0 && iy1 == 0) continue;
                    if (guiX >= ix0 && guiX <= ix1 && guiY >= iy0 && guiY <= iy1) {
                        String nbt = iconNbtRefs[i][j];
                        int src = iconNbtSource[i][j];
                        if (nbt != null && isTaczGun(nbt)) {
                            ClassConfig srcCls = classOptions.get(i);
                            List<GunAttachmentConfig> pools = srcCls.gunAttachmentPools;
                            int clsI = i, iconJ = j;
                            String oldNbt = nbt;
                            Minecraft.getInstance().setScreen(new GunModScreen(nbt, pools,
                                    newNbt -> writeBackNbt(clsI, iconJ, oldNbt, newNbt),
                                    () -> Minecraft.getInstance().setScreen(new ClassSelectScreen())));
                        }
                        return true;
                    }
                }
            }

            // 点击卡片主体 → 仅选中高亮，不发装备
            int x0 = classCardRects[i * 4];
            int y0 = classCardRects[i * 4 + 1];
            int x1 = classCardRects[i * 4 + 2];
            int y1 = classCardRects[i * 4 + 3];
            if (guiX >= x0 && guiX <= x1 && guiY >= y0 && guiY <= y1) {
                selectedClassIdx = i;
                // 无变体时自动选中默认装配
                ClassConfig cc = classOptions.get(i);
                if (cc.variants == null || cc.variants.isEmpty()) {
                    selectedVariantIdx[i] = -1;
                } else if (selectedVariantIdx[i] < 0) {
                    selectedVariantIdx[i] = 0;
                }
                return true;
            }
        }

        // 点击据点视角跳转按钮
        if (handleCapturePointBarClick(guiX, guiY)) {
            return true;
        }

        // 点击部署按钮 → 开始淡入黑屏，之后才发送装备
        if (guiX >= deployBtnX0 && guiX <= deployBtnX1 && guiY >= deployBtnY0 && guiY <= deployBtnY1) {
            long remainingMs = Math.max(0, deployCooldownUntil - System.currentTimeMillis());
            if (remainingMs > 0) return true; // 冷却中，忽略点击
            if (selectedClassIdx >= 0 && selectedClassIdx < classOptions.size()) {
                startDeployFade();
            }
            return true;
        }

        // 点击出生点标记 → 选中出生点（互斥：清空信标/宽松重生点/载具）
        if (com.battlelinesystem.client.SpawnPointRenderer.handleClick((int) guiX, (int) guiY)) {
            com.battlelinesystem.client.BeaconRenderer.resetSelection();
            com.battlelinesystem.client.LooseSpawnRenderer.resetSelection();
            VehicleSelectionOverlay.resetSelection();
            return true;
        }

        // 点击信标实体 → 选中信标（互斥：清空出生点/宽松重生点）
        if (com.battlelinesystem.client.BeaconRenderer.handleClick((int) guiX, (int) guiY)) {
            com.battlelinesystem.client.SpawnPointRenderer.resetSelection();
            com.battlelinesystem.client.LooseSpawnRenderer.resetSelection();
            return true;
        }

        // 点击队友（宽松重生点）→ 选中队友部署位置
        if (com.battlelinesystem.client.LooseSpawnRenderer.handleClick((int) guiX, (int) guiY)) {
            return true;
        }

        // 点击载具选择
        if (VehicleSelectionOverlay.handleClick(guiX, guiY)) return true;

        return false;
    }

    private static void startDeployFade() {
        if (deployFade.isFading()) return;
        // 重置旧状态，防止上次部署残留 closeScreen 导致界面异常
        closeScreen = false;
        // 先缓存部署数据（之后 classOptions 会被清空）
        if (selectedClassIdx >= 0 && classOptions != null && selectedClassIdx < classOptions.size()) {
            pendingDeployClass = classOptions.get(selectedClassIdx);
        } else {
            pendingDeployClass = null;
        }
        // 缓存据点出生选择（resetSelection 会清空）
        pendingCaptureSpawnName = com.battlelinesystem.client.SpawnPointRenderer.getSelectedCapturePointName();
        // 缓存信标选择（resetSelection 会清空）
        pendingBeaconUuid = com.battlelinesystem.client.BeaconRenderer.getSelectedBeaconUuid();
        // 缓存宽松重生点选择
        pendingLooseSpawnId = com.battlelinesystem.client.LooseSpawnRenderer.getSelectedUUID();
        // 缓存出生点索引（resetSelection 会清空）
        pendingSpawnIndex = com.battlelinesystem.client.SpawnPointRenderer.getSelectedSpawnIndex();
        // 清空部署界面数据（不关 closeScreen，让 Screen 继续渲染黑屏覆盖）
        classOptions = null;
        active = false;
        com.battlelinesystem.client.SpawnPointRenderer.resetSelection();
        com.battlelinesystem.client.BeaconRenderer.resetSelection();
        // 再开始黑屏淡入
        deployFade.start(20, 10, 10,
                WaitHudOverlay::deploySelected,          // 全黑后发送部署
                () -> closeScreen = true);                 // 淡出后关闭界面
    }

    /** 由 BattleLineSystem 调用，不依赖 active 状态 */
    public static void tickFade() {
        deployFade.tick();
    }

    private static void deploySelected() {
        ClassConfig c = pendingDeployClass;
        if (c == null) return;
        pendingDeployClass = null;

        String equipHelmet = c.helmet, equipChest = c.chestplate, equipLegs = c.leggings,
               equipBoots = c.boots, equipOff = c.offHand;
        List<String> equipExtras = c.extraItems;
        String vid = "";

        if (c.variants != null && !c.variants.isEmpty()) {
            if (selectedVariantIdx[selectedClassIdx] > 0) {
                int realVi = selectedVariantIdx[selectedClassIdx] - 1;
                if (realVi < c.variants.size()) {
                    ClassVariant v = c.variants.get(realVi);
                    if (!isUnlocked(v)) return;
                    vid = v.id;
                    equipHelmet = v.helmet;
                    equipChest = v.chestplate;
                    equipLegs = v.leggings;
                    equipBoots = v.boots;
                    equipOff = v.offHand;
                    equipExtras = v.extraItems;
                }
            }
        }
        String vehicleNbt = null;
        int vehicleSlot = -1;
        if (VehicleSelectionOverlay.getSelectedConfig() != null && VehicleSelectionOverlay.hasUsableSpawn()) {
            vehicleNbt = VehicleSelectionOverlay.getSelectedVehicleNbt();
            vehicleSlot = VehicleSelectionOverlay.getSelectedIdx();
        }
        AllPackets.getChannel().sendToServer(
                new PacketClassSelect(classFactionId, c.id, vid,
                        equipHelmet, equipChest, equipLegs, equipBoots, equipOff, equipExtras,
                        pendingSpawnIndex,
                        pendingCaptureSpawnName,
                        vehicleNbt, vehicleSlot,
                        pendingBeaconUuid,
                        pendingLooseSpawnId));
        // 客户端本地标记存活，等待服务端 Push 冷却数据
        VehicleSelectionOverlay.startLocalCooldown(VehicleSelectionOverlay.getSelectedIdx());
        pendingCaptureSpawnName = null;
        pendingBeaconUuid = null;
        pendingLooseSpawnId = null;
        pendingSpawnIndex = -1;
        // 界面清理已由 startDeployFade 处理
    }

    private static boolean isUnlocked(ClassVariant v) {
        if (v.unlockCondition == null || v.unlockCondition.isEmpty()) return true;
        String cond = v.unlockCondition.trim();
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (cond.startsWith("permission:")) {
            String perm = cond.substring("permission:".length());
            return player.hasPermissions(2);
        }
        if (cond.startsWith("level:")) {
            try {
                int lv = Integer.parseInt(cond.substring("level:".length()));
                return player.experienceLevel >= lv;
            } catch (NumberFormatException e) { return false; }
        }
        if (cond.startsWith("purchase:")) {
            return true; // 客户端放行，由服务端 PacketClassSelect 精确校验
        }
        return true;
    }

    public static void render(GuiGraphics g, int screenW, int screenH) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();

        // ===== 左侧选择区域 =====
        boolean inClassSelect = classOptions != null && !classOptions.isEmpty();
        int leftCenter = screenW / 4;
        if (inClassSelect) {
            renderClassSelection(g, screenW, screenH, leftCenter, mc);
        } else {
            g.drawCenteredString(mc.font, "已选择阵营", leftCenter, screenH / 2 - 16, 0xFFFFFF);
            int dots = (tick / 30) % 4;
            StringBuilder sb = new StringBuilder("等待其他玩家准备");
            for (int i = 0; i < dots; i++) sb.append(".");
            g.drawCenteredString(mc.font, sb.toString(), leftCenter, screenH / 2 + 4, 0xFFAAAAAA);
        }

        // === 部署按钮（屏幕右下角） ===
        if (inClassSelect) {
            int deployW = 80, deployH = 28;
            int deployX = screenW - deployW - 10;
            int deployY = screenH - deployH - 4;
            deployBtnX0 = deployX;
            deployBtnY0 = deployY;
            deployBtnX1 = deployX + deployW;
            deployBtnY1 = deployY + deployH;
            boolean hasSelection = selectedClassIdx >= 0;
            long remainingMs = Math.max(0, deployCooldownUntil - System.currentTimeMillis());
            boolean inCooldown = remainingMs > 0;
            int deployBg;
            int textColor;
            String btnText;
            if (inCooldown) {
                // 冷却中：灰色 + 倒计时
                deployBg = 0xCC444444;
                textColor = 0xFF888888;
                int sec = (int) Math.ceil(remainingMs / 1000.0);
                btnText = String.valueOf(sec);
            } else {
                deployBg = hasSelection ? 0xCC229944 : 0xCC000000;
                textColor = hasSelection ? 0xFFFFFF : 0xFF555555;
                btnText = "部署";
            }
            int deployBorder = 0xFFFFFFFF;
            g.fill(deployBtnX0, deployBtnY0, deployBtnX1, deployBtnY1, deployBg);
            // 描边
            g.fill(deployBtnX0, deployBtnY0, deployBtnX1, deployBtnY0 + 1, deployBorder);
            g.fill(deployBtnX0, deployBtnY1 - 1, deployBtnX1, deployBtnY1, deployBorder);
            g.fill(deployBtnX0, deployBtnY0, deployBtnX0 + 1, deployBtnY1, deployBorder);
            g.fill(deployBtnX1 - 1, deployBtnY0, deployBtnX1, deployBtnY1, deployBorder);
            g.drawCenteredString(mc.font, btnText, deployX + deployW / 2, deployY + deployH / 2 - 4, textColor);

            // 显示选中的出生点
            String spawnLabel = com.battlelinesystem.client.SpawnPointRenderer.getSelectedLabel();
            java.util.UUID beaconUid = com.battlelinesystem.client.BeaconRenderer.getSelectedBeaconUuid();
            java.util.UUID looseUid = com.battlelinesystem.client.LooseSpawnRenderer.getSelectedUUID();
            if (!spawnLabel.isEmpty()) {
                g.drawCenteredString(mc.font, spawnLabel, screenW / 2, deployY + deployH / 2 - 4, 0xFFCC44);
            } else if (looseUid != null) {
                g.drawCenteredString(mc.font, "已选队友", screenW / 2, deployY + deployH / 2 - 4, 0xFF00FF88);
            } else if (beaconUid != null) {
                g.drawCenteredString(mc.font, "已选信标", screenW / 2, deployY + deployH / 2 - 4, 0xFF00FF88);
            }

            // 载具池渲染（在据点选择栏上方）
            VehicleSelectionOverlay.render(g, screenW, screenH, mc);

            // === 据点快捷视角跳转栏 ===
            renderCapturePointBar(g, screenW, screenH, mc);
        }

        // 部署淡入淡出黑屏覆盖（最上层）
        renderFadeOverlay(g, screenW, screenH);
    }

    /** 由 ClassSelectScreen 调用，渲染黑屏覆盖 */
    public static void renderFadeOverlay(GuiGraphics g, int screenW, int screenH) {
        deployFade.render(g, screenW, screenH);
    }

    // === 据点快捷视角跳转 ===

    private static void renderCapturePointBar(GuiGraphics g, int screenW, int screenH, Minecraft mc) {
        java.util.List<com.battlelinesystem.world.MapConfig.CapturePoint> cps =
                com.battlelinesystem.client.CapturePointRenderer.getCapturePoints();
        net.minecraft.core.BlockPos[] sps = com.battlelinesystem.client.SpawnPointRenderer.getMySpawnPoints();

        int n = Math.min(cps.size(), 26);
        int m = Math.min(sps.length, 32);
        cpBtnCount = n;
        spBtnCount = m;

        if (n == 0 && m == 0) return;

        int btnSize = 28;
        int gap = 4;
        int totalW = (n + m) * btnSize + (n + m - 1) * gap;
        int barY = screenH - btnSize - 4;
        int startX = (screenW - totalW) / 2;

        // 出生点按钮（最左边）
        int myTeam = com.battlelinesystem.client.SpawnPointRenderer.getMyTeam();
        int selTeam = com.battlelinesystem.client.SpawnPointRenderer.getSelectedTeam();
        int selIdx = com.battlelinesystem.client.SpawnPointRenderer.getSelectedSpawnIndex();
        for (int i = 0; i < m; i++) {
            int bx = startX + i * (btnSize + gap);
            spBtnRects[i * 4] = bx;
            spBtnRects[i * 4 + 1] = barY;
            spBtnRects[i * 4 + 2] = bx + btnSize;
            spBtnRects[i * 4 + 3] = barY + btnSize;

            boolean isSelected = (selTeam == myTeam && selIdx == i);
            int bgColor = isSelected ? 0xCCFFAA00 : 0x80448844;
            int borderColor = 0xFFFFFFFF;

            drawBarButton(g, bx, barY, btnSize, btnSize, bgColor, borderColor,
                    String.valueOf(i + 1), mc.font);
        }

        // 据点按钮（出生点右边）
        for (int i = 0; i < n; i++) {
            int bx = startX + (m + i) * (btnSize + gap);
            cpBtnRects[i * 4] = bx;
            cpBtnRects[i * 4 + 1] = barY;
            cpBtnRects[i * 4 + 2] = bx + btnSize;
            cpBtnRects[i * 4 + 3] = barY + btnSize;

            com.battlelinesystem.client.CapturePointRenderer.CaptureProgressData prog =
                    com.battlelinesystem.client.CapturePointRenderer.getProgress(cps.get(i).name);

            // 进度已被反推到敌方一侧 → 不可传送，变灰
            boolean canSpawnHere = (myTeam == 0 && prog != null && prog.owner == 1 && prog.progress < 0)
                    || (myTeam == 1 && prog != null && prog.owner == 2 && prog.progress > 0);

            int bgColor;
            if (prog != null && prog.owner != 0) {
                // 己方=蓝, 敌方=红
                boolean isMine = (myTeam == 0 && prog.owner == 1) || (myTeam == 1 && prog.owner == 2);
                if (isMine) {
                    bgColor = canSpawnHere ? 0xCC4488FF : 0x664488FF;
                } else {
                    bgColor = 0xCCFF4444;
                }
            } else if (prog != null && prog.capturing != 0) {
                bgColor = 0xCCCC6600;
            } else {
                bgColor = 0x80444444;
            }

            drawBarButton(g, bx, barY, btnSize, btnSize, bgColor, 0xFFFFFFFF,
                    String.valueOf((char)('A' + i)), mc.font);
        }
    }

    private static void drawBarButton(GuiGraphics g, int bx, int barY, int btnW, int btnH,
                                       int bgColor, int borderColor, String label, net.minecraft.client.gui.Font font) {
        g.fill(bx, barY, bx + btnW, barY + btnH, bgColor);
        // 描边内部
        g.fill(bx, barY, bx + btnW, barY + 1, borderColor);
        g.fill(bx, barY + btnH - 1, bx + btnW, barY + btnH, borderColor);
        g.fill(bx, barY, bx + 1, barY + btnH, borderColor);
        g.fill(bx + btnW - 1, barY, bx + btnW, barY + btnH, borderColor);
        g.drawCenteredString(font, label, bx + btnW / 2, barY + btnH / 2 - 4, 0xFFFFFF);
    }

    private static boolean handleCapturePointBarClick(double guiX, double guiY) {
        java.util.List<com.battlelinesystem.world.MapConfig.CapturePoint> cps =
                com.battlelinesystem.client.CapturePointRenderer.getCapturePoints();
        net.minecraft.core.BlockPos[] sps = com.battlelinesystem.client.SpawnPointRenderer.getMySpawnPoints();

        int myTeam = com.battlelinesystem.client.SpawnPointRenderer.getMyTeam();

        // 出生点按钮（先检查，排左边）
        for (int i = 0; i < spBtnCount; i++) {
            int bx0 = spBtnRects[i * 4], by0 = spBtnRects[i * 4 + 1];
            int bx1 = spBtnRects[i * 4 + 2], by1 = spBtnRects[i * 4 + 3];
            if (guiX >= bx0 && guiX <= bx1 && guiY >= by0 && guiY <= by1) {
                com.battlelinesystem.client.SpawnPointRenderer.setSelectedSpawnIndex(myTeam, i);
                com.battlelinesystem.client.LooseSpawnRenderer.resetSelection();
                VehicleSelectionOverlay.resetSelection();
                net.minecraft.core.BlockPos sp = sps[i];
                AllPackets.getChannel().sendToServer(
                        new PacketCapturePointViewTeleportRaw(sp));
                return true;
            }
        }

        // 据点按钮
        for (int i = 0; i < cpBtnCount; i++) {
            int bx0 = cpBtnRects[i * 4], by0 = cpBtnRects[i * 4 + 1];
            int bx1 = cpBtnRects[i * 4 + 2], by1 = cpBtnRects[i * 4 + 3];
            if (guiX >= bx0 && guiX <= bx1 && guiY >= by0 && guiY <= by1) {
                com.battlelinesystem.client.CapturePointRenderer.CaptureProgressData prog =
                        com.battlelinesystem.client.CapturePointRenderer.getProgress(cps.get(i).name);
                // 本队完全占领 + 进度仍在己方一侧才能传送
                boolean ownedByMe = (myTeam == 0 && prog != null && prog.owner == 1 && prog.progress < 0)
                        || (myTeam == 1 && prog != null && prog.owner == 2 && prog.progress > 0);
                if (!ownedByMe) return false;
                com.battlelinesystem.client.SpawnPointRenderer.setSelectedCapturePointName(cps.get(i).name);
                com.battlelinesystem.client.LooseSpawnRenderer.resetSelection();
                VehicleSelectionOverlay.resetSelection();
                AllPackets.getChannel().sendToServer(
                        new PacketCapturePointViewTeleport(cps.get(i).name));
                return true;
            }
        }
        return false;
    }

    private static void renderClassSelection(GuiGraphics g, int screenW, int screenH,
                                              int centerX, Minecraft mc) {
        if (classOptions == null || classOptions.isEmpty()) return;

        int cardW = CLASS_CARD_W, cardH = CLASS_CARD_H;
        int cardX = 4; // 紧贴屏幕左侧

        // 计算总高度：选中项 = cardH + iconRow + gap，未选中 = cardH + 3
        int count = Math.min(classOptions.size(), 16);
        int totalH = 0;
        for (int i = 0; i < count; i++) {
            totalH += cardH;
            totalH += (selectedClassIdx == i) ? (ICON_ROW_H + CLASS_CARD_GAP) : 3;
        }
        totalH -= 3; // 去掉末尾间距

        int startY = screenH / 2 - totalH / 2;

        int titleY = startY - 14;
        if (titleY < 10) titleY = 10;
        g.drawString(mc.font, classFactionName, cardX, titleY, 0xFFFFFF);

        int pc = parseColor(classFactionColor);
        int cy = startY;

        for (int i = 0; i < count; i++) {
            ClassConfig c = classOptions.get(i);
            boolean hasVars = c.variants != null && !c.variants.isEmpty();
            boolean isSelected = selectedClassIdx == i;

            // === 卡片命中区域 ===
            int rectIdx = i * 4;
            classCardRects[rectIdx] = cardX;
            classCardRects[rectIdx + 1] = cy;
            classCardRects[rectIdx + 2] = cardX + cardW;
            classCardRects[rectIdx + 3] = cy + cardH;

            // === 卡片背景 ===
            g.fill(cardX, cy, cardX + cardW, cy + cardH,
                    isSelected ? 0xAA333333 : 0x80000000);
            if (isSelected) {
                g.renderOutline(cardX - 1, cy - 1, cardW + 2, cardH + 2, 0xFFFFCC00);
            }

            // === 卡片内：名字 左对齐 ===
            String trimmedName = trimStr(mc, c.name, cardW - 110);
            g.drawString(mc.font, trimmedName, cardX + 6, cy + 4, 0xFFFFFF);

            // === 人数上限计数 ===
            if (c.maxPlayers > 0 && totalPlayers > 0 && i < classCounts.length) {
                int limit = Math.max(1, (int) Math.ceil(totalPlayers * c.maxPlayers / 100.0));
                int cur = classCounts[i];
                int countColor = cur >= limit ? 0xFF5555 : 0x55FF55;
                String countStr = cur + "/" + limit;
                int cw = mc.font.width(countStr);
                g.drawString(mc.font, countStr, cardX + cardW - cw - 6, cy + 4, countColor);
            }

            // === 变体按钮（卡片内右侧，底部对齐"默认装配"文字） ===
            if (hasVars) {
                int totalBtns = 1 + c.variants.size();
                int bw = 10, bh = 10;
                int btnY = cy + 16 + mc.font.lineHeight - bh;
                for (int vi = 0; vi < totalBtns && vi < 8; vi++) {
                    boolean unlocked;
                    if (vi == 0) unlocked = true;
                    else unlocked = isUnlocked(c.variants.get(vi - 1));
                    boolean sel = selectedVariantIdx[i] == vi;
                    int bx = cardX + cardW - 6 - (totalBtns - vi) * (bw + 2);
                    int base = i * 16 + vi * 4;
                    variantBtnRects[base] = bx;
                    variantBtnRects[base + 1] = btnY;
                    variantBtnRects[base + 2] = bx + bw;
                    variantBtnRects[base + 3] = btnY + bh;

                    int btnBg;
                    if (!unlocked) btnBg = 0xFF555555;
                    else if (sel) btnBg = 0xAA40A040;
                    else btnBg = 0x80004488;
                    g.fill(bx, btnY, bx + bw, btnY + bh, btnBg);
                    if (!unlocked) {
                        g.hLine(bx, bx + bw - 1, btnY, 0xFF777777);
                        g.hLine(bx, bx + bw - 1, btnY + bh - 1, 0xFF777777);
                        g.vLine(bx, btnY, btnY + bh - 1, 0xFF777777);
                        g.vLine(bx + bw - 1, btnY, btnY + bh - 1, 0xFF777777);
                    }
                    int numColor = unlocked ? 0xFFFFFF : 0xFF999999;
                    g.drawCenteredString(mc.font, String.valueOf(vi + 1), bx + bw / 2,
                            btnY + bh / 2 - mc.font.lineHeight / 2, numColor);
                }
            }

            // === 选中提示 ===
            if (hasVars) {
                String hint = selectedVariantIdx[i] > 0
                        ? c.variants.get(selectedVariantIdx[i] - 1).name
                        : "默认装配";
                g.drawString(mc.font, hint, cardX + 6, cy + 16, 0x88FF88);
            }

            // === 颜色条 ===
            g.fill(cardX + 4, cy + cardH - 3, cardX + cardW - 4, cy + cardH - 1, 0xFF000000 | pc);

            // === 装备图标行（仅选中的职业展示） ===
            if (isSelected) {
                int iconY = cy + cardH + 2;
                int step = ICON_RENDER_SZ + ICON_GAP;
                int maxIcons = MAX_ICONS_PER_ROW;
                // 用预解析缓存替代每帧 collectIconsWithNbt + parseTag
                int visible = 0;
                for (int j = 0; j < maxIcons; j++) {
                    if (!cachedIconStacks[i][j].isEmpty()) visible++;
                    else break;
                }
                int iconStartX = cardX + 4;
                for (int j = 0; j < visible; j++) {
                    int ix = iconStartX + j * step;
                    int ir = i * maxIcons * 4 + j * 4;
                    iconRects[ir] = ix;
                    iconRects[ir + 1] = iconY;
                    iconRects[ir + 2] = ix + ICON_RENDER_SZ;
                    iconRects[ir + 3] = iconY + ICON_RENDER_SZ;
                    g.fill(ix, iconY, ix + ICON_RENDER_SZ, iconY + ICON_RENDER_SZ, 0x40000000);
                    g.pose().pushPose();
                    g.pose().translate(ix, iconY, 0);
                    g.pose().scale(ICON_SCALE, ICON_SCALE, 1);
                    g.renderItem(cachedIconStacks[i][j], 0, 0);
                    g.pose().popPose();
                }
            } else {
                // 未选中时清空此职业的图标命中区
                int maxIcons = MAX_ICONS_PER_ROW;
                for (int j = 0; j < maxIcons; j++) {
                    int ir = i * maxIcons * 4 + j * 4;
                    iconRects[ir] = iconRects[ir + 1] = iconRects[ir + 2] = iconRects[ir + 3] = 0;
                }
            }

            // 推进 Y
            cy += cardH + (isSelected ? (ICON_ROW_H + CLASS_CARD_GAP) : 3);
        }
    }

    static String trimStr(Minecraft mc, String s, int maxW) {
        if (mc.font.width(s) <= maxW) return s;
        while (s.length() > 1 && mc.font.width(s + "...") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    private static void collectIconsWithNbt(List<ItemStack> out, String helmet, String chestplate,
                                             String leggings, String boots, String offHand,
                                             List<String> extras, ClassConfig whitelist,
                                             int clsIdx, int srcType) {
        List<String> nbtRefs = new ArrayList<>();
        boolean skipArmor = whitelist != null && whitelist.hideArmorIcons;
        if (!skipArmor) {
            addIconWithNbt(out, nbtRefs, helmet, whitelist);
            addIconWithNbt(out, nbtRefs, chestplate, whitelist);
            addIconWithNbt(out, nbtRefs, leggings, whitelist);
            addIconWithNbt(out, nbtRefs, boots, whitelist);
        }
        addIconWithNbt(out, nbtRefs, offHand, whitelist);
        if (extras != null) {
            for (String e : extras) addIconWithNbt(out, nbtRefs, e, whitelist);
        }
        for (int j = 0; j < nbtRefs.size() && j < MAX_ICONS_PER_ROW; j++) {
            iconNbtRefs[clsIdx][j] = nbtRefs.get(j);
            iconNbtSource[clsIdx][j] = srcType;
        }
    }

    /** 预解析职业装备图标并缓存，避免每帧 TagParser.parseTag() */
    private static void buildIconCache(int clsIdx) {
        if (classOptions == null || clsIdx >= classOptions.size()) return;
        ClassConfig c = classOptions.get(clsIdx);
        // 清空缓存
        for (int j = 0; j < MAX_ICONS_PER_ROW; j++) cachedIconStacks[clsIdx][j] = ItemStack.EMPTY;

        List<ItemStack> icons = new ArrayList<>();
        boolean hasVars = c.variants != null && !c.variants.isEmpty();
        if (hasVars && selectedVariantIdx[clsIdx] > 0) {
            int vi = selectedVariantIdx[clsIdx] - 1;
            if (vi < c.variants.size()) {
                ClassVariant v = c.variants.get(vi);
                collectIconsWithNbt(icons, v.helmet, v.chestplate, v.leggings, v.boots, v.offHand, v.extraItems, c, clsIdx, 2);
            }
        } else {
            collectIconsWithNbt(icons, c.helmet, c.chestplate, c.leggings, c.boots, c.offHand, c.extraItems, c, clsIdx, 0);
        }
        int len = Math.min(icons.size(), MAX_ICONS_PER_ROW);
        for (int j = 0; j < len; j++) cachedIconStacks[clsIdx][j] = icons.get(j);
    }

    private static void addIconWithNbt(List<ItemStack> out, List<String> nbtRefs, String nbtStr, ClassConfig whitelist) {
        if (nbtStr == null || nbtStr.isEmpty()) return;
        ItemStack st = parseNbtItem(nbtStr);
        if (st.isEmpty()) return;
        String rn = getRegistryName(st);
        if (rn.contains("tacz:ammo_box")) return;
        if (whitelist != null && whitelist.isHidden(rn)) return;
        out.add(st);
        nbtRefs.add(nbtStr);
    }

    /** 从 NBT 字符串解析 ItemStack */
    private static ItemStack parseNbtItem(String s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (s.startsWith("{")) {
            try { return ItemStack.of(TagParser.parseTag(s)); }
            catch (Exception e) { return ItemStack.EMPTY; }
        }
        try {
            int colon = s.indexOf(':');
            ResourceLocation rl;
            if (colon >= 0) rl = new ResourceLocation(s.substring(0, colon), s.substring(colon + 1));
            else rl = new ResourceLocation(s);
            Item item = BuiltInRegistries.ITEM.get(rl);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    /** 获取 ItemStack 的注册名（用于隐藏判定） */
    private static String getRegistryName(ItemStack st) {
        var key = BuiltInRegistries.ITEM.getKey(st.getItem());
        return key != null ? key.toString() : "";
    }

    /** 判断 NBT 字符串是否为 tacz 枪械（有 GunId 标签） */
    private static boolean isTaczGun(String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) return false;
        // SNBT 格式: {id:"tacz:ak47",...tag:{GunId:"..."}}
        return nbtStr.contains("id:\"tacz:") && nbtStr.contains("GunId");
    }

    /** 把修改后的 NBT 写回 ClassConfig/Variant 对应位置，并更新追踪数组 */
    private static void writeBackNbt(int clsIdx, int iconIdx, String oldNbt, String newNbt) {
        if (clsIdx >= classOptions.size() || oldNbt == null) return;
        ClassConfig c = classOptions.get(clsIdx);
        int src = iconNbtSource[clsIdx][iconIdx];
        boolean modified = false;

        // 尝试匹配和替换
        ClassVariant activeVar = null;
        if (src == 2 && c.variants != null && selectedVariantIdx[clsIdx] > 0) {
            int realVi = selectedVariantIdx[clsIdx] - 1;
            if (realVi < c.variants.size()) {
                activeVar = c.variants.get(realVi);
            }
        }

        // 装备槽位匹配
        String[] slots = (activeVar != null) ?
                new String[]{activeVar.helmet, activeVar.chestplate, activeVar.leggings, activeVar.boots, activeVar.offHand} :
                new String[]{c.helmet, c.chestplate, c.leggings, c.boots, c.offHand};

        for (int s = 0; s < 5; s++) {
            if (oldNbt.equals(slots[s])) {
                if (activeVar != null) {
                    switch (s) { case 0 -> activeVar.helmet = newNbt; case 1 -> activeVar.chestplate = newNbt;
                        case 2 -> activeVar.leggings = newNbt; case 3 -> activeVar.boots = newNbt;
                        case 4 -> activeVar.offHand = newNbt; }
                } else {
                    switch (s) { case 0 -> c.helmet = newNbt; case 1 -> c.chestplate = newNbt;
                        case 2 -> c.leggings = newNbt; case 3 -> c.boots = newNbt;
                        case 4 -> c.offHand = newNbt; }
                }
                iconNbtRefs[clsIdx][iconIdx] = newNbt;
                modified = true;
                break;
            }
        }

        // 额外物品匹配
        if (!modified) {
            List<String> extras = (activeVar != null) ? activeVar.extraItems : c.extraItems;
            if (extras != null) {
                for (int e = 0; e < extras.size(); e++) {
                    if (oldNbt.equals(extras.get(e))) {
                        extras.set(e, newNbt);
                        iconNbtRefs[clsIdx][iconIdx] = newNbt;
                        modified = true;
                        break;
                    }
                }
            }
        }

        // 发送到服务端持久化
        if (modified && classFactionId != null && !classFactionId.isEmpty()) {
            com.battlelinesystem.network.AllPackets.getChannel().sendToServer(
                    new com.battlelinesystem.network.packet.PacketSaveGunMod(classFactionId, c.id, oldNbt, newNbt));
        }
    }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) { return 0xFFFFFF; }
    }
}

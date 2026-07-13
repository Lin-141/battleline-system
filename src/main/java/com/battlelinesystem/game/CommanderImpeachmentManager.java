package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 弹劾指挥官管理器 — 同队玩家可发起弹劾，投票决定是否替换指挥官。
 * 弹劾期 30 秒，同意 > 反对即弹劾成功。每次弹劾结束后冷却 3 分钟。
 */
public class CommanderImpeachmentManager {

    private static final CommanderImpeachmentManager INSTANCE = new CommanderImpeachmentManager();
    private static final int IMPEACH_DURATION = 30;
    private static final long COOLDOWN_MS = 180_000; // 3 分钟

    private final Map<String, Impeachment> activeImpeachments = new HashMap<>();
    /** 队伍 -> 冷却结束时间戳 (System.currentTimeMillis()) */
    private final Map<String, Long> cooldowns = new HashMap<>();
    private long lastTick;

    private CommanderImpeachmentManager() {}

    public static CommanderImpeachmentManager getInstance() { return INSTANCE; }

    // ---- 查询 ----

    public boolean hasActive(String team) {
        Impeachment imp = activeImpeachments.get(team);
        return imp != null && imp.remaining > 0 && !imp.finished;
    }

    /** 获取剩余冷却秒数，-1=不在冷却中 */
    public int getCooldownSeconds(String team) {
        Long end = cooldowns.get(team);
        if (end == null) return -1;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(team);
            return -1;
        }
        return (int) (remaining / 1000);
    }

    public int getRemaining(String team) {
        Impeachment imp = activeImpeachments.get(team);
        return imp != null ? imp.remaining : -1;
    }

    // ---- 发起弹劾 ----

    public boolean startImpeachment(String team, UUID initiatorUuid, String initiatorName,
                                     String commanderName, UUID replacementUuid, String replacementName,
                                     String reason, MinecraftServer server) {
        if (hasActive(team)) return false;
        if (commanderName == null) return false;
        if (initiatorName.equals(commanderName)) return false;
        if (replacementName.equals(commanderName)) return false;

        int cd = getCooldownSeconds(team);
        if (cd > 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt) && sp.getUUID().equals(initiatorUuid)) {
                    sp.sendSystemMessage(Component.literal("§c弹劾冷却中，请等待 " + cd + " 秒"));
                }
            }
            return false;
        }

        Impeachment imp = new Impeachment(team, initiatorUuid, initiatorName,
                commanderName, replacementUuid, replacementName, IMPEACH_DURATION);
        activeImpeachments.put(team, imp);

        // 广播弹劾消息（带可点击的交互文字）
        String reasonLine = (reason != null && !reason.isEmpty())
                ? " §7理由: §f" + reason : "";
        Component msg = Component.literal("")
                .append(Component.literal("§6[弹劾] §f" + initiatorName
                        + " §7发起弹劾指挥官 §c" + commanderName
                        + " §7，提议由 §b" + replacementName + " §7接任。" + reasonLine + " "))
                .append(Component.literal("[§a同意§f]")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/bls kickcommandervote agree"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§a点击投票同意弹劾")))))
                .append(Component.literal(" "))
                .append(Component.literal("[§c反对§f]")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/bls kickcommandervote disagree"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§c点击投票反对弹劾")))))
                .append(Component.literal(" §7(" + IMPEACH_DURATION + "秒)"));

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
            if (team.equals(pt)) {
                sp.sendSystemMessage(msg);
            }
        }

        BattleLineSystem.LOGGER.info("[弹劾] {}队 {} 发起弹劾指挥官 {}，提议 {}",
                team, initiatorName, commanderName, replacementName);
        return true;
    }

    // ---- 投票 ----

    public VoteResult vote(String team, UUID voterUuid, String voterName, boolean agree) {
        Impeachment imp = activeImpeachments.get(team);
        if (imp == null || imp.finished) return VoteResult.NOT_ACTIVE;

        // 指挥官本人不能投票
        if (voterName.equals(imp.commanderName)) return VoteResult.NOT_IN_TEAM;

        if (imp.votes.containsKey(voterUuid)) return VoteResult.ALREADY_VOTED;
        imp.votes.put(voterUuid, agree);

        BattleLineSystem.LOGGER.info("[弹劾] {}队 {} 投票: {}",
                team, voterName, agree ? "同意" : "反对");
        return VoteResult.OK;
    }

    // ---- tick ----

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastTick < 1000) return;
        lastTick = now;

        List<String> finished = new ArrayList<>();
        for (var entry : activeImpeachments.entrySet()) {
            Impeachment imp = entry.getValue();
            if (imp.finished) continue;
            imp.remaining--;
            if (imp.remaining <= 0) {
                imp.remaining = 0;
                imp.finished = true;
                finished.add(entry.getKey());
            }
        }

        for (String team : finished) {
            finishImpeachment(team, server);
        }
    }

    public void reset() {
        activeImpeachments.clear();
        cooldowns.clear();
        lastTick = 0;
    }

    // ---- 内部 ----

    private void finishImpeachment(String team, MinecraftServer server) {
        Impeachment imp = activeImpeachments.get(team);
        if (imp == null) return;

        int agree = 0, disagree = 0;
        for (boolean v : imp.votes.values()) {
            if (v) agree++; else disagree++;
        }

        CommanderVoteManager cvm = CommanderVoteManager.getInstance();

        if (agree > disagree && agree > 0) {
            // 弹劾成功
            cvm.setCommander(team, imp.replacementName);
            cooldowns.put(team, System.currentTimeMillis() + COOLDOWN_MS);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt)) {
                    sp.sendSystemMessage(Component.literal(
                            "§6[弹劾] §a弹劾成功！§f" + imp.commanderName
                                    + " §7被弹劾，§b" + imp.replacementName
                                    + " §7接任指挥官！(同意 " + agree + " 反对 " + disagree + ") §7弹劾冷却中，3分钟内不可再次发起"));
                }
            }
            BattleLineSystem.LOGGER.info("[弹劾] {}队弹劾成功: {} → {} (同意{} 反对{})",
                    team, imp.commanderName, imp.replacementName, agree, disagree);
        } else {
            // 弹劾失败
            cooldowns.put(team, System.currentTimeMillis() + COOLDOWN_MS);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                String pt = CapturePointManager.getInstance().getPlayerTeam(sp.getUUID());
                if (team.equals(pt)) {
                    sp.sendSystemMessage(Component.literal(
                            "§6[弹劾] §c弹劾失败！§f" + imp.commanderName
                                    + " §7继续担任指挥官。(同意 " + agree + " 反对 " + disagree + ") §7弹劾冷却中，3分钟内不可再次发起"));
                }
            }
            BattleLineSystem.LOGGER.info("[弹劾] {}队弹劾失败: {} (同意{} 反对{})",
                    team, imp.commanderName, agree, disagree);
        }
    }

    // ---- 数据结构 ----

    public enum VoteResult { OK, NOT_ACTIVE, NOT_IN_TEAM, ALREADY_VOTED }

    private static class Impeachment {
        final String team;
        final UUID initiatorUuid;
        final String initiatorName;
        final String commanderName;
        final UUID replacementUuid;
        final String replacementName;
        int remaining;
        final Map<UUID, Boolean> votes = new LinkedHashMap<>();
        boolean finished;

        Impeachment(String team, UUID initiatorUuid, String initiatorName,
                    String commanderName, UUID replacementUuid, String replacementName, int remaining) {
            this.team = team;
            this.initiatorUuid = initiatorUuid;
            this.initiatorName = initiatorName;
            this.commanderName = commanderName;
            this.replacementUuid = replacementUuid;
            this.replacementName = replacementName;
            this.remaining = remaining;
        }
    }
}

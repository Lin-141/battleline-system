package com.battlelinesystem.game;

import com.battlelinesystem.BattleLineSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 模式选择倒计时管理器（服务端单例）
 * 当首次有人选择模式后启动10秒倒计时，
 * 结束时分发胜出模式通知给所有玩家。
 */
public class ModeCountdownManager {

    private static final ModeCountdownManager INSTANCE = new ModeCountdownManager();

    /** 倒计时总秒数 */
    public static final int COUNTDOWN_SECONDS = 10;

    /** 倒计时剩余秒数，-1 表示未启动 */
    private int remainingSeconds = -1;

    /** 最后一次 tick 时是否已结算（防止重复结算） */
    private boolean finished = false;

    private ModeCountdownManager() {}

    public static ModeCountdownManager getInstance() {
        return INSTANCE;
    }

    /**
     * 启动倒计时（首次有人选择模式时调用）
     */
    public void startIfNeeded() {
        if (remainingSeconds < 0) {
            remainingSeconds = COUNTDOWN_SECONDS;
            finished = false;
            BattleLineSystem.LOGGER.info("模式倒计时已启动：{}秒", COUNTDOWN_SECONDS);
        }
    }

    /**
     * 获取当前剩余秒数
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * 倒计时是否已结束
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 重置倒计时状态
     */
    public void reset() {
        remainingSeconds = -1;
        finished = false;
    }

    /**
     * 强制结束倒计时（全票通过时调用）
     * 设置 finished=true，跳过剩余秒数
     */
    public void forceFinish() {
        if (remainingSeconds > 0) {
            BattleLineSystem.LOGGER.info("全票通过，跳过剩余 {} 秒", remainingSeconds);
        }
        remainingSeconds = 0;
        finished = true;
    }

    /**
     * 服务端 tick 调用（每秒一次）
     * 返回 true 表示刚刚到期需要结算
     */
    public boolean tick() {
        if (remainingSeconds < 0 || finished) return false;

        remainingSeconds--;
        if (remainingSeconds <= 0) {
            remainingSeconds = 0;
            finished = true;

            // 算出得票最多的模式
            GameModeManager gmm = GameModeManager.getInstance();
            String winner = gmm.getWinningMode();

            BattleLineSystem.LOGGER.info("倒计时结束，胜出模式：{}", winner);
            return true;
        }
        return false;
    }
}

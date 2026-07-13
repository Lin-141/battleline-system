package com.battlelinesystem.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用黑屏淡入淡出工具。
 * 使用时：调用 start(...) 启动，每帧 tick()，渲染时调用 render(...) 画覆盖层。
 */
public class ScreenFadeUtil {

    public static final int STATE_IDLE = 0;
    public static final int STATE_FADE_IN = 1;
    public static final int STATE_HOLD = 2;
    public static final int STATE_FADE_OUT = 3;

    private int state = STATE_IDLE;
    private float alpha;
    private int tick;

    private int fadeInTicks = 20;
    private int holdTicks = 10;
    private int fadeOutTicks = 10;

    private Runnable onFadeInDone;
    private Runnable onFadeOutDone;

    /**
     * 启动淡入淡出序列。
     *
     * @param fadeInTicks  淡入到全黑的 tick 数（20 = 1秒）
     * @param holdTicks    全黑保持的 tick 数
     * @param fadeOutTicks 淡出消失的 tick 数
     * @param onFadeInDone 全黑后回调（可 null）
     * @param onFadeOutDone 淡出结束后回调（可 null）
     */
    public void start(int fadeInTicks, int holdTicks, int fadeOutTicks,
                      Runnable onFadeInDone, Runnable onFadeOutDone) {
        this.fadeInTicks = fadeInTicks > 0 ? fadeInTicks : 20;
        this.holdTicks = holdTicks > 0 ? holdTicks : 10;
        this.fadeOutTicks = fadeOutTicks > 0 ? fadeOutTicks : 10;
        this.onFadeInDone = onFadeInDone;
        this.onFadeOutDone = onFadeOutDone;
        this.state = STATE_FADE_IN;
        this.tick = 0;
        this.alpha = 0f;
    }

    /** 每帧调用。如果未在渐变中则什么也不做。 */
    public void tick() {
        switch (state) {
            case STATE_IDLE:
                return;

            case STATE_FADE_IN:
                tick++;
                alpha = Math.min(1f, (float) tick / fadeInTicks);
                if (tick >= fadeInTicks) {
                    alpha = 1f;
                    state = STATE_HOLD;
                    tick = 0;
                    if (onFadeInDone != null) {
                        onFadeInDone.run();
                    }
                }
                break;

            case STATE_HOLD:
                tick++;
                if (tick >= holdTicks) {
                    state = STATE_FADE_OUT;
                    tick = 0;
                }
                break;

            case STATE_FADE_OUT:
                tick++;
                alpha = Math.max(0f, 1f - (float) tick / fadeOutTicks);
                if (tick >= fadeOutTicks) {
                    alpha = 0f;
                    state = STATE_IDLE;
                    if (onFadeOutDone != null) {
                        onFadeOutDone.run();
                    }
                }
                break;
        }
    }

    /** 渲染黑色覆盖层。 */
    public void render(GuiGraphics g, int screenW, int screenH) {
        if (state == STATE_IDLE || alpha <= 0f) return;
        int a = (int) (alpha * 255f);
        if (a <= 0) return;
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, screenW, screenH, (a << 24));
    }

    /** 是否正在渐变中。 */
    public boolean isFading() {
        return state != STATE_IDLE;
    }

    /** 当前透明度（0~1）。 */
    public float getAlpha() {
        return alpha;
    }

    /** 当前状态。 */
    public int getState() {
        return state;
    }

    /** 强制重置到空闲。 */
    public void reset() {
        state = STATE_IDLE;
        alpha = 0f;
        tick = 0;
        onFadeInDone = null;
        onFadeOutDone = null;
    }
}

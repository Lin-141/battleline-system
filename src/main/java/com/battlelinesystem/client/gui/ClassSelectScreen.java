package com.battlelinesystem.client.gui;

import com.battlelinesystem.faction.ClassConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 职业选择时的透明壳 Screen — 只用来显示鼠标光标，渲染和点击全委托给 WaitHudOverlay
 */
public class ClassSelectScreen extends Screen {

    public ClassSelectScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() { super.init(); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (WaitHudOverlay.handleClick(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.player != null) {
            if (this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
                this.minecraft.setScreen(createChatScreen(""));
                return true;
            }
            if (this.minecraft.options.keyCommand.matches(keyCode, scanCode)) {
                this.minecraft.setScreen(createChatScreen("/"));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 创建 ChatScreen：关闭时（无论是按 Esc 还是回车）都回到 ClassSelectScreen
     */
    private ChatScreen createChatScreen(String defaultText) {
        return new ChatScreen(defaultText) {
            private boolean restored;

            @Override
            public void removed() {
                if (!restored && this.minecraft != null) {
                    restored = true;
                    // 延迟到下一 tick，否则在 Minecraft.setScreen(null) 调用栈中会被覆盖
                    this.minecraft.tell(() -> {
                        if (this.minecraft.screen == null) {
                            this.minecraft.setScreen(ClassSelectScreen.this);
                        }
                    });
                }
            }
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 只在淡出完成后关闭 Screen
        if (WaitHudOverlay.closeScreen) {
            WaitHudOverlay.closeScreen = false;
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        // 淡入淡出期间：只渲染黑屏覆盖，不渲染 Screen 背景（避免 fillGradient 污染 blend 状态）
        if (WaitHudOverlay.isFading()) {
            WaitHudOverlay.renderFadeOverlay(g, this.width, this.height);
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        WaitHudOverlay.render(g, this.width, this.height);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

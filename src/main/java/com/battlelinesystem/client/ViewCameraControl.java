package com.battlelinesystem.client;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.packet.PacketViewMove;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * RTS风格视角控制器（客户端）
 * 按住中键拖动来移动旁观者视角
 */
public class ViewCameraControl {

    private static final double DRAG_SENSITIVITY = 0.3;

    private static boolean enabled = false;
    private static double lastMouseX, lastMouseY;
    private static boolean wasMiddleDown = false;

    public static void enable() { enabled = true; }
    public static void disable() { enabled = false; }
    public static boolean isEnabled() { return enabled; }

    /**
     * 每客户端 tick 调用（约20次/秒）
     */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (enabled) disable();
            return;
        }

        boolean inGameDim = mc.level.dimension().location().getNamespace()
                .equals(BattleLineSystem.MOD_ID);
        boolean isSpectator = mc.player.isSpectator();
        boolean isHighUp = mc.player.getY() > 300;

        if (inGameDim && isSpectator && isHighUp) {
            enabled = true;
        } else {
            if (enabled) disable();
            return;
        }

        long window = mc.getWindow().getWindow();
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);

        boolean middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleDown && wasMiddleDown && (mx[0] != lastMouseX || my[0] != lastMouseY)) {
            double px = (lastMouseX - mx[0]) * DRAG_SENSITIVITY;
            double py = (lastMouseY - my[0]) * DRAG_SENSITIVITY;
            AllPackets.getChannel().sendToServer(new PacketViewMove(px, py));
        }
        lastMouseX = mx[0];
        lastMouseY = my[0];
        wasMiddleDown = middleDown;
    }
}

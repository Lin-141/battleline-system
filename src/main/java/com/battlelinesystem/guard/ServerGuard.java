package com.battlelinesystem.guard;

import com.battlelinesystem.BattleLineSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 服务端防护类。仅在专用服务端注册并抛出异常，防止客户端 JAR 被用于开服。
 * 构建时 serverJar 排除此包，clientJar 包含此包。
 * <p>
 * 原理：@Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER) 使得
 * 客户端环境不注册此监听器，专用服务端注册后在 setup 阶段直接抛异常。
 */
@Mod.EventBusSubscriber(modid = BattleLineSystem.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public class ServerGuard {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        throw new RuntimeException(
                "\n\n========================================\n"
                        + "  此 JAR 为客户端版本，不可用于专用服务端！\n"
                        + "  请联系 3314660537\n"
                        + "========================================\n");
    }
}

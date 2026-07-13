package com.battlelinesystem.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * 暴露 MinecraftServer 的世界创建/删除能力
 */
public interface MinecraftServerAccessor {
    boolean bls$createLevel(ResourceKey<Level> key, LevelStem stem);

    void bls$removeWorld(ResourceKey<Level> key);
}

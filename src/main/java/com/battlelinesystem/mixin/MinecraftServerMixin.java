package com.battlelinesystem.mixin;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.accessor.MinecraftServerAccessor;
import com.battlelinesystem.world.GameWorldManager;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends BlockableEventLoop<TickTask> implements MinecraftServerAccessor {

    @Shadow(remap = false, aliases = {"levels"})
    @Final
    private Map<ResourceKey<Level>, ServerLevel> f_129762_;

    @Shadow(remap = false, aliases = {"executor"})
    @Final
    private Executor f_129738_;

    @Shadow(remap = false, aliases = {"storageSource"})
    @Final
    protected LevelStorageSource.LevelStorageAccess f_129744_;

    @Shadow(remap = false, aliases = {"worldData"})
    @Final
    protected WorldData f_129749_;

    @Shadow(remap = false)
    public abstract void markWorldsDirty();

    protected MinecraftServerMixin(String name) {
        super(name);
    }

    private MinecraftServer self() {
        return (MinecraftServer) (Object) this;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onServerStart(CallbackInfo ci) {
        BattleLineSystem.setServer(self());
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onServerStop(CallbackInfo ci) {
        GameWorldManager.cleanupAll(self());
        BattleLineSystem.setServer(null);
    }

    @Override
    public boolean bls$createLevel(ResourceKey<Level> key, LevelStem stem) {
        try {
            boolean debugWorld = this.f_129749_.isDebugWorld();
            long seed = BiomeManager.obfuscateSeed(this.f_129749_.worldGenOptions().seed());
            DerivedLevelData derivedData = new DerivedLevelData(this.f_129749_, this.f_129749_.overworldData());

            ServerLevel world = new ServerLevel(
                    self(), f_129738_, f_129744_,
                    derivedData,
                    key, stem,
                    new StoringChunkProgressListener(16),
                    debugWorld, seed,
                    ImmutableList.of(), false, null
            );

            this.f_129762_.put(key, world);
            this.markWorldsDirty();
            return true;
        } catch (Exception e) {
            BattleLineSystem.LOGGER.error("Failed to create level: {}", key.location(), e);
            return false;
        }
    }

    @Override
    public void bls$removeWorld(ResourceKey<Level> key) {
        ServerLevel removed = this.f_129762_.remove(key);
        if (removed != null) {
            this.markWorldsDirty();
        }
    }
}

package com.battlelinesystem.sound;

import com.battlelinesystem.BattleLineSystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BattleLineSystem.MOD_ID);

    public static final RegistryObject<SoundEvent> START_01 = SOUND_EVENTS.register("start_01",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "start_01")));

    public static final RegistryObject<SoundEvent> WAITING_01 = SOUND_EVENTS.register("waiting_01",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "waiting_01")));

    public static final RegistryObject<SoundEvent> ENDING_01 = SOUND_EVENTS.register("ending_01",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "ending_01")));

    public static final RegistryObject<SoundEvent> POINTWAITING = SOUND_EVENTS.register("pointwaiting",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "pointwaiting")));

    public static final RegistryObject<SoundEvent> USAIR = SOUND_EVENTS.register("usair",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "usair")));
    public static final RegistryObject<SoundEvent> USLAND = SOUND_EVENTS.register("usland",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "usland")));
    public static final RegistryObject<SoundEvent> RULAND = SOUND_EVENTS.register("ruland",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "ruland")));
    public static final RegistryObject<SoundEvent> RUAIR = SOUND_EVENTS.register("ruair",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "ruair")));
    public static final RegistryObject<SoundEvent> CNAIR = SOUND_EVENTS.register("cnair",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "cnair")));
    public static final RegistryObject<SoundEvent> CNLAND = SOUND_EVENTS.register("cnland",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "cnland")));

    public static final RegistryObject<SoundEvent> CNTAKE = SOUND_EVENTS.register("cntake",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "cntake")));
    public static final RegistryObject<SoundEvent> CNLOSE = SOUND_EVENTS.register("cnlose",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "cnlose")));
    public static final RegistryObject<SoundEvent> RUTAKE = SOUND_EVENTS.register("rutake",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "rutake")));
    public static final RegistryObject<SoundEvent> RULOSE = SOUND_EVENTS.register("rulose",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "rulose")));
    public static final RegistryObject<SoundEvent> USTAKE = SOUND_EVENTS.register("ustake",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "ustake")));
    public static final RegistryObject<SoundEvent> USLOSE = SOUND_EVENTS.register("uslose",
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(BattleLineSystem.MOD_ID, "uslose")));

    public static void init(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}

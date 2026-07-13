package com.battlelinesystem.items;

import com.battlelinesystem.BattleLineSystem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = BattleLineSystem.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BattleLineSystem.MOD_ID);

    public static final RegistryObject<Item> SELECTION_WAND = ITEMS.register("selection_wand",
            SelectionWandItem::new);

    public static final RegistryObject<Item> BEACON_WAND = ITEMS.register("beacon_wand",
            BeaconWandItem::new);

    public static final RegistryObject<Item> POLYGON_WAND = ITEMS.register("polygon_wand",
            PolygonWandItem::new);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(SELECTION_WAND.get());
            event.accept(BEACON_WAND.get());
            event.accept(POLYGON_WAND.get());
        }
    }
}

package com.avilixradiomod.registry;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.item.RemoteItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AvilixRadioMod.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> RADIO = ITEMS.registerSimpleBlockItem(ModBlocks.RADIO);
    public static final DeferredHolder<Item, BlockItem> SPEAKER = ITEMS.registerSimpleBlockItem(ModBlocks.SPEAKER);

    public static final DeferredHolder<Item, Item> REMOTE = ITEMS.registerItem(
            "remote",
            RemoteItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

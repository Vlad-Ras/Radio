package com.avilixradiomod.registry;

import com.avilixradiomod.AvilixRadioMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AvilixRadioMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AVILIX_TAB =
            TABS.register("avilix_radio", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.avilixradiomod"))
                    .icon(() -> new ItemStack(ModItems.REMOTE.get()))
                    .build()
            );

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        modBus.addListener(ModCreativeTabs::buildContents);
    }

    private static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != AVILIX_TAB.getKey()) return;

        // ✅ блоки (добавляем item-форму блока)
        event.accept(ModBlocks.RADIO);
        event.accept(ModBlocks.SPEAKER);

        // ✅ предметы
        event.accept((ItemLike) ModItems.REMOTE);
    }
}

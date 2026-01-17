package com.avilixradiomod;

import com.avilixradiomod.client.ClientInit;
import com.avilixradiomod.config.ModConfigs;
import com.avilixradiomod.network.ModPayloads;
import com.avilixradiomod.registry.*;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(AvilixRadioMod.MOD_ID)
public final class AvilixRadioMod {
    public static final String MOD_ID = "avilixradiomod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AvilixRadioMod(IEventBus modBus, Dist dist, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        ModConfigs.register(container);

        modBus.addListener(com.avilixradiomod.network.ModPayloads::register);
        if (dist.isClient()) {
            ClientInit.init(modBus);
        }
    }

    private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.RADIO);
            event.accept(ModBlocks.SPEAKER);
            event.accept(ModItems.REMOTE.get());
        }
    }
}

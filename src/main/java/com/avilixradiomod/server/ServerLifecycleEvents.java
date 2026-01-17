package com.avilixradiomod.server;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.server.db.RadioDatabase;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = AvilixRadioMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        RadioDatabase.init();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RadioDatabase.shutdown();
    }
}

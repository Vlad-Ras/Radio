package com.avilixradiomod.client;

import com.avilixradiomod.client.audio.RadioAudioController;
import com.avilixradiomod.client.ModSoundOptionsHook;
import com.avilixradiomod.client.screen.RadioScreen;
import com.avilixradiomod.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modBus) {
        // Ensure client config values edited via in-game slider are loaded from disk.
        ClientConfigPersistence.loadGlobalVolumeIntoConfig();

        // ✅ Это модовый bus — тут ок
        modBus.addListener(ClientInit::registerScreens);

        // ✅ А это game bus — тик только тут
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);

        // ✅ Добавляем ползунок громкости мода прямо в меню звука Minecraft
        NeoForge.EVENT_BUS.addListener(ModSoundOptionsHook::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ModSoundOptionsHook::onScreenRender);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RADIO_MENU.get(), RadioScreen::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        RadioAudioController.clientTick();
    }
}

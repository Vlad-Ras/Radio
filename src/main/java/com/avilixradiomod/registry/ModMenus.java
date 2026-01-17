package com.avilixradiomod.registry;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.menu.RadioMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, AvilixRadioMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RadioMenu>> RADIO_MENU = MENU_TYPES.register(
            "radio",
            () -> IMenuTypeExtension.create(RadioMenu::new)
    );

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}

package com.avilixradiomod.registry;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AvilixRadioMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioBlockEntity>> RADIO = BLOCK_ENTITY_TYPES.register(
            "radio",
            () -> BlockEntityType.Builder.of(RadioBlockEntity::new, ModBlocks.RADIO.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpeakerBlockEntity>> SPEAKER = BLOCK_ENTITY_TYPES.register(
            "speaker",
            () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, ModBlocks.SPEAKER.get()).build(null)
    );

    public static void register(IEventBus modBus) {
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}

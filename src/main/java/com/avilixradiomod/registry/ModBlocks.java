package com.avilixradiomod.registry;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.block.RadioBlock;
import com.avilixradiomod.block.SpeakerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AvilixRadioMod.MOD_ID);

    public static final DeferredBlock<Block> RADIO = BLOCKS.register(
            "radio",
            () -> new RadioBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    // ❌ requiresCorrectToolForDrops УБРАТЬ
            )
    );

    public static final DeferredBlock<Block> SPEAKER = BLOCKS.register(
            "speaker",
            () -> new SpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    // ❌ requiresCorrectToolForDrops УБРАТЬ
            )
    );

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}

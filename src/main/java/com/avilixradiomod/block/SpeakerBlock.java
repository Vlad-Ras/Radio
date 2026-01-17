package com.avilixradiomod.block;

import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import com.avilixradiomod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;


public class SpeakerBlock extends BaseEntityBlock {

    // ✅ обязательно для 1.21+
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    // ✅ направление
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // ✅ играет/не играет (для выбора модели/анимации)
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    public SpeakerBlock(Properties properties) {
        super(properties);
        // ✅ обязательно задать значения ДЛЯ ВСЕХ свойств (FACING + PLAYING)
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(PLAYING, false)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    // ✅ добавляем свойства в blockstate (ОДИН раз!)
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    // ✅ поворот при установке
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(PLAYING, false);
    }

    // ✅ серверный тикер — обновляет PLAYING по радио
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;

        return type == ModBlockEntities.SPEAKER.get()
                ? (lvl, pos, st, be) -> SpeakerBlockEntity.serverTick(lvl, pos, st, (SpeakerBlockEntity) be)
                : null;
    }

    // ----------------------------------------------------------------------
    // ✅ Частицы
    // ----------------------------------------------------------------------

    @Override
    public void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            level.addDestroyBlockEffect(pos, state);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance);

        if (!level.isClientSide) return;
        if (fallDistance < 1.5f) return;

        RandomSource random = level.random;
        int count = Math.min(10, 5 + (int) (fallDistance * 2.5f));

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.01;
            double z = pos.getZ() + random.nextDouble();

            double mx = (random.nextDouble() - 0.5) * 0.14;
            double my = 0.08 + random.nextDouble() * 0.08;
            double mz = (random.nextDouble() - 0.5) * 0.14;

            level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    x, y, z,
                    mx, my, mz
            );
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        ItemStack tool = player.getMainHandItem();

        // ✅ если в руке ТОПОР
        if (tool.getItem() instanceof AxeItem) {
            return super.getDestroyProgress(state, player, level, pos) * 4.0F;
        }

        // рука / другие инструменты
        return super.getDestroyProgress(state, player, level, pos);
    }


    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(this.asItem()));
        }
    }

}

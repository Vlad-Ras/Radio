package com.avilixradiomod.block;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.server.data.RadioWorldState;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RadioBlock extends BaseEntityBlock {

    // ✅ обязательно для Minecraft 1.21+
    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);

    // ✅ направление (север/юг/восток/запад)
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public RadioBlock(Properties properties) {
        super(properties);
        // ✅ дефолтное состояние
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    // ✅ обязательно для Minecraft 1.21+
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // ✅ регистрируем свойство FACING
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // ✅ при установке блок поворачивается лицом к игроку
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ✅ Меню
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadioBlockEntity radio) {
                serverPlayer.openMenu(radio, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBlockEntity(pos, state);
    }

    // ----------------------------------------------------------------------
    // ✅ Частицы
    // ----------------------------------------------------------------------

    /**
     * Частицы "осколков" при разрушении — ванильный эффект, но с твоей текстурой.
     */
    @Override
    public void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            level.addDestroyBlockEffect(pos, state);
        }
    }


    /**
     * Всплеск частиц при падении на блок (именно "упал сверху").
     */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance);

        if (!level.isClientSide) return;
        if (fallDistance < 1.5f) return;

        RandomSource random = level.random;
        int count = Math.min(10, 6 + (int) (fallDistance * 3.0f));

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.01;
            double z = pos.getZ() + random.nextDouble();

            double mx = (random.nextDouble() - 0.5) * 0.15;
            double my = 0.08 + random.nextDouble() * 0.08;
            double mz = (random.nextDouble() - 0.5) * 0.15;

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

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                RadioWorldState.get(sl).remove(pos);
            }
        }
    }

}

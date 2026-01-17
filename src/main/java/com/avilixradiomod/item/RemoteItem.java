package com.avilixradiomod.item;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Remote item:
 *  - Right click a Radio to store its position
 *  - Right click a Speaker to link it to the stored radio
 *  - Shift-right click a Speaker to clear speaker link
 *  - Shift-right click in the air to clear stored link in the remote
 */
public final class RemoteItem extends Item {
    private static final String TAG_REMOTE = "Remote";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_DIM = "Dim";

    public RemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        final Level level = ctx.getLevel();
        final Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        final BlockPos pos = ctx.getClickedPos();
        final ItemStack stack = ctx.getItemInHand();
        final BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof RadioBlockEntity) {
            if (!level.isClientSide) {
                storeLink(stack, pos, level.dimension().location());
                player.displayClientMessage(Component.translatable("message.avilixradiomod.remote_saved"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (be instanceof SpeakerBlockEntity speaker) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    speaker.setRadioLink(null, null);
                    player.displayClientMessage(Component.translatable("message.avilixradiomod.speaker_cleared"), true);
                } else {
                    final Link link = readLink(stack);
                    if (link == null) {
                        player.displayClientMessage(Component.translatable("message.avilixradiomod.remote_empty"), true);
                    } else {
                        speaker.setRadioLink(link.pos, link.dim);
                        player.displayClientMessage(Component.translatable("message.avilixradiomod.speaker_linked"), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown()) {
            clearLink(stack);
            player.displayClientMessage(Component.translatable("message.avilixradiomod.remote_cleared"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    // --------- 1.21+ CustomData helpers ---------

    private static CompoundTag getRoot(ItemStack stack) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        // copyTag() даёт изменяемый CompoundTag
        return cd.copyTag();
    }

    private static void setRoot(ItemStack stack, CompoundTag root) {
        // Устанавливаем обратно как компонент
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void storeLink(ItemStack stack, BlockPos pos, ResourceLocation dim) {
        CompoundTag root = getRoot(stack);

        CompoundTag t = new CompoundTag();
        t.putInt(TAG_X, pos.getX());
        t.putInt(TAG_Y, pos.getY());
        t.putInt(TAG_Z, pos.getZ());
        t.putString(TAG_DIM, dim.toString());

        root.put(TAG_REMOTE, t);
        setRoot(stack, root);
    }

    private static void clearLink(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        if (root.contains(TAG_REMOTE)) {
            root.remove(TAG_REMOTE);
            setRoot(stack, root);
        }
    }

    @Nullable
    private static Link readLink(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        if (!root.contains(TAG_REMOTE)) return null;

        CompoundTag t = root.getCompound(TAG_REMOTE);
        BlockPos pos = new BlockPos(t.getInt(TAG_X), t.getInt(TAG_Y), t.getInt(TAG_Z));

        String dimStr = t.getString(TAG_DIM);
        ResourceLocation dim = dimStr.isEmpty() ? null : ResourceLocation.tryParse(dimStr);
        if (dim == null) return null;

        return new Link(pos, dim);
    }

    private record Link(BlockPos pos, ResourceLocation dim) {}
}

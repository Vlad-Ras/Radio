package com.avilixradiomod.blockentity;

import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.registry.ModBlockEntities;
import com.avilixradiomod.config.ModConfigs;
import com.avilixradiomod.server.data.RadioWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RadioBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_URL = "Url";
    private static final String TAG_PLAYING = "Playing";
    private static final String TAG_VOLUME = "Volume";
    private static final String TAG_INIT = "Init";

    private String url = "";
    private boolean playing = false;
    private int volume = 100;
    private boolean init = false;

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && !level.isClientSide) {
            // Apply server-configured defaults once for newly placed radios.
            if (!init) {
                String defUrl = ModConfigs.COMMON.defaultStreamUrl.get();
                if (defUrl == null) defUrl = "";
                defUrl = defUrl.trim();

                if (!defUrl.isEmpty() && isValidStreamUrl(defUrl) && (url == null || url.isBlank())) {
                    url = defUrl;
                }

                volume = Math.max(0, Math.min(100, ModConfigs.COMMON.defaultVolume.get()));
                playing = false;
                init = true;
                setChanged();
            }

            // Publish state to world SavedData so speakers can work even if this chunk unloads.
            RadioWorldState.get((net.minecraft.server.level.ServerLevel) level).update(worldPosition, url, playing, volume);
        }
    }

    public String getUrl() {
        return url;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getVolume() {
        return volume;
    }

    /**
     * Client-only preview (GUI responsiveness)
     */
    public void setClientSidePreview(String url, boolean playing, int volume) {
        if (this.level == null || !this.level.isClientSide) return;

        if (url == null) url = "";
        url = url.trim(); // ✅ УБИРАЕМ ПРОБЕЛЫ

        this.url = url;
        this.playing = playing;
        this.volume = Math.max(0, Math.min(100, volume));
        this.setChanged();
    }

    /**
     * Server-authoritative settings
     */
    public void setSettings(String url, boolean playing, int volume) {
        if (url == null) url = "";
        url = url.trim();

        if (playing && !isValidStreamUrl(url)) {
            playing = false; // ✅ сервер принудительно глушит
        }

        this.url = url;
        this.playing = playing;
        this.volume = Math.max(0, Math.min(100, volume));

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            if (!level.isClientSide) {
                RadioWorldState.get((net.minecraft.server.level.ServerLevel) level)
                        .update(worldPosition, this.url, this.playing, this.volume);
            }
        }
    }



    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.avilixradiomod.radio");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RadioMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_URL, url);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putInt(TAG_VOLUME, volume);
        tag.putBoolean(TAG_INIT, init);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        url = tag.getString(TAG_URL);
        playing = tag.getBoolean(TAG_PLAYING);
        volume = tag.contains(TAG_VOLUME) ? tag.getInt(TAG_VOLUME) : 100;
        init = tag.getBoolean(TAG_INIT);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean isValidStreamUrl(String url) {
        if (url == null) return false;
        url = url.trim();
        if (url.isEmpty()) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }
}

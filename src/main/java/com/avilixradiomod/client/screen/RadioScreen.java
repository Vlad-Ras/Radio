package com.avilixradiomod.client.screen;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.client.UrlHistory;
import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.network.ModPayloads;
import com.avilixradiomod.network.RadioSettingsPayload;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.appliedenergistics.yoga.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal modern radio GUI built with LDLib2 (LDLib2 2.x API).
 */
public final class RadioScreen extends ModularUIContainerScreen<RadioMenu> {

    private static final int UI_W = 320;
    private static final int UI_H = 188;

    private static final ResourceLocation PANEL_TEX = ResourceLocation.fromNamespaceAndPath(
            AvilixRadioMod.MOD_ID,
            "textures/gui/radio_panel.png"
    );

    private TextField urlField;
    private Label statusLabel;
    private Label volumeLabel;
    private Selector<String> historySelector;
    private Button playStopButton;

    private Button saveButton;
    private Button clearHistoryButton;
    private Button volMinusButton;
    private Button volPlusButton;

    private static final class UiRefs {
        TextField urlField;
        Label statusLabel;
        Label volumeLabel;
        Selector<String> historySelector;
        Button playStopButton;
        Button saveButton;
        Button clearHistoryButton;
        Button volMinusButton;
        Button volPlusButton;
    }

    private record Build(ModularUI ui, UiRefs refs) {}

    public RadioScreen(RadioMenu menu, Inventory inv, Component title) {
        this(buildUI(), menu, inv, title);
    }

    private RadioScreen(Build build, RadioMenu menu, Inventory inv, Component title) {
        super(build.ui, menu, inv, title);

        // Pull refs from the built UI tree.
        UiRefs r = build.refs;
        this.urlField = r.urlField;
        this.statusLabel = r.statusLabel;
        this.volumeLabel = r.volumeLabel;
        this.historySelector = r.historySelector;
        this.playStopButton = r.playStopButton;
        this.saveButton = r.saveButton;
        this.clearHistoryButton = r.clearHistoryButton;
        this.volMinusButton = r.volMinusButton;
        this.volPlusButton = r.volPlusButton;

        // Wire callbacks here (we have 'this.menu' available).
        this.playStopButton.setOnClick(e -> {
            boolean nowPlaying = !this.menu.isPlaying();
            apply(nowPlaying, this.menu.getVolume());
        });
        this.saveButton.setOnClick(e -> apply(this.menu.isPlaying(), this.menu.getVolume()));
        this.volMinusButton.setOnClick(e -> setVolume(this.menu.getVolume() - 5));
        this.volPlusButton.setOnClick(e -> setVolume(this.menu.getVolume() + 5));
        this.clearHistoryButton.setOnClick(e -> {
            UrlHistory.clear();
            refreshHistory();
        });
        this.historySelector.registerValueListener(v -> {
            if (v != null && !v.isBlank()) {
                this.urlField.setText(v);
            }
        });
    }

    private static Build buildUI() {
        UiRefs refs = new UiRefs();

        UIElement root = new UIElement()
                .layout(layout -> {
                    layout.setWidth(UI_W);
                    layout.setHeight(UI_H);
                    layout.setFlexDirection(YogaFlexDirection.COLUMN);
                    layout.setPadding(YogaEdge.ALL, 10);
                    layout.setGap(YogaGutter.ALL, 8);
                });

        // Header row
        UIElement header = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                    layout.setGap(YogaGutter.COLUMN, 6);
                });

        Label title = new Label();
        title.setText(Component.translatable("screen.avilixradiomod.radio"));
        title.textStyle(s -> s.fontSize(12).adaptiveWidth(true).textShadow(true));

        refs.statusLabel = new Label();
        refs.statusLabel.setText(Component.empty());
        refs.statusLabel.textStyle(s -> s.fontSize(9).textColor(0xFFB0B0B0).adaptiveWidth(true));

        header.addChildren(title, refs.statusLabel);
        root.addChild(header);

        // URL label
        Label urlLbl = new Label();
        urlLbl.setText(Component.translatable("screen.avilixradiomod.url"));
        urlLbl.textStyle(s -> s.fontSize(9).textColor(0xFFB0B0B0).adaptiveWidth(true));
        root.addChild(urlLbl);

        // URL field
        // LDLib2's fluent helpers (layout/style/...) return UIElement, not the concrete subtype.
        // So we configure elements in separate statements to keep strong types.
        refs.urlField = new TextField();
        refs.urlField.layout(layout -> layout.setWidthPercent(100));
        refs.urlField.style(style -> style.backgroundTexture(Sprites.RECT_RD_SOLID));
        refs.urlField.setPlaceholder(Component.translatable("screen.avilixradiomod.url_placeholder").getString());
        refs.urlField.setTextValidator(s -> s != null && s.length() <= 256);
        root.addChild(refs.urlField);

        // Controls row (Play/Stop + Save)
        UIElement controls = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setGap(YogaGutter.COLUMN, 6);
                });

        refs.playStopButton = new Button();
        refs.playStopButton.layout(layout -> layout.setFlex(1));
        refs.saveButton = new Button();
        refs.saveButton.layout(layout -> layout.setFlex(1));
        controls.addChildren(refs.playStopButton, refs.saveButton);
        root.addChild(controls);

        // Volume row
        UIElement volumeRow = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                });
        Label volTitle = new Label();
        volTitle.setText(Component.translatable("screen.avilixradiomod.volume"));
        volTitle.textStyle(s -> s.fontSize(9).textColor(0xFFB0B0B0).adaptiveWidth(true));

        UIElement volControls = new UIElement()
                .layout(layout -> {
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setGap(YogaGutter.COLUMN, 6);
                });

        refs.volMinusButton = new Button();
        refs.volMinusButton.setText(Component.literal("-"));
        refs.volMinusButton.layout(layout -> layout.setWidth(22));

        refs.volumeLabel = new Label();
        refs.volumeLabel.setText(Component.literal("100%"));
        refs.volumeLabel.textStyle(s -> s.fontSize(9).adaptiveWidth(true).textColor(0xFFE6E6E6));

        refs.volPlusButton = new Button();
        refs.volPlusButton.setText(Component.literal("+"));
        refs.volPlusButton.layout(layout -> layout.setWidth(22));

        volControls.addChildren(refs.volMinusButton, refs.volumeLabel, refs.volPlusButton);
        volumeRow.addChildren(volTitle, volControls);
        root.addChild(volumeRow);

        // History header (label + clear)
        UIElement historyHeader = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                });
        Label histTitle = new Label();
        histTitle.setText(Component.translatable("screen.avilixradiomod.history"));
        histTitle.textStyle(s -> s.fontSize(9).textColor(0xFFB0B0B0).adaptiveWidth(true));

        refs.clearHistoryButton = new Button();
        refs.clearHistoryButton.setText(Component.literal("X"));
        refs.clearHistoryButton.layout(layout -> layout.setWidth(20));

        historyHeader.addChildren(histTitle, refs.clearHistoryButton);
        root.addChild(historyHeader);

        // History dropdown
        refs.historySelector = new Selector<>();
        refs.historySelector.layout(layout -> layout.setWidthPercent(100));
        refs.historySelector.setCandidates(List.of(""));
        root.addChild(refs.historySelector);

        UI ui = UI.of(root);
        return new Build(new ModularUI(ui), refs);
    }

    @Override
    public void init() {
        // Let ModularUIContainerScreen init the ModularUI and place it.
        super.init();

        // Fill UI elements from current menu state.
        if (urlField != null) {
            urlField.setText(initialUrl());
        }
        refreshHistory();
        syncLabels();
    }

    /**
     * Keep vanilla dimming behind the GUI.
     *
     * In 1.21.x the background method signature is:
     *   renderBackground(GuiGraphics, int, int, float)
     */
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Dark rounded panel texture behind LDLib2.
        gfx.blit(PANEL_TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncLabels();
    }

    private void syncLabels() {
        if (statusLabel != null) {
            statusLabel.setText(menu.isPlaying()
                    ? Component.translatable("screen.avilixradiomod.playing")
                    : Component.translatable("screen.avilixradiomod.stopped"));
        }
        if (playStopButton != null) {
            playStopButton.setText(menu.isPlaying()
                    ? Component.translatable("screen.avilixradiomod.stop")
                    : Component.translatable("screen.avilixradiomod.play"));
        }
        if (saveButton != null) {
            saveButton.setText(Component.translatable("screen.avilixradiomod.save"));
        }
        if (volumeLabel != null) {
            volumeLabel.setText(Component.literal(menu.getVolume() + "%"));
        }
    }

    private void setVolume(int newVolume) {
        newVolume = Math.max(0, Math.min(100, newVolume));
        apply(menu.isPlaying(), newVolume);
    }

    private void apply(boolean playing, int volume) {
        String url = (urlField != null) ? urlField.getText() : menu.getUrl();
        if (url == null) url = "";
        url = url.trim();

        if (!url.isEmpty()) {
            UrlHistory.remember(url);
            refreshHistory();
        }

        RadioBlockEntity radio = menu.getRadio();
        if (radio != null) {
            radio.setClientSidePreview(url, playing, volume);
        }

        ModPayloads.sendToServer(new RadioSettingsPayload(menu.getPos(), url, playing, volume));
    }

    private void refreshHistory() {
        if (historySelector == null) return;
        historySelector.setCandidates(loadHistory());
    }

    private static List<String> loadHistory() {
        List<String> out = new ArrayList<>(UrlHistory.getAll());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private String initialUrl() {
        String url = menu.getUrl();
        if (url != null && !url.isBlank()) return url.trim();

        List<String> hist = UrlHistory.getAll();
        if (!hist.isEmpty()) {
            String last = hist.get(0);
            if (last != null && !last.isBlank()) return last.trim();
        }
        return UrlHistory.getDefaultUrl();
    }
}

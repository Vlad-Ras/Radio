package com.avilixradiomod.client.screen;

import com.avilixradiomod.blockentity.RadioBlockEntity;
import com.avilixradiomod.client.UrlHistory;
import com.avilixradiomod.menu.RadioMenu;
import com.avilixradiomod.network.ModPayloads;
import com.avilixradiomod.network.RadioSettingsPayload;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
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
import net.minecraft.world.entity.player.Inventory;

import org.appliedenergistics.yoga.*;

import java.util.ArrayList;
import java.util.List;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
/**
 * Radio screen in vintage "radiola" style.
 * Same behavior, different presentation.
 */
public final class RadioScreen extends ModularUIContainerScreen<RadioMenu> {

    private static final int UI_W = 360;
    private static final int UI_H = 228;

    private static final int COLOR_BUTTON_MAIN = 0xFF6B4726;
    private static final int COLOR_BUTTON_SMALL = 0xFF5E3D20;
    private static final int COLOR_TEXT_MAIN = 0xFFF3E6C8;
    private static final int COLOR_TEXT_GOLD = 0xFFD8C38A;
    private static final int COLOR_TEXT_STATUS = 0xFFD5B56F;
    private static final int COLOR_TEXT_VOLUME = 0xFFF1E5C5;
    private static final int COLOR_FIELD_BG = 0xFF5A3A1E;
    private static final int COLOR_SELECTOR_BG = 0xFF4B2F18;

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

        this.imageWidth = UI_W;
        this.imageHeight = UI_H;

        this.titleLabelY = -10_000;
        this.inventoryLabelY = -10_000;

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
                    layout.setPadding(YogaEdge.ALL, 20);
                    layout.setGap(YogaGutter.ALL, 10);
                });

        UIElement header = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                });

        Label title = new Label();
        title.setText(Component.literal("АВИЛИКС • РАДИО"));
        title.textStyle(s -> s
                .fontSize(13)
                .textColor(0xFFE6D09C)
                .adaptiveWidth(true)
                .textShadow(true));

        refs.statusLabel = new Label();
        refs.statusLabel.setText(Component.empty());
        refs.statusLabel.textStyle(s -> s
                .fontSize(9)
                .textColor(COLOR_TEXT_STATUS)
                .adaptiveWidth(true));
        //refs.statusLabel.layout(layout -> layout.setMargin(YogaEdge.TOP, 2));

        header.addChildren(title, refs.statusLabel);
        root.addChild(header);

        UIElement body = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setGap(YogaGutter.COLUMN, 12);
                    layout.setFlex(1);
                });

        UIElement leftColumn = new UIElement()
                .layout(layout -> {
                    layout.setWidth(116);
                    layout.setFlexDirection(YogaFlexDirection.COLUMN);
                    layout.setGap(YogaGutter.ALL, 8);
                });

        UIElement speakerSpacer = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setHeight(102);
                });

        UIElement historyHeader = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                    layout.setGap(YogaGutter.COLUMN, 6);
                });

        refs.clearHistoryButton = new Button();
        refs.clearHistoryButton.setText(Component.literal("Очистить"));
        refs.clearHistoryButton.layout(layout -> layout.setWidthPercent(95));
        applyRadiolaButtonStyle(refs.clearHistoryButton);

        historyHeader.addChildren(refs.clearHistoryButton);

        refs.historySelector = new Selector<>();
        refs.historySelector.layout(layout -> {
            layout.setWidthPercent(95);
            layout.setMargin(YogaEdge.TOP, 15);
        });
        refs.historySelector.setCandidates(List.of(""));
        applyRadiolaSelectorStyle(refs.historySelector);

        leftColumn.addChildren(speakerSpacer, refs.historySelector, historyHeader);

        UIElement rightColumn = new UIElement()
                .layout(layout -> {
                    layout.setFlex(1);
                    layout.setFlexDirection(YogaFlexDirection.COLUMN);
                    layout.setGap(YogaGutter.ALL, 8);
                });

        UIElement dialSpacer = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setHeight(50);
                });

        Label urlLbl = new Label();
        urlLbl.setText(Component.literal("ИСТОЧНИК"));
        urlLbl.textStyle(s -> s
                .fontSize(9)
                .textColor(COLOR_TEXT_GOLD)
                .adaptiveWidth(true));
        urlLbl.layout(layout -> {
            layout.setMargin(YogaEdge.TOP, 3);
            layout.setMargin(YogaEdge.LEFT, 3);
        });

        refs.urlField = new TextField();
        refs.urlField.layout(layout -> layout.setWidthPercent(100));
        refs.urlField.setPlaceholder("Ссылка на поток");
        refs.urlField.setTextValidator(s -> s != null && s.length() <= 256);
        applyRadiolaTextFieldStyle(refs.urlField);

        UIElement controls = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setGap(YogaGutter.COLUMN, 8);
                });

        refs.playStopButton = new Button();
        refs.playStopButton.layout(layout -> layout.setFlex(1));
        applyRadiolaButtonStyle(refs.playStopButton);

        refs.saveButton = new Button();
        refs.saveButton.layout(layout -> layout.setFlex(1));
        applyRadiolaButtonStyle(refs.saveButton);

        controls.addChildren(refs.playStopButton, refs.saveButton);

        UIElement volumeRow = new UIElement()
                .layout(layout -> {
                    layout.setWidthPercent(100);
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setPadding(YogaEdge.HORIZONTAL, 20);
                    layout.setJustifyContent(YogaJustify.SPACE_BETWEEN);
                });

        Label volTitle = new Label();
        volTitle.setText(Component.literal("ГРОМКОСТЬ"));
        volTitle.textStyle(s -> s
                .fontSize(9)
                .textColor(COLOR_TEXT_GOLD)
                .adaptiveWidth(true));

        UIElement volControls = new UIElement()
                .layout(layout -> {
                    layout.setFlexDirection(YogaFlexDirection.ROW);
                    layout.setAlignItems(YogaAlign.CENTER);
                    layout.setGap(YogaGutter.COLUMN, 8);
                });

        refs.volMinusButton = new Button();
        refs.volMinusButton.setText(Component.literal("−"));
        refs.volMinusButton.layout(layout -> layout.setWidth(24));
        applyRadiolaSmallButtonStyle(refs.volMinusButton);

        refs.volumeLabel = new Label();
        refs.volumeLabel.setText(Component.literal("100%"));
        refs.volumeLabel.textStyle(s -> s
                .fontSize(10)
                .adaptiveWidth(true)
                .textColor(COLOR_TEXT_VOLUME)
                .textShadow(true));

        refs.volPlusButton = new Button();
        refs.volPlusButton.setText(Component.literal("+"));
        refs.volPlusButton.layout(layout -> layout.setWidth(24));
        applyRadiolaSmallButtonStyle(refs.volPlusButton);

        volControls.addChildren(refs.volMinusButton, refs.volumeLabel, refs.volPlusButton);
        volumeRow.addChildren(volTitle, volControls);

        rightColumn.addChildren(dialSpacer, urlLbl, refs.urlField, controls, volumeRow);

        body.addChildren(leftColumn, rightColumn);
        root.addChild(body);

        UI ui = UI.of(root);
        return new Build(new ModularUI(ui), refs);
    }

    private static void applyRadiolaButtonStyle(Button button) {
        button.textStyle(s -> s
                .textColor(0xFFF3E6C8)
                .textShadow(true));

        Button.ButtonStyle bs = button.getButtonStyle();
        bs.defaultTexture(new ColorRectTexture(0xFF2c1d13));   // как корпус динамика
        bs.hoverTexture(new ColorRectTexture(0xFF4A2D18));  // чуть светлее при наведении
        bs.pressedTexture(new ColorRectTexture(0xFF24150B)); // как тёмная сетка динамика
    }

    private static void applyRadiolaSmallButtonStyle(Button button) {
        button.textStyle(s -> s
                .textColor(0xFFF3E6C8)
                .textShadow(true));

        Button.ButtonStyle bs = button.getButtonStyle();
        bs.defaultTexture(new ColorRectTexture(0xFF2c1d13));
        bs.hoverTexture(new ColorRectTexture(0xFF4A2D18));
        bs.pressedTexture(new ColorRectTexture(0xFF24150B));
    }


    private static void applyRadiolaTextFieldStyle(TextField textField) {
        textField.style(style -> style.backgroundTexture(new ColorRectTexture(COLOR_FIELD_BG)));

        textField.textFieldStyle(style -> style
                .fontSize(9)
                .textColor(COLOR_TEXT_MAIN)
                .errorColor(0xFFFF6B6B)
                .cursorColor(COLOR_TEXT_MAIN));
    }

    private static void applyRadiolaSelectorStyle(Selector<String> selector) {
        selector.style(style -> style.backgroundTexture(new ColorRectTexture(COLOR_SELECTOR_BG)));
        selector.selectorStyle(style -> style
                .showOverlay(true)
                .closeAfterSelect(true));
    }

    @Override
    public void init() {
        super.init();

        if (urlField != null) {
            urlField.setText(initialUrl());
        }

        refreshHistory();
        syncLabels();
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        drawRadiolaBackground(gfx);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncLabels();
    }

    private void syncLabels() {
        if (statusLabel != null) {
            statusLabel.setText(menu.isPlaying()
                    ? Component.literal("● ЭФИР")
                    : Component.literal("○ ТИШИНА"));
        }

        if (playStopButton != null) {
            playStopButton.setText(menu.isPlaying()
                    ? Component.literal("СТОП")
                    : Component.literal("ПУСК"));
        }

        if (saveButton != null) {
            saveButton.setText(Component.literal("СОХРАНИТЬ"));
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

        String def = UrlHistory.getDefaultUrl();
        if (def != null && !def.isBlank()) return def.trim();

        List<String> hist = UrlHistory.getAll();
        if (!hist.isEmpty()) {
            String last = hist.get(0);
            if (last != null && !last.isBlank()) return last.trim();
        }

        return "";
    }

    private void drawRadiolaBackground(GuiGraphics gfx) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;

        gfx.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xAA000000);

        fillFrame(gfx, x, y, w, h, 0xFF2F1D10, 0xFF8C6031, 0xFF5C3B20);
        fillFrame(gfx, x + 8, y + 8, w - 16, h - 16, 0xFF4C3118, 0xFFC19A57, 0xFF74542E);
        fillFrame(gfx, x + 14, y + 12, w - 28, 24, 0xFF5B3A1E, 0xFFD7B46F, 0xFF7F5D34);

        int speakerX = x + 16;
        int speakerY = y + 46;
        int speakerW = 116;
        int speakerH = 104;
        fillFrame(gfx, speakerX, speakerY, speakerW, speakerH, 0xFF3B2414, 0xFF9D7845, 0xFF5F4225);
        drawSpeakerGrille(gfx, speakerX + 8, speakerY + 8, speakerW - 16, speakerH - 16);

        int dialX = x + 144;
        int dialY = y + 46;
        int dialW = 198;
        int dialH = 42;
        fillFrame(gfx, dialX, dialY, dialW, dialH, 0xFFEEE0B5, 0xFFD6B673, 0xFF9A7740);
        drawScale(gfx, dialX + 8, dialY + 8, dialW - 16, dialH - 16);

        fillFrame(gfx, x + 144, y + 96, 198, 90, 0xFF432A16, 0xFFA67C45, 0xFF62411F);
        fillFrame(gfx, x + 16, y + 160, 116, 46, 0xFF432A16, 0xFFA67C45, 0xFF62411F);

        gfx.drawString(font, "ПАМЯТЬ", x + 22, y + 151, COLOR_TEXT_GOLD, false);

    }

    private void drawSpeakerGrille(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, 0xFF24150B);

        for (int yy = y + 2; yy < y + h - 2; yy += 6) {
            gfx.fill(x + 4, yy, x + w - 4, yy + 1, 0xFF7C6038);
        }

        for (int xx = x + 8; xx < x + w - 8; xx += 14) {
            gfx.fill(xx, y + 4, xx + 1, y + h - 4, 0xFF4A351D);
        }

        fillFrame(gfx, x + 18, y + 18, w - 36, h - 36, 0x221A0F07, 0x554E3820, 0x331A0F07);
    }

    private void drawScale(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.drawString(font, "LW", x + 4, y - 2, 0xFF6A4E27, false);
        gfx.drawString(font, "MW", x + 34, y - 2, 0xFF6A4E27, false);
        gfx.drawString(font, "FM", x + 64, y - 2, 0xFF6A4E27, false);
        gfx.drawString(font, "NET", x + 94, y - 2, 0xFF6A4E27, false);

        int lineY = y + h - 9;
        gfx.fill(x + 2, lineY, x + w - 2, lineY + 2, 0xFF7A5B2D);

        for (int i = 0; i <= 14; i++) {
            int tx = x + 4 + (i * (w - 8) / 14);
            int tickH = (i % 2 == 0) ? 10 : 6;
            gfx.fill(tx, lineY - tickH, tx + 1, lineY + 2, 0xFF8B6A37);
        }

        float tune = menu.getVolume() / 100.0f;
        int px = x + 4 + Math.round((w - 8) * tune);
        gfx.fill(px - 1, y + 4, px + 1, y + h - 2, menu.isPlaying() ? 0xFFE19B2E : 0xFF9D7740);
    }

    private void drawLamp(GuiGraphics gfx, int centerX, int centerY, int color) {
        gfx.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFF2D1C0D);
        gfx.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, color);
    }

    private void fillFrame(GuiGraphics gfx, int x, int y, int w, int h, int fill, int light, int dark) {
        gfx.fill(x, y, x + w, y + h, fill);

        gfx.fill(x, y, x + w, y + 2, light);
        gfx.fill(x, y, x + 2, y + h, light);

        gfx.fill(x, y + h - 2, x + w, y + h, dark);
        gfx.fill(x + w - 2, y, x + w, y + h, dark);

        gfx.fill(x + 2, y + 2, x + w - 2, y + 4, 0x22FFFFFF);
        gfx.fill(x + 2, y + 2, x + 4, y + h - 2, 0x22FFFFFF);
    }
}
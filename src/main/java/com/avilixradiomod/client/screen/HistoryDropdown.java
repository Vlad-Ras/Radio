package com.avilixradiomod.client.screen;

import com.avilixradiomod.client.UrlHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable dropdown for URL history.
 * Opens only by button ▼.
 *
 * Fixes:
 *  - Renders above the whole GUI (high Z)
 *  - Uses scissor (clipping) so rows never draw outside dropdown
 *  - Cuts text to fit width
 */
public final class HistoryDropdown extends AbstractWidget {

    private static final int ITEM_HEIGHT = 24; //12
    private static final int MAX_VISIBLE = 6;

    private final Consumer<String> onSelect;

    private List<String> items = List.of();
    private int scroll = 0;
    private boolean open = false;

    public HistoryDropdown(int x, int y, int width, Consumer<String> onSelect) {
        super(x, y, width, ITEM_HEIGHT * MAX_VISIBLE, Component.empty());
        this.onSelect = onSelect;
        this.visible = false;
        this.active = false;
    }

    // ----------------- public API -----------------

    public void openBelow(int x, int y, int width) {
        this.items = UrlHistory.getAll();
        this.scroll = 0;

        this.setX(x);
        this.setY(y);
        this.setWidth(width);

        int visibleRows = Math.min(items.size(), MAX_VISIBLE);
        this.setHeight(Math.max(0, visibleRows) * ITEM_HEIGHT);

        this.open = true;
        this.visible = true;
        this.active = true;
    }

    public void close() {
        this.open = false;
        this.visible = false;
        this.active = false;
    }

    public boolean isOpen() {
        return open;
    }

    // ----------------- rendering -----------------

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!visible || !open || height <= 0) return;

        // draw ABOVE everything
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        // background + border
        g.fill(x0, y0, x1, y1, 0xFF202020);
        g.fill(x0, y0, x1, y0 + 1, 0xFF000000);
        g.fill(x0, y1 - 1, x1, y1, 0xFF000000);
        g.fill(x0, y0, x0 + 1, y1, 0xFF000000);
        g.fill(x1 - 1, y0, x1, y1, 0xFF000000);

        // CLIP drawing to dropdown rect (prevents any "налезает")
        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);

        var font = Minecraft.getInstance().font;

        int maxRows = Math.min(items.size() - scroll, MAX_VISIBLE);
        for (int row = 0; row < maxRows; row++) {
            int idx = scroll + row;
            int rowY = y0 + row * ITEM_HEIGHT;

            boolean hover =
                    mouseX >= x0 && mouseX < x1
                            && mouseY >= rowY && mouseY < rowY + ITEM_HEIGHT;

            if (hover) {
                g.fill(x0 + 1, rowY, x1 - 1, rowY + ITEM_HEIGHT, 0xFF3A3A3A);
            }

            String text = items.get(idx);

            // cut to fit inside dropdown
            String cut = font.plainSubstrByWidth(text, width - 10);

            g.drawString(font, cut, x0 + 4, rowY + 2, 0xFFFFFF, false);
        }

        g.disableScissor();
        g.pose().popPose();
    }

    // ----------------- mouse -----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || !visible || height <= 0) return false;

        if (mouseX < getX() || mouseX >= getX() + width) return false;
        if (mouseY < getY() || mouseY >= getY() + height) return false;

        int row = (int) ((mouseY - getY()) / ITEM_HEIGHT);
        int index = scroll + row;

        if (index >= 0 && index < items.size()) {
            onSelect.accept(items.get(index));
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!open || !visible || height <= 0) return false;

        // only when mouse is over dropdown
        if (mouseX < getX() || mouseX >= getX() + width) return false;
        if (mouseY < getY() || mouseY >= getY() + height) return false;

        int maxScroll = Math.max(0, items.size() - MAX_VISIBLE);

        if (deltaY < 0) scroll = Math.min(scroll + 1, maxScroll);
        if (deltaY > 0) scroll = Math.max(scroll - 1, 0);

        return true;
    }

    // Required by AbstractWidget (1.21+)
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // mouse-only dropdown
    }
}

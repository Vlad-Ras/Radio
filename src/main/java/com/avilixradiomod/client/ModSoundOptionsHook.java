package com.avilixradiomod.client;

import com.avilixradiomod.config.ModConfigs;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;

/**
 * Adds a master volume slider for the mod directly into Minecraft's Sound Options screen.
 */
public final class ModSoundOptionsHook {
    private ModSoundOptionsHook() {}

    // We keep per-screen state without leaking memory.
    private static final Map<Screen, ModMasterVolumeSlider> SLIDERS = new WeakHashMap<>();

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof SoundOptionsScreen)) return;

        // IMPORTANT: Don't place the slider as a floating widget.
        // SoundOptionsScreen uses a scrollable OptionsList; when the window is small it scrolls,
        // and when it's large it lays out everything in a stable grid. If we add a free-floating
        // widget, it will drift, stick to the bottom, or behave oddly with scrolling.
        //
        // The correct solution is to inject our slider INTO the OptionsList entry that has an empty
        // right slot (the last row where the left slider exists but right is blank). Then vanilla
        // layout/scrolling does the right thing and the widget never "runs away".

        OptionsList optionsList = findOptionsList(screen);
        if (optionsList == null) return;

        // Create or re-add our slider as a normal screen widget, but anchor it to the exact
        // "empty right slot" row by tracking the left widget's x/y (which already scrolls correctly).
        // This avoids touching protected OptionsList.Entry internals and works across UI mods.
        ModMasterVolumeSlider slider = SLIDERS.get(screen);
        if (slider == null) {
            slider = new ModMasterVolumeSlider(0, 0, 150, 20);
            SLIDERS.put(screen, slider);
        }

        // Ensure it's in the screen's listener list (Init clears them).
        event.addListener(slider);
    }

    /**
     * Every frame we anchor the slider to the "missing right column" row.
     * This makes it stable on huge windows and correctly scroll with the OptionsList on small windows.
     */
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof SoundOptionsScreen)) return;

        ModMasterVolumeSlider slider = SLIDERS.get(screen);
        if (slider == null) return;

        OptionsList optionsList = findOptionsList(screen);
        if (optionsList == null) {
            slider.visible = false;
            return;
        }

        AbstractWidget left = findEmptyRightSlotLeftWidget(optionsList);
        if (left == null) {
            slider.visible = false;
            return;
        }

        // Match size to vanilla small sliders.
        slider.setWidth(left.getWidth());
        slider.setHeight(left.getHeight());

        // Right column X in vanilla is leftX + 160 (150 width + 10 gap). Using left widget makes it scale-proof.
        slider.setX(left.getX() + left.getWidth() + 10);
        slider.setY(left.getY());

        // Respect clipping: if the left widget isn't visible (scrolled out), hide ours too.
        slider.visible = left.visible;
        slider.active = left.active;
    }


    private static OptionsList findOptionsList(Screen screen) {
        for (GuiEventListener l : screen.children()) {
            if (l instanceof OptionsList ol) return ol;
        }
        return null;
    }

    private static AbstractWidget findEmptyRightSlotLeftWidget(OptionsList list) {
        List<?> entries = getOptionsListEntries(list);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Object entry = entries.get(i);
            List<GuiEventListener> kids = safeChildren(entry);
            if (kids == null) continue;

            if (kids.size() == 1 && kids.getFirst() instanceof AbstractWidget w) {
                // Typical small sliders are 150x20 (some UI mods can change a bit).
                if (w.getHeight() == 20 && w.getWidth() >= 120 && w.getWidth() <= 220) {
                    return w;
                }
            }
        }
        return null;
    }

    /**
     * OptionsList#children() is public, but its return type mentions OptionsList.Entry which is
     * a protected nested class. Referencing it directly from our package fails compilation.
     *
     * So we fetch the entries reflectively as a plain List<?>.
     */
    @SuppressWarnings("unchecked")
    private static List<?> getOptionsListEntries(OptionsList list) {
        try {
            Method m = OptionsList.class.getMethod("children");
            Object r = m.invoke(list);
            if (r instanceof List<?> lst) return lst;
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<GuiEventListener> safeChildren(Object entry) {
        // 1) Try the public children() method
        try {
            Method m = entry.getClass().getMethod("children");
            Object r = m.invoke(entry);
            if (r instanceof List<?> lst) {
                return (List<GuiEventListener>) lst;
            }
        } catch (Throwable ignored) {}

        // 2) Reflectively find a List field that looks like the children list
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object r = f.get(entry);
                if (r instanceof List<?> lst) {
                    // Heuristic: children list usually contains AbstractWidget instances.
                    boolean hasWidget = false;
                    for (Object o : lst) {
                        if (o instanceof AbstractWidget) { hasWidget = true; break; }
                    }
                    if (hasWidget) return (List<GuiEventListener>) lst;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean tryAddToList(List<GuiEventListener> list, GuiEventListener child) {
        try {
            list.add(child);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class ModMasterVolumeSlider extends AbstractSliderButton {
        private static final Component LABEL = Component.translatable("options.avilixradiomod.master_volume");

        ModMasterVolumeSlider(int x, int y, int width, int height) {
            super(x, y, width, height,
                    Component.empty(),
                    clamp01(ModConfigs.CLIENT.globalVolume.get()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = (int) Math.round(this.value * 100.0);
            this.setMessage(LABEL.copy().append(": ").append(Component.literal(pct + "%")));
        }

        @Override
        protected void applyValue() {
            ModConfigs.CLIENT.globalVolume.set(clamp01(this.value));
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean r = super.mouseReleased(mouseX, mouseY, button);
            // Explicitly flush to disk so the value persists across reconnects / restarts.
            ClientConfigPersistence.saveGlobalVolume(ModConfigs.CLIENT.globalVolume.get());
            return r;
        }

        private static double clamp01(double v) {
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }
    }
}

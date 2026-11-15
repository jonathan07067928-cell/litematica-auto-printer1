package com.autoprinter.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/** A slider that shows "Label: value" and reports the real (not 0..1) value when moved. */
final class ConfigSlider extends AbstractSliderButton {
    private final String label;
    private final double min;
    private final double max;
    private final boolean integer;
    private final String suffix;
    private final DoubleConsumer onChange;

    ConfigSlider(int x, int y, int width, int height, String label, double min, double max,
                 boolean integer, double current, String suffix, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
        this.label = label;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.suffix = suffix;
        this.onChange = onChange;
        updateMessage();
    }

    private double actual() {
        double raw = min + this.value * (max - min);
        return integer ? Math.round(raw) : Math.round(raw * 10.0) / 10.0;
    }

    @Override
    protected void updateMessage() {
        if (label == null) {
            return; // the parent constructor may call this before our fields exist
        }
        double value = actual();
        String text = integer ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
        setMessage(Component.literal(label + ": " + text + suffix));
    }

    @Override
    protected void applyValue() {
        onChange.accept(actual());
    }
}

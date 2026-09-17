package mira.client.features.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Formatting;
import mira.client.core.manager.client.ModuleManager;
import mira.client.gui.font.FontRenderers;
import mira.client.features.hud.HudElement;
import mira.client.features.modules.render.HudEditor;
import mira.client.utility.math.MathUtility;
import mira.client.utility.render.Render2DEngine;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Collections;

public class AutoCrystalInfo extends HudElement {
    public AutoCrystalInfo() {
        super("AutoCrystalInfo", 175, 80);
    }

    private final ArrayDeque<Integer> speeds = new ArrayDeque<>(20);
    private int max, min;
    private long time;

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);
        
    }

    public Formatting getCalcColor(float val) {
        if (val > 20) return Formatting.RED;
        else if (val > 10) return Formatting.YELLOW;
        return Formatting.GREEN;
    }

    public Formatting getEfficiencyColor(float val) {
        if (val > 6) return Formatting.GREEN;
        else if (val < 1) return Formatting.RED;
        return Formatting.YELLOW;
    }

    public void onSpawn() {
        if (time != 0L) {
            if (speeds.size() > 20)
                speeds.poll();

            speeds.add((int) (1000f / (float) (System.currentTimeMillis() - time)));
            max = Collections.max(speeds);
            min = Collections.min(speeds);
        }
        time = System.currentTimeMillis();
    }
}

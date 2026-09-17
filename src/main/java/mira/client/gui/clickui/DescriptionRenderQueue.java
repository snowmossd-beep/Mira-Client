package mira.client.gui.clickui;
import net.minecraft.client.gui.DrawContext;
import mira.client.gui.font.FontRenderers;
import mira.client.utility.render.Render2DEngine;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
public class DescriptionRenderQueue {
    private static final List<Entry> QUEUE = new ArrayList<>();
    public static void add(String text, float x, float y) {
        if (text == null || text.isEmpty()) return;
        QUEUE.add(new Entry(text, x, y));
    }
    public static void renderAll(DrawContext ctx) {
        for (Entry e : QUEUE) {
            float w = FontRenderers.sf_medium.getStringWidth(e.text) + 10;
            float h = 14;
            Render2DEngine.drawRound(ctx.getMatrices(), e.x, e.y, w, h, 3f, new Color(18, 18, 22, 240));
            FontRenderers.sf_medium.drawString(ctx.getMatrices(), e.text, e.x + 5, e.y + 3, Color.WHITE.getRGB());
        }
        QUEUE.clear();
    }
    private record Entry(String text, float x, float y) {}
}

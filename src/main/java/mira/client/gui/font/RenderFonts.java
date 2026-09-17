package mira.client.gui.font;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static mira.client.core.manager.IManager.mc;

@SuppressWarnings("All")
public class RenderFonts implements Closeable {

    private static final ExecutorService ASYNC_WORKER      = Executors.newSingleThreadExecutor();
    private static final float           SCALE_FACTOR       = 0.5f;
    private static final float           INV_COLOR_DIVISOR  = 1f / 255f;
    private static final int             COLOR_SHIFT_ALPHA  = 24;
    private static final int             COLOR_SHIFT_RED    = 16;
    private static final int             COLOR_SHIFT_GREEN  = 8;

    private final Map<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new ConcurrentHashMap<>();
    private final List<GlyphMap>                         maps             = new ArrayList<>();
    private final Char2ObjectMap<Glyph>                  allGlyphs        = new Char2ObjectArrayMap<>();
    private final int                                    charsPerPage;
    private final int                                    padding;
    private final String                                 prebakeGlyphs;
    private final float                                  originalSize;
    private final FontRenderContext                      sharedFontRenderContext;

    private float   cachedMaxHeight = 0f;
    private boolean heightDirty     = true;

    private final Map<String, Float> widthCache = new ConcurrentHashMap<>();
    private static final int MAX_WIDTH_CACHE_SIZE = 1000;

    private Font          font;
    private Future<Void>  prebakeGlyphsFuture;

    private static final Char2ObjectMap<float[]> COLOR_CODES_FLOAT = new Char2ObjectArrayMap<>();
    static {
        COLOR_CODES_FLOAT.put('0', new float[]{0f,           0f,           0f          });
        COLOR_CODES_FLOAT.put('1', new float[]{0f,           0f,           0.6666667f  });
        COLOR_CODES_FLOAT.put('2', new float[]{0f,           0.6666667f,   0f          });
        COLOR_CODES_FLOAT.put('3', new float[]{0f,           0.6666667f,   0.6666667f  });
        COLOR_CODES_FLOAT.put('4', new float[]{0.6666667f,   0f,           0f          });
        COLOR_CODES_FLOAT.put('5', new float[]{0.6666667f,   0f,           0.6666667f  });
        COLOR_CODES_FLOAT.put('6', new float[]{1f,           0.6666667f,   0f          });
        COLOR_CODES_FLOAT.put('7', new float[]{0.6666667f,   0.6666667f,   0.6666667f  });
        COLOR_CODES_FLOAT.put('8', new float[]{0.33333334f,  0.33333334f,  0.33333334f });
        COLOR_CODES_FLOAT.put('9', new float[]{0.33333334f,  0.33333334f,  1f          });
        COLOR_CODES_FLOAT.put('a', new float[]{0.33333334f,  1f,           0.33333334f });
        COLOR_CODES_FLOAT.put('b', new float[]{0.33333334f,  1f,           1f          });
        COLOR_CODES_FLOAT.put('c', new float[]{1f,           0.33333334f,  0.33333334f });
        COLOR_CODES_FLOAT.put('d', new float[]{1f,           0.33333334f,  1f          });
        COLOR_CODES_FLOAT.put('e', new float[]{1f,           1f,           0.33333334f });
        COLOR_CODES_FLOAT.put('f', new float[]{1f,           1f,           1f          });
    }

    private static final ThreadLocal<StringBuilder> STRING_BUILDER_POOL = ThreadLocal.withInitial(StringBuilder::new);
    private static final ThreadLocal<char[]>        CHAR_ARRAY_BUFFER   = ThreadLocal.withInitial(() -> new char[1024]);

    public RenderFonts(Font font, float sizePx, int charsPerPage, int padding, String prebakeGlyphs) {
        this.originalSize   = sizePx;
        this.charsPerPage   = charsPerPage;
        this.padding        = padding;
        this.prebakeGlyphs  = prebakeGlyphs;

        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        this.sharedFontRenderContext = g.getFontRenderContext();
        g.dispose();

        initializeFont(font, sizePx);
    }

    public RenderFonts(Font font, float sizePx) {
        this(font, sizePx, 256, 5, null);
    }

    private void initializeFont(Font baseFont, float sizePx) {
        this.font = baseFont.deriveFont(sizePx);
        if (prebakeGlyphs != null && !prebakeGlyphs.isEmpty()) {
            prebakeGlyphsFuture = ASYNC_WORKER.submit(() -> {
                for (char c : prebakeGlyphs.toCharArray()) {
                    if (Thread.interrupted()) break;
                    locateGlyph(c);
                }
                return null;
            });
        }
    }

    public void drawLeftAligned(MatrixStack ms, String text, float x, float y, int color) {
        render(ms, text, x, y, color, false);
    }

    public void drawRightAligned(MatrixStack ms, String text, float x, float y, int color) {
        render(ms, text, x - getWidth(text), y, color, false);
    }

    public void centeredDraw(MatrixStack ms, String text, float x, float y, int color) {
        render(ms, text, x - getWidth(text) * 0.5f, y, color, false);
    }

    public float getWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;

        Float cached = widthCache.get(text);
        if (cached != null) return cached;

        float width = 0;
        char[] chars = getCharArray(text);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = chars[i];
            if ((c == '\u00a7' || c == '&') && i + 1 < len) { i++; continue; }
            Glyph glyph = locateGlyph(c);
            if (glyph != null) width += glyph.width() * SCALE_FACTOR;
        }

        if (widthCache.size() < MAX_WIDTH_CACHE_SIZE) widthCache.put(text, width);
        return width;
    }

    public float getHeight() {
        if (heightDirty) {
            float max = 0;
            for (Glyph g : allGlyphs.values()) {
                if (g != null) { float h = g.height(); if (h > max) max = h; }
            }
            cachedMaxHeight = max * SCALE_FACTOR;
            heightDirty = false;
        }
        return cachedMaxHeight;
    }

    public void drawClipped(MatrixStack ms, String text, float maxWidth, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        StringBuilder clipped = STRING_BUILDER_POOL.get();
        clipped.setLength(0);
        float w = 0;
        char[] chars = getCharArray(text);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = chars[i];
            if ((c == '\u00a7' || c == '&') && i + 1 < len) { i++; continue; }
            Glyph glyph = locateGlyph(c);
            if (glyph == null) continue;
            float gw = glyph.width() * SCALE_FACTOR;
            if (w + gw > maxWidth) break;
            clipped.append(c);
            w += gw;
        }
        render(ms, clipped.toString(), x, y, color, false);
    }

    public List<String> splitTextToLines(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder current = new StringBuilder();
        String[] words = text.split(" ");
        for (String word : words) {
            if (current.length() > 0) {
                String test = current + " " + word;
                if (getWidth(test) <= maxWidth) { current.append(" ").append(word); }
                else { lines.add(current.toString()); current = new StringBuilder(word); }
            } else {
                if (getWidth(word) <= maxWidth) { current.append(word); }
                else {
                    StringBuilder part = new StringBuilder();
                    for (char c : word.toCharArray()) {
                        if (getWidth(part.toString() + c) <= maxWidth) { part.append(c); }
                        else { lines.add(part.toString()); part = new StringBuilder("" + c); }
                    }
                    current = part;
                }
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private void render(MatrixStack ms, String text, float x, float y, int color, boolean isGradient) {
        if (text == null || text.isEmpty()) return;
        awaitPrebake();

        float a = ((color >>> COLOR_SHIFT_ALPHA) & 0xFF) * INV_COLOR_DIVISOR;
        float r = ((color >>> COLOR_SHIFT_RED)   & 0xFF) * INV_COLOR_DIVISOR;
        float g = ((color >>> COLOR_SHIFT_GREEN) & 0xFF) * INV_COLOR_DIVISOR;
        float b = ( color                         & 0xFF) * INV_COLOR_DIVISOR;

        RenderSystem.setShaderColor(r, g, b, a);
        ms.push();
        ms.translate(x, y, 0);
        ms.scale(SCALE_FACTOR, SCALE_FACTOR, 1);

        enableRender();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        Matrix4f mat = ms.peek().getPositionMatrix();

        Map<Identifier, List<DrawEntry>> localCache = new Object2ObjectOpenHashMap<>();
        GLYPH_PAGE_CACHE.forEach((k, v) -> localCache.put(k, new ObjectArrayList<>(v)));
        GLYPH_PAGE_CACHE.clear();

        float cursorX = 0;
        char[] chars = getCharArray(text);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = chars[i];
            if (!isGradient && (c == '\u00a7' || c == '&') && i + 1 < len) {
                char code = Character.toLowerCase(chars[++i]);
                float[] col = COLOR_CODES_FLOAT.get(code);
                if (col != null) { r = col[0]; g = col[1]; b = col[2]; }
                continue;
            }
            Glyph glyph = locateGlyph(c);
            if (glyph != null) {
                localCache.computeIfAbsent(glyph.owner().bindToTexture, k -> new ObjectArrayList<>())
                          .add(new DrawEntry(cursorX, 0, r, g, b, glyph));
                cursorX += glyph.width();
            }
        }

        float finalA = a;
        Tessellator tess = Tessellator.getInstance();
        localCache.forEach((id, entries) -> {
            RenderSystem.setShaderTexture(0, id);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (DrawEntry e : entries) {
                Glyph gl = e.toDraw;
                GlyphMap gm = gl.owner();
                float w  = gl.width(), h = gl.height();
                float u1 = gl.u() * gm.invWidth,       v1 = gl.v() * gm.invHeight;
                float u2 = (gl.u() + w) * gm.invWidth, v2 = (gl.v() + h) * gm.invHeight;
                buf.vertex(mat, e.atX,     e.atY + h, 0).texture(u1, v2).color(e.r, e.g, e.b, finalA);
                buf.vertex(mat, e.atX + w, e.atY + h, 0).texture(u2, v2).color(e.r, e.g, e.b, finalA);
                buf.vertex(mat, e.atX + w, e.atY,     0).texture(u2, v1).color(e.r, e.g, e.b, finalA);
                buf.vertex(mat, e.atX,     e.atY,     0).texture(u1, v1).color(e.r, e.g, e.b, finalA);
            }
            endBuilding(buf);
        });

        ms.pop();
        disableRender();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public void renderGradientText(MatrixStack ms, String text, float x, float y, int color1, int color2) {
        renderGradientInternal(ms, text, x, y, color1, color2, 0f, false);
    }

    public void renderAnimatedGradientText(MatrixStack ms, String text, float x, float y, int color1, int color2, float time) {
        renderGradientInternal(ms, text, x, y, color1, color2, time, true);
    }

    private void renderGradientInternal(MatrixStack ms, String text, float x, float y,
                                        int color1, int color2, float time, boolean animated) {
        if (text == null || text.isEmpty()) return;
        int length = text.length();

        final float rS = ((color1 >>> COLOR_SHIFT_RED)   & 0xFF) * INV_COLOR_DIVISOR;
        final float gS = ((color1 >>> COLOR_SHIFT_GREEN) & 0xFF) * INV_COLOR_DIVISOR;
        final float bS = ( color1                         & 0xFF) * INV_COLOR_DIVISOR;
        final float aS = ((color1 >>> COLOR_SHIFT_ALPHA) & 0xFF) * INV_COLOR_DIVISOR;
        final float rE = ((color2 >>> COLOR_SHIFT_RED)   & 0xFF) * INV_COLOR_DIVISOR;
        final float gE = ((color2 >>> COLOR_SHIFT_GREEN) & 0xFF) * INV_COLOR_DIVISOR;
        final float bE = ( color2                         & 0xFF) * INV_COLOR_DIVISOR;
        final float aE = ((color2 >>> COLOR_SHIFT_ALPHA) & 0xFF) * INV_COLOR_DIVISOR;

        awaitPrebake();

        ms.push();
        ms.translate(x, y, 0);
        ms.scale(SCALE_FACTOR, SCALE_FACTOR, 1);

        enableRender();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        Matrix4f mat = ms.peek().getPositionMatrix();

        Map<Identifier, List<GradientDrawEntry>> texMap = new Object2ObjectOpenHashMap<>();
        float cursorX = 0;
        final float invLen = length == 1 ? 0 : 1f / (length - 1);
        char[] chars = getCharArray(text);
        for (int i = 0; i < length; i++) {
            char c = chars[i];
            float t = i * invLen;
            if (animated) {
                float tRaw = t + (time % 1f);
                t = tRaw % 1f;
                t = t < 0.5f ? t * 2f : (1f - t) * 2f;
            }
            final float r = rS + (rE - rS) * t;
            final float g = gS + (gE - gS) * t;
            final float b = bS + (bE - bS) * t;
            final float a = aS + (aE - aS) * t;
            Glyph glyph = locateGlyph(c);
            if (glyph == null) continue;
            texMap.computeIfAbsent(glyph.owner().bindToTexture, k -> new ObjectArrayList<>())
                  .add(new GradientDrawEntry(cursorX, r, g, b, a, glyph));
            cursorX += glyph.width();
        }

        Tessellator tess = Tessellator.getInstance();
        texMap.forEach((tex, entries) -> {
            RenderSystem.setShaderTexture(0, tex);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (GradientDrawEntry e : entries) {
                Glyph gl = e.glyph;
                float w  = gl.width(), h = gl.height();
                float u1 = gl.u() * gl.owner().invWidth,       v1 = gl.v() * gl.owner().invHeight;
                float u2 = (gl.u() + w) * gl.owner().invWidth, v2 = (gl.v() + h) * gl.owner().invHeight;
                buf.vertex(mat, e.x,     h, 0).texture(u1, v2).color(e.r, e.g, e.b, e.a);
                buf.vertex(mat, e.x + w, h, 0).texture(u2, v2).color(e.r, e.g, e.b, e.a);
                buf.vertex(mat, e.x + w, 0, 0).texture(u2, v1).color(e.r, e.g, e.b, e.a);
                buf.vertex(mat, e.x,     0, 0).texture(u1, v1).color(e.r, e.g, e.b, e.a);
            }
            endBuilding(buf);
        });

        ms.pop();
        disableRender();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Nullable
    private Glyph locateGlyph(char c) {
        Glyph existing = allGlyphs.get(c);
        if (existing != null) return existing;
        Glyph created = createGlyph(c);
        if (created != null) { allGlyphs.put(c, created); heightDirty = true; }
        return created;
    }

    @Nullable
    private Glyph createGlyph(char c) {
        for (GlyphMap m : maps) { if (m.contains(c)) return m.getGlyph(c); }
        int base = charsPerPage * (c / charsPerPage);
        String id = generateRandomId(16);
        GlyphMap map = new GlyphMap((char) base, (char)(base + charsPerPage), font,
                Identifier.of("font", "temp/" + id), padding, sharedFontRenderContext);
        maps.add(map);
        return map.getGlyph(c);
    }

    private void awaitPrebake() {
        if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone()) {
            try { prebakeGlyphsFuture.get(); } catch (Exception ignored) {}
        }
    }

    private char[] getCharArray(String text) {
        char[] buf = CHAR_ARRAY_BUFFER.get();
        int len = text.length();
        if (len > buf.length) { buf = new char[len]; CHAR_ARRAY_BUFFER.set(buf); }
        text.getChars(0, len, buf, 0);
        return buf;
    }

    private static String generateRandomId(int length) {
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append((char)('a' + rng.nextInt(26)));
        return sb.toString();
    }

    private static void enableRender() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    private static void disableRender() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void endBuilding(BufferBuilder buf) {
        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    @Override
    public void close() {
        try {
            if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone() && !prebakeGlyphsFuture.isCancelled()) {
                prebakeGlyphsFuture.cancel(true);
                prebakeGlyphsFuture.get();
                prebakeGlyphsFuture = null;
            }
            maps.forEach(GlyphMap::destroy);
            maps.clear();
            allGlyphs.clear();
            GLYPH_PAGE_CACHE.clear();
            widthCache.clear();
        } catch (Exception ignored) {}
    }

    private record Glyph(int u, int v, int width, int height, char value, GlyphMap owner) {}
    private record DrawEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) {}
    private record GradientDrawEntry(float x, float r, float g, float b, float a, Glyph glyph) {}

    private class GlyphMap {
        private final Char2ObjectMap<Glyph> glyphs = new Char2ObjectArrayMap<>();
        private final Font               font;
        private final Identifier         bindToTexture;
        private final char               fromIncl, toExcl;
        private final int                pixelPadding;
        private final FontRenderContext  fontRenderContext;
        private int   width, height;
        float invWidth, invHeight;
        private boolean generated = false;

        GlyphMap(char from, char to, Font font, Identifier id, int padding, FontRenderContext frc) {
            this.fromIncl         = from;
            this.toExcl           = to;
            this.font             = font;
            this.bindToTexture    = id;
            this.pixelPadding     = padding;
            this.fontRenderContext = frc;
        }

        Glyph getGlyph(char c) { if (!generated) generate(); return glyphs.get(c); }
        boolean contains(char c) { return c >= fromIncl && c < toExcl; }

        void destroy() {
            mc.getTextureManager().destroyTexture(bindToTexture);
            glyphs.clear();
            width = -1; height = -1; generated = false;
        }

        private void generate() {
            if (generated) return;

            int range       = toExcl - fromIncl;
            int charsPerRow = (int) Math.ceil(Math.sqrt(range));
            int charsPerCol = (int) Math.ceil((double) range / charsPerRow);

            int maxCharWidth = 0, maxCharHeight = 0;
            CharMetrics[] metrics = new CharMetrics[range];
            for (int i = 0; i < range; i++) {
                char c = (char)(fromIncl + i);
                Rectangle2D bounds = font.getStringBounds(String.valueOf(c), fontRenderContext);
                int w = (int) Math.ceil(bounds.getWidth());
                int h = (int) Math.ceil(bounds.getHeight());
                maxCharWidth  = Math.max(maxCharWidth,  w);
                maxCharHeight = Math.max(maxCharHeight, h);
                metrics[i] = new CharMetrics(c, w, h);
            }

            this.width     = Math.max((maxCharWidth  + pixelPadding) * charsPerRow + pixelPadding, 1);
            this.height    = Math.max((maxCharHeight + pixelPadding) * charsPerCol + pixelPadding, 1);
            this.invWidth  = 1f / width;
            this.invHeight = 1f / height;

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, width, height);
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(Color.WHITE);
            g2d.setFont(font);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            FontMetrics fm        = g2d.getFontMetrics();
            int         baseAscent = fm.getAscent();

            for (int i = 0; i < metrics.length; i++) {
                CharMetrics cm  = metrics[i];
                int row = i / charsPerRow, col = i % charsPerRow;
                int px  = col * (maxCharWidth  + pixelPadding) + pixelPadding;
                int py  = row * (maxCharHeight + pixelPadding) + pixelPadding + baseAscent;
                glyphs.put(cm.character, new Glyph(px, py - baseAscent, cm.width, cm.height, cm.character, this));
                g2d.drawString(String.valueOf(cm.character), px, py);
            }
            g2d.dispose();

            try (NativeImage ni = new NativeImage(NativeImage.Format.RGBA, img.getWidth(), img.getHeight(), false)) {
                int[] pixels = new int[img.getWidth() * img.getHeight()];
                img.getRGB(0, 0, img.getWidth(), img.getHeight(), pixels, 0, img.getWidth());
                for (int i = 0; i < pixels.length; i++) {
                    int argb = pixels[i];
                    int a = (argb >>> 24), r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
                    ni.setColor(i % img.getWidth(), i / img.getWidth(), (a << 24) | (b << 16) | (g << 8) | r);
                }
                NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
                tex.upload();
                mc.getTextureManager().registerTexture(bindToTexture, tex);
            } catch (Throwable ignored) {}

            generated = true;
        }
    }

    private record CharMetrics(char character, int width, int height) {}
}

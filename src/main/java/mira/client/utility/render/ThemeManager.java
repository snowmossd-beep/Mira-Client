package mira.client.utility.render;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static mira.client.features.modules.Module.mc;
public class ThemeManager {
    public static final ThemeManager INSTANCE = new ThemeManager();
    public static class Style {
        public final String name;
        public final int[] colors;
        public Style(String name, int... colors) { this.name = name; this.colors = colors; }
    }
    private final List<Style> styles = new CopyOnWriteArrayList<>();
    private Style current;
    private Path file;
    public void init() {
        file = Paths.get(mc.runDirectory.getAbsolutePath(), "mira", "themes.dat");
        addBuiltin("Client",    "#4A9EFF", "#B8DCFF");
        addBuiltin("Autumn",    "#FF7D00", "#FFD700");
        addBuiltin("Acid",      "#CCFF00", "#00FF00");
        addBuiltin("Ocean",     "#0077BE", "#00B4D8");
        addBuiltin("Cherry",    "#8B0000", "#FF1493");
        addBuiltin("Sunset",    "#FF4500", "#FF8C00");
        addBuiltin("Lavender",  "#9B59B6", "#D7BDE2");
        addBuiltin("Mint",      "#00B894", "#55EFC4");
        addBuiltin("Rose",      "#E84393", "#FF7EB3");
        addBuiltin("Gold",      "#F9CA24", "#F0932B");
        addBuiltin("Arctic",    "#74B9FF", "#A29BFE");
        loadCustom();
        if (!styles.isEmpty()) current = styles.get(0);
    }
    private void addBuiltin(String name, String... hex) {
        int[] colors = Arrays.stream(hex).mapToInt(ThemeManager::hexToInt).toArray();
        styles.add(new Style(name, colors));
    }
    public static int hexToInt(String hex) {
        int rgb = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        return (255 << 24) | (rgb & 0xFFFFFF);
    }
    public void addCustom(String name, int c1, int c2) {
        Style s = new Style(name, c1, c2);
        styles.add(s);
        saveCustom();
    }
    public void removeStyle(Style s) {
        if (s == null || !s.name.startsWith("Custom")) return;
        styles.remove(s);
        if (current == s) current = styles.isEmpty() ? null : styles.get(0);
        saveCustom();
    }
    public void setTheme(Style s) { if (styles.contains(s)) current = s; }
    public Style getTheme()       { return current; }
    public List<Style> getStyles(){ return styles; }
    public int getFirstColor() {
        return current != null && current.colors.length > 0 ? current.colors[0] : 0xFF4A9EFF;
    }
    public int getSecondColor() {
        return current != null && current.colors.length > 1 ? current.colors[1] : getFirstColor();
    }
    public Color getColor(int index) {
        return new Color(gradient(5, index, getFirstColor(), getSecondColor()));
    }
    public Color getColorAlpha(int index, int alpha) {
        int g = gradient(5, index, getFirstColor(), getSecondColor());
        return new Color((g >> 16) & 0xFF, (g >> 8) & 0xFF, g & 0xFF, Math.max(0, Math.min(255, alpha)));
    }
    public static int gradient(int speed, int index, int... colors) {
        int angle = (int)((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int ci = (int)(angle / 360f * colors.length);
        if (ci >= colors.length) ci = colors.length - 1;
        int c1 = colors[ci];
        int c2 = colors[ci == colors.length - 1 ? 0 : ci + 1];
        return interpolate(c1, c2, angle / 360f * colors.length - ci);
    }
    public static int interpolate(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int)(((c1 >> 8)  & 0xFF) * (1 - t) + ((c2 >> 8)  & 0xFF) * t);
        int b = (int)(( c1        & 0xFF) * (1 - t) + ( c2        & 0xFF) * t);
        int a = (int)(((c1 >> 24) & 0xFF) * (1 - t) + ((c2 >> 24) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private void saveCustom() {
        if (file == null) file = Paths.get(mc.runDirectory.getAbsolutePath(), "mira", "themes.dat");
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (Style s : styles) {
                if (!s.name.startsWith("Custom")) continue;
                StringBuilder sb = new StringBuilder(s.name);
                for (int c : s.colors) sb.append(":").append(String.format("#%06X", c & 0xFFFFFF));
                lines.add(sb.toString());
            }
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) { System.err.println("ThemeManager save failed: " + e.getMessage()); }
    }
    private void loadCustom() {
        if (file == null) file = Paths.get(mc.runDirectory.getAbsolutePath(), "mira", "themes.dat");
        try {
            if (!Files.exists(file)) return;
            Files.lines(file, StandardCharsets.UTF_8).map(String::trim).filter(s -> !s.isEmpty()).forEach(line -> {
                try {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) addBuiltin(parts[0], parts[1], parts[2]);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) { System.err.println("ThemeManager load failed: " + e.getMessage()); }
    }
                   }

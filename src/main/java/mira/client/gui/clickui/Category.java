package mira.client.gui.clickui;
import net.minecraft.client.gui.DrawContext;
import mira.client.MiraClient;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.modules.Module;
import mira.client.features.modules.misc.BaritoneSettings;
import mira.client.features.modules.render.ClickGui;
import mira.client.gui.clickui.impl.SearchBar;
import mira.client.gui.font.FontRenderers;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.animation.AnimationUtility;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
public class Category extends AbstractCategory {
    private boolean scrollHover;
    private final List<AbstractButton> buttons;
    public float catHeight;
    private static final Color PANEL_BG    = new Color(14, 14, 20, 248);
    private static final Color HEADER_BG   = new Color(20, 20, 28, 252);
    private static final Color HEADER_TEXT = new Color(195, 195, 210, 255);
    private static final Color SEPARATOR   = new Color(40, 40, 55, 160);
    public Category(Module.Category category, ArrayList<Module> features, float x, float y, float width, float height) {
        super(category.getName(), x, y, width, height);
        buttons = new ArrayList<>();
        features.forEach(f -> {
            if (!(f instanceof BaritoneSettings) || MiraClient.baritone)
                buttons.add(new ModuleButton(f));
        });
    }
    @Override
    public void init() {
        buttons.forEach(AbstractButton::init);
    }
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        setWidth(ModuleManager.clickGui.moduleWidth.getValue());
        scrollHover = Render2DEngine.isHovered(mx, my, getX(), getY() + height, width, catHeight + 20);
        float targetH;
        double bh = getButtonsHeight();
        if (ModuleManager.clickGui.scrollMode.getValue() == ClickGui.scrollModeEn.Old
                || bh < ModuleManager.clickGui.catHeight.getValue())
            targetH = (float) bh;
        else
            targetH = ModuleManager.clickGui.catHeight.getValue();
        catHeight = AnimationUtility.fast(catHeight, targetH, 30f);
        ctx.getMatrices().push();
        float hx = getX();
        float hy = getY() - 5;
        float hw = width;
        float hh = height;
        Render2DEngine.drawRound(ctx.getMatrices(), hx, hy, hw, hh + (isOpen() ? 1 : 0), 6f, HEADER_BG);
        String label = getName().toUpperCase();
        float lx = hx + hw / 2f - FontRenderers.sf_medium.getStringWidth(label) / 2f;
        float ly = hy + hh / 2f - 4f;
        FontRenderers.sf_medium.drawString(ctx.getMatrices(), label, lx, ly, HEADER_TEXT.getRGB());
        if (isOpen()) {
            Render2DEngine.drawRect(ctx.getMatrices(), hx + 5, hy + hh - 0.5f, hw - 10, 1f, SEPARATOR);
            Render2DEngine.drawRound(ctx.getMatrices(), hx, hy + hh, hw, catHeight + 4, 6f, PANEL_BG);
            boolean pop = false;
            if (!(ModuleManager.clickGui.scrollMode.getValue() == ClickGui.scrollModeEn.Old
                    || getButtonsHeight() < ModuleManager.clickGui.catHeight.getValue())) {
                Render2DEngine.addWindow(ctx.getMatrices(), hx, hy + hh, hx + hw, hy + hh + catHeight + 4, 1f);
                pop = true;
            }
            String filter2 = SearchBar.listening ? SearchBar.moduleName.toLowerCase() : null;
            float modH = ModuleManager.clickGui.moduleHeight.getValue();
            float baseY = getY() + height;
            for (AbstractButton btn : buttons) {
                if (filter2 != null && btn instanceof ModuleButton mb
                        && !mb.module.getName().toLowerCase().contains(filter2))
                    continue;
                if (pop && buttons.getFirst().getY() + moduleOffset < baseY) {
                    btn.setY(baseY + moduleOffset);
                } else {
                    btn.setY(baseY);
                    moduleOffset = 0f;
                }
                btn.setX(getX());
                btn.setWidth(width);
                btn.setHeight(modH);
                btn.render(ctx, mx, my, delta);
            }
            if (pop) Render2DEngine.popWindow();
        }
        ctx.getMatrices().pop();
        updatePosition();
    }
    @Override
    public void mouseClicked(int mx, int my, int btn) {
        if (btn == 1 && hovered) setOpen(!isOpen());
        super.mouseClicked(mx, my, btn);
        if (isOpen() && scrollHover)
            buttons.forEach(b -> b.mouseClicked(mx, my, btn));
    }
    @Override
    public void mouseReleased(int mx, int my, int btn) {
        super.mouseReleased(mx, my, btn);
        if (isOpen()) buttons.forEach(b -> b.mouseReleased(mx, my, btn));
    }
    @Override
    public boolean keyTyped(int keyCode) {
        if (isOpen()) buttons.forEach(b -> b.keyTyped(keyCode));
        return false;
    }
    @Override
    public void charTyped(char key, int keyCode) {
        if (isOpen()) buttons.forEach(b -> b.charTyped(key, keyCode));
    }
    @Override
    public void onClose() {
        super.onClose();
        buttons.forEach(AbstractButton::onGuiClosed);
    }
    @Override
    public void tick() {
        buttons.forEach(AbstractButton::tick);
    }
    private void updatePosition() {
        float off = 0;
        String filter = SearchBar.listening ? SearchBar.moduleName.toLowerCase() : null;
        for (AbstractButton btn : buttons) {
            if (filter != null && btn instanceof ModuleButton mb
                    && !mb.module.getName().toLowerCase().contains(filter))
                continue;
            btn.setTargetOffset(off);
            if (btn instanceof ModuleButton mb2 && mb2.isOpen()) {
                for (AbstractElement el : mb2.getElements())
                    if (el.isVisible()) off += el.getHeight();
                off += 2f;
            }
            off += btn.getHeight();
        }
    }
    @Override
    public void hudClicked(Module module) {
        for (AbstractButton btn : buttons)
            if (btn instanceof ModuleButton mb && mb.module == module)
                mb.setOpen(true);
    }
    public double getButtonsHeight() {
        double h = 8;
        String filter = SearchBar.listening ? SearchBar.moduleName.toLowerCase() : null;
        for (AbstractButton btn : buttons) {
            if (filter != null && btn instanceof ModuleButton mb
                    && !mb.module.getName().toLowerCase().contains(filter))
                continue;
            if (btn instanceof ModuleButton mb2) {
                if (mb2.isOpen()) h += 2f;
                h += mb2.getElementsHeight();
            }
            h += btn.getHeight();
        }
        return h;
    }
}

package mira.client.gui.clickui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.cmd.Command;
import mira.client.features.hud.impl.TargetHud;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.ClickGui;
import mira.client.features.modules.render.HudEditor;
import mira.client.features.modules.render.HudEditor;
import mira.client.gui.clickui.impl.*;
import mira.client.gui.font.FontRenderers;
import mira.client.gui.misc.DialogScreen;
import mira.client.setting.Setting;
import mira.client.setting.impl.*;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.TextureStorage;
import mira.client.utility.render.animation.AnimationUtility;
import mira.client.utility.render.animation.GearAnimation;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static mira.client.features.modules.Module.mc;
import static mira.client.features.modules.render.ClientSettings.isRu;
import static mira.client.utility.render.animation.AnimationUtility.fast;
public class ModuleButton extends AbstractButton {
    private final List<AbstractElement> elements;

    public final Module module;
    private boolean open;
    private boolean hovered, prevHovered;
    private float animation, animation2;
    float category_animation = 0f;
    int ticksOpened;
    private final GearAnimation gearAnimation = new GearAnimation();
    private boolean binding  = false;
    private boolean holdbind = false;
    private static final Color ROW_BG_HOVER   = new Color(255, 255, 255, 10);
    private static final Color ROW_BG_ENABLED = new Color(255, 255, 255, 5);
    private static final Color NAME_ENABLED   = new Color(225, 225, 238, 255);
    private static final Color NAME_DISABLED  = new Color(90, 90, 108, 255);
    private static final Color BIND_TEXT      = new Color(65, 65, 85, 200);
    private static final Color ROW_DIVIDER    = new Color(28, 28, 40, 140);
    private static final Color TRACK_OFF      = new Color(35, 35, 48, 230);
    private static final Color KNOB_WHITE     = new Color(240, 240, 245, 255);
    public ModuleButton(Module module) {
        this.module = module;
        elements = new ArrayList<>();
        for (Setting<?> s : module.getSettings()) {
            if (s.getValue() instanceof Boolean && !s.getName().equals("Enabled") && !s.getName().equals("Drawn")) {
                elements.add(new BooleanElement(s));
            } else if (s.getValue() instanceof ColorSetting) {
                elements.add(new ColorPickerElement(s));
            } else if (s.getValue() instanceof BooleanSettingGroup) {
                elements.add(new BooleanParentElement(s));
            } else if (s.isNumberSetting() && s.hasRestriction()) {
                elements.add(new SliderElement(s));
            } else if (s.getValue() instanceof ItemSelectSetting) {
                elements.add(new ItemSelectElement(s));
            } else if (s.getValue() instanceof SettingGroup) {
                elements.add(new ParentElement(s));
            } else if (s.isEnumSetting() && !(s.getValue() instanceof PositionSetting)) {
                elements.add(new ModeElement(s));
            } else if (s.getValue() instanceof Bind && !s.getName().equals("Keybind")) {
                elements.add(new BindElement(s));
            } else if ((s.getValue() instanceof String || s.getValue() instanceof Character)
                    && !s.getName().equalsIgnoreCase("displayName")) {
                elements.add(new StringElement(s));
            }
        }
    }
    public void init() { elements.forEach(AbstractElement::init); }
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hovered   = Render2DEngine.isHovered(mx, my, x, y, width, height);
        animation  = fast(animation,  module.isEnabled() ? 1f : 0f, 9f);
        animation2 = fast(animation2, 1f, 10f);
        if (hovered) {
            if (!prevHovered) Managers.SOUND.playScroll();
            ClickGUI.currentDescription = I18n.translate(module.getDescription());
        }
        prevHovered = hovered;
        offsetY = AnimationUtility.fast(offsetY, target_offset, 20f);
        if (hovered || module.isEnabled()) {
            Color rowCol = hovered ? ROW_BG_HOVER : ROW_BG_ENABLED;
            Render2DEngine.drawRect(ctx.getMatrices(), x + 2, y + 0.5f, width - 4, height - 1f, rowCol);
        }
        Render2DEngine.drawRect(ctx.getMatrices(), x + 6, y + height - 0.5f, width - 12, 0.5f, ROW_DIVIDER);
        float trackW = 24f;
        float trackH = 11f;
        float trackX = x + width - trackW - 7;
        float trackY = y + height / 2f - trackH / 2f;
        Color raw = HudEditor.getColor(0);
        Color accentOn = (raw.getAlpha() < 10 || (raw.getRed() < 5 && raw.getGreen() < 5 && raw.getBlue() < 5))
                ? new Color(200, 80, 80, 255) : raw;
        int tr = (int)(TRACK_OFF.getRed()   + (accentOn.getRed()   - TRACK_OFF.getRed())   * animation);
        int tg = (int)(TRACK_OFF.getGreen() + (accentOn.getGreen() - TRACK_OFF.getGreen()) * animation);
        int tb = (int)(TRACK_OFF.getBlue()  + (accentOn.getBlue()  - TRACK_OFF.getBlue())  * animation);
        Render2DEngine.drawRound(ctx.getMatrices(), trackX, trackY, trackW, trackH, 6f, new Color(tr, tg, tb, 230));
        float knobSize   = trackH - 4f;
        float knobTravel = trackW - knobSize - 4f;
        float knobX      = trackX + 2f + knobTravel * animation;
        float knobY      = trackY + 2f;
        Render2DEngine.drawRound(ctx.getMatrices(), knobX, knobY, knobSize, knobSize, 5f, KNOB_WHITE);
        float nameX = x + 8;
        float nameY = y + height / 2f - 4f;
        int nameCol = animation > 0.5f ? NAME_ENABLED.getRGB() : NAME_DISABLED.getRGB();
        if (hovered && InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_SHIFT)) {
            FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(),
                    "Drawn " + (module.isDrawn() ? Formatting.GREEN + "TRUE" : Formatting.RED + "FALSE"),
                    nameX, nameY, nameCol);
        } else if (binding) {
            FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(),
                    holdbind ? (Formatting.GRAY + "Toggle / " + Formatting.RESET + "Hold")
                             : (Formatting.RESET + "Toggle " + Formatting.GRAY + "/ Hold"),
                    nameX, nameY,
                    Render2DEngine.applyOpacity(Color.WHITE.getRGB(), animation2));
        } else {
            FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(), module.getName(), nameX, nameY, nameCol);
        }
        if (!module.getBind().getBind().equalsIgnoreCase("none") && !binding) {
            String sb = formatBind(module.getBind().getBind());
            float bx  = trackX - FontRenderers.sf_medium_modules.getStringWidth(sb) - 5;
            FontRenderers.sf_medium_modules.drawString(ctx.getMatrices(), sb, bx, nameY, BIND_TEXT.getRGB());
        }
        if (elements.size() > 0 && isOpen()) {
            float subOffY  = 0;
            float subBaseY = y + height + 2;
            Render2DEngine.addWindow(ctx.getMatrices(),
                    new Render2DEngine.Rectangle(x + 1, y + height - 2, x + width - 2,
                            (float)(y + height + 1f + getElementsHeight())));
            for (AbstractElement el : elements) {
                if (!el.isVisible()) continue;
                el.setOffsetY(subOffY);
                el.setX(x);
                el.setY(subBaseY);
                el.setWidth(width);
                el.setHeight(13);
                if (el instanceof ColorPickerElement picker) el.setHeight(picker.getHeight());
                else if (el instanceof SliderElement)       el.setHeight(18);
                if (el instanceof ModeElement me) {
                    me.setWHeight(13);
                    el.setHeight(me.isOpen() ? 13 + me.getSetting().getModes().length * 12 : 13);
                }
                subOffY += el.getHeight();
            }
            ctx.getMatrices().push();
            TargetHud.sizeAnimation(ctx.getMatrices(), x + width / 2f + 6, y + height / 2f - 12,
                    ticksOpened < 5 ? Math.clamp(category_animation / Math.max(subOffY, 1f), 0f, 1f) : 1f);
            elements.forEach(e -> { if (e.isVisible()) e.render(ctx, mx, my, delta); });
            ctx.getMatrices().pop();
            Render2DEngine.popWindow();
            category_animation = fast(category_animation, subOffY, 20f);
        } else {
            category_animation = fast(category_animation, 0f, 20f);
        }
        if (hovered && GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND)
            GLFW.glfwSetCursor(mc.getWindow().getHandle(),
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
    }
    private float computeRawElementsH() {
        float h = 0;
        for (AbstractElement el : elements) {
            if (!el.isVisible()) continue;
            if (el instanceof ColorPickerElement picker) {
                h += picker.getHeight();
            } else if (el instanceof SliderElement) {
                h += 18;
            } else if (el instanceof ModeElement me) {
                h += me.isOpen() ? 13 + me.getSetting().getModes().length * 12 : 13;
            } else {
                h += 13;
            }
        }
        return h;
    }
    @NotNull
    private String formatBind(String s) {
        return switch (s) {
            case "LEFT_CONTROL"  -> "LCtrl";
            case "RIGHT_CONTROL" -> "RCtrl";
            case "LEFT_SHIFT"    -> "LShift";
            case "RIGHT_SHIFT"   -> "RShift";
            case "LEFT_ALT"      -> "LAlt";
            case "RIGHT_ALT"     -> "RAlt";
            default              -> s;
        };
    }
    public void mouseClicked(int mx, int my, int btn) {
        if (binding) {
            if (mx > x + 56 && mx < x + 67 && my > y && my < y + height) { holdbind = false; module.getBind().setHold(false); return; }
            if (mx > x + 78 && mx < x + 88 && my > y && my < y + height) { holdbind = true;  module.getBind().setHold(true);  return; }
            module.setBind(btn, true, holdbind);
            binding = false;
        }
        if (hovered) {
            if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.GLFW_KEY_LEFT_SHIFT) && btn == 0) {
                module.setDrawn(!module.isDrawn()); return;
            }
            if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.GLFW_KEY_DELETE) && btn == 0) {
                DialogScreen dlg = new DialogScreen(TextureStorage.questionPic,
                        isRu() ? "Сброс модуля" : "Reset module",
                        isRu() ? "Ты действительно хочешь сбросить " + module.getName() + "?" : "Are you sure you want to reset " + module.getName() + "?",
                        isRu() ? "Да" : "Yes", isRu() ? "Нет" : "No",
                        () -> {
                            if (module.isEnabled()) module.disable("reseting");
                            for (Setting s : module.getSettings()) {
                                if (s.getValue() instanceof ColorSetting cs) cs.setDefault();
                                else s.setValue(s.getDefaultValue());
                            }
                            mc.setScreen(null);
                        }, () -> mc.setScreen(null));
                mc.setScreen(dlg);
            }
            if (btn == 0) {
                if (module.isToggleable()) module.toggle();
            } else if (btn == 1 && elements.size() > 0) {
                setOpen(!open);
                if (open) Managers.SOUND.playSwipeIn(); else Managers.SOUND.playSwipeOut();
                animation = 0.5f;
            } else if (btn == 2) {
                animation2 = 0;
                binding = !binding;
            }
        }
        if (open) elements.forEach(el -> { if (el.isVisible()) el.mouseClicked(mx, my, btn); });
    }
    public void mouseReleased(int mx, int my, int btn) {
        if (isOpen()) elements.forEach(el -> el.mouseReleased(mx, my, btn));
    }
    public void charTyped(char key, int keyCode) {
        if (isOpen()) elements.forEach(el -> el.charTyped(key, keyCode));
    }
    public void keyTyped(int keyCode) {
        if (isOpen()) elements.forEach(el -> el.keyTyped(keyCode));
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                module.setBind(-1, false, holdbind);
                Command.sendMessage((isRu() ? "Удален бинд с модуля " : "Removed bind from ") + module.getName());
            } else {
                module.setBind(keyCode, false, holdbind);
                Command.sendMessage(module.getName() + (isRu() ? " бинд изменен на " : " bind changed to ") + module.getBind().getBind());
            }
            binding = false;
        }
    }
    public void onGuiClosed() { elements.forEach(AbstractElement::onClose); }
    public List<AbstractElement> getElements() { return elements; }
    public double getElementsHeight() { return category_animation; }
    public boolean isOpen()           { return open; }
    public void    setOpen(boolean o) {
        this.open = o;

    }
    public void tick() {
        if (isOpen()) { gearAnimation.tick(); ticksOpened++; }
        else            ticksOpened = 0;
    }
}
    
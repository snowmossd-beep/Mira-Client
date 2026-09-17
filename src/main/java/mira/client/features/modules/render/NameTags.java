package mira.client.features.modules.render;

import com.google.common.collect.Ordering;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector4d;
import mira.client.core.Managers;
import mira.client.core.manager.player.FriendManager;
import mira.client.core.manager.client.ModuleManager;
import mira.client.gui.font.FontRenderers;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.HudEditor;
import mira.client.features.modules.misc.NameProtect;
import mira.client.setting.Setting;
import mira.client.setting.impl.ColorSetting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.Render3DEngine;
import mira.client.utility.render.TextureStorage;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class NameTags extends Module {

    private final Map<RegistryKey<Enchantment>, String> encMap = new HashMap<>();

    public NameTags() {
        super("NameTags", Category.RENDER);
        encMap.put(Enchantments.BLAST_PROTECTION, "B");
        encMap.put(Enchantments.PROTECTION,       "P");
        encMap.put(Enchantments.SHARPNESS,        "S");
        encMap.put(Enchantments.EFFICIENCY,       "E");
        encMap.put(Enchantments.UNBREAKING,       "U");
        encMap.put(Enchantments.POWER,            "PO");
        encMap.put(Enchantments.THORNS,           "T");
    }

    private final Setting<Boolean> self         = new Setting<>("Self",          false);
    private final Setting<Float>   scale        = new Setting<>("Scale",         1f, 0.1f, 10f);
    private final Setting<Boolean> resize       = new Setting<>("Resize",        false);
    private final Setting<Float>   height       = new Setting<>("Height",        2f, 0.1f, 10f);
    private final Setting<Boolean> showPing     = new Setting<>("Ping",          true);
    private final Setting<Boolean> showHp       = new Setting<>("HP",            true);
    private final Setting<Boolean> showDist     = new Setting<>("Distance",      true);
    private final Setting<Boolean> showGamemode = new Setting<>("Gamemode",      false);
    private final Setting<Boolean> showArmor    = new Setting<>("Armor",         true);
    private final Setting<Boolean> showEnchants = new Setting<>("Enchants",      true);
    private final Setting<Boolean> showLogo     = new Setting<>("ClientLogo",    true);
    private final Setting<ColorSetting> bgColor = new Setting<>("Background",    new ColorSetting(0x80000000));
    private final Setting<ColorSetting> outlineColor = new Setting<>("Outline",  new ColorSetting(0x80000000));

    public void onRender2D(DrawContext ctx) {
        if (mc.options.hudHidden) return;

        for (PlayerEntity ent : mc.world.getPlayers()) {
            if (ent == mc.player && (mc.options.getPerspective().isFirstPerson() || !self.getValue())) continue;

            double ex = ent.prevX + (ent.getX() - ent.prevX) * Render3DEngine.getTickDelta();
            double ey = ent.prevY + (ent.getY() - ent.prevY) * Render3DEngine.getTickDelta();
            double ez = ent.prevZ + (ent.getZ() - ent.prevZ) * Render3DEngine.getTickDelta();

            float sc = resize.getValue()
                    ? scale.getValue() / mc.player.distanceTo(ent)
                    : scale.getValue();

            Vec3d vec = Render3DEngine.worldSpaceToScreenSpace(new Vec3d(ex, ey + height.getValue(), ez));
            if (vec.z <= 0 || vec.z >= 1) continue;

            Vector4d pos = new Vector4d(vec.x, vec.y, vec.x, vec.y);

            String nameStr = "";
            if (FriendManager.friends.stream().anyMatch(i -> i.contains(ent.getDisplayName().getString()))
                    && NameProtect.hideFriends.getValue() && ModuleManager.nameProtect.isEnabled()) {
                nameStr = NameProtect.getCustomName();
            } else {
                nameStr = ent.getDisplayName().getString();
            }

            String label = "";
            if (showPing.getValue())     label += getPingColor(getEntityPing(ent)) + getEntityPing(ent) + "ms " + Formatting.WHITE;
            if (showGamemode.getValue()) label += translateGamemode(getEntityGamemode(ent)) + " ";
            label += nameStr + " ";
            if (showHp.getValue())       label += getHealthColor(ent.getHealth() + ent.getAbsorptionAmount()) + round2(ent.getHealth() + ent.getAbsorptionAmount()) + " ";
            if (showDist.getValue())     label += Formatting.GRAY + String.format("%.1f", mc.player.distanceTo(ent)) + "m";

            float textW = FontRenderers.sf_bold.getStringWidth(label);
            float tagX  = (float)(pos.x - textW / 2f);
            float tagY  = (float) pos.y;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(tagX - 2 + (textW + 4) / 2f, tagY - 13f + 6.5f, 0);
            ctx.getMatrices().scale(sc, sc, 1f);
            ctx.getMatrices().translate(-(tagX - 2 + (textW + 4) / 2f), -(tagY - 13f + 6.5f), 0);

            if (showArmor.getValue()) {
                ArrayList<ItemStack> stacks = new ArrayList<>();
                stacks.add(ent.getOffHandStack());
                stacks.add(ent.getInventory().armor.get(0));
                stacks.add(ent.getInventory().armor.get(1));
                stacks.add(ent.getInventory().armor.get(2));
                stacks.add(ent.getInventory().armor.get(3));
                stacks.add(ent.getMainHandStack());

                float itemOff = 0;
                float enchY   = 0;
                for (ItemStack stack : stacks) {
                    if (stack.isEmpty()) { itemOff += 18f; continue; }

                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(tagX - 55 + itemOff, tagY - 33f, 0);
                    ctx.getMatrices().scale(1.1f, 1.1f, 1.1f);
                    DiffuseLighting.disableGuiDepthLighting();
                    ctx.drawItem(stack, 0, 0);
                    ctx.drawItemInSlot(mc.textRenderer, stack, 0, 0);
                    ctx.getMatrices().pop();

                    if (showEnchants.getValue()) {
                        var enchants = EnchantmentHelper.getEnchantments(stack);
                        float eY = 0;
                        for (RegistryKey<Enchantment> key : encMap.keySet()) {
                            var entryOpt = mc.world.getRegistryManager()
                                    .get(Enchantments.PROTECTION.getRegistryRef()).getEntry(key);
                            if (entryOpt.isEmpty()) continue;
                            if (enchants.getEnchantments().contains(entryOpt.get())) {
                                int lvl = enchants.getLevel(entryOpt.get());
                                FontRenderers.sf_bold.drawString(ctx.getMatrices(),
                                        encMap.get(key) + lvl,
                                        tagX - 50 + itemOff, tagY - 45 + eY, -1);
                                eY -= 8;
                            }
                        }
                    }
                    itemOff += 18f;
                }
            }

            Render2DEngine.drawRectWithOutline(ctx.getMatrices(),
                    tagX - 2, tagY - 13f, textW + 4, 11,
                    bgColor.getValue().getColorObject(),
                    outlineColor.getValue().getColorObject());

            if (showLogo.getValue() && Managers.TELEMETRY.getOnlinePlayers().contains(ent.getGameProfile().getName())) {
                Render2DEngine.drawRect(ctx.getMatrices(), tagX - 14, tagY - 13f, 12, 11,
                        bgColor.getValue().getColorObject().brighter().brighter());
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                Color lc = HudEditor.getColor(0);
                RenderSystem.setShaderColor(lc.getRed() / 255f, lc.getGreen() / 255f, lc.getBlue() / 255f, 1f);
                RenderSystem.setShaderTexture(0, TextureStorage.miniLogo);
                Render2DEngine.renderTexture(ctx.getMatrices(), tagX - 13, tagY - 12.5f, 10, 10, 0, 0, 256, 256, 256, 256);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }

            FontRenderers.sf_bold.drawString(ctx.getMatrices(), label, tagX, tagY - 10, -1);

            ctx.getMatrices().pop();
        }
    }

    public static int getEntityPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) return 0;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    public static GameMode getEntityGamemode(PlayerEntity entity) {
        if (entity == null || mc.getNetworkHandler() == null) return null;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return entry == null ? null : entry.getGameMode();
    }

    private String translateGamemode(GameMode gm) {
        if (gm == null) return "[BOT]";
        return switch (gm) {
            case SURVIVAL   -> "[S]";
            case CREATIVE   -> "[C]";
            case SPECTATOR  -> "[SP]";
            case ADVENTURE  -> "[A]";
        };
    }

    public @NotNull String getHealthColor(float hp) {
        if (hp > 15)            return Formatting.GREEN + "";
        if (hp > 7)             return Formatting.YELLOW + "";
        return Formatting.RED + "";
    }

    public @NotNull String getPingColor(int ping) {
        if (ping <= 60)         return Formatting.GREEN + "";
        if (ping < 120)         return Formatting.YELLOW + "";
        return Formatting.RED + "";
    }

    public static float round2(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).floatValue();
    }
}

package mira.client.features.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.ThemeManager;

import java.awt.*;

public class Hat extends Module {
    public Hat() {
        super("Hat", Category.RENDER);
    }

    private enum Style { CAP, CROWN, HALO }

    private final Setting<Style> style = new Setting<>("Style", Style.CAP);
    private final Setting<Float> size = new Setting<>("Size", 1.0f, 0.5f, 2.0f);
    private final Setting<Float> offsetY = new Setting<>("OffsetY", 0.08f, -0.3f, 0.6f);
    private final Setting<Boolean> useThemeColor = new Setting<>("UseThemeColor", true);
    private final Setting<Boolean> hideSelf = new Setting<>("HideSelf", false);
    private final Setting<Boolean> showOthers = new Setting<>("ShowOthers", true);
    private final Setting<Boolean> spin = new Setting<>("Spin", false, v -> style.is(Style.HALO));
    private final Setting<Float> spinSpeed = new Setting<>("SpinSpeed", 1.0f, 0.2f, 3.0f, v -> style.is(Style.HALO) && spin.getValue());

    private float rotation = 0f;

    @Override
    public void onRender3D(MatrixStack stack) {
        if (fullNullCheck()) return;

        for (PlayerEntity entity : mc.world.getPlayers()) {
            boolean isSelf = entity == mc.player;
            if (isSelf && hideSelf.getValue()) continue;
            if (!isSelf && !showOthers.getValue()) continue;

            double camX = mc.getEntityRenderDispatcher().camera.getPos().getX();
            double camY = mc.getEntityRenderDispatcher().camera.getPos().getY();
            double camZ = mc.getEntityRenderDispatcher().camera.getPos().getZ();

            float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
            double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta - camX;
            double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta - camY;
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta - camZ;

            float headYaw = entity.prevHeadYaw + (entity.headYaw - entity.prevHeadYaw) * tickDelta;
            float headY = (float) y + entity.getHeight() + offsetY.getValue();

            Color col = resolveColor();

            switch ((Style) style.getValue()) {
                case CAP -> renderCap(stack, (float) x, headY, (float) z, headYaw, col);
                case CROWN -> renderCrown(stack, (float) x, headY, (float) z, headYaw, col);
                case HALO -> renderHalo(stack, (float) x, headY, (float) z, headYaw, col);
            }
        }

        if (style.is(Style.HALO) && spin.getValue()) {
            rotation += 0.05f * spinSpeed.getValue();
        }
    }

    private Color resolveColor() {
        if (!useThemeColor.getValue()) {
            return switch ((Style) style.getValue()) {
                case CAP -> new Color(58, 107, 156);
                case CROWN -> new Color(232, 184, 75);
                case HALO -> new Color(240, 216, 120);
            };
        }
        int c1 = ThemeManager.INSTANCE.getFirstColor();
        int c2 = ThemeManager.INSTANCE.getSecondColor();
        int grad = ThemeManager.gradient(5, 0, c1, c2);
        return new Color((grad >> 16) & 0xFF, (grad >> 8) & 0xFF, grad & 0xFF);
    }

    private void beginTris() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }

    private void endTris() {
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderCap(MatrixStack stack, float x, float y, float z, float yaw, Color col) {
        float s = size.getValue() * 0.32f;
        int rgb = col.getRGB();
        int shadeRgb = col.darker().getRGB();

        beginTris();
        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        Matrix4f mat = stack.peek().getPositionMatrix();

        int segs = 12;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2 / segs);
            float x0 = (float) Math.cos(a0) * s, z0 = (float) Math.sin(a0) * s;
            float x1 = (float) Math.cos(a1) * s, z1 = (float) Math.sin(a1) * s;

            buf.vertex(mat, 0f, s * 0.9f, 0f).color(rgb);
            buf.vertex(mat, x0, 0f, z0).color(shadeRgb);
            buf.vertex(mat, x1, 0f, z1).color(shadeRgb);

            float bs = s * 1.35f;
            float bx0 = (float) Math.cos(a0) * bs, bz0 = (float) Math.sin(a0) * bs;
            float bx1 = (float) Math.cos(a1) * bs, bz1 = (float) Math.sin(a1) * bs;
            buf.vertex(mat, 0f, -s * 0.05f, 0f).color(shadeRgb);
            buf.vertex(mat, bx0, -s * 0.05f, bz0).color(shadeRgb);
            buf.vertex(mat, bx1, -s * 0.05f, bz1).color(shadeRgb);
        }

        Render2DEngine.endBuilding(buf);
        stack.pop();
        endTris();
    }

    private void renderCrown(MatrixStack stack, float x, float y, float z, float yaw, Color col) {
        float s = size.getValue() * 0.3f;
        int gold = col.getRGB();
        int darkGold = col.darker().getRGB();
        int jewel = new Color(200, 55, 79).getRGB();

        beginTris();
        stack.push();
        stack.translate(x, y, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        Matrix4f mat = stack.peek().getPositionMatrix();

        int spikes = 6;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < spikes; i++) {
            float a0 = (float) (i * Math.PI * 2 / spikes);
            float a1 = (float) ((i + 1) * Math.PI * 2 / spikes);
            float amid = (a0 + a1) / 2f;

            float x0 = (float) Math.cos(a0) * s, z0 = (float) Math.sin(a0) * s;
            float x1 = (float) Math.cos(a1) * s, z1 = (float) Math.sin(a1) * s;

            buf.vertex(mat, x0, 0f, z0).color(darkGold);
            buf.vertex(mat, x1, 0f, z1).color(darkGold);
            buf.vertex(mat, 0f, 0f, 0f).color(darkGold);

            float peakX = (float) Math.cos(amid) * s * 0.5f;
            float peakZ = (float) Math.sin(amid) * s * 0.5f;
            buf.vertex(mat, x0, 0f, z0).color(gold);
            buf.vertex(mat, peakX, s * 0.7f, peakZ).color(gold);
            buf.vertex(mat, x1, 0f, z1).color(gold);

            float jx = (float) Math.cos(amid) * s * 0.6f;
            float jz = (float) Math.sin(amid) * s * 0.6f;
            float jr = s * 0.08f;
            buf.vertex(mat, jx - jr, s * 0.35f, jz).color(jewel);
            buf.vertex(mat, jx + jr, s * 0.35f, jz).color(jewel);
            buf.vertex(mat, jx, s * 0.5f, jz).color(jewel);
        }

        Render2DEngine.endBuilding(buf);
        stack.pop();
        endTris();
    }

    private void renderHalo(MatrixStack stack, float x, float y, float z, float yaw, Color col) {
        float s = size.getValue() * 0.4f;
        int rgb = col.getRGB();
        float ringOffsetY = s * 0.6f;

        beginTris();
        stack.push();
        stack.translate(x, y + ringOffsetY, z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw - (spin.getValue() ? rotation * 57.3f : 0f)));
        Matrix4f mat = stack.peek().getPositionMatrix();

        int segs = 24;
        float outer = s;
        float inner = s * 0.82f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2 / segs);

            float ox0 = (float) Math.cos(a0) * outer, oz0 = (float) Math.sin(a0) * outer * 0.35f;
            float ox1 = (float) Math.cos(a1) * outer, oz1 = (float) Math.sin(a1) * outer * 0.35f;
            float ix0 = (float) Math.cos(a0) * inner, iz0 = (float) Math.sin(a0) * inner * 0.35f;
            float ix1 = (float) Math.cos(a1) * inner, iz1 = (float) Math.sin(a1) * inner * 0.35f;

            buf.vertex(mat, ox0, 0f, oz0).color(rgb);
            buf.vertex(mat, ix0, 0f, iz0).color(rgb);
            buf.vertex(mat, ix1, 0f, iz1).color(rgb);

            buf.vertex(mat, ox0, 0f, oz0).color(rgb);
            buf.vertex(mat, ix1, 0f, iz1).color(rgb);
            buf.vertex(mat, ox1, 0f, oz1).color(rgb);
        }

        Render2DEngine.endBuilding(buf);
        stack.pop();
        endTris();
    }
}

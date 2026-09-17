package mira.client.utility.render.shaders.satin.api.managed;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;

public interface ManagedFramebuffer {
    Framebuffer getFramebuffer();

    void beginWrite(boolean updateViewport);

    void draw();

    void draw(int width, int height, boolean disableBlend);

    void clear();

    void clear(boolean swallowErrors);
}

package mira.client.utility.render.shaders.satin.api.managed;

import mira.client.utility.render.shaders.satin.api.managed.uniform.UniformFinder;
import net.minecraft.client.gl.ShaderProgram;

public interface ManagedCoreShader extends UniformFinder {
    ShaderProgram getProgram();

    void release();
}

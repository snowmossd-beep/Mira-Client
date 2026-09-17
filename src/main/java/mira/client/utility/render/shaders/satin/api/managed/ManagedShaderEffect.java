package mira.client.utility.render.shaders.satin.api.managed;

import mira.client.utility.render.shaders.satin.api.managed.uniform.SamplerUniformV2;
import mira.client.utility.render.shaders.satin.api.managed.uniform.UniformFinder;
import net.minecraft.client.gl.PostEffectProcessor;

public interface ManagedShaderEffect extends UniformFinder {

    PostEffectProcessor getShaderEffect();

    void release();

    void render(float tickDelta);

    ManagedFramebuffer getTarget(String name);

    void setUniformValue(String uniformName, int value);

    void setUniformValue(String uniformName, float value);

    void setUniformValue(String uniformName, float value0, float value1);

    void setUniformValue(String uniformName, float value0, float value1, float value2);

    void setUniformValue(String uniformName, float value0, float value1, float value2, float value3);

    @Override
    SamplerUniformV2 findSampler(String samplerName);
}

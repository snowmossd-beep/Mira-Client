package mira.client.utility.render.shaders.satin.api.managed;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;
import mira.client.utility.render.shaders.satin.impl.ReloadableShaderEffectManager;

import java.util.function.Consumer;

public interface ShaderEffectManager {
    static ShaderEffectManager getInstance() {
        return ReloadableShaderEffectManager.INSTANCE;
    }

    ManagedShaderEffect manage(Identifier location);

    ManagedShaderEffect manage(Identifier location, Consumer<ManagedShaderEffect> initCallback);

    ManagedCoreShader manageCoreShader(Identifier location);

    ManagedCoreShader manageCoreShader(Identifier location, VertexFormat vertexFormat);

    ManagedCoreShader manageCoreShader(Identifier location, VertexFormat vertexFormat, Consumer<ManagedCoreShader> initCallback);
}

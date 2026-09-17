package mira.client.utility.render.shaders.satin.api.managed.uniform;

import org.joml.Vector3f;

public interface Uniform3f {

    void set(float value0, float value1, float value2);

    void set(Vector3f value);
}

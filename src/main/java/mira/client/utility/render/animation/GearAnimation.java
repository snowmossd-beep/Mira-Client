package mira.client.utility.render.animation;

import mira.client.features.modules.render.ClickGui;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.Render3DEngine;

public class GearAnimation {
    private float espValue = 1f, prevEspValue;
    private float espSpeed = 1f;
    private boolean flipSpeed;

    public float getValue() {
        return Render2DEngine.interpolateFloat(prevEspValue, espValue, Render3DEngine.getTickDelta());
    }

    public void tick() {
        prevEspValue = espValue;
        espValue += espSpeed;
        if (espSpeed > ClickGui.gearStop.getValue()) flipSpeed = true;
        if (espSpeed < -ClickGui.gearStop.getValue()) flipSpeed = false;
        espSpeed = flipSpeed ? espSpeed - ClickGui.gearDuration.getValue() : espSpeed + ClickGui.gearDuration.getValue();
    }
}

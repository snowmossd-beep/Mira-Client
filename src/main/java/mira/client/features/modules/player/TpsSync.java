package mira.client.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import mira.client.MiraClient;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventTick;
import mira.client.features.modules.Module;

public class TpsSync extends Module {
    public TpsSync() {
        super("TpsSync", Module.Category.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(EventTick e) {
        if (ModuleManager.timer.isEnabled()) return;
        if (Managers.SERVER.getTPS() > 1)
            MiraClient.TICK_TIMER = Managers.SERVER.getTPS() / 20f;
        else MiraClient.TICK_TIMER = 1f;
    }

    @Override
    public void onDisable() {
        MiraClient.TICK_TIMER = 1f;
    }
}

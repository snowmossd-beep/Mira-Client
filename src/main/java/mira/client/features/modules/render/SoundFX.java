package mira.client.features.modules.render;

import meteordevelopment.orbit.EventHandler;
import org.jetbrains.annotations.NotNull;
import mira.client.core.Managers;
import mira.client.events.impl.EventDeath;
import mira.client.events.impl.EventAttack;
import mira.client.features.modules.Module;
import mira.client.features.modules.combat.Aura;
import mira.client.setting.Setting;

public final class SoundFX extends Module {
    public SoundFX() {
        super("SoundFX", Category.RENDER);
    }

    public final Setting<Integer> volume = new Setting<>("Volume", 100, 0, 100);
    public final Setting<OnOffSound> enableMode = new Setting<>("EnableMode", OnOffSound.Inertia);
    public final Setting<OnOffSound> disableMode = new Setting<>("DisableMode", OnOffSound.Inertia);
    public final Setting<HitSound> hitSound = new Setting<>("HitSound", HitSound.OFF);
    public final Setting<KillSound> killSound = new Setting<>("KillSound", KillSound.OFF);
    public final Setting<ScrollSound> scrollSound = new Setting<>("ScrollSound", ScrollSound.KeyBoard);

    @EventHandler
    @SuppressWarnings("unused")
    public void onAttack(@NotNull EventAttack event) {
        if (!event.isPre())
            Managers.SOUND.playHitSound(hitSound.getValue());
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onDeath(EventDeath e) {
        if (Aura.target != null && Aura.target == e.getPlayer() && killSound.is(KillSound.Custom)) {
            Managers.SOUND.playSound("kill");
        }
    }

    public enum OnOffSound {
        Custom, Inertia, OFF
    }

    public enum HitSound {
        UWU, MOAN, SKEET, RIFK, KEYBOARD, CUTIE, CUSTOM, OFF
    }

    public enum KillSound {
        Custom, OFF
    }

    public enum ScrollSound {
        Custom, OFF, KeyBoard
    }
}

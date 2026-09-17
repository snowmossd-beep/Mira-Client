package mira.client.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import mira.client.core.Managers;
import mira.client.events.impl.EventAttack;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;

public class CritSound extends Module {
    public final Setting<Boolean> onlyCrit = new Setting<>("OnlyCrit", true);

    public CritSound() {
        super("CritSound", Category.MISC);
    }

    @EventHandler
    public void onAttack(@NotNull EventAttack event) {
        if (event.isPre()) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        boolean isCrit = !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isInLava()
                && !mc.player.isSubmergedInWater()
                && mc.player.getVelocity().y < 0
                && !mc.player.getAbilities().flying;

        if (onlyCrit.getValue() && !isCrit) return;

        Managers.SOUND.playWither(1.0f);
    }
            }

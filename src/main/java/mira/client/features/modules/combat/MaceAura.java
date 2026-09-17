package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventTick;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.Timer;

import static mira.client.features.modules.Module.mc;

public class MaceAura extends Module {

    public final Setting<Float>   range           = new Setting<>("Range", 3.2f, 1f, 6f);
    public final Setting<Float>   minCooldown     = new Setting<>("MinCooldown", 1.0f, 0.1f, 1.0f);
    public final Setting<Boolean> requireFalling  = new Setting<>("RequireFalling", false);
    public final Setting<Float>   minFallDistance = new Setting<>("MinFallDistance", 1.5f, 0f, 20f, v -> requireFalling.getValue());
    public final Setting<Boolean> swing           = new Setting<>("Swing", true);
    public final Setting<Integer> attackDelay     = new Setting<>("AttackDelay", 0, 0, 500);

    private final Timer attackTimer = new Timer();

    public MaceAura() {
        super("MaceAura", Category.COMBAT);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        if (!(mc.player.getMainHandStack().getItem() instanceof MaceItem)) return;

        var target = ModuleManager.aura.getTarget();
        if (target == null) return;

        if (mc.player.squaredDistanceTo(target) > range.getValue() * range.getValue()) return;

        if (requireFalling.getValue()) {
            boolean validFall = !mc.player.isOnGround()
                    && mc.player.getVelocity().y < 0
                    && mc.player.fallDistance >= minFallDistance.getValue();
            if (!validFall) return;
        }

        if (ModuleManager.aura.getAttackCooldown() < minCooldown.getValue()) return;
        if (!attackTimer.passedMs(attackDelay.getValue())) return;

        mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
        );
        if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);

        attackTimer.reset();
    }
}

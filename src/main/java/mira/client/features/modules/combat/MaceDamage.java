package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import mira.client.events.impl.EventTick;
import mira.client.events.impl.PacketEvent;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class MaceDamage extends Module {

    public final Setting<Integer> blocks    = new Setting<>("Blocks",    22,  1, 100);
    public final Setting<Integer> repeat    = new Setting<>("Repeat",    3,   1, 20);
    public final Setting<Double>  stepSize  = new Setting<>("StepSize",  8.5, 1.0, 10.0);
    public final Setting<Integer> perTick   = new Setting<>("PerTick",   3,   1, 8);
    public final Setting<Boolean> jitter    = new Setting<>("Jitter",    true);
    public final Setting<Boolean> onlyMace  = new Setting<>("OnlyMace",  true);
    public final Setting<Boolean> onGround  = new Setting<>("OnGround",  true);

    private final Queue<PlayerMoveC2SPacket> queue = new LinkedList<>();
    private final Random rng = new Random();
    private boolean triggered = false;

    public MaceDamage() {
        super("MaceDamage", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        queue.clear();
        triggered = false;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (queue.isEmpty()) return;
        for (int i = 0; i < perTick.getValue() && !queue.isEmpty(); i++) {
            mc.getNetworkHandler().sendPacket(queue.poll());
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (!queue.isEmpty() || triggered) return;
        if (mc.player == null) return;
        if (onlyMace.getValue() && !(mc.player.getMainHandStack().getItem() instanceof MaceItem)) return;
        if (!(e.getPacket() instanceof PlayerMoveC2SPacket)) return;
        if (onGround.getValue() && !mc.player.isOnGround()) return;

        triggered = true;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double target = y + blocks.getValue();
        double step   = stepSize.getValue();

        for (int r = 0; r < repeat.getValue(); r++) {
            double cur = y;
            while (cur < target) {
                double next = Math.min(cur + step, target);
                double jx = jitter.getValue() ? x + (rng.nextDouble() - 0.5) * 0.002 : x;
                double jz = jitter.getValue() ? z + (rng.nextDouble() - 0.5) * 0.002 : z;
                queue.add(new PlayerMoveC2SPacket.PositionAndOnGround(jx, next, jz, false));
                cur = next;
            }
            queue.add(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.05, z, false));
        }

        queue.add(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));

        triggered = false;
    }
    }

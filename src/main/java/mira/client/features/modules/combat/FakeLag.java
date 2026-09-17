package mira.client.features.modules.combat;

import mira.client.events.impl.EventTick;
import mira.client.events.impl.PacketEvent;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

public class FakeLag extends Module {

    public enum Mode { Constant, Dynamic }

    private final Setting<Mode>    mode       = new Setting<>("Mode",       Mode.Dynamic);
    private final Setting<Float>   range      = new Setting<>("Range",      5f, 0f, 15f, v -> mode.getValue() == Mode.Dynamic);
    private final Setting<Integer> holdMs     = new Setting<>("HoldMs",     400, 50, 2000);
    private final Setting<Integer> flushMs    = new Setting<>("FlushMs",    80,  10, 500);
    private final Setting<Integer> maxQueue   = new Setting<>("MaxQueue",   8,   1,  20);
    private final Setting<Integer> recoilMs   = new Setting<>("RecoilMs",   300, 0,  2000);

    private final Deque<Packet<?>> queue = new ArrayDeque<>();
    private long holdStart  = -1;
    private long flushStart = -1;
    private long lastFlushPacket = 0;
    private long lastRecoil = 0;
    private boolean flushing = false;

    public FakeLag() {
        super("FakeLag", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        queue.clear();
        holdStart  = System.currentTimeMillis();
        flushStart = -1;
        flushing   = false;
    }

    @Override
    public void onDisable() {
        flushAll();
    }

    @EventHandler
    public void onTick(@NotNull EventTick e) {
        if (fullNullCheck()) return;

        if (mode.getValue() == Mode.Dynamic) {
            boolean enemyNear = mc.world.getPlayers().stream()
                .anyMatch(p -> p != mc.player
                    && !p.isTeammate(mc.player)
                    && p.distanceTo(mc.player) <= range.getValue());
            if (!enemyNear) {
                flushAll();
                flushing  = false;
                holdStart = System.currentTimeMillis();
                return;
            }
        }

        long now = System.currentTimeMillis();

        if (!flushing) {
            if (queue.size() >= maxQueue.getValue()) {
                flushing   = true;
                flushStart = now;
                lastFlushPacket = now;
                return;
            }
            if (holdStart >= 0 && now - holdStart >= holdMs.getValue()) {
                flushing   = true;
                flushStart = now;
                lastFlushPacket = now;
            }
        } else {
            if (now - lastFlushPacket >= flushMs.getValue() && !queue.isEmpty()) {
                sendPacketSilent(queue.poll());
                lastFlushPacket = now;
            }
            if (queue.isEmpty()) {
                flushing  = false;
                holdStart = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    public void onSend(PacketEvent.@NotNull Send e) {
        if (fullNullCheck()) return;
        if (!(e.getPacket() instanceof PlayerMoveC2SPacket)) return;
        if (flushing || !isEnabled() || mc.player.isDead()) return;
        if (queue.size() >= maxQueue.getValue()) return;

        queue.add(e.getPacket());
        e.cancel();
    }

    @EventHandler
    public void onReceive(PacketEvent.@NotNull Receive e) {
        if (fullNullCheck()) return;
        Packet<?> pkt = e.getPacket();

        if (pkt instanceof PlayerPositionLookS2CPacket) {
            flushAll();
            disable();
            return;
        }

        long now = System.currentTimeMillis();
        boolean recoil = false;

        if (pkt instanceof HealthUpdateS2CPacket hp) {
            if (hp.getHealth() <= 0) { flushAll(); return; }
            recoil = true;
        } else if (pkt instanceof EntityVelocityUpdateS2CPacket vel) {
            if (vel.getId() == mc.player.getId()) recoil = true;
        } else if (pkt instanceof ExplosionS2CPacket ex) {
            if (ex.getPlayerVelocityX() != 0 || ex.getPlayerVelocityY() != 0 || ex.getPlayerVelocityZ() != 0)
                recoil = true;
        }

        if (recoil && now - lastRecoil >= recoilMs.getValue()) {
            lastRecoil = now;
            flushAll();
            flushing  = false;
            holdStart = System.currentTimeMillis();
        }
    }

    private void flushAll() {
        while (!queue.isEmpty())
            sendPacketSilent(queue.poll());
    }
    }

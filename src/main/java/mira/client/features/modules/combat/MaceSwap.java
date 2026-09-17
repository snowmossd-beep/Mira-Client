package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import mira.client.core.InputBlocker;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventTick;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.Timer;

import static mira.client.features.modules.Module.mc;

public class MaceSwap extends Module {

    public final Setting<Integer> toMaceDelay   = new Setting<>("ToMaceDelay",   10,  0, 500);
    public final Setting<Integer> attackDelay   = new Setting<>("AttackDelay",   20, 0, 500);
    public final Setting<Boolean> attackWithMace = new Setting<>("AttackWithMace", true);
    public final Setting<Float>   reach         = new Setting<>("Reach",         3.2f, 1f, 6f);
    public final Setting<Boolean> onlyFalling   = new Setting<>("OnlyFalling",   false);
    public final Setting<Float>   minFall       = new Setting<>("MinFall",        3f, 0f, 20f, v -> onlyFalling.getValue());

    private final Timer toMaceTimer  = new Timer();
    private final Timer attackTimer  = new Timer();

    private int savedSlot         = -1;
    private boolean swapped       = false;
    private boolean waitingAttack = false;

    private static final String SWITCH_BLOCK_OWNER = "maceswap_switch";
    private static final long LEGIT_SWITCH_DELAY_MS = 5L;
    private boolean switchPending;
    private int switchPendingSlot = -1;
    private long switchPendingAt = -1L;

    public MaceSwap() {
        super("MaceSwap", Category.COMBAT);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        var target = ModuleManager.aura.getTarget();

        if (target == null) {
            restoreIfNeeded();
            return;
        }

        if (onlyFalling.getValue()) {
            boolean validFall = !mc.player.isOnGround()
                    && mc.player.getVelocity().y < 0
                    && mc.player.fallDistance >= minFall.getValue();
            if (!validFall) {
                restoreIfNeeded();
                return;
            }
        }

        if (!swapped && !waitingAttack) {
            if (!toMaceTimer.passedMs(toMaceDelay.getValue())) return;

            if (mc.player.squaredDistanceTo(target) > reach.getValue() * reach.getValue()) return;

            int maceSlot = findHotbar(true);
            if (maceSlot == -1) return;

            if (savedSlot == -1) savedSlot = mc.player.getInventory().selectedSlot;
            if (!ensureSlot(maceSlot)) return;
            swapped = true;

            if (attackWithMace.getValue()) {
                waitingAttack = true;
                attackTimer.reset();
            }
            return;
        }

        if (waitingAttack && swapped) {
            if (!attackTimer.passedMs(attackDelay.getValue())) return;

            if (ModuleManager.aura.getTarget() != null) {
                mc.getNetworkHandler().sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
                );
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            waitingAttack = false;
            if (!ensureSlot(savedSlot)) return;
            swapped   = false;
            savedSlot = -1;
            toMaceTimer.reset();
        }
    }

    private void restoreIfNeeded() {
        if (swapped && savedSlot != -1) {
            if (!ensureSlot(savedSlot)) return;
            swapped       = false;
            savedSlot     = -1;
            waitingAttack = false;
            toMaceTimer.reset();
        }
    }

    private boolean ensureSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.player == null) return false;
        if (mc.player.getInventory().selectedSlot == slot) return true;

        if (!switchPending) {
            InputBlocker.block(SWITCH_BLOCK_OWNER);
            switchPending = true;
            switchPendingSlot = slot;
            switchPendingAt = System.currentTimeMillis() + LEGIT_SWITCH_DELAY_MS;
            return false;
        }

        if (switchPendingSlot == slot) {
            if (System.currentTimeMillis() >= switchPendingAt) {
                silentSwap(slot);
                clearSwitchPending();
                return true;
            }
            return false;
        }

        clearSwitchPending();
        return false;
    }

    private void clearSwitchPending() {
        switchPending = false;
        switchPendingSlot = -1;
        switchPendingAt = -1L;
        InputBlocker.unblock(SWITCH_BLOCK_OWNER);
    }

    private void silentSwap(int slot) {
        if (slot < 0 || slot > 8 || mc.player == null) return;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private int findHotbar(boolean mace) {
        for (int i = 0; i < 9; i++) {
            var item = mc.player.getInventory().getStack(i).getItem();
            if (mace  && item instanceof MaceItem)  return i;
            if (!mace && item instanceof SwordItem) return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        if (swapped && savedSlot != -1 && mc.player != null) {
            silentSwap(savedSlot);
        }
        clearSwitchPending();
        swapped       = false;
        savedSlot     = -1;
        waitingAttack = false;
    }
    }

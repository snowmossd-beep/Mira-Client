package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import mira.client.core.Managers;
import mira.client.events.impl.EventTick;
import mira.client.events.impl.EventTravel;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.Timer;

public class MaceDive extends Module {

    public final Setting<Float>   launchHeight  = new Setting<>("LaunchHeight",  20f,  5f, 60f);
    public final Setting<Float>   peakOffset    = new Setting<>("PeakOffset",    2.5f, 0f, 10f);
    public final Setting<Float>   attackRange   = new Setting<>("AttackRange",   4.5f, 1f, 8f);
    public final Setting<Integer> fireworkDelay = new Setting<>("FireworkDelay", 12,   5,  40);
    public final Setting<Integer> swapDelay     = new Setting<>("SwapDelay",     3,    1,  15);
    public final Setting<Integer> pingComp      = new Setting<>("PingComp",      100,  0,  500);
    public final Setting<Boolean> predictTarget = new Setting<>("PredictTarget", true);

    private enum Phase {
        EQUIP_ELYTRA,
        JUMP,
        ASCEND,
        PEAK_AIM,
        DIVE,
        PRE_HIT_SWAP,
        ATTACK,
        RE_EQUIP,
        RELAUNCH
    }

    private Phase phase = Phase.EQUIP_ELYTRA;
    private PlayerEntity target = null;
    private int savedSlot = -1;
    private int tick = 0;
    private int fwTick = 0;
    private boolean chestSwapped = false;
    private Vec3d predictedPos = null;
    private final Timer swapTimer = new Timer();

    public MaceDive() {
        super("MaceDive", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (savedSlot != -1 && mc.player != null) silentSlot(savedSlot);
        mc.options.jumpKey.setPressed(false);
    }

    private void reset() {
        phase = Phase.EQUIP_ELYTRA;
        target = null;
        tick = 0; fwTick = 0;
        chestSwapped = false;
        predictedPos = null;
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        tick++;
        switch (phase) {
            case EQUIP_ELYTRA -> tickEquip();
            case JUMP         -> tickJump();
            case ASCEND       -> tickAscend();
            case PEAK_AIM     -> tickPeakAim();
            case DIVE         -> tickDive();
            case PRE_HIT_SWAP -> tickPreHitSwap();
            case ATTACK       -> tickAttack();
            case RE_EQUIP     -> tickReEquip();
            case RELAUNCH     -> tickRelaunch();
        }
    }

    @EventHandler
    public void onTravel(EventTravel e) {
        if (fullNullCheck() || !e.isPre() || target == null) return;
        switch (phase) {
            case ASCEND -> {
                if (mc.player.isFallFlying()) mc.player.setPitch(-82f);
            }
            case PEAK_AIM, DIVE -> {
                if (mc.player.isFallFlying() && predictedPos != null) {
                    aimAt(predictedPos);
                    mc.player.setPitch(Math.max(mc.player.getPitch(), 75f));
                }
            }
        }
    }

    private void tickEquip() {
        if (!hasElytraChest() && !equipElytra()) { disable(); return; }
        if (tick < 2) return;
        target = findTarget();
        if (target == null) return;
        phase = Phase.JUMP; tick = 0;
    }

    private void tickJump() {
        target = refresh(target);
        if (target == null) { phase = Phase.EQUIP_ELYTRA; return; }
        if (mc.player.isOnGround()) {
            mc.player.jump();
            mc.options.jumpKey.setPressed(true);
            return;
        }
        mc.options.jumpKey.setPressed(false);
        if (!mc.player.isFallFlying() && mc.player.getVelocity().y < 0) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startFallFlying();
        }
        if (mc.player.isFallFlying()) { fwTick = 0; phase = Phase.ASCEND; tick = 0; }
    }

    private void tickAscend() {
        target = refresh(target);
        if (target == null) { phase = Phase.EQUIP_ELYTRA; return; }
        if (!mc.player.isFallFlying()) { phase = Phase.JUMP; return; }
        fwTick++;
        if (fwTick >= fireworkDelay.getValue()) { useFirework(); fwTick = 0; }
        double targetHeight = target.getY() + launchHeight.getValue();
        if (mc.player.getY() >= targetHeight) {
            updatePrediction();
            phase = Phase.PEAK_AIM; tick = 0;
        }
    }

    private void tickPeakAim() {
        target = refresh(target);
        if (target == null) { phase = Phase.EQUIP_ELYTRA; return; }
        if (!mc.player.isFallFlying()) { phase = Phase.JUMP; return; }
        updatePrediction();
        if (tick >= 3) { phase = Phase.DIVE; tick = 0; }
    }

    private void tickDive() {
        target = refresh(target);
        if (target == null) { phase = Phase.RE_EQUIP; return; }
        if (!mc.player.isFallFlying() && mc.player.isOnGround()) { phase = Phase.RE_EQUIP; return; }
        updatePrediction();
        double dist = predictedPos != null
            ? mc.player.getPos().distanceTo(predictedPos)
            : mc.player.distanceTo(target);
        if (dist <= attackRange.getValue() + peakOffset.getValue()) {
            phase = Phase.PRE_HIT_SWAP; tick = 0;
        }
    }

    private void tickPreHitSwap() {
        if (tick < swapDelay.getValue()) return;
        if (!chestSwapped) { swapToChestplate(); chestSwapped = true; }
        int maceSlot = findHotbar(MaceItem.class);
        if (maceSlot != -1) {
            if (savedSlot == -1) savedSlot = mc.player.getInventory().selectedSlot;
            silentSlot(maceSlot);
        }
        phase = Phase.ATTACK; tick = 0;
    }

    private void tickAttack() {
        target = refresh(target);
        if (target == null || target.isDead() || target.getHealth() <= 0) {
            phase = Phase.RE_EQUIP; return;
        }
        double dist = mc.player.distanceTo(target);
        if (dist <= attackRange.getValue()) {
            Vec3d aimPos = predictTarget.getValue() ? predictPos(target) : target.getEyePos();
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                calcYaw(aimPos), calcPitch(aimPos), mc.player.isOnGround()));
            mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (target.isDead() || target.getHealth() <= 0) { phase = Phase.RE_EQUIP; return; }
        if (mc.player.isOnGround() || dist > attackRange.getValue() + 3f) {
            phase = Phase.RE_EQUIP; tick = 0;
        }
    }

    private void tickReEquip() {
        if (savedSlot != -1) { silentSlot(savedSlot); savedSlot = -1; }
        if (tick < swapDelay.getValue()) return;
        if (chestSwapped) { equipElytra(); chestSwapped = false; }
        phase = Phase.RELAUNCH; tick = 0;
    }

    private void tickRelaunch() {
        target = refresh(target);
        if (target == null) { phase = Phase.EQUIP_ELYTRA; tick = 0; return; }
        if (tick < swapDelay.getValue()) return;
        predictedPos = null;
        phase = Phase.JUMP; tick = 0;
    }

    private void updatePrediction() {
        if (target == null) return;
        if (predictTarget.getValue()) {
            predictedPos = predictPos(target);
        } else {
            predictedPos = target.getPos().add(0, 1, 0);
        }
    }

    private Vec3d predictPos(PlayerEntity p) {
        double ticks = pingComp.getValue() / 50.0;
        return p.getPos().add(p.getVelocity().multiply(ticks)).add(0, 1, 0);
    }

    private void aimAt(Vec3d pos) {
        Vec3d dir = pos.subtract(mc.player.getEyePos()).normalize();
        mc.player.setYaw(calcYaw(pos));
        mc.player.setPitch(calcPitch(pos));
    }

    private float calcYaw(Vec3d pos) {
        Vec3d dir = pos.subtract(mc.player.getEyePos());
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private float calcPitch(Vec3d pos) {
        Vec3d dir = pos.subtract(mc.player.getEyePos()).normalize();
        return (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dir.y))));
    }

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null) return null;
        return mc.world.getPlayers().stream()
            .filter(p -> p != mc.player && !p.isDead()
                && !Managers.FRIEND.isFriend(p.getGameProfile().getName()))
            .min((a, b) -> Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)))
            .orElse(null);
    }

    private PlayerEntity refresh(PlayerEntity old) {
        if (old == null || old.isDead() || old.getHealth() <= 0) return findTarget();
        return old;
    }

    private boolean hasElytraChest() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem;
    }

    private boolean equipElytra() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof ElytraItem) {
                swapArmorSlot(i, 6);
                return true;
            }
        }
        return false;
    }

    private void swapToChestplate() {
        for (int i = 0; i < 36; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item instanceof ArmorItem ai
                && ai.getSlotType() == EquipmentSlot.CHEST
                && ai.getProtection() > 0) {
                swapArmorSlot(i, 6);
                return;
            }
        }
    }

    private void swapArmorSlot(int inventoryIndex, int armorSlot) {
        if (mc.player == null || mc.interactionManager == null) return;
        int screenSlot = inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void useFirework() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof FireworkRocketItem) {
                int prev = mc.player.getInventory().selectedSlot;
                silentSlot(i);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                silentSlot(prev);
                return;
            }
        }
    }

    private void silentSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.player == null) return;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private int findHotbar(Class<?> cls) {
        for (int i = 0; i < 9; i++)
            if (cls.isInstance(mc.player.getInventory().getStack(i).getItem()))
                return i;
        return -1;
    }
    }
        
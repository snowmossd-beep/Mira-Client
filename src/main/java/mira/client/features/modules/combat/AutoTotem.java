package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventSync;
import mira.client.events.impl.PacketEvent;
import mira.client.injection.accesors.IMinecraftClient;
import mira.client.features.modules.Module;
import mira.client.features.modules.movement.Blink;
import mira.client.setting.Setting;
import mira.client.setting.impl.Bind;
import mira.client.setting.impl.BooleanSettingGroup;
import mira.client.setting.impl.SettingGroup;
import mira.client.utility.Timer;
import mira.client.utility.world.ExplosionUtility;
import mira.client.utility.math.PredictUtility;
import mira.client.utility.player.InventoryUtility;
import mira.client.utility.player.SearchInvResult;

public final class AutoTotem extends Module {
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<OffHand> offhand = new Setting<>("Item", OffHand.Totem);
    private final Setting<BooleanSettingGroup> bindSwap = new Setting<>("BindSwap", new BooleanSettingGroup(false), v -> offhand.is(OffHand.Totem));
    private final Setting<Bind> swapButton = new Setting<>("SwapButton", new Bind(GLFW.GLFW_KEY_CAPS_LOCK, false, false)).addToGroup(bindSwap);
    private final Setting<Swap> swapMode = new Setting<>("Swap", Swap.GappleShield).addToGroup(bindSwap);
    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Float> healthF = new Setting<>("HP", 16f, 0f, 36f);
    private final Setting<Float> healthS = new Setting<>("ShieldGappleHp", 16f, 0f, 20f, v -> offhand.getValue() == OffHand.Shield);
    private final Setting<Boolean> calcAbsorption = new Setting<>("CalcAbsorption", true);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);

    private final Setting<BooleanSettingGroup> guardian = new Setting<>("Guardian", new BooleanSettingGroup(true));
    private final Setting<Integer> guardianDelay = new Setting<>("GuardianDelay", 3, 1, 10).addToGroup(guardian);
    private final Setting<Integer> guardianRetries = new Setting<>("GuardianRetries", 3, 1, 10).addToGroup(guardian);

    private final Setting<SettingGroup> safety = new Setting<>("Safety", new SettingGroup(false, 0));
    private final Setting<Boolean> hotbarFallBack = new Setting<>("HotbarFallback", false).addToGroup(safety);
    private final Setting<Boolean> fallBackCalc = new Setting<>("FallBackCalc", true, v -> hotbarFallBack.getValue()).addToGroup(safety);
    private final Setting<Boolean> onElytra = new Setting<>("OnElytra", true).addToGroup(safety);
    private final Setting<Boolean> onFall = new Setting<>("OnFall", true).addToGroup(safety);
    private final Setting<Boolean> onCrystal = new Setting<>("OnCrystal", true).addToGroup(safety);
    private final Setting<Boolean> onObsidianPlace = new Setting<>("OnObsidianPlace", false).addToGroup(safety);
    private final Setting<Boolean> onCrystalInHand = new Setting<>("OnCrystalInHand", false).addToGroup(safety);
    private final Setting<Boolean> onMinecartTnt = new Setting<>("OnMinecartTNT", true).addToGroup(safety);
    private final Setting<Boolean> onCreeper = new Setting<>("OnCreeper", true).addToGroup(safety);
    private final Setting<Boolean> onAnchor = new Setting<>("OnAnchor", true).addToGroup(safety);
    private final Setting<Boolean> onTnt = new Setting<>("OnTNT", true).addToGroup(safety);

    private final Setting<BooleanSettingGroup> totemLegit = new Setting<>("TotemLegit", new BooleanSettingGroup(false));
    private final Setting<Float>   legitHP       = new Setting<>("LegitHP",       8f, 0f, 36f).addToGroup(totemLegit);
    private final Setting<Integer> legitDelay    = new Setting<>("LegitDelay",   150, 0, 1000).addToGroup(totemLegit);
    private final Setting<Integer> auraBreakWait = new Setting<>("AuraBreakWait",200, 0, 1000).addToGroup(totemLegit);
    private final Setting<Boolean> pauseOnAura   = new Setting<>("PauseOnAura",  true).addToGroup(totemLegit);
    private final Setting<Boolean> forceOnDanger = new Setting<>("ForceOnDanger",true).addToGroup(totemLegit);

    private final Setting<SettingGroup> bypass = new Setting<>("Bypass", new SettingGroup(false, 0));
    private final Setting<Boolean> silentSwapB   = new Setting<>("Silent",       false).addToGroup(bypass);
    private final Setting<Boolean> packetClose   = new Setting<>("PacketClose",  true).addToGroup(bypass);
    private final Setting<Boolean> grimBypass    = new Setting<>("GrimBypass",   false).addToGroup(bypass);
    private final Setting<Boolean> ncpBypass     = new Setting<>("NCPBypass",    false).addToGroup(bypass);
    private final Setting<Boolean> aacBypass     = new Setting<>("AACBypass",    false).addToGroup(bypass);
    private final Setting<Boolean> verusBypass   = new Setting<>("VerusBypass",  false).addToGroup(bypass);
    private final Setting<Boolean> randomDelay   = new Setting<>("RandomDelay",  false).addToGroup(bypass);
    private final Setting<Integer> randomMin     = new Setting<>("RandomMin",    10, 0, 100, v -> randomDelay.getValue()).addToGroup(bypass);
    private final Setting<Integer> randomMax     = new Setting<>("RandomMax",    50, 0, 300, v -> randomDelay.getValue()).addToGroup(bypass);
    private final Setting<Boolean> fakeDelay     = new Setting<>("FakeDelay",    false).addToGroup(bypass);
    private final Setting<Integer> fakeDelayMs   = new Setting<>("FakeMs",       50, 0, 300, v -> fakeDelay.getValue()).addToGroup(bypass);
    private final Setting<Boolean> antiAntiCheat = new Setting<>("AntiAC",       false).addToGroup(bypass);
    private final Setting<Boolean> swingFix      = new Setting<>("SwingFix",     false).addToGroup(bypass);
    private final Setting<Boolean> sprintReset   = new Setting<>("SprintReset",  false).addToGroup(bypass);

    public final Setting<RCGap> rcGap = new Setting<>("RightClickGapple", RCGap.Off);
    private final Setting<Boolean> crappleSpoof = new Setting<>("CrappleSpoof", true, v -> offhand.getValue() == OffHand.GApple);

    private enum OffHand { Totem, Crystal, GApple, Shield }
    private enum Mode { Default, Alternative, Matrix, MatrixPick, NewVersion }
    private enum Swap { GappleShield, BallShield, GappleBall, BallTotem }
    public enum RCGap { Off, Always, OnlySafe }

    private int delay;
    private final Timer bindDelay = new Timer();
    private final Timer legitTimer = new Timer();
    private final Timer auraBreakTimer = new Timer();
    private Item prevItem;
    private volatile boolean isSwapping = false;
    private long lastSwapTime = 0;
    private static final long MIN_SWAP_INTERVAL = 100;

    private Item lastTargetItem;
    private Item guardianExpectedItem;
    private int guardianCooldown = -1;
    private int guardianRetryCount;

    private static long lastAuraHitTime = 0;

    public static void notifyAuraHit() {
        lastAuraHitTime = System.currentTimeMillis();
    }

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (isLegitPaused()) return;

        if (totemLegit.getValue().isEnabled()) {
            long d = getEffectiveDelay();
            if (!legitTimer.passedMs(d)) return;
        }

        swapTo(getItemSlot());
        handleGuardianVerify();

        if (rcGap.not(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !mc.player.isUsingItem())
            ((IMinecraftClient) mc).idoItemUse();

        delay--;

        if (totemLegit.getValue().isEnabled()) legitTimer.reset();
    }

    private boolean isLegitPaused() {
        if (!totemLegit.getValue().isEnabled()) return false;
        if (!pauseOnAura.getValue()) return false;
        if (!ModuleManager.aura.isEnabled()) return false;

        boolean auraCombat = System.currentTimeMillis() - lastAuraHitTime < auraBreakWait.getValue();
        if (!auraCombat) return false;

        if (forceOnDanger.getValue() && isDangerState()) return false;

        return true;
    }

    private boolean isDangerState() {
        float hp = getTriggerHealth();
        if (totemLegit.getValue().isEnabled() && hp <= legitHP.getValue()) return true;
        return mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING;
    }

    private long getEffectiveDelay() {
        if (randomDelay.getValue()) {
            return randomMin.getValue() + (long)(Math.random() * (randomMax.getValue() - randomMin.getValue()));
        }
        long base = legitDelay.getValue();
        if (fakeDelay.getValue()) base += fakeDelayMs.getValue();
        return base;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof EntitySpawnS2CPacket spawn) {
            if (spawn.getEntityType() == EntityType.END_CRYSTAL) {
                if (getPlayerPos().squaredDistanceTo(spawn.getX(), spawn.getY(), spawn.getZ()) < 36) {
                    if (hotbarFallBack.getValue()) {
                        if (fallBackCalc.getValue() && ExplosionUtility.getExplosionDamageWPredict(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()), mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < getTriggerHealth() + 4f)
                            return;
                        runInstant();
                    }
                    if (onCrystal.getValue()) {
                        if (getTriggerHealth() - ExplosionUtility.getExplosionDamageWPredict(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()), mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < 0.5) {
                            int slot = -1;
                            for (int i = 9; i < 45; i++) {
                                if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem().equals(Items.TOTEM_OF_UNDYING)) {
                                    slot = i >= 36 ? i - 36 : i;
                                    break;
                                }
                            }
                            lastTargetItem = Items.TOTEM_OF_UNDYING;
                            swapTo(slot);
                            debug("spawn switch");
                        }
                    }
                }
            }
        }

        if (e.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState().getBlock() == Blocks.OBSIDIAN && onObsidianPlace.getValue()) {
                if (getPlayerPos().squaredDistanceTo(blockUpdate.getPos().toCenterPos()) < 36 && delay <= 0)
                    runInstant();
            }
        }
    }

    private float getTriggerHealth() {
        return mc.player.getHealth() + (calcAbsorption.getValue() ? mc.player.getAbsorptionAmount() : 0f);
    }

    private void runInstant() {
        SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(Items.TOTEM_OF_UNDYING);
        SearchInvResult invResult = InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING);
        lastTargetItem = Items.TOTEM_OF_UNDYING;
        if (hotbarResult.found()) {
            hotbarResult.switchTo();
            delay = 20;
        } else if (invResult.found()) {
            int slot = invResult.slot() >= 36 ? invResult.slot() - 36 : invResult.slot();
            if (!hotbarFallBack.getValue()) swapTo(slot);
            else mc.interactionManager.pickFromInventory(slot);
            delay = 20;
        }
    }

    public void swapTo(int slot) {
        if (slot != -1 && delay <= 0) {
            if (mc.currentScreen instanceof GenericContainerScreen) return;
            if (isSwapping) return;
            long now = System.currentTimeMillis();
            if (now - lastSwapTime < MIN_SWAP_INTERVAL) return;

            if (stopMotion.getValue()) mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);

            if (sprintReset.getValue() && ncpStrict.getValue())
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

            int nearestSlot = findNearestCurrentItem();
            int prevCurrentItem = mc.player.getInventory().selectedSlot;

            isSwapping = true;
            lastSwapTime = now;

            Mode effectiveMode = mode.getValue();
            if (ModuleManager.aura.isEnabled() && totemLegit.getValue().isEnabled()) {
                effectiveMode = Mode.Matrix;
            }

            if (slot >= 9) {
                switch (effectiveMode) {
                    case Default -> {
                        clickSlot(slot);
                        clickSlot(45);
                        clickSlot(slot);
                        if (packetClose.getValue())
                            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Alternative -> {
                        clickSlot(slot, nearestSlot, SlotActionType.SWAP);
                        clickSlot(45, nearestSlot, SlotActionType.SWAP);
                        clickSlot(slot, nearestSlot, SlotActionType.SWAP);
                        if (packetClose.getValue())
                            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Matrix -> {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
                        sendPacket(new UpdateSelectedSlotC2SPacket(nearestSlot));
                        mc.player.getInventory().selectedSlot = nearestSlot;

                        ItemStack itemstack = mc.player.getOffHandStack();
                        mc.player.setStackInHand(Hand.OFF_HAND, mc.player.getMainHandStack());
                        mc.player.setStackInHand(Hand.MAIN_HAND, itemstack);
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

                        sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                        mc.player.getInventory().selectedSlot = prevCurrentItem;

                        if (grimBypass.getValue()) {
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                        }

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
                        if (packetClose.getValue())
                            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                        if (resetAttackCooldown.getValue())
                            mc.player.resetLastAttackedTicks();
                    }
                    case MatrixPick -> {
                        sendPacket(new PickFromInventoryC2SPacket(slot));
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                        int prevSlot = mc.player.getInventory().selectedSlot;
                        Managers.ASYNC.run(() -> mc.player.getInventory().selectedSlot = prevSlot, 300);
                    }
                    case NewVersion -> {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                        if (packetClose.getValue())
                            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
            } else {
                if (silentSwapB.getValue()) {
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    mc.player.getInventory().selectedSlot = slot;
                } else {
                    mc.player.getInventory().selectedSlot = slot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                }
                if (swingFix.getValue()) sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                mc.player.getInventory().selectedSlot = prevCurrentItem;
                if (resetAttackCooldown.getValue())
                    mc.player.resetLastAttackedTicks();
            }

            if (ncpBypass.getValue()) sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
            if (aacBypass.getValue()) sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

            if (guardian.getValue().isEnabled() && lastTargetItem != null) {
                guardianExpectedItem = lastTargetItem;
                guardianCooldown = guardianDelay.getValue();
            }

            delay = (int)(2 + (Managers.SERVER.getPing() / 25f));
            isSwapping = false;
        }
    }

    private void handleGuardianVerify() {
        if (!guardian.getValue().isEnabled() || mc.player == null) {
            guardianCooldown = -1;
            guardianRetryCount = 0;
            return;
        }

        if (guardianCooldown < 0) return;

        if (guardianCooldown > 0) {
            guardianCooldown--;
            return;
        }

        guardianCooldown = -1;
        if (guardianExpectedItem == null) return;

        if (mc.player.getOffHandStack().getItem() != guardianExpectedItem) {
            if (guardianRetryCount < guardianRetries.getValue()) {
                SearchInvResult retry = InventoryUtility.findItemInInventory(guardianExpectedItem);
                if (retry.found()) {
                    guardianRetryCount++;
                    int rawSlot = retry.slot() >= 36 ? retry.slot() - 36 : retry.slot();
                    lastTargetItem = guardianExpectedItem;
                    delay = 0;
                    swapTo(rawSlot);
                }
            }
        } else {
            guardianRetryCount = 0;
        }
    }

    public static int findNearestCurrentItem() {
        int i = mc.player.getInventory().selectedSlot;
        if (i == 8) return 7;
        if (i == 0) return 1;
        return i - 1;
    }

    public int getItemSlot() {
        if (mc.player == null || mc.world == null) return -1;

        SearchInvResult gapple = InventoryUtility.findItemInInventory(Items.ENCHANTED_GOLDEN_APPLE);
        SearchInvResult crapple = InventoryUtility.findItemInInventory(Items.GOLDEN_APPLE);
        SearchInvResult shield = InventoryUtility.findItemInInventory(Items.SHIELD);
        Item offHandItem = mc.player.getOffHandStack().getItem();

        Item item = null;
        switch (offhand.getValue()) {
            case Totem -> {
                if (offHandItem != Items.TOTEM_OF_UNDYING && !mc.player.getOffHandStack().isEmpty())
                    prevItem = offHandItem;
                item = prevItem;

                if (bindSwap.getValue().isEnabled())
                    if (isKeyPressed(swapButton) && bindDelay.every(250)) {
                        switch (swapMode.getValue()) {
                            case BallShield -> { if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD) item = Items.PLAYER_HEAD; else item = Items.SHIELD; }
                            case GappleBall -> { if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.GOLDEN_APPLE) item = Items.PLAYER_HEAD; else item = Items.GOLDEN_APPLE; }
                            case GappleShield -> { if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD) item = Items.GOLDEN_APPLE; else item = Items.SHIELD; }
                            case BallTotem -> { if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.TOTEM_OF_UNDYING) item = Items.PLAYER_HEAD; else item = Items.TOTEM_OF_UNDYING; }
                        }
                        prevItem = item;
                    }
            }
            case Crystal -> item = Items.END_CRYSTAL;
            case GApple -> {
                if (crappleSpoof.getValue()) {
                    if (mc.player.hasStatusEffect(StatusEffects.ABSORPTION) && mc.player.getStatusEffect(StatusEffects.ABSORPTION).getAmplifier() > 2) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
                        else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                    }
                } else {
                    if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                    else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
                }
            }
            case Shield -> {
                if (shield.found() || offHandItem == Items.SHIELD) {
                    if (getTriggerHealth() <= healthS.getValue()) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (!mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) item = Items.SHIELD;
                        else {
                            if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                            else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
                        }
                    }
                } else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
            }
        }

        if (getTriggerHealth() <= healthF.getValue() && (InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING).found() || offHandItem == Items.TOTEM_OF_UNDYING))
            item = Items.TOTEM_OF_UNDYING;

        if (!rcGap.is(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !(offHandItem instanceof ShieldItem)) {
            if (rcGap.is(RCGap.Always) || (rcGap.is(RCGap.OnlySafe) && getTriggerHealth() > healthF.getValue())) {
                if (crapple.found() || offHandItem == Items.GOLDEN_APPLE) item = Items.GOLDEN_APPLE;
                if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE) item = Items.ENCHANTED_GOLDEN_APPLE;
            }
        }

        if (onFall.getValue() && (getTriggerHealth()) - (((mc.player.fallDistance - 3) / 2F) + 3.5F) < 0.5) item = Items.TOTEM_OF_UNDYING;
        if (onElytra.getValue() && mc.player.isFallFlying()) item = Items.TOTEM_OF_UNDYING;

        if (onCrystalInHand.getValue()) {
            for (PlayerEntity pl : Managers.ASYNC.getAsyncPlayers()) {
                if (Managers.FRIEND.isFriend(pl) || pl == mc.player) continue;
                if (getPlayerPos().squaredDistanceTo(pl.getPos()) < 36) {
                    if (pl.getMainHandStack().getItem() == Items.OBSIDIAN || pl.getMainHandStack().getItem() == Items.END_CRYSTAL
                            || pl.getOffHandStack().getItem() == Items.OBSIDIAN || pl.getOffHandStack().getItem() == Items.END_CRYSTAL)
                        item = Items.TOTEM_OF_UNDYING;
                }
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive()) continue;
            if (getPlayerPos().squaredDistanceTo(entity.getPos()) > 36) continue;

            if (onCrystal.getValue() && entity instanceof EndCrystalEntity) {
                if (getTriggerHealth() - ExplosionUtility.getExplosionDamageWPredict(entity.getPos(), mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < 0.5) {
                    item = Items.TOTEM_OF_UNDYING; break;
                }
            }
            if (onTnt.getValue() && entity instanceof TntEntity) { item = Items.TOTEM_OF_UNDYING; break; }
            if (onMinecartTnt.getValue() && entity instanceof net.minecraft.entity.vehicle.TntMinecartEntity) { item = Items.TOTEM_OF_UNDYING; break; }
            if (onCreeper.getValue() && entity instanceof CreeperEntity) { item = Items.TOTEM_OF_UNDYING; break; }
        }

        if (onAnchor.getValue()) {
            for (int x = -6; x <= 6; x++)
                for (int y = -6; y <= 6; y++)
                    for (int z = -6; z <= 6; z++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        if (mc.world.getBlockState(bp).getBlock() == Blocks.RESPAWN_ANCHOR) {
                            item = Items.TOTEM_OF_UNDYING; break;
                        }
                    }
        }

        int itemSlot = -1;
        lastTargetItem = item;
        for (int i = 9; i < 45; i++) {
            if (mc.player.getOffHandStack().getItem() == item) return -1;
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem().equals(item)) {
                itemSlot = i >= 36 ? i - 36 : i; break;
            }
        }

        if (item == mc.player.getMainHandStack().getItem() && mc.options.useKey.isPressed()) return -1;
        return itemSlot;
    }

    private Vec3d getPlayerPos() {
        return ModuleManager.blink.isEnabled() ? Blink.lastPos : mc.player.getPos();
    }

    @Override
    public void onDisable() {
        guardianCooldown = -1;
        guardianRetryCount = 0;
        guardianExpectedItem = null;
        isSwapping = false;
        super.onDisable();
    }
}

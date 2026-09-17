package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import mira.client.core.InputBlocker;
import mira.client.core.Managers;
import mira.client.events.impl.EventSync;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.Timer;
import mira.client.utility.interfaces.ICrystal;
import mira.client.utility.math.PredictUtility;
import mira.client.utility.player.InteractionUtility;
import mira.client.utility.player.InventoryUtility;
import mira.client.utility.player.SearchInvResult;
import mira.client.utility.world.ExplosionUtility;

public class AuraCrystal extends Module {
    private final Setting<Float> targetRange = new Setting<>("TargetRange", 6f, 2f, 15f);
    private final Setting<Boolean> ignoreFriends = new Setting<>("IgnoreFriends", true);
    private final Setting<Boolean> placeObsidian = new Setting<>("PlaceObsidian", true);
    private final Setting<Float> range = new Setting<>("Range", 5f, 1f, 7f);
    private final Setting<Integer> delay = new Setting<>("Delay", 2, 0, 20);
    private final Setting<Boolean> chain = new Setting<>("Chain", true);

    private final Setting<Integer> placeRadius = new Setting<>("PlaceRadius", 2, 1, 4);
    private final Setting<Integer> predictTicks = new Setting<>("PredictTicks", 1, 0, 5);
    private final Setting<Float> minDamage = new Setting<>("MinDamage", 6f, 0f, 36f);

    private final Setting<Float> selfSafeDistance = new Setting<>("SelfSafeDistance", 3f, 0f, 6f);
    private final Setting<Boolean> ignoreSelfDamage = new Setting<>("IgnoreSelfDamage", false);
    private final Setting<Float> maxSelfDamage = new Setting<>("MaxSelfDamage", 8f, 0f, 36f, v -> !ignoreSelfDamage.getValue());

    private final Setting<Boolean> returnSlot = new Setting<>("ReturnSlot", true);
    private final Setting<Boolean> swing = new Setting<>("Swing", true);

    private final Timer actionTimer = new Timer();
    private final Timer targetScanTimer = new Timer();

    private State state = State.IDLE;
    private PlayerEntity currentTarget;
    private BlockPos obsidianPos;
    private BlockPos crystalPos;
    private int prevSlot;
    private int waitTicks;
    private float lastTargetDamage;

    private static final String SWITCH_BLOCK_OWNER = "auracrystal_switch";
    private static final long LEGIT_SWITCH_DELAY_MS = 5L;
    private boolean switchPending;
    private int switchPendingSlot = -1;
    private long switchPendingAt = -1L;

    public AuraCrystal() {
        super("AuraCrystal", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public String getDisplayInfo() {
        return lastTargetDamage > 0f ? String.format("%.1f", lastTargetDamage) : null;
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (fullNullCheck()) return;

        if (state == State.IDLE && targetScanTimer.passedMs(150)) {
            targetScanTimer.reset();
            currentTarget = findTarget();
            if (currentTarget != null) start();
        }

        if (state == State.IDLE) return;
        if (!actionTimer.passedMs(delay.getValue() * 50L)) return;

        switch (state) {
            case PLACING_OBSIDIAN -> doPlaceObsidian();
            case PLACING_CRYSTAL -> doPlaceCrystal();
            case WAIT_SPAWN -> doWaitSpawn();
            case BREAKING -> doBreak();
        }
    }

    private PlayerEntity findTarget() {
        if (mc.world == null || mc.player == null) return null;

        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || !player.isAlive()) continue;
            if (ignoreFriends.getValue() && Managers.FRIEND.isFriend(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > targetRange.getValue()) continue;

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    private void start() {
        prevSlot = mc.player.getInventory().selectedSlot;
        obsidianPos = null;
        crystalPos = null;
        waitTicks = 0;

        Placement placement = findBestPlacement(currentTarget);
        if (placement == null) {
            reset();
            return;
        }

        lastTargetDamage = placement.targetDamage();
        crystalPos = placement.crystalPos();

        if (mc.world.getBlockState(placement.basePos()).isOf(Blocks.OBSIDIAN) || mc.world.getBlockState(placement.basePos()).isOf(Blocks.BEDROCK)) {
            state = State.PLACING_CRYSTAL;
        } else if (placeObsidian.getValue()) {
            obsidianPos = placement.basePos();
            state = State.PLACING_OBSIDIAN;
        } else {
            reset();
            return;
        }

        actionTimer.reset();
    }

    private record Placement(BlockPos basePos, BlockPos crystalPos, float targetDamage, float selfDamage, float score) {}

    private Placement findBestPlacement(PlayerEntity target) {
        if (mc.world == null || mc.player == null || target == null) return null;

        Vec3d predictedPos = predictTicks.getValue() > 0 ? PredictUtility.predictPosition(target, predictTicks.getValue()) : null;
        if (predictedPos == null) predictedPos = target.getPos();

        BlockPos center = BlockPos.ofFloored(predictedPos);
        int radius = placeRadius.getValue();

        Placement best = null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos base = center.add(dx, dy, dz);

                    if (mc.player.distanceTo(base.toCenterPos()) > range.getValue() + 2f) continue;
                    if (!selfSafetyPrefilter(base)) continue;

                    BlockPos crystalSpot;
                    if (mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN) || mc.world.getBlockState(base).isOf(Blocks.BEDROCK)) {
                        crystalSpot = base.up();
                    } else if (placeObsidian.getValue()
                            && mc.world.getBlockState(base).isReplaceable()
                            && !InteractionUtility.getSupportBlocks(base).isEmpty()) {
                        crystalSpot = base.up();
                    } else {
                        continue;
                    }

                    if (!mc.world.getBlockState(crystalSpot).isReplaceable() || !mc.world.getBlockState(crystalSpot.up()).isReplaceable())
                        continue;

                    Vec3d explosionPos = crystalSpot.toCenterPos();
                    float targetDamage = ExplosionUtility.getAutoCrystalDamage(explosionPos, target, predictTicks.getValue(), true);
                    if (targetDamage < minDamage.getValue()) continue;

                    float selfDamage = ignoreSelfDamage.getValue() ? 0f : ExplosionUtility.getSelfExplosionDamage(explosionPos, 0, true);
                    if (!ignoreSelfDamage.getValue() && selfDamage > maxSelfDamage.getValue()) continue;

                    float score = targetDamage - selfDamage * 0.5f;

                    if (best == null || score > best.score()) {
                        best = new Placement(base, crystalSpot, targetDamage, selfDamage, score);
                    }
                }
            }
        }

        return best;
    }

    private boolean selfSafetyPrefilter(BlockPos pos) {
        if (ignoreSelfDamage.getValue() || mc.player == null) return true;
        return mc.player.getPos().distanceTo(pos.toCenterPos()) >= selfSafeDistance.getValue();
    }

    private boolean ensureSlot(int slot) {
        if (mc.player == null) return false;
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
                InventoryUtility.switchTo(slot);
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

    private void doPlaceObsidian() {
        if (currentTarget == null || currentTarget.isRemoved() || !currentTarget.isAlive() || obsidianPos == null) {
            finish();
            return;
        }

        SearchInvResult obsResult = InventoryUtility.findBlockInHotBar(Blocks.OBSIDIAN);
        if (!obsResult.found()) {
            sendMessage("Het obsidian!");
            finish();
            return;
        }

        if (mc.world.getBlockState(obsidianPos.up()).isReplaceable() && mc.world.getBlockState(obsidianPos.up(2)).isReplaceable()) {
            if (!mc.world.getBlockState(obsidianPos).isReplaceable()) {
                finish();
                return;
            }
        } else {
            finish();
            return;
        }

        if (!ensureSlot(obsResult.slot())) return;

        boolean placed = InteractionUtility.placeBlock(
                obsidianPos,
                InteractionUtility.Rotate.None,
                InteractionUtility.Interact.Strict,
                InteractionUtility.PlaceMode.Normal,
                obsResult,
                false,
                false
        );

        if (placed) {
            state = State.PLACING_CRYSTAL;
        } else {
            finish();
        }

        actionTimer.reset();
    }

    private void doPlaceCrystal() {
        if (currentTarget == null || currentTarget.isRemoved() || !currentTarget.isAlive() || crystalPos == null) {
            finish();
            return;
        }

        BlockPos basePos = crystalPos.down();
        if (mc.world.getBlockState(basePos).getBlock() != Blocks.OBSIDIAN && mc.world.getBlockState(basePos).getBlock() != Blocks.BEDROCK) {
            finish();
            return;
        }

        SearchInvResult crystalResult = InventoryUtility.findItemInHotBar(Items.END_CRYSTAL);
        if (!crystalResult.found()) {
            sendMessage("Het end crystal!");
            finish();
            return;
        }

        if (!ensureSlot(crystalResult.slot())) return;

        Vec3d hitPos = basePos.toCenterPos().add(0, 0.5, 0);
        BlockHitResult placeHit = new BlockHitResult(hitPos, Direction.UP, basePos, false);

        float[] angle = InteractionUtility.calculateAngle(hitPos);
        mc.player.setYaw(angle[0]);
        mc.player.setPitch(angle[1]);

        if (mc.interactionManager != null)
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);

        if (swing.getValue())
            mc.player.swingHand(Hand.MAIN_HAND);

        waitTicks = 0;
        state = State.WAIT_SPAWN;
        actionTimer.reset();
    }

    private void doWaitSpawn() {
        EndCrystalEntity crystal = findCrystalAt(crystalPos);
        if (crystal != null) {
            state = State.BREAKING;
            actionTimer.reset();
            return;
        }

        waitTicks++;
        if (waitTicks > 3) {
            finish();
            return;
        }

        actionTimer.reset();
    }

    private void doBreak() {
        if (crystalPos == null) {
            finish();
            return;
        }

        EndCrystalEntity crystal = findCrystalAt(crystalPos);
        if (crystal == null || mc.interactionManager == null) {
            finish();
            return;
        }

        if (!(crystal instanceof ICrystal ic) || !ic.canAttack()) {
            actionTimer.reset();
            return;
        }

        float[] angle = InteractionUtility.calculateAngle(crystal.getPos());
        mc.player.setYaw(angle[0]);
        mc.player.setPitch(angle[1]);

        mc.interactionManager.attackEntity(mc.player, crystal);
        ic.attack();

        if (swing.getValue())
            mc.player.swingHand(Hand.MAIN_HAND);

        finish();
    }

    private EndCrystalEntity findCrystalAt(BlockPos pos) {
        if (mc.world == null || pos == null) return null;
        Box box = new Box(pos).expand(0.6);
        return mc.world.getEntitiesByClass(EndCrystalEntity.class, box, e -> true)
                .stream().findFirst().orElse(null);
    }

    private void finish() {
        clearSwitchPending();

        boolean canChain = chain.getValue()
                && currentTarget != null
                && !currentTarget.isRemoved()
                && currentTarget.isAlive()
                && mc.player != null
                && mc.player.distanceTo(currentTarget) <= targetRange.getValue();

        if (!canChain && returnSlot.getValue() && mc.player != null)
            InventoryUtility.switchTo(prevSlot);

        reset();

        if (canChain) {
            PlayerEntity next = findTarget();
            if (next != null) {
                currentTarget = next;
                start();
            }
        }
    }

    private void reset() {
        state = State.IDLE;
        currentTarget = null;
        obsidianPos = null;
        crystalPos = null;
        waitTicks = 0;
    }

    private enum State {
        IDLE,
        PLACING_OBSIDIAN,
        PLACING_CRYSTAL,
        WAIT_SPAWN,
        BREAKING
    }
}

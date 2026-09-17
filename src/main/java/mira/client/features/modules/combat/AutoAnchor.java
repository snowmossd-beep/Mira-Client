package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import mira.client.core.InputBlocker;
import mira.client.core.Managers;
import mira.client.events.impl.EventFixVelocity;
import mira.client.events.impl.EventKeyboardInput;
import mira.client.events.impl.EventSync;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.setting.impl.Bind;
import mira.client.utility.Timer;
import mira.client.utility.player.InteractionUtility;
import mira.client.utility.player.InventoryUtility;
import mira.client.utility.player.SearchInvResult;

import org.jetbrains.annotations.Nullable;
import java.util.Random;

public class AutoAnchor extends Module {
    private final Setting<Bind> placeKey = new Setting<>("PlaceKeybind", new Bind(-1, false, false));
    private final Setting<Boolean> changeLook = new Setting<>("ChangeLook", false);
    private final Setting<Boolean> autoTarget = new Setting<>("AuraAnchor", false);
    private final Setting<Float> targetRange = new Setting<>("TargetRange", 6f, 2f, 15f);
    private final Setting<Boolean> ignoreFriends = new Setting<>("IgnoreFriends", true);
    private final Setting<Integer> charges = new Setting<>("Charges", 1, 1, 4);
    private final Setting<Integer> actionDelay = new Setting<>("ActionDelay", 50, 0, 200);
    private final Setting<Float> range = new Setting<>("Range", 5f, 1f, 7f);
    private final Setting<Boolean> returnSlot = new Setting<>("ReturnSlot", true);
    private final Setting<Integer> triggerSlot = new Setting<>("TriggerSlot", 8, 0, 8);
    private final Setting<Boolean> swing = new Setting<>("Swing", true);
    private final Setting<Boolean> autoTrigger = new Setting<>("AutoTrigger", true);
    private final Setting<Boolean> shield = new Setting<>("Shield", true);
    private final Setting<Boolean> randomDelay = new Setting<>("RandomDelay", true);
    private final Setting<Integer> maxJitter = new Setting<>("MaxJitter", 30, 0, 100);

    private final Random random = new Random();
    private final Timer actionTimer = new Timer();
    private final Timer targetScanTimer = new Timer();
    private State state = State.IDLE;
    private BlockPos anchorPos;
    private BlockHitResult cachedPlaceHit;
    private BlockPos cachedTargetPos;
    private BlockPos pendingShieldPos;
    private PlayerEntity currentTarget;
    private int chargesDone;
    private int prevSlot;
    private boolean keyWasDown;

    private volatile float[] silentRotation = null;
    private volatile boolean rotating = false;

    private static final String SWITCH_BLOCK_OWNER = "autoanchor_switch";
    private static final long SWITCH_DELAY_MS = 5L;
    private boolean switchPending;
    private int switchPendingSlot = -1;
    private long switchPendingAt = -1L;

    public AutoAnchor() {
        super("AutoAnchor", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        if (state != State.IDLE && returnSlot.getValue() && mc.player != null) {
            InventoryUtility.switchTo(prevSlot);
        }
        reset();
        clearSwitchPending();
        silentRotation = null;
        rotating = false;
        cachedTargetPos = null;
        cachedPlaceHit = null;
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (fullNullCheck()) return;

        if (rotating && silentRotation != null) {
            mc.player.setYaw(silentRotation[0]);
            mc.player.setPitch(silentRotation[1]);
        }

        boolean keyDown = isKeyPressed(placeKey);
        boolean shouldStart = keyDown && !keyWasDown;
        if (shouldStart && state == State.IDLE) start(null);
        keyWasDown = keyDown;

        if (autoTarget.getValue() && state == State.IDLE && targetScanTimer.passedMs(200 + random.nextInt(100))) {
            targetScanTimer.reset();
            PlayerEntity target = findTarget();
            if (target != null) start(target);
        }

        if (state == State.IDLE) return;
        if (!actionTimer.passedMs(getDelayWithJitter())) return;

        if (state == State.PLACING) doPlace();
        else if (state == State.CHARGING) doCharge();
        else if (state == State.SHIELDING) doShield();
        else if (state == State.ATTACKING) doAttack();
    }

    @EventHandler(priority = -200)
    public void onPlayerMove(EventFixVelocity event) {
        if (changeLook.getValue()) return;
        float[] rotation = getRotation();
        if (rotation != null) {
            event.setVelocity(fixMovement(rotation[0], event.getMovementInput(), event.getSpeed()));
        }
    }

    @EventHandler(priority = -200)
    public void onKeyboardInput(EventKeyboardInput event) {
        if (changeLook.getValue()) return;
        float[] rotation = getRotation();
        if (rotation != null) {
            float moveForward = mc.player.input.movementForward;
            float moveSideways = mc.player.input.movementSideways;
            float delta = (mc.player.getYaw() - rotation[0]) * (float) (Math.PI / 180.0);
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            mc.player.input.movementSideways = Math.round(moveSideways * cos - moveForward * sin);
            mc.player.input.movementForward = Math.round(moveForward * cos + moveSideways * sin);
        }
    }

    private long getDelayWithJitter() {
        if (!randomDelay.getValue()) return actionDelay.getValue();
        return actionDelay.getValue() + random.nextInt(maxJitter.getValue() + 1);
    }

    private void applyRotation(float[] angle) {
        silentRotation = angle;
        rotating = true;
        if (changeLook.getValue()) {
            mc.player.setYaw(angle[0]);
            mc.player.setPitch(angle[1]);
        }
    }

    private void endRotation() {
        silentRotation = null;
        rotating = false;
    }

    @Nullable
    private float[] getRotation() {
        float[] rotation = silentRotation;
        return !changeLook.getValue() && rotating && rotation != null ? rotation : null;
    }

    private Vec3d fixMovement(float yaw, Vec3d movementInput, float speed) {
        double lengthSquared = movementInput.lengthSquared();
        if (lengthSquared < 1.0E-7) {
            return Vec3d.ZERO;
        }
        Vec3d movement = (lengthSquared > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float sin = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
        float cos = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
        return new Vec3d(movement.x * cos - movement.z * sin, movement.y, movement.z * cos + movement.x * sin);
    }

    private void runWithInteractionRotation(float[] angle, Runnable action) {
        if (mc.player == null) return;
        if (!changeLook.getValue() && angle != null) {
            float prevYaw = mc.player.getYaw();
            float prevPitch = mc.player.getPitch();
            mc.player.setYaw(angle[0]);
            mc.player.setPitch(angle[1]);
            try {
                action.run();
            } finally {
                mc.player.setYaw(prevYaw);
                mc.player.setPitch(prevPitch);
            }
        } else {
            action.run();
        }
    }

    private void interactWithBlock(BlockHitResult hit) {
        if (mc.player == null || mc.interactionManager == null) return;

        float[] angle = InteractionUtility.calculateAngle(hit.getPos());
        applyRotation(angle);

        runWithInteractionRotation(angle, () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        });

        if (swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
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

            if (dist < nearestDist && random.nextFloat() > 0.15f) {
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    private boolean ensureSlot(int slot) {
        if (mc.player == null) return false;
        if (mc.player.getInventory().selectedSlot == slot) {
            clearSwitchPending();
            return true;
        }

        if (!switchPending) {
            InputBlocker.block(SWITCH_BLOCK_OWNER);
            switchPending = true;
            switchPendingSlot = slot;
            switchPendingAt = System.currentTimeMillis() + SWITCH_DELAY_MS + random.nextInt(10);
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

    private void start(PlayerEntity target) {
        SearchInvResult anchorResult = InventoryUtility.findBlockInHotBar(Blocks.RESPAWN_ANCHOR);
        if (!anchorResult.found()) {
            sendMessage("Khong tim thay neo hoi sinh trong hotbar!");
            return;
        }

        prevSlot = mc.player.getInventory().selectedSlot;
        chargesDone = 0;
        anchorPos = null;
        currentTarget = target;
        state = State.PLACING;
        actionTimer.reset();
    }

    private void doPlace() {
        SearchInvResult anchorResult = InventoryUtility.findBlockInHotBar(Blocks.RESPAWN_ANCHOR);
        if (!anchorResult.found()) {
            sendMessage("Het neo hoi sinh!");
            finish();
            return;
        }

        if (cachedTargetPos == null) {
            BlockPos targetPos;
            BlockHitResult placeHit;

            if (currentTarget != null) {
                if (currentTarget.isRemoved() || !currentTarget.isAlive()
                        || mc.player.distanceTo(currentTarget) > targetRange.getValue() + 2f) {
                    finish();
                    return;
                }

                placeHit = findPlacementNear(currentTarget);
                if (placeHit == null) {
                    finish();
                    return;
                }

                targetPos = placeHit.getBlockPos().offset(placeHit.getSide());
            } else {
                BlockPos firePos = findFireAlongLook();
                if (firePos != null) {
                    targetPos = firePos;
                    placeHit = new BlockHitResult(firePos.toCenterPos().add(0, 0.5, 0), Direction.UP, firePos, false);
                } else {
                    BlockHitResult lookHit = rayTraceLookBlock();
                    if (lookHit == null || lookHit.getType() != HitResult.Type.BLOCK) {
                        finish();
                        return;
                    }

                    targetPos = lookHit.getBlockPos().offset(lookHit.getSide());
                    if (mc.world == null) {
                        finish();
                        return;
                    }

                    net.minecraft.block.BlockState ts = mc.world.getBlockState(targetPos);
                    boolean canPlace = ts.isAir() || ts.isReplaceable()
                            || ts.getBlock() == Blocks.FIRE
                            || ts.getBlock() == Blocks.SOUL_FIRE;
                    if (!canPlace) {
                        finish();
                        return;
                    }

                    placeHit = new BlockHitResult(lookHit.getPos(), lookHit.getSide(), lookHit.getBlockPos(), false);
                }
            }

            cachedTargetPos = targetPos;
            cachedPlaceHit = placeHit;
        }

        BlockPos targetPos = cachedTargetPos;
        BlockHitResult placeHit = cachedPlaceHit;

        if (!ensureSlot(anchorResult.slot())) return;

        if (mc.world.getBlockState(targetPos).getBlock() == Blocks.RESPAWN_ANCHOR) {
            anchorPos = targetPos;
            cachedTargetPos = null;
            cachedPlaceHit = null;
            state = State.CHARGING;
            actionTimer.reset();
            return;
        }

        interactWithBlock(placeHit);
        anchorPos = targetPos;
        cachedTargetPos = null;
        cachedPlaceHit = null;
        state = State.CHARGING;
        actionTimer.reset();
    }

    private BlockHitResult findPlacementNear(PlayerEntity target) {
        if (mc.world == null || mc.player == null) return null;

        BlockPos feet = BlockPos.ofFloored(target.getPos());
        BlockPos pillarBase = feet.down();

        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        candidates.add(feet.north());
        candidates.add(feet.south());
        candidates.add(feet.east());
        candidates.add(feet.west());
        candidates.add(pillarBase.north());
        candidates.add(pillarBase.south());
        candidates.add(pillarBase.east());
        candidates.add(pillarBase.west());

        java.util.Collections.shuffle(candidates, random);

        for (BlockPos pos : candidates) {
            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > (range.getValue() + 2f) * (range.getValue() + 2f))
                continue;

            net.minecraft.block.BlockState posState = mc.world.getBlockState(pos);
            boolean posPlaceable = posState.isAir() || posState.isReplaceable()
                    || posState.getBlock() == Blocks.FIRE
                    || posState.getBlock() == Blocks.SOUL_FIRE;
            if (!posPlaceable) continue;

            net.minecraft.block.BlockState topState = mc.world.getBlockState(pos.up());
            boolean topFree = topState.isAir() || topState.isReplaceable()
                    || topState.getBlock() == Blocks.FIRE
                    || topState.getBlock() == Blocks.SOUL_FIRE;
            if (!topFree) continue;

            BlockHitResult check = InteractionUtility.getPlaceResult(pos, InteractionUtility.Interact.Strict, false);
            if (check == null) continue;

            return check;
        }

        return null;
    }

    private void doCharge() {
        if (mc.world == null
                || anchorPos == null
                || chargesDone >= charges.getValue()
                || mc.world.getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
            finish();
            return;
        }

        SearchInvResult glowResult = InventoryUtility.findItemInHotBar(Items.GLOWSTONE);
        if (!glowResult.found()) {
            sendMessage("Het da phat sang!");
            finish();
            return;
        }

        if (!ensureSlot(glowResult.slot())) return;

        BlockHitResult chargeHit = InteractionUtility.getPlaceResult(anchorPos, InteractionUtility.Interact.Strict, false);
        if (chargeHit == null)
            chargeHit = new BlockHitResult(anchorPos.toCenterPos().add(0, 0.5, 0), Direction.UP, anchorPos, false);

        if (chargesDone > 0) {
        }

        interactWithBlock(chargeHit);
        chargesDone++;

        if (chargesDone >= charges.getValue()) {
            if (shield.getValue()) {
                state = State.SHIELDING;
            } else if (autoTrigger.getValue()) {
                state = State.ATTACKING;
            } else {
                finish();
            }
        }

        actionTimer.reset();
    }

    private void doShield() {
        if (mc.world == null || mc.player == null || anchorPos == null) {
            finish();
            return;
        }

        SearchInvResult glowResult = InventoryUtility.findItemInHotBar(Items.GLOWSTONE);
        if (!glowResult.found()) {
            sendMessage("Het da phat sang de lam tam chan!");
            pendingShieldPos = null;
            state = autoTrigger.getValue() ? State.ATTACKING : State.IDLE;
            if (state == State.IDLE) finish();
            actionTimer.reset();
            return;
        }

        Vec3d toPlayer = mc.player.getPos().subtract(anchorPos.toCenterPos());
        if (pendingShieldPos == null) {
            Direction shieldDir = Direction.getFacing(toPlayer.x, toPlayer.y, toPlayer.z);
            pendingShieldPos = anchorPos.offset(shieldDir);
        }
        BlockPos shieldPos = pendingShieldPos;

        if (!ensureSlot(glowResult.slot())) return;

        if (mc.world.getBlockState(shieldPos).isReplaceable()) {
            float[] angle = InteractionUtility.calculateAngle(shieldPos.toCenterPos());
            applyRotation(angle);

            runWithInteractionRotation(angle, () -> {
                InteractionUtility.placeBlock(
                        shieldPos,
                        InteractionUtility.Rotate.None,
                        InteractionUtility.Interact.Vanilla,
                        InteractionUtility.PlaceMode.Normal,
                        glowResult,
                        false,
                        false
                );
            });
        }

        pendingShieldPos = null;
        state = autoTrigger.getValue() ? State.ATTACKING : State.IDLE;
        if (state == State.IDLE) finish();
        actionTimer.reset();
    }

    private void doAttack() {
        if (mc.world == null
                || anchorPos == null
                || mc.world.getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR
                || mc.interactionManager == null) {
            finish();
            return;
        }

        if (!ensureSlot(triggerSlot.getValue())) return;

        if (mc.player.getInventory().getStack(triggerSlot.getValue()).isOf(Items.GLOWSTONE)) {
            sendMessage("TriggerSlot dang cam Glowstone -> se khong no! Doi TriggerSlot sang slot khac.");
            finish();
            return;
        }

        if (mc.player.isSneaking())
            mc.player.setSneaking(false);

        BlockHitResult useHit = InteractionUtility.getPlaceResult(anchorPos, InteractionUtility.Interact.Strict, false);
        if (useHit == null)
            useHit = new BlockHitResult(anchorPos.toCenterPos().add(0, 0.5, 0), Direction.UP, anchorPos, false);

        final BlockHitResult finalUseHit = useHit;

        float[] angle = InteractionUtility.calculateAngle(finalUseHit.getPos());
        applyRotation(angle);

        runWithInteractionRotation(angle, () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, finalUseHit);
        });

        finish();
    }

    private BlockPos findFireAlongLook() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        double maxDist = range.getValue();
        BlockPos lastPos = null;

        for (double d = 0; d <= maxDist; d += 0.1) {
            BlockPos checkPos = BlockPos.ofFloored(eyePos.add(lookVec.multiply(d)));
            if (checkPos.equals(lastPos)) continue;
            lastPos = checkPos;

            net.minecraft.block.BlockState state = mc.world.getBlockState(checkPos);
            if (state.getBlock() == Blocks.FIRE || state.getBlock() == Blocks.SOUL_FIRE) {
                return checkPos;
            }
            if (state.isSolid() && !state.isReplaceable()) {
                return null;
            }
        }
        return null;
    }

    private BlockHitResult rayTraceLookBlock() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(range.getValue()));

        return mc.world.raycast(new RaycastContext(
                eyePos,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    private void finish() {
        clearSwitchPending();
        endRotation();
        if (returnSlot.getValue() && mc.player != null) {
            InventoryUtility.switchTo(prevSlot);
        }
        reset();
    }

    private void reset() {
        state = State.IDLE;
        anchorPos = null;
        currentTarget = null;
        chargesDone = 0;
        cachedTargetPos = null;
        cachedPlaceHit = null;
        pendingShieldPos = null;
    }

    private enum State {
        IDLE,
        PLACING,
        CHARGING,
        SHIELDING,
        ATTACKING
    }
    }

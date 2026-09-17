package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventMove;
import mira.client.events.impl.EventSync;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.player.MovementUtility;

public class TargetStrafe extends Module {
    public static boolean strafesCheck;
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Velocity);
    public final Setting<Boolean> jump = new Setting<>("Jump", true, v -> this.mode.is(Mode.Default));
    private final Setting<Float> defaultDistance = new Setting<>("Distance", 2.4F, 0.1F, 6.0F, v -> this.mode.is(Mode.Default));
    private final Setting<Float> defaultSpeed = new Setting<>("Speed", 0.4F, 0.05F, 1.0F, v -> this.mode.is(Mode.Default));
    private final Setting<Boolean> damageBoost = new Setting<>("DamageBoost", false, v -> this.mode.is(Mode.Default));
    private final Setting<Float> boostSpeed = new Setting<>(
        "BoostSpeed", 0.5F, 0.1F, 1.5F, v -> this.mode.is(Mode.Default) && this.damageBoost.getValue()
    );
    
    private final Setting<Float> velocitySpeed = new Setting<>("Speed", 0.045F, 0.01F, 0.1F, v -> this.mode.is(Mode.Velocity));
    private final Setting<Float> velocityRange = new Setting<>("Range", 2.2F, 1.0F, 6.0F, v -> this.mode.is(Mode.Velocity));
    private final Setting<Float> smoothing = new Setting<>("Smoothing", 0.65F, 0.1F, 1.0F, v -> this.mode.is(Mode.Velocity));

    private boolean clockwise = true;
    private boolean hasQueuedMotion;
    private double queuedMotionX;
    private double queuedMotionZ;
    private int jumpTicks;

    public TargetStrafe() {
        super("TargetStrafe", Module.Category.COMBAT);
    }

    @Override
    public void onDisable() {
        this.hasQueuedMotion = false;
        strafesCheck = false;
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (event == null) {
            return;
        }
        if (fullNullCheck()) {
            this.hasQueuedMotion = false;
            strafesCheck = false;
            return;
        }
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) {
            this.hasQueuedMotion = false;
            strafesCheck = false;
            return;
        }
        this.jumpTicks = Math.max(0, this.jumpTicks - 1);
        this.hasQueuedMotion = false;
        strafesCheck = false;
        if (this.mode.is(Mode.Velocity)) {
            this.handleVelocityMode(player);
        } else {
            this.handleDefaultMode(player, world);
        }
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (!this.mode.not(Mode.Default) && this.hasQueuedMotion) {
            event.setX(this.queuedMotionX);
            event.setZ(this.queuedMotionZ);
            event.cancel();
            this.hasQueuedMotion = false;
        }
    }

    private void handleVelocityMode(ClientPlayerEntity player) {
        if (!MovementUtility.isMoving()) {
            return;
        }
        if (!(Aura.target instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }

        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0E-4) {
            return;
        }

        double desired = this.velocityRange.getValue();
        
        double tolerance = 0.35;

        double toX = dx / dist;
        double toZ = dz / dist;
        double tanX = this.clockwise ? -toZ : toZ;
        double tanZ = this.clockwise ? toX : -toX;

        double radial = 0.0;
        if (dist > desired + tolerance) {
            radial = Math.min(1.0, (dist - desired) / 2.0); 
        } else if (dist < desired - tolerance) {
            radial = -Math.min(1.0, (desired - dist) / 2.0); 
        }

        double speed = this.velocitySpeed.getValue();
        
        double wishX = (tanX + toX * radial) * speed;
        double wishZ = (tanZ + toZ * radial) * speed;

        double blend = this.smoothing.getValue();
        Vec3d vel = player.getVelocity();
        double newX = vel.x + (wishX - vel.x) * blend;
        double newZ = vel.z + (wishZ - vel.z) * blend;

        if (!Double.isNaN(newX) && !Double.isNaN(newZ)) {
            player.setVelocity(newX, vel.y, newZ);
            strafesCheck = true;
        }
    }

    private void handleDefaultMode(ClientPlayerEntity player, ClientWorld world) {
        if (Aura.target instanceof LivingEntity target && target.isAlive()) {
            if (this.jump.getValue() && player.isOnGround()) {
                mc.options.jumpKey.setPressed(false);
                player.jump();
            }

            float maxDistance = ModuleManager.aura.attackRange.getValue() + ModuleManager.aura.aimRange.getValue();
            double distanceToTarget = player.distanceTo(target);
            if (!(distanceToTarget > maxDistance)) {
                mc.options.forwardKey.setPressed(false);
                float speed = this.defaultSpeed.getValue();
                if (this.damageBoost.getValue() && player.hurtTime > 0 && player.isAlive()) {
                    speed += this.boostSpeed.getValue();
                }

                float clampDist = (float) MathHelper.clamp(distanceToTarget, 0.01F, maxDistance);
                double baseAngle = Math.atan2(player.getZ() - target.getZ(), player.getX() - target.getX());
                float angleStep = MathHelper.clamp(speed / clampDist, 0.01F, 1.0F);
                double orbitAngle = baseAngle + (this.clockwise ? angleStep : -angleStep);
                double orbitX = target.getX() + this.defaultDistance.getValue() * Math.cos(orbitAngle);
                double orbitZ = target.getZ() + this.defaultDistance.getValue() * Math.sin(orbitAngle);
                if (this.isUnsafeAreaAround(orbitX, orbitZ, player, world)) {
                    this.clockwise = !this.clockwise;
                    orbitAngle = baseAngle + (this.clockwise ? angleStep : -angleStep);
                    orbitX = target.getX() + this.defaultDistance.getValue() * Math.cos(orbitAngle);
                    orbitZ = target.getZ() + this.defaultDistance.getValue() * Math.sin(orbitAngle);
                }

                strafesCheck = true;
                double angleTo = Math.toRadians(this.calculateAngleToTarget(orbitX, orbitZ, player));
                this.queuedMotionX = speed * -Math.sin(angleTo);
                this.queuedMotionZ = speed * Math.cos(angleTo);
                this.hasQueuedMotion = !Double.isNaN(this.queuedMotionX) && !Double.isNaN(this.queuedMotionZ);
            }
        }
    }

    private boolean isUnsafeAreaAround(double x, double z, ClientPlayerEntity player, ClientWorld world) {
        if (!player.horizontalCollision && (!mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed() || this.jumpTicks > 0)) {
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            int top = (int) (player.getY() + 4.0);

            for (int y = top; y >= world.getBottomY(); y--) {
                mutable.set(x, y, z);
                BlockState state = world.getBlockState(mutable);
                Block block = state.getBlock();
                if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.COBWEB) {
                    return true;
                }

                if (!state.isAir()) {
                    return false;
                }
            }

            return this.isVoidAboveVoid(x, z, player, world);
        } else {
            this.jumpTicks = 10;
            return true;
        }
    }

    private boolean isVoidAboveVoid(double x, double z, ClientPlayerEntity player, ClientWorld world) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = (int) player.getY(); y > world.getBottomY(); y--) {
            mutable.set(x, y, z);
            if (!world.getBlockState(mutable).isAir()) {
                return false;
            }
        }

        return true;
    }

    private float calculateAngleToTarget(double x, double z, ClientPlayerEntity player) {
        double diffX = x - player.getX();
        double diffZ = z - player.getZ();
        return (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI - 90.0);
    }

    private enum Mode {
        Default,
        Velocity;
    }
                        }

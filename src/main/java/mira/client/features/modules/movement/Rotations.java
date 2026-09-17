package mira.client.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventFixVelocity;
import mira.client.events.impl.EventKeyboardInput;
import mira.client.events.impl.EventPlayerJump;
import mira.client.events.impl.EventPlayerTravel;
import mira.client.features.modules.Module;
import mira.client.features.modules.combat.Aura;
import mira.client.setting.Setting;
import mira.client.utility.player.MovementUtility;

public class Rotations extends Module {
    public Rotations() {
        super("Rotations", Category.MOVEMENT);
    }

    private final Setting<MoveFix> moveFix = new Setting<>("MoveFix", MoveFix.Off);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);

    public final Setting<Boolean> hvhTargetStrafe = new Setting<>("TargetStrafe", false, v -> moveFix.is(MoveFix.HvH));
    public final Setting<StrafeDir> strafeDir = new Setting<>("StrafeDir", StrafeDir.Auto, v -> moveFix.is(MoveFix.HvH) && hvhTargetStrafe.getValue());
    public final Setting<Float> strafeSpeed = new Setting<>("StrafeSpeed", 0.287f, 0.1f, 0.6f, v -> moveFix.is(MoveFix.HvH) && hvhTargetStrafe.getValue());
    public final Setting<Float> strafeRadius = new Setting<>("StrafeRadius", 3.0f, 1.0f, 6.0f, v -> moveFix.is(MoveFix.HvH) && hvhTargetStrafe.getValue());
    public final Setting<PitchMode> hvhPitch = new Setting<>("HvHPitch", PitchMode.Target, v -> moveFix.is(MoveFix.HvH));
    public final Setting<Float> customPitch = new Setting<>("CustomPitch", -45f, -90f, 90f, v -> moveFix.is(MoveFix.HvH) && hvhPitch.is(PitchMode.Custom));

    private enum MoveFix {
        Off, Focused, Free, HvH
    }

    private enum StrafeDir {
        Left, Right, Auto
    }

    private enum PitchMode {
        Target, Down, Custom
    }

    public float fixRotation = Float.NaN;
    private float prevYaw, prevPitch;
    private int currentStrafeDir = 1;
    private boolean wasColliding = false;

    @EventHandler
    public void onJump(EventPlayerJump e) {
        if (Float.isNaN(fixRotation) || moveFix.getValue() == MoveFix.Off || mc.player.isRiding())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            mc.player.setYaw(fixRotation);
        } else {
            mc.player.setYaw(prevYaw);
        }
    }

    @EventHandler
    public void onPlayerMove(EventFixVelocity event) {
        if (moveFix.getValue() == MoveFix.Free) {
            if (Float.isNaN(fixRotation) || mc.player.isRiding())
                return;
            event.setVelocity(fix(fixRotation, event.getMovementInput(), event.getSpeed()));
        }

        if (moveFix.getValue() == MoveFix.HvH && hvhTargetStrafe.getValue()) {
            if (ModuleManager.aura.target == null || mc.player.isRiding())
                return;
            applyTargetStrafe(event);
        }
    }

    @EventHandler
    public void modifyVelocity(EventPlayerTravel e) {
        if (ModuleManager.aura.isEnabled() && ModuleManager.aura.target != null
                && ModuleManager.aura.rotationMode.not(Aura.Mode.None)
                && ModuleManager.aura.elytraTarget.getValue()
                && Managers.PLAYER.ticksElytraFlying > 5) {
            if (e.isPre()) {
                prevYaw = mc.player.getYaw();
                prevPitch = mc.player.getPitch();
                mc.player.setYaw(fixRotation);
                mc.player.setPitch(ModuleManager.aura.rotationPitch);
            } else {
                mc.player.setYaw(prevYaw);
                mc.player.setPitch(prevPitch);
            }
            return;
        }

        if (moveFix.getValue() == MoveFix.Focused && !Float.isNaN(fixRotation) && !mc.player.isRiding()) {
            if (e.isPre()) {
                prevYaw = mc.player.getYaw();
                mc.player.setYaw(fixRotation);
            } else {
                mc.player.setYaw(prevYaw);
            }
        }

        if (moveFix.getValue() == MoveFix.HvH && !Float.isNaN(fixRotation) && !mc.player.isRiding()) {
            if (e.isPre()) {
                prevYaw = mc.player.getYaw();
                prevPitch = mc.player.getPitch();
                mc.player.setYaw(fixRotation);
                mc.player.setPitch(resolveHvhPitch());
            } else {
                mc.player.setYaw(prevYaw);
                mc.player.setPitch(prevPitch);
            }
        }
    }

    @EventHandler
    public void onKeyInput(EventKeyboardInput e) {
        if (moveFix.getValue() == MoveFix.Free) {
            if (Float.isNaN(fixRotation) || mc.player.isRiding())
                return;

            float mF = mc.player.input.movementForward;
            float mS = mc.player.input.movementSideways;
            float delta = (mc.player.getYaw() - fixRotation) * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            mc.player.input.movementSideways = mS * cos - mF * sin;
            mc.player.input.movementForward = mF * cos + mS * sin;
        }
    }

    private void applyTargetStrafe(EventFixVelocity event) {
        if (strafeDir.getValue() == StrafeDir.Auto) {
            boolean colliding = mc.player.horizontalCollision;
            if (colliding && !wasColliding)
                currentStrafeDir *= -1;
            wasColliding = colliding;
        } else {
            currentStrafeDir = strafeDir.getValue() == StrafeDir.Left ? -1 : 1;
        }

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = ModuleManager.aura.target.getPos();

        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.01) return;

        double nx = dx / dist;
        double nz = dz / dist;

        double perpX = -nz * currentStrafeDir;
        double perpZ = nx * currentStrafeDir;

        double radius = strafeRadius.getValue();
        double radiusDiff = dist - radius;
        double radialX = -nx * radiusDiff * 0.15;
        double radialZ = -nz * radiusDiff * 0.15;

        double speed = strafeSpeed.getValue();
        double vx = (perpX * speed + radialX);
        double vz = (perpZ * speed + radialZ);

        double len = Math.sqrt(vx * vx + vz * vz);
        if (len > speed) {
            vx = vx / len * speed;
            vz = vz / len * speed;
        }

        event.setVelocity(new Vec3d(vx, event.getVelocity().y, vz));
    }

    private float resolveHvhPitch() {
        return switch (hvhPitch.getValue()) {
            case Down -> -89.9f;
            case Custom -> customPitch.getValue();
            default -> ModuleManager.aura.rotationPitch;
        };
    }

    private Vec3d fix(float yaw, Vec3d movementInput, float speed) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7)
            return Vec3d.ZERO;
        Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float f = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
        float g = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }
                    }

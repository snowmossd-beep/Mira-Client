package mira.client.features.modules.combat;

import baritone.api.BaritoneAPI;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import mira.client.MiraClient;
import mira.client.core.Core;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventSync;
import mira.client.events.impl.PacketEvent;
import mira.client.events.impl.PlayerUpdateEvent;
import mira.client.gui.notification.Notification;
import mira.client.injection.accesors.ILivingEntity;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.HudEditor;
import mira.client.setting.Setting;
import mira.client.setting.impl.BooleanSettingGroup;
import mira.client.setting.impl.SettingGroup;
import mira.client.utility.Timer;
import mira.client.utility.interfaces.IOtherClientPlayerEntity;
import mira.client.utility.math.MathUtility;
import mira.client.utility.player.InteractionUtility;
import mira.client.utility.player.InventoryUtility;
import mira.client.utility.player.PlayerUtility;
import mira.client.utility.player.SearchInvResult;
import mira.client.utility.render.Render2DEngine;
import mira.client.utility.render.Render3DEngine;
import mira.client.utility.render.TextureStorage;
import mira.client.utility.render.animation.CaptureMark;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.util.UseAction.BLOCK;
import static net.minecraft.util.math.MathHelper.wrapDegrees;
import static mira.client.features.modules.render.ClientSettings.isRu;
import static mira.client.utility.math.MathUtility.random;

public class Aura extends Module {
    public final Setting<Float> attackRange = new Setting<>("Range", 3.1f, 1f, 6.0f);
    public final Setting<Mode> rotationMode = new Setting<>("RotationMode", Mode.Track);
    public final Setting<Integer> interactTicks = new Setting<>("InteractTicks", 3, 1, 10, v -> rotationMode.getValue() == Mode.Interact);
    public final Setting<Float> wallRange = new Setting<>("ThroughWallsRange", 3.1f, 0f, 6.0f);
    public final Setting<Boolean> elytra = new Setting<>("ElytraOverride",false);
    public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 3.1f, 1f, 6.0f, v -> elytra.getValue());
    public final Setting<Float> elytraWallRange = new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f,v -> elytra.getValue());
    public final Setting<WallsBypass> wallsBypass = new Setting<>("WallsBypass", WallsBypass.Off, v -> getWallRange() > 0);
    public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
    public final Setting<Switch> switchMode = new Setting<>("AutoWeapon", Switch.None);
    public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false, v -> switchMode.getValue() != Switch.Silent);
    public final Setting<BooleanSettingGroup> smartCrit = new Setting<>("SmartCrit", new BooleanSettingGroup(true));
    public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(smartCrit);
    public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(smartCrit);
    public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true);
    public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false);
    public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    public final Setting<Boolean> pauseBaritone = new Setting<>("PauseBaritone", false);
    public final Setting<BooleanSettingGroup> oldDelay = new Setting<>("OldDelay", new BooleanSettingGroup(false));
    public final Setting<Integer> minCPS = new Setting<>("MinCPS", 7, 1, 20).addToGroup(oldDelay);
    public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 12, 1, 20).addToGroup(oldDelay);

    public final Setting<ESP> esp = new Setting<>("ESP", ESP.CelkaPasta);

    public final Setting<SettingGroup> wraithGroup = new Setting<>("WraithSettings", new SettingGroup(true, 0), v -> esp.is(ESP.Wraith));
    public final Setting<Integer> wraithLength = new Setting<>("Length", 14, 1, 40, v -> esp.is(ESP.Wraith)).addToGroup(wraithGroup);
    public final Setting<Integer> wraithFactor = new Setting<>("Factor", 8, 1, 20, v -> esp.is(ESP.Wraith)).addToGroup(wraithGroup);
    public final Setting<Float> wraithShaking = new Setting<>("Shaking", 1.8f, 1.5f, 10f, v -> esp.is(ESP.Wraith)).addToGroup(wraithGroup);
    public final Setting<Float> wraithAmplitude = new Setting<>("Amplitude", 3f, 0.1f, 8f, v -> esp.is(ESP.Wraith)).addToGroup(wraithGroup);

    public final Setting<SettingGroup> ghostV1Group = new Setting<>("GhostV1Settings", new SettingGroup(true, 0), v -> esp.is(ESP.GhostV1));
    public final Setting<Float> ghostV1Speed = new Setting<>("Speed", 0.62f, 0.1f, 3.0f, v -> esp.is(ESP.GhostV1)).addToGroup(ghostV1Group);

    public final Setting<SettingGroup> ghostV2Group = new Setting<>("GhostV2Settings", new SettingGroup(true, 0), v -> esp.is(ESP.GhostV2));
    public final Setting<Integer> ghostV2ParticleLimit = new Setting<>("ParticleLimit", 6, 1, 12, v -> esp.is(ESP.GhostV2)).addToGroup(ghostV2Group);
    public final Setting<Float> ghostV2ParticleSize = new Setting<>("ParticleSize", 0.22f, 0.05f, 1.0f, v -> esp.is(ESP.GhostV2)).addToGroup(ghostV2Group);
    public final Setting<Float> ghostV2Length = new Setting<>("Length", 14.0f, 4f, 60f, v -> esp.is(ESP.GhostV2)).addToGroup(ghostV2Group);

    public final Setting<Sort> sort = new Setting<>("Sort", Sort.LowestDistance);
    public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true);
    public final Setting<Boolean> elytraTarget = new Setting<>("ElytraTarget", true);

    public final Setting<SettingGroup> advanced = new Setting<>("Advanced", new SettingGroup(false, 0));
    public final Setting<Float> aimRange = new Setting<>("AimRange", 3.1f, 0f, 6.0f).addToGroup(advanced);
    public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false).addToGroup(advanced);
    public final Setting<Boolean> pauseInInventory = new Setting<>("PauseInInventory", true).addToGroup(advanced);
    public final Setting<Boolean> dropSprint = new Setting<>("DropSprint", true).addToGroup(advanced);
    public final Setting<Boolean> returnSprint = new Setting<>("ReturnSprint", true, v -> dropSprint.getValue()).addToGroup(advanced);
    public final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget).addToGroup(advanced);
    public final Setting<Boolean> grimRayTrace = new Setting<>("GrimRayTrace", true).addToGroup(advanced);
    public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true).addToGroup(advanced);
    public final Setting<Boolean> deathDisable = new Setting<>("DisableOnDeath", true).addToGroup(advanced);
    public final Setting<Boolean> tpDisable = new Setting<>("TPDisable", false).addToGroup(advanced);
    public final Setting<Boolean> pullDown = new Setting<>("FastFall", false).addToGroup(advanced);
    public final Setting<Boolean> onlyJumpBoost = new Setting<>("OnlyJumpBoost", false, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<Float> pullValue = new Setting<>("PullValue", 3f, 0f, 20f, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand).addToGroup(advanced);
    public final Setting<Resolver> resolver = new Setting<>("Resolver", Resolver.Advantage).addToGroup(advanced);
    public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> resolver.is(Resolver.BackTrack)).addToGroup(advanced);
    public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !resolver.is(Resolver.Off)).addToGroup(advanced);
    public final Setting<AccelerateOnHit> accelerateOnHit = new Setting<>("AccelerateOnHit", AccelerateOnHit.Off).addToGroup(advanced);
    public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 65, 1, 180).addToGroup(advanced);
    public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 75, 1, 180).addToGroup(advanced);
    public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1f, 0f, 90f).addToGroup(advanced);
    public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 8f, 1f, 90f).addToGroup(advanced);
    public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65f, 1f, 10f).addToGroup(advanced);
    public final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f).addToGroup(advanced);
    public final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f).addToGroup(advanced);
    public final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20).addToGroup(advanced);
    public final Setting<Float> critFallDistance = new Setting<>("CritFallDistance", 0f, 0f, 1f).addToGroup(advanced);

    public final Setting<SettingGroup> bypass = new Setting<>("Bypass", new SettingGroup(false, 0));
    public final Setting<AntiCheat> antiCheat = new Setting<>("AntiCheat", AntiCheat.None).addToGroup(bypass);
    public final Setting<Boolean> noSwing      = new Setting<>("NoSwing",     false).addToGroup(bypass);
    public final Setting<Boolean> keepSprint   = new Setting<>("KeepSprint",  true).addToGroup(bypass);
    public final Setting<Boolean> randomHitbox = new Setting<>("RandomHitbox", false).addToGroup(bypass);
    public final Setting<Float>   randomHitboxR= new Setting<>("HitboxRange", 0.1f, 0f, 0.5f, v -> randomHitbox.getValue()).addToGroup(bypass);
    public final Setting<Boolean> velocityBypass = new Setting<>("VelocityFix", false).addToGroup(bypass);
    public final Setting<Float>   veloH        = new Setting<>("VeloH", 0.5f, 0f, 1f, v -> velocityBypass.getValue()).addToGroup(bypass);
    public final Setting<Float>   veloV        = new Setting<>("VeloV", 0.5f, 0f, 1f, v -> velocityBypass.getValue()).addToGroup(bypass);
    public final Setting<Boolean> antiKB       = new Setting<>("AntiKB", false).addToGroup(bypass);
    public final Setting<Float>   antiKBH      = new Setting<>("KBKBH", 0.0f, 0f, 1f, v -> antiKB.getValue()).addToGroup(bypass);
    public final Setting<Float>   antiKBV      = new Setting<>("KBKBV", 0.0f, 0f, 1f, v -> antiKB.getValue()).addToGroup(bypass);
    public final Setting<Boolean> randomDelay  = new Setting<>("RandomDelay", true).addToGroup(bypass);
    public final Setting<Integer> randomMin    = new Setting<>("RandomMin", 10, 0, 100, v -> randomDelay.getValue()).addToGroup(bypass);
    public final Setting<Integer> randomMax    = new Setting<>("RandomMax", 40, 0, 200, v -> randomDelay.getValue()).addToGroup(bypass);

    public enum AntiCheat {
        None, NCP, Grim, AAC, Vulcan, Verus, Karhu, Watchdog, Sparky
    }

    public boolean isPredictiveAC() {
        return switch (antiCheat.getValue()) {
            case Grim, Vulcan, AAC -> true;
            default -> false;
        };
    }

    public boolean isStrictCpsAC() {
        return switch (antiCheat.getValue()) {
            case NCP, Watchdog, Sparky -> true;
            default -> false;
        };
    }

    public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(true, 0));
    public final Setting<Boolean> Players = new Setting<>("Players", true).addToGroup(targets);
    public final Setting<Boolean> Mobs = new Setting<>("Mobs", true).addToGroup(targets);
    public final Setting<Boolean> Animals = new Setting<>("Animals", true).addToGroup(targets);
    public final Setting<Boolean> Villagers = new Setting<>("Villagers", true).addToGroup(targets);
    public final Setting<Boolean> Slimes = new Setting<>("Slimes", true).addToGroup(targets);
    public final Setting<Boolean> hostiles = new Setting<>("Hostiles", true).addToGroup(targets);
    public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", true, v -> hostiles.getValue()).addToGroup(targets);
    public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", true).addToGroup(targets);
    public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(targets);
    public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(targets);
    public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(targets);
    public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true).addToGroup(targets);
    public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(targets);
    public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", true).addToGroup(targets);

    public static Entity target;
    public static LivingEntity getTarget() {
        return target instanceof LivingEntity le ? le : null;
    }

    public float rotationYaw;
    public float rotationPitch;
    public float pitchAcceleration = 1f;

    private float mlsacVelocityYaw   = 0f;
    private float mlsacVelocityPitch = 0f;
    private float mlsacLastDeltaYaw   = 0f;
    private float mlsacLastDeltaPitch = 0f;
    private float mlsacGcdAccumYaw   = 0f;
    private float mlsacGcdAccumPitch = 0f;
    private int   mlsacNoisePhase    = 0;

    private Vec3d rotationPoint = Vec3d.ZERO;
    private Vec3d rotationMotion = Vec3d.ZERO;

    private int hitTicks;
    private int trackticks;
    private boolean lookingAtHitbox;

    private final Timer delayTimer = new Timer();
    private final Timer pauseTimer = new Timer();

    public Box resolvedBox;
    static boolean wasTargeted = false;

    public boolean externalPause = false;

    public Aura() {
        super("Aura", Category.COMBAT);
    }

    private float getRange(){
        return elytra.getValue() && mc.player.isFallFlying() ? elytraAttackRange.getValue() : attackRange.getValue();
    }
    private float getWallRange(){
        return elytra.getValue() && mc.player != null && mc.player.isFallFlying() ? elytraWallRange.getValue() : wallRange.getValue();
    }

    public void auraLogic() {
        if (externalPause) return;

        if (!haveWeapon()) {
            target = null;
            return;
        }

        handleKill();
        updateTarget();

        if (target == null) {
            return;
        }

        if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && autoJump.getValue())
            mc.player.jump();

        boolean readyForAttack;

        if (grimRayTrace.getValue()) {
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
            calcRotations(autoCrit());
        } else {
            calcRotations(autoCrit());
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
        }

        if (readyForAttack) {
            if (shieldBreaker(false))
                return;

            boolean[] playerState = preAttack();
            if (!(target instanceof PlayerEntity pl) || !(pl.isUsingItem() && pl.getOffHandStack().getItem() == Items.SHIELD) || ignoreShield.getValue())
                attack();

            postAttack(playerState[0], playerState[1]);
        }
    }

    private boolean haveWeapon() {
        Item handItem = mc.player.getMainHandStack().getItem();
        if (onlyWeapon.getValue()) {
            if (switchMode.getValue() == Switch.None) {
                return handItem instanceof SwordItem || handItem instanceof AxeItem || handItem instanceof MaceItem;
            } else {
                return (InventoryUtility.getSwordHotBar().found() || InventoryUtility.getAxeHotBar().found());
            }
        }
        return true;
    }

    private boolean skipRayTraceCheck() {
        return rotationMode.getValue() == Mode.None || rayTrace.getValue() == RayTrace.OFF
                || rotationMode.is(Mode.Grim)
                || (rotationMode.is(Mode.Interact) && (interactTicks.getValue() <= 1
                || mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext()));
    }

    public void attack() {
        Criticals.cancelCrit = true;
        ModuleManager.criticals.doCrit();
        int prevSlot = switchMethod();
        mc.interactionManager.attackEntity(mc.player, target);
        Criticals.cancelCrit = false;
        swingHand();
        hitTicks = getHitTicks();
        if (prevSlot != -1)
            InventoryUtility.switchTo(prevSlot);
        ModuleManager.autoTotem.notifyAuraHit();
    }

    private boolean @NotNull [] preAttack() {
        boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == BLOCK;
        if (blocking && unpressShield.getValue())
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

        boolean sprint = Core.serverSprint;
        if (sprint && dropSprint.getValue())
            disableSprint();

        if (rotationMode.is(Mode.Grim))
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));

        return new boolean[]{blocking, sprint};
    }

    public void postAttack(boolean block, boolean sprint) {
        if (sprint && returnSprint.getValue() && dropSprint.getValue())
            enableSprint();

        if (block && unpressShield.getValue())
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, rotationYaw, rotationPitch));

        if (rotationMode.is(Mode.Grim))
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
    }

    private void disableSprint() {
        mc.player.setSprinting(false);
        mc.options.sprintKey.setPressed(false);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
    }

    private void enableSprint() {
        mc.player.setSprinting(true);
        mc.options.sprintKey.setPressed(true);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    public void resolvePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).resolve(resolver.getValue());
    }

    public void restorePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).releaseResolver();
    }

    public void handleKill() {
        if (target instanceof LivingEntity && (((LivingEntity) target).getHealth() <= 0 || ((LivingEntity) target).isDead()))
            Managers.NOTIFICATION.publicity("Aura", isRu() ? "Цель успешно нейтрализована!" : "Target successfully neutralized!", 3, Notification.Type.SUCCESS);
    }

    private int switchMethod() {
        int prevSlot = -1;
        SearchInvResult swordResult = InventoryUtility.getSwordHotBar();
        if (swordResult.found() && switchMode.getValue() != Switch.None) {
            if (switchMode.getValue() == Switch.Silent)
                prevSlot = mc.player.getInventory().selectedSlot;
            swordResult.switchTo();
        }

        return prevSlot;
    }

    private int getHitTicks() {
        if (mc.player.getMainHandStack().getItem() instanceof MaceItem)
            return 1;
        return oldDelay.getValue().isEnabled() ? 1 + (int) (20f / random(minCPS.getValue(), maxCPS.getValue())) : (shouldRandomizeDelay() ? (int) MathUtility.random(11, 13) : attackTickLimit.getValue());
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
            return;
        if(pauseBaritone.getValue() && MiraClient.baritone){
            boolean isTargeted = (target != null);
            if (isTargeted && !wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
                wasTargeted = true;
            } else if (!isTargeted && wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
                wasTargeted = false;
            }
        }

        resolvePlayers();
        auraLogic();
        restorePlayers();
        hitTicks--;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
            return;

        if (!haveWeapon())
            return;

        if (target != null && rotationMode.getValue() != Mode.None && rotationMode.getValue() != Mode.Grim) {
            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);
        } else {
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }

        if (oldDelay.getValue().isEnabled())
            if (minCPS.getValue() > maxCPS.getValue())
                minCPS.setValue(maxCPS.getValue());

        if (target != null && pullDown.getValue() && (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !onlyJumpBoost.getValue()))
            mc.player.addVelocity(0f, -pullValue.getValue() / 1000f, 0f);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send e) {
        if (e.getPacket() instanceof PlayerInteractEntityC2SPacket pie && Criticals.getInteractType(pie) != Criticals.InteractType.ATTACK && target != null)
            e.cancel();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof EntityStatusS2CPacket status)
            if (status.getStatus() == 30 && status.getEntity(mc.world) != null && target != null && status.getEntity(mc.world) == target)
                Managers.NOTIFICATION.publicity("Aura", isRu() ? ("Успешно сломали щит игроку " + target.getName().getString()) : ("Succesfully destroyed " + target.getName().getString() + "'s shield"), 2, Notification.Type.SUCCESS);

        if (e.getPacket() instanceof PlayerPositionLookS2CPacket && tpDisable.getValue())
            disable(isRu() ? "Отключаю из-за телепортации!" : "Disabling due to tp!");

        if (e.getPacket() instanceof EntityStatusS2CPacket pac && pac.getStatus() == 3 && pac.getEntity(mc.world) == mc.player && deathDisable.getValue())
            disable(isRu() ? "Отключаю из-за смерти!" : "Disabling due to death!");
    }

    @Override
    public void onEnable() {
        target = null;
        lookingAtHitbox = false;
        externalPause = false;
        rotationPoint = Vec3d.ZERO;
        rotationMotion = Vec3d.ZERO;
        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();
        delayTimer.reset();
    }

    private boolean autoCrit() {
        boolean reasonForSkipCrit =
                !smartCrit.getValue().isEnabled()
                        || mc.player.getAbilities().flying
                        || (mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled())
                        || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                        || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                        || Managers.PLAYER.isInWeb();

        if (hitTicks > 0)
            return false;

        if (pauseInInventory.getValue() && Managers.PLAYER.inInventory)
            return false;

        float cooldownThreshold = mc.player.getMainHandStack().getItem() instanceof MaceItem
                ? 0.5f
                : attackCooldown.getValue();
        if (getAttackCooldown() < cooldownThreshold && !oldDelay.getValue().isEnabled())
            return false;

        if (ModuleManager.criticals.isEnabled() && ModuleManager.criticals.mode.is(Criticals.Mode.Grim))
            return true;

        boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
        boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.player.isOnGround();

        if (!mc.options.jumpKey.isPressed() && mergeWithTargetStrafe && mergeWithSpeed && !onlySpace.getValue() && !autoJump.getValue())
            return true;

        if (mc.player.isInLava() || mc.player.isSubmergedInWater())
            return true;

        if (!mc.options.jumpKey.isPressed() && isAboveWater())
            return true;

        if (mc.player.fallDistance > 1 && mc.player.fallDistance < 1.14)
            return false;

        if (!reasonForSkipCrit)
            return !mc.player.isOnGround() && mc.player.fallDistance > (shouldRandomizeFallDistance() ? MathUtility.random(0.15f, 0.7f) : critFallDistance.getValue());
        return true;
    }

    private boolean shieldBreaker(boolean instant) {
        int axeSlot = InventoryUtility.getAxe().slot();
        if (axeSlot == -1) return false;
        if (!shieldBreaker.getValue()) return false;
        if (!(target instanceof PlayerEntity)) return false;
        if (!((PlayerEntity) target).isUsingItem() && !instant) return false;
        if (((PlayerEntity) target).getOffHandStack().getItem() != Items.SHIELD && ((PlayerEntity) target).getMainHandStack().getItem() != Items.SHIELD)
            return false;

        if (axeSlot >= 9) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.interactionManager.attackEntity(mc.player, target);
            swingHand();
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        } else {
            sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
            mc.interactionManager.attackEntity(mc.player, target);
            swingHand();
            sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        }
        hitTicks = 10;
        return true;
    }

    private void swingHand() {
        switch (attackHand.getValue()) {
            case OffHand -> mc.player.swingHand(Hand.OFF_HAND);
            case MainHand -> mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    public boolean isAboveWater() {
        return mc.player.isSubmergedInWater() || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos().add(0, -0.4, 0))).getBlock() == Blocks.WATER;
    }

    public float getAttackCooldownProgressPerTick() {
        return (float) (1.0 / mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) * (20.0 * MiraClient.TICK_TIMER * (tpsSync.getValue() ? Managers.SERVER.getTPSFactor() : 1f)));
    }

    public float getAttackCooldown() {
        return MathHelper.clamp(((float) ((ILivingEntity) mc.player).getLastAttackedTicks() + attackBaseTime.getValue()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }

    private void updateTarget() {
        Entity candidat = findTarget();

        if (target == null) {
            target = candidat;
            return;
        }

        if (sort.getValue() == Sort.FOV || !lockTarget.getValue())
            target = candidat;

        if (candidat instanceof ProjectileEntity)
            target = candidat;

        if (skipEntity(target))
            target = null;
    }

    private void calcRotations(boolean ready) {
        if (ready) {
            trackticks = (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext() ? 1 : interactTicks.getValue());
        } else if (trackticks > 0) {
            trackticks--;
        }

        if (target == null)
            return;

        Vec3d targetVec;

        if (mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled()) targetVec = target.getEyePos();
        else targetVec = getLegitLook(target);

        if (targetVec == null)
            return;

        pitchAcceleration = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange() + aimRange.getValue(), getRange() + aimRange.getValue(), rayTrace.getValue())
                ? aimedPitchStep.getValue() : pitchAcceleration < maxPitchStep.getValue() ? pitchAcceleration * pitchAccelerate.getValue() : maxPitchStep.getValue();

        float delta_yaw = wrapDegrees((float) wrapDegrees(Math.toDegrees(Math.atan2(targetVec.z - mc.player.getZ(), (targetVec.x - mc.player.getX()))) - 90) - rotationYaw) + (wallsBypass.is(WallsBypass.V2) && !ready && !mc.player.canSee(target) ? 20 : 0);
        float delta_pitch = ((float) (-Math.toDegrees(Math.atan2(targetVec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())), Math.sqrt(Math.pow((targetVec.x - mc.player.getX()), 2) + Math.pow(targetVec.z - mc.player.getZ(), 2))))) - rotationPitch);

        float yawStep;
        float pitchStep;

        if (rotationMode.is(Mode.MLSAC)) {
            double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;

            mlsacNoisePhase = (mlsacNoisePhase + 1) % 360;
            float noiseYaw   = (float)(Math.sin(mlsacNoisePhase * 0.13 + Math.cos(mlsacNoisePhase * 0.07)) * 0.32
                                     + Math.sin(mlsacNoisePhase * 0.031) * 0.11);
            float noisePitch = (float)(Math.cos(mlsacNoisePhase * 0.11 + Math.sin(mlsacNoisePhase * 0.05)) * 0.20
                                     + Math.cos(mlsacNoisePhase * 0.047) * 0.08);

            float springK   = 0.11f;
            float dampening = 0.80f;
            mlsacVelocityYaw   = mlsacVelocityYaw   * dampening + delta_yaw   * springK + noiseYaw;
            mlsacVelocityPitch = mlsacVelocityPitch * dampening + delta_pitch * springK + noisePitch;

            float maxV = MathHelper.clamp(random(minYawStep.getValue(), maxYawStep.getValue()) * 0.2f, 4f, 14f);
            mlsacVelocityYaw   = MathHelper.clamp(mlsacVelocityYaw,   -maxV, maxV);
            mlsacVelocityPitch = MathHelper.clamp(mlsacVelocityPitch, -maxV * 0.4f, maxV * 0.4f);

            float rawNewYaw   = rotationYaw   + mlsacVelocityYaw;
            float rawNewPitch = MathHelper.clamp(rotationPitch + mlsacVelocityPitch, -90f, 90f);

            mlsacGcdAccumYaw   += rawNewYaw   - rotationYaw;
            mlsacGcdAccumPitch += rawNewPitch - rotationPitch;

            float gcdYaw   = (float)(mlsacGcdAccumYaw   - mlsacGcdAccumYaw   % gcdFix);
            float gcdPitch = (float)(mlsacGcdAccumPitch - mlsacGcdAccumPitch % gcdFix);
            mlsacGcdAccumYaw   -= gcdYaw;
            mlsacGcdAccumPitch -= gcdPitch;

            rotationYaw   = rotationYaw   + gcdYaw;
            rotationPitch = MathHelper.clamp(rotationPitch + gcdPitch, -90f, 90f);

            mlsacLastDeltaYaw   = gcdYaw;
            mlsacLastDeltaPitch = gcdPitch;
        } else {
            yawStep = rotationMode.getValue() != Mode.Track ? 360f : random(minYawStep.getValue(), maxYawStep.getValue());
            pitchStep = rotationMode.getValue() != Mode.Track ? 180f : Managers.PLAYER.ticksElytraFlying > 5 ? 180 : (pitchAcceleration + random(-1f, 1f));

            if (ready)
                switch (accelerateOnHit.getValue()) {
                    case Yaw -> yawStep = 180f;
                    case Pitch -> pitchStep = 90f;
                    case Both -> {
                        yawStep = 180f;
                        pitchStep = 90f;
                    }
                }

            if (delta_yaw > 180)
                delta_yaw = delta_yaw - 180;

            float deltaYaw = MathHelper.clamp(MathHelper.abs(delta_yaw), -yawStep, yawStep);
            float deltaPitch = MathHelper.clamp(delta_pitch, -pitchStep, pitchStep);

            float newYaw = rotationYaw + (delta_yaw > 0 ? deltaYaw : -deltaYaw);
            float newPitch = MathHelper.clamp(rotationPitch + deltaPitch, -90.0F, 90.0F);

            double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;

            if (trackticks > 0 || rotationMode.getValue() == Mode.Track) {
                rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
                rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);
            } else {
                rotationYaw = mc.player.getYaw();
                rotationPitch = mc.player.getPitch();
            }
        }

        if (!rotationMode.is(Mode.Grim))
            ModuleManager.rotations.fixRotation = rotationYaw;

        if (rotationMode.is(Mode.MLSAC) && target != null) {
            float[] aim = InteractionUtility.calculateAngle(target.getBoundingBox().getCenter());
            lookingAtHitbox = Managers.PLAYER.checkRtx(aim[0], aim[1], getRange(), getWallRange(), target);
        } else {
            lookingAtHitbox = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange(), getWallRange(), rayTrace.getValue());
        }
    }

    public void onRender3D(MatrixStack stack) {
        if (!haveWeapon() || target == null)
            return;

        if ((resolver.is(Resolver.BackTrack) || resolverVisualisation.getValue()) && resolvedBox != null)
            Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(resolvedBox, HudEditor.getColor(0), 1));

        switch (esp.getValue()) {
            case CelkaPasta -> Render3DEngine.drawOldTargetEsp(stack, target);
            case NurikZapen -> CaptureMark.render(target);
            case Wraith -> renderWraith(target);
            case GhostV1 -> renderGhostV1Esp(target);
            case GhostV2 -> renderGhostV2Esp(target);
        }

        if (!esp.is(ESP.GhostV1))
            resetGhostV1();
        if (!esp.is(ESP.GhostV2))
            ghostV2Renderer.reset();

        if (clientLook.getValue() && rotationMode.getValue() != Mode.None) {
            mc.player.setYaw((float) Render2DEngine.interpolate(mc.player.prevYaw, rotationYaw, Render3DEngine.getTickDelta()));
            mc.player.setPitch((float) Render2DEngine.interpolate(mc.player.prevPitch, rotationPitch, Render3DEngine.getTickDelta()));
        }
    }

    private void renderWraith(Entity target) {
        if (target instanceof LivingEntity living) {
            Render3DEngine.renderGhosts(wraithLength.getValue(), wraithFactor.getValue(), wraithShaking.getValue(), wraithAmplitude.getValue(), living);
        }
    }

    private final mira.client.utility.render.animation.advanced.Animation ghostV1Anim =
            new mira.client.utility.render.animation.advanced.Animation()
                    .setEasing(mira.client.utility.render.animation.advanced.Easing.EASE_OUT_QUAD)
                    .setSpeed(400)
                    .setSize(1.0F)
                    .setForward(false);
    private LivingEntity ghostV1Target;

    private final GhostV2EspRenderer ghostV2Renderer = new GhostV2EspRenderer();

    private void renderGhostV1Esp(Entity target) {
        LivingEntity livingTarget = target instanceof LivingEntity living && !living.isSleeping() ? living : null;
        if (livingTarget != null)
            ghostV1Target = livingTarget;

        ghostV1Anim.setForward(livingTarget != null);

        if (ghostV1Target != null) {
            if (!ghostV1Target.isSleeping() && !ghostV1Anim.finished(false)) {
                float anim = ghostV1Anim.get();
                float red = MathHelper.clamp((ghostV1Target.hurtTime - Render3DEngine.getTickDelta()) / 20.0F, 0.0F, 1.0F);
                Render3DEngine.renderGhostV1(ghostV1Target, anim, red, ghostV1Speed.getValue());
            } else {
                ghostV1Target = null;
            }
        }
    }

    private void resetGhostV1() {
        ghostV1Target = null;
        ghostV1Anim.setForward(false);
        ghostV1Anim.reset();
    }

    private void renderGhostV2Esp(Entity target) {
        if (target instanceof LivingEntity living) {
            ghostV2Renderer.render(living, ghostV2ParticleLimit.getValue(), ghostV2ParticleSize.getValue(), ghostV2Length.getValue());
        }
    }

    private static class GhostV2EspRenderer {
        private final java.util.List<mira.client.utility.render.GhostRenderer3D> particles = new java.util.ArrayList<>();
        private final mira.client.utility.render.animation.advanced.InfinityAnimation moving =
                new mira.client.utility.render.animation.advanced.InfinityAnimation();
        private final mira.client.utility.render.animation.advanced.Animation targetEspAnim =
                new mira.client.utility.render.animation.advanced.Animation()
                        .setEasing(mira.client.utility.render.animation.advanced.Easing.TARGETESP_EASE_OUT_BACK)
                        .setSpeed(300)
                        .setSize(1.0F)
                        .setForward(false);

        void render(LivingEntity target, int particleLimit, float particleSize, float trailLength) {
            if (target == null || target.isSleeping()) {
                reset();
                return;
            }

            spawnParticleIfNeeded(target, particleLimit, particleSize, trailLength);

            float fpsFactor = 500.0F / Math.max(mc.getCurrentFps(), 5);
            moving.animate(moving.get() + 20.0F, 55);
            targetEspAnim.setForward(target.hurtTime > 7);
            float movementValue = moving.get();
            float animationFactor = targetEspAnim.get();

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, TextureStorage.firefly);
            Camera camera = mc.gameRenderer.getCamera();

            for (int index = 0; index < particles.size(); index++) {
                mira.client.utility.render.GhostRenderer3D particle = particles.get(index);
                updateParticle(particle, index, fpsFactor, target, movementValue, animationFactor, particleLimit, particleSize);
            }

            boolean hasAnyTrail = false;
            for (mira.client.utility.render.GhostRenderer3D particle : particles) {
                if (particle.hasTrail()) {
                    hasAnyTrail = true;
                    break;
                }
            }

            if (hasAnyTrail) {
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (mira.client.utility.render.GhostRenderer3D particle : particles) {
                    particle.render(buffer, camera);
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        private void spawnParticleIfNeeded(LivingEntity target, int particleLimit, float particleSize, float trailLength) {
            int desired = Math.max(1, particleLimit);
            while (particles.size() < desired) {
                particles.add(new mira.client.utility.render.GhostRenderer3D(target.getPos(), Vec3d.ZERO, particleSize, trailLength));
            }
            while (particles.size() > desired) {
                particles.remove(particles.size() - 1);
            }
        }

        private void updateParticle(mira.client.utility.render.GhostRenderer3D particle, int index, float fpsFactor, LivingEntity target, float movementValue, float animationFactor, int particleLimit, float particleSize) {
            int segments = Math.max(1, particleLimit);
            float angleOffset = index * 360.0F / segments;
            float currentAngle = movementValue + angleOffset;
            double rad = Math.toRadians(currentAngle);
            double baseRadius = getOrbitRadius(target, particleSize);
            double dynamicRadius = baseRadius - baseRadius * animationFactor;
            double offsetX = Math.sin(rad) * dynamicRadius;
            double offsetZ = Math.cos(rad) * dynamicRadius;
            double verticalSwing = Math.sin(Math.toRadians(movementValue / (index + 1.0F))) * getVerticalAmplitude(target);
            Vec3d desiredPos = target.getPos().add(offsetX, getVerticalCenter(target) + verticalSwing, offsetZ);
            double mul = 0.05F * fpsFactor;
            Vec3d motion = desiredPos.subtract(particle.getPosition()).multiply(mul, mul, mul);
            particle.setMotion(motion);
            particle.tick();
        }

        private double getOrbitRadius(LivingEntity target, float particleSize) {
            Box box = target.getBoundingBox();
            double hitboxWidth = Math.max(box.getLengthX(), box.getLengthZ());
            return hitboxWidth * 0.5 + particleSize * 0.25;
        }

        private double getVerticalCenter(LivingEntity target) {
            return getVerticalAmplitude(target) - 0.7;
        }

        private double getVerticalAmplitude(LivingEntity target) {
            return target.getBoundingBox().getLengthY() * 0.5;
        }

        void reset() {
            particles.clear();
            moving.getAnimation().reset();
            targetEspAnim.reset();
        }
    }

    @Override
    public void onDisable() {
        target = null;
        externalPause = false;
        resetGhostV1();
        ghostV2Renderer.reset();
        mlsacVelocityYaw   = 0f;
        mlsacVelocityPitch = 0f;
        mlsacLastDeltaYaw   = 0f;
        mlsacLastDeltaPitch = 0f;
        mlsacGcdAccumYaw   = 0f;
        mlsacGcdAccumPitch = 0f;
        mlsacNoisePhase    = 0;
    }

    public float getSquaredRotateDistance() {
        float dst = getRange();
        dst += aimRange.getValue();
        if ((mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled()) && target != null) dst += 4f;
        if (ModuleManager.strafe.isEnabled()) dst += 4f;
        if (rotationMode.getValue() != Mode.Track || rayTrace.getValue() == RayTrace.OFF)
            dst = getRange();

        return dst * dst;
    }

    public Vec3d getLegitLook(Entity target) {

        float minMotionXZ = 0.003f;
        float maxMotionXZ = 0.03f;

        float minMotionY = 0.001f;
        float maxMotionY = 0.03f;

        double lenghtX = target.getBoundingBox().getLengthX();
        double lenghtY = target.getBoundingBox().getLengthY();
        double lenghtZ = target.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO))
            rotationMotion = new Vec3d(random(-0.05f, 0.05f), random(-0.05f, 0.05f), random(-0.05f, 0.05f));

        rotationPoint = rotationPoint.add(rotationMotion);

        if (rotationPoint.x >= (lenghtX - 0.05) / 2f)
            rotationMotion = new Vec3d(-random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());

        if (rotationPoint.y >= lenghtY)
            rotationMotion = new Vec3d(rotationMotion.getX(), -random(minMotionY, maxMotionY), rotationMotion.getZ());

        if (rotationPoint.z >= (lenghtZ - 0.05) / 2f)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), -random(minMotionXZ, maxMotionXZ));

        if (rotationPoint.x <= -(lenghtX - 0.05) / 2f)
            rotationMotion = new Vec3d(random(minMotionXZ, 0.03f), rotationMotion.getY(), rotationMotion.getZ());

        if (rotationPoint.y <= 0.05)
            rotationMotion = new Vec3d(rotationMotion.getX(), random(minMotionY, maxMotionY), rotationMotion.getZ());

        if (rotationPoint.z <= -(lenghtZ - 0.05) / 2f)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), random(minMotionXZ, maxMotionXZ));

        rotationPoint.add(random(-0.03f, 0.03f), 0f, random(-0.03f, 0.03f));

        if (!mc.player.canSee(target)) {
            if (Objects.requireNonNull(wallsBypass.getValue()) == WallsBypass.V1) {
                return target.getPos().add(random(-0.15, 0.15), lenghtY, random(-0.15, 0.15));
            }
        }

        float[] rotation;

        if (!Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange(), getWallRange(), rayTrace.getValue())) {
            float[] rotation1 = Managers.PLAYER.calcAngle(target.getPos().add(0, target.getEyeHeight(target.getPose()) / 2f, 0));

            if (PlayerUtility.squaredDistanceFromEyes(target.getPos().add(0, target.getEyeHeight(target.getPose()) / 2f, 0)) <= attackRange.getPow2Value()
                    && Managers.PLAYER.checkRtx(rotation1[0], rotation1[1], getRange(), 0, rayTrace.getValue())) {
                rotationPoint = new Vec3d(random(-0.1f, 0.1f), target.getEyeHeight(target.getPose()) / (random(1.8f, 2.5f)), random(-0.1f, 0.1f));
            } else {
                float halfBox = (float) (lenghtX / 2f);

                for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.05f) {
                    for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.05f) {
                        for (float y1 = 0.05f; y1 <= target.getBoundingBox().getLengthY(); y1 += 0.15f) {

                            Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);

                            if (PlayerUtility.squaredDistanceFromEyes(v1) > attackRange.getPow2Value()) continue;

                            rotation = Managers.PLAYER.calcAngle(v1);
                            if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], getRange(), 0, rayTrace.getValue())) {
                                rotationPoint = new Vec3d(x1, y1, z1);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return target.getPos().add(rotationPoint);
    }

    public boolean isInRange(Entity target) {

        if (PlayerUtility.squaredDistanceFromEyes(target.getPos().add(0, target.getEyeHeight(target.getPose()), 0)) > getSquaredRotateDistance() + 4) {
            return false;
        }

        float[] rotation;
        float halfBox = (float) (target.getBoundingBox().getLengthX() / 2f);

        for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.15f) {
            for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.15f) {
                for (float y1 = 0.05f; y1 <= target.getBoundingBox().getLengthY(); y1 += 0.25f) {
                    if (PlayerUtility.squaredDistanceFromEyes(new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1)) > getSquaredRotateDistance())
                        continue;

                    rotation = Managers.PLAYER.calcAngle(new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1));
                    if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], (float) Math.sqrt(getSquaredRotateDistance()), getWallRange(), rayTrace.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Entity findTarget() {
        List<LivingEntity> first_stage = new CopyOnWriteArrayList<>();
        for (Entity ent : mc.world.getEntities()) {
            if ((ent instanceof ShulkerBulletEntity || ent instanceof FireballEntity)
                    && ent.isAlive()
                    && isInRange(ent)
                    && Projectiles.getValue()) {
                return ent;
            }
            if (skipEntity(ent)) continue;
            if (!(ent instanceof LivingEntity)) continue;
            first_stage.add((LivingEntity) ent);
        }

        return switch (sort.getValue()) {
            case LowestDistance ->
                    first_stage.stream().min(Comparator.comparing(e -> (mc.player.squaredDistanceTo(e.getPos())))).orElse(null);

            case HighestDistance ->
                    first_stage.stream().max(Comparator.comparing(e -> (mc.player.squaredDistanceTo(e.getPos())))).orElse(null);

            case FOV -> first_stage.stream().min(Comparator.comparing(this::getFOVAngle)).orElse(null);

            case LowestHealth ->
                    first_stage.stream().min(Comparator.comparing(e -> (e.getHealth() + e.getAbsorptionAmount()))).orElse(null);

            case HighestHealth ->
                    first_stage.stream().max(Comparator.comparing(e -> (e.getHealth() + e.getAbsorptionAmount()))).orElse(null);

            case LowestDurability -> first_stage.stream().min(Comparator.comparing(e -> {
                        float v = 0;
                        for (ItemStack armor : e.getArmorItems())
                            if (armor != null && !armor.getItem().equals(Items.AIR)) {
                                v += ((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage());
                            }
                        return v;
                    }
            )).orElse(null);

            case HighestDurability -> first_stage.stream().max(Comparator.comparing(e -> {
                        float v = 0;
                        for (ItemStack armor : e.getArmorItems())
                            if (armor != null && !armor.getItem().equals(Items.AIR)) {
                                v += ((armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage());
                            }
                        return v;
                    }
            )).orElse(null);
        };
    }

    private boolean skipEntity(Entity entity) {
        if (isBullet(entity)) return false;
        if (!(entity instanceof LivingEntity ent)) return true;
        if (ent.isDead() || !entity.isAlive()) return true;
        if (entity instanceof ArmorStandEntity) return true;
        if (entity instanceof CatEntity) return true;
        if (skipNotSelected(entity)) return true;
        if (!InteractionUtility.isVecInFOV(ent.getPos(), fov.getValue())) return true;

        if (entity instanceof PlayerEntity player) {
            if (ModuleManager.antiBot.isEnabled() && AntiBot.bots.contains(entity))
                return true;
            if (player == mc.player || Managers.FRIEND.isFriend(player))
                return true;
            if (player.isCreative() && ignoreCreative.getValue())
                return true;
            if (player.getArmor() == 0 && ignoreNaked.getValue())
                return true;
            if (player.isInvisible() && ignoreInvisible.getValue())
                return true;
            if (player.getTeamColorValue() == mc.player.getTeamColorValue() && ignoreTeam.getValue() && mc.player.getTeamColorValue() != 16777215)
                return true;
        }

        return !isInRange(entity) || (entity.hasCustomName() && ignoreNamed.getValue());
    }

    private boolean isBullet(Entity entity) {
        return (entity instanceof ShulkerBulletEntity || entity instanceof FireballEntity)
                && entity.isAlive()
                && PlayerUtility.squaredDistanceFromEyes(entity.getPos()) < getSquaredRotateDistance()
                && Projectiles.getValue();
    }

    private boolean skipNotSelected(Entity entity) {
        if (entity instanceof SlimeEntity && !Slimes.getValue()) return true;
        if (entity instanceof HostileEntity he) {
            if (!hostiles.getValue())
                return true;

            if (onlyAngry.getValue())
                return !he.isAngryAt(mc.player);
        }

        if (entity instanceof PlayerEntity && !Players.getValue()) return true;
        if (entity instanceof VillagerEntity && !Villagers.getValue()) return true;
        if (entity instanceof MobEntity && !Mobs.getValue()) return true;
        return entity instanceof AnimalEntity && !Animals.getValue();
    }

    private float getFOVAngle(@NotNull LivingEntity e) {
        double difX = e.getX() - mc.player.getX();
        double difZ = e.getZ() - mc.player.getZ();
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        return Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
    }

    public void pause() {
        pauseTimer.reset();
    }

    private boolean shouldRandomizeDelay() {
        return randomHitDelay.getValue() && (mc.player.isOnGround() || mc.player.fallDistance < 0.12f || mc.player.isSwimming() || mc.player.isFallFlying());
    }

    private boolean shouldRandomizeFallDistance() {
        return randomHitDelay.getValue() && !shouldRandomizeDelay();
    }

    public static class Position {
        private double x, y, z;
        private int ticks;

        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean shouldRemove() {
            return ticks++ > ModuleManager.aura.backTicks.getValue();
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;

        }
    }

    public enum RayTrace {
        OFF, OnlyTarget, AllEntities
    }

    public enum Sort {
        LowestDistance, HighestDistance, LowestHealth, HighestHealth, LowestDurability, HighestDurability, FOV
    }

    public enum Switch {
        Normal, None, Silent
    }

    public enum Resolver {
        Off, Advantage, Predictive, BackTrack
    }

    public enum Mode {
        Interact, Track, Grim, None, MLSAC
    }

    public enum AttackHand {
        MainHand, OffHand, None
    }

    public enum ESP {
        Off, NurikZapen, CelkaPasta, Wraith, GhostV1, GhostV2
    }

    public enum AccelerateOnHit {
        Off, Yaw, Pitch, Both
    }

    public enum WallsBypass {
        Off, V1, V2
    }
}

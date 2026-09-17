package mira.client.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.math.ChunkPos;
import mira.client.events.impl.EventTick;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.setting.impl.BooleanSettingGroup;
import mira.client.utility.Timer;

public class Optimizer extends Module {

    private final Setting<Boolean> freeMemory     = new Setting<>("FreeMemory",      true);
    private final Setting<Integer> gcInterval     = new Setting<>("GCInterval",       3, 1, 10, v -> freeMemory.getValue());

    private final Setting<Boolean> lowGraphics    = new Setting<>("LowGraphics",     true);
    private final Setting<Boolean> noParticles    = new Setting<>("NoParticles",     true);
    private final Setting<Boolean> noWeather      = new Setting<>("NoWeather",       true);
    private final Setting<Boolean> noBobbing      = new Setting<>("NoBobbing",       true);
    private final Setting<Boolean> noEntityModels = new Setting<>("NoEntityModels",  false);

    private final Setting<Boolean> maxFPS         = new Setting<>("MaxFPS",          true);
    private final Setting<Integer> fpsLimit       = new Setting<>("FPSLimit",        60, 30, 260, v -> maxFPS.getValue());

    private final Setting<Boolean> reduceDist     = new Setting<>("ReduceDistance",  true);
    private final Setting<Integer> renderDist     = new Setting<>("RenderDist",      4, 2, 16, v -> reduceDist.getValue());
    private final Setting<Integer> entityDist     = new Setting<>("EntityDist",      32, 8, 64, v -> reduceDist.getValue());

    private final Setting<Boolean> entityCulling  = new Setting<>("EntityCulling",   true);
    private final Setting<Boolean> hideAnimals    = new Setting<>("HideAnimals",     false);
    private final Setting<Boolean> hideArmorStand = new Setting<>("HideArmorStand",  false);
    private final Setting<Boolean> hideAmbient    = new Setting<>("HideAmbient",     true);

    private final Setting<Boolean> aggressiveGC   = new Setting<>("AggressiveGC",   false);

    private final Setting<BooleanSettingGroup> foliageGroup = new Setting<>("OptimizeFoliage", new BooleanSettingGroup(true));
    private final Setting<Integer> foliageBlend  = new Setting<>("LeafBlend", 0, 0, 7).addToGroup(foliageGroup);

    private final Setting<BooleanSettingGroup> waterGroup   = new Setting<>("OptimizeWater", new BooleanSettingGroup(true));

    private final Setting<BooleanSettingGroup> snowGroup    = new Setting<>("OptimizeSnow", new BooleanSettingGroup(true));

    private final Setting<BooleanSettingGroup> chunkGroup   = new Setting<>("OptimizeChunkLoad", new BooleanSettingGroup(true));
    private final Setting<Integer> chunkLoadDist  = new Setting<>("LoadDistance", 3, 2, 8).addToGroup(chunkGroup);
    private final Setting<Integer> chunkSettleTicks = new Setting<>("SettleTicks", 20, 5, 100).addToGroup(chunkGroup);

    private final Setting<BooleanSettingGroup> combatGroup  = new Setting<>("CombatBoost", new BooleanSettingGroup(true));
    private final Setting<Integer> combatRadius   = new Setting<>("CombatRadius", 12, 4, 32).addToGroup(combatGroup);
    private final Setting<Integer> combatViewDist = new Setting<>("CombatViewDist", 3, 2, 8).addToGroup(combatGroup);
    private final Setting<Boolean> combatHideMobs = new Setting<>("HideMobsInCombat", true).addToGroup(combatGroup);

    private final Timer gcTimer     = new Timer();
    private final Timer aggrGCTimer = new Timer();

    private int savedRenderDist  = -1;
    private boolean savedBobbing = true;

    private ChunkPos lastChunkPos = null;
    private int ticksSinceChunkChange = 0;

    public Optimizer() {
        super("Optimizer", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (fullNullCheck()) return;
        savedRenderDist = mc.options.getViewDistance().getValue();
        savedBobbing    = mc.options.getBobView().getValue();
        lastChunkPos = null;
        ticksSinceChunkChange = 0;
    }

    @Override
    public void onDisable() {
        mc.options.getParticles().setValue(ParticlesMode.ALL);
        mc.options.getBobView().setValue(savedBobbing);
        if (savedRenderDist != -1)
            mc.options.getViewDistance().setValue(savedRenderDist);
        if (mc.world != null) {
            mc.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
            mc.options.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
            mc.options.getEntityShadows().setValue(true);
            mc.options.getEnableVsync().setValue(true);
            mc.options.getBiomeBlendRadius().setValue(2);
        }
        if (mc.world != null) {
            for (Entity en : mc.world.getEntities()) en.setInvisible(false);
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;

        if (freeMemory.getValue() && gcTimer.every((long) gcInterval.getValue() * 60000L)) {
            System.gc();
            Runtime.getRuntime().freeMemory();
        }

        if (aggressiveGC.getValue() && aggrGCTimer.every(5000)) {
            System.gc();
        }

        if (lowGraphics.getValue()) {
            mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            mc.options.getEntityShadows().setValue(false);
            mc.options.getBiomeBlendRadius().setValue(0);
        }

        if (foliageGroup.getValue().isEnabled()) {
            mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            mc.options.getBiomeBlendRadius().setValue(foliageBlend.getValue());
        }

        if (waterGroup.getValue().isEnabled()) {
            mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
        }

        if (snowGroup.getValue().isEnabled()) {
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }

        if (noParticles.getValue()) {
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }

        if (noWeather.getValue() && mc.world != null) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        }

        if (noBobbing.getValue()) {
            mc.options.getBobView().setValue(false);
        }

        if (maxFPS.getValue()) {
            mc.options.getEnableVsync().setValue(false);
            mc.options.getMaxFps().setValue(fpsLimit.getValue());
        }

        int baseDist = reduceDist.getValue() ? renderDist.getValue() : savedRenderDist;
        if (baseDist == -1) baseDist = mc.options.getViewDistance().getValue();
        int targetDist = baseDist;

        if (chunkGroup.getValue().isEnabled() && mc.player != null) {
            ChunkPos current = new ChunkPos(mc.player.getBlockPos());
            if (lastChunkPos == null || !current.equals(lastChunkPos)) {
                lastChunkPos = current;
                ticksSinceChunkChange = 0;
            } else if (ticksSinceChunkChange < chunkSettleTicks.getValue()) {
                ticksSinceChunkChange++;
            }
            if (ticksSinceChunkChange < chunkSettleTicks.getValue()) {
                targetDist = Math.min(targetDist, chunkLoadDist.getValue());
            }
        }

        boolean inCombat = false;
        if (combatGroup.getValue().isEnabled() && mc.world != null && mc.player != null) {
            double radiusSq = (double) combatRadius.getValue() * combatRadius.getValue();
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player || !entity.isPlayer()) continue;
                if (mc.player.squaredDistanceTo(entity) <= radiusSq) {
                    inCombat = true;
                    break;
                }
            }
            if (inCombat) targetDist = Math.min(targetDist, combatViewDist.getValue());
        }

        mc.options.getViewDistance().setValue(targetDist);

        if (entityCulling.getValue() && mc.world != null && mc.player != null) {
            int distSq = entityDist.getValue() * entityDist.getValue();
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player) continue;
                double d = mc.player.squaredDistanceTo(entity);

                boolean hide = false;
                if (d > distSq && !entity.isPlayer()) hide = true;
                if (hideAnimals.getValue() && entity instanceof AnimalEntity) hide = true;
                if (hideArmorStand.getValue() && entity instanceof ArmorStandEntity) hide = true;
                if (hideAmbient.getValue() && entity instanceof BatEntity) hide = true;
                if (inCombat && combatHideMobs.getValue() && !entity.isPlayer()) hide = true;

                entity.setInvisible(hide);
            }
        }

        if (noEntityModels.getValue() && mc.world != null && mc.player != null) {
            int distSq = 16 * 16;
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player || entity.isPlayer()) continue;
                if (mc.player.squaredDistanceTo(entity) > distSq) {
                    entity.setInvisible(true);
                }
            }
        }
    }
    }

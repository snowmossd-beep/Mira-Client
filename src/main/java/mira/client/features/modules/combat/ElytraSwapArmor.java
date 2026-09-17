package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Box;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.events.impl.EventTick;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.setting.impl.BooleanSettingGroup;
import mira.client.utility.Timer;

import java.util.List;

import static mira.client.features.modules.Module.mc;

public class ElytraSwapArmor extends Module {

    public final Setting<Float> range       = new Setting<>("Range",       20f, 5f, 100f);
    public final Setting<Boolean> onlyESP   = new Setting<>("OnlyESP",     true);
    public final Setting<Float> swapDelay   = new Setting<>("Delay",       200f, 0f, 2000f);
    public final Setting<Boolean> swapBack  = new Setting<>("SwapBack",    true);
    public final Setting<Float> swapBackDelay = new Setting<>("BackDelay", 3000f, 500f, 10000f, v -> swapBack.getValue());
    public final Setting<Boolean> onlyGround = new Setting<>("OnlyGround", false);

    public final Setting<BooleanSettingGroup> bypass = new Setting<>("Bypass", new BooleanSettingGroup(false));
    public final Setting<Boolean> silent     = new Setting<>("Silent",  false).addToGroup(bypass);
    public final Setting<Boolean> packet     = new Setting<>("Packet",  true).addToGroup(bypass);
    public final Setting<Boolean> antiAntiCheat = new Setting<>("AAC",  false).addToGroup(bypass);

    private final Timer swapTimer     = new Timer();
    private final Timer swapBackTimer = new Timer();
    private boolean swapped = false;
    private ItemStack savedChest = ItemStack.EMPTY;

    public ElytraSwapArmor() { super("ElytraSwapArmor", Category.COMBAT); }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (onlyGround.getValue() && !mc.player.isOnGround()) return;

        boolean playerNear = hasPlayerNear();

        if (playerNear && isWearingElytra()) {
            if (!swapTimer.passedMs(swapDelay.getValue().longValue())) return;
            swapElytraToChestplate();
            swapped = true;
            swapBackTimer.reset();
        }

        if (!playerNear && swapped && swapBack.getValue()) {
            if (swapBackTimer.passedMs(swapBackDelay.getValue().longValue())) {
                swapChestplateToElytra();
                swapped = false;
                swapTimer.reset();
            }
        }
    }

    private boolean hasPlayerNear() {
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class,
                new Box(mc.player.getPos(), mc.player.getPos()).expand(range.getValue()),
                p -> p != mc.player && !p.isDead() && !Managers.FRIEND.isFriend(p.getGameProfile().getName()));
        if (players.isEmpty()) return false;
        if (onlyESP.getValue()) {
            return players.stream().anyMatch(p -> ModuleManager.aura.getTarget() == p ||
                    mc.player.distanceTo(p) <= ModuleManager.aura.attackRange.getValue() * 3);
        }
        return true;
    }

    private boolean isWearingElytra() {
        ItemStack chest = mc.player.getInventory().armor.get(2);
        return chest.getItem() instanceof ElytraItem;
    }

    private void swapElytraToChestplate() {
        savedChest = mc.player.getInventory().armor.get(2).copy();
        int chestSlot = findChestplate();
        if (chestSlot == -1) return;

        int armorInvSlot = 6;
        if (packet.getValue()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, chestSlot < 9 ? chestSlot : chestSlot - 9,
                    SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    chestSlot + 9, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, 0, SlotActionType.PICKUP, mc.player);
        }
        swapTimer.reset();
    }

    private void swapChestplateToElytra() {
        int elytraSlot = findElytraInInventory();
        if (elytraSlot == -1) return;
        int armorInvSlot = 6;
        if (packet.getValue()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, elytraSlot < 9 ? elytraSlot : elytraSlot - 9,
                    SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    elytraSlot + 9, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    armorInvSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private int findChestplate() {
        for (int i = 0; i < 36; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item instanceof ArmorItem ai && ai.getMaterial().value().defense().get(net.minecraft.entity.EquipmentSlot.CHEST) > 0)
                return i;
        }
        return -1;
    }

    private int findElytraInInventory() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof ElytraItem)
                return i;
        }
        return -1;
    }
}

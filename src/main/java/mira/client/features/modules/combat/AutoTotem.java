package mira.client.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.NotNull;
import mira.client.events.impl.EventSync;
import mira.client.events.impl.PacketEvent;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.utility.player.InventoryUtility;
import mira.client.utility.player.SearchInvResult;

public final class AutoTotem extends Module {
    private final Setting<Float> hp = new Setting<>("HP", 6f, 0f, 20f);
    private final Setting<Integer> delay = new Setting<>("Delay", 2, 0, 10);
    private final Setting<Boolean> hotbarRefill = new Setting<>("HotbarRefill", false);
    private final Setting<Integer> hotbarSlot = new Setting<>("HotbarSlot", 1, 1, 9, v -> hotbarRefill.getValue());
    private static final int MAX_RETRIES = 3;


    private int cooldown;
    private Item expectedItem;
    private int verifyCooldown = -1;
    private int retries;
    private boolean forceCheck;

    private static AutoTotem instance;

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
        instance = this;
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        verifyCooldown = -1;
        retries = 0;
        expectedItem = null;
        forceCheck = false;
        lastKnownHealth = -1f;
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        verifyCooldown = -1;
        retries = 0;
        expectedItem = null;
        forceCheck = false;
        lastKnownHealth = -1f;
    }

    public static void notifyAuraHit() {
        if (instance != null) {
            instance.forceCheck = true;
        }
    }

    private float lastKnownHealth = -1f;

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof ExplosionS2CPacket) {
            forceCheck = true;
        } else if (e.getPacket() instanceof HealthUpdateS2CPacket hp) {
            if (lastKnownHealth >= 0f && hp.getHealth() < lastKnownHealth) {
                forceCheck = true;
            }
            lastKnownHealth = hp.getHealth();
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player == null || mc.world == null) return;

        if (verifyCooldown > 0) {
            verifyCooldown--;
        } else if (verifyCooldown == 0) {
            verifyCooldown = -1;
            verify();
        }

        if (cooldown > 0 && !forceCheck) {
            cooldown--;
            return;
        }
        forceCheck = false;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean offhandHasTotem = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;

        if (health <= hp.getValue() && !offhandHasTotem) {
            SearchInvResult result = InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING);
            if (result.found()) {
                retries = 0;
                performSwap(result.slot());
            }
        }

        cooldown = delay.getValue();

        if (hotbarRefill.getValue()) {
            checkHotbarSlot();
        }
    }

    private void checkHotbarSlot() {
        int slotIndex = hotbarSlot.getValue() - 1;
        if (mc.player.getInventory().getStack(slotIndex).getItem() == Items.TOTEM_OF_UNDYING) return;

        SearchInvResult result = InventoryUtility.findItemInInventory(Items.TOTEM_OF_UNDYING);
        if (result.found() && result.slot() != slotIndex) {
            int sourceScreenSlot = convertSlotIndex(result.slot());
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, sourceScreenSlot, slotIndex, SlotActionType.SWAP, mc.player);
        }
    }

    private void performSwap(int slot) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, convertSlotIndex(slot), 40, SlotActionType.SWAP, mc.player);
        expectedItem = Items.TOTEM_OF_UNDYING;
        verifyCooldown = 3;
    }

    private void verify() {
        if (mc.player == null || expectedItem == null) return;

        if (mc.player.getOffHandStack().getItem() != expectedItem) {
            if (retries < MAX_RETRIES) {
                SearchInvResult result = InventoryUtility.findItemInInventory(expectedItem);
                if (result.found()) {
                    retries++;
                    performSwap(result.slot());
                    return;
                }
            }
        }

        expectedItem = null;
    }

    private static int convertSlotIndex(int slotIndex) {
        return slotIndex < 9 ? 36 + slotIndex : slotIndex;
    }
    }
            

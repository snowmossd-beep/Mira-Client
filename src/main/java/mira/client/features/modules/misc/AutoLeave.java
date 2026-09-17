package mira.client.features.modules.misc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import mira.client.core.Managers;
import mira.client.core.manager.client.ModuleManager;
import mira.client.features.modules.Module;
import mira.client.setting.Setting;
import mira.client.setting.impl.SettingGroup;
import mira.client.utility.Timer;
import mira.client.utility.player.InventoryUtility;

import static mira.client.features.modules.render.ClientSettings.isRu;

public class AutoLeave extends Module {
    public AutoLeave() {
        super("AutoLeave", Category.MISC);
    }

    private final Setting<Boolean> antiHelperLeave = new Setting<>("AntiHelperLeave", true);
    private final Setting<Boolean> antiKTLeave = new Setting<>("AntiKTLeave", true);
    private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", true);
    private final Setting<Boolean> fastLeave = new Setting<>("InstantLeave", true);
    public static Setting<String> command = new Setting<>("Command", "hub");
    private final Setting<SettingGroup> leaveIf = new Setting<>("Leave if", new SettingGroup(false, 0));
    private final Setting<Boolean> low_hp = new Setting<>("LowHp", false).addToGroup(leaveIf);
    private final Setting<Boolean> totems = new Setting<>("Totems", false).addToGroup(leaveIf);
    private final Setting<Integer> totemsCount = new Setting<>("TotemsCount", 2, 0, 10, v -> totems.getValue());
    private final Setting<Float> leaveHp = new Setting<>("HP", 8.0f, 1f, 20.0f, v -> low_hp.getValue());
    private final Setting<LeaveMode> staff = new Setting<>("Staff", LeaveMode.None).addToGroup(leaveIf);
    private final Setting<LeaveMode> players = new Setting<>("Players", LeaveMode.Leave).addToGroup(leaveIf);
    private final Setting<Integer> distance = new Setting<>("Distance", 256, 4, 256, v -> players.getValue() != LeaveMode.None).addToGroup(leaveIf);

    private final Timer chatDelay = new Timer();

    private final Timer hurtTimer = new Timer();

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null)
            return;

        if (mc.player.hurtTime > 0)
            hurtTimer.reset();

        if (antiKTLeave.getValue() && !hurtTimer.passedMs(30000))
            return;

        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl.getScoreboardTeam() != null && antiHelperLeave.getValue()) {
                String prefix = pl.getScoreboardTeam().getPrefix().getString();
                if (isStaff(Formatting.strip(prefix)))
                    continue;
            }

            if (pl != mc.player && !Managers.FRIEND.isFriend(pl) && players.getValue() != LeaveMode.None && mc.player.squaredDistanceTo(pl.getPos()) <= distance.getPow2Value()) {
                switch (players.getValue()) {
                    case Command -> {
                        if (autoDisable.getValue()) disable();
                        sendMessage(isRu() ? "Ливнул т.к. рядом появился игрок!" : "Logged out because there was a player!");
                        mc.player.networkHandler.sendChatCommand(command.getValue());
                        return;
                    }
                    case Leave -> {
                        leave(isRu() ? "Ливнул т.к. рядом появился игрок" : "Logged out because there was a player");
                        return;
                    }
                }
            }
        }

        if (totems.getValue() && InventoryUtility.getItemCount(Items.TOTEM_OF_UNDYING) <= totemsCount.getValue())
            leave(isRu() ? "Ливнул т.к. кончились тотемы" : "Logged out because out of totems");

        if (mc.player.getHealth() < leaveHp.getValue() && low_hp.getValue())
            leave(isRu() ? "Ливнул т.к. мало хп" : "Logged out because ur hp is low");

        if (staff.getValue() != LeaveMode.None && ModuleManager.staffBoard.isDisabled() && mc.player.age % 5 == 0)
            sendMessage(isRu() ? "Включи StaffBoard!" : "Turn on StaffBoard!");
    }

    private void leave(String message) {
        if (!chatDelay.passedMs(1000))
            return;
        chatDelay.reset();

        if (autoDisable.getValue())
            disable(message);

        if (fastLeave.getValue()) sendPacket(new UpdateSelectedSlotC2SPacket(228));
        else mc.player.networkHandler.getConnection().disconnect(Text.of("[AutoLeave] " + message));
    }

    public static boolean isStaff(String name) {
        if (name == null) return false;

        name = name.toLowerCase();

        return name.contains("helper") || name.contains("moder") || name.contains("admin") || name.contains("owner") || name.contains("curator") || name.contains("куратор") || name.contains("модер") || name.contains("админ") || name.contains("хелпер") || name.contains("поддержка");
    }

    private enum LeaveMode {
        None, Command, Leave
    }
}

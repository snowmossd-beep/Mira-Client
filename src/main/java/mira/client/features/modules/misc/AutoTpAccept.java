package mira.client.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import mira.client.core.Managers;
import mira.client.events.impl.PacketEvent;
import mira.client.gui.font.FontRenderers;
import mira.client.features.modules.Module;
import mira.client.features.modules.render.HudEditor;
import mira.client.setting.Setting;
import mira.client.utility.MiraUtility;
import mira.client.utility.math.MathUtility;

import static mira.client.features.modules.render.ClientSettings.isRu;

public class AutoTpAccept extends Module {
    public AutoTpAccept() {
        super("AutoTPaccept", Category.MISC);
    }

    public Setting<Boolean> grief = new Setting<>("Grief", false);
    public Setting<Boolean> onlyFriends = new Setting<>("onlyFriends", true);
    public Setting<Boolean> duo = new Setting<>("Duo", false);
    private final Setting<Integer> timeOut = new Setting<>("TimeOut", 60, 1, 180, v -> duo.getValue());

    private TpTask tpTask;

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (fullNullCheck()) return;
        if (event.getPacket() instanceof GameMessageS2CPacket) {
            final GameMessageS2CPacket packet = event.getPacket();
            if (packet.content().getString().contains("телепортироваться") || packet.content().getString().contains("tpaccept")) {
                if (onlyFriends.getValue()) {
                    if (Managers.FRIEND.isFriend(MiraUtility.solveName(packet.content().getString()))) {
                        if (!duo.getValue()) acceptRequest(packet.content().getString());
                        else
                            tpTask = new TpTask(() -> acceptRequest(packet.content.getString()), System.currentTimeMillis());
                    }
                } else acceptRequest(packet.content().getString());
            }
        }
    }

    public void onRender2D(DrawContext context) {
        if (duo.getValue() && tpTask != null) {
            String text = (isRu() ? "Ждем таргета " : "Awaiting target ") + MathUtility.round((timeOut.getValue() * 1000 - (System.currentTimeMillis() - tpTask.time())) / 1000f, 1);
            FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), text, mc.getWindow().getScaledWidth() / 2f, mc.getWindow().getScaledHeight() / 2f + 30, HudEditor.getColor(1).getRGB());
        }
    }

    @Override
    public void onUpdate() {
        if (duo.getValue() && tpTask != null) {
            if (System.currentTimeMillis() - tpTask.time > timeOut.getValue() * 1000) {
                tpTask = null;
                return;
            }
            for (PlayerEntity pl : mc.world.getPlayers()) {
                if (pl == mc.player) continue;
                if (Managers.FRIEND.isFriend(pl)) continue;
                tpTask.task.run();
                tpTask = null;
                break;
            }
        }
    }

    public void acceptRequest(String name) {
        if (grief.getValue()) mc.getNetworkHandler().sendChatCommand("tpaccept " + MiraUtility.solveName(name));
        else mc.getNetworkHandler().sendChatCommand("tpaccept");
    }

    private record TpTask(Runnable task, long time) {}
}

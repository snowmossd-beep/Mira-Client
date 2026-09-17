package mira.client.features.modules.misc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.jetbrains.annotations.NotNull;
import mira.client.core.Managers;
import mira.client.events.impl.EventSync;
import mira.client.events.impl.PacketEvent;
import mira.client.features.modules.Module;
import mira.client.gui.notification.Notification;
import mira.client.setting.Setting;

public class ServerFinder extends Module {
    private final Setting<Boolean> scanChat = new Setting<>("ScanChat", true);
    private final Setting<Boolean> scanTabList = new Setting<>("ScanTabList", true);
    private final Setting<Integer> scanInterval = new Setting<>("ScanInterval", 100, 20, 400);
    private final Set<String> foundThisSession = new HashSet<>();
    private int tickCounter;

    private static final List<String> KNOWN_SIGNATURES = List.of(
            "grim", "vulcan", "matrix", "spartan", "negativity",
            "verus", "themis", "karhu", "intave", "polar",
            "aac", "watchdog", "hawk", "vanish", "essentials",
            "luckperms", "worldguard", "coreprotect"
    );

    public ServerFinder() {
        super("ServerFinder", Category.MISC);
    }

    @Override
    public void onEnable() {
        foundThisSession.clear();
        tickCounter = 0;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!scanTabList.getValue() || mc.getNetworkHandler() == null) return;
        if (++tickCounter < scanInterval.getValue()) return;
        tickCounter = 0;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getDisplayName() != null) {
                check(entry.getDisplayName().getString());
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (scanChat.getValue() && e.getPacket() instanceof GameMessageS2CPacket packet) {
            check(packet.content.getString());
        }
    }

    private void check(String text) {
        if (text == null || text.isEmpty()) return;

        String lower = text.toLowerCase();
        for (String signature : KNOWN_SIGNATURES) {
            if (lower.contains(signature) && foundThisSession.add(signature)) {
                Managers.NOTIFICATION.publicity(
                        "ServerFinder",
                        "Detected possible " + signature + " on this server.",
                        4,
                        Notification.Type.WARNING
                );
            }
        }
    }
}

package mira.client.api;

import com.mojang.logging.LogUtils;
import mira.client.features.cmd.Command;
import mira.client.features.hud.HudElement;
import mira.client.features.modules.Module;

import java.util.List;

public interface IAddon {
    void onInitialize();

    List<Module> getModules();

    List<Command> getCommands();

    List<HudElement> getHudElements();

    String getPackage();

    String getName();

    String getAuthor();

    String getRepo();

    String getVersion();

    default String getDescription() {
        return "";
    }

    default void onShutdown() {
        LogUtils.getLogger().info("Shutting down addon: " + getName());
    }
}

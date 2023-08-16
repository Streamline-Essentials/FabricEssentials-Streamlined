package net.streamline.base;

import lombok.Getter;
import lombok.Setter;
import net.streamline.platform.BasePlugin;
import net.streamline.api.modules.ModuleManager;
import net.streamline.platform.commands.StreamlineSpigotCommand;

public class Streamline extends BasePlugin {
    @Getter @Setter
    private static StreamlineSpigotCommand streamlineSpigotCommand;

    @Override
    public void enable() {
        try {
            ModuleManager.registerExternalModules();
            ModuleManager.startModules();
        } catch (Exception e) {
            e.printStackTrace();
        }

        streamlineSpigotCommand = new StreamlineSpigotCommand();
    }

    @Override
    public void disable() {
        ModuleManager.stopModules();
    }

    @Override
    public void load() {

    }

    @Override
    public void reload() {

    }

    @Override
    public String getName() {
        return "StreamlineCore";
    }
}

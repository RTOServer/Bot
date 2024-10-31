package net.cjsah.bot.extra;

import net.cjsah.bot.FilePaths;
import net.cjsah.bot.command.Command;
import net.cjsah.bot.command.CommandManager;
import net.cjsah.bot.command.CommandParam;
import net.cjsah.bot.command.source.CommandSource;
import net.cjsah.bot.plugin.Plugin;

public class RTOSPlugin extends Plugin {
    private static final FilePaths.AppFile ConfigPath = FilePaths.regFile(FilePaths.CONFIG.resolve("rtos.json"), "[]");

    @Override
    public void onLoad() {
        ConfigPath.checkAndCreate();
        CommandManager.register(RTOSPlugin.class);
    }

    @Command("/bind")
    public static void bindUser(@CommandParam("id") String id, CommandSource source) {
        String str = ConfigPath.read();

    }

}

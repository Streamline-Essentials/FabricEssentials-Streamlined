package net.streamline.platform;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.streamline.api.SLAPI;
import net.streamline.api.configs.given.GivenConfigs;
import net.streamline.api.events.StreamlineEvent;
import net.streamline.api.events.server.ServerStopEvent;
import net.streamline.api.interfaces.IProperEvent;
import net.streamline.api.interfaces.IStreamline;
import net.streamline.api.modules.ModuleUtils;
import net.streamline.api.objects.StreamlineResourcePack;
import net.streamline.api.savables.users.StreamlineConsole;
import net.streamline.api.savables.users.StreamlinePlayer;
import net.streamline.api.savables.users.StreamlineUser;
import net.streamline.api.utils.MessageUtils;
import net.streamline.api.utils.UserUtils;
import net.streamline.platform.commands.ProperCommand;
import net.streamline.api.command.StreamlineCommand;
import net.streamline.platform.handlers.BackendHandler;
import net.streamline.platform.messaging.ProxyPluginMessenger;
import net.streamline.platform.profile.SpigotProfiler;
import net.streamline.platform.savables.UserManager;
import net.streamline.platform.listeners.PlatformListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tv.quaint.events.BaseEventHandler;

import javax.swing.Timer;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;

public abstract class BasePlugin implements IStreamline {
    public static class Runner implements Runnable {
        public Runner() {
            MessageUtils.logInfo("Task Runner registered!");
        }

        @Override
        public void run() {
            SLAPI.getMainScheduler().tick();
        }
    }

    @Getter
    private final PlatformType platformType = PlatformType.SPIGOT;
    @Getter
    private final ServerType serverType = ServerType.BACKEND;

    @Getter @Setter
    private StreamlineResourcePack resourcePack;

    @Getter
    private final String version = "${{project.version}}";
    @Getter
    private static BasePlugin instance;
    @Getter
    private SLAPI<BasePlugin, UserManager, Messenger> slapi;

    @Getter @Setter
    private MinecraftServer server;

    public MinecraftServer getProxy() {
        return getServer();
    }

    @Getter
    private UserManager userManager;
    @Getter
    private Messenger messenger;

    public void onLoad(MinecraftServer server, File dataFolder) {
        instance = this;

        String parentPath = dataFolder.getParent();
        if (parentPath != null) {
            File parentFile = new File(parentPath);
            File[] files = parentFile.listFiles((f) -> {
                if (! f.isDirectory()) return false;
                if (f.getName().equals("StreamlineAPI")) return true;
                return false;
            });

            if (files != null) {
                Arrays.stream(files).forEach(file -> {
                    file.renameTo(new File(parentPath, "StreamlineCore"));
                });
            }
        }

        this.load();

        onEnable();
    }

    @Getter @Setter
    private Runner runner;

    public void onEnable() {
        userManager = new UserManager();
        messenger = new Messenger();
        slapi = new SLAPI<>(getName(), this, getUserManager(), getMessenger());
        SLAPI.setBackendHandler(new BackendHandler());

        getSlapi().setProxyMessenger(new ProxyPluginMessenger());
        getSlapi().setProfiler(new SpigotProfiler());

//        getProxy().getScheduler().scheduleSyncRepeatingTask(this, new Runner(), 0, 1);

        runner = new Runner();
        Timer timer = new Timer(50, e -> runner.run());

        this.enable();
        new PlatformListener();
    }

    public void onDisable() {
        for (StreamlineUser user : UserUtils.getLoadedUsersSet()) {
            user.saveAll();
        }

        this.disable();
        fireStopEvent();
    }

    public void fireStopEvent() {
        ServerStopEvent e = new ServerStopEvent().fire();
        if (e.isCancelled()) return;
        if (! e.isSendable()) return;
        ModuleUtils.sendMessage(ModuleUtils.getConsole(), e.getMessage());
    }

    abstract public void enable();

    abstract public void disable();

    abstract public void load();

    abstract public void reload();

    @Override
    public @NotNull ConcurrentSkipListSet<StreamlinePlayer> getOnlinePlayers() {
        ConcurrentSkipListSet<StreamlinePlayer> players = new ConcurrentSkipListSet<>();

        for (Player player : onlinePlayers()) {
            players.add(getUserManager().getOrGetPlayer(player));
        }

        return players;
    }

    @Override
    public ProperCommand createCommand(StreamlineCommand command) {
        return new ProperCommand(command);
    }

    @Override
    public int getMaxPlayers() {
        return getInstance().getProxy().getMaxPlayers();
    }

    @Override
    public ConcurrentSkipListSet<String> getOnlinePlayerNames() {
        ConcurrentSkipListSet<String> r = new ConcurrentSkipListSet<>();

        getOnlinePlayers().forEach(a -> {
            r.add(a.getName());
        });

//        r.add(getUserManager().getConsole().latestName);

        return r;
    }

    @Override
    public long getConnectionThrottle() {
        return -1;
    }

    public static List<Player> onlinePlayers() {
        return new ArrayList<>(getInstance().getProxy().getPlayerCount());
    }

    public static List<Player> playersOnServer(String serverName) {
        return new ArrayList<>(/*getInstance().getProxy().gets(serverName).getPlayers()*/);
    }

    public static Player getPlayer(String uuid) {
        for (Player player : onlinePlayers()) {
            if (player.getUUID().toString().equals(uuid)) return player;
        }

        return null;
    }

    public static Optional<Player> getPlayerByName(String name) {
        return Optional.ofNullable(getInstance().getProxy().getPlayerList().getPlayerByName(name));
    }

    public static @Nullable Player getPlayerExact(@NotNull String name) {
        if (getPlayerByName(name).isEmpty()) return null;
        return getPlayerByName(name).get();
    }

    public static @NotNull List<Player> matchPlayer(@NotNull String name) {
        Player player = getPlayerExact(name);
        if (player == null) return new ArrayList<>();
        return List.of(player);
    }

    public static @Nullable Player getPlayer(@NotNull UUID id) {
        return getPlayer(id.toString());
    }

    public static Player getPlayer(CommandSender sender) {
        return getInstance().getProxy().getPlayer(sender.getName());
    }

    @Override
    public boolean getOnlineMode() {
        return getInstance().getProxy().getOnlineMode();
    }

    @Override
    public void shutdown() {
        getInstance().getProxy().shutdown();
    }

    @Override
    public int broadcast(@NotNull String message, @NotNull String permission) {
        int people = 0;

        for (Player player : onlinePlayers()) {
            if (! player.hasPermission(permission)) continue;
            getMessenger().sendMessage(player, message);
            people ++;
        }

        return people;
    }

    @Override
    public boolean hasPermission(StreamlineUser user, String permission) {
        Player player = getPlayer(user.getUuid());
        if (player == null) return false;
        return player.hasPermission(permission);
    }

    @Override
    public void chatAs(StreamlineUser as, String message) {
        if (as instanceof StreamlineConsole) {
            runAsStrictly(as, message);
        }
        if (as instanceof StreamlinePlayer) {
            if (MessageUtils.isCommand(message)) runAsStrictly(as, message.substring("/".length()));
            Player player = getPlayer(as.getUuid());
            if (player == null) return;
            player.chat(message);
        }
    }

    @Override
    public void runAsStrictly(StreamlineUser as, String command) {
        if (as instanceof StreamlineConsole) {
            getInstance().getProxy().dispatchCommand(getInstance().getProxy().getConsoleSender(), command);
        }
        if (as instanceof StreamlinePlayer) {
            if (MessageUtils.isCommand(command)) runAsStrictly(as, command.substring("/".length()));
            Player player = getPlayer(as.getUuid());
            if (player == null) return;
            getInstance().getProxy().dispatchCommand(player, command);
        }
    }

    @Override
    public boolean serverHasPlugin(String plugin) {
        return getInstance().getProxy().getPluginManager().getPlugin(plugin) != null;
    }

    @Override
    public boolean equalsAnyServer(String servername) {
        return getServerNames().contains(servername);
    }

    @Override
    public void fireEvent(IProperEvent<?> event) {
        if (! (event.getEvent() instanceof Event)) return;
        Event e = (Event) event.getEvent();
        getInstance().getProxy().getPluginManager().callEvent(e);
    }

    @Override
    public void fireEvent(StreamlineEvent event) {
        fireEvent(event, true);
    }

    @Override
    public void fireEvent(StreamlineEvent event, boolean async) {
        try {
            BaseEventHandler.fireEvent(event);
        } catch (Exception e) {
            handleMisSync(event, async);
        }
    }

    @Override
    public void handleMisSync(StreamlineEvent event, boolean async) {
        BaseEventHandler.fireEvent(event);
    }

    @Override
    public ConcurrentSkipListSet<String> getServerNames() {
        return new ConcurrentSkipListSet<>(GivenConfigs.getProfileConfig().getCachedProfile().getServers().keySet());
    }

    @Override
    public void sendResourcePack(StreamlineResourcePack resourcePack, StreamlineUser player) {
        Player p = getPlayer(player.getUuid());
        sendResourcePack(resourcePack, p);
    }

    @Override
    public void sendResourcePack(StreamlineResourcePack resourcePack, String uuid) {
        Player p = getPlayer(uuid);

//        getMessenger().logInfo("Attempting to send a resource pack to a uuid of '" + whitelistedUuid + "'...");

        sendResourcePack(resourcePack, p);
    }

    public void sendResourcePack(StreamlineResourcePack resourcePack, Player player) {
        if (player == null) {
            MessageUtils.logWarning("Tried to send a player a resource pack, but could not find their player!");
            return;
        }

//        getMessenger().logInfo("Sending resource pack to '" + player.getName() + "'.");

        try {
            if (resourcePack.getHash().length > 0) {
                if (! resourcePack.getPrompt().equals("")) {
                    player.setResourcePack(resourcePack.getUrl(), resourcePack.getHash(), resourcePack.getPrompt(), resourcePack.isForce());
                    return;
                }
                player.setResourcePack(resourcePack.getUrl(), resourcePack.getHash(), resourcePack.isForce());
                return;
            }
            player.setResourcePack(resourcePack.getUrl());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ClassLoader getMainClassLoader() {
        return getProxy().getClass().getClassLoader();
    }

    @Getter @Setter
    private static boolean commandsNeedToBeSynced = false;

    /**
     * Register command(s) into the server command map.
     * @param commands The command(s) to register
     */
    public static void registerCommands(Command... commands) {
        // Get the commandMap
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Register all the commands into the map
            for (final Command command : commands) {
                commandMap.register(command.getLabel(), command);
            }

            CompletableFuture.runAsync(BasePlugin::syncCommands);
        } catch (final Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Unregister command(s) from the server command map.
     * @param commands The command(s) to unregister
     */
    public static void unregisterCommands(String... commands) {
        // Get the commandMap
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Register all the commands into the map
            for (final String command : commands) {
                Command com = commandMap.getCommand(command);
                if (com == null) {
                    MessageUtils.logWarning("Tried to unregister a command that does not exist: " + command);
                    continue;
                }

                com.unregister(commandMap);
            }

            CompletableFuture.runAsync(BasePlugin::syncCommands);
        } catch (final Exception e) {
            MessageUtils.logWarningWithInfo("Failed to unregister commands: ", e);
        }
    }

    public static void syncCommands() {
        try {
            Class<?> craftServer = Bukkit.getServer().getClass();
            Method syncCommandsMethod = craftServer.getDeclaredMethod("syncCommands");
            syncCommandsMethod.setAccessible(true);
            syncCommandsMethod.invoke(Bukkit.getServer());
        } catch (Exception e) {
            MessageUtils.logWarningWithInfo("Failed to sync commands: ", e);
        }
    }
}

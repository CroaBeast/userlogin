package com.elchologamer.userlogin;

import com.elchologamer.userlogin.api.UserLoginAPI;
import com.elchologamer.userlogin.api.event.AuthenticationEvent;
import com.elchologamer.userlogin.api.types.AuthType;
import com.elchologamer.userlogin.util.QuickMap;
import com.elchologamer.userlogin.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ULPlayer {
    public static Map<UUID, ULPlayer> players = new HashMap<>();

    private final UserLogin plugin = UserLogin.getPlugin();
    private final UUID uuid;
    private boolean loggedIn = false;
    private String ip = null;
    private int loginAttempts = 0;

    // Scheduler task IDs
    private int timeout = -1;
    private int welcomeMessage = -1;
    private int ipForgor = -1;

    private ULPlayer(UUID uuid) {
        this.uuid = uuid;
        players.put(uuid, this);
    }

    public static ULPlayer get(Player player) {
        return get(player.getUniqueId());
    }

    public static ULPlayer get(UUID uuid) {
        ULPlayer player = players.get(uuid);
        if (player == null) player = new ULPlayer(uuid);
        return player;
    }

    public void onJoin(boolean fromOtherServer) {
        loggedIn = false;
        loginAttempts = 0;

        Player player = getPlayer();

        // Teleport to login position
        if (plugin.getConfig().getBoolean("teleports.toLogin"))
            player.teleport(plugin.getLocations().getLocation("login", player.getWorld().getSpawnLocation()));

        if (fromOtherServer) {
            onAuthenticate(AuthType.LOGIN);
            return;
        }

        // Bypass if IP is registered
        InetSocketAddress address = player.getAddress();
        if (plugin.getConfig().getBoolean("ipRecords.enabled")) {
            if (ip != null && address != null && address.getHostString().equals(ip)) {
                onAuthenticate(AuthType.LOGIN);
                return;
            }

            if (ipForgor != -1) {
                plugin.getServer().getScheduler().cancelTask(ipForgor);
                ipForgor = -1;
            }
        }

        schedulePreLoginTasks();
        sendWelcomeMessage();
    }

    public void onQuit() {
        if (!loggedIn) cancelPreLoginTasks();
        else {
            loggedIn = false;

            if (plugin.getConfig().getBoolean("teleports.savePosition"))
                plugin.getLocations().savePlayerLocation(getPlayer());

            // Store IP address if enabled
            if (plugin.getConfig().getBoolean("ipRecords.enabled")) {
                // Schedule IP deletion
                ipForgor = plugin.getServer().getScheduler().scheduleSyncDelayedTask(
                        plugin,
                        () -> ip = null,
                        plugin.getConfig().getLong("ipRecords.delay", 10) * 20
                );
            }
        }
    }

    public void onAuthenticate(AuthType type) {
        Player player = getPlayer();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection teleports = config.getConfigurationSection("teleports");
        assert teleports != null;

        // Call event
        AuthenticationEvent event;

        boolean bungeeEnabled = config.getBoolean("bungeeCord.enabled");
        if (bungeeEnabled) {
            String targetServer = config.getString("bungeeCord.spawnServer");
            event = new AuthenticationEvent(player, type, targetServer);
        }
        else {
            Location target = null;
            Location spawn = player.getWorld().getSpawnLocation();

            if (teleports.getBoolean("savePosition"))
                target = plugin.getLocations().getPlayerLocation(player, spawn);
            else if (teleports.getBoolean("toSpawn", true))
                target = plugin.getLocations().getLocation("spawn", spawn);

            event = new AuthenticationEvent(player, type, target);
        }

        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        cancelPreLoginTasks();

        // Save IP address
        InetSocketAddress address = player.getAddress();
        if (config.getBoolean("ipRecords.enabled") && address != null) ip = address.getHostString();

        // Send login message
        if (event.getMessage() != null) player.sendMessage(event.getMessage());

        // Join announcement
        if (event.getAnnouncement() != null) for (Player onlinePlayer : player.getServer().getOnlinePlayers())
            if (UserLoginAPI.isLoggedIn(player)) onlinePlayer.sendMessage(event.getAnnouncement());

        loggedIn = true;

        // Run login commands
        List<String> loginCommands = config.getStringList("loginCommands");
        for (String command : loginCommands) {
            plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    command.replace("{player}", player.getName()).replaceFirst("^/", "")
            );
        }

        // Teleport to destination
        if (bungeeEnabled && event.getTargetServer() != null)
            Utils.sendPluginMessage(player, "BungeeCord", "Connect", event.getTargetServer());
        else if (event.getDestination() != null) player.teleport(event.getDestination());
    }

    public boolean onLoginAttempt() {
        int maxAttempts = plugin.getConfig().getInt("password.maxLoginAttempts");

        if (++loginAttempts >= maxAttempts) {
            getPlayer().kickPlayer(
                    plugin.getLang().getMessage("messages.max_attempts_exceeded").replace("{count}", Integer.toString(maxAttempts))
            );
            return false;
        }
        else return true;
    }

    public void sendWelcomeMessage() {
        String path = "messages.welcome." + ((UserLoginAPI.isRegistered(getPlayer())) ? "registered" : "unregistered");
        sendMessage(path, new QuickMap<>("player", getPlayer().getName()));
    }

    public void schedulePreLoginTasks() {
        Player player = getPlayer();

        // Timeout
        if (plugin.getConfig().getBoolean("timeout.enabled", true)) {
            long timeoutDelay = plugin.getConfig().getLong("timeout.time");
            timeout = player.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin,
                    () -> player.kickPlayer(plugin.getLang().getMessage("messages.timeout")),
                    timeoutDelay * 20
            );
        }

        // Repeating welcome message
        long interval = plugin.getConfig().getLong("repeatingWelcomeMsg", -1) * 20;
        if (interval > 0) {
            welcomeMessage = player.getServer().getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    this::sendWelcomeMessage,
                    interval, interval
            );
        }
    }

    public void cancelPreLoginTasks() {
        if (timeout != -1) {
            getPlayer().getServer().getScheduler().cancelTask(timeout);
            timeout = -1;
        }

        if (welcomeMessage != -1) {
            getPlayer().getServer().getScheduler().cancelTask(welcomeMessage);
            welcomeMessage = -1;
        }
    }

    public void sendMessage(String path) { sendMessage(path, null); }

    public void sendMessage(String path, Map<String, Object> replace) {
        String message = plugin.getLang().getMessage(path);
        if (message == null || message.isEmpty()) return;

        if (replace != null) for (String k : replace.keySet())
            message = message.replace("{" + k + "}", replace.get(k).toString());

        getPlayer().sendMessage(Utils.color(message));
    }

    public Player getPlayer() {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null)
            throw new IllegalArgumentException("Player with UUID " + uuid + " not found");
        return player;
    }

    public boolean isLoggedIn() { return loggedIn; }
}
package com.elchologamer.userlogin;

import com.elchologamer.userlogin.command.*;
import com.elchologamer.userlogin.command.base.*;
import com.elchologamer.userlogin.command.sub.*;
import com.elchologamer.userlogin.database.*;
import com.elchologamer.userlogin.listener.*;
import com.elchologamer.userlogin.listener.restriction.*;
import com.elchologamer.userlogin.manager.*;
import com.elchologamer.userlogin.util.*;
import com.elchologamer.userlogin.util.Metrics.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.*;

public final class UserLogin extends JavaPlugin {

    private static UserLogin plugin;

    public final static int PLUGIN_ID = 80669;

    private LangManager lang;
    private LocationsManager locationsManager;
    private Database db = null;

    @Override
    public void onEnable() {
        plugin = this;

        Utils.debug("RUNNING IN DEBUG MODE");

        // Must be loaded on enable as they get the plugin instance when initialized
        locationsManager = new LocationsManager();
        lang = new LangManager();

        reloadPlugin();

        // Register FastLogin hook
        if (getServer().getPluginManager().isPluginEnabled("FastLogin")) {
            new FastLoginHook().register();
            Utils.log("FastLogin hook registered");
        }

        try {
            LogFilter.register();
        } catch (NoClassDefFoundError e) {
            Utils.log("&eFailed to register logging filter");
        }

        // Register event listeners
        if (getConfig().getBoolean("bungeecord.autoLogin")) {
            PluginMsgListener listener = new PluginMsgListener();
            getServer().getMessenger().registerIncomingPluginChannel(this, "userlogin:returned", listener);
            registerEvents(listener);
            Utils.log("Autologin enabled");
        }
        else registerEvents(new JoinQuitListener());

        registerEvents(
                new ChatRestriction(),
                new MovementRestriction(),
                new BlockBreakingRestriction(),
                new BlockPlacingRestriction(),
                new CommandRestriction(),
                new ItemDropRestriction(),
                new AttackRestriction(),
                new ReceiveDamageRestriction()
        );

        // Register Item Pickup restriction if class exists
        try {
            Class.forName("org.bukkit.event.entity.EntityPickupItemEvent");
            getServer().getPluginManager().registerEvents(new ItemPickupRestriction(), this);
        }
        catch (ClassNotFoundException ignored) {}

        CommandHandler mainCommand = new CommandHandler("userlogin");
        mainCommand.add(
                new HelpCommand(), new SetCommand(),
                new ReloadCommand(), new UnregisterCommand()
        );

        // Register commands
        mainCommand.register();
        new LoginCommand().register();
        new RegisterCommand().register();
        new ChangePasswordCommand().register();

        // bStats setup
        if (!getConfig().getBoolean("debug")) {
            Metrics metrics = new Metrics(this, 8586);

            metrics.addCustomChart(new SimplePie("storage_type", () -> getConfig().getString("database.type", "yaml").toLowerCase()));
            metrics.addCustomChart(new SimplePie("lang", () -> getConfig().getString("lang", "en_US")));
        }

        // Check for updates
        if (getConfig().getBoolean("checkUpdates", true))
            getServer().getScheduler().runTaskAsynchronously(this, this::checkUpdates);

        Utils.log(getName() + " v" + getDescription().getVersion() + " enabled");
    }

    public void reloadPlugin() {
        // Load configurations
        saveDefaultConfig();
        reloadConfig();

        locationsManager.reload();
        lang.reload();

        // Cancel all plugin tasks
        getServer().getScheduler().cancelTasks(this);

        // Start database
        disconnectDatabase();

        db = Database.select();
        getServer().getScheduler().runTaskAsynchronously(this, this::connectDatabase);
    }

    private void disconnectDatabase() {
        if (db == null) return;

        try { db.disconnect(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void connectDatabase() {
        try {
            db.connect();
            if (db.shouldLogConnected()) Utils.log(lang.getMessage("other.database_connected"));
        } catch (Exception e) {
            String log = e instanceof ClassNotFoundException ?
                    lang.getMessage("other.driver_missing").replace("{driver}", e.getMessage()) :
                    lang.getMessage("other.database_error");
            if (log != null) Utils.log(log);
            e.printStackTrace();
        }
    }

    private void checkUpdates() {
        String latest = Utils.fetch("https://api.spigotmc.org/legacy/update.php?resource=" + PLUGIN_ID);
        String current = getDescription().getVersion();

        if (latest == null) Utils.log("&cUnable to fetch latest version");
        else if (!latest.equalsIgnoreCase(current))
            Utils.log("&eA new UserLogin version is available! (v" + latest + ")");
        else Utils.log("&aRunning latest version! (v" + current + ")");
    }

    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) getServer().getPluginManager().registerEvents(listener, this);
    }

    public boolean hasPack() {
        return Bukkit.getPluginManager().isPluginEnabled("PressurePack");
    }

    @Override
    public void onDisable() { disconnectDatabase(); }

    public static UserLogin getPlugin() {
        return plugin;
    }

    public Database getDB() {
        return db;
    }

    public LangManager getLang() {
        return lang;
    }

    public LocationsManager getLocations() {
        return locationsManager;
    }
}
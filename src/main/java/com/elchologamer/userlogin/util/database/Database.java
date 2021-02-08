package com.elchologamer.userlogin.util.database;

import com.elchologamer.userlogin.UserLogin;
import com.elchologamer.userlogin.util.Utils;
import com.elchologamer.userlogin.util.database.sql.MariaDB;
import com.elchologamer.userlogin.util.database.sql.MySQL;
import com.elchologamer.userlogin.util.database.sql.PostgreSQL;
import com.elchologamer.userlogin.util.database.sql.SQLite;

import java.util.UUID;

public abstract class Database {

    private static final UserLogin plugin = UserLogin.getPlugin();

    public static Database select() {
        String type = plugin.getConfig().getString("database.type", "yaml");

        switch (type.toLowerCase()) {
            case "mysql":
                return new MySQL();
            case "postgresql":
                return new PostgreSQL();
            case "mariadb":
                return new MariaDB();
            case "sqlite":
                return new SQLite();
            case "mongodb":
                return new MongoDB();
            default:
                Utils.log("&eInvalid database type selected, defaulting to \"yaml\"");
            case "yaml":
                return new YamlDB();
        }
    }

    public abstract void connect() throws Exception;

    public abstract void disconnect() throws Exception;

    public abstract String getPassword(UUID uuid);

    public abstract void setPassword(UUID uuid, String password) throws Exception;

    public abstract void deletePassword(UUID uuid) throws Exception;

    public UserLogin getPlugin() {
        return plugin;
    }
}
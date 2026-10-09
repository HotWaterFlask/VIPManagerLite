package com.vipmanagerlite;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据库管理类
 * 支持SQLite和MySQL
 */
public class DatabaseManager {

    private final VIPManagerLite plugin;
    private final ConfigManager configManager;
    private Connection connection;
    private String tableName;

    public DatabaseManager(VIPManagerLite plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tableName = configManager.getTablePrefix() + "vip_data";
    }

    /**
     * 连接数据库
     */
    public boolean connect() {
        try {
            String type = configManager.getDatabaseType();
            if (type.equalsIgnoreCase("mysql")) {
                return connectMySQL();
            } else {
                return connectSQLite();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("数据库连接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 连接SQLite
     */
    private boolean connectSQLite() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "database.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdir();
            }
            String url = "jdbc:sqlite:" + dataFolder.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("已连接到SQLite数据库");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite连接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 连接MySQL
     */
    private boolean connectMySQL() {
        try {
            String host = configManager.getMySQLHost();
            int port = configManager.getMySQLPort();
            String database = configManager.getMySQLDatabase();
            String username = configManager.getMySQLUsername();
            String password = configManager.getMySQLPassword();

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("已连接到MySQL数据库");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL连接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建数据表并进行迁移
     */
    public boolean createTable() {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "vip_group VARCHAR(50) NOT NULL, " +
                "original_group VARCHAR(50) NOT NULL, " +
                "expire_time BIGINT NOT NULL, " +
                "pending_removal BOOLEAN DEFAULT FALSE" +
                ")";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            // 迁移旧表：如果是从旧版升级，pending_removal 列可能不存在
            try {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN pending_removal BOOLEAN DEFAULT FALSE");
            } catch (SQLException e) {
                // 仅忽略"列已存在"的错误（SQLite: "duplicate column", MySQL: "Duplicate column name"/1060），其他错误仍输出警告
                String msg = e.getMessage().toLowerCase();
                if (!msg.contains("duplicate column") && !msg.contains("1060")) {
                    plugin.getLogger().warning("添加pending_removal列失败: " + e.getMessage());
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据表失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存VIP数据（支持 UPSERT）
     */
    public boolean saveVIP(UUID uuid, String vipGroup, String originalGroup, long expireTime) {
        if (!ensureConnection()) {
            return false;
        }

        String sql;
        String dbType = configManager.getDatabaseType();
        if (dbType.equalsIgnoreCase("mysql")) {
            sql = "INSERT INTO " + tableName + " (uuid, vip_group, original_group, expire_time, pending_removal) " +
                  "VALUES (?, ?, ?, ?, FALSE) " +
                  "ON DUPLICATE KEY UPDATE vip_group=?, original_group=?, expire_time=?, pending_removal=FALSE";
        } else {
            sql = "INSERT OR REPLACE INTO " + tableName + " (uuid, vip_group, original_group, expire_time, pending_removal) " +
                  "VALUES (?, ?, ?, ?, FALSE)";
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, vipGroup);
            statement.setString(3, originalGroup);
            statement.setLong(4, expireTime);

            if (dbType.equalsIgnoreCase("mysql")) {
                statement.setString(5, vipGroup);
                statement.setString(6, originalGroup);
                statement.setLong(7, expireTime);
            }

            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("保存VIP数据失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除VIP数据
     */
    public boolean deleteVIP(UUID uuid) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "DELETE FROM " + tableName + " WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("删除VIP数据失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 标记VIP为待清理（玩家离线，无法立即恢复权限组）
     */
    public boolean markPendingRemoval(UUID uuid) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "UPDATE " + tableName + " SET pending_removal = TRUE WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("标记待清理失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取玩家VIP信息（原始数据，不检查过期和待清理状态）
     * 供调度器到期时调用
     */
    public VIPData.VIPInfo getVIPInfoRaw(UUID uuid) {
        if (!ensureConnection()) {
            return null;
        }

        String sql = "SELECT vip_group, original_group, expire_time, pending_removal FROM " + tableName + " WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new VIPData.VIPInfo(
                        rs.getString("vip_group"),
                        rs.getString("original_group"),
                        rs.getLong("expire_time"),
                        rs.getBoolean("pending_removal")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询VIP原始数据失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取玩家VIP信息（仅有效VIP，不包括过期和待清理的记录）
     */
    public VIPData.VIPInfo getVIPInfo(UUID uuid) {
        if (!ensureConnection()) {
            return null;
        }

        String sql = "SELECT vip_group, original_group, expire_time, pending_removal FROM " + tableName + " WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String vipGroup = rs.getString("vip_group");
                    String originalGroup = rs.getString("original_group");
                    long expireTime = rs.getLong("expire_time");
                    boolean pendingRemoval = rs.getBoolean("pending_removal");

                    // 已标记待清理的记录视为无效VIP
                    if (pendingRemoval) {
                        return null;
                    }

                    // 检查是否过期
                    if (System.currentTimeMillis() > expireTime) {
                        return null;
                    }

                    return new VIPData.VIPInfo(vipGroup, originalGroup, expireTime);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询VIP数据失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取待清理的VIP信息（用于玩家上线时处理）
     * @param uuid 玩家UUID
     * @return VIP信息，如果没有待清理记录则返回null
     */
    public VIPData.VIPInfo getPendingRemovalVIP(UUID uuid) {
        if (!ensureConnection()) {
            return null;
        }

        String sql = "SELECT vip_group, original_group, expire_time FROM " + tableName +
                     " WHERE uuid = ? AND pending_removal = TRUE";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String vipGroup = rs.getString("vip_group");
                    String originalGroup = rs.getString("original_group");
                    long expireTime = rs.getLong("expire_time");
                    return new VIPData.VIPInfo(vipGroup, originalGroup, expireTime);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询待清理VIP失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取所有VIP原始数据（不过滤过期和待清理状态）
     * 供启动时初始化调度器使用
     */
    public Map<UUID, VIPData.VIPInfo> getAllVIPRaw() {
        Map<UUID, VIPData.VIPInfo> map = new HashMap<>();
        if (!ensureConnection()) {
            return map;
        }

        String sql = "SELECT uuid, vip_group, original_group, expire_time, pending_removal FROM " + tableName;

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String vipGroup = rs.getString("vip_group");
                String originalGroup = rs.getString("original_group");
                long expireTime = rs.getLong("expire_time");
                boolean pendingRemoval = rs.getBoolean("pending_removal");

                map.put(uuid, new VIPData.VIPInfo(vipGroup, originalGroup, expireTime, pendingRemoval));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询所有VIP原始数据失败: " + e.getMessage());
        }

        return map;
    }

    /**
     * 获取所有VIP数据（不包括已过期和待清理的记录）
     */
    public Map<UUID, VIPData.VIPInfo> getAllVIP() {
        Map<UUID, VIPData.VIPInfo> map = new HashMap<>();
        if (!ensureConnection()) {
            return map;
        }

        String sql = "SELECT uuid, vip_group, original_group, expire_time FROM " + tableName +
                     " WHERE pending_removal = FALSE AND expire_time > " + System.currentTimeMillis();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String vipGroup = rs.getString("vip_group");
                String originalGroup = rs.getString("original_group");
                long expireTime = rs.getLong("expire_time");

                map.put(uuid, new VIPData.VIPInfo(vipGroup, originalGroup, expireTime));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询所有VIP数据失败: " + e.getMessage());
        }

        return map;
    }

    /**
     * 获取用于列表展示的VIP数据：
     * 包含有效VIP，以及已标记待清理（pending_removal）的记录，
     * 以便 /vipmgr list 将待清理玩家显示为"已过期待清理"。
     */
    public Map<UUID, VIPData.VIPInfo> getAllVIPForList() {
        Map<UUID, VIPData.VIPInfo> map = new HashMap<>();
        if (!ensureConnection()) {
            return map;
        }

        String sql = "SELECT uuid, vip_group, original_group, expire_time, pending_removal FROM " + tableName +
                     " WHERE pending_removal = TRUE OR expire_time > " + System.currentTimeMillis();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String vipGroup = rs.getString("vip_group");
                String originalGroup = rs.getString("original_group");
                long expireTime = rs.getLong("expire_time");
                boolean pendingRemoval = rs.getBoolean("pending_removal");

                map.put(uuid, new VIPData.VIPInfo(vipGroup, originalGroup, expireTime, pendingRemoval));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询VIP列表数据失败: " + e.getMessage());
        }

        return map;
    }

    /**
     * 重连数据库（重载配置后调用）
     * 先尝试新配置连接，成功后才关闭旧连接；失败则保留旧连接
     */
    public boolean reconnect() {
        Connection oldConnection = this.connection;
        String oldTableName = this.tableName;

        this.tableName = configManager.getTablePrefix() + "vip_data";
        if (!connect()) {
            // 新连接失败，恢复旧连接状态
            this.connection = oldConnection;
            this.tableName = oldTableName;
            return false;
        }

        // 新连接成功，关闭旧连接
        try {
            if (oldConnection != null && !oldConnection.isClosed()) {
                oldConnection.close();
            }
        } catch (SQLException e) {
            // 忽略关闭旧连接的错误
        }

        return createTable();
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 确保连接有效，连接失效时自动重连
     * @return 连接是否可用
     */
    private boolean ensureConnection() {
        if (connection != null) {
            try {
                // 用真实查询验证连接是否存活。
                // 不能用 connection.isValid()：服务器端 wait_timeout 断开的"半开连接"
                // 在客户端仍显示为 valid，会导致后续 SQL 才报错。
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("SELECT 1")) {
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("数据库连接已失效，尝试自动重连...");
            }
            close();
        }
        return connect();
    }

    /**
     * 检查连接是否有效
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}

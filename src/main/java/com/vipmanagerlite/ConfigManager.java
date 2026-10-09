package com.vipmanagerlite;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 配置管理类
 * 管理插件的配置文件
 */
public class ConfigManager {

    private final VIPManagerLite plugin;
    private FileConfiguration config;

    public ConfigManager(VIPManagerLite plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    /**
     * 获取数据库类型（sqlite 或 mysql）
     */
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    /**
     * 获取MySQL主机
     */
    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    /**
     * 获取MySQL端口
     */
    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    /**
     * 获取MySQL数据库名
     */
    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "vipmanager");
    }

    /**
     * 获取MySQL用户名
     */
    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    /**
     * 获取MySQL密码
     */
    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }

    /**
     * 获取表前缀（支持 database.table-prefix 和旧路径 database.mysql.table-prefix）
     */
    public String getTablePrefix() {
        // 优先使用新路径
        String prefix = config.getString("database.table-prefix");
        if (prefix != null) {
            return prefix;
        }
        // 兼容旧路径
        return config.getString("database.mysql.table-prefix", "vipmgr_");
    }

    /**
     * 获取工作模式
     * @return "vault" 或 "command"
     */
    public String getMode() {
        return config.getString("mode", "vault");
    }

    /**
     * 是否使用 Vault 模式
     * @return 是否使用 Vault 模式
     */
    public boolean isVaultMode() {
        return getMode().equalsIgnoreCase("vault");
    }

    /**
     * 判断离线玩家是否立即处理（到期/移除）
     * @return true=直接处理，false=等玩家上线再处理
     */
    public boolean isImmediateOfflineMode() {
        return config.getString("offline-mode", "on-join").equalsIgnoreCase("immediate");
    }

    /**
     * 获取所有VIP组名称列表
     * @return VIP组名称列表，配置缺失时返回空列表
     */
    public List<String> getVIPGroups() {
        var section = config.getConfigurationSection("vip-groups");
        if (section == null) {
            plugin.getLogger().warning("配置文件中没有 vip-groups 节！");
            return new java.util.ArrayList<>();
        }
        return new java.util.ArrayList<>(section.getKeys(false));
    }

    /**
     * 检查指定的组是否在VIP组配置中
     * @param groupName 组名
     * @return 是否在配置中
     */
    public boolean isVIPGroup(String groupName) {
        var section = config.getConfigurationSection("vip-groups");
        if (section == null) {
            return false;
        }
        return section.contains(groupName);
    }

    /**
     * 获取VIP组的别名
     * @param groupName 组名
     * @return 别名，如果没有配置则返回组名本身
     */
    public String getGroupAlias(String groupName) {
        String alias = config.getString("vip-groups." + groupName + ".alias");
        return alias != null ? alias : groupName;
    }

    /**
     * 获取VIP组的描述
     * @param groupName 组名
     * @return 描述
     */
    public String getGroupDescription(String groupName) {
        return config.getString("vip-groups." + groupName + ".description", "无描述");
    }

    /**
     * 获取VIP组开通时执行的命令列表
     * @param groupName 组名
     * @return 命令列表
     */
    public List<String> getGrantCommands(String groupName) {
        return config.getStringList("vip-groups." + groupName + ".commands.on-grant");
    }

    /**
     * 获取VIP组到期时执行的命令列表
     * @param groupName 组名
     * @return 命令列表
     */
    public List<String> getExpireCommands(String groupName) {
        return config.getStringList("vip-groups." + groupName + ".commands.on-expire");
    }

    /**
     * 获取VIP组到期后回到的权限组
     * @param groupName VIP组名
     * @return 到期后回到的权限组，支持 %originalgroup% 变量
     */
    public String getExpireGroup(String groupName) {
        return config.getString("vip-groups." + groupName + ".expire-group", "%originalgroup%");
    }

}

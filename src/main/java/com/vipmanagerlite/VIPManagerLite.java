package com.vipmanagerlite;

import com.vipmanagerlite.commands.VIPCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * VIPManagerLite 主类
 * 轻量版限时VIP权限组管理插件
 */
public class VIPManagerLite extends JavaPlugin {

    private static VIPManagerLite instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PermissionManager permissionManager;
    private ExpiryScheduler expiryScheduler;
    private VIPCommand vipCommand;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        getLogger().info("配置文件已加载");

        // 初始化权限管理器
        permissionManager = new PermissionManager(this);
        if (!permissionManager.isAvailable()) {
            getLogger().severe("权限管理器初始化失败，请确保已安装 Vault 和权限插件！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("权限管理器已初始化，工作模式: " + permissionManager.getMode());

        // 初始化数据库
        databaseManager = new DatabaseManager(this, configManager);
        if (!databaseManager.connect()) {
            getLogger().severe("数据库连接失败，插件停用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!databaseManager.createTable()) {
            getLogger().severe("创建数据表失败，插件停用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("数据库已连接");

        // 初始化到期调度器
        expiryScheduler = new ExpiryScheduler(this);

        // 启动时处理：为未过期的VIP安排到期任务，已过期的标记待清理
        startupCleanup();

        // 注册玩家上线监听器（处理离线期间到期/标记待清理的玩家）
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // 注册命令
        vipCommand = new VIPCommand(this, permissionManager);
        getCommand("vipmgr").setExecutor(vipCommand);
        getCommand("vipmgr").setTabCompleter(vipCommand);

        getLogger().info("VIPManagerLite 已启用！");
    }

    @Override
    public void onDisable() {
        // 取消所有到期任务
        if (expiryScheduler != null) {
            expiryScheduler.cancelAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
            getLogger().info("数据库连接已关闭");
        }
        instance = null;
        getLogger().info("VIPManagerLite 已停用！");
    }

    /**
     * 插件重载：
     * 1. 取消所有到期定时任务
     * 2. 重载配置文件
     * 3. 重新初始化权限管理器
     * 4. 重新安排所有到期任务
     */
    public void reload() {
        // 取消所有到期任务
        expiryScheduler.cancelAll();
        getLogger().info("已取消所有到期任务");

        // 重载配置
        configManager.loadConfig();
        getLogger().info("配置文件已重载");

        // 重连数据库（支持切换数据库类型、表前缀等）
        if (!databaseManager.reconnect()) {
            getLogger().warning("数据库重连失败，继续使用旧的数据库连接");
        } else {
            getLogger().info("数据库已重连");
        }

        // 重新初始化权限管理器
        permissionManager = new PermissionManager(this);
        if (!permissionManager.isAvailable()) {
            getLogger().severe("权限管理器初始化失败！");
            return;
        }
        // 更新命令处理器的权限管理器引用
        vipCommand.setPermissionManager(permissionManager);
        getLogger().info("权限管理器已重载，工作模式: " + permissionManager.getMode());

        // 重新安排到期任务
        startupCleanup();

        getLogger().info("VIPManagerLite 重载完成！");
    }

    /**
     * 启动时/重载时的清理工作：
     * - 待清理（pending_removal）：immediate 模式下立即处理，否则等玩家上线
     * - 已过期的：immediate 模式下立即处理，否则标记 pending_removal
     * - 未过期的：按到期时间安排精准定时任务
     */
    private void startupCleanup() {
        Map<UUID, VIPData.VIPInfo> allVIP = databaseManager.getAllVIPRaw();
        boolean immediate = configManager.isImmediateOfflineMode();
        int processed = 0;
        int pending = 0;
        int scheduled = 0;

        for (Map.Entry<UUID, VIPData.VIPInfo> entry : allVIP.entrySet()) {
            UUID uuid = entry.getKey();
            VIPData.VIPInfo info = entry.getValue();

            // 已标记待清理的记录
            if (info.isPendingRemoval()) {
                if (immediate) {
                    // 立即模式下，重载时顺带把待清理记录也处理掉
                    if (processExpiredNow(uuid, info)) {
                        processed++;
                    } else {
                        pending++;
                    }
                } else {
                    // 等玩家上线处理
                    pending++;
                }
                continue;
            }

            if (info.getExpireTime() <= System.currentTimeMillis()) {
                // 已到期
                if (immediate) {
                    if (processExpiredNow(uuid, info)) {
                        processed++;
                    } else {
                        // 处理失败，标记待清理，等玩家上线重试
                        databaseManager.markPendingRemoval(uuid);
                        pending++;
                    }
                } else {
                    // 标记待清理，等玩家上线时处理
                    databaseManager.markPendingRemoval(uuid);
                    pending++;
                }
            } else {
                // 未到期：安排精准定时任务
                expiryScheduler.schedule(uuid, info.getExpireTime());
                scheduled++;
            }
        }

        if (processed > 0) {
            getLogger().info("已处理 " + processed + " 个VIP");
        }
        if (pending > 0) {
            getLogger().info("有 " + pending + " 个VIP待玩家上线处理");
        }
        if (scheduled > 0) {
            getLogger().info("已为 " + scheduled + " 个VIP安排了到期任务");
        }
    }

    /**
     * 立即处理一条记录：恢复权限组并删除数据库记录
     * @param uuid 玩家UUID
     * @param info VIP信息
     * @return 是否处理成功（权限组已恢复并删除记录）
     */
    private boolean processExpiredNow(UUID uuid, VIPData.VIPInfo info) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (processVIPExpiry(player, info) == null) {
            return false;
        }
        databaseManager.deleteVIP(uuid);
        return true;
    }

    /**
     * 处理VIP到期：恢复权限组并执行到期命令
     * @param player 玩家（可离线）
     * @param info VIP信息
     * @return 实际恢复到的权限组名称，失败返回 null
     */
    public String processVIPExpiry(OfflinePlayer player, VIPData.VIPInfo info) {
        String originalGroup = info.getOriginalGroup();
        String expireGroup = configManager.getExpireGroup(info.getVipGroup());
        // 替换 %originalgroup% 变量
        if (expireGroup.contains("%originalgroup%")) {
            expireGroup = expireGroup.replace("%originalgroup%", originalGroup);
        }

        if (!permissionManager.setPlayerGroup(player, expireGroup)) {
            return null;
        }

        // 执行到期命令
        CommandExecutor.executeCommands(
            configManager.getExpireCommands(info.getVipGroup()),
            player,
            originalGroup,
            info.getVipGroup()
        );

        return expireGroup;
    }

    public static VIPManagerLite getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public ExpiryScheduler getExpiryScheduler() {
        return expiryScheduler;
    }
}

package com.vipmanagerlite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VIP到期精准定时调度器
 * 每个VIP到期时间设置一个一次性定时任务，到点触发处理
 */
public class ExpiryScheduler {

    private final VIPManagerLite plugin;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public ExpiryScheduler(VIPManagerLite plugin) {
        this.plugin = plugin;
    }

    /**
     * 为玩家安排到期任务
     * @param uuid 玩家UUID
     * @param expireTime 到期时间戳（毫秒）
     */
    public void schedule(UUID uuid, long expireTime) {
        // 先取消已有的任务（如果有的话）
        cancel(uuid);

        long now = System.currentTimeMillis();
        long delayTicks = Math.max(0, (expireTime - now) / 50); // 毫秒转tick，1 tick = 50ms

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(uuid);
            handleExpiry(uuid);
        }, delayTicks);

        tasks.put(uuid, task);
    }

    /**
     * 取消某个玩家的到期任务
     */
    public void cancel(UUID uuid) {
        BukkitTask task = tasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 取消所有到期任务（插件停用时调用）
     */
    public void cancelAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    /**
     * VIP到期处理：
     * - 在线 → 立即恢复权限组 + 执行命令 + 删除记录
     * - 离线 → 根据配置：立即处理，或标记 pending_removal 等上线处理
     */
    private void handleExpiry(UUID uuid) {
        // 查询数据库（不检查是否过期，因为此时刚好到期）
        VIPData.VIPInfo info = plugin.getDatabaseManager().getVIPInfoRaw(uuid);
        if (info == null) {
            return; // 记录已被删除，无需处理
        }

        // 跳过已标记待清理的（避免重复处理）
        if (info.isPendingRemoval()) {
            return;
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);

        // 离线且配置为等待上线：标记待清理
        if (onlinePlayer == null && !plugin.getConfigManager().isImmediateOfflineMode()) {
            plugin.getDatabaseManager().markPendingRemoval(uuid);
            plugin.getLogger().info("离线VIP到期已标记: " + uuid);
            return;
        }

        // 立即处理（在线，或离线且配置为直接处理）
        OfflinePlayer target = (onlinePlayer != null) ? onlinePlayer : Bukkit.getOfflinePlayer(uuid);
        String expireGroup = plugin.processVIPExpiry(target, info);
        if (expireGroup != null) {
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage("§e你的VIP已到期，已恢复权限组: " + expireGroup);
            }
            plugin.getDatabaseManager().deleteVIP(uuid);
            plugin.getLogger().info("VIP到期已处理: " + target.getName() + " → " + expireGroup);
        } else {
            // 处理失败：离线时标记待清理等待上线重试
            if (onlinePlayer == null) {
                plugin.getDatabaseManager().markPendingRemoval(uuid);
            }
            plugin.getLogger().warning("VIP到期处理失败: " + target.getName() + "，权限组未恢复");
        }
    }
}

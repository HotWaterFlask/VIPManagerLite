package com.vipmanagerlite;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家上线监听器
 * 处理玩家离线期间到期的VIP
 */
public class PlayerJoinListener implements Listener {

    private final VIPManagerLite plugin;

    public PlayerJoinListener(VIPManagerLite plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 检查是否有待清理的VIP
        VIPData.VIPInfo info = plugin.getDatabaseManager().getPendingRemovalVIP(player.getUniqueId());
        if (info == null) {
            return;
        }

        // 恢复权限组并执行到期命令
        String expireGroup = plugin.processVIPExpiry(player, info);
        if (expireGroup != null) {
            player.sendMessage("§e你的VIP已到期，已恢复权限组: " + expireGroup);
            // 仅在恢复成功时删除数据库记录
            plugin.getDatabaseManager().deleteVIP(player.getUniqueId());
        } else {
            plugin.getLogger().warning("玩家上线VIP处理失败: " + player.getName() + "，权限组未恢复");
        }
    }
}

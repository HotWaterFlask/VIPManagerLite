package com.vipmanagerlite;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

/**
 * 权限组管理类
 * 支持 Vault 模式和命令模式进行权限组操作
 */
public class PermissionManager {

    private final VIPManagerLite plugin;
    private Permission permission;
    private final boolean vaultMode;

    public PermissionManager(VIPManagerLite plugin) {
        this.plugin = plugin;
        this.vaultMode = plugin.getConfigManager().isVaultMode();

        if (vaultMode) {
            setupPermission();
        }
    }

    /**
     * 设置 Vault 权限接口
     * 失败情况由 isAvailable() 判断
     */
    private void setupPermission() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("未找到 Vault 插件！");
            return;
        }

        RegisteredServiceProvider<Permission> rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            plugin.getLogger().severe("未找到权限插件！");
            return;
        }

        permission = rsp.getProvider();
    }

    /**
     * 获取玩家当前权限组
     * 使用带 world 的 OfflinePlayer 重载，避免离线玩家 getWorld() 为 null 导致 NPE
     *
     * @param player 玩家（可离线）
     * @return 权限组名称
     */
    public String getPlayerGroup(OfflinePlayer player) {
        if (vaultMode) {
            if (permission == null) {
                return null;
            }
            return permission.getPrimaryGroup(resolveWorld(player), player);
        } else {
            // 命令模式下无法获取玩家当前组，返回 null
            // 需要在 give 命令时由管理员指定原组
            return null;
        }
    }

    /**
     * 设置玩家权限组
     *
     * @param player 玩家（可离线）
     * @param group 权限组名称
     * @return 是否成功
     */
    public boolean setPlayerGroup(OfflinePlayer player, String group) {
        if (!vaultMode) {
            // 命令模式下不执行任何操作，权限组切换通过 on-grant/on-expire 命令实现
            return true;
        }

        // Vault 模式
        if (permission == null) {
            return false;
        }

        // 先获取当前权限组
        String currentGroup = getPlayerGroup(player);

        // GroupManager 的 Vault 适配器在 world=null 时按玩家名查世界，仅对在线玩家有效，
        // 离线会失败；因此显式传入具体世界名。
        String world = resolveWorld(player);

        // 如果有当前组且与新组不同，先移除旧组
        if (currentGroup != null && !currentGroup.equals(group)) {
            permission.playerRemoveGroup(world, player, currentGroup);
            // 移除旧组后若添加新组失败，回滚恢复原组，避免玩家失去所有权限组
            if (!permission.playerAddGroup(world, player, group)) {
                permission.playerAddGroup(world, player, currentGroup);
                return false;
            }
            return true;
        }

        // 添加新的权限组
        return permission.playerAddGroup(world, player, group);
    }

    /**
     * 解析 Vault 组操作使用的世界名。
     * 在线玩家用其当前世界，离线玩家用主世界（第一个已加载世界）。
     * @param player 目标玩家（可离线）
     * @return 世界名，无可用世界时返回 null
     */
    private String resolveWorld(OfflinePlayer player) {
        if (player.isOnline()) {
            return player.getPlayer().getWorld().getName();
        }
        List<World> worlds = plugin.getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0).getName();
    }

    /**
     * 检查权限管理器是否可用
     */
    public boolean isAvailable() {
        if (vaultMode) {
            return permission != null;
        } else {
            // 命令模式总是可用的
            return true;
        }
    }

    /**
     * 获取当前工作模式
     */
    public String getMode() {
        return vaultMode ? "vault" : "command";
    }
}

package com.vipmanagerlite.commands;

import com.vipmanagerlite.PermissionManager;
import com.vipmanagerlite.TimeParser;
import com.vipmanagerlite.VIPData;
import com.vipmanagerlite.VIPManagerLite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * VIP管理命令处理器
 * 处理 /vipmgr 命令及其子命令
 */
public class VIPCommand implements CommandExecutor, TabCompleter {

    private final VIPManagerLite plugin;
    private PermissionManager permissionManager;

    public VIPCommand(VIPManagerLite plugin, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.permissionManager = permissionManager;
    }

    /**
     * 更新权限管理器引用（重载时调用）
     */
    public void setPermissionManager(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 没有参数时显示帮助
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                handleInfo(sender);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "look":
                handleLook(sender, args);
                break;
            case "upgrade":
                handleUpgrade(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sender.sendMessage("§c未知命令，输入 /vipmgr 查看帮助");
                break;
        }

        return true;
    }

    // 管理员专用子命令
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList("give", "remove", "list", "look", "upgrade", "reload");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // 补全子命令
            List<String> suggestions = new ArrayList<>();
            suggestions.add("info"); // 所有玩家都可以用 info
            if (sender.hasPermission("vipmanagerlite.admin")) {
                suggestions.addAll(ADMIN_SUBCOMMANDS);
            }
            return filterStartsWith(suggestions, args[0]);
        }

        if (!sender.hasPermission("vipmanagerlite.admin")) {
            return null; // 非管理员不提供进一步补全
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give":
                case "look":
                    // 补全在线玩家名
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                case "remove":
                case "upgrade":
                    // 补全有VIP的玩家名
                    return filterStartsWith(getVIPPlayerNames(), args[1]);
                default:
                    return null;
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "give":
                case "upgrade":
                    // 补全VIP组名
                    return filterStartsWith(plugin.getConfigManager().getVIPGroups(), args[2]);
                default:
                    return null;
            }
        }

        return null;
    }

    /**
     * 过滤以指定前缀开头的字符串列表
     */
    private List<String> filterStartsWith(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    /**
     * 获取在线玩家名称列表
     */
    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取有VIP的玩家名称列表（在线+离线）
     */
    private List<String> getVIPPlayerNames() {
        Map<UUID, VIPData.VIPInfo> allVIP = plugin.getDatabaseManager().getAllVIP();
        return allVIP.keySet().stream()
                .map(uuid -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    return player.getName();
                })
                .filter(name -> name != null)
                .collect(Collectors.toList());
    }

    /**
     * 显示帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== VIPManagerLite 帮助 =====");
        sender.sendMessage("§e/vipmgr info §7- 查看自己的VIP信息");
        if (sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§e/vipmgr give <玩家> <VIP组> <时间> [原权限组] §7- 给予玩家VIP");
            sender.sendMessage("§e/vipmgr remove <玩家> §7- 移除玩家VIP");
            sender.sendMessage("§e/vipmgr list §7- 列出所有VIP玩家");
            sender.sendMessage("§e/vipmgr look <玩家> §7- 查看指定玩家的VIP信息");
            sender.sendMessage("§e/vipmgr upgrade <玩家> <新VIP组> §7- 升级已有VIP玩家的VIP组");
            sender.sendMessage("§e/vipmgr reload §7- 重载插件配置文件");
        }
    }

    /**
     * 处理 /vipmgr info 命令
     * 玩家查看自己的VIP信息
     */
    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令");
            return;
        }

        Player player = (Player) sender;
        // 从数据库查询玩家VIP信息
        VIPData.VIPInfo info = plugin.getDatabaseManager().getVIPInfo(player.getUniqueId());
        sendVIPInfo(sender, "你的VIP信息", info);
    }

    /**
     * 输出VIP信息（供 info / look 复用）
     * @param sender 消息接收者
     * @param title 标题
     * @param info VIP信息，null 表示无VIP
     */
    private void sendVIPInfo(CommandSender sender, String title, VIPData.VIPInfo info) {
        sender.sendMessage("§6===== " + title + " =====");
        if (info == null) {
            sender.sendMessage("§e当前状态: §c无VIP");
            sender.sendMessage("§e原权限组: §7无");
            sender.sendMessage("§e剩余时间: §7无");
        } else {
            String groupAlias = plugin.getConfigManager().getGroupAlias(info.getVipGroup());
            String groupDesc = plugin.getConfigManager().getGroupDescription(info.getVipGroup());
            sender.sendMessage("§e当前状态: §a" + groupAlias);
            sender.sendMessage("§e组描述: §7" + groupDesc);
            sender.sendMessage("§e原权限组: §7" + info.getOriginalGroup());
            sender.sendMessage("§e剩余时间: §7" + TimeParser.formatTime(info.getRemainingTime()));
        }
    }

    /**
     * 处理 /vipmgr give 命令
     * 给予玩家VIP
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage("§c用法: /vipmgr give <玩家> <VIP组> <时间> [原权限组]");
            sender.sendMessage("§c示例: /vipmgr give Steve vip 30d");
            return;
        }

        String targetName = args[1];
        String vipGroup = args[2];
        String timeStr = args[3];

        // 检查VIP组是否在白名单中
        if (!plugin.getConfigManager().isVIPGroup(vipGroup)) {
            sender.sendMessage("§c无效的VIP组: " + vipGroup);
            sender.sendMessage("§e可用的VIP组: " + String.join(", ", plugin.getConfigManager().getVIPGroups()));
            return;
        }

        // 解析时间
        long duration = TimeParser.parseTime(timeStr);
        if (duration == -1) {
            sender.sendMessage("§c时间格式错误！支持: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
            sender.sendMessage("§c示例: 30d = 30天, 2h = 2小时, 1w = 1周");
            return;
        }

        // 获取目标玩家（必须在线）
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage("§c玩家不在线");
            return;
        }

        UUID targetUUID = targetPlayer.getUniqueId();

        // 检查玩家是否已有VIP
        if (plugin.getDatabaseManager().getVIPInfo(targetUUID) != null) {
            sender.sendMessage("§c该玩家已有VIP，请先使用 /vipmgr remove 移除");
            return;
        }

        // 获取玩家当前权限组作为原组
        // 优先使用命令中显式指定的原组（命令模式无法自动查询，需管理员指定）
        String originalGroup;
        if (args.length >= 5) {
            originalGroup = args[4];
        } else {
            originalGroup = permissionManager.getPlayerGroup(targetPlayer);
        }
        if (originalGroup == null) {
            sender.sendMessage("§c无法自动获取玩家当前权限组，请在命令末尾指定原权限组：");
            sender.sendMessage("§c/vipmgr give <玩家> <VIP组> <时间> <原权限组>");
            return;
        }

        // 计算过期时间
        long expireTime = System.currentTimeMillis() + duration;

        // 先保存到数据库
        if (!plugin.getDatabaseManager().saveVIP(targetUUID, vipGroup, originalGroup, expireTime)) {
            sender.sendMessage("§c保存VIP数据失败！");
            return;
        }

        // 切换权限组
        if (!permissionManager.setPlayerGroup(targetPlayer, vipGroup)) {
            sender.sendMessage("§c切换权限组失败！请检查Vault和权限插件配置");
            // 回滚数据库
            plugin.getDatabaseManager().deleteVIP(targetUUID);
            return;
        }

        // 安排到期精准定时任务
        plugin.getExpiryScheduler().schedule(targetUUID, expireTime);

        // 执行开通命令
        com.vipmanagerlite.CommandExecutor.executeCommands(
            plugin.getConfigManager().getGrantCommands(vipGroup),
            targetPlayer,
            originalGroup,
            vipGroup
        );

        sender.sendMessage("§a已给予 " + targetName + " VIP组: " + vipGroup + ", 时长: " + TimeParser.formatTime(duration));
        targetPlayer.sendMessage("§a你已获得VIP: " + vipGroup + ", 时长: " + TimeParser.formatTime(duration));
    }

    /**
     * 处理 /vipmgr remove 命令
     * 移除玩家VIP（支持离线玩家）
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /vipmgr remove <玩家>");
            return;
        }

        String targetName = args[1];

        // 获取玩家UUID（支持离线玩家）
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (offlinePlayer == null || offlinePlayer.getName() == null) {
            sender.sendMessage("§c找不到该玩家");
            return;
        }
        UUID targetUUID = offlinePlayer.getUniqueId();

        // 检查玩家是否有VIP
        VIPData.VIPInfo info = plugin.getDatabaseManager().getVIPInfo(targetUUID);
        if (info == null) {
            sender.sendMessage("§c该玩家没有VIP");
            return;
        }

        // 取消到期精准定时任务
        plugin.getExpiryScheduler().cancel(targetUUID);

        // 检查玩家是否在线
        Player targetPlayer = offlinePlayer.getPlayer();
        boolean online = targetPlayer != null && targetPlayer.isOnline();

        // 离线且配置为等待上线：标记为待清理，玩家上线时自动处理
        if (!online && !plugin.getConfigManager().isImmediateOfflineMode()) {
            if (!plugin.getDatabaseManager().markPendingRemoval(targetUUID)) {
                sender.sendMessage("§c标记待清理失败！");
                return;
            }

            sender.sendMessage("§a已标记 " + targetName + " 的VIP为待清理，将在玩家上线时恢复权限组");
            return;
        }

        // 立即处理（在线，或离线且配置为直接处理）
        String expireGroup = plugin.processVIPExpiry(offlinePlayer, info);
        if (expireGroup == null) {
            sender.sendMessage("§c恢复权限组失败！");
            return;
        }

        if (!plugin.getDatabaseManager().deleteVIP(targetUUID)) {
            sender.sendMessage("§c删除VIP数据失败！");
            return;
        }

        sender.sendMessage("§a已移除 " + targetName + " 的VIP，已恢复权限组: " + expireGroup);
        if (online) {
            targetPlayer.sendMessage("§e你的VIP已到期，已恢复权限组: " + expireGroup);
        }
    }

    /**
     * 处理 /vipmgr list 命令
     * 列出所有VIP玩家
     */
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        Map<UUID, VIPData.VIPInfo> allVIP = plugin.getDatabaseManager().getAllVIPForList();
        sender.sendMessage("§6===== VIP玩家列表 (" + allVIP.size() + ") =====");
        if (allVIP.isEmpty()) {
            sender.sendMessage("§7暂无VIP玩家");
        } else {
            for (Map.Entry<UUID, VIPData.VIPInfo> entry : allVIP.entrySet()) {
                String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                VIPData.VIPInfo info = entry.getValue();
                String groupAlias = plugin.getConfigManager().getGroupAlias(info.getVipGroup());
                if (info.isPendingRemoval()) {
                    // 待清理记录：已过期但尚未恢复权限组，等玩家上线时处理
                    sender.sendMessage("§e" + playerName + " §7- §a" + groupAlias + " §7(§c已过期 待清理§7)");
                } else {
                    sender.sendMessage("§e" + playerName + " §7- §a" + groupAlias + " §7(剩余: " + TimeParser.formatTime(info.getRemainingTime()) + ")");
                }
            }
        }
    }

    /**
     * 处理 /vipmgr look 命令
     * 查看指定玩家的VIP信息
     */
    private void handleLook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /vipmgr look <玩家>");
            return;
        }

        String targetName = args[1];

        // 支持查询离线玩家
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (offlinePlayer == null || offlinePlayer.getName() == null) {
            sender.sendMessage("§c找不到该玩家");
            return;
        }

        // 从数据库查询玩家VIP信息
        VIPData.VIPInfo info = plugin.getDatabaseManager().getVIPInfo(offlinePlayer.getUniqueId());
        sendVIPInfo(sender, targetName + " 的VIP信息", info);
    }

    /**
     * 处理 /vipmgr upgrade 命令
     * 升级已有VIP玩家到更高的VIP组
     */
    private void handleUpgrade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /vipmgr upgrade <玩家> <新VIP组>");
            sender.sendMessage("§c示例: /vipmgr upgrade Steve vip2");
            return;
        }

        String targetName = args[1];
        String newVipGroup = args[2];

        // 检查新VIP组是否在白名单中
        if (!plugin.getConfigManager().isVIPGroup(newVipGroup)) {
            sender.sendMessage("§c无效的VIP组: " + newVipGroup);
            sender.sendMessage("§e可用的VIP组: " + String.join(", ", plugin.getConfigManager().getVIPGroups()));
            return;
        }

        // 获取目标玩家（必须在线）
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage("§c玩家不在线");
            return;
        }

        UUID targetUUID = targetPlayer.getUniqueId();

        // 检查玩家是否有VIP
        VIPData.VIPInfo info = plugin.getDatabaseManager().getVIPInfo(targetUUID);
        if (info == null) {
            sender.sendMessage("§c该玩家没有VIP，无法升级。请使用 /vipmgr give 命令");
            return;
        }

        String oldVipGroup = info.getVipGroup();
        String originalGroup = info.getOriginalGroup();

        // 检查新组和旧组是否相同
        if (oldVipGroup.equals(newVipGroup)) {
            sender.sendMessage("§c该玩家已经是 " + plugin.getConfigManager().getGroupAlias(newVipGroup) + " 了");
            return;
        }

        // 更新数据库，保留原组信息和到期时间
        if (!plugin.getDatabaseManager().saveVIP(targetUUID, newVipGroup, originalGroup, info.getExpireTime())) {
            sender.sendMessage("§c更新VIP数据失败！");
            return;
        }

        // 切换权限组
        if (!permissionManager.setPlayerGroup(targetPlayer, newVipGroup)) {
            sender.sendMessage("§c切换权限组失败！请检查Vault和权限插件配置");
            // 回滚数据库
            plugin.getDatabaseManager().saveVIP(targetUUID, oldVipGroup, originalGroup, info.getExpireTime());
            return;
        }

        // 执行新VIP组的开通命令
        com.vipmanagerlite.CommandExecutor.executeCommands(
            plugin.getConfigManager().getGrantCommands(newVipGroup),
            targetPlayer,
            originalGroup,
            newVipGroup
        );

        // 执行旧VIP组的到期命令（降级通知）
        // 命令模式下 on-expire 通常包含权限组切换命令，执行会把刚升级的新组覆盖回原组，故跳过
        if ("vault".equals(permissionManager.getMode())) {
            com.vipmanagerlite.CommandExecutor.executeCommands(
                plugin.getConfigManager().getExpireCommands(oldVipGroup),
                targetPlayer,
                originalGroup,
                oldVipGroup
            );
        }

        String oldAlias = plugin.getConfigManager().getGroupAlias(oldVipGroup);
        String newAlias = plugin.getConfigManager().getGroupAlias(newVipGroup);
        String remainingTime = TimeParser.formatTime(info.getRemainingTime());

        sender.sendMessage("§a已将 " + targetName + " 从 " + oldAlias + " 升级到 " + newAlias);
        sender.sendMessage("§e保留剩余时间: " + remainingTime);
        targetPlayer.sendMessage("§a你的VIP已从 " + oldAlias + " 升级到 " + newAlias);
        targetPlayer.sendMessage("§e保留剩余时间: " + remainingTime);
    }

    /**
     * 处理 /vipmgr reload 命令
     * 重载插件配置文件和权限管理器
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("vipmanagerlite.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        plugin.reload();
        sender.sendMessage("§a插件已重载！");
        sender.sendMessage("§a当前工作模式: " + plugin.getPermissionManager().getMode());
        sender.sendMessage("§a可用VIP组: " + String.join(", ", plugin.getConfigManager().getVIPGroups()));
    }
}

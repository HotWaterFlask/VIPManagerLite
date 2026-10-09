package com.vipmanagerlite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * 命令执行器类
 * 用于执行配置中定义的命令，并替换变量
 */
public class CommandExecutor {

    /**
     * 执行命令列表
     * @param commands 命令列表
     * @param player 目标玩家（可离线）
     * @param originalGroup 原权限组
     * @param vipGroup VIP组
     */
    public static void executeCommands(List<String> commands, OfflinePlayer player, String originalGroup, String vipGroup) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (String command : commands) {
            // 替换变量
            command = command.replace("%player%", player.getName());
            command = command.replace("%originalgroup%", originalGroup);
            command = command.replace("%vipgroup%", vipGroup);
            
            // 执行命令
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}

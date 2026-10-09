# VIPManagerLite

轻量版**限时 VIP 权限组管理**插件，适用于 Paper 服务端。玩家开通 VIP 后自动切换权限组，到期后自动恢复原权限组并按配置执行自定义命令。

支持 `vault` 与 `command` 两种运行模式，数据库可选 SQLite / MySQL。

## 特性

- 限时 VIP：给出指定时长，到期自动处理
- 到期精准调度：在线立即处理；离线可选「立即处理」或「等上线处理」
- 到期自动恢复原权限组，并执行该组的 `on-expire` 命令
- 支持升级已有 VIP 到更高等级，保留剩余时长
- 数据库支持 SQLite / MySQL，可配置表前缀，连接失效自动重连
- 兼容 Vault 权限插件；针对 GroupManager 做了离线切组适配
- `/vipmgr list` 会显示「已过期 待清理」的记录，便于排查

## 环境要求

- Java 25
- Paper（API 26.1.2）
- Vault 及权限插件（`vault` 模式必需；`command` 模式可选）

## 安装

1. 将 `VIPManagerLite-<version>.jar` 放入服务器 `plugins/` 目录
2. 重启服务器。SQLite / MySQL 驱动由 Paper 依据 `plugin.yml` 的 `libraries` 自动下载
3. 按需修改 `plugins/VIPManagerLite/config.yml`

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/vipmgr info` | 查看自己的 VIP 信息 | 所有玩家 |
| `/vipmgr give <玩家> <VIP组> <时间> [原权限组]` | 给予玩家限时 VIP | `vipmanagerlite.admin` |
| `/vipmgr remove <玩家>` | 移除玩家 VIP（支持离线） | `vipmanagerlite.admin` |
| `/vipmgr list` | 列出所有 VIP 玩家 | `vipmanagerlite.admin` |
| `/vipmgr look <玩家>` | 查看指定玩家 VIP 信息 | `vipmanagerlite.admin` |
| `/vipmgr upgrade <玩家> <新VIP组>` | 升级已有 VIP 玩家的权限组 | `vipmanagerlite.admin` |
| `/vipmgr reload` | 重载配置并重新调度任务 | `vipmanagerlite.admin` |

> `command` 模式下无法自动读取玩家当前权限组，使用 `give` 时需在命令末尾显式指定 `原权限组`。

### 时间格式

| 单位 | 含义 | 示例 |
| --- | --- | --- |
| `s` | 秒 | `30s` |
| `m` | 分钟 | `30m` |
| `h` | 小时 | `2h` |
| `d` | 天 | `7d` |
| `w` | 周 | `2w` |
| `M` | 月（按 30 天） | `1M` |
| `y` | 年（按 365 天） | `1y` |

## 配置说明

```yaml
# 运行模式: vault 或 command
# vault:   使用 Vault API 切换权限组（需安装 Vault 与权限插件）
# command: 通过 on-grant / on-expire 中配置的命令切换权限组
mode: vault

# 离线玩家处理方式（适用于 VIP 到期和手动 remove）
# immediate: 直接处理，离线玩家也立刻切换权限组并删除记录
# on-join:   先标记待清理，等玩家上线后再恢复权限组并删除记录（默认）
offline-mode: on-join

database:
  type: sqlite          # sqlite 或 mysql
  table-prefix: vipmgr_ # 表前缀
  mysql:                # 仅 type 为 mysql 时生效
    host: localhost
    port: 3306
    database: vipmanager
    username: root
    password: ''

# 只有在此定义的组才能被插件发放
vip-groups:
  vip1:
    alias: "初级VIP"
    description: "享受基础VIP特权"
    # 到期后回到的权限组（仅 vault 模式生效）
    # %originalgroup% 表示开通 VIP 前的原始组
    expire-group: "%originalgroup%"
    commands:
      on-grant:
        - "say %player% 从 %originalgroup% 升级到了 %vipgroup%！"
      on-expire:
        - "say %player% 从 %vipgroup% 到期了降回了 %originalgroup%！"
```

### 命令变量

`on-grant` / `on-expire` 中支持以下占位符，命令以控制台身份执行：

| 变量 | 含义 |
| --- | --- |
| `%player%` | 玩家名 |
| `%originalgroup%` | 开通 VIP 前的原始权限组 |
| `%vipgroup%` | 当前 VIP 权限组 |

## 构建

```bash
mvn clean package
```

产物位于 `target/VIPManagerLite-<version>.jar`。

## 许可

详见仓库根目录的 [LICENSE](LICENSE)。

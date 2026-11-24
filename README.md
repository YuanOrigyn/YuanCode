<h1 align="center">
  YuanCode | Yuan兑换码<br>
  <sub>高级兑换码系统 · Paper 插件版</sub>
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.x-3fb950" alt="Minecraft 26.1.x">
  <img src="https://img.shields.io/badge/Paper-26.1%2B-5865F2" alt="Paper 26.1+">
  <img src="https://img.shields.io/badge/Java-25-orange" alt="Java 25">
  <img src="https://img.shields.io/badge/Storage-SQLite%20%7C%20MySQL-2496ED" alt="SQLite / MySQL">
  <img src="https://img.shields.io/badge/PlaceholderAPI-2.11%2B-FF6B6B" alt="PlaceholderAPI">
  <img src="https://img.shields.io/badge/Vault-1.7-6A5ACD" alt="Vault">
</p>

<p align="center"><i>六种兑换码、四类奖励、条件分支、延迟发放、自动清理、异步 SQL 与图形化管理。</i></p>

<p align="center">作者：<b>Yuan</b> · 适用：<b>Paper 26.1+ / Java 25</b></p>

---

## 📖 简介

**YuanCode** 是一个面向 **Paper 26.1+** 的高级兑换码插件。  
它把兑换码定义放在易读的 `codes.yml`，把领取次数、领取流水和待发奖励放在数据库里，所有 SQL 操作都在独立线程池执行，不阻塞主线程。

插件提供六种兑换码类型：一次性、全局限次、每玩家限次、UUID 专属、周期循环、时效码。奖励支持物品、Vault 金币、权限和控制台指令，并可为奖励配置条件、延迟和条件分支。

- 兑换码定义：**`codes.yml`**
- 领取次数 / 流水 / 待发：**SQLite 或 MySQL**
- 消息与 GUI 文本：**`lang.yml`，全 MiniMessage**
- 管理方式：**`/yc` 指令 + 分页 GUI**
- 可选兼容：**Vault、PlaceholderAPI、LuckPerms**

> 示例 `codes.yml` 中所有兑换码默认 `enabled: false`，避免开服即被白嫖。配置奖励并确认限制后再启用。

## ✨ 功能

| 机制 | 说明 |
| --- | --- |
| 🎟️ **六种兑换码** | `ONE_TIME`、`GLOBAL_LIMIT`、`PLAYER_LIMIT`、`UUID_BOUND`、`PERIODIC`、`EXPIRING`，全部支持 `start` / `end` 时间窗口 |
| 🎁 **四类奖励** | 物品、Vault 金币、权限节点、控制台指令 |
| 🌿 **条件与分支** | 每个奖励可配条件；`branches` 按顺序匹配，第一个满足的分支生效，顶层 `rewards` 作为兜底 |
| ⏳ **延迟发放** | `delay: 5m`，奖励先写入 `yuancode_pending`，重启/离线不丢失，上线后继续发放 |
| 🛡️ **防刷风控** | UUID 白/黑名单、IP 次数/账号数限制、领取冷却、并发锁、失败次数限流 |
| 🗄️ **异步数据库** | 默认 SQLite，可切 MySQL；主线程不执行 JDBC |
| 🧹 **自动清理** | 已兑换完或已过期的兑换码自动从 `codes.yml` 删除，避免文件无限变长 |
| 🖥️ **指令 + GUI** | 创建、编辑、启停、奖励管理、克隆、删除确认、批量生成 |
| 📤 **CSV 导出** | UTF-8 BOM，Excel 打开中文不乱码 |
| 🧩 **软依赖兼容** | Vault、PlaceholderAPI、LuckPerms |
| 🧾 **独立语言文件** | `lang.yml` 全 MiniMessage，支持热重载 |
| ⚡ **并发安全** | 每玩家 Semaphore + SQL 事务行锁/写锁，多线程/多服防重复领取 |

## 🧩 运行依赖

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| **Paper** | **26.1+** | 必需；`api-version: 26.1`，不支持 Folia |
| **Java** | **25** | 服务端运行时 |
| Vault + 经济插件 | 1.7+ | 可选；发放 `money` 奖励需要 |
| PlaceholderAPI | 2.11+ | 可选；启用 `papi:` 条件和 `%yuancode_...%` 变量 |
| LuckPerms | 5.x+ | 可选；权限奖励持久化推荐安装 |

- 没有 Vault：`money` 奖励会失败并写日志，不影响其他奖励。
- 没有 LuckPerms：权限奖励会降级为仅在线会话有效的 Bukkit attachment。
- 没有 PlaceholderAPI：`papi:` 条件会按不通过处理。

## 🚀 安装

1. 服务器安装 **Paper 26.1+**，使用 **Java 25**。
2. 将 `YuanCode-1.0.0.jar` 放入 `plugins/`。
3. 可选安装：`Vault` + 经济插件、`PlaceholderAPI`、`LuckPerms`。
4. 启动服务器，自动生成：
   ```text
   plugins/YuanCode/config.yml
   plugins/YuanCode/lang.yml
   plugins/YuanCode/codes.yml
   plugins/YuanCode/yuancode.db
   ```
5. 编辑 `codes.yml`，把示例码的 `enabled` 改为 `true`，或执行：
   ```text
   /yc create WELCOME-2026 ONE_TIME
   ```
6. 执行 `/yc reload`，然后玩家使用：
   ```text
   /yc redeem <code>
   ```

## ⚙️ 配置文件

`plugins/YuanCode/config.yml`

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `settings.time-zone` | `server` | `server` 使用 JVM 默认时区；也可填 `Asia/Shanghai` |
| `settings.date-format` | `yyyy-MM-dd HH:mm:ss` | 兑换码起止时间格式 |
| `settings.server-id` | `server-1` | 写入领取记录；多服请设置唯一值 |
| `settings.security.max-attempts` | `8` | 时间窗口内允许的失败次数 |
| `settings.security.attempt-window` | `10s` | 失败次数统计窗口 |
| `settings.security.block-duration` | `30s` | 触发限制后的封禁时长 |
| `settings.security.hash-ip` | `false` | 是否对 IP 做 SHA-256 后存储 |
| `settings.pending.check-interval` | `5s` | 延迟奖励轮询间隔 |
| `settings.pending.expire-after` | `30d` | 超期未发放奖励清理时间 |
| `settings.auto-delete-codes.enabled` | `false` | 是否自动删除已兑换完/已过期的码 |
| `settings.auto-delete-codes.delete-exhausted` | `true` | 全局限量领完后删除 |
| `settings.auto-delete-codes.delete-expired` | `true` | 超过 `end` 后删除 |
| `settings.auto-delete-codes.check-interval` | `5m` | 后台扫描间隔，最低约 10 秒 |
| `settings.generator.length` | `10` | 批量生成随机码默认长度 |
| `settings.generator.max-batch` | `5000` | 单次最多生成数量 |
| `settings.gui.page-size` | `28` | GUI 列表每页数量，最大 28 |
| `settings.export.include-ip` | `true` | CSV 是否包含 IP |
| `database.type` | `SQLITE` | `SQLITE` 或 `MYSQL` |
| `hooks.vault.enabled` | `true` | 是否启用 Vault 金币奖励 |
| `hooks.placeholderapi.enabled` | `true` | 是否注册 PlaceholderAPI 变量 |
| `hooks.permission.provider` | `auto` | `auto` / `command` / `attachment` |
| `rewards.item.drop-when-full` | `true` | 背包满时掉落剩余物品 |

修改 `database.*` 后必须重启服务器；`/yc reload` 不会重建连接池。

## 🎯 指令与权限

主指令 `/yuancode`，别名 `/yc`。控制台可执行全部管理指令；`/yc gui` 仅玩家可用。

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/yc redeem <code>` | `yuancode.use` | 兑换兑换码 |
| `/yc gui` | `yuancode.admin.gui` | 打开管理 GUI |
| `/yc list [page]` | `yuancode.admin.list` | 分页列出兑换码 |
| `/yc info <code>` | `yuancode.admin.info` | 查看兑换码详情 |
| `/yc create <code> <type>` | `yuancode.admin.create` | 创建兑换码（默认禁用） |
| `/yc delete <code> confirm` | `yuancode.admin.delete` | 删除兑换码 |
| `/yc enable <code>` | `yuancode.admin.edit` | 启用兑换码 |
| `/yc disable <code>` | `yuancode.admin.edit` | 禁用兑换码 |
| `/yc set <code> <field> <value>` | `yuancode.admin.edit` | 修改字段 |
| `/yc reward add <code> <spec>` | `yuancode.admin.reward` | 添加默认奖励 |
| `/yc reward list <code>` | `yuancode.admin.reward` | 列出默认奖励 |
| `/yc reward remove <code> <index>` | `yuancode.admin.reward` | 移除第 N 项奖励 |
| `/yc reward clear <code>` | `yuancode.admin.reward` | 清空默认奖励 |
| `/yc generate <template> <amount> [length] [prefix]` | `yuancode.admin.generate` | 批量生成随机码 |
| `/yc export [filename]` | `yuancode.admin.export` | 导出领取记录 CSV |
| `/yc reload` | `yuancode.admin.reload` | 热重载配置/语言/兑换码 |
| `/yc stats` | `yuancode.admin.stats` | 查看统计 |
| `/yc help` | — | 显示帮助 |

`yuancode.admin` 默认 OP，并作为所有管理子权限的父权限。

### 单码权限

在兑换码中设置：

```yaml
permission: "yuancode.code.vip2026"
```

玩家还需要该权限才能兑换。该节点是动态的，不写入 `plugin.yml`，可交给 LuckPerms 管理。

## 🎟️ 兑换码类型

| 类型 | 行为 |
| --- | --- |
| `ONE_TIME` | 全服只能领取 1 次；全局和每玩家次数强制为 1 |
| `GLOBAL_LIMIT` | 全服共可领取 `global-limit` 次；必须大于 0 |
| `PLAYER_LIMIT` | **全服不限量**，每个玩家最多领取 `per-player-limit` 次 |
| `UUID_BOUND` | 只有 `uuid.whitelist` 内的 UUID 可领取 |
| `PERIODIC` | 每名玩家在每个周期内可领取 `per-player-limit` 次 |
| `EXPIRING` | 只在 `start` 到 `end` 之间有效，`end` 必填 |

### 全服不限，每人一次

```yaml
codes:
  ONCE-PER-PLAYER:
    enabled: true
    type: PLAYER_LIMIT
    global-limit: 0
    per-player-limit: 1
    rewards:
      - type: money
        amount: 200
```

- `global-limit: 0`：不限制全服总领取次数。
- `per-player-limit: 1`：每个 UUID 只能领取一次。
- 不需要 `end`；如果活动有时效，仍可加 `start` / `end`。

### 周期循环

```yaml
codes:
  DAILY-BONUS:
    enabled: true
    type: PERIODIC
    period: "daily"
    global-limit: 0
    per-player-limit: 1
```

`period` 支持 `daily` / `weekly` / `monthly`，也支持 `1d` / `7d` / `30d` 等固定时长。

## 🎁 奖励

```yaml
rewards:
  - type: item
    material: DIAMOND
    amount: 3
    name: "<aqua>新手钻石</aqua>"
    lore:
      - "<gray>来自 YuanCode。</gray>"

  - type: money
    amount: 500

  - type: permission
    node: "yuancode.example.vip"
    duration: "30d"       # permanent 或省略 = 永久

  - type: command
    command: "give %player% cooked_beef 8"
    delay: "1m"
```

| 类型 | 必填字段 | 说明 |
| --- | --- | --- |
| `item` | `material` | 支持 `amount`、MiniMessage `name` / `lore`，自动分堆 |
| `money` | `amount` | 通过 Vault 发放 |
| `permission` | `node` | LuckPerms 命令或在线 attachment |
| `command` | `command` | 控制台执行，支持 `%player%`、`%uuid%`、`%code%`、`%world%`、`%x%`、`%y%`、`%z%` 和 PlaceholderAPI |

### 条件

条件之间是 **AND** 关系，单个条件前加 `!` 取反：

```yaml
conditions:
  - "permission:group.vip"
  - "!permission:group.banned"
  - "world:world_nether"
  - "gamemode:survival"
  - "papi:%player_level% >= 10"
  - "papi:%player_name% contains a"
```

`papi:` 支持 `==`、`!=`、`>`、`>=`、`<`、`<=`、`contains`、`!contains`、`startsWith`、`endsWith`、`matches`。

### 条件分支

```yaml
rewards:
  - type: money
    amount: 500

branches:
  - id: "vip"
    conditions:
      - "permission:group.vip"
    rewards:
      - type: money
        amount: 1500
```

- `branches` 是可选字段，每个兑换码独立。
- 从上到下匹配，第一个满足的分支生效。
- 全部不满足时使用顶层 `rewards`。
- 不需要 VIP 奖励时，直接删掉该兑换码的 `branches` 整段即可。

## 🧹 自动清理兑换码

默认关闭，避免升级后误删历史配置。开启后：

```yaml
settings:
  auto-delete-codes:
    enabled: true
    delete-exhausted: true
    delete-expired: true
    check-interval: "5m"
```

- `delete-exhausted`：`GLOBAL_LIMIT` / `ONE_TIME` 等有全局限量的码领完后，立即从内存和 `codes.yml` 删除。
- `delete-expired`：过了 `end` 时间后，由后台任务扫描删除。
- 每次成功兑换还会立即检查当前码，不必等后台任务。
- 删除定义时保留 `yuancode_usage` 和领取流水；历史领取次数仍然有效，建议不要复用已删除的 code ID。
- 历史领取流水仍保留在 `yuancode_redemptions`，CSV 导出不受影响。
- `PLAYER_LIMIT` 全局限量为 0，不会因为“领完”删除；如果设置了 `end`，过期后仍会清理。

## 🛡️ 风控配置

```yaml
settings:
  security:
    max-attempts: 8
    attempt-window: "10s"
    block-duration: "30s"
    hash-ip: false
    ip-salt: "change-this-salt"
```

单码 IP 限制：

```yaml
ip:
  max-claims: 3
  max-accounts: 2
  window: "7d"
```

- `max-claims`：同一 IP 在该码上的领取次数。
- `max-accounts`：同一 IP 在该码上关联的不同 UUID 数。
- `hash-ip: true` 后数据库存 SHA-256，IP 限制仍有效，CSV 中显示哈希。

## 🗄️ 数据库

### SQLite（默认）

```yaml
database:
  type: SQLITE
  sqlite:
    file: "yuancode.db"
```

### MySQL

```yaml
database:
  type: MYSQL
  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "yuancode"
    username: "root"
    password: "change-me"
    use-ssl: false
    pool-size: 6
    connection-timeout-ms: 10000
    properties:
      serverTimezone: "UTC"
```

表结构：

| 表 | 作用 |
| --- | --- |
| `yuancode_meta` | schema 版本 |
| `yuancode_usage` | 每个码的全服已领取数 |
| `yuancode_redemptions` | 领取流水（UUID、名称、IP、周期、时间、服务器 ID） |
| `yuancode_pending` | 延迟/离线待发奖励 |

完整 DDL：

- [SQLite](docs/schema-sqlite.sql)
- [MySQL](docs/schema-mysql.sql)

## 🧵 异步与并发设计

1. 主线程完成时间、权限、UUID、条件分支判断。
2. 取得每玩家 `Semaphore`，立即失败返回 `BUSY`，防止连点。
3. 数据库线程池执行单个事务：
   - 锁全服用量行 / SQLite 写锁；
   - 检查全服次数；
   - 检查每玩家当前周期次数；
   - 检查冷却；
   - 检查 IP 次数 / 关联账号；
   - 插入领取流水；
   - 全服计数 +1；
   - 提交。
4. 回到主线程发奖励；延迟或离线时先写入 `yuancode_pending`。
5. `codes.yml` 使用独立单线程写队列 + 临时文件原子替换。

因此：主线程不做 JDBC；同一玩家不会并发重复领取；多服共用 MySQL 时全局限次也成立。

## 🧩 PlaceholderAPI

| 变量 | 说明 |
| --- | --- |
| `%yuancode_total%` | 玩家历史总领取次数 |
| `%yuancode_global_used_CODE%` | 该码全服已领取 |
| `%yuancode_remaining_CODE%` | 该码剩余，`-1` 表示无限 |
| `%yuancode_player_claims_CODE%` | 玩家在该码上的历史领取次数 |
| `%yuancode_cooldown_CODE%` | 冷却剩余秒数 |
| `%yuancode_last_redeem_CODE%` | 最后领取时间 |
| `%yuancode_status_CODE%` | `ACTIVE` / `DISABLED` / `NOT_STARTED` / `EXPIRED` |
| `%yuancode_type_CODE%` | 类型 |
| `%yuancode_enabled_CODE%` | `true` / `false` |

示例：`%yuancode_remaining_WELCOME-2026%`。  
数据来自内存缓存，占位符请求不会阻塞主线程查询 SQL。

## 🔐 权限奖励说明

`permission` 奖励默认 `provider: auto`：

- 检测到 **LuckPerms**：执行配置模板：
  - 临时：`lp user %player% permission settemp %permission% true %duration%`
  - 永久：`lp user %player% permission set %permission% true`
- 未检测到 LuckPerms：使用 `Player#addAttachment`，**仅当前在线会话有效**。

生产环境建议安装 LuckPerms，或将 `hooks.permission.provider` 改为 `command` 并配置为你的权限插件命令。

## 🗂️ 目录结构

```text
src/main/java/com/yuan/yuancode/
├── YuanCodePlugin.java          # 启动、依赖装配、任务、热重载
├── command/                     # /yc 指令与 Tab 补全
├── config/                      # config.yml、lang.yml、不可变配置快照
├── code/                        # 类型、定义、解析、序列化、编辑器
├── storage/                     # CodeStorage 接口 + SQLite/MySQL 实现
├── service/                     # 兑换、奖励、待发、条件、缓存、限流、自动清理
├── hook/                        # Vault / PlaceholderAPI / LuckPerms
├── gui/                         # 管理 GUI、聊天输入
├── listener/                    # 玩家加入/退出
└── util/                        # 时间、文本、文件、JSON 编解码
```

关键文件：

- [plugin.yml](src/main/resources/plugin.yml)
- [codes.yml 样例](src/main/resources/codes.yml)
- [config.yml](src/main/resources/config.yml)
- [lang.yml](src/main/resources/lang.yml)
- [SQLite 建表](docs/schema-sqlite.sql)
- [MySQL 建表](docs/schema-mysql.sql)
- [指令权限表](docs/COMMANDS.md)
- [codes.yml 完整参考](docs/CODES.md)
- [核心兑换服务](src/main/java/com/yuan/yuancode/service/RedemptionService.java)
- [SQL 存储实现](src/main/java/com/yuan/yuancode/storage/SqlCodeStorage.java)

## 🔨 构建

需要 **JDK 25**。工程自带 Gradle Wrapper，可直接构建。

Windows：

```powershell
.\gradlew.bat clean build
```

Linux / macOS：

```bash
./gradlew clean build
```

产物：

```text
build/libs/YuanCode-1.0.0.jar
build/libs/YuanCode-1.0.0-sources.jar
```

插件已内嵌 SQLite JDBC、MySQL Connector/J、HikariCP，不需要额外安装驱动。Vault / PlaceholderAPI 使用 `compileOnly`，不会打进 jar。

> jar 约 **18MB**，主要来自 SQLite 多平台 native 库（约 13.4MB）和 MySQL Connector/J + protobuf。  
> 这样可完全离线部署；如果服务器有网络并希望瘦身，可改用 Paper 的 `libraries` 在启动时下载依赖。

### 已验证环境

- Paper **26.1.2 build 74**
- Java **25.0.3**
- Gradle **9.7.1**
- `gradlew clean build`：**9 个测试全部通过**
- SQLite 模式启动、建表、管理指令、批量生成、CSV 导出、热重载已实际运行验证
- MySQL 代码路径已实现，但本次环境未连接真实 MySQL；上线前请先在测试库执行 DDL 并验证连接

## ⚠️ 已知边界

- GUI 只编辑顶层 `rewards`；`branches` 请在 `codes.yml` 中配置。
- GUI/指令保存会重写 `codes.yml`，自定义注释可能丢失。
- 自动清理会重写 `codes.yml` 并删除已完成的兑换码定义；默认关闭，开启前请确认不需要保留这些配置。
- `PLAYER_LIMIT` 的 `global-limit` 固定为 0，不能设置全服上限；如需全服上限请用 `GLOBAL_LIMIT`。
- MySQL 表名固定为 `yuancode_*`，暂不支持前缀。
- 延迟奖励为“至少一次”投递：极端崩溃窗口可能重复发放，请让奖励命令尽量幂等。
- 修改 `database.*` 后必须重启；`/yc reload` 不会重建连接池。
- 本次验证未接入真实玩家客户端做端到端兑换；限次、冷却、IP 与事务由 SQLite 集成测试覆盖。
- 物品奖励使用 Bukkit `Material`，不支持 NBT/自定义模型数据；复杂物品建议用 `command` 奖励交给其他插件。

## 📚 更多文档

- [codes.yml 字段与示例](docs/CODES.md)
- [指令与权限表](docs/COMMANDS.md)
- [SQLite 建表语句](docs/schema-sqlite.sql)
- [MySQL 建表语句](docs/schema-mysql.sql)

## 📜 授权与第三方

本工程为按需求定制的 Paper 插件源码，可自行修改与部署。  
分发前请确认第三方依赖的许可证要求：

- Paper API
- VaultAPI
- PlaceholderAPI
- SQLite JDBC
- MySQL Connector/J
- HikariCP

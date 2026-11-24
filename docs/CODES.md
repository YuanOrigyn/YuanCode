# codes.yml 参考

`codes.yml` 是兑换码定义的唯一来源。数据库只保存领取次数、领取记录和待发奖励，因此备份时请同时备份 `codes.yml` 与数据库。

> GUI 或指令修改兑换码后会重写 `codes.yml`，自定义注释可能丢失。复杂条件分支建议直接编辑 YAML。

## 顶层结构

```yaml
codes:
  CODE_ID:
    enabled: true
    type: ONE_TIME
    # ...
```

兑换码 ID 会统一转为大写，只允许 `A-Z`、`0-9`、`_`、`-`，最长 64 字符。

## 通用字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `enabled` | boolean | 是否启用，默认 `true` |
| `type` | enum | `ONE_TIME` / `GLOBAL_LIMIT` / `PLAYER_LIMIT` / `UUID_BOUND` / `PERIODIC` / `EXPIRING` |
| `display-name` | MiniMessage | GUI 显示名，可省略 |
| `start` | 时间 | 开始时间，可省略；格式 `yyyy-MM-dd HH:mm:ss` 或 ISO-8601 |
| `end` | 时间 | 结束时间，可省略；`EXPIRING` 必填 |
| `global-limit` | int | 全服总领取次数，`0` 表示无限；`ONE_TIME` 固定为 1，`PLAYER_LIMIT` 固定为 0 |
| `per-player-limit` | int | 每个玩家可领取次数，`0` 表示无限（`PLAYER_LIMIT` 必须大于 0）；周期码按“当前周期内”计算 |
| `cooldown` | 时长 | 同一玩家两次领取之间的冷却，`0s` 表示无 |
| `period` | 周期 | 仅 `PERIODIC`；`daily` / `weekly` / `monthly` 或 `1d` / `7d` / `30d` |
| `permission` | string | 领取所需权限节点，可留空 |
| `uuid.whitelist` | UUID 列表 | 白名单；非空时只允许列表内 UUID |
| `uuid.blacklist` | UUID 列表 | 黑名单；优先级高于白名单 |
| `ip.max-claims` | int | 同一 IP 在窗口内最多领取次数，`0` 关闭 |
| `ip.max-accounts` | int | 同一 IP 在窗口内最多关联账号数，`0` 关闭 |
| `ip.window` | 时长 | IP 统计窗口，默认 `7d` |
| `rewards` | 列表 | 默认奖励；没有分支命中时作为兜底 |
| `branches` | 列表 | 有序条件分支，第一个所有条件都满足的分支生效 |

时间使用 `settings.time-zone` 指定的时区；`server` 表示 JVM 默认时区。

## 六种类型

| 类型 | 行为 |
|---|---|
| `ONE_TIME` | 全服只能领取 1 次；`global-limit` 与 `per-player-limit` 强制为 1 |
| `GLOBAL_LIMIT` | 全服共可领取 `global-limit` 次；必须设置大于 0 的 `global-limit` |
| `PLAYER_LIMIT` | 全服不限量；每个玩家最多领取 `per-player-limit` 次，`global-limit` 强制为 0 |
| `UUID_BOUND` | 只有 `uuid.whitelist` 内的 UUID 可领取；白名单为空时无人可领 |
| `PERIODIC` | 每名玩家在每个周期内可领取 `per-player-limit` 次；必须设置 `period` |
| `EXPIRING` | 只在 `start` 到 `end` 之间有效；`end` 必填，次数由限制字段决定 |

> 如果开启 `settings.auto-delete-codes.enabled`，已兑换完或已过期的兑换码会自动从 `codes.yml` 删除，避免文件无限变长。

### 全服不限、每人一次

这是 `PLAYER_LIMIT` 的典型用法：

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
- `per-player-limit: 1`：每个 UUID 最多领取一次。
- 不需要设置 `end`；如需活动时间窗口，仍可加 `start` / `end`。

## 奖励

```yaml
rewards:
  - type: item
    material: DIAMOND
    amount: 3
    name: "<aqua>新手钻石</aqua>"
    lore:
      - "<gray>来自 YuanCode。</gray>"
    delay: "0s"
    conditions: []

  - type: money
    amount: 500
    delay: "0s"

  - type: permission
    node: "yuancode.example.vip"
    duration: "30d"       # permanent 或省略 = 永久
    delay: "5s"

  - type: command
    command: "give %player% cooked_beef 8"
    delay: "1m"
```

### 奖励类型

| `type` | 必填字段 | 说明 |
|---|---|---|
| `item` | `material` | `amount` 默认 1；`name` / `lore` 支持 MiniMessage |
| `money` | `amount` | 通过 Vault 经济发放；未安装 Vault 时失败并写日志 |
| `permission` | `node` | LuckPerms 存在时走命令模板；否则使用仅在线有效的 Bukkit attachment |
| `command` | `command` | 以控制台身份执行；支持 `%player%`、`%uuid%`、`%code%`、`%world%`、`%x%`、`%y%`、`%z%` 和 PlaceholderAPI |

### 延迟发放

`delay` 支持 `500ms`、`30s`、`5m`、`2h`、`7d`。带延迟的奖励会先写入 `yuancode_pending` 表，服务器重启不会丢失；玩家离线时到期也会保留，下次进入服务器再发放。

延迟奖励的 `conditions` 在领取时立即计算，不会在延迟到期时重新计算。

## 条件

条件之间是 **AND** 关系；单个条件前加 `!` 取反。

```yaml
conditions:
  - "permission:group.vip"
  - "!permission:group.banned"
  - "world:world_nether"
  - "gamemode:survival"
  - "papi:%player_level% >= 10"
  - "papi:%player_name% contains a"
```

支持的 `papi:` 运算符：

`==`、`!=`、`>`、`>=`、`<`、`<=`、`contains`、`!contains`、`startsWith`、`endsWith`、`matches`（正则）。

两边都能解析为数字时按数字比较，否则按字符串比较。使用 `papi:` 时必须安装 PlaceholderAPI。

## 条件分支

`branches` 从上到下匹配，第一个所有条件都满足的分支生效；如果没有任何分支命中，则使用顶层 `rewards`。分支内部的 `rewards` 也支持每个奖励自己的 `conditions`。

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
      - type: item
        material: EMERALD
        amount: 8
```

上例中 VIP 玩家拿 1500，其他玩家走顶层奖励拿 500。

## 时间与周期

- `start` / `end`：`2026-12-31 23:59:59`、`2026-12-31T23:59:59+08:00`、`2026-12-31`。
- `cooldown` / `delay` / `ip.window`：`30s`、`5m`、`2h`、`7d`、`1mo`（30 天），可组合为 `1d12h`。
- `period`：`daily`、`weekly`、`monthly`，或固定时长。
  - 1 天按自然日切分。
  - 7 天按 ISO 周切分。
  - 30 天按自然月切分。
  - 其他时长按 epoch 固定宽度切分。

## 完整示例

```yaml
codes:
  WEEKLY-VIP:
    enabled: true
    type: PERIODIC
    display-name: "<gradient:#FFD700:#FF8C00>每周 VIP 礼包</gradient>"
    period: "weekly"
    global-limit: 0
    per-player-limit: 1
    cooldown: "0s"
    permission: "yuancode.weekly"
    uuid:
      whitelist: []
      blacklist: []
    ip:
      max-claims: 3
      max-accounts: 2
      window: "7d"
    rewards:
      - type: money
        amount: 500
      - type: item
        material: DIAMOND
        amount: 2
    branches:
      - id: "vip-plus"
        conditions:
          - "permission:group.vip+"
          - "papi:%player_level% >= 50"
        rewards:
          - type: money
            amount: 1500
          - type: permission
            node: "yuancode.vip.plus"
            duration: "7d"
```

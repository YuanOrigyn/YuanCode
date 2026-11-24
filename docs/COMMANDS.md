# 指令与权限

主指令：`/yuancode`，别名 `/yc`。控制台可执行所有管理指令；`/yc gui` 仅玩家可用。

## 指令表

| 指令 | 权限 | 说明 |
|---|---|---|
| `/yc redeem <code>` | `yuancode.use` | 兑换兑换码 |
| `/yc gui` | `yuancode.admin.gui` | 打开兑换码管理 GUI |
| `/yc list [page]` | `yuancode.admin.list` | 分页列出兑换码 |
| `/yc info <code>` | `yuancode.admin.info` | 查看兑换码详情 |
| `/yc create <code> <type>` | `yuancode.admin.create` | 创建兑换码（默认禁用） |
| `/yc delete <code> confirm` | `yuancode.admin.delete` | 删除兑换码，必须带 `confirm` |
| `/yc enable <code>` | `yuancode.admin.edit` | 启用兑换码 |
| `/yc disable <code>` | `yuancode.admin.edit` | 禁用兑换码 |
| `/yc set <code> <field> <value>` | `yuancode.admin.edit` | 修改字段 |
| `/yc reward add <code> <spec>` | `yuancode.admin.reward` | 添加默认奖励 |
| `/yc reward list <code>` | `yuancode.admin.reward` | 列出默认奖励 |
| `/yc reward remove <code> <index>` | `yuancode.admin.reward` | 删除第 N 项默认奖励（从 1 开始） |
| `/yc reward clear <code>` | `yuancode.admin.reward` | 清空默认奖励 |
| `/yc generate <template> <amount> [length] [prefix]` | `yuancode.admin.generate` | 批量生成随机码并写入文本文件 |
| `/yc export [filename]` | `yuancode.admin.export` | 异步导出领取记录 CSV |
| `/yc reload` | `yuancode.admin.reload` | 热重载 config.yml / lang.yml / codes.yml |
| `/yc stats` | `yuancode.admin.stats` | 查看兑换码与数据库统计 |
| `/yc help` | 无 | 显示帮助 |

`yuancode.admin` 默认 `op`，并作为下列子权限的父权限。拥有 `yuancode.admin` 等同于拥有全部管理子权限。

## 权限表

| 权限 | 默认 | 说明 |
|---|---|---|
| `yuancode.use` | `true` | 允许使用 `/yc redeem` |
| `yuancode.admin` | `op` | 全部管理权限 |
| `yuancode.admin.gui` | `false` | GUI 管理 |
| `yuancode.admin.list` | `false` | 列表 |
| `yuancode.admin.info` | `false` | 详情 |
| `yuancode.admin.create` | `false` | 创建 |
| `yuancode.admin.delete` | `false` | 删除 |
| `yuancode.admin.edit` | `false` | 启用/禁用/修改字段 |
| `yuancode.admin.reward` | `false` | 奖励管理 |
| `yuancode.admin.generate` | `false` | 批量生成 |
| `yuancode.admin.export` | `false` | CSV 导出 |
| `yuancode.admin.reload` | `false` | 热重载 |
| `yuancode.admin.stats` | `false` | 统计 |

### 单码权限

在 `codes.yml` 中为兑换码设置：

```yaml
permission: "yuancode.code.vip2026"
```

则玩家还需要该权限才能兑换。该节点是动态的，不写入 `plugin.yml`，可由 LuckPerms 等权限插件授予。

## `/yc set` 字段

| 字段 | 示例 |
|---|---|
| `type` | `ONE_TIME` / `GLOBAL_LIMIT` / `PLAYER_LIMIT` / `UUID_BOUND` / `PERIODIC` / `EXPIRING` |
| `global-limit` | `100`；`0` = 无限 |
| `per-player-limit` | `1`；`0` = 无限 |
| `cooldown` | `30s`、`5m`、`1h`、`0` |
| `period` | `daily`、`weekly`、`monthly`、`1d`、`7d`、`30d` |
| `start` / `end` | `2026-12-31 23:59:59`，`-` 表示清除 |
| `permission` | `yuancode.code.vip`，`-` 表示清除 |
| `display-name` | 支持 MiniMessage，`-` 表示清除 |
| `uuid-whitelist` / `uuid-blacklist` | 逗号或空格分隔，`-` 表示清空 |
| `ip` | `<max-claims> <max-accounts> <window>`，如 `3 2 7d`；`0 0 0` 关闭 |
| `enabled` | `true` / `false` |

## 奖励快捷格式

`/yc reward add <code> ...` 与 GUI 添加奖励共用以下格式：

```text
money 500
item DIAMOND 3 新手钻石
permission yuancode.vip 30d
command give %player% cooked_beef 8
```

可在末尾添加 `--delay=5m`：

```text
item DIAMOND 3 新手钻石 --delay=5m
```

复杂条件、分支和 MiniMessage 名称建议直接编辑 `codes.yml`。

## 批量生成

```text
/yc generate WELCOME-2026 100 12 WELCOME-
```

- 以 `WELCOME-2026` 为模板克隆。
- 生成 100 个 ID 形如 `WELCOME-XXXXXXXXXXXX` 的兑换码。
- 生成结果写入 `plugins/YuanCode/generated/codes-<时间>.txt`。
- 默认每个克隆体继承模板的所有字段与奖励。
- 请先把模板配置好并启用；生成的码是否启用继承模板的 `enabled`。

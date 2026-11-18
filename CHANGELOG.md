# 更新日志

本文件记录 Forestry IO 的所有重要变更。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)（0.x 阶段：新增功能进次版本号，修复与文档进修订号）。

## [未发布]

## [0.1.1] - 2026-11-18

### 新增

- 新增 CurseForge 商店页文案 `docs/CURSEFORGE.md`（简介、功能、安装、常见问题与许可证说明）。
- 构建产物 jar 中现在包含 `LICENSE` 全文（项目根目录，便于下载的构建自描述许可证）。

### 变更

- **许可证由 `All Rights Reserved` 改为 `LGPL-3.0-only`**，与上游 Forestry: Community Edition 一致；
  `gradle.properties` 的 `mod_license`、`META-INF/neoforge.mods.toml` 与 README 同步更新。
- README 增补「许可证」章节与合规要点（`libs/` 下的依赖 jar 不随发布分发、LGPL 重新链接要求等）。
- `.gitattributes` 将 `LICENSE` 固定为 LF，避免换行符差异弄脏许可证全文。

### 修复

- 林业过滤器：已加入的筛选条件现在**始终**显示在列表中（蓝色），不再因为以下情况消失，从而避免条件仍在
  生效却看不到、也无法删除：未放入样品、样品换成其它物种类型、样品在该染色体上的等位基因与条件不同。
  同染色体的条件排在该染色体行之后，其它物种类型的条件排在列表末尾并带物种名前缀。
- 林业过滤器：搜索无结果时提示「无匹配项」，不再因为样品槽为空而误报「请放入样品」。

## [0.1.0] - 2026-11-18

首个版本，目标环境 Minecraft 1.21.1 / NeoForge 21.1.240 / Java 21。

### 新增

- LaserIO × Forestry: Community Edition 联动扩展，Mod ID `forestryio`。
- `forestryio:forestry_filter`：按林业遗传物品的基因组筛选物品的 LaserIO 过滤器，支持蜜蜂、树木以及林业
  注册的其它遗传物种（每个物种类型按染色体 / 显性-隐性等位基因列出条件，全部匹配才通过，可切换白名单 /
  黑名单）。
- 多物种基因组支持：按样品所属物种类型动态列出染色体行；树苗等树木遗传物品同样可用。
- 列表搜索、分页（按钮 / 滚轮）、`+` 一键加入样品全部属性、垃圾桶清空全部条件。
- 条件以字符串形式序列化保存，重载后按当前 Forestry 版本重新解析，失效的染色体或等位基因静默丢弃。
- 离线 Gradle 构建（依赖 jar 放在 `libs/`，`compileOnly` + `runtimeOnly`，不 shade 进发布 jar）。

[未发布]: https://github.com/zhichaoxi2006/forestryio/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/zhichaoxi2006/forestryio/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/zhichaoxi2006/forestryio/commits/v0.1.0

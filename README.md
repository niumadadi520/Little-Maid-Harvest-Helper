# Touhou Little Maid Berry Harvest Compat

一个面向 Minecraft 1.20.1 Forge 的东方小女仆兼容模组，让农场模式能够寻找并右键收获浆果、瓜果及其他模组添加的可重复采摘作物。

## 功能

- 支持原版甜浆果与发光浆果。
- 支持注册名包含 `berry`、`melon`、`fruit`、`apple`、`grape`、`banana`、`currant`、`bush`、`vine` 等关键词的模组作物。
- 识别 `age`、`stage`、`berries`、`ripe`、`mature`、`fruiting` 成熟属性。
- 女仆处于农场模式时扫描附近成熟果实，主动寻路并执行右键收获。
- 忽略手持锄头触发的破坏模式，避免毁坏可重复采摘植株。
- 将地面掉落物和右键直接进入交互者背包的产物转移到女仆库存。

## 环境

- Minecraft 1.20.1
- Forge 47.4.22
- Java 17
- Touhou Little Maid 1.5.3

## 构建

项目使用 Gradle 和 ForgeGradle。东方小女仆编译依赖通过 CurseMaven 自动下载，无需提交第三方 Jar。

Windows：

```powershell
.\gradlew.bat clean jar
```

Linux 或 macOS：

```bash
./gradlew clean jar
```

构建产物位于 `build/libs/`。

## 使用

将构建出的 Jar 与东方小女仆及其依赖一起放入客户端和服务端的 `mods` 目录。请勿同时安装本项目的多个版本。

启动日志中出现以下内容时，表示兼容扩展已加载并完成作物注册：

```text
女仆浆果采摘兼容已加载
女仆浆果兼容注入完成，共注册 X 个方块
```

## 兼容说明

本项目通过作物注册名和方块状态属性进行通用识别。若某个模组使用完全不同的命名、成熟状态或交互机制，请在 Issue 中附上模组名称、版本、作物注册名和日志。

## 许可证

本项目使用 MIT License。东方小女仆及其他兼容模组的版权归各自作者所有。

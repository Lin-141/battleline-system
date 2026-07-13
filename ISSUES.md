# 常见问题 & 错误速查

## 1. 服务端崩溃：`Attempted to load class ... for invalid dist DEDICATED_SERVER`

**错误日志特征**：
```
java.lang.BootstrapMethodError: java.lang.RuntimeException: Attempted to load 
class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER
	at com.battlelinesystem.network.AllPackets.<clinit>(AllPackets.java:35)
```

**原因**：S→C 数据包的 `handle()` 方法中直接或间接引用了客户端类（`Minecraft`、`Screen`、`client.gui.*` 等），Forge 在服务端加载该类时触发拦截。

**修复**：
1. S→C 包的 `handle()` 改为 `context.enqueueWork(() -> NetworkManager.dispatchClient(N, this));`
2. 客户端渲染逻辑放在 `ClientPacketHandler.java`（`@OnlyIn(Dist.CLIENT)`）
3. S→C 包文件中移除 `import net.minecraftforge.fml.DistExecutor` 和客户端类引用
4. **必须重新 `gradlew build`**，`compileJava` 通过不代表 jar 已更新

---

## 2. `classSelectionsMap()` 跨包访问权限错误

**错误日志特征**：
```
错误: classSelectionsMap()在NetworkManager中不是公共的; 无法从外部程序包中对其进行访问
```

**原因**：`NetworkManager.classSelectionsMap()` 是包私有（无修饰符），`network.packet` 子包中的类无法访问。

**修复**：改为 `public static`。

---

## 3. `TargetPoint.p()` 不兼容 `SimpleChannel.send()`

**错误日志特征**：
```
错误: 不兼容的类型: Supplier<TargetPoint>无法转换为PacketTarget
```

**原因**：`AllPackets.sendToNear()` 中 `TargetPoint.p()` 返回 `Supplier`，`SimpleChannel.send()` 需要 `PacketTarget`。

**修复**：
```java
// 错误
channel.send(TargetPoint.p(x, y, z, range, world.dimension()), message);

// 正确
channel.send(PacketDistributor.NEAR.with(() -> 
    new TargetPoint(x, y, z, range, world.dimension())), message);
```

---

## 4. 编译无错但服务端仍崩溃

**原因**：只跑了 `gradlew compileJava`，未重新执行 `gradlew build`。服务器上运行的 jar 仍是旧版本。

**修复**：`gradlew build -x test`，然后用 `build/libs/` 下的新 jar 替换服务器 mods 目录中的旧文件。

---

## 5. 数据包改造后游戏流程不通

**排查顺序**：
1. `AllPackets.java` 中该包的 `NetworkDirection` 是否正确（PLAY_TO_SERVER / PLAY_TO_CLIENT）
2. `AllPackets.registerPackets()` 是否在 `BattleLineSystem.java` 中调用
3. 所有 `NetworkManager.CHANNEL` 引用是否都已改为 `AllPackets.getChannel()`
4. `ClientPacketHandler.java` 中的 switch-case ID 是否与 `NetworkManager.dispatchClient(id, msg)` 一致

---

## 6. 网络包 ID 对照表

| ID | S→C/C→S | 类名 | handle 所在 |
|----|---------|------|-------------|
| 0 | S→C | PacketOpenScreen | ClientPacketHandler |
| 1 | S→C | PacketFactionList | ClientPacketHandler |
| 2 | S→C | PacketMapListResponse | ClientPacketHandler |
| 3 | S→C | PacketOpenFactionVote | ClientPacketHandler |
| 4 | S→C | PacketOpenClassVote | ClientPacketHandler |
| 5 | S→C | PacketSyncCapturePoints | ClientPacketHandler |
| 6 | S→C | PacketCapturePointProgress | ClientPacketHandler |
| 7 | S→C | PacketSyncSpawnPoints | ClientPacketHandler |
| 8 | S→C | PacketGameOverResult | ClientPacketHandler |
| 9 | S→C | PacketTimeUp | ClientPacketHandler |
| 10 | S→C | PacketSyncBeaconEntities | ClientPacketHandler |
| 11 | S→C | PacketOpenCommanderVote | ClientPacketHandler |
| 12 | S→C | PacketLooseSpawnTest | ClientPacketHandler |
| -- | C→S | PacketSelectMode | 自身的 handle() |
| -- | C→S | PacketSelectMap | 自身的 handle() |
| -- | C→S | 其余 C→S 包 | 自身的 handle() |

**注意**：C→S 包的 `handle()` 在服务端执行，可自由引用服务端类（MinecraftServer、ServerPlayer 等）。
S→C 包的 `handle()` 两端都执行但客户端逻辑必须通过反射/ClientPacketHandler 分发。

---

## 7. 构建命令速查

```bash
# 仅编译验证（快，但不产 jar）
gradlew compileJava

# 完整构建
gradlew build -x test

# 输出位置
build/libs/battlelinesystem-1.0.0.jar
```

---

## 8. switchteam 跳边后结算仍显示原队伍

**现象**：`/bls switchteam` 跳边后，游戏结束时结算画面仍显示玩家在原队伍。

**原因**：`OpenMapCommand` 中 switchteam handler 只更新了 `CapturePointManager` 的玩家队伍映射，但没有同步更新 `PlayerGameStats.team`。

**修复**：跳边时同步更新 `CapturePointManager.getInstance().getPlayerStats(uuid).team = newTeam`。

**涉及文件**：
- `command/OpenMapCommand.java` — switchteam handler
- `game/CapturePointManager.java` — PlayerGameStats

---

## 9. 跳边玩家在部署界面看不到选择框

**现象**：对方玩家跳到自己队伍后，在部署界面看不到该玩家的 LooseSpawn 选择框。

**原因**：switchteam 后未向新队伍广播 `PacketOpenClassVote`，新队友的客户端没有跳边玩家的 UUID。

**修复**：跳边后向新队伍全队发送 `PacketOpenClassVote`（含 `sameTeamUUIDs`）。

**涉及文件**：
- `command/OpenMapCommand.java` — switchteam handler

---

## 10. 指挥官无人投票时不随机选指挥官

**现象**：指挥官投票倒计时结束后，如果无人投票，不会自动随机选出一名指挥官。

**原因**：`CommanderVoteManager` 超时逻辑未处理无人投票的情况。

**修复**：超时时从队伍中随机选一名玩家担任指挥官。

**涉及文件**：
- `game/CommanderVoteManager.java`

---

## 11. HUD 显示"A队"/"B队"而非阵营名

**现象**：选完阵营后，屏幕上方分数旁边显示的是"A队"/"B队"而不是实际阵营名称。

**原因**：`skipToFactionVote()` 中漏发了 `PacketOpenFactionVote`，导致客户端 `cacheFactionPools()` 从未被调用，阵营名称未缓存。

**修复**：`skipToFactionVote()` 中补充发送 `PacketOpenFactionVote`。

**涉及文件**：
- `BattleLineSystem.java` — `skipToFactionVote()`

---

## 12. 首次/死亡后重新部署看不到队友选择框

**现象**：首次选完阵营进入战场时看不到队友的 LooseSpawn 选择框；死亡后重新部署也看不到。

**原因**：
1. `WaitHudOverlay.setClassOptions()` 从客户端 `FactionManager` 读取 `looseSpawn`，可能返回 null → 改为从 `PacketOpenClassVote` 直接传递 `looseSpawn` 字段。
2. `GameEventHandler.onPlayerDeath()` 重建 `PacketOpenClassVote` 时漏填 `looseSpawn` 和 `sameTeamUUIDs`。

**修复**：
1. `PacketOpenClassVote` 新增 `looseSpawn` 字段，通过编码/解码直接传递。
2. `ClientPacketHandler.handleOpenClassVote()` 直接使用 `msg.looseSpawn` 设置 `LooseSpawnRenderer.setEnabled()`。
3. `GameEventHandler.onPlayerDeath()` 补上 `looseSpawn` 和 `sameTeamUUIDs`。

**涉及文件**：
- `network/packet/PacketOpenClassVote.java` — 新增 `looseSpawn` 字段
- `network/ClientPacketHandler.java` — `handleOpenClassVote()`
- `event/GameEventHandler.java` — `onPlayerDeath()`

---

## 13. GameEventHandler 在全维度处理死亡事件

**现象**：玩家在主世界死亡也会触发 BLS 的死亡处理逻辑。

**原因**：`GameEventHandler` 的死亡事件 handler 未检查维度 namespace，对所有维度的死亡都执行了 BLS 逻辑。

**修复**：添加 `player.level().dimension().location().getNamespace().equals(BattleLineSystem.MOD_ID)` 过滤。

**涉及文件**：
- `event/GameEventHandler.java`

---

## 14. 选中地图后一直提示"正在加载地图"

**现象**：选完地图后客户端一直显示"正在加载地图"，后续的队伍/阵营选择界面不会弹出。

**原因**：`GameWorldManager.createAndLoadWorld()` 中的 `forceLoadSpawnChunks` 调用了 `world.getChunk(..., FULL, true)`，在主线程同步等待区块加载完成，导致死锁。此外后续 `teleportTo` 触发的 `PlayerChangedDimensionEvent` 被 DistantHorizons 模组拦截抛 NPE，打断了后续网络包的发送。

**修复**：
1. 将 `forceLoadSpawnChunks` 重命名为 `initSpawnChunks`，移除 `world.getChunk(..., FULL, true)` 同步阻塞调用，改为仅标记出生点区块为 `forceLoad`。
2. `PacketSelectMap` handler 中 `teleportTo` 加 try-catch，异常时清理 `setActiveMapConfig(null)`。

**涉及文件**：
- `world/GameWorldManager.java`
- `network/packet/PacketSelectMap.java`

---

## 15. DistantHorizons NPE 导致传送/命令失败

**现象**：传送玩家到 BLS 自定义维度时，DistantHorizons 模组不认识自定义维度，`PlayerChangedDimensionEvent` 中抛 NPE。影响范围：
- 选地图后传送：队伍/阵营选择框不弹出
- `/bls stopgame`：命令执行失败
- `BattleLineSystem.onGameEnd()`：游戏结束传送回主世界可能失败

**修复**：所有跨维度的 `player.teleportTo()` 均用 try-catch 包裹，捕获异常后仅打日志，不阻断正常流程。已包裹的位置：

| 文件 | 方法/位置 |
|------|----------|
| `network/packet/PacketSelectMap.java` | 选地图传送 |
| `network/packet/PacketClassSelect.java` | 部署传送 |
| `command/OpenMapCommand.java` | stopgame 传送回主世界 |
| `command/OpenMapCommand.java` | 编辑模式传送 |
| `command/OpenMapCommand.java` | spectator 模式传送 |
| `command/OpenMapCommand.java` | switchteam 传送 |
| `world/GameWorldManager.java` | `teleportAllToOverworld()` |
| `BattleLineSystem.java` | `onGameEnd()` 传送 |

**注意**：`teleportTo` 的异常不会阻止玩家已发送到服务端的网络包处理，但会中断同一 handler 中后续的网络包发送（所以必须 try-catch）。

---

## 16. 部署界面不关闭（closeScreen + deployFade 状态残留）

**现象**：部署有几率成功但部署界面不关闭，画面停留在部署界面。

**原因**：客户端 `ScreenFadeUtil` 状态管理问题：
1. `startDeployFade()` 未重置 `closeScreen = false`，上次部署残留的 `closeScreen=true` 可能被新部署流程检测到。
2. `ClassSelectScreen.render()` 检查 `closeScreen` → 如果残留 true 则立即关闭屏幕，但此时 fade 动画尚未完成。
3. 打开 `handleOpenClassVote` 时未重置 `deployFade`，旧的 `onFadeOutDone` 回调可能设置 `closeScreen=true` 干扰新屏幕。

**修复**：
1. `startDeployFade()` 开头添加 `closeScreen = false`。
2. `WaitHudOverlay` 新增 `resetDeployFade()` 方法。
3. `ClientPacketHandler.handleOpenClassVote()` 开头调用 `resetDeployFade()` + `closeScreen = false`。
4. `ClientSetup.java` 手动打开部署界面时也重置 `closeScreen`。

**涉及文件**：
- `client/gui/WaitHudOverlay.java`
- `client/gui/ScreenFadeUtil.java`
- `network/ClientPacketHandler.java`
- `client/ClientSetup.java`

---

## 17. 载具部署后重开部署界面

**现象**：选择载具部署后，已进入生存模式的玩家又被弹出部署界面。

**原因**：服务端 `NetworkManager.broadcastVehicleState()` 遍历同阵营所有玩家发送 `PacketOpenClassVote`，包括刚刚部署完的玩家自己。客户端 `handleOpenClassVote()` 无条件打开 `ClassSelectScreen`。

**修复（双层保护）**：
1. **服务端**：`broadcastVehicleState()` 中跳过已非观察者的玩家（`if (!p.isSpectator()) continue;`），不向已部署玩家发送职业选择包。
2. **客户端**：`handleOpenClassVote()` 中只有 `mc.player.isSpectator()` 时才打开 `ClassSelectScreen`。

**涉及文件**：
- `network/NetworkManager.java` — `broadcastVehicleState()`
- `network/ClientPacketHandler.java` — `handleOpenClassVote()`

---

## 18. /bls stopgame 错误不报错

**现象**：`/bls stopgame` 执行失败时只显示"试图执行该命令时出现意外错误"，没有具体错误信息。

**原因**：stopgame handler 整体没有 try-catch，DistantHorizons NPE 或其他异常直接导致命令框架返回通用错误。

**修复**：stopgame handler 整体用 try-catch 包裹，异常时打印 `LOGGER.error` 并返回 `§c停止游戏失败: {错误信息}`。

**涉及文件**：
- `command/OpenMapCommand.java` — stopgame handler

---

## 19. PacketOpenScreen 缺少队伍人数信息

**现象**：队伍选择界面需要显示双方人数以支持人数平衡检查。

**修复**：`PacketOpenScreen` 新增 `countA`/`countB` 字段，`TeamSelectScreen` 客户端检查人数差异 >= 2 时禁止加入。

**涉及文件**：
- `network/packet/PacketOpenScreen.java`
- `client/gui/TeamSelectScreen.java`

---

## 20. PacketOpenClassVote.sameTeamUUIDs 访问权限

**错误**：`sameTeamUUIDs` 是 package-private，`command.OpenMapCommand` 中 switchteam 逻辑在 `com.battlelinesystem.command` 包中无法直接访问。

**修复**：将 `sameTeamUUIDs` 改为 `public`，或通过 setter 方法赋值。

**涉及文件**：
- `network/packet/PacketOpenClassVote.java`

---

## 21. skipToFactionVote 中变量重复声明

**错误**：`BattleLineSystem.skipToFactionVote()` 中 `MinecraftServer srv` 被重复声明两次导致编译错误。

**修复**：移除其中一个声明。

**涉及文件**：
- `BattleLineSystem.java`

---

## 22. PacketSelectMap catch 块中引用作用域外变量

**错误**：`PacketSelectMap` handler 的 catch 块中引用了 try 块内声明的 `player` 变量，作用域外不可见导致编译错误。

**修复**：移除 catch 块中该引用。

**涉及文件**：
- `network/packet/PacketSelectMap.java`

---

## 23. 载具出生点类型不同步队伍 — A队有land时B队也显示载具

**现象**：只要A队配置了 `land` 类型的载具出生点，B队玩家即便没有 `land` 出生点（也没有匹配类型如 `tank`/`apc`/`car`），部署界面仍会显示对应的载具选择框。同理，如果只有B队有 `plane` 出生点，A队玩家也能看到 `plane` 载具。

**原因**：`PacketSyncSpawnPoints` 只发送了一个全局的 `vehicleSpawnTypes` 集合（`config.vehicleSpawnPoints.types.keySet()`），包含所有类型名，但不区分哪个队伍实际有该类型的出生点。客户端 `SpawnPointRenderer.hasUsableVehicleSpawn()` 对所有玩家使用同一集合判断。

**修复**：
1. `PacketSyncSpawnPoints` 将 `vehicleSpawnTypes` 拆为 `vehicleSpawnTypesA` / `vehicleSpawnTypesB`，分别编码/解码。
2. `PacketSelectMap` 新增 `buildTeamVehicleTypes()` 方法：遍历 `TypeSpawns`，根据 `team_a.length > 0` 或 `team_b.length > 0` 分别构建A队和B队的类型集合。
3. `SpawnPointRenderer` 拆分存储 `vehicleSpawnTypesA` / `vehicleSpawnTypesB`，`hasUsableVehicleSpawn()` 根据 `myTeam` 选择对应集合判断。
4. `ClientPacketHandler.handleSyncSpawnPoints()` 适配新的双参数 `setVehicleSpawnTypes(typesA, typesB)`。

**涉及文件**：
- `network/packet/PacketSyncSpawnPoints.java`
- `network/PacketSelectMap.java`
- `client/SpawnPointRenderer.java`
- `network/ClientPacketHandler.java`

---

## 问题分类大纲

### 服务端/客户端兼容
- **#1** S→C 包在专用服加载客户端类崩溃
- **#4** 仅 `compileJava` 未 `build`，jar 未更新

### 编译与构建
- **#2** `classSelectionsMap()` 跨包权限
- **#3** `TargetPoint.p()` 类型不兼容
- **#20** `sameTeamUUIDs` 跨包权限
- **#21** `srv` 变量重复声明
- **#22** catch 块引用作用域外变量

### 游戏流程缺陷
- **#5** 网络包改造后流程不通
- **#8** switchteam 后结算显示原队伍
- **#9** 跳边后新队友无选择框
- **#10** 无人投票不随机选指挥官
- **#11** HUD 显示"A/B队"而非阵营名
- **#12** 部署时看不到队友选择框
- **#14** 选地图后卡加载
- **#18** stopgame 不报具体错误
- **#19** 队伍选择缺人数

### UI/客户端状态
- **#16** 部署界面不关闭（状态残留）
- **#17** 载具部署后重开部署界面

### 三方模组兼容
- **#15** DistantHorizons NPE，传送需 try-catch

### 事件过滤
- **#13** 死亡事件未过滤维度

### 数据同步
- **#23** 载具出生点类型不分队伍

---

## 24. 音效声道错误导致全图静音

**现象**：游戏内音效使用 `SoundSource.MUSIC` 声道，在部分服务器环境下音量异常或无法播放。

**原因**：`playSoundToWorld()` / `playSoundToPlayer()` 使用 `MUSIC` 声道 + 1000.0f 音量，MUSIC 声道受客户端音量滑块独立控制。

**修复**：
1. 所有音效播放改用 `SoundSource.RECORDS`（RECORDS 声道全图可闻、不受衰减距离影响）
2. 音量从 `1000.0f` 降为 `1.0f`
3. 新增 `stopSoundToWorld()` 方法用于停止指定音效
4. 开局音乐从 `tick()` 中移除，改为 `startGameWithMap()` 选地图完成后播放

**涉及文件**：
- `game/CapturePointManager.java`

---

## 25. 开局音乐时机错误 — 地图未加载完就开始播放

**现象**：开局音乐早于玩家实际传送到游戏世界，等玩家到达时音乐可能已播放过半。

**原因**：`CapturePointManager.tick()` 首次 tick 即播放开局音乐，但此时地图区块仍在异步加载中。

**修复**：开局音乐移到 `PacketSelectMap.startGameWithMap()` 中，所有玩家传送完成后播放。

**涉及文件**：
- `game/CapturePointManager.java`
- `network/PacketSelectMap.java`

---

## 26. 据点占领/丢失无阵营专属音效

**现象**：据点被占领时所有玩家听到相同的原版音效，无阵营区分。

**修复**：
1. 新增 6 个阵营音效事件：`cntake`/`cnlose`、`rutake`/`rulose`、`ustake`/`uslose`
2. `playCaptureSound()` 根据占领方和被占方阵营配置播放对应 `captureSound`/`loseSound`
3. 回退使用原版 `UI_TOAST_CHALLENGE_COMPLETE`/`NOTE_BLOCK_BASS` 作为 fallback

**涉及文件**：
- `sound/ModSounds.java`
- `game/CapturePointManager.java`
- `resources/assets/battlelinesystem/sounds.json`

---

## 27. 死亡后可瞬间重新部署（缺少冷却）

**现象**：玩家死亡后立即可以重新部署，无等待时间。

**修复**：
1. `CapturePointManager` 新增 `deathTimestamps` 记录死亡时间，`DEPLOY_COOLDOWN_MS` = 5000
2. `GameEventHandler.onPlayerDeath()` 调用 `recordDeathTimestamp()`
3. `PacketOpenClassVote` 新增 `deployCooldownMs` 字段，下发冷却剩余时间
4. 客户端 `WaitHudOverlay` 冷却期间部署按钮变灰+显示倒计时秒数，忽略点击

**涉及文件**：
- `game/CapturePointManager.java`
- `event/GameEventHandler.java`
- `network/packet/PacketOpenClassVote.java`
- `network/ClientPacketHandler.java`
- `client/gui/WaitHudOverlay.java`

---

## 28. 同队玩家之间无视觉识别

**现象**：战场上无法快速区分队友和敌人（双方外观一致）。

**修复**：`CapturePointManager.syncPlayerGlowing()` 每 tick 同步，同队玩家设置原版发光效果（`setGlowingTag(true)`），非同队不发光。

**涉及文件**：
- `game/CapturePointManager.java`

---

## 29. totalPlayers 显示全局人数而非同队人数

**现象**：`PacketOpenClassVote.totalPlayers` 使用的是 `server.getPlayerList().getPlayerCount()`（全局所有玩家），导致职业人数限额显示不准。

**修复**：
1. 新增 `CapturePointManager.countTeamPlayers()` 统计同队人数
2. 所有构建 `PacketOpenClassVote` 的地方改用 `countTeamPlayers()`

**涉及文件**：
- `game/CapturePointManager.java`
- `network/NetworkManager.java`
- `event/GameEventHandler.java`
- `command/OpenMapCommand.java`
- `game/CommanderVoteManager.java`

---

## 30. PacketOpenClassVote 阵营名称未随包下发

**现象**：HUD 中阵营名称显示 "A队"/"B队"，依赖客户端本地 `FactionManager` 的第一个阵营池猜测。

**修复**：
1. `PacketOpenClassVote` 新增 `teamAName`/`teamBName` 字段，编解码实现同步
2. 所有构建 `PacketOpenClassVote` 的位置均设置实际阵营名称
3. 客户端 `CapturePointRenderer.setTeamNames()` 统一使用服务端下发的名称

**涉及文件**：
- `network/packet/PacketOpenClassVote.java`
- `network/NetworkManager.java`
- `network/ClientPacketHandler.java`
- `event/GameEventHandler.java`
- `command/OpenMapCommand.java`
- `game/CommanderVoteManager.java`
- `client/CapturePointRenderer.java`

---

## 31. switchteam 后新玩家缺据点/禁区/出生点数据

**现象**：`/bls switchteam` 跳边后，新加入的玩家客户端缺少据点、禁区、出生点等同步数据，后续部署时这些区域不显示。

**原因**：这些数据包只在游戏开局时广播一次，switchteam 后未补发。

**修复**：switchteam handler 中补发 `PacketSyncCapturePoints`、`PacketSyncForbiddenZones`、`PacketSyncSpawnPoints`。

**涉及文件**：
- `command/OpenMapCommand.java`

---

## 32. 枪械改装丢失（客户端只改本地不持久化）

**现象**：玩家在部署界面对武器 NBT 进行改装（改配件），重进服务器后改装丢失。

**修复**：
1. 新增 `PacketSaveGunMod` 网络包，客户端改装后发送到服务端
2. `GunModStorage` 存储玩家改装数据
3. 玩家离开游戏时清理 `GunModStorage`

**涉及文件**：
- `network/packet/PacketSaveGunMod.java`（新增）
- `network/AllPackets.java`
- `client/gui/WaitHudOverlay.java`
- `event/GameEventHandler.java`

---

## 33. 装备图标每帧重新解析 NBT（性能浪费）

**现象**：部署界面每个职业卡片下的装备图标每帧调用 `TagParser.parseTag()` 重新解析。

**修复**：新增 `buildIconCache()` 在设置职业选项/切换变体时预解析并缓存 `ItemStack`，渲染时直接用缓存。

**涉及文件**：
- `client/gui/WaitHudOverlay.java`

---

## 34. 据点传送条件过于宽松 — 已失守据点仍可传送

**现象**：本队已占领的据点被敌方反推到进度条越过中线后，本队玩家仍可点击该据点传送。

**原因**：原判断条件只检查 `owner == 本队`，未检查进度条是否仍在己方一侧。

**修复**：
1. 传送条件增加进度检查：`owner == 本队 && progress 仍在己方一侧`
2. 界面显示改进：进度被敌方反推的据点变半透明灰色，提示不可传送

**涉及文件**：
- `client/gui/WaitHudOverlay.java`

---

## 35. 死亡后职业槽被释放导致换职业

**现象**：玩家死亡后同阵营职业限额被释放，复活的玩家可被分配不同职业。

**原因**：`onPlayerDeath()` 中调用了 `NetworkManager.removePlayerFromClassSelections()` 释放职业槽。

**修复**：移除死亡时的 `removePlayerFromClassSelections()` 调用，职业分配保持持久化直到游戏结束。

**涉及文件**：
- `event/GameEventHandler.java`

---

## 36. PacketSelectMap 逻辑无法复用

**现象**：`/bls settings` 地图选择逻辑与 `PacketSelectMap` handler 中的逻辑重复。

**修复**：提取 `startGameWithMap(server, mapId)` 公共静态方法，`PacketSelectMap` 和 `OpenMapCommand` 共用。

**涉及文件**：
- `network/PacketSelectMap.java`
- `command/OpenMapCommand.java`

---

## 37. 据点占领防守加权导致推进困难

**现象**：已占领方在据点争夺时获得 1.5x 人数加权，导致进攻方需要远超防守方的人数才能推进。

**修复**：移除防守加权，改为纯人数决定推进方向（B队人数对比A队人数），让游戏节奏更快。

**涉及文件**：
- `game/CapturePointManager.java`

---

## 问题分类大纲（续）

### 音效
- **#24** 音效声道 MUSIC → RECORDS
- **#25** 开局音乐时机错误
- **#26** 据点无阵营专属音效

### 游戏流程
- **#27** 死亡后无部署冷却
- **#29** totalPlayers 统计错误
- **#30** 阵营名称未同步
- **#31** switchteam 缺同步数据
- **#34** 据点传送条件过宽
- **#35** 死亡释放职业槽
- **#37** 据点防守加权过高

### 视觉/识别
- **#28** 同队玩家无视觉识别

### 持久化
- **#32** 枪械改装不持久化

### 性能
- **#33** 装备图标每帧重解析 NBT

### 代码质量
- **#36** PacketSelectMap 逻辑重复

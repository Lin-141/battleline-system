# BattleLine System 项目结构文档

> 基于 Forge 1.20.1 的服务端战地玩法模组  
> 约 86 个 Java 文件，总量约 12,000+ 行

---

## 一、根目录关键文件

| 文件 | 用途 |
|------|------|
| `src/main/java/.../BattleLineSystem.java` | **模组入口**：注册事件、指令、网络包、服务端 tick |
| `.gitignore` | 过滤 build 产物、运行数据、编译日志 |
| `build.gradle` | Gradle 构建脚本 |

---

## 二、包结构总览

```
com.battlelinesystem
├── BattleLineSystem.java          # 模组主类（入口）
│
├── network/                       # ====== 网络层 ======
│   ├── PacketBase.java            # 数据包抽象基类（write + handle）
│   ├── AllPackets.java            # 枚举注册中心（26个数据包声明于此）
│   ├── NetworkManager.java        # 网络工具：职业计数、载具追踪
│   ├── ClientPacketHandler.java   # [仅客户端] S→C 包渲染逻辑分发
│   ├── PacketSelectMode.java      # C→S 模式投票
│   ├── PacketSelectMap.java       # C→S 地图选择（含世界创建）
│   ├── PacketTimeUp.java          # S→C 倒计时结束 → 地图投票界面
│   └── packet/                    # 23 个独立数据包文件
│       ├── PacketOpenScreen.java          # S→C 0  打开模式/队伍选择界面
│       ├── PacketFactionList.java         # S→C 1  阵营列表 → 多屏更新
│       ├── PacketMapListResponse.java     # S→C 2  地图列表响应
│       ├── PacketOpenFactionVote.java     # S→C 3  打开阵营投票界面
│       ├── PacketOpenClassVote.java       # S→C 4  打开职业/载具选择界面
│       ├── PacketSyncCapturePoints.java   # S→C 5  同步据点列表
│       ├── PacketCapturePointProgress.java# S→C 6  同步据点占领进度（含CaptureEntry）
│       ├── PacketSyncSpawnPoints.java     # S→C 7  同步出生点坐标
│       ├── PacketGameOverResult.java      # S→C 8  游戏结算（含PlayerStatEntry）
│       ├── PacketSyncBeaconEntities.java  # S→C 10 信标实体同步（含BeaconEntry）
│       ├── PacketOpenCommanderVote.java   # S→C 11 指挥官投票界面
│       ├── PacketLooseSpawnTest.java      # S→C 12 宽松重生调试
│       ├── PacketFactionAction.java       # C→S ADD/REMOVE/UPDATE阵营
│       ├── PacketFactionRequest.java      # C→S 请求阵营列表
│       ├── PacketMapListRequest.java      # C→S 请求地图列表
│       ├── PacketFactionSelect.java       # C→S 选择阵营 → 触发职业选择
│       ├── PacketMapConfigSave.java       # C→S 保存地图配置到JSON
│       ├── PacketTeamSelect.java          # C→S 选择AB队 → 平衡检查 → 指挥官投票
│       ├── PacketClassSelect.java         # C→S 选择职业/变体/载具/生成点 → 生成
│       ├── PacketCapturePointViewTeleport.java  # C→S 视角传送到据点
│       ├── PacketCapturePointViewTeleportRaw.java # C→S 视角传送到坐标
│       ├── PacketCommanderVote.java       # C→S 指挥官投票
│
├── game/                          # ====== 游戏逻辑层 ======
│   ├── CapturePointManager.java   # [核心] 据点占领系统（~800行）
│   │   - 玩家队伍分配（team/faction）          - 据点归属/占领进度计算
│   │   - AB队分数追踪                            - 全局脚本触发（到达阈值时执行指令）
│   │   - 玩家战绩统计                            - 音乐播放（start/victory/defeat/nearEnd）
│   │   - 信标系统（flag_capture_on_player）       - 死亡扣费
│   ├── CommanderVoteManager.java  # 指挥官投票系统（每队独立投票）
│   ├── GameModeManager.java       # 各模式投票统计
│   ├── ModeCountdownManager.java  # 模式选择阶段倒计时（~77行）
│   ├── SelectionCountdownManager.java # 队伍/阵营选择倒计时（~85行）
│   │   - 自动选择  - 超时跳过
│   ├── VehicleRespawnManager.java # 载具重生冷却管理
│
├── faction/                       # ====== 阵营/职业配置 ======
│   ├── FactionManager.java        # 阵营增删改查、活跃地图配置持有
│   ├── FactionConfig.java         # 阵营配置数据（id, name, classes, vehicles等）
│   ├── ClassConfig.java           # 职业配置（装备NBT、变体、枪械配件池）
│   ├── ClassVariant.java          # 职业变体（替换装备 + 解锁条件）
│   ├── VehicleConfig.java         # 载具配置（type, maxCount, cooldown, deployScripts）
│   ├── GunAttachmentConfig.java   # 枪械配件池（scope, muzzle, grip, stock, laser）
│   ├── GunAttachmentOption.java   # 单个配件选项
│
├── world/                         # ====== 世界/地图 ======
│   ├── GameWorldManager.java      # 动态创建/加载游戏世界
│   ├── MapConfig.java             # 地图配置结构（~431行）
│   │   - 内部类: CapturePoint, SpawnPoints, VehicleSpawnPoints, GlobalScript
│
├── event/                         # ====== 事件监听 ======
│   ├── GameEventHandler.java      # 死亡处理、载具重生、信标交互、重启检测
│   ├── EmergencyShutdownHandler.java # 紧急关服处理
│
├── command/                       # ====== 服务端指令 ======
│   ├── OpenMapCommand.java        # /openmap - 核心流程控制
│   ├── BeaconCommand.java         # /beacon - 信标管理
│   ├── SpawnPointCommand.java     # /spawnpoint - 出生点管理
│   ├── CapturePointCommand.java   # /capturepoint - 据点管理
│   ├── LooseSpawnTestCommand.java # /loosespawntest - 宽松重生测试
│
├── client/                        # ====== [仅客户端] UI/渲染 ======
│   ├── ClientSetup.java           # 客户端注册入口
│   ├── CapturePointRenderer.java  # 据点头标渲染
│   ├── BeaconRenderer.java        # 信标渲染
│   ├── SpawnPointRenderer.java    # 出生点标记渲染
│   ├── LooseSpawnRenderer.java    # 宽松重生队友显示
│   ├── WorldHudUtils.java         # HUD 工具
│   ├── gui/
│   │   ├── MapSelectScreen.java         # 模式投票界面
│   │   ├── MapVoteScreen.java           # 地图投票界面
│   │   ├── TeamSelectScreen.java        # 队伍选择（AB队）
│   │   ├── FactionVoteScreen.java       # 阵营投票界面
│   │   ├── ClassSelectScreen.java       # 职业选择界面
│   │   ├── WaitHudOverlay.java          # [大文件~1000行] HUD混合体（小地图/职业/变体/载具）
│   │   ├── GameOverScreen.java          # 结算画面
│   │   ├── CommanderVoteScreen.java     # 指挥官投票界面
│   │   ├── FactionSettingsScreen.java   # OP 阵营管理界面
│   │   ├── FactionEditScreen.java       # 编辑阵营属性
│   │   ├── MapSettingsScreen.java       # OP 地图管理界面
│   │   ├── MapEditScreen.java           # 编辑地图属性
│   │   ├── CapturePointConfigScreen.java# 据点区域配置
│   │   ├── SettingsScreen.java          # OP 全局设置
│   │   ├── ClassEditScreen.java         # 编辑职业
│   │   ├── ClassVariantEditScreen.java  # 编辑职业变体
│   │   ├── VehicleEditScreen.java       # 编辑载具
│   │   ├── CommanderItemsEditScreen.java# 编辑指挥官物品
│   │   ├── GunModScreen.java            # 枪械改装界面
│   │   ├── GunAttachmentPoolEditScreen.java # 编辑枪械配件池
│   │   └── ScreenFadeUtil.java          # 界面淡入淡出工具
│
├── items/                         # ====== 物品 ======
│   ├── ModItems.java              # 物品注册
│   ├── SelectionWandItem.java     # 选区魔杖
│   └── BeaconWandItem.java        # 信标魔杖
│
├── sound/                         # ====== 音效 ======
│   └── ModSounds.java             # 音效注册（start_01, waiting_01, ending_01）
│
├── mixin/                         # ====== Mixin ======
│   ├── MinecraftServerMixin.java  # 服务端 tick 钩子
│   └── LevelResourceAccessor.java # 世界资源访问器
│
└── accessor/                      # ====== 访问器 ======
    └── MinecraftServerAccessor.java # MinecraftServer 扩展访问
```

---

## 三、核心数据流

```
[客户端]                        [服务端]
MapSelectScreen                 ModeCountdownManager
  └─ PacketSelectMode ────────>   └─ 统计票数 → PacketTimeUp
                                        ↓
MapVoteScreen                   GameModeManager
  └─ PacketSelectMap ─────────>   └─ 创建世界 → PacketOpenScreen(队伍选择)
                                        ↓
TeamSelectScreen                SelectionCountdownManager
  └─ PacketTeamSelect ────────>   └─ CommanderVoteManager
                                        ↓
FactionVoteScreen               FactionManager
  └─ PacketFactionSelect ─────>   └─ 分配阵营 → PacketOpenClassVote
                                        ↓
ClassSelectScreen               VehicleRespawnManager
  └─ PacketClassSelect ───────>   └─ 装备授予、传送、载具生成
                                        ↓
[CapturePointRenderer]          CapturePointManager
  ◄── PacketSyncCapturePoints ──   同步据点
  ◄── PacketCapturePointProgress ─ 同步进度
                                        ↓
[GameOverScreen]                CapturePointManager
  ◄── PacketGameOverResult ────   分数统计、结算
```

---

## 四、已有已知问题（待优化）

| 优先级 | 问题 | 涉及文件 |
|--------|------|----------|
| 高 | `WaitHudOverlay.java` ~1000行，混合小地图/职业/变体/载具 UI | `client/gui/WaitHudOverlay.java` |
| 高 | `CapturePointManager.java` ~800行，据点+分数+脚本+统计+音乐 | `game/CapturePointManager.java` |
| 中 | `MapConfig.java` 内部类过多（GlobalScript, CapturePoint, SpawnPoints） | `world/MapConfig.java` |
| 低 | 三个倒计时管理器结构相似可提取基类 | `game/*CountdownManager.java` |
| 低 | `BattleLineSystem.java` 中全限定名调用过多 | `BattleLineSystem.java` |

---

## 五、网络包快速索引

**C→S（客户端发服务端）**: `PacketSelectMode`, `PacketSelectMap`, `PacketFactionAction`, `PacketFactionRequest`, `PacketMapListRequest`, `PacketFactionSelect`, `PacketMapConfigSave`, `PacketTeamSelect`, `PacketClassSelect`, `PacketCapturePointViewTeleport`, `PacketCapturePointViewTeleportRaw`, `PacketCommanderVote`

**S→C（服务端发客户端）**: `PacketOpenScreen`, `PacketTimeUp`, `PacketFactionList`, `PacketMapListResponse`, `PacketOpenFactionVote`, `PacketOpenClassVote`, `PacketSyncCapturePoints`, `PacketCapturePointProgress`, `PacketSyncSpawnPoints`, `PacketGameOverResult`, `PacketSyncBeaconEntities`, `PacketOpenCommanderVote`, `PacketLooseSpawnTest`

**新增数据包步骤**：(1) 创建独立文件 extends PacketBase → (2) 在 AllPackets.java enum 加一行 → (3) S→C 包在 ClientPacketHandler.java 加 case

---

## 六、构建/部署

```
# 编译
gradlew compileJava

# 打包（输出到 build/libs/）
gradlew build -x test
```

构建注意事项：
- **数据包 handle() 中禁止直接引用 `Minecraft`、`Screen`、`client.gui.*` 等客户端类**
- S→C 包客户端逻辑必须放在 `ClientPacketHandler.java`（`@OnlyIn(Dist.CLIENT)`）
- 调用方式：`context.enqueueWork(() -> NetworkManager.dispatchClient(ID, this))`

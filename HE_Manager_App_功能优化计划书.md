# HE Manager App 功能优化计划书

> **项目**：HE_manager_App（安卓原生端，`com.hemanager.mobile`，v0.1.0）
> **审查范围**：MainActivity、feature/library 全套、feature/creators、feature/login、player/*、audio/*、ApiClient、HePrefs、build.gradle
> **日期**：2026-06-22
> **用途**：交付 Codex 逐项实施，每项可独立成 PR

---

## 一、概述

本次审查逐文件阅读了根包 Java POJO（ApiClient / MediaItem 等）、MainActivity.kt（539 行，承载全局手势分发与图廊 pinch 监听）、feature/library 子包（LibraryScreen.kt 1335 行为主屏核心，外加 Drawer / Filter / Gallery / Card / Atoms / Helpers / TagPicker 共 8 个文件约 5000 行）、feature/creators、feature/login、player/*（视频播放器 MVVM 全套）、audio/*（ASMR 播放器 + Media3 Service）以及构建配置 build.gradle，总计约 9000 行 Kotlin + 1100 行 Java。

**结论**：安卓端在浏览、图廊缩放、封面预取等"看得见的体验"上已经打磨得相当细致，但在**网络健壮性、错误反馈、设置完整性**等"看不见的基础"上存在若干缺口。本计划书按 P0 / P1 / P2 三档优先级给出可执行方案。

### 优先级定义

| 等级 | 含义 | 建议处理窗口 |
|------|------|------------|
| **P0** | 直接影响核心可用性，用户高频踩中 | 1–3 天内 |
| **P1** | 功能不完整或体验明显短板 | 1–2 周内 |
| **P2** | 工程隐患 / 死代码 / 可维护性 | 择期清理 |

---

## 二、P0：网络健壮性缺口

### 2.1 缺失 401 / Token 过期处理

**现状**

`ApiClient.readResponse()`（`ApiClient.java:164`）对所有非 2xx 响应一律抛出 `RuntimeException(message)`，全局没有任何一处代码识别 HTTP 401。后端 access_token 通常有效期较短（数小时），过期后所有需要鉴权的请求（刷新库、收藏、删除、标签）都会静默失败，库列表卡在旧数据，用户只看到通用的"读取失败"提示，无法判断是网络问题还是登录失效，只能手动进入抽屉点 DISCONNECT 重登。

`readableError()`（`Helpers.kt:478`）只匹配了 `"Failed to connect"` 和 `"timeout"` 两种字符串，401 的真实信息（后端返回的 detail 字段，如 `"Could not validate credentials"`）被原样抛给用户。

**影响**

- 登录态失效后，App 内所有操作静默失败，体感像"App 坏了"。
- 用户无法自助判断需要重登，只能盲试退出重登，排查成本高。
- 收藏 / 删除等乐观更新会因 401 永远回滚，且错误提示不明确。

**方案**

1. 在 `ApiClient.java` 中新增专用异常类 `UnauthorizedException`，在 `readResponse()` 里识别 `code == 401` 时抛出。
2. 在 `LibraryScreenV2.load()` 与 `CreatorsScreen.openCreator()` 的失败分支捕获 `UnauthorizedException`，调用 `onLogout()`（已存在）或 `prefs.clearToken()`，自动退回登录页并附带提示「登录已过期，请重新登录」。
3. 扩展 `readableError()` 识别 401 相关关键字，给出友好文案。
4. 在 `ApiClient.toggleFavorite` / `deleteMedia` / `addTag` 等写操作路径上同样接入，避免乐观更新永久回滚。

**示意代码**（`ApiClient.java`）：

```java
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String msg) { super(msg); }
}

// readResponse() 内
if (code == 401) {
    throw new UnauthorizedException("登录已过期");
}
```

**工作量**：约 0.5 天（含异常类定义、调用点接入、readableError 扩展、弱网模拟测试）。

---

### 2.2 列表请求缺少自动重试

**现状**

`ApiClient` 配置了 8 秒连接超时、20 秒读取超时，失败即抛错，整个 App 没有任何自动重试机制，只有 UI 上的手动 RETRY 按钮（`ErrorPanelV2`）。手机连接家庭服务器场景下（Wi-Fi 漫游、信号衰减、路由抖动），偶发的瞬时连接失败会直接整屏报错，用户必须手动点重试，体验割裂。

**方案**

1. 在 `ApiClient.getMedia` / `getCreators` / `getCreatorDetail` 等列表读取路径上包一层 `retrying { }`，最多重试 2 次，指数退避（200ms → 600ms）。
2. 只对网络类异常（`SocketTimeoutException` / `ConnectException` / `UnknownHostException`）重试，对业务异常（4xx / 5xx 返回的业务错误）不重试，避免放大错误。
3. 写操作（`toggleFavorite` / `deleteMedia`）不重试，避免重复写入。
4. 通过 `runCatching` + 循环实现，无需引入新依赖。

**工作量**：约 1–2 小时（retrying 工具函数 + 列表路径接入 + 弱网测试）。

---

## 三、P1：功能完整性缺口

### 3.1 设置页为空实现

**现状**

抽屉导航中的"设置"项（`Drawer.kt:340`，`LibraryScreen.kt:872`）点击后只调用 `context.toastComingSoon("设置")` 弹一个 Toast，没有任何真实功能。若干本应可配置的项当前被硬编码：

- 封面磁盘缓存上限固定 256 MB（`MainActivity.kt:249`），不可调整。
- 图廊默认列数只能通过 pinch 修改，且只存在单台设备的 SharedPreferences。
- 切换服务器地址必须先退出登录再重登，已有的 `serverHistory` 数据没有直接切换入口。
- 视频默认播放速度 / 默认字幕轨（`PlayerPreferences` 已存在）没有 UI 暴露。

**方案**

补一个最小可用的设置页（`feature/settings/SettingsScreen.kt`），首版包含以下分组，全部基于已存在的 `HePrefs` / `PlayerPreferences` 扩展，无需新增后端接口：

| 分组 | 设置项 | 数据来源 |
|------|--------|---------|
| 显示 | 图廊默认列数（3–7 滑杆） | `HePrefs.galleryColumns` |
| 显示 | 主题强调色（可选扩展） | 新增 key |
| 缓存 | 封面磁盘缓存上限（128 / 256 / 512 MB） | `coverImageLoader` 配置 |
| 缓存 | 立即清理封面缓存按钮 | `cacheDir` 操作 |
| 连接 | 切换服务器（列出 `serverHistory` 单选） | `HePrefs.serverHistory` |
| 播放 | 视频默认速度 / 默认字幕语言 | `PlayerPreferences` |
| 关于 | 版本号 / 服务器地址 / 退出登录 | BuildConfig + HePrefs |

**工作量**：约 1 天（含 UI、prefs 扩展、缓存清理逻辑）。

---

### 3.2 创作者详情 STAR 按钮空实现

**现状**

`CreatorsScreen.kt:696` 的 `GhostCta("STAR")` 按钮 onClick 是注释 `/* TODO: 收藏整个 creator — 后端尚无接口 */`，点击后没有任何反馈。这是一个会让用户困惑的"假按钮"。

**方案**（二选一）

- **路 A（推荐，短期）**：直接移除该按钮，避免假交互。10 分钟可完成。
- **路 B（需后端配合）**：后端新增 `POST /mobile/creators/{key}/favorite` 接口，前端接入乐观更新（复用 `runQuickAction` 模式）。工作量约 1 天（前后端各半天）。

**建议**：先走路 A 移除按钮消除假交互，待后端排期再做路 B。

---

### 3.3 搜索 / 排序逻辑位置不合理

**现状**

`LibraryScreenV2` 一次性拉取全量 `allItems` 后，`mediaType` / `status` / `source` 三类筛选都在客户端 `derivedStateOf` 里完成（`LibraryScreen.kt:360`），但 `search` 和 `sortFilter` 又走后端 `load()`（`LibraryScreen.kt:822`）。这种"半本地半远端"的混合策略导致：

- 每次搜索框输入都触发整库重拉（虽有 280ms debounce），响应延迟数百毫秒。
- 排序切换必须等网络，无法瞬时切换。
- 离线时搜索完全不可用，但本地筛选仍可用，行为不一致。

**方案**

**决策依据**：个人自用库数据量通常在几百到几千条，完全可以全量加载到内存。全量加载后，搜索 / 排序 / 筛选统一在本地完成，搜索响应从"网络延迟"降为"瞬时"。若未来数据量增长到上万条，则反过来给后端加分页（offset / limit），客户端筛选退化为服务端筛选。当前是夹在中间的尴尬状态。

**实施**：将 `sortFilter` / `search` 也并入客户端 `derivedStateOf`，`load()` 只在 `ON_RESUME` 和手动刷新时触发。

**工作量**：约 0.5 天。

---

## 四、P2：工程层面隐患

### 4.1 巨型重复 import 块与 V2 死代码

**现状**

`feature/library/*.kt` 每个文件顶部都有约 230 行几乎完全相同的 import 块（`LibraryScreen` / `Drawer` / `Helpers` / `Filter` / `Gallery` / `Card` / `Atoms` 七个文件共享同一批批量 import），这是当初从 MainActivity 平移时整块拷过来的，大部分根本没用到——但因为同包 internal 不影响编译就一直留着。

此外 `Helpers.kt` 里 `progressText`（无 V2）与 `progressTextV2`、`meta` 与 `metaInlineV2`、`typeLabel` 与 `mediaTypeLabelV2`、`progressColor` 与 `progressColorV2` 全是重复语义的两套实现，文件头注释自己也承认"未来可合并去掉 V2 后缀"。

**方案**

开一个清理 PR：运行 IDE Optimize Imports 批量精简 import，删除 V2 旧版重复函数（保留 V2 版本，删除无后缀版），光这一步就能砍掉上千行噪声，后续维护成本显著降低。此步不触碰任何功能逻辑，风险极低。

**工作量**：约 1–2 小时。

---

### 4.2 JVM 目标版本偏低（Java 8 → 17）

**现状**

`build.gradle:43-49` 配置 `compileSdk 35` / `targetSdk 35`，但 `jvmTarget` 仍是 1.8。Compose、Media3、Coil 等依赖早已基于 Kotlin 1.9 / JVM 17 生态，继续用 Java 8 会丢失更好的 desugaring 与 ART 优化空间。

**方案**

```groovy
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
```

**工作量**：约 30 分钟（含 release 包回归测试）。低风险升级。

---

### 4.3 MediaItem 可变 POJO 与 Compose 状态

**现状**

`MediaItem.java` 全是 public 可变字段，运行时直接 `item.favorite = target` 写入（`LibraryScreen.kt:763`）。Compose 的 `derivedStateOf` / `remember` 读的是对象引用，字段被原地改时 Compose 不会 recompose。当前能"工作"是因为紧接着 `allItems = allItems.map { ... }` 制造了新列表引用触发重组——但这个写法很脆弱，任何一处忘了 `.map` 重建就会出现"收藏点了 UI 没反应"的偶发 bug。

**方案**

中期建议：把 `MediaItem` 改为 Kotlin `data class`（全 `val` 字段），所有更新走 `copy()`。这会顺带让 `derivedStateOf` 的依赖追踪更可靠。改动面较大（涉及所有读写点），但一劳永逸。建议在 P0 / P1 落定后作为一个独立重构 PR 推进。

**工作量**：约 1–2 天。

---

### 4.4 MainActivity 跨模块可变状态耦合

**现状**

`MainActivity` 持有 `edgeDrawerGestureEnabled` / `imageGalleryNativePinching` / `creatorsScreenActive` 等 `internal var`（`MainActivity.kt:262-277`），被 `LibraryScreen` / `Gallery` / `CreatorsScreen` 通过 `context as MainActivity` 强转后读写。这是隐式的全局可变状态，在并发场景（手势线程 + Compose 重组）下没有可见性保证（非 `volatile`、非 `synchronized`）。目前没炸是因为都是 UI 单线程访问，但属于"地雷"。

**方案**

建议把这些状态收口到一个单例 `UiGestureState` 或 `CompositionLocal`，去掉 `as MainActivity` 强转耦合。可与 4.3 一并重构。

---

## 五、实施计划与路线图

### 5.1 工作量与优先级汇总

| 序号 | 改进项 | 优先级 | 工作量 | 风险 | 状态 |
|------|--------|--------|--------|------|------|
| 1 | ApiClient 识别 401 自动退回登录 | **P0** | 0.5 天 | 低 | 已完成 ✅ |
| 2 | 列表请求自动重试 | **P0** | 1–2 小时 | 低 | 已完成 ✅ |
| 3 | 补最小可用设置页 | **P1** | 1 天 | 中 | 未开始 ❌ |
| 4 | 创作者 STAR 按钮处理 | **P1** | 10 分钟（移除） | 极低 | 已完成 ✅ |
| 5 | 搜索 / 排序改纯客户端 | **P1** | 0.5 天 | 中 | 已完成 ✅ |
| 6 | 清理死 import + 合并 V2 函数 | **P2** | 1–2 小时 | 极低 | 部分完成 (Helpers.kt 已清) 🟡 |
| 7 | JVM 目标升至 17 | **P2** | 30 分钟 | 低 | 已完成 ✅ |
| 8 | MediaItem 改 Kotlin data class | **P2** | 1–2 天 | 中高 | 未开始 ❌ |

### 5.2 建议实施顺序

**第一阶段：网络健壮性（P0，预计 1 天）**
优先做改进项 1 + 2（401 处理 + 自动重试）。这两项加起来一两小时到半天能搞定，但对日常用这个 App 的体感改善最直接——token 过期不再"假死"，弱网不再整屏报错。

**第二阶段：功能完整性（P1，预计 2 天）**
- 改进项 4（移除假按钮）可立即顺手做掉；
- 改进项 3（设置页）和改进项 5（搜索本地化）作为独立 PR 推进，互不依赖，可并行。

**第三阶段：工程清理（P2，预计 2–3 天，择期）**
- 改进项 6（死 import 清理）零风险，随时可做，建议优先；
- 改进项 7（JVM 17）半小时即可，顺手做；
- 改进项 8（MediaItem 重构）改动面大，建议作为收尾的重构 PR，与改进项 4.4（MainActivity 状态收口）合并推进。

### 5.3 验证策略

- **P0 验证**：手动让后端 token 过期（重启后端或等待自然过期），确认 App 自动退回登录页并提示；用 Charles / mitmproxy 模拟弱网（断流 / 高延迟），确认列表请求自动重试。
- **P1 验证**：设置页各项开关实测生效；搜索本地化后，关闭网络仍可搜索已加载内容。
- **P2 验证**：清理死 import 后 `gradlew.bat :app:assembleDebug -q` 无输出即成功；JVM 17 升级后跑 `android_release.bat` 确认 release 包功能正常。
- **回归基线**：所有改动以 `android_release.bat` 构建的 release 包为准做体感验证，debug 包不代表真实性能（AGENTS.md 已强调）。

---

## 六、附录

### 6.1 关键代码位置索引

| 模块 | 文件 | 关键行 |
|------|------|--------|
| 网络层 | `ApiClient.java` | `readResponse():164` / `open():151` |
| 错误处理 | `Helpers.kt` | `readableError():478` |
| 主屏 | `LibraryScreen.kt` | `load():727` / `runQuickAction():757` |
| 登录 | `LoginScreen.kt` | `submit():112` |
| 创作者 | `CreatorsScreen.kt` | STAR 空实现 `:696` |
| 偏好 | `HePrefs.kt` | `saveCredentials():85` |
| 构建 | `build.gradle` | `jvmTarget:49` / `minify:27` |

### 6.2 未纳入本次计划的已知约束

- **MangaActivity / PlayerActivity 受保护**：AGENTS.md 明确这两个文件"受保护，自管窗口 / 视频播放器重写中"，本次审查未深入，相关优化另行排期。
- **ProGuard / R8 配置**：release 已开启 `minify` + `shrinkResources`，但本次未审查 `proguard-rules.pro` 是否对 `com.hemanager.mobile.**` 数据类做了 `-keep`，建议在 P0 落地后用 `android_release.bat` 验证一次完整流程。

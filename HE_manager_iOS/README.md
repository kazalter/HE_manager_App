# HE Manager iOS (SwiftUI Native)

这是针对 HE Manager 媒体库服务（自建局域网服务端，如 `http://192.168.x.x:8010`）的 iOS 原生客户端。基于 **Swift 6 + SwiftUI + AVFoundation** 构建，完全对齐 Android 端的全部业务功能与 OP（Operator）科技风设计系统。

---

## 📱 核心功能特性

1. **OP 科技风视觉设计系统**：
   - 全局深色高对比度主题（Void / Ink / Panel）。
   - 切角几何容器（`AngularPanel`）与高饱和黄（`#F5D800`）装饰封口。
   - HUD 风格状态指示、Monospace 元数据排版与微动效。

2. **身份鉴权与终端连接**：
   - 局域网服务器连接与连通性检测。
   - 自动记录历史服务器地址，支持一键切换。
   - Token 自动持久化、HTTP 401 自动失效登出提示、弱网指数退避重试（200ms / 600ms）。
   - 支持创建根管理员（`/auth/bootstrap`）与常规登录（`/auth/login`）。

3. **主媒体库与图廊**：
   - 纯客户端全量实时搜索与多维复合筛选（媒体类型、观看状态、来源站点、标签组合）。
   - **动态网格双指缩放（Pinch to Zoom）**：在屏幕上双指张合即可无缝在 3 ~ 7 列网格之间动态缩放，带震动触觉反馈。
   - 封面两级缓存（内存 NSCache + 磁盘沙盒缓存），支持设置中查看缓存体积与一键清理。
   - 乐观更新快捷收藏（Star）与标签快速编辑弹窗（`TagPickerView`）。

4. **创作者聚合屏**：
   - 按画师（Artist）与推主（X Creator）聚合分类展示作品。
   - 创作者详情页：作者主页横幅、作品总数、关联全部作品网格。

5. **漫画 / 图集双模式阅读器**：
   - **分页模式（Paged）**：左右滑动手势翻页，高分辨率大图无极双指平移缩放与双击复位，前 4 页自动静默预加载。
   - **条漫长图模式（Webtoon）**：垂直瀑布流连续无限滚动。
   - 浮动 HUD 控制层：页码进度条直接拖动跳转、阅读进度实时同步回传服务器。

6. **视频播放器**：
   - 基于 `AVPlayer` + 自定义全套控制层。
   - **全屏手势系统**：
     - 左半屏垂直滑动手势：实时调节屏幕背光亮度。
     - 右半屏垂直滑动手势：调节播放音量。
     - 水平滑动手势：精确快进 / 快退 Scrub。
     - 左右两侧双击手势：快退 / 快进 10 秒（带 HUD 视觉涟漪动效）。
   - 倍速播放切换（0.75x ~ 2.0x）、画面比例切换（Fit / Fill）、进度记忆续播与定时同步。

7. **ASMR 音频播放器**：
   - 基于 `AVAudioSession` 配置 `.playback`，支持**真正的后台常驻播放与锁屏控制**。
   - 系统锁屏面板（`MPNowPlayingInfoCenter`）与耳机/控制中心线控（`MPRemoteCommandCenter`）完整支持。
   - **时间轴歌词同步**：根据当前播放时间毫秒级高亮并平滑自动居中滚动，点击单行歌词可直接跳转进度。
   - 睡眠定时器（15分 / 30分 / 45分 / 60分 / 播完本轨后停止）。
   - 跨多音轨累计进度计算与服务端进度同步。

---

## 🛠 开发与编译运行 (Xcode)

### 前置条件
- 一台安装了 macOS 的苹果电脑。
- 从 Mac App Store 下载安装 **Xcode 15.0+**（推荐 Xcode 16+）。

### 运行步骤
1. 双击打开项目根目录下的 `HEManager.xcodeproj`：
   ```bash
   open HEManager_iOS/HEManager.xcodeproj
   ```
2. 在 Xcode 顶部选择运行目标（例如：`iPhone 16 Pro 模拟器` 或你通过数据线连接的 `个人真机`）。
3. 按下快捷键 `Cmd + R`，Xcode 将自动完成编译并在模拟器或真机上启动 App。

---

## 🔐 签名与真机安装指南 (iOS Deployment)

由于自建媒体库 App 无法（也无需）上架苹果 App Store，你可以通过以下三种主流方式安装到自己的 iPhone / iPad：

### 方案 1：Apple Developer 开发者证书（最省心）
- 如果你拥有个人 Apple 开发者账号（$99/年）：
  1. 在 Xcode 的 `Signing & Capabilities` 中登录你的 Apple ID。
  2. 勾选 `Automatically manage signing`，Team 选择你的账号。
  3. 连上手机点击运行，即可安装到真机。证书有效期为 **1 年**。

### 方案 2：免费 Apple ID + AltStore / SideStore（完全免费）
- 如果你使用的是普通免费 Apple ID：
  1. 可以在电脑上安装 [AltStore](https://altstore.io/)。
  2. 在 Xcode 中通过 `Product -> Archive` 导出 `.ipa` 安装包，或者直接用 Xcode 装入手机。
  3. 通过 AltStore 在同一局域网 Wi-Fi 下，手机每 7 天会在后台自动静默续签，无需连电脑。

### 方案 3：TrollStore（永久免签，若系统支持）
- 如果你的 iPhone 系统版本处于 TrollStore 支持范围（iOS 14.0 ~ 16.6.1 / 17.0 部分）：
  1. 在 Xcode 导出 `HEManager.ipa`。
  2. 通过 AirDrop 发送到手机，用 TrollStore 直接打开安装，享受永不过期的真机原生体验。

---

## ⚙️ 局域网连接注意事项

- **HTTP 访问许可**：
  iOS 默认强制启用 ATS（HTTPS）。本项目已在 `App/Info.plist` 中配置了 `NSAllowsArbitraryLoads = true` 和 `NSAllowsLocalNetworking = true`，允许与你的家庭局域网服务器（如 `http://192.168.1.xxx:8010`）进行正常的明文 HTTP 握手。
- **真机网络权限**：
  在 iOS 14+ 真机上首次打开 App 时，如果弹出「允许 App 查找并连接到本地网络上的设备」，请务必点击**允许**。

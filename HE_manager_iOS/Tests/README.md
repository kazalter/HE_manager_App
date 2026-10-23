# 连续阅读器回归验证

这个独立模拟器测试 App 直接编译正式的连续阅读器、图片缓存和主题代码，
使用本地生成的图片与 URLProtocol 延迟/失败响应，不需要服务器账号。
测试使用独立 Bundle ID 和沙盒，不修改 HE Manager 的登录信息、阅读数据或缓存。
测试代码不加入正式 App target。

运行（需 Xcode、rg 和一个已启动的 iOS 模拟器）：

```sh
xcrun simctl list devices booted
bash HE_manager_iOS/Tests/run-reader-regressions.sh <模拟器UDID>
```

脚本编译并安装测试 App，等待最多 30 秒，然后输出断言结果。
完整日志保留在脚本打印的临时目录中。全部通过时输出 HE_READER_TESTS_OK。

覆盖内容：

- 初始阅读位置、被动页码反馈不跳页、主动跳页、同页再次跳转、命令不重复执行。
- 双击放大/还原、二维焦点、原生内容缩放、横向移动、放大后旋转。
- 百页文档只挂载附近页视图、切换阅读模式不重放旧命令。
- 延迟图片的真实高度、上方页面高度改变后的锚点补偿、更新总页数。
- HTTP 失败占位与重试、取消旧文档请求、缩放动画期间图片到达。
- 越界页码、空文档。

这些是实际 UIKit 视图上的集成断言，不能替代真实触摸体验测试。
真机/可交互模拟器上还需分别从 IMAGE 和 MANGA 入口验证：

1. 持续上下拖动、快速甩动、途中反向拖动，确认跨页不会自动拉回页首。
2. 在屏幕上、中、下部捏合，同时移动双指中心；松开一指继续拖动。
3. 放大后斜向拖动、滚动至首尾、双击还原，确认没有意外空白或位置漂移。
4. 慢网速下连续阅读长短图混排图集，确认加载不打断手势。
5. 拖动进度条、切换 PAGE/SCROLL、横竖屏切换、退出重进，核对页码和进度。

## 登录、媒体库与创作者交互回归（macOS）

```sh
bash HE_manager_iOS/Tests/run-interaction-regressions.sh
```

需要 macOS 14+、Xcode 命令行工具和 Python 3；不需要模拟器、设备或真实服务器。
脚本在临时目录编译正式 ApiClient、Preferences、媒体模型及 ViewModel，启动仅监听
127.0.0.1 的合成 HTTP fixture，退出时停止 fixture。FilterState 和 CreatorsViewModel
从正式 SwiftUI 文件的类型边界前提取原文，不复制实现，也不加入正式 App target。
测试使用独立 UserDefaults suite，结束后清除，不读写正式登录或媒体库数据。

覆盖未认证 401 与会话过期的区别、连续收藏的请求顺序/失败回滚/刷新保护、
媒体库刷新失败保留数据、并发刷新及创作者分类的旧响应保护。
全部通过输出 `HE_INTERACTION_TESTS_OK`；临时目录保留编译与断言日志。
这些是实际网络和 ViewModel 回归，不代表 SwiftUI 点击、触摸、VoiceOver 或真机测试。

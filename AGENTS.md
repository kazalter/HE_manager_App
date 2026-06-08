# AGENTS.md — HE Manager App

个人自用媒体库的安卓原生 App 端项目。
本文件是常驻索引，避免每次重进逐个翻文件。

## ⚠️ 硬规则 — 防数据/代码丢失（任何 Codex 接手都必须遵守）

**触发条件**：要执行 `git reset --hard` / `git checkout -- <path>` / `git checkout <branch>`
（在脏工作区切分支）/ `git stash drop` / `git clean -fd*` / `git branch -D`（删未合并分支）
/ `git worktree remove --force` 之前。

**强制流程**（不能跳）：
1. **先跑 `git status`**——不要凭"我以为工作区干净"做判断，永远显式确认一次。
2. **如有任何工作区改动**（含 modified tracked + untracked source）：
   - 修改类（`M`）→ 先 `git commit`（即便起名 `WIP: ...` 也行，重点是入仓）。
   - 未追踪源码（`??` 含 .kt / .java / .xml / .gradle / .md / .ps1 等）→ 评估应 commit 还是
     应 ignore，逐个分类处理。**不允许"它好像没用就放着"**。
3. 如果用户压时间不想 commit，至少 `git stash push -u -m "preflight"`，记下 stash 编号。
4. **完成 commit / stash 之后才能跑那条破坏性命令。**

## ⚠️ 硬规则 — 防把不相关改动夹带进 commit（任何 Codex 接手都必须遵守）

**触发条件**：要执行 `git add <file>` / `git add .` / `git add -A` / `git commit -a` 之前。

**强制流程**（不能跳）：
1. **先跑 `git status`**——看清你**这次**改的文件之前**有没有 M / ?? 标记**（说明上次会话或别处已经留了改动）。
2. **如果目标文件已有 pre-existing 改动**（你来之前它就 M 了）：
   - **不许直接 `git add <file>`**——`git add` 的最小颗粒是整文件，会把别人留的 hunk 一锅端走，
     使本次 commit 包含**你没刻意挑的代码**。
   - 走以下任一路：
     - **路 A（推荐）**：先把 pre-existing 改动 `commit`（`WIP: ...` 也行）或 `stash` 出去，
       让工作区变干净，再做你自己的活，再 commit。
     - **路 B（要硬上）**：把目标 hunks 写到 `.patch` 文件，用 `git apply --cached <patch>`
       只 stage 自己的 hunks。`git add -p` 因为是交互式的不能用。
3. **commit 前必须看一眼 `git diff --cached`**——验证**真正**要进 commit 的 diff 就是你这次干的活，
   多一行少一行都得说得清。
4. **会话结束前要留干净工作区**——所有 M / ?? 要么 commit（含 `WIP: ...`）、要么 stash、要么
   加 .gitignore，**不允许把混合状态文件留给下一次会话**。这是预防"下次 Codex 进来踩这条规则"
   的源头治理。

## ⚠️ 部署与环境修改规则

- **改动环境目标**：当用户要求“修改后端”或“修改网页前端（如 EmbedSpine 等）”时，若未特别指明，默认都是指**修改并部署到 Linux 服务器（192.168.50.1）上的 Docker 化服务**。
  - 需要在本地 Windows 对应仓库中修改代码，如涉及前端需本地执行 `npm run build` 编译。
  - 之后利用 Python SFTP/SSH 脚本（如 paramiko）将修改后的文件（或编译后的 `dist/` 目录）上传并覆盖到 Linux 服务器上的 `/opt/stacks/he-manager/` 对应路径。
  - 上传完成后，需通过 SSH 执行 `cd /opt/stacks/he-manager && docker compose restart` 重新启动容器使其生效。

## 跑 / 构建 / 测试

- **安卓 debug**（日常迭代，快）：`android_preview.bat`（`installDebug`，有 `-Watch`）。
- **安卓 release**（性能/体感检查点，~2min）：`android_release.bat`（`installRelease`，非 debuggable，
  debug 签名可覆盖装，跳 lint 提速）。**判断启动卡/滑动顺必须用 release，debug 不代表性能。**
  Baseline Profile 首启后台装，第二次冷启才是优化后体感。
- 设备：两台连着，脚本只认 `f…` 那台、排除 `hbl…`（`ANDROID_SERIAL` 钉死，防双装）。
- 安卓构建验证：`gradlew.bat :app:assembleDebug -q`（在项目根目录运行，无输出=成功）。

## 改动边界（重要）

- **安卓**：编辑限 `MainActivity`（主浏览 UI）。**别动** `MangaActivity`、`player/PlayerActivity`
  （受保护：自管窗口/视频播放器重写中）。新增**独立新文件/包**可以（如 `audio/`）。

## 架构关键事实（省得重新探索）

- 安卓主列表屏是 **`LibraryScreenV2`**（MainActivity ~1710，`setContent`→432 渲染它）；
  老 `LibraryScreen`(~605) 是**死代码**。V2：整库一次拉入 `allItems` + 客户端筛选/计数/搜索 +
  RecyclerView 自定义 adapter（卡片走 ComposeView，杂图走原生 TileHolder）。
- 安卓 ASMR 播放器在 `audio/` 包：`AsmrPlaybackService`(MediaSessionService) +
  `AudioPlayerViewModel`(MediaController) + `AudioPlayerActivity`+`ui/AudioPlayerScreen`。
  续听复用 `media.progress` 列存累计秒。

## 坑

- **Kotlin 块注释会嵌套**：KDoc 里写 `/audio/*` 这类含 `/*` 的文本会开未闭合嵌套注释，整文件编译失败。

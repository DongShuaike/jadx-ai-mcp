# JADX AI MCP Plugin（云端侧）

本 README 只覆盖 **云端插件**（构建、安装、启动、能力边界）。

端到端流程请先看：
- `<workspace-root>/README.md`

客户端 MCP 桥接请看：
- `<workspace-root>/jadx-mcp-server/README.md`

## 1. 作用与边界

这个项目负责：

- 在 JADX 进程内启动 HTTP 服务
- 暴露 class/method/resource/xrefs 等能力
- 在 remote mode 下使用一次性 token 鉴权

这个项目不负责：

- Claude Code 的 MCP 配置管理
- 本地隧道管理与多环境冲突处理

---

## 2. 构建与安装

### 2.1 直接安装发布版（推荐）

```bash
jadx plugins --install "github:zinja-coder:jadx-ai-mcp"
```

说明：从 GitHub release 安装，不是本地编译产物。

### 2.2 本地构建并安装 JAR

构建：

```bash
cd <workspace-root>/jadx-ai-mcp
mvn -DskipTests package
```

安装：

```bash
JAR_PATH=$(ls -t target/*.jar | head -n 1)
jadx plugins --install-jar "$JAR_PATH"
```

---

## 3. 云端启动模式

### 3.1 GUI 模式（有桌面）

```bash
export JADX_AI_MCP_HOST=127.0.0.1
export JADX_AI_MCP_PORT=8650
export JADX_AI_MCP_REMOTE_MODE=true
jadx-gui /path/to/app.apk
```

### 3.2 Linux 无桌面 GUI 模式（Xvfb）

```bash
xvfb-run -a jadx-gui /path/to/app.apk
```

### 3.3 纯 CLI headless 常驻（推荐）

```bash
java -cp "<jadx-all-jar>:<jadx-ai-mcp-jar>" \
  com.zin.jadxaimcp.cli.HeadlessServerLauncher \
  --port 8650 \
  --remote-mode true \
  /path/to/app.apk
```

关键点：

- 不要用 `jadx` / `jadx-cli` 当常驻入口（会结束后退出）
- `HeadlessServerLauncher` 才是常驻服务入口

---

## 4. 如何找到 `jadx-dev-all.jar`

### 4.1 Homebrew（macOS）

```bash
find "$(brew --prefix jadx)" -type f -name 'jadx*-all.jar'
```

常见结果：

```text
/opt/homebrew/Cellar/jadx/<version>/libexec/lib/jadx-dev-all.jar
```

### 4.2 通用方式（已安装 `jadx` 命令）

```bash
JADX_REAL="$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$(command -v jadx)")"
JADX_HOME="$(cd "$(dirname "$JADX_REAL")/.." && pwd)"
find "$JADX_HOME" -maxdepth 4 -type f -name 'jadx*-all.jar'
```

### 4.3 更稳妥的 classpath 写法（避免版本变更）

```bash
java -cp "$(brew --prefix jadx)/libexec/lib/*:/path/to/jadx-ai-mcp.jar" \
  com.zin.jadxaimcp.cli.HeadlessServerLauncher \
  --port 8650 \
  --remote-mode true \
  /path/to/app.apk
```

---

## 5. 鉴权与日志

启动后会打印一次性 token：

```text
One-time token (shown once): <TOKEN>
Use Authorization header: Bearer <TOKEN>
```

说明：

- 开启 `remote mode` 后，`/health` 也要带 token
- 未带 token 会返回 `401 Unauthorized`
- 当前仓库分支已将 `未授权 /health` 日志降为 `DEBUG`，避免健康探测刷 `WARN`

---

## 6. Headless 能力边界

### 6.1 可用（适合云端常驻分析）

- `all-classes`
- `class-source` / `methods-of-class` / `fields-of-class`
- `method-by-name` / `search-method`
- `manifest` / `strings` / `get-resource-file`
- `main-activity` / `main-application-classes-*`
- `xrefs-to-*`

### 6.2 不可用（会返回 501）

- `current-class`（依赖 GUI 当前焦点）
- `selected-text`（依赖 GUI 文本选择）
- `rename-*`（依赖 GUI 改名流程）
- `debug/*`（依赖 GUI debugger）

结论：

- headless 可稳定提供“按名称/路径检索与反编译结果”
- 不适合“当前 GUI 上下文操作”

---

## 7. 常见问题

### Q1. 云端 `jadx-gui` 报 `HeadlessException`

无桌面会话导致。改用 Xvfb 或 headless launcher。

### Q2. 日志里有 `Can't find 'R' class in app package`

通常是信息级提示，不会阻断服务启动。以 `/health` 与业务接口可用性为准。

### Q3. 启动后看到大量 `Unauthorized` 日志

先确认本地桥接是否传了 token。若仅是健康探测，可使用当前仓库最新代码（`/health` 未授权日志已降级）。

### Q4. `jadx` 扫描完成后进程退出

你用的是 CLI 标准入口，不是常驻入口。请改为 `HeadlessServerLauncher`。

---

## 8. 最小自检

```bash
# 带 token 健康检查
curl -H "Authorization: Bearer <TOKEN>" http://127.0.0.1:8650/health

# 拉取少量类名
curl -H "Authorization: Bearer <TOKEN>" "http://127.0.0.1:8650/all-classes?limit=2"
```

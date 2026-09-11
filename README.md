# 高漫 M8 压感笔适配工具 (Gaomon M8 Tool)

专为 **高漫 M8 (Gaomon M8)** 数位板打造的 Android 压感笔按键适配与深度增强模块，深度内嵌适配平板笔记软件 **StarNote (`com.onyx.galaxy.note`)**，实现压感笔主副侧键在画板中的底层原生级快速切换与原生设置页无缝集成。

---

## ✨ 核心特性

- 🛡️ **100% 免 Root 运行 (Zero Root Dependency)**：
  - 彻底摆脱 `su` 提权、后台常驻守护服务与 `/dev/input/event` 原始字符设备读取；
  - 仅需标准的 LSPosed 模块作用域注入，不申请任何敏感权限，零功耗开销。
- ⚡ **高效按键映射与画板全接管**：
  - **主键**（原下侧键）：默认支持 **按住切橡皮，松开回画笔**（擦除误笔随心自如，松开即刻写字）；
  - **副键**（原上侧键）：默认映射为 **套索**（一键快速切至套索工具，轻松圈选移动排版）；
  - 支持在设置中自由映射为：`按住切橡皮，松开回画笔`、`画笔`、`橡皮擦`、`套索` 或 `无操作`。
- 🎯 **进程级内存挂钩 (LibXposed API 102)**：
  - 遵循现代 LibXposed 规范，直接通过进程内反射与弱引用 View 高速缓存调度 StarNote 画板原生工具控制器（热路径响应延迟仅 1~2ms）；
  - **完全免疫** 工具栏停靠位置（顶部/底部/左侧/右侧/悬浮）、工具栏折叠隐藏及图标顺序变化；
  - 底层精准拦截并静默丢弃硬件广播的 `KEYCODE_E` 字符键，彻底根除在文本框中误输入字母 "e" 的痛点。
- 🎨 **StarNote「手写笔设置」顶层无缝内嵌 UI**：
  - **顶层配置卡片**：直接注入到 StarNote「设置」->「手写笔设置」最上方，与系统原有界面融为一体；
  - **自适应原生主题**：动态采样宿主深棕/深色/浅色配色方案，卡片圆角、文本主副色、分割线与水波纹完全一致；
  - **原生次级选择页面**：点击主/副键平滑切换至与原版「触控笔(双击)」完全一致的次级选择页（顶栏「‹ 返回」、居中标题、圆角卡片、右侧橙色对勾 `✓` 标记与物理返回键拦截）；
  - **实时设备连接状态**：免 Root 调用 Android 标准 `UsbManager`，实时显示当前连接的数位板型号（如 `Gaomon Tablet_M8 (256C:0064)`）。

---

## 🛠️ 运行环境要求

1. **Android 系统**：Android 8.0 及以上（已实测通过 Android 14 / Android 15）；
2. **Xposed 框架**：LSPosed / LSPosed_mod 等支持现代 Zygote 注入的框架；
3. **Root 权限**：**不需要！**（仅需 LSPosed 能够对 StarNote 生效即可）。

---

## 🚀 安装与使用指引

1. **安装 APK**：从 Releases 下载最新版本 APK 并安装到平板；
2. **启用 LSPosed 模块**：
   - 打开 LSPosed 管理器，启用 **高漫 M8 压感笔适配工具**；
   - 作用域勾选 **StarNote (`com.onyx.galaxy.note`)**；
   - 重启 StarNote 应用；
3. **配置与书写**：
   - 打开 StarNote，进入「设置」->「手写笔设置」，在顶层卡片即可直接查验连接状态并配置主键与副键；
   - 进入笔记画板，即刻享受双侧键零延迟切换与橡皮回弹体验！

---

## 🏗️ 架构设计

```mermaid
graph TD
    subgraph 硬件输入层
        Gaomon_Stylus[高漫 M8 压感笔: 主键 / 副键] -->|原生 InputChannel| StarNoteWindow[StarNote 画板窗口]
    end

    subgraph StarNote 进程 (com.onyx.galaxy.note)
        StarNoteWindow -->|dispatchKeyEvent| KeyHook[副键拦截 KEYCODE_E 并调度工具]
        StarNoteWindow -->|dispatchGenericMotionEvent / dispatchTouchEvent| MotionHook[主键监听按压切橡皮/松开回画笔]
        KeyHook & MotionHook --> CanvasExec[直接调用内存工具栏 View 点击]

        subgraph 设置页内嵌模块 (StylusSettingsInjector)
            SettingsStylus[SettingsStylusFragment 手写笔设置] -->|动态视图注入| EmbeddedCard[高漫 M8 顶层卡片]
            EmbeddedCard -->|点击展开| SubPage[原生次级选择页: 顶栏返回 + 橙色对勾]
            SubPage -->|写入| LocalPref[本地私有偏好]
            LocalPref -->|内存单例读取| KeyHook & MotionHook
        end
    end
```

---

## 💻 开发者工具箱 (Developer Tooling)

项目根目录提供了基于 `uv` 的现代化 Python CLI 开发调试工具箱 `tools/dev.py`：

```bash
# 自动检测并无线连接 ADB 设备 (默认: 10.0.0.12:5555)
uv run python tools/dev.py connect

# 扫描并分类平板所有输入设备节点 (高漫 M8 / 触摸屏 / 实体键盘)
uv run python tools/dev.py inspect-inputs

# 一键 Gradle 编译并无线部署推送到平板，自动重启 StarNote
uv run python tools/dev.py build-deploy

# 实时彩色日志监控 (自动过滤 Gaomon M8 与 StarNote 关键日志)
uv run python tools/dev.py logcat

# 查看近期 LSPosed 模块框架运行日志
uv run python tools/dev.py lsp-log

# 快速打开 StarNote 设置页面
uv run python tools/dev.py open-settings

# 模拟发送压感笔侧键广播进行远程测试
uv run python tools/dev.py test-action pen
uv run python tools/dev.py test-action eraser
```

---

## 📦 源码编译构建

本工程使用标准 Gradle 构建系统与 Version Catalog (`gradle/libs.versions.toml`)：

```bash
# 克隆本仓库
git clone https://github.com/jnshuiji/GaomonM8Tool.git
cd GaomonM8Tool

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK
./gradlew assembleRelease
```

编译输出文件位于 `app/build/outputs/apk/` 目录下。

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。

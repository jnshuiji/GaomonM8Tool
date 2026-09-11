# 高漫 M8 压感笔适配工具 (Gaomon M8 Tool)

专为 **高漫 M8 (Gaomon M8)** 数位板打造的 Android 压感笔按键适配与深度增强模块，深度适配平板笔记软件 **StarNote (`com.onyx.galaxy.note`)**，实现压感笔双侧键在画板中的底层原生级快速切换。

---

## ✨ 核心特性

- ⚡ **高效按键映射**：
  - **上侧键**：默认映射为 **套索**（一键快速切至套索工具，轻松圈选移动排版）；
  - **下侧键**：默认支持 **按住切橡皮，松开回画笔**（擦除误笔随心自如，松开即刻写字）；
  - 支持在设置界面自由映射为：`画笔`、`橡皮擦`、`套索`、`按住切橡皮` 或 `无操作`。
- 🎯 **进程级内存挂钩 (LibXposed API 102)**：
  - 遵循现代 LibXposed 规范，直接通过进程内反射与弱引用 View 高速缓存调度 StarNote 画板原生工具控制器（热路径响应延迟仅 1~2ms）；
  - **完全免疫** 工具栏停靠位置（顶部/底部/左侧/右侧/悬浮）、工具栏折叠隐藏及图标顺序变化；
  - 底层精准拦截并静默丢弃上侧键硬件广播的 `KEYCODE_E` 字符键，彻底根除在文本框中误输入字母 "e" 的痛点。
- 🔍 **精准硬件事件解析 (Zero Interference)**：
  - 动态解析 `/proc/bus/input/devices`，精准锁定高漫 M8 专属事件节点（如 `event16`~`event20`）；
  - 彻底过滤物理外接键盘与触摸屏事件，杜绝键盘打字干扰与 CPU 无效空转。
- 🎨 **Material 3 现代规范界面 & 国际化**：
  - 全面采用 Android 推荐的 MVVM 架构（ViewModel + StateFlow + Repository）；
  - 支持中英双语界面（Chinese & English Localization）；
  - 全面适配系统**深色模式**与**浅色模式**，文本与菜单项动态对比度自适应；
  - 完美适配系统状态栏 `WindowInsets`，标题栏绝不被顶部系统栏遮挡；
  - 状态区域清晰展示 Root 授权、硬件连接识别及 LSPosed 激活状态。
- ⌨️ **系统输入增强**：
  - 支持开启“连接数位板时保持虚拟键盘弹出”，解决外接硬件数位板被 Android 识别为实体键盘导致软键盘隐藏的问题。

---

## 🛠️ 运行环境要求

1. **Android 系统**：Android 8.0 及以上（全面适配 Android 14+ / Android 15，实测通过）；
2. **Root 环境**：KernelSU / Magisk / APatch（用于读取 `/dev/input/event` 硬件按键报文）；
3. **Xposed 框架**：LSPosed / LSPosed_mod 等支持现代 Zygote Hook 的框架。

---

## 🚀 安装与使用指引

1. **安装 APK**：从 Releases 下载最新版本 APK 并安装到平板；
2. **启用 LSPosed 模块**：
   - 打开 LSPosed 管理器，启用 **高漫 M8 压感笔适配工具**；
   - 作用域勾选 **StarNote (`com.onyx.galaxy.note`)**；
   - （可选）若 LSPosed 提示重启作用域，请重启 StarNote；
3. **授权与配置**：
   - 打开工具应用，首次运行将在 Root 授权管理器（如 KernelSU）中弹出授权提示，请允许授予 Root 权限；
   - 根据书写习惯微调上侧键与下侧键的动作设置；
4. **尽情书写**：
   - 打开 StarNote 进入手写画板，手写笔双侧键即刻以 0 延迟响应切换！

---

## 🏗️ 架构设计

```mermaid
graph TD
    subgraph 硬件输入层
        Gaomon_Stylus[高漫 M8 压感笔双侧键] -->|/dev/input/event*| InputDaemon
    end

    subgraph 本工具进程 (com.gaomon.m8)
        Config[(按键映射配置)] --> UI[Material 3 配置界面 (MVVM)]
        UI -->|启动守护| DaemonService[前台驱动守护服务]
        DaemonService --> InputDaemon[Root 硬件监听器 (DeviceProcParser 过滤)]
        InputDaemon -->|读取配置并分发指令| CommandSender[IPC 显式广播调度器]
    end

    subgraph StarNote 进程 (com.onyx.galaxy.note)
        CommandSender -->|com.gaomon.m8.ACTION_COMMAND| HookReceiver[Xposed 内部接收器]
        HookReceiver --> NoteHook[NoteScribbleHook 画板调度核心]
        NoteHook -->|View 弱引用缓存快速调度| StarNoteCanvas[StarNote 画板: 画笔 / 橡皮 / 套索]
        KeyFilter[dispatchKeyEvent Hook] -->|拦截丢弃 KEYCODE_E| KeyFilterEnd[消除文本误打 'e']
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

# 一键 Gradle 编译并无线部署推送到平板，自动重启应用
uv run python tools/dev.py build-deploy

# 实时彩色日志监控 (自动过滤 Gaomon M8 与 StarNote 关键日志)
uv run python tools/dev.py logcat

# 快速重启 StarNote 应用以重新加载 Xposed 模块
uv run python tools/dev.py restart-note

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

# 高漫 M8 压感笔适配工具 (Gaomon M8 Tool)

专为 **高漫 M8 (Gaomon M8)** 数位板打造的 Android 压感笔按键适配与深度增强模块，深度适配平板笔记软件 **StarNote（繁星笔记，`com.onyx.galaxy.note`）**，实现压感笔双侧键在画板中的底层原生级快速切换。

---

## ✨ 核心特性

- ⚡ **无缝按住切换**：
  - **上侧键**：默认支持 **按住切套索，松开回画笔**（快速套索圈选排版，松手即恢复书写）；
  - **下侧键**：默认支持 **按住切橡皮，松开回画笔**（擦除误笔随心自如，无需在画笔与橡皮间反复点按）；
  - 支持在设置界面自由映射为：`画笔`、`橡皮擦`、`套索`、`按住切橡皮`、`按住切套索` 或 `无操作`。
- 🎯 **进程级内存挂钩 (LibXposed API 102)**：
  - 遵循现代 LibXposed 规范，直接通过进程内反射调度 StarNote 画板原生工具控制器；
  - **完全免疫** 工具栏停靠位置（顶部/底部/左侧/右侧/悬浮）、工具栏折叠隐藏及图标顺序变化；
  - 底层精准拦截并静默丢弃上侧键硬件广播的 `KEYCODE_E` 字符键，彻底根除在文本框中误输入字母 "e" 的痛点。
- 🎨 **Material 3 现代规范界面**：
  - 全面适配系统**深色模式**与**浅色模式**，文本与菜单项动态对比度自适应；
  - 完美适配系统状态栏 `WindowInsets`，标题栏绝不被顶部系统栏遮挡；
  - 状态区域清晰展示 Root 授权、硬件连接识别及 LSPosed 激活状态。
- ⌨️ **系统输入增强**：
  - 支持开启“连接数位板时保持虚拟键盘弹出”，解决外接硬件数位板被 Android 识别为实体键盘导致软键盘隐藏的问题。

---

## 🛠️ 运行环境要求

1. **Android 系统**：Android 8.0 及以上（已在 Android 14 / Xiaomi HyperOS 平板实测验证）；
2. **Root 环境**：KernelSU / Magisk / APatch（用于读取 `/dev/input/event` 硬件按键报文）；
3. **Xposed 框架**：LSPosed / LSPosed_mod 等支持现代 Zygote Hook 的框架。

---

## 🚀 安装与使用指引

1. **安装 APK**：从 Releases 下载最新版本 APK 并安装到平板；
2. **启用 LSPosed 模块**：
   - 打开 LSPosed 管理器，启用 **高漫 M8 压感笔适配工具**；
   - 作用域勾选 **繁星笔记 (`com.onyx.galaxy.note`)**；
   - （可选）若 LSPosed 提示重启作用域，请重启繁星笔记；
3. **授权与配置**：
   - 打开工具应用，首次运行将在 Root 授权管理器（如 KernelSU）中弹出授权提示，请允许授予 Root 权限；
   - 根据书写习惯微调上侧键与下侧键的动作设置；
4. **尽情书写**：
   - 打开繁星笔记进入笔记画板，手写笔双侧键即刻以 0 延迟响应切换！

---

## 🏗️ 架构设计

```mermaid
graph TD
    subgraph 硬件输入层
        Gaomon_Stylus[高漫 M8 压感笔双侧键] -->|/dev/input/event*| InputDaemon
    end

    subgraph 本工具进程 (com.gaomon.m8)
        Config[(按键映射配置)] --> UI[Material 3 配置界面]
        UI -->|启动守护| DaemonService[前台驱动守护服务]
        DaemonService --> InputDaemon[Root 硬件监听器]
        InputDaemon -->|读取配置并分发指令| CommandSender[IPC 广播调度器]
    end

    subgraph 繁星笔记进程 (com.onyx.galaxy.note)
        CommandSender -->|com.gaomon.m8.ACTION_COMMAND| HookReceiver[Xposed 内部接收器]
        HookReceiver --> NoteHook[NoteScribbleHook 画板调度核心]
        NoteHook -->|原生工具调用| StarNoteCanvas[StarNote 画板: 画笔 / 橡皮 / 套索]
        KeyFilter[dispatchKeyEvent Hook] -->|拦截丢弃 KEYCODE_E| KeyFilterEnd[消除文本误打 'e']
    end
```

---

## 💻 源码编译构建

本工程使用标准 Gradle 构建系统，推荐使用 **Android Studio Ladybug (2024.2+)** 或直接通过命令行构建：

```bash
# 克隆本仓库
git clone https://github.com/<your-username>/GaomonM8Tool.git
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

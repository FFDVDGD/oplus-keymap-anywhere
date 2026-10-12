# 开发与维护

本文面向维护者，记录技术约束、代码职责和构建发布方法。用户使用说明见 [README](README.md)，Agent 工作规范见 [AGENTS](AGENTS.md)。不在这里记录每次调试的过程或设备临时状态。

## 模块边界

- 应用名称为「游戏助手键鼠解锁」，应用 ID 为 `io.github.ffdvdgd.opluskeymap`，源码仓库为 `FFDVDGD/oplus-keymap-anywhere`，项目采用 MIT 许可证。
- 使用现代 libxposed API 102，`module.prop` 声明 `staticScope=true`，作用域仅为 `com.oplus.games`；`KeyboardHook` 再检查包名与主进程。
- 模块不修改系统或游戏 APK，不扩大游戏助手原生的游戏支持范围。模块自己的设置页面由普通 Activity 处理输入，不扩大 Xposed 作用域。
- 不按游戏助手版本号限制启用。目标类、方法和类型兼容时尝试安装，失败时记录错误并保留原有行为；不能仅凭签名一致就宣称新版本已验证。
- 目前验证环境为 OPD2413、Android 16、游戏助手 10.34.21。键盘判断只接受非虚拟的完整键盘，包括 scrcpy UHID；不能把 SDK 注入、手写笔或音量键当作完整键盘。

## 代码职责

Java 文件位于 `src/main/java/io/github/ffdvdgd/opluskeymap/`：

| 文件 | 职责 |
| --- | --- |
| `KeyboardHook` | 模块入口、键盘厂商限制解除、游戏助手进程内的外接键盘适配 |
| `MouseIdleHook` | 游戏名单判定、500 ms 自动松开抑制、输入拦截、触摸释放与复位 |
| `SettingsApp` | LSPosed 服务连接、配置读写、向游戏助手发送操作请求 |
| `SettingsPage` | 设置页共用布局、服务状态和已绑定快捷键处理 |
| `SettingsActivity` | 分组首页、直接按键绑定、复位入口 |
| `GameSelectionActivity` | 应用列表、图标、搜索、已选置顶 |
| `ShortcutKeys` | 键盘/鼠标侧键归一化、按下状态、去重与操作优先级 |

增加设置时沿用这一分工：保存与服务连接归 `SettingsApp`，独立选择页面继承 `SettingsPage`，首页按功能分组。不要把新设置全部堆进游戏选择页。

## 输入行为约定

### 鼠标保持只改变 500 ms 路径

`MouseIdleHook` 记录计时任务启动时的游戏、触点和目标进程的 Kotlin Unit，仅在任务正常延迟完成、仍为同一触点且名单及映射状态满足时跳过自动 UP 和重新 DOWN 标记。异常与取消仍走原流程。

首次按下的 20＋80 ms 任务、边界重定位、其他按键松开保持原样。不能通过取消整套任务来实现保持，否则会破坏后续移动的状态更新。名单默认为空；移出名单、关闭映射/鼠标模式、进入编辑或退出游戏时释放模块保持的触点，不释放已经替换的新触点。

### 快捷键只在指定界面独占

- 手动松开与强制复位分别绑定单个按键，支持键盘和鼠标 BACK/FORWARD 侧键；不检测冲突、不提供侧键预设。
- `ShortcutKeys` 处理两类事件并保留一次按下的所有权，避免重复执行或漏掉导航拦截。长按与抬键不重复执行；同键时强制复位优先。
- 游戏中的拦截入口在原生转发之前，仅在键鼠映射已开启、游戏有效且屏幕可交互未锁定时启用。模块页面单独处理快捷键，其他应用保持原有输入行为。
- 绑定框捕获按下后继续接收抬键，防止记录绑定时误执行新操作。清除绑定不改其他设置，升级保留已有配置。
- 模块清单关闭预测式返回回调，让 Android 16 的 BACK 进入普通按键分发；否则系统可能先触发导航，绕过 Activity 或 Dialog 的按键捕获。

### 强制复位须清理底层残留

首页复位和复位快捷键复用同一操作，不受游戏名单限制。操作先取消待执行任务，异步等待任务完成，再释放映射触点、清理记录及鼠标临时状态；正常鼠标保持不能取消首次按下任务，显式强制复位可以。

游戏助手的接触列表可能已经清空，底层仍保留映射触点；游戏模式外原生包装层也可能丢弃 UP。因此复位通过 OPlus 输入服务释放映射命名空间，而不是只清空 Java 列表。现有实现释放记录中的 `contactId + 11`，并补发 11–29 范围的 UP；不能扩大到正常手指触摸或其他输入来源。

模块到游戏助手的操作请求使用同签名权限保护的定向有序广播。没有接收者返回未响应，执行出错返回失败；只有完成清理后才返回成功。维护时保留这一区分。

## 构建与签名

使用 Windows PowerShell、JDK 和 Android SDK（platform 36 或更新版本及 build-tools）：

```powershell
.\build.ps1
# SDK 或 JDK 不在默认位置时：
.\build.ps1 -Sdk 'D:\Android\Sdk' -Jdk 'D:\Java\jdk-21'
```

SDK 默认位于 `%LOCALAPPDATA%\Android\Sdk`。JDK 使用 `JAVA_HOME`，未设置时使用 Android Studio 的 JBR。脚本下载 libxposed API 102.0.0 用于编译，将 service/interface 102.0.0 打包，并附带依赖许可证。

输出为 `dist/oplus-keymap-anywhere.apk`，缓存与本地签名密钥位于 `.amp/in/`。发布必须沿用同一密钥，避免无法覆盖安装；密钥不进入 Git。脚本清理旧编译产物，先签名和校验临时 APK，再更新输出，避免把失败构建当成新包。

## 验证与发布

基础验证为构建、签名、设置读写与 Hook 载入；界面变化检查实机渲染。用户已确认鼠标保持、手动松开和强制复位正常，游戏内验证由用户完成。调试材料只保存在被忽略的 `.amp/in/`，不写进公开使用文档。

发布前核对清单中的 versionCode/versionName、中文名称和签名；按功能拆分可编译的提交。标签格式为 `<versionCode>-<versionName>`，1.3 为 `4-1.3`；Release 标题和说明使用中文，附带 APK。发布后核对标签指向、Release 附件和下载文件校验值。

LSPosed 官方目录材料为合规应用 ID、README、SUMMARY 与 APK Release。目录仓库应只承载说明与发行，不转移整个源码仓库；收录申请入口为 [Xposed-Modules-Repo/submission](https://github.com/Xposed-Modules-Repo/submission)，需要独立授权，不能把「资料已准备」写成「已经收录」。

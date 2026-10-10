# OPlus KeyMap Anywhere

Unlock OPlus Game Assistant's built-in keyboard/mouse mapping for non-official full keyboards, including scrcpy UHID. Requires modern libxposed API 102. Currently supports **Game Assistant 10.34.21 (versionCode 500340021) only**; other versions are left unchanged.

[Source](https://github.com/FFDVDGD/oplus-keymap-anywhere) · [Download APK](https://github.com/FFDVDGD/oplus-keymap-anywhere/releases/latest) · [Report an issue](https://github.com/FFDVDGD/oplus-keymap-anywhere/issues)

LSPosed 模块，解除 OPlus / 一加游戏助手内置「键鼠映射」的官方键盘厂商限制。
只作用于 `com.oplus.games`，不修改系统 APK、游戏 APK 或系统输入服务。
使用现代 libxposed API 102，以 `staticScope=true` 将可选作用域限制为游戏助手；不能额外勾选系统框架或其他应用。模块仍保留包名、主进程和游戏助手版本检查。
公开版包名为 `io.github.ffdvdgd.opluskeymap`，不是 OPlus、OnePlus 或 OPPO 的官方产品。

## 支持范围

- 针对 OPD2413 上游戏助手 **10.34.21，versionCode 500340021** 制作。
- 原版要求厂商 ID 为 `0x22D9` 或云端白名单中的值；模块让其他厂商的完整外接键盘也通过同一检查。
- USB、蓝牙和 UHID 键盘必须被 Android 识别为非虚拟的完整键盘。
- 此系统把 scrcpy UHID 标记为内部设备。模块只在游戏助手进程内，将非虚拟的完整键盘视为外接键盘；不修改系统或游戏进程中的判断。其他内置完整键盘也会通过。
- 手写笔、音量键、SDK 虚拟键盘和无键盘连接的状态不会被当作完整键盘。
- 不解除游戏支持列表等其他限制，不保证所有游戏或所有硬件都可用。
- 游戏助手升级到其他版本时自动不应用修改；需要重新确认混淆类和方法，不能直接放宽版本检查。

## 安装和撤销

1. 设备须已有 root 和支持现代 API 102 的 LSPosed。下载发布页 APK，或自行构建 `dist/oplus-keymap-anywhere.apk`。
2. 在 LSPosed 的模块列表中启用「OPlus KeyMap Anywhere」，作用域选择「游戏助手」`com.oplus.games`。静态作用域限制可选目标，不代替模块启用开关，也不强制用户保持勾选。
3. 结束并重新启动游戏助手进程，再进入游戏侧栏的「键鼠映射」。不要结束正在进行且未保存的游戏。
4. 连接普通键盘，使用原有界面配置映射。

早期测试包为 `dev.gamehub.keyboard`，公开版不会覆盖它。**先停用旧模块，再启用公开版，不要同时启用两者。** 后续公开版更新使用同一包名和签名。

撤销：在 LSPosed 中禁用本模块，结束并重新启动游戏助手进程；也可卸载模块。
不需要恢复或重签系统 APK。不要同时使用其他修改同一键盘判断的模块。

## scrcpy

使用 scrcpy 的物理 HID 模式：

```powershell
scrcpy --keyboard=uhid --mouse=uhid
```

仅需键盘时可省略 `--mouse=uhid`。多个设备连接时用 `-s <设备序列号>` 指定目标。
UHID 支持 ADB 无线连接。首次使用可在 Android 的「实体键盘」设置中选择布局。

默认 `--keyboard=sdk` 是 API 事件注入，不会连接一块完整外接键盘，本模块不将它伪装为硬件。
也不保证默认 SDK 鼠标注入符合游戏助手的鼠标映射要求。
参考 [scrcpy 官方键盘文档](https://github.com/Genymobile/scrcpy/blob/master/doc/keyboard.md)。

## 实机验证

OPD2413，Android 16 / OPlus 16.1，LSPosed IT 2.2.1-it (7920)，scrcpy 5.0.1，无线 ADB：

- 公开包 `io.github.ffdvdgd.opluskeymap` 已实机安装、启用并在游戏助手中载入；旧测试模块已停用，公开包的输入过滤器正常启用。
- 模块 1.1 被识别为 API 102；作用域页提示「模块声明了静态作用域」，只显示游戏助手。新版已实际载入，scrcpy UHID 映射覆盖层和输入过滤器仍正常启用。
- scrcpy UHID 的厂商 ID 为 `0`、`KeyboardType: 2`、`IsExternal: false`；只去掉厂商限制仍会被拒绝。
- 加上游戏助手进程内的外接判断适配后，《绝区零》内可打开原生键鼠映射编辑面板。
- 启用映射后，系统显示 `InputFilterEnabled: true`，游戏助手收到 `KEYCODE_MINUS` / `KEYCODE_EQUALS` 的按下和松开事件。
- scrcpy 断开后，游戏助手自动卸载输入过滤器，恢复 `InputFilterEnabled: false`；仍连接的手写笔没有被当作完整键盘。
- 尚未验证游戏内具体键位到触控的完整链路，也未实测 USB / 蓝牙实体键盘。

## 构建

Windows PowerShell，需 JDK、Android SDK（含 platform 36 或更新版、build-tools）和网络：

```powershell
.\build.ps1
# 非默认安装位置：
.\build.ps1 -Sdk 'D:\Android\Sdk' -Jdk 'D:\Java\jdk-21'
```

默认使用 `%LOCALAPPDATA%\Android\Sdk` 和 `JAVA_HOME`；未设置 `JAVA_HOME` 时使用 Android Studio 的 JBR。
libxposed API 102.0.0 从 Maven Central 下载，仅用于编译，不打包进 APK。
构建缓存和本地开发签名密钥位于 `.amp/in/`，APK 位于 `dist/`。
自行构建会生成本地开发签名；不同签名的 APK 不能直接覆盖安装。本项目发布版使用持续保留的同一签名密钥，密钥不上传到 GitHub。

## 隐私和许可

模块不申请额外 Android 权限，不发起网络请求，不保存或上报按键。模块只记录载入、目标版本和错误日志；游戏助手自身的网络和日志行为不受本模块控制。

本项目采用 [MIT License](https://github.com/FFDVDGD/oplus-keymap-anywhere/blob/main/LICENSE)，允许商用、修改和再分发，须保留版权与许可声明；软件按「原样」提供，不作担保。第三方依赖仍遵循各自的许可证。

## LSPosed 官方模块目录准备

当前仅发布于作者仓库，**尚未提交或获准收录**到 [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/submission)。已准备好归属合规的应用 ID、`README.md`、`SUMMARY` 和带 APK 的版本发布。

- 申请标题：`[submission] io.github.ffdvdgd.opluskeymap`。
- 官方目录仓库名：`Xposed-Modules-Repo/io.github.ffdvdgd.opluskeymap`，由官方申请流程创建并邀请作者维护。
- 目录仓库描述：`OPlus KeyMap Anywhere`；主页/支持链接使用本项目 GitHub 地址。
- 目录仓库只复制 `README.md` 和 `SUMMARY`，不要把源码仓库整体转移过去。
- 发布标题：`1.1`；标签：`2-1.1`（versionCode-versionName）；附 APK，发布说明使用对应的 [changelog](https://github.com/FFDVDGD/oplus-keymap-anywhere/blob/main/CHANGELOG.md)。

公开要求允许现代 libxposed API 102 和静态作用域，不要求单独的图标或许可证文件。资料符合提交格式不等于承诺收录；兼容范围和未验证项仍须保持上述说明。

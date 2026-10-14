# 更新日志

## 游戏助手键鼠解锁 1.5

- 适配游戏助手 10.42.5，恢复键盘解锁、保持鼠标按下、手动松开及强制复位功能。
- 保留游戏助手 10.34.21 的兼容，升级保留已有游戏名单与快捷键设置。

## 游戏助手键鼠解锁 1.4

- 设置界面改用 Miuix，支持系统深浅色主题和宽屏双栏布局，保留应用图标、搜索、已选置顶及按键绑定。
- 启用系统预测性返回手势；设置项改称「快捷键」，复位用途说明移入按钮。
- 优化应用列表打开速度，减少切换页面时的卡顿。

## 游戏助手键鼠解锁 1.3

- 应用名称改为「游戏助手键鼠解锁」，增加键盘与鼠标组合的桌面图标。
- 为所选游戏取消停止鼠标移动 500 毫秒后的自动松开。
- 新增手动松开和强制复位快捷键，直接按下键盘按键或鼠标侧键即可绑定，也可清除；同键时优先强制复位。
- 绑定键仅在模块界面和启用键鼠映射的游戏中独占使用，其他应用不受影响；长按不重复执行，支持 Android 16 的返回键绑定。
- 首页新增「强制松开并复位所有触摸」，用于恢复卡住的键鼠映射触点，不更改设置。
- 设置首页按功能分组；游戏选择支持应用图标、搜索和已选置顶。
- 精简界面说明，名单和快捷键修改立即生效。

## 1.2

- Remove the exact Game Assistant version-code gate; attempt the hooks on other versions when the target class and method signature still match.
- Leave keyboard behavior unchanged if the target class or method is missing, or the keyboard check no longer returns boolean. Do not scan for replacement methods.
- Keep the package, main-process, static-scope and attached-full-keyboard restrictions.
- Game Assistant 10.34.21 remains the only tested version; matching an obfuscated method signature does not guarantee unchanged semantics on future versions.

## 1.1 — first public release

- Remove the official-keyboard vendor restriction from OPlus Game Assistant 10.34.21 (versionCode 500340021).
- Accept attached full keyboards, including scrcpy UHID devices that OPlus classifies as internal.
- Use modern libxposed API 102 and a static scope limited to `com.oplus.games`.
- Keep package, main-process, target-version and full-keyboard checks. Unknown Game Assistant versions are left unchanged.
- Publish under the owned application ID `io.github.ffdvdgd.opluskeymap`.

### Upgrading from the private test build

The previous test package was `dev.gamehub.keyboard`. The public package has a different application ID and does not replace it. Disable the old module before enabling this release; do not enable both at once.

### Compatibility

Tested on OPD2413 with Android 16 / OPlus 16.1, LSPosed IT 2.2.1-it (7920), and scrcpy 5.0.1 UHID over wireless ADB. The mapping editor and input filter have been exercised. USB/Bluetooth hardware and complete game-specific key-to-touch mappings have not yet been validated.

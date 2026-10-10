# Changelog

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

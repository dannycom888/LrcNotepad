# LRC记事本 v1.0.0 构建报告

构建日期：2026-11-17（America/New_York）
规格来源：用户 2026-11-17 要求——像记事本一样轻量的、能打开 .lrc 文件的软件；点开看全文、能改、能存，不要播放/跟随高亮那套重功能。

## 产物

| 项 | 值 |
|---|---|
| APK（工程根目录副本） | `~/workspace/lrc-notepad/LrcNotepad-1.0.0.apk` |
| APK（Gradle 原始输出） | `app/build/outputs/apk/release/app-release.apk` |
| 大小 | 657,683 字节（约 0.63 MB，对比 LRC 编辑器 9.5 MB） |
| SHA-256 | `2e1a5858cbd05c5d0a713099a4b01d10ab9b39f8d890c2d09fb775d1faab7a30` |
| 包名 | `com.dannycom888.lrcnotepad` |
| 应用名 | LRC记事本 |
| versionName / versionCode | 1.0.0 / 1 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| 签名 | 与 LRC 编辑器 / MySing / MyLRC Auto 同一把共享 keystore；`apksigner verify` 通过（v2），证书 SHA-256 `1336ee0aefc229c3f0e6c025060ec0267825dbfc83f8799c3981dd74ea2befcc`，与 LrcEditor-1.0.0.apk 的证书逐字一致 |
| 权限清单 | **零权限**（`aapt dump permissions` 无任何条目）：无 INTERNET、无其他权限 |
| Release | https://github.com/dannycom888/LrcNotepad/releases/tag/v1.0.0 |

## 单元测试

`:app:testDebugUnitTest` —— **9 个测试，0 失败，0 错误，0 跳过**（数自 test-results XML：TextCodecTest 5 + DocumentStateTest 4）。

| 测试类 | 数 | 覆盖 |
|---|---|---|
| TextCodecTest | 5 | 中文 + LRC 行/逐字标签经编码→解码往返不变且二次往返逐字节一致；GBK 字节自动回退 GBK 解码；合法 UTF-8 中文不误判 GBK；UTF-8 BOM 剥除；空文件 |
| DocumentStateTest | 4 | 新建/刚打开为未修改；编辑变脏、保存变净；改回原文自动变净；新建清空且变净 |

非 ASCII 端到端：测试用真实中文歌名/歌词（含繁简混排、中文标点）做完整往返，标签 `[mm:ss.xx]` / `<mm:ss.xx>` 原样保留。

## 功能对照规格

- ✅ 纯记事本：单个 Activity、原生 Android Views、零第三方运行时依赖（无 Compose、无 AndroidX 运行时库），所以 APK 只有 0.63 MB。
- ✅ 打开 .lrc 看**原始全文**（含全部时间标签）在可编辑等宽文本区里。
- ✅ 「打开」按钮走 SAF `ACTION_OPEN_DOCUMENT`（text/* 等）；同时注册 VIEW/EDIT intent-filter（content/file scheme、text/*、.lrc/.txt 路径、octet-stream 兜底），在文件管理器/下载里点 .lrc 可选本 App；content:// 尽量取持久读写权限。
- ✅ .txt 同样可打开（纯文本）。
- ✅ 「保存」写回同一 URI（UTF-8）；「另存为」走 `ACTION_CREATE_DOCUMENT`；「新建」清空开新页。
- ✅ 未保存提醒：标题文件名后带 ●；新建/打开其他文件/返回退出时弹窗（保存 / 放弃修改 / 取消）。
- ✅ 读取编码：严格 UTF-8 优先，解码失败自动回退 GBK，打开时提示；保存统一 UTF-8。
- ✅ 简单查找：「查找」输入框 + 「下一个」循环定位选中。
- ✅ 标题栏显示当前文件名。
- ✅ 按规格**没有**：播放、MP3、歌词高亮、文件夹扫描、联网。

## 已知边界

- 本次为构建环境内的静态与单测验证；真机（Note10+）上从文件管理器点 .lrc 的关联弹窗、SAF 打开/保存仍需用户装机实测。不同文件管理器对无标准 MIME 的 .lrc 处理不一，已用 text/*、路径后缀、octet-stream 三组 intent-filter 尽量覆盖。

## 本软件由 Muse 协助制作

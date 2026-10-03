# fcitx5（Android 拼音输入法）

一个从零手写的 Android 拼音输入法：词库是 fcitx5 官方的，词频是 RIME 八股文的，代码
全是 Kotlin，没有 C++、没有 NDK、没有插件系统。给想看懂并改得动自己输入法的人用。

## 安装

前置条件：Android 8.0（API 26）及以上，允许安装未知来源应用；启用与切换需要 root 或
`adb shell` 权限。

1. 从 CI 构建的 Artifacts 里下载 APK，或自己构建（见「构建」）。
2. 安装：`adb install -r app-debug.apk`
3. 启用并设为默认（root）：

   ```shell
   su -c 'ime enable sumicya.fcitx5/sumicya.fcitx5.ImeService'
   su -c 'ime set    sumicya.fcitx5/sumicya.fcitx5.ImeService'
   ```

   这两条命令会修改系统的输入法设置，属于系统级改动。

装过旧版 `sumicya.fcitx5` 的要先卸载：签名不同，无法覆盖安装。

## 使用

- 上滑按键输入次要符号，没有长按。
- 空格横滑移动光标，退格左滑删整个词。
- `☰` 打开面板：剪贴板历史、常用短语、表情。
- `中` / `英` 切换中英；密码、邮箱、网址和数字输入框自动进英文。
- 打字时按 `1`–`9` 直选候选，`空格` 上屏第一个候选，`回车` 上屏原始拼音。
- 可以只打声母：`yh` 出银行、樱花，`wm` 出我们，也能混着打（`wohh`）。全拼永远排在
  简拼前面。
- `ui` / `iu` / `un` / `ong` 这些俗写会当成 `wei` / `you` / `wen` / `weng`。

## 适用环境与限制

- Android 8.0（API 26）及以上，全 ABI（纯 Kotlin，无 native 库）。
- 用户数据都在 `/data/data/sumicya.fcitx5/files`：`user.txt` 是选词学习记录，`clip.txt`
  是剪贴板历史，`phrases.txt` 是收藏的短语；删掉即重置。
- 剪贴板历史不限条数，键盘每次弹出时收录系统剪贴板，永久保留。
- 排序来自语料统计，语料里没有的词需要你选过才会靠前；整句语言模型还没做。
- 词库与词表在构建时下载，不进 git。

## 构建

需要 `python3`、`zstd`（脚本会在缺 `zstd` 时自行用 pip 安装 `zstandard`）和 JDK 21。

```shell
./gradlew :app:assembleDebug     # 缺词库时自动跑 scripts/build_dict.py，只需联网一次
python3 scripts/build_dict.py    # 只重新生成词库资源
```

版本号形如 `26.10.2.58`：年月日取 Asia/Shanghai，末段是正式工作流的 CI 运行序号，
由 CI 在一次运行内算好后传给 Gradle。不带参数本地构建得到 `0.0.0.dev`，仅用于开发。

## 相关文档

- [排序与数据来源](docs/ranking.md)：词库、词频、简繁对照表的来源与许可，以及排序怎么定
- 词库：[fcitx/libime](https://github.com/fcitx/libime) 的 `dict_sc.txt`
- 词频：[rime-essay](https://github.com/rime/rime-essay) 的 `essay.txt`
- 简繁对照：[OpenCC](https://github.com/BYVoid/OpenCC) 的 `STCharacters.txt`

## 许可

代码 LGPL-2.1-or-later。因为打包了 LGPL-3.0 的语料表，装到设备上的 APK 整体按
LGPL-3.0 分发；用 `--no-essay` 构建时不引入 LGPL-3.0 数据。上游 fcitx5 与
fcitx5-android 的许可与致谢一并保留。

# fcitx5-android（纯 Kotlin 重写版）

[Fcitx5](https://github.com/fcitx/fcitx5) 的官方拼音词库 + 一个从零手写的 Android 输入法。
**没有 C++，没有 NDK，没有 JNI，没有插件系统**：整个 APK 是 Kotlin，只有一个模块、零第三方依赖。

## 为什么重写

上游 `fcitx5-android` 把整套 fcitx5 C++ 框架（fcitx5 / libime / chinese-addons / lua）编进 APK，
再套上 Room、Paging、Navigation、图片裁剪、插件 IPC 等一堆包装层，app 侧 319 个 Kotlin 文件。
打字真正需要的只是：**拼音 → 候选词 → 上屏**。其余都是可以删的代码。

## 现状

| | |
|---|---|
| 输入法 | 全拼拼音（30 万词条）、英文、数字与符号层、中英切换 |
| 键盘 | 手写 Canvas 四行 QWERTY，**上滑**出次要符号（无长按）、空格横滑移动光标、退格左滑删词 |
| 候选 | 音节切分 + 词库匹配 + 词频排序，**选过的词自动置顶**（用户词频学习） |
| 中文标点 | `,` `.` `?` `!` `:` `;` `(` `)` 自动转全角 |
| 体积 | 单词典数据（构建期生成，5.4 MB），无 native 库 |
| 平台 | minSdk 26（Android 8+），全 ABI（纯 Kotlin 零成本） |
| 包名 | `sumicya.fcitx5` |

还没做：剪贴板、简繁切换、表情面板、用户自造词、模糊音、双拼。

## 构建

词库不进 git：它是从官方源下载的派生数据，构建期生成。

```shell
./gradlew :app:assembleDebug     # 缺词库时会自动跑 scripts/build_dict.py（只需联网一次）
```

手动生成词库（CI 里是单独一步，方便看日志）：

```shell
python3 scripts/build_dict.py    # 下载 dict_sc.txt → app/src/main/assets/pinyin.dict
```

`python3` 与 `zstd` 需要有（转换脚本在无 `zstd` 时会 pip 装 `zstandard`）。
单元测试会读打包好的词库，校验格式、偏移与排序，坏了直接构建失败。

### 安装

```shell
su -c 'ime enable sumicya.fcitx5/sumicya.fcitx5.ImeService'
su -c 'ime set    sumicya.fcitx5/sumicya.fcitx5.ImeService'
```

（装过旧版 `sumicya.fcitx5` 的要先卸载，签名不同。）

## 词库打哪来的

`https://download.fcitx-im.org/data/dict-20260907.tar.zst` → `dict_sc.txt`，
许可 **LGPL-2.1-or-later**（见 [fcitx/libime 的 REUSE.toml](https://github.com/fcitx/libime/blob/main/REUSE.toml)），
与本仓库一致。下载带 SHA256 校验，校验值取自 libime 的 `data/CMakeLists.txt`。

## 排序为什么是这样（已知短板）

官方词库**没有词频**：30 万条里只有 1498 条带一个相对字频，其余全是 0，
而那个字频列本身也只在一部分情况下靠谱（它能正确排出 的 > 得 > 地 > 底，
却也声称 倭 比 涡 常见）。所以基础排序用两个弱信号取平均：

1. 一个字出现在多少词条里，以及**它出现在的词条本身有多常用**
   （只数词条会把「尼」排到「你」前面——尼散布在地名里，你只在你好/你们里）；
2. 词条自带的相对字频，且只在为负值时采信（正值是多音字把整字频算到罕见读音上的产物，比如老挝的挝）。

因此基础排序只保证「最该出现的字排在前三」，**真正让排序变准的是用户词频学习**：
你选过的词会置顶，选得越多越靠前，存到 `/data/data/sumicya.fcitx5/files/user.txt`。

## 许可

LGPL-2.1-or-later，与上游 `fcitx5-android` 一致。

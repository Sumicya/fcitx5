# fcitx5-android（纯 Kotlin 重写版）

[Fcitx5](https://github.com/fcitx/fcitx5) 的官方拼音词库 + RIME 的真实语料词频 + 一个从零手写的 Android 输入法。
**没有 C++，没有 NDK，没有 JNI，没有插件系统**：整个 APK 是 Kotlin，只有一个模块、零第三方依赖。

## 为什么重写

上游 `fcitx5-android` 把整套 fcitx5 C++ 框架（fcitx5 / libime / chinese-addons / lua）编进 APK，
再套上 Room、Paging、Navigation、图片裁剪、插件 IPC 等一堆包装层，app 侧 319 个 Kotlin 文件。
打字真正需要的只是：**拼音 → 候选词 → 上屏**。其余都是可以删的代码。

## 现状

| | |
|---|---|
| 输入法 | 全拼拼音（30 万词条，21 万条有真实词频）、英文、数字与符号层、中英切换 |
| 键盘 | 手写 Canvas 四行 QWERTY，**上滑**出次要符号（无长按）、空格横滑移动光标、退格左滑删词 |
| 候选 | 音节切分 + 词库匹配 + 语料词频排序，**选过的词自动置顶**（用户词频学习） |
| ☰ 面板 | 剪贴板历史（50 条）+ 常用短语 + 表情（8 类约 400 个） |
| 自造词 | 剪贴板条目点 ★ 存成短语，之后打它的拼音就能上屏（按字表注音，词库里没有也算） |
| 简繁 | 按字转繁体（OpenCC 对照表，4012 字），候选与上屏都转 |
| 中文标点 | `,` `.` `?` `!` `:` `;` `(` `)` 自动转全角 |
| 体积 | 单词典数据（构建期生成，5.4 MB），无 native 库 |
| 平台 | minSdk 26（Android 8+），全 ABI（纯 Kotlin 零成本） |
| 包名 | `sumicya.fcitx5` |

还没做：模糊音、双拼、滑行输入（上滑只用来出符号）。

## 构建

词库不进 git：它是从官方源下载的派生数据，构建期生成。

```shell
./gradlew :app:assembleDebug     # 缺词库时会自动跑 scripts/build_dict.py（只需联网一次）
```

手动生成词库（CI 里是单独一步，方便看日志）：

```shell
python3 scripts/build_dict.py    # 下载词库与词频 → app/src/main/assets/{pinyin.dict,st.txt}
python3 scripts/build_dict.py --no-essay   # 不下载语料，只靠启发式排序
```

`python3` 与 `zstd` 需要有（转换脚本在无 `zstd` 时会 pip 装 `zstandard`）。
单元测试会读打包好的词库，校验格式、偏移、排序与简繁表，坏了直接构建失败。

### 安装

```shell
su -c 'ime enable sumicya.fcitx5/sumicya.fcitx5.ImeService'
su -c 'ime set    sumicya.fcitx5/sumicya.fcitx5.ImeService'
```

（装过旧版 `sumicya.fcitx5` 的要先卸载，签名不同。）

## 数据打哪来的

| 用途 | 来源 | 许可 |
|---|---|---|
| 词库（30 万词条） | `download.fcitx-im.org/data/dict-20260907.tar.zst` → `dict_sc.txt` | LGPL-2.1-or-later |
| 词频（37 万词） | [rime-essay](https://github.com/rime/rime-essay) `essay.txt`，按 commit `054920d` 钉死 | LGPL-3.0 |
| 简繁对照 | OpenCC `STCharacters.txt`，已内置在 `scripts/` | Apache-2.0 |
| 注音（自造词用） | 上面那份词库里的单字条目，构建时抽出 | LGPL-2.1-or-later |

词库带 SHA256 校验（取自 libime 的 `data/CMakeLists.txt`）；语料没有公布校验值，
所以用 commit 钉住 URL 而不是校验和。

## 排序是怎么定的

官方词库**没有词频**：30 万条里只有 1498 条带一个相对字频，其余全是 0，
所以词序来自 RIME 八股文的真实语料计数——它是繁体写的，先经 OpenCC 表折回简体再计数
（这一步有损：干/乾/幹 会并成一个，计数相加）。

- **有计数的词**（21 万条，70%）排在前面，按 `log(计数)` 归一化：的 482 万、我 119 万、你 97 万、好 37 万。
- **没有计数的词**退回原来的启发式（字在多少词条里出现 + 它出现在的词条有多常用 + 词库自带的负字频），
  整体压在语料带之下，所以任何有语料证据的词都赢过没有的。
- 用户词频学习仍然在最上面：选过的词置顶，存到 `/data/data/sumicya.fcitx5/files/user.txt`。

已知短板：剩下 30% 没有语料证据的词内部顺序只是启发式；
整句层面（wo men → 我们 vs 卧扪）还没有语言模型（官方 `lm_sc.arpa` 是 LGPL-2.1-or-later，可以后加）。

## 许可

代码 LGPL-2.1-or-later。因为打包了 LGPL-3.0 的语料表，**装到手机上的这个 APK 整体按 LGPL-3.0 分发**
（代码是 "or later"，允许升到 v3；词库与语料本身是独立作品，不受影响）。
不下载语料构建（`--no-essay`）时不引入 LGPL-3.0 数据。

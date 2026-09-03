# MapReset

Minecraft **Paper 1.21.11 / Java 21** 向けの、ミニゲームワールドをMinecraftの
region fileから高速に初期状態へ戻すプラグインです。

ゲーム中のBlockイベント、Entityイベント、Chunk差分、Snapshotは一切追跡しません。
ゲーム終了後に対象Worldをsave・unloadし、templateと異なるregion artifactだけを置換してから
Worldをreloadします。そのため、データパックの`/setblock`・`/fill`・`/clone`、流体、
Explosion、FallingBlock、矢、他プラグインによるBlock変更も、保存されたWorldデータを
templateへ戻すことで復元できます。

> [!WARNING]
> 必ず先にテストサーバーでtemplate作成・restore・server再起動後の動作を確認してください。
> data pack custom dimensionはPaper 1.21.11に固定したNMS reloadを使用します。

## 目次

- [必要環境とインストール](#必要環境とインストール)
- [最短セットアップ](#最短セットアップ)
- [ゲーム終了時の流れ](#ゲーム終了時の流れ)
- [コマンド](#コマンド)
- [管理者通知・権限・メッセージ](#管理者通知権限メッセージ)
- [Templateと復元対象](#templateと復元対象)
- [通常Worldとcustom dimension](#通常worldとcustom-dimension)
- [設定](#設定)
- [安全性・backup・crash recovery](#安全性backupcrash-recovery)
- [運用・トラブルシューティング](#運用トラブルシューティング)
- [非対応事項と制限](#非対応事項と制限)

## 必要環境とインストール

| 項目 | 要件 |
| --- | --- |
| Minecraft / Paper | **1.21.11** |
| Java | **21** |
| プラグイン | `MapReset-1.0.0.jar` |
| 対応サーバー | Paper専用。Folia非対応 |

1. [MapReset-1.0.0.jar](build/libs/MapReset-1.0.0.jar) をサーバーの`plugins/`へ配置します。
2. サーバーを完全に再起動します。
3. 初回起動後に生成される`plugins/MapReset/config.yml`を確認します。
4. 通知を受ける管理者へtagを付けます。

```mcfunction
tag <player> add developer
```

既定の通知tagは`developer`です。`notifications.admin-tag`で任意のscoreboard tagに変更できます。
これはpermissionとは独立しています。

アップデート時に既存の`config.yml`と`messages.yml`は上書きされません。新しい設定や色付き
メッセージを導入する場合は、[config.yml](src/main/resources/config.yml)と
[messages.yml](src/main/resources/messages.yml)を既存ファイルへマージして、`/mapreset reload`を
実行してください。

> Paper開発用bundleの依存座標には`SNAPSHOT`が含まれますが、これはビルド時だけに使用します。
> 配布プラグインのバージョンは正式版の`1.0.0`です。

## 最短セットアップ

以下では`battle_world`を`battle`として管理します。対象Worldは先にロード済みでなければなりません。

```mcfunction
# 1. Map登録。Map名とWorld名は英数字、.、_、-のみで1～64文字。
mapreset create battle battle_world

# 2. 全Playerが対象Worldの外にいることを確認して、現在の正常状態をtemplate化。
mapreset template create battle

# 3. 内容を確認。
mapreset template info battle
mapreset status battle
```

作成後の概略構成です。

```text
plugins/MapReset/
├─ config.yml
├─ messages.yml
├─ maps.yml
├─ templates/
│  └─ battle/
│     ├─ metadata.json
│     ├─ region/
│     ├─ entities/
│     └─ poi/
├─ backups/
└─ transactions/
```

`template create`は対象Worldを`save(true)`してunloadした後にコピーします。コピー中にゲームを
続けないでください。Worldが正常にreloadされてから`READY`になります。

## ゲーム終了時の流れ

ゲーム本体やデータパックは、MapResetの内部実装を知る必要がありません。参加PlayerをLobby等へ
移動してから、最後にrestoreコマンドを実行します。

```text
ゲーム進行
  ↓
全Playerを対象Worldの外へ移動
  ↓
/mapreset restore battle
  ↓
World.save(true) → unload → 比較/backup/置換 → reload
  ↓
READY
```

データパックの例です。Lobby dimension名・座標は環境に合わせてください。

```mcfunction
# Battle Royale終了処理の例
execute as @a[tag=br_player] in <lobby_dimension> run tp @s <x> <y> <z>
scoreboard players set #game state 0

# Playerがbattle_worldに残っていないことを前提に実行
mapreset restore battle
```

対象WorldにPlayerが1人でも残っている場合、MapResetは**save、unload、ファイル変更を一切開始せず**
restoreを中止します。Playerをteleport・kickすることはありません。

## コマンド

すべての管理コマンドには`mapreset.admin`が必要です。Consoleは常に実行できます。

| コマンド | 説明 |
| --- | --- |
| `/mapreset create <map> <world>` | Mapを登録します。Worldを作成・ロードはしません。 |
| `/mapreset delete <map>` | Map登録だけを削除します。template、backup、transactionは安全のため残します。 |
| `/mapreset template create <map>` | 現在の対象Worldを正常状態としてtemplate化します。Player残留時は拒否します。 |
| `/mapreset template info <map>` | manifestを非同期で読み、作成日時とartifact数を表示します。 |
| `/mapreset restore <map>` | templateとの差分artifactだけを復元します。 |
| `/mapreset status <map>` | Map state、現在phase/進捗、template有無、直近restore、ERROR理由を表示します。 |
| `/mapreset list` | 登録済みMapとstateを一覧表示します。 |
| `/mapreset reload` | `config.yml`と`messages.yml`を再読込します。restore/template実行中は拒否します。 |

Tab Completeはサブコマンド、`template create|info`、登録済みMap、ロード済みWorldを補完します。

### State

| State | 意味 |
| --- | --- |
| `READY` | 操作可能です。 |
| `CREATING_TEMPLATE` | template作成中です。対象Mapへの別操作は拒否されます。 |
| `RESTORING` | restore中です。対象Worldには入らないでください。 |
| `ERROR` | 安全に完了できませんでした。`/mapreset status <map>`の理由を確認してください。 |

`ERROR`の主な例は、World未ロード、Player残留、template/manifest不正、save/unload失敗、copy失敗、
unexpected World load、reload失敗、reload属性不一致です。

## 管理者通知・権限・メッセージ

### 通知先

通知先はonlineで`notifications.admin-tag`を持つPlayerだけです。既定値は`developer`です。

```mcfunction
tag Sider add developer
tag Sider remove developer
```

実行者はtagを持たなくても、queued、save、unload、reload、完了、エラーの直接フィードバックを
受け取ります。通知tag保持者は開始・中止・完了・エラー・recovery・進捗を受け取ります。

| 色 | 意味 |
| --- | --- |
| Green | 成功・完了 |
| Yellow / Gold | 待機、進捗、注意が必要な警告 |
| Red | 実行拒否、ERROR |

`logging.console-detail: true`では、ConsoleへWorld profile、phase、比較件数、copy、reload診断と
例外スタックトレースを出力します。`logging.verbose: true`はartifact単位のログも出すため、
大規模Worldでは通常falseのままにしてください。

### `messages.yml`

メッセージはAdventure **MiniMessage**形式です。

```yaml
completed: '<prefix><green><bold><map> の復元が完了しました</bold></green><gray> — Changed: <white><changed></white>'
error: '<prefix><red><bold><map> は ERROR です</bold></red><gray>: <white><reason>'
```

主なplaceholderは次のとおりです。

| Placeholder | 用途 |
| --- | --- |
| `<prefix>` | `notifications.prefix` |
| `<map>` / `<world>` | Map名 / World名 |
| `<reason>` | 拒否・ERROR理由 |
| `<phase>` / `<current>` / `<total>` | restore進捗 |
| `<changed>` / `<restored>` / `<deleted>` / `<copied>` / `<duration>` | restore結果 |
| `<files>` / `<created>` | template情報 |
| `<state>` / `<result>` / `<template>` / `<error>` | status情報 |

構文エラーを含む`messages.yml`や範囲外の設定値は`/mapreset reload`で拒否され、既に動作中の
設定・メッセージは維持されます。

## Templateと復元対象

### 管理するartifact

MapResetは各dimensionの次だけを管理します。

| ディレクトリ | 対象 | 用途 |
| --- | --- | --- |
| `region/` | `r.<x>.<z>.mca`, `c.<x>.<z>.mcc` | Block、BlockEntity、chunk data |
| `entities/` | `r.<x>.<z>.mca`, `c.<x>.<z>.mcc` | Mob、Item、Arrow、FallingBlock等のEntity |
| `poi/` | `r.<x>.<z>.mca`, `c.<x>.<z>.mcc` | POI情報 |

`.mcc`は外部chunk dataであり、`.mca`から参照される可能性があるためセットで扱います。
template manifestの`metadata.json`には各artifactの相対path、file size、streaming SHA-256、
作成日時、World名、dimension keyが保存されます。

### 復元の判定

比較は`EXACT`固定です。sizeが異なるartifactは変更候補になり、同sizeでもSHA-256を計算して
完全一致を確認します。mtimeだけで未変更とは判断しません。

| Template | 現在のWorld | 動作 |
| --- | --- | --- |
| あり | 同じhash | 何もしない |
| あり | 不一致 | templateからatomic置換 |
| あり | なし | templateから復元 |
| なし | あり | 新規artifactとして削除 |

復元対象外は次です。

- `level.dat`
- `data/`、datapack
- `playerdata/`
- `stats/`
- `advancements/`
- `uid.dat`、`session.lock`、その他のWorld root file

従って、MapResetはplayer inventory、player stats、advancement、scoreboard、datapack自体を
rollbackしません。また、template作成時点で存在したMob、Item、Arrow、FallingBlock等は
Entity regionを戻すことで再出現します。

### Templateを更新する

マップ編集後に新しい正常状態を採用する場合は、全Playerを対象Worldの外へ出してから同じコマンドを
再実行します。

```mcfunction
mapreset template create battle
```

既存templateはstaging directoryを経由して安全に置換します。失敗した場合、従来templateを保持します。

## 通常Worldとcustom dimension

### 通常World

通常のWorldは、元の`WorldCreator`設定をコピーしてPaper/Bukkitの`Bukkit.createWorld`でreloadします。
seed、environment、generator、biome provider、World path、min/max heightをreload後に照合します。

### Data pack custom dimension

`World.Environment.CUSTOM`はPaper 1.21.11の公開`Bukkit.createWorld`経路では動的reloadできず、
`Illegal dimension (CUSTOM)`となります。MapResetはこの限定的な問題に対応するため、
**動作確認済みのPaper 1.21.11専用NMS loader**を使用します。

- 既にdata packへ登録された`LevelStem`を解決してreloadします。
- 新しいdimensionを作成しません。
- `level.dat`、datapack、dimension settingsを上書きしません。
- custom dimensionの実データpath（通常は`<world root>/dimensions/<namespace>/<path>/`）の
  `region/`、`entities/`、`poi/`をtemplate化・復元します。
- 変更対象artifactは常にbackupされます。

> [!CAUTION]
> このloaderはMinecraft/Paper **1.21.11専用**です。別のPaper buildやMinecraft versionへの
> 互換性を保証しません。アップデート前に必ずstagingサーバーでtemplate/restoreを検証し、
> `backups/`を保管してください。

設定`custom-dimension.nms-reload: false`の場合、custom dimensionのtemplate/restoreはsave・
unload・ファイル変更の前に停止します。`custom-dimension.enabled: false`はcustom dimensionを
管理対象外にします。

reload後はname、key、root path、seed、environment、height、generator、biome providerを照合します。
既定の`error-on-verification-failure: true`では不一致を`ERROR`とします。明示的にfalseにした場合は
`SUCCESS_WITH_RELOAD_WARNING`と通知・Console warningを残すため、手動確認なしに次ゲームを開始
しないでください。

## 設定

編集後は、処理中でないことを確認してから実行します。

```mcfunction
mapreset reload
```

### `io`

| Key | 既定値 | 説明 |
| --- | ---: | --- |
| `io.parallelism` | `1` | scan/hash/copy/backup/journal用の共有worker数。1～16。通常は1、十分なI/O余力がある場合のみ2以上。 |
| `io.buffer-size-kib` | `1024` | copy用buffer。64～16384 KiB。 |
| `io.queue-limit` | `256` | 共有I/O executorの待機task上限。1～100000。 |
| `comparison.mode` | `EXACT` | 現在は`EXACT`のみ。変更しないでください。 |
| `comparison.hash-buffer-kib` | `1024` | SHA-256計算用buffer。64～16384 KiB。 |

### `restore`

| Key | 既定値 | 説明 |
| --- | ---: | --- |
| `restore.backup-before-replace` | `false` | 通常Worldでも変更/削除対象artifactをbackupします。 |
| `restore.backup-directory` | `backups` | plugin data folder内のbackup保存先。外部pathは拒否されます。 |
| `restore.restore-region` | `true` | `region/`の復元を有効化。通常は変更しないでください。 |
| `restore.restore-entities` | `true` | `entities/`の復元を有効化。 |
| `restore.restore-poi` | `true` | `poi/`の復元を有効化。 |
| `restore.allow-concurrent-maps` | `false` | trueで別Mapのrestore/templateを並行許可します。I/O負荷を理解した場合だけ有効化してください。 |

### `custom-dimension`

| Key | 既定値 | 説明 |
| --- | ---: | --- |
| `enabled` | `true` | CUSTOM Worldを管理するか。 |
| `require-backup` | `true` | custom dimensionの変更対象artifactを必ずbackupします。運用上trueを維持してください。 |
| `verify-reload` | `true` | reload後のWorld属性照合を行います。 |
| `error-on-verification-failure` | `true` | 属性不一致をERRORとして停止します。falseは警告付き成功です。 |
| `nms-reload` | `true` | Paper 1.21.11専用NMS custom reloadを使います。falseならファイル変更前に拒否します。 |

### `notifications` と `logging`

| Key | 既定値 | 説明 |
| --- | ---: | --- |
| `notifications.admin-tag` | `developer` | 通知対象のscoreboard tag。 |
| `notifications.prefix` | `[MapReset] ` | メッセージの`<prefix>`値。MiniMessageも使用できます。 |
| `notifications.console` | `true` | lifecycle通知をConsoleへ出力します。 |
| `notifications.progress` | `true` | 進捗通知を有効化します。 |
| `notifications.progress-interval-seconds` | `5` | 進捗通知間隔。1～3600秒。 |
| `notifications.events.*` | `true` | `start`、`aborted`、`completed`、`error`、`recovery`、`progress`、`warning`を個別に制御します。 |
| `logging.console-detail` | `true` | phase、path、比較結果、reload診断をConsoleへ出力します。 |
| `logging.verbose` | `false` | artifact単位のrestoreログを追加します。 |

### `recovery` と `lifecycle`

| Key | 既定値 | 説明 |
| --- | ---: | --- |
| `recovery.auto-resume` | `false` | 未完了transaction検出時に、通常のWorld/Player安全チェックを通した後でrestoreを再試行します。 |
| `recovery.transaction-retention-days` | `30` | 将来のtransaction cleanup用に保持されている値です。現在、成功transactionは直ちに削除され、未完了transactionは手動recoveryまで保持されます。 |
| `lifecycle.retry-timeout-seconds` | `10` | Paperが安全なWorld lifecycle pointになるまで待つ最大秒数。1～120。 |

### 推奨設定

大規模Worldの安全重視設定です。

```yaml
io:
  parallelism: 1
  buffer-size-kib: 1024

restore:
  backup-before-replace: true
  allow-concurrent-maps: false

custom-dimension:
  require-backup: true
  verify-reload: true
  error-on-verification-failure: true
  nms-reload: true

logging:
  console-detail: true
  verbose: false
```

## 安全性・backup・crash recovery

### World lifecycleとthread境界

| Main thread | 専用I/O executor |
| --- | --- |
| World取得、Player残留確認、`World.save(true)`、unload、reload、通知 | manifest preflight、scan、SHA-256、backup、copy、delete、transaction journal、template copy |

unloadが拒否または失敗した場合、MapResetはregion artifactを書き換えません。復元中に対象Worldが
予期せずloadされた場合もERRORにします。Mapごとのsession lockと、既定ではglobal lockにより、
同一Mapまたは複数Mapの同時処理を防ぎます。

### Atomic replace

template artifactはdestinationと同じdirectoryの一時ファイルへコピーし、`FileChannel.force(true)`後に
`ATOMIC_MOVE`で置換します。filesystemがatomic moveを提供しない場合だけ通常replaceへfallbackします。
一時copy中に失敗した場合は一時ファイルを削除します。

### Backup

backupが有効な場合、変更・削除される**現在のartifactだけ**を次へ保存します。

```text
plugins/MapReset/backups/<map>/<timestamp>/<region|entities|poi>/...
```

templateにあるが現在Worldにないartifactは、復元前にbackupする元ファイルがないためbackupには
含まれません。template作成や`/mapreset create`だけではbackupは生成されません。

### Crash recovery

restore開始時に次のtransactionを永続化し、file操作ごとに進捗を更新します。

```text
plugins/MapReset/transactions/<map>.json
plugins/MapReset/transactions/<map>.journal
```

server crash、plugin disable、copy失敗などでrestoreが途中停止した場合、次回起動時にMapはERRORとして
警告されます。完了扱いにはなりません。

通常の復旧手順:

1. 対象Worldがロード済みであることを確認します。
2. 全Playerを対象Worldの外へ出します。
3. `/mapreset status <map>`で理由を確認します。
4. `/mapreset restore <map>`を再実行します。

restoreは常にtemplateとの差分へ収束するため、再実行で安全に復旧できます。`auto-resume: true`は
起動時に対象Worldが空であることを運用で保証できる場合だけ使用してください。

## 運用・トラブルシューティング

| 表示・状態 | 原因 / 対処 |
| --- | --- |
| `Players are still inside ...` | 全PlayerをLobby等へ移動してから再実行します。 |
| `Target world is not loaded` | そのWorld/dimensionを通常どおりロードしてから実行します。MapResetはWorldを新規作成しません。 |
| `Template manifest is missing/invalid` | `templates/<map>/metadata.json`とartifactを確認し、必要ならtemplateを作り直します。 |
| `Map is busy` | templateまたはrestoreの完了/ERRORを待ちます。`status`でphaseを確認します。 |
| `custom-dimension.nms-reload is disabled` | `custom-dimension.nms-reload: true`を確認し、`/mapreset reload`を実行します。 |
| `reload verification failed` | Console detailを確認し、backupを保全します。custom dimensionではdata pack/Paper 1.21.11の組合せを確認します。 |
| `Incomplete restore transaction detected` | 上記のcrash recovery手順でrestoreを再実行します。 |
| templateに`metadata.json`しかない | 対象dimensionのmanaged directoryにartifactがなかったか、古いJARでtemplateを作成した可能性があります。現行JARでtemplateを作り直し、Consoleの`Template snapshot copied N managed artifacts`でNを確認します。 |

`/mapreset status <map>`はERROR理由と最終restore metricsを表示します。Consoleの詳細ログには、通常World rootと
custom dimension artifact path、比較数、hash数、変更数、NMS reload診断が記録されます。

## 非対応事項と制限

MapResetはWorld region dataの復元プラグインです。次は行いません。

- Player inventory / Ender Chestのrollback
- Player stats、advancement、recipe、scoreboardのrollback
- datapack、function、resource packのrollback
- `level.dat`の毎回復元
- ゲーム中のBlock/Entityイベント追跡
- Block単位Snapshot、Chunk比較、dirty tracking
- Playerの強制teleport / kick
- Folia対応
- Paper 1.21.11以外でのcustom dimension NMS reload保証

この設計ではゲーム中のTPS負荷をほぼゼロに保ち、終了後のI/O時間とディスク容量を使って確実に
Worldデータを戻します。

## 開発者向けビルド

Gradle Wrapperを含みます。Java 21で実行してください。

```powershell
.\gradlew.bat build
```

成果物は`build/libs/MapReset-1.0.0.jar`です。`BUILD SUCCESSFUL`になることを確認してから
サーバーへ配置してください。

# todo-app 要件まとめ

## 概要

Clojure 製のスタンドアロン TODO アプリ。起動時の引数でモードを選択し、CUI・GUI・REST サーバーの3種類のインターフェースで動作する。REST モード時はブラウザから操作できる ClojureScript 製 Web フロントエンドも提供する。

---

## 技術仕様

| 項目 | 内容 |
|------|------|
| 言語 | Clojure 1.11.1 |
| ビルドツール | Leiningen |
| バージョン | 1.0.0 |
| エントリポイント | `todo-app.core/-main` |
| ライセンス | MIT |

### 依存ライブラリ

| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| seesaw | 1.5.0 | GUI（Swing ラッパー） |
| ring/ring-core | 1.11.0 | HTTP サーバー基盤 |
| ring/ring-jetty-adapter | 1.11.0 | 組み込み Jetty |
| compojure | 1.7.1 | ルーティング |
| ring/ring-json | 0.5.1 | JSON ミドルウェア |
| cheshire | 5.12.0 | JSON 変換 |
| ring/ring-mock | 0.4.0（dev） | HTTP テスト用モックリクエスト |
| lein-cljsbuild | 1.1.8（dev plugin） | ClojureScript ビルド |
| org.clojure/clojurescript | 1.11.60（dev） | ClojureScript コンパイラ |
| reagent | 1.2.0（dev） | ClojureScript 向け React ラッパー |
| cljsjs/react | 17.0.2-0（dev） | React 本体（Reagent 1.x で必須） |
| cljsjs/react-dom | 17.0.2-0（dev） | React DOM（Reagent 1.x で必須） |

> **注意**: `dev` 印のライブラリは `:dev` プロファイル専用。uberjar には含まれない。

---

## ファイル構成

### Clojure ソース（`src/clj/todo_app/`）

| ファイル | 名前空間 | 責務 |
|----------|----------|------|
| `status.clj` | `todo-app.status` | ステータス定数・メッセージ生成 |
| `todo.clj` | `todo-app.todo` | ドメインロジック（純粋関数のみ） |
| `store.clj` | `todo-app.store` | 永続化（EDN ファイル読み書き・初期化） |
| `util.clj` | `todo-app.util` | 共通ユーティリティ（parse-num / now / select-data） |
| `cui.clj` | `todo-app.cui` | CUI インターフェース（コマンド解釈・表示） |
| `gui.clj` | `todo-app.gui` | GUI インターフェース（Seesaw） |
| `server.clj` | `todo-app.server` | REST サーバー（Ring / Compojure） |
| `core.clj` | `todo-app.core` | エントリポイント（モード振り分けのみ） |

### ClojureScript ソース

| ファイル | 用途 |
|----------|------|
| `src/cljs/vanilla/todo_app/core.cljs` | vanilla 版 Web フロントエンド（素の ClojureScript） |
| `src/cljs/reagent/todo_app/core.cljs` | Reagent 版 Web フロントエンド（React ラッパー） |

### 静的リソース（`resources/public/`）

| ファイル | 用途 |
|----------|------|
| `index.html` | vanilla 版エントリ HTML（`/js/vanilla/main.js` を読み込む） |
| `reagent/index.html` | Reagent 版エントリ HTML（`/js/reagent/main.js` を読み込む） |
| `style.css` | スタイルシート（両版共用） |
| `js/vanilla/main.js` | vanilla 版コンパイル済み JS |
| `js/reagent/main.js` | Reagent 版コンパイル済み JS |

### テスト（`test/clj/todo_app/`）

| ファイル | テスト対象 |
|----------|------------|
| `status_test.clj` | ステータス定数・`get-key-by-label` / `get-num-by-label` |
| `todo_test.clj` | `add-todo` / `update-status` / `delete-todo` |
| `store_test.clj` | `initialize-store!` / `load-todos` / `save-todos!` |
| `util_test.clj` | `parse-num` / `now` / `select-data` |
| `cui_test.clj` | `parse-command` / `format-*` / `execute-command!` |
| `server_test.clj` | REST エンドポイント（全ルート・各ステータスコード） |

```bash
lein test   # 全テスト実行（68 tests / 142 assertions）
```

---

## 起動方法

### 1. REPL から `-main` を呼び出す

```bash
lein repl
```

```clojure
(-main "1")   ;; Repl CUI モード
(-main "2")   ;; GUI モード
(-main "3")   ;; REST サーバーモード
(-main "0" "add" "買い物をする")   ;; Simple CUI モード
```

### 2. `lein run` で直接実行する

```bash
lein run 1              # Repl CUI モード（対話ループ）
lein run 2              # GUI モード
lein run 3              # REST サーバーモード（http://localhost:3000）
lein run 0 add 買い物をする   # Simple CUI モード
lein run 0 list
lein run 0 update 1 3
lein run 0 delete 2
```

### 3. uberjar をビルドして実行する

```bash
# JS を先にビルド（uberjar に同梱するため）
lein cljsbuild once vanilla-release
lein cljsbuild once reagent-release

# uberjar ビルド
lein uberjar

# 実行
java -jar target/uberjar/todo-app-1.0.0-standalone.jar 1
java -jar target/uberjar/todo-app-1.0.0-standalone.jar 3
```

### 4. ClojureScript をビルドする

```bash
# 開発ビルド（:simple 最適化・デバッグしやすい）
lein cljsbuild once vanilla-dev
lein cljsbuild once reagent-dev

# 本番ビルド（:advanced 最適化・ファイルサイズ最小）
lein cljsbuild once vanilla-release
lein cljsbuild once reagent-release
```

### 起動方法による違い

| 方法 | 用途 | 特徴 |
|------|------|------|
| REPL | 開発・動作確認 | 関数を直接呼べる |
| `lein run` | 開発・簡易実行 | Leiningen が必要 |
| uberjar | 配布・本番利用 | JRE があればどこでも動く |

---

## `-main` の動作モード

```
0: Simple CUI  引数としてコマンドを受け取り1回実行して終了
1: Repl CUI    プロンプト "todo> " を表示し対話ループ。exit / quit または Ctrl+D で終了
2: GUI         Seesaw（Swing）ウィンドウを表示
3: REST        Jetty を起動（http://localhost:3000）。ブラウザから操作可能
```

### CUI コマンド処理のパイプライン

`run-command` は以下の3段階で処理する：

```
validate-input   入力の検証（parse-command の結果に :data-atom を付加）
     ↓
execute-command! コマンドの実行（データ操作・永続化）
     ↓
format-result    結果の文字列化（表示用フォーマット）
     ↓
println          出力
```

---

## REST API 仕様

ベース URL: `http://localhost:3000`

| メソッド | パス | 説明 | 成功時 | エラー時 |
|---------|------|------|--------|---------|
| `GET` | `/todos` | 全件取得（`?status=N` でフィルタ可） | 200 | — |
| `GET` | `/todos/:id` | 1件取得 | 200 | 404 |
| `POST` | `/todos` | タスク追加（body: `{"title": "..."}` ） | 201 | 400 |
| `PATCH` | `/todos/:id` | ステータス更新（body: `{"status": N}` ） | 200 | 400 / 404 |
| `DELETE` | `/todos/:id` | タスク削除 | 204 | 404 |
| `GET` | `/` | vanilla 版 `index.html` を返す | 200 | — |
| `GET` | `/vanilla` | vanilla 版 `index.html` を返す | 200 | — |
| `GET` | `/reagent` | Reagent 版 `reagent/index.html` を返す | 200 | — |

### PATCH のバリデーション

- `status` が存在しないキー → 400
- `status = 0`（未着手）への変更 → 400
- 指定 ID が存在しない → 404

---

## データ仕様

### データファイルの位置

`store.clj` の `data-file`（`delay` による遅延評価）で決定される：

| 実行方法 | データファイルの位置 |
|----------|---------------------|
| `lein run` / REPL | `./log/todo.edn`（カレントディレクトリ相対） |
| uberjar（`java -jar`） | JAR ファイルと同じディレクトリの `log/todo.edn` |

JAR 実行時はリソース URL のプロトコルが `jar:` かどうかで判定し、JAR の絶対パスを `java.net.URI` 経由で取得する（URL エンコード対策）。

### 初期化

`initialize-store!` が起動時に1回だけ呼ばれ、ファイル・ディレクトリが存在しない場合に自動生成する。

### データ構造

- **保存形式**: EDN ファイル
- **データ構造**:
  ```edn
  {:next-id 4
   :todos [{:id 1
            :title "タスク名"
            :status :doing
            :start-at "26-03-07 20:12"
            :end-at nil}
           ...]}
  ```

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `:next-id` | 整数 | 次に割り当てる ID（自動インクリメント） |
| `:id` | 整数 | タスク ID |
| `:title` | 文字列 | タスク名 |
| `:status` | キーワード | ステータス（`:todo` / `:doing` / `:pending` / `:done`） |
| `:start-at` | 文字列 / nil | 進行中にした日時（`"yy-MM-dd HH:mm"` 形式） |
| `:end-at` | 文字列 / nil | 完了した日時（`"yy-MM-dd HH:mm"` 形式） |

---

## 機能要件

### CUI コマンド

| コマンド | 引数 | 動作 |
|----------|------|------|
| `add <タスク名>` | タスク名（スペース区切り可） | タスクを追加する（初期ステータス: 未着手） |
| `list [番号]` | ステータス番号（省略可） | タスク一覧を表示する。番号指定でフィルタリング |
| `update <id> <番号>` | タスク ID・ステータス番号 | ステータスを更新する |
| `delete <id>` | タスク ID（整数） | タスクを削除する |
| `help` / 不明コマンド | なし | ヘルプを表示する |
| `exit` / `quit` | なし | アプリを終了する（REPL モードのみ） |

### ステータス一覧

| 番号 | キーワード | 表示名 | `list` フィルタ | `update` 指定 |
|------|-----------|--------|----------------|--------------:|
| 0 | `:todo` | 未着手 | 可 | 不可 |
| 1 | `:doing` | 進行中 | 可 | 可 |
| 2 | `:pending` | 保留 | 可 | 可 |
| 3 | `:done` | 完了 | 可 | 可 |

> **注意**: `update` コマンドおよび REST PATCH で `:todo`（0: 未着手）への変更は不可。

### ステータス遷移による日時の自動設定

| 変更後のステータス | `start-at` | `end-at` |
|------------------|-----------|---------:|
| `:doing`（進行中） | 現在日時を設定 | `nil` にリセット |
| `:done`（完了） | 変更なし | 現在日時を設定 |
| `:pending`（保留） | 変更なし | `nil` にリセット |

---

## CUI 表示仕様

```
[　]   1. 未着手のタスク [  ]
[進]   2. 進行中のタスク [開始:26-03-07 20:12  ]
[完]   3. 完了したタスク [開始:26-03-07 20:00  終了:26-03-07 21:00]
```

- ステータスの先頭1文字を `[　]` 内に表示（未着手は全角スペース）
- ID は3桁右詰め
- タスクが空の場合: `タスクはありません。` と表示

---

## エラーハンドリング

| 状況 | 対応 |
|------|------|
| `add` でタスク名が空 | エラーメッセージ表示 |
| `update` / `delete` で ID が数値でない | エラーメッセージ表示 |
| `update` で無効なステータス番号（0 または範囲外） | エラーメッセージ表示 |
| 指定 ID のタスクが存在しない | エラーメッセージ表示（CUI・REST 共通） |
| EDN ファイルが壊れている | 初期データ `{:next-id 1 :todos []}` で代替 |

---

## アーキテクチャ上の設計判断

- **アトムキャッシュ**: 起動時に1回だけ `load-todos` し、以降はメモリ上のアトムを参照
- **書き込みは都度**: 各コマンド実行後に即時 EDN ファイルへ保存（異常終了時のデータ保護）
- **保存 → アトム更新の順**: 保存失敗時にアトムが汚染されない
- **`todo.clj` は純粋関数のみ**: 副作用（現在時刻取得・ファイル操作）は `cui.clj` / `store.clj` に分離
- **`datetime` の依存性注入**: `now` は入口で1回だけ呼び出し、文字列として流す。テスト時は固定文字列を渡せる
- **`parse-command` は純粋関数**: `validate-input` のラッパーとして分離することで、`data-atom` なしに単体テスト可能
- **`make-handler [app-state]` クロージャ**: REST サーバーのハンドラにアトムをクロージャで渡すことでグローバル状態を排除し、テスト時に独立した状態を注入可能
- **`util/select-data` による共通フィルタ**: CUI / GUI / REST の3箇所で使われるフィルタ処理を1関数に集約
- **ベクターアクセスに `get` を使用**: `(get stat-keys n)` は範囲外・nil で例外を投げず `nil` を返す
- **ClojureScript を `:dev` プロファイルに分離**: ClojureScript の依存する `instaparse` が Java 21 の `java.util.SequencedCollection` を参照するため、Java 17 で uberjar を動かすには `:dev` プロファイルへの分離が必要
- **Reagent 版と vanilla 版を URL で切り分け**: `/vanilla`（素の ClojureScript）と `/reagent`（Reagent）で別フロントエンドを提供。REST API は共用

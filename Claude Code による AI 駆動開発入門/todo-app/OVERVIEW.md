# todo-app 要件まとめ

## 概要

Clojure 製のスタンドアロン CUI（コマンドライン）TODO アプリ。
引数なしで起動すると対話型 REPL モード、引数ありで起動するとシンプルモード（1コマンド実行）で動作する。

---

## 技術仕様

| 項目 | 内容 |
|------|------|
| 言語 | Clojure 1.11.1 |
| ビルドツール | Leiningen |
| バージョン | 1.0.0 |
| エントリポイント | `todo-app.core/-main` |
| ライセンス | MIT |

---

## ファイル構成

### ソース

| ファイル | 名前空間 | 責務 |
|----------|----------|------|
| `src/todo_app/status.clj` | `todo-app.status` | ステータス定数・メッセージ生成 |
| `src/todo_app/todo.clj` | `todo-app.todo` | ドメインロジック（純粋関数のみ） |
| `src/todo_app/store.clj` | `todo-app.store` | 永続化（ファイル読み書き・初期化） |
| `src/todo_app/core.clj` | `todo-app.core` | UI・エントリポイント（コマンド解釈・表示） |

### テスト

| ファイル | テスト対象 |
|----------|------------|
| `test/todo_app/status_test.clj` | ステータス定数の値 |
| `test/todo_app/todo_test.clj` | `add-todo` / `update-status` / `delete-todo` |
| `test/todo_app/core_test.clj` | `parse-id` / `parse-command` / `format-*` / `execute-command!` |
| `test/todo_app/store_test.clj` | `initialize-store!` / `load-todos` / `save-todos!` |

```bash
lein test   # 全テスト実行
```

---

## 起動方法

### 1. REPL から `-main` を呼び出す

```bash
lein repl
```

```clojure
(-main)                        ;; 引数なし → REPL モード（対話ループ）
(-main "add" "買い物をする")   ;; シンプルモード（1コマンド実行）
(-main "list")
(-main "update" "1" "3")
(-main "delete" "2")
```

### 2. `lein run` で直接実行する

```bash
lein run                        # REPL モード
lein run add 買い物をする        # シンプルモード
lein run list
lein run update 1 3
lein run delete 2
```

### 3. uberjar をビルドして実行する

```bash
# ビルド
lein uberjar

# 実行
java -jar target/uberjar/todo-app-1.0.0-standalone.jar          # REPL モード
java -jar target/uberjar/todo-app-1.0.0-standalone.jar add 買い物をする
java -jar target/uberjar/todo-app-1.0.0-standalone.jar list
```

### 起動方法による違い

| 方法 | 用途 | 特徴 |
|------|------|------|
| REPL | 開発・動作確認 | 関数を直接呼べる。状態を保ちながら繰り返し試せる |
| `lein run` | 開発・簡易実行 | ソースから直接実行。Leiningen が必要 |
| uberjar | 配布・本番利用 | Leiningen 不要。JRE があればどこでも動く |

---

## `-main` の動作モード

```
引数なし → REPL モード（対話ループ）
  - プロンプト "todo> " を表示し入力を待つ
  - exit / quit または Ctrl+D で終了

引数あり → シンプルモード（1コマンド実行して終了）
  - 第1引数をコマンド、第2引数以降をオプションとして処理
```

### コマンド処理のパイプライン

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

`parse-command` は純粋関数。`validate-input` はそのラッパーで `:data-atom` を付加する。

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

> **注意**: `update` コマンドで `:todo`（0: 未着手）への変更は不可。

### ステータス遷移による日時の自動設定

| 変更後のステータス | `start-at` | `end-at` |
|------------------|-----------|---------:|
| `:doing`（進行中） | 現在日時を設定 | `nil` にリセット |
| `:done`（完了） | 変更なし | 現在日時を設定 |
| `:pending`（保留） | 変更なし | `nil` にリセット |

---

## 表示仕様

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
| 指定 ID のタスクが存在しない | エラーメッセージ表示 |
| EDN ファイルが壊れている | 初期データ `{:next-id 1 :todos []}` で代替 |

---

## アーキテクチャ上の設計判断

- **アトムキャッシュ**: 起動時に1回だけ `load-todos` し、以降はメモリ上のアトムを参照
- **書き込みは都度**: 各コマンド実行後に即時 EDN ファイルへ保存（異常終了時のデータ保護）
- **保存 → アトム更新の順**: 保存失敗時にアトムが汚染されない
- **`todo.clj` は純粋関数のみ**: 副作用（現在時刻取得・ファイル操作）は `core.clj` / `store.clj` に分離
- **`datetime` の依存性注入**: `now` は `-main` の入口で1回だけ呼び出し、文字列として `run-command` → `parse-command` と流す。テスト時は固定文字列を渡せる
- **`parse-command` は純粋関数**: `validate-input` のラッパーとして分離することで、`data-atom` なしに単体テスト可能
- **ベクターアクセスに `get` を使用**: `(get stat-keys n)` は範囲外・nil で例外を投げず `nil` を返す（`parse-command` / `update-status`）

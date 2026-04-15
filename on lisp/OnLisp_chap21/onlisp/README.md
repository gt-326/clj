# On Lisp — 第21章 マルチプロセス（Multiple Processes）Clojure 実装

Paul Graham 著『On Lisp』第21章の協調マルチタスク（コルーチン）を Clojure で実装・探究したプロジェクト。
プロセス構造体と継続関数を組み合わせ、スレッドを使わない優先度ベースのプロセス切り替えを実現する。

## テスト

```
lein test
;; Ran 43 tests containing 134 assertions. 0 failures, 0 errors.
```

---

## プロジェクト構成

```
src/onlisp/
├── common/
│   └── util.clj                     # CPS マクロ共通ライブラリ（=defn / =bind など）
└── chap21/
    ├── common/
    │   ├── layer1.clj               # コアプロセス管理（Proc / グローバル状態 / スケジューラ）
    │   ├── layer2.clj               # プロセス制御（fork / wait / yield / halt / kill）
    │   └── layer3.clj               # プログラムマクロ（program / program-cps）
    ├── black_board.clj              # ブラックボード（claim / unclaim / check）
    ├── fnc.clj                      # 応用例・通常 defn 版（pedestrian / ballet / barbarians）
    ├── cps.clj                      # 応用例・CPS 版（=defn を使用）
    └── core.clj                     # REPL 動作確認用

test/onlisp/
├── chap21/
│   ├── common/
│   │   ├── layer1_test.clj          # コアプロセス管理のテスト
│   │   └── layer2_test.clj          # プロセス制御のテスト
│   ├── black_board_test.clj         # ブラックボードのテスト
│   ├── fnc_test.clj                 # 応用例・通常 defn 版の統合テスト
│   └── cps_test.clj                 # 応用例・CPS 版の統合テスト
└── common/
    └── util_test.clj                # CPS マクロのテスト
```

---

## アーキテクチャ

chap21 の協調マルチタスクは、スレッドやプリエンプションを使わない。
代わりに「継続を明示的に保存してキューに積み、優先度順に取り出して実行する」仕組みで実現する。

```
プロセス構造体 Proc
  :pri    — 優先度（数値が大きいほど高優先）
  :state  — 継続関数（fn [v] ...）
  :wait   — 待機条件（nil または (fn [] bool)）

PROCS (atom)  ← 実行待ちプロセスのキュー
PROC  (atom)  ← 現在実行中のプロセス

[実行サイクル]
pick-process
  └─ most-urgent-process で :wait が真かつ :pri 最大のプロセスを選択
  └─ PROCS から取り出して (:state proc) を呼ぶ
       └─ yield / wait の呼び出し
            └─ arbitrator で現プロセスを PROCS に戻して pick-process へ
```

---

## コアプロセス管理 — `common/layer1.clj`

### グローバル状態

```clojure
(def HALT  (gensym))       ; システム停止シグナル
(def PROCS (atom nil))     ; 実行待ちプロセスキュー
(def PROC  (atom nil))     ; 現在実行中のプロセス
```

### `most-urgent-process` — スケジューリング

`:wait` 条件が真かつ `:pri` が最大のプロセスを線形探索で選択する。
条件を満たすプロセスがなければ `DEFAULT-PROC`（REPL ループ）を返す。

### `pick-process` — プロセスの取り出しと実行

```clojure
(defn pick-process []
  (multiple-value-bind
    (p v)
    (most-urgent-process)
    (reset! PROC p)
    (swap! PROCS #(remove #{p} %))
    ((:state p) (when (:wait p) v))))
```

`most-urgent-process` が返した `[proc val]` を受け取り、プロセスをキューから除いて state 関数を呼ぶ。

state 関数に渡す引数は `:wait` の有無で切り替える：

| `:wait` | state 関数への引数 | 理由 |
|---|---|---|
| `nil`（条件なし） | `nil` | 待機条件がないため渡すべき値が存在しない |
| `(fn [] test)`（条件あり） | `v`（条件の評価結果） | `wait` マクロの `param` に束縛される意味のある値 |

原著 CL では `:wait nil` のとき `t` が渡されていたが、これは `(or (not nil) ...)` の副産物であり、
`fork` / `yield` の state 関数は引数を常に無視するため実害はなかった。
Clojure 版では `(when (:wait p) v)` として意図をコードで明示している。

---

## プロセス制御 — `common/layer2.clj`

### `fork` — プロセスの生成

```clojure
(defmacro fork [expr pri]
  `(let [p# (l1/make-proc
              :state (fn [& g#] (do ~expr (l1/pick-process)))
              :pri   ~pri)]
     (swap! l1/PROCS conj p#)
     '~expr))
```

`expr` を実行して `pick-process` に移行する継続をプロセスとして登録する。

### `arbitrator` — 継続の保存と再スケジューリング

```clojure
(defn arbitrator [test cont]
  (swap! l1/PROC #(assoc % :wait test :state cont))
  (swap! l1/PROCS conj @l1/PROC)
  (l1/pick-process))
```

現在プロセスの `:state` を `cont`（継続関数）で上書きし、キューに戻してスケジューラへ委譲する。
これが chap21 における継続保存の核心。

`:wait` と `:state` の更新は1回の `swap!` でまとめて行う。
原著 CL の `(setf (proc-state *proc*) cont (proc-wait *proc*) test)` が1操作であることに対応する。

### `wait` — 条件付き待機

```clojure
(defmacro wait [param test & body]
  `(arbitrator
     (fn [] ~test)          ; :wait 条件
     (fn [~param] ~@body))) ; 再開時の継続
```

`test` が真になるまでプロセスを待機させ、真になったとき `param` に真値を束縛して `body` を実行する。

### `yield` — 実行権の明示的な解放

```clojure
(defmacro yield [& body]
  `(arbitrator nil (fn [x#] ~@body)))
```

条件なし（`:wait nil`）でキューに戻す。他プロセスに実行機会を与えてから `body` を継続する。

### その他

| 関数 | 役割 |
|------|------|
| `setpri n` | 現在プロセスの優先度を `n` に変更 |
| `halt` | `HALT` シグナル例外を投げてシステム全体を停止 |
| `kill` | 指定プロセスをキューから除去（引数なしで現在プロセスを終了）。副作用のみを目的とする手続きのため、返り値は常に `nil` |

---

## プログラムマクロ — `common/layer3.clj`

```clojure
(defmacro program [name args & body]
  `(defn ~name [~@args]
     (try
       (reset! l1/PROCS nil)
       ~@body
       (loop [] (l1/pick-process))
       (catch Exception ex
         (if (= (str l1/HALT) (.getMessage ex)) ...)))))

(defmacro program-cps [name args & body]
  `(u/=defn ~name [~@args]
            (try ...)))
```

`program` は通常 `defn` を、`program-cps` は `u/=defn` を使って関数を定義する。
`fork` で登録したプロセスを `pick-process` ループで順次実行し、`halt` が呼ばれると例外で停止する。

---

## ブラックボード — `chap21/black_board.clj`

プロセス間の共有状態（アサーション）を管理するシンプルな blackboard 実装。

```clojure
(def BBOARD (atom nil))

(defn claim   [& f] (swap! BBOARD conj f))                   ; アサートを追加
(defn unclaim [& f] (swap! BBOARD #(remove #{f} %)))         ; アサートを削除
(defn check   [& f] (seq (filter #(= % f) @BBOARD)))         ; アサートを検索
```

`check` は `seq` でラップすることで、マッチなしのとき `nil`（falsy）を返す。
`(filter ...)` 単体は空でも lazy seq を返すため常に truthy となり `wait` 条件が機能しなくなる。
また `first` と異なり全マッチを返す点で原著 CL の `remove-if-not` と同等の動作になる。

---

## 応用例 — `fnc.clj`（通常 defn 版）

`=defn` を使わない通常の `defn` による実装。原著の意図を素直に表現する。

### `ballet` — visitor / host パターン

```clojure
(defn visitor [door]
  (print "\nApproach" door ". ")
  (b/claim 'knock door)
  (l2/wait d (b/check 'open door)
    (print "Enter" door ". ")
    (b/unclaim 'knock door)
    (b/claim 'inside door)))

(defn host [door]
  (l2/wait k (b/check 'knock door)
    (print "Open" door ". ")
    (b/claim 'open door)
    (l2/wait g (b/check 'inside door)
      (print "Close" door ".")
      (b/unclaim 'open door))))
```

```clojure
(ballet)
;; Approach door2 . Open door2 . Enter door2 . Close door2 .
;; Approach door1 . Open door1 . Enter door1 . Close door1 .
```

visitor と host が `wait` を通じて互いの状態変化を待ち合わせる。

### `barbarians` — 優先度切り替えのデモ

```clojure
(defn capture [city]
  (b/my-take city)
  (l2/setpri 1)       ; 優先度を 1000 → 1 に下げて yield
  (l2/yield (b/fortify city)))

(defn plunder [city]
  (b/loot city)
  (b/ransom city))
```

```clojure
(barbarians)
;; Liberating TOKYO.
;; Nationalizing TOKYO.
;; Refinancing TOKYO.
;; Rebuilding TOKYO.
```

`capture` が `setpri 1` で優先度を下げた後に `yield` することで、
`plunder`（pri=980）が先に完了してから `capture` の残り処理が実行される。

---

## 応用例 — `cps.clj`（CPS 版）

`u/=defn` を用いた CPS 変換版。`program-cps` マクロで定義するため関数名に `_` 接尾辞は不要。

```clojure
(u/=defn visitor [door]
  (print "\nApproach" door ". ")
  (b/claim 'knock door)
  (l2/wait d (b/check 'open door)
    (print "Enter" door ". ")
    (b/unclaim 'knock door)
    (b/claim 'inside door)))
```

`=defn` は呼び出しマクロ（`visitor`）と実体関数（`=visitor`）を定義する。
別ネームスペースから `(mproc/visitor door)` のように呼んだとき、
完全修飾シンボルで展開されるよう `=defn` を修正済み（後述）。

`barbarians` の優先度は `capture` が 100 / `plunder` が 98 で定義されている（`fnc.clj` 版は 1000/980）。

---

## CPS マクロ — `common/util.clj`

chap20 で開発した CPS マクロ群を共通ライブラリとして配置している。
`cps.clj` ではプロセス間協調に `=defn` を使用している。
`fnc.clj`（通常 defn 版）では使用していない。

| マクロ | 役割 | 展開形 |
|--------|------|--------|
| `=defn` | CPS 関数の定義（再帰対応） | `declare` → `defmacro` → `defn` |
| `=defn_` | CPS 関数の定義（バグ版・比較用） | `defn` → `defmacro` |
| `=bind` | 継続のバインド | `(let [outer# *cont*] (binding [*cont* (fn params (binding [*cont* outer#] body))] expr))` |
| `=bind_` | 継続のバインド（バグ版・比較用） | `(binding [*cont* (fn params body)] expr)` |
| `=values` | 継続の呼び出し | `(*cont* retvals...)` |
| `=fn` | 無名 CPS 関数の生成 | `(fn [*cont* params] body)` |
| `=fncall` | CPS クロージャの呼び出し | `(fnc *cont* params...)` |
| `=apply` | シーケンス引数で CPS 関数を呼ぶ | `(apply fnc *cont* args...)` |

### `=defn` の名前解決修正

元の実装では別ネームスペースから `(mproc/visitor)` のように呼び出したとき
`"Unable to resolve symbol: =visitor in this context"` が発生した。

原因は `=defn` が生成する内部マクロが非修飾シンボルを使っていたため。

```clojure
;; 修正前（バグ）: ネストしたバックティックで =visitor が非修飾のまま展開される
(defmacro ~symbol-name ~params
  `(~'~f *cont* ~~@params))

;; 修正後: list 形式で完全修飾シンボルを埋め込む
(defmacro ~symbol-name [~@params]
  (list '~qf '~qcont ~@params))
;; → (onlisp.chap21.cps/=visitor onlisp.common.util/*cont* door)
```

`*cont*` も同様に `onlisp.common.util/*cont*` として明示的に修飾する。

---

## テスト詳細

| テストファイル | 対象 | テスト / アサーション |
|---|---|---|
| `common/layer1_test.clj` | `make-proc` / `multiple-value-bind` / `most-urgent-process` / `pick-process` | 8 / 28 |
| `common/layer2_test.clj` | `fork` / `halt` / `setpri` / `kill` / `arbitrator` / `wait` / `yield` | 9 / 21 |
| `black_board_test.clj` | `claim` / `unclaim` / `check` | 3 / 12 |
| `fnc_test.clj` | `ped` / `ballet`（統合）/ `barbarians`（統合）・通常 defn 版 | 6 / 19 |
| `cps_test.clj` | `ped` / `ballet`（統合）/ `barbarians`（統合）・CPS 版 | 6 / 19 |
| `util_test.clj` | CPS マクロ: `=defn` / `=defn_` / `=bind` / `=bind_` / `=values` / `=fn` / `=fncall` / `=apply` | 11 / 35 |
| **合計** | | **43 / 134** |

### `common/layer1_test.clj` — テスト内容

| テスト名 | 検証内容 |
|---|---|
| `make-proc-test` | フィールド設定・`:wait` 省略時の nil・Proc レコード型 |
| `multiple-value-bind-test` | 2要素・3要素の分解、`most-urgent-process` 戻り値との組み合わせ |
| `most-urgent-process-empty-test` | `PROCS` 空のとき `DEFAULT-PROC` を返す |
| `most-urgent-process-priority-test` | `:pri` 最大の選択、同 `:pri` での先頭優先 |
| `most-urgent-process-wait-test` | `false`/`nil` 条件のスキップ、truthy 値の val への反映、全 falsy 時の fallback |
| `pick-process-executes-test` | 最高優先度の実行・`PROCS` からの除去・`PROC` への設定 |
| `pick-process-val-test` | `:wait nil` → `nil` 渡し、`:wait fn` → 条件結果渡し |
| `pick-process-sequential-test` | 優先度順の逐次実行（p3→p2→p1） |

### `common/layer2_test.clj` — テスト内容

| テスト名 | 検証内容 |
|---|---|
| `fork-test` | 登録件数・`:pri`・`:wait nil`・複数件の蓄積 |
| `halt-throws-test` | ExceptionInfo を投げる・メッセージが `HALT` の文字列・`ex-data` の内容 |
| `setpri-test` | `PROC` の `:pri` を変更する・`:state` / `:wait` は変更しない |
| `kill-with-arg-test` | 指定プロセスの除去・nil 返却・存在しないプロセスを渡しても安全 |
| `kill-no-arg-test` | 次のプロセスを実行する |
| `arbitrator-updates-proc-test` | `PROC` の `:wait` / `:state` を更新して `PROCS` に追加する |
| `wait-passes-condition-result-test` | 条件の評価結果を `param` に束縛して body を実行する |
| `wait-blocks-until-condition-test` | 条件が false のあいだ待機し、true になると再開する |
| `yield-with-setpri-test` | `setpri` + `yield` で低優先度プロセスに実行権を渡し後で再開する |

**テストの停止方法**: `pick-process` は `PROCS` が空になると `DEFAULT-PROC`（stdin 待ち）に遷移する。
テストでは state 関数の末尾で `(l2/halt)` を呼ぶことで ExceptionInfo を発生させ停止する。
途中のプロセスで `halt` を呼ぶと後続が実行されないため、チェーンの末尾のプロセスのみ `halt` にする。

**`wait` マクロの test 式に関する注意**: `(wait param TEST body)` は `(fn [] TEST)` に展開される。
`TEST` に関数リテラルを渡すと `(fn [] (fn [] ...))` となり、`param` に関数オブジェクト自体が束縛される。
test には「param に渡したい評価結果」を直接書く。

### `black_board_test.clj` — テスト内容

| テスト名 | 検証内容 |
|---|---|
| `claim-test` | エントリの追加・複数件の蓄積・引数なし時の無操作 |
| `unclaim-test` | 指定エントリの除去・他エントリを残す・存在しないエントリへの安全性・引数なし時の無操作 |
| `check-test` | 空 BBOARD で nil・マッチなしで nil・マッチありで truthy・全マッチのシーケンス返却・引数なしで nil |

### `fnc_test.clj` / `cps_test.clj` — テスト内容

通常 defn 版と CPS 版で同一の構造を持つ。

| テスト名 | 検証内容 |
|---|---|
| `ped-test` | OPEN-DOORS に扉ありで入室メッセージ出力・空なら halt で停止 |
| `ballet-output-test` | door1/door2 の Approach/Open/Enter/Close をすべて出力する |
| `ballet-order-test` | 各扉内で Approach → Open → Enter → Close の順序を `.indexOf` で検証 |
| `ballet-BBOARD-test` | 終了後 `('inside door)` エントリが BBOARD に残留する（unclaim されない） |
| `barbarians-output-test` | Liberating/Nationalizing/Refinancing/Rebuilding をすべて出力 |
| `barbarians-order-test` | 出力順 Liberating → Nationalizing → Refinancing → Rebuilding を検証 |

**`ballet` / `barbarians` の停止方法**: `program` マクロは全プロセス完了後に `DEFAULT-PROC`（stdin 待ち）に遷移する。
`(with-in-str "(onlisp.chap21.common.layer2/halt)" (mproc/ballet))` のように stdin を差し替えることで、
`DEFAULT-PROC` が halt コールを eval して例外を発生させ、`program` の catch ブロックで停止できる。

**`testing` ブロック間での BBOARD の汚染**: fixture は `deftest` 単位でリセットするが、
`testing` ブロック間は引き継がれる。`ballet-order-test` 内の2〜4番目のブロック先頭で
`(reset! b/BBOARD nil)` を明示的に呼ぶことで独立性を保つ。

---

## 原著（Common Lisp）との差異

### 言語制約による必然的な変換

| 項目 | 原著 CL | Clojure 版 |
|---|---|---|
| プロセス構造体 | `(defstruct proc pri state (wait nil))` | `(defrecord Proc [pri state wait])` + `make-proc` |
| 状態変数 | `(defvar *procs* nil)` + `setq` | `(def PROCS (atom nil))` + `swap!` / `reset!` |
| 非局所脱出 | `(throw *halt* val)` — タグジャンプ | `(throw (ex-info (str HALT) {:val val}))` — Java 例外 |
| 多値返却 | 組み込み `multiple-value-bind` | 独自マクロ `multiple-value-bind` で模倣 |
| リスト操作 | `(delete obj *procs*)` — 破壊的 | `(remove #{obj} PROCS)` — 関数型 |
| 末尾再帰 | CL の TCO により自然に末尾最適化 | `(loop [] (pick-process))` で明示的にループ |

---

### 設計の明確化・改善

#### `program` — `=defn` を使わない

原著は `program` が生成する関数を CPS 関数（`=defn`）として定義している。
Clojure 版では通常の `defn` で足りると判断し、CPS 変換なしで実装した。

```
原著:  `(=defn ,name ,args (setq *procs* nil) ,@body (pick-process))
Clojure: (defn ~name [~@args] (try (reset! PROCS nil) ~@body (loop [] (pick-process)) ...))
```

`=defn` を使った版（`program-cps`）は `layer3.clj` / `cps.clj` に比較用として残している。

---

#### `pick-process` — state 関数への引数

原著は `:wait nil` のときも `val = t`（`(not nil)` の副産物）を state 関数に渡す。
Clojure 版では「条件なし → 渡すべき値なし」として `nil` を渡すよう明示した。

```
原著:  (funcall (proc-state p) val)              ; val = t（:wait nil のとき）
Clojure: ((:state p) (when (:wait p) v))          ; :wait nil なら nil を渡す
```

`fork` / `yield` の state 関数は引数を常に無視するため動作上の差異はない。

---

#### `fork` — state 関数の引数定義

```
原著:  (lambda (x) expr (pick-process))          ; 1引数、無視
Clojure: (fn [& g#] (do expr (pick-process)))     ; 可変長引数、無視
```

可変長にすることで「引数を無視する」という意図をより明示している。

---

#### `arbitrator` — 原子的な更新

原著の `setf` は `:state` と `:wait` を1操作で更新する。
Clojure 版では1回の `swap!` で両フィールドを同時に更新することで対応した。

```
原著:  (setf (proc-state *proc*) cont (proc-wait *proc*) test)
Clojure: (swap! PROC #(assoc % :wait test :state cont))
```

---

#### `check` — truthiness の一致

原著の `remove-if-not` はマッチなしのとき `nil`（falsy）を返す。
`(filter ...)` 単体は空でも lazy seq を返すため常に truthy となり `wait` 条件が機能しない。
`seq` でラップすることで空のとき `nil` になり、原著と同じ truthiness を再現した。

```
原著:  (remove-if-not #'(lambda (x) (equal x f)) *bboard*)  ; 空 → nil
Clojure: (seq (filter #(= % f) @BBOARD))                     ; 空 → nil
```

また `first` と異なり全マッチを返す点でも原著の動作に揃っている。

---

#### `kill` — 返り値の明示

原著でも `kill` の返り値は意図されていない。
Clojure 版では `swap!` や `pick-process` の戻り値が漏れないよう、`kill` の末尾で明示的に `nil` を返す。

---

## 参考文献

- Paul Graham『On Lisp』第21章 — Multiple Processes

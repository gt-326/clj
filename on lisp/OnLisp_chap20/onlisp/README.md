# On Lisp — 第20章 継続（Continuations）Clojure 実装

Paul Graham 著『On Lisp』第20章の CPS（継続渡しスタイル）マクロ群を Clojure で実装・探究したプロジェクト。
継続の3段階の実装アプローチと、その設計上のトレードオフを動くコードで示す。

## テスト

```
lein test
;; Ran 24 tests containing 79 assertions. 0 failures, 0 errors.
```

---

## プロジェクト構成

```
src/onlisp/
├── core.clj                          # DFT 応用例・dft-node の設計比較
└── chap20/
    ├── continuations.clj             # 最終実装（=defn / =defn_ / =bind など）
    ├── continuations2.clj            # 参照用（整理前の実装）
    └── continuations/
        ├── atom.clj                  # 第1版: atom によるグローバル管理
        └── dynamic.clj               # 第2版: ^:dynamic Var + binding による管理

test/onlisp/chap20/continuations/
├── atom_test.clj                     # atom 版の特性テスト
├── dynamic_test.clj                  # dynamic 版の特性テスト
└── continuations_test.clj            # 最終版（=defn / =defn_ 比較含む）
```

---

## マクロ一覧

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

---

## 実装の3段階

### 第1版: `atom.clj` — グローバルな可変状態

継続 `cont` を atom で管理する最初の試み。

```clojure
(def cont (atom identity))

(defmacro =bind [params expr & body]
  `(do
     (reset! cont (fn ~params ~@body))
     ~expr))
```

**問題点**: `=bind` が `cont` を `reset!` するため、スコープを抜けても状態が残る。
テストに `use-fixtures :each` でのリセットが必要。

---

### 第2版: `dynamic.clj` — `^:dynamic` Var + `binding`

`binding` の動的スコープを利用して継続を管理する。

```clojure
(def ^:dynamic *cont* identity)

(defmacro =bind [params expr & body]
  `(binding [*cont* (fn ~params ~@body)]
     ~expr))
```

**改善点**: `=bind` スコープを抜けると `*cont*` が自動的に `identity` に戻る。
`use-fixtures` 不要。

---

### 第3版: `continuations.clj` — `=defn` / `=defn_` / `=bind` / `=bind_` の追加

`dynamic.clj` に `=defn`（再帰対応）・`=defn_`（バグ版）・`=bind`（body 修正版）・`=bind_`（バグ版）を加えた最終版。

#### `=defn_`（バグあり）: `defn` → `defmacro` の展開順

```clojure
(defmacro =defn_ [symbol-name params & body]
  (let [f (symbol (str "=" (name symbol-name)))]
    `(do
       (defn ~f [~'*cont* ~@params] ~@body)   ; 先に defn → body コンパイル時 f マクロ未定義
       (defmacro ~symbol-name ~params
         `(~'~f *cont* ~~@params)))))          ; 後から defmacro
```

`body` がコンパイルされる時点でマクロ `symbol-name` がまだ存在しないため、
body 内の自己呼び出し `(f ...)` が未解決シンボルとしてコンパイルエラーになる。

#### `=defn`（正しい）: `declare` → `defmacro` → `defn` の展開順

```clojure
(defmacro =defn [symbol-name params & body]
  (let [f (symbol (str "=" (name symbol-name)))]
    (declare f)
    `(do
       (defmacro ~symbol-name ~params          ; 先に defmacro
         `(~'~f *cont* ~~@params))
       (defn ~f [~'*cont* ~@params] ~@body)))) ; 後から defn → body コンパイル時マクロ定義済み
```

`defmacro` を先に定義することで、`defn` の `body` がコンパイルされる時点で
`(symbol-name ...)` が `(=symbol-name *cont* ...)` に展開可能になる。

---

## `=bind_`（バグ版）と `=bind`（正しい版）の比較

### `=bind_`（バグあり）: body 内 `*cont*` が自己参照になる

```clojure
(defmacro =bind_ [params expr & body]
  `(binding [*cont* (fn ~params ~@body)]
     ~expr))
```

`body` 実行時も `binding` スコープが active なため、`*cont*` は内側の継続 `(fn params ...)` 自身を指す。
`body` 内で `(=values ...)` を呼ぶと `*cont*` が自己参照になり、アリティ不一致でエラーになるか無限ループに陥る。

```clojure
;; ArityException になる（2引数継続を1引数で呼ぶ）
(=defn message []  (=values 'hello 'there))

(=defn baz []
  (=bind_ [m n] (message)
    (=values (list m n))))   ; (*cont* (list m n)) → =baz$fn を1引数で呼ぶ → エラー
```

### `=bind`（正しい）: body 実行前に外側の継続を復元する

```clojure
(defmacro =bind [params expr & body]
  `(let [outer# *cont*]
     (binding [*cont* (fn ~params
                        (binding [*cont* outer#]
                          ~@body))]
       ~expr)))
```

継続が呼ばれる際に `*cont*` を `outer#`（`=bind` 前の継続）に戻してから `body` を実行する。
`body` 内の `(=values ...)` は外側の継続を正しく呼ぶ。

```clojure
;; 正常に動作する
(=defn baz []
  (=bind [m n] (message)
    (=values (list m n))))   ; (*cont* = outer# = identity) → (identity '(hello there)) ✓

(baz)  ;; => (hello there)
```

---

## `core.clj` — DFT（深さ優先探索）への応用

### `dft-node_` と `dft-node` の設計比較

```clojure
;; dft-node_: 葉で =values（動的 Var 参照）
(c/=defn dft-node_ [tree]
  (cond
    (not (list? tree)) (c/=values tree)   ; (c/*cont* tree) — 実行時に動的解決
    ...))

;; dft-node: 葉で fnc-cont（レキシカルキャプチャ）
(c/=defn dft-node [fnc-cont tree]
  (cond
    (not (list? tree)) (fnc-cont tree)    ; クロージャ作成時にキャプチャ済み
    ...))
```

`saved` に積まれるラムダの違い：

| | `dft-node_` | `dft-node` |
|---|---|---|
| `saved` に積むラムダ | `(fn [] (dft-node_ rest))` | `(fn [] (dft-node fnc-cont rest))` |
| 継続のキャプチャ | なし | `fnc-cont` をクロージャでキャプチャ |
| `=bind` 外から `restart` 呼び出し | `c/*cont* = identity` → 生の値 | `fnc-cont(leaf)` → 継続が動く |

### `dft-node_` が動く条件

`restart` が `=bind` の継続の**内側**から呼ばれる場合のみ正しく動作する。

```clojure
;; dft2_: restart を継続の内側から呼ぶ → dft-node_ でも動作する
(c/=defn dft2_ [tree]
  (do
    (reset! saved '())
    (c/=bind [node] (dft-node_ tree)
      (if (= node '[done])
        node
        (do (print node) (restart))))))   ; ← 継続の内側から呼ぶ
```

`=bind_`（バグ版）では `binding` スコープが継続呼び出し中も active なため、
`c/*cont*` が維持されて正しく動く。これが原著 CL 版の `dft2` と等価な設計。

**注意**: `=bind`（修正版）では body 実行前に `*cont*` が外側に戻るため、
`saved` から呼ばれる `(fn [] (dft-node_ rest))` 実行時に `c/*cont* = identity` となり、
`dft2_` は動作しなくなる。`dft-node_` パターン（継続を明示的にキャプチャしない設計）は
`=bind_` の旧セマンティクスに依存している。

### `=bind` 外から `restart` を呼ぶ場合に正しく動く唯一の形

```clojure
;; 両方 dft-node（fnc-cont キャプチャ）
(def b
  (c/=bind [node1] (dft-node c/*cont* t1)
    (if (= node1 '[done])
      node1
      (c/=bind [node2] (dft-node c/*cont* t2)
               [node1 node2]))))
```

REPL から `(restart)` を 63 回呼んで t1 × t2 の全ペアを正しく得られる唯一の実装。

**理由**: `saved` に積むラムダが `fnc-cont`（継続）をキャプチャしているため、
`=bind` スコープが終了した後も継続が生き続ける。

### `dft3` — 2つのツリーのインターリーブ

```clojure
(c/=defn dft3 [t1 t2]
  (do
    (reset! saved '())
    (c/=bind [node1] (dft-node c/*cont* t1)
      (c/=bind [node2] (dft-node c/*cont* t2)
        (if (= node2 '[done])
          node2
          (do (print (list node1 node2) " ")
              (restart)))))))
```

```clojure
(dft3 t1 t2)
;; (a 1) (b 1) (b 2) ... (g 5) [done]   ← 63 ペア
```

---

## `dft-node_` と `dft-node` の動作の違い（実測）

`(reset! saved '())` 後にそれぞれを評価し、`(restart)` を繰り返した結果：

| | `dft-node_`（t1） + `dft-node`（t2） | `dft-node`（t1 + t2）|
|---|---|---|
| 初期結果 | `[a 1]` | `[a 1]` |
| restart[1..6] | `[a 2]..[a 5]`（ペア） | `[a 2]..[a 5]`（ペア） |
| restart[7] | `b`（生の値） | `[b 1]`（ペア） |
| restart[8..] | `d h c e f i g`（生の値） | `[b 2]..[g 5]`（ペア続く） |
| 終了 | restart[15]: `[done]` | restart[63]: `[done]` |
| 合計ペア数 | **7 ペア** | **63 ペア** |

t2 に `dft-node` を使うと t2 のラムダが `fnc-cont` をキャプチャするため、
t2 分（6回）のペアは正しく返る。しかし t1 のラムダはキャプチャなしのため、
t1 のターンになると生の値になる。

---

## テスト詳細

| テストファイル | 対象 | テスト / アサーション |
|---|---|---|
| `atom_test.clj` | atom 版: `=defn` / `=bind` / `=values` / `=fn` / `=fncall` / `=apply` | 7 / 25 |
| `dynamic_test.clj` | dynamic 版: 同上 + `binding` スコープの自動解放 | 7 / 23 |
| `continuations_test.clj` | 最終版: `=defn` vs `=defn_` / `=bind` vs `=bind_` 比較 | 10 / 31 |
| **合計** | | **24 / 79** |

各テストで `atom.clj` と `dynamic.clj` の挙動差を対比して確認している：

- **`atom.clj`**: `=bind` 後も `cont` が書き換わったまま残る → `use-fixtures` でリセット必要
- **`dynamic.clj`**: `=bind` 後に `*cont*` が `identity` に自動復帰 → リセット不要

---

## 参考文献

- Paul Graham『On Lisp』第20章 — Continuations

# On Lisp — 第22章 非決定性（Nondeterminism）Clojure 実装

Paul Graham 著『On Lisp』第22章の非決定性プログラミングを Clojure で実装・探究したプロジェクト。
CPS（継続渡しスタイル）と継続スタックを組み合わせ、バックトラッキングによる非決定的探索を実現する。

## テスト

```
lein test
```

---

## プロジェクト構成

```
src/onlisp/
├── common/
│   └── util.clj                     # CPS マクロ共通ライブラリ（=defn / =bind など）
└── chap22/
    ├── common.clj                   # バックトラッキング基盤（PATHS / fail / cb / mark / cut / true-choose / true-choose-simple）
    ├── nondetermin.clj              # 応用例（parlor-trick / descent / find-boxes 系）
    └── nondetermin2.clj             # 拡張例（bf-path / descent-impl2~4 — 循環グラフ対応）

test/onlisp/
├── chap22/
│   └── common_test.clj              # バックトラッキング基盤のユニットテスト
└── common/
    └── util_test.clj                # CPS マクロのテスト
```

---

## アーキテクチャ

非決定性は「試せる選択肢の継続をスタックに積み、失敗したら巻き戻す」仕組みで実現する。

```
PATHS (atom [])  ← 試みる継続のスタック（ベクタ、右端が top）

[実行サイクル]
choose-bind / cb
  └─ 残りの選択肢の継続を PATHS に push
  └─ 先頭の選択肢で body を実行

fail
  └─ PATHS が空 → failsym（[end]）を返す
  └─ PATHS が非空 → top を pop して呼ぶ（次の選択肢へバックトラック）

mark / cut
  └─ mark: fail 関数を番兵として PATHS に push
  └─ cut:  番兵まで PATHS をクリア（探索空間の枝刈り）
```

---

## バックトラッキング基盤 — `chap22/common.clj`

### グローバル状態

```clojure
(def PATHS   (atom []))    ; 継続スタック（ベクタ、右端が top）
(def failsym '[end])       ; 失敗を表すシンボル
```

### `fail` — バックトラックのエントリポイント

```clojure
(defn fail []
  (if (empty? @PATHS)
    failsym
    (let [fnc (peek @PATHS)]
      (swap! PATHS pop)
      (fnc))))
```

PATHS が空なら `[end]` を返す。非空なら先頭の継続を pop して呼ぶ。

### `cb` — 選択肢の実行と継続の保存

```clojure
(defn cb [fnc choices]
  (if (seq choices)
    (do
      (when (rest choices)
        (let [saved u/*cont*]
          (swap! PATHS conj (fn []
                              (binding [u/*cont* saved]
                                (cb fnc (rest choices)))))))
      (fnc (first choices)))
    (fail)))
```

残りの選択肢の継続を PATHS に積んでから先頭の選択肢で `fnc` を呼ぶ。
継続を積む時点の `*cont*` を `saved` として保存することで、
`fail` から呼ばれる際にも正しい継続で再開できる。

> **注意（Clojure vs Common Lisp）**:
> Common Lisp では `(rest '(x))` = `nil`（falsy）のため単一選択肢では PATHS に積まれない。
> Clojure では `(rest '(x))` = `()`（truthy）のため失敗継続が積まれる。
> 動作に支障はないが、Clojure では1ステップ余計な `fail` が発生する。

### `choose-bind` / `choose` — マクロ

```clojure
(defmacro choose-bind [param choices & body]
  `(cb (fn [~param] ~@body) ~choices))

(defmacro choose [& choices]
  (if (seq choices)
    `(do
       ~@(map (fn [c] `(swap! PATHS conj #(~@c))) (reverse (rest choices)))
       ~(first choices))
    `(fail)))
```

### `mark` / `cut` — 枝刈り

```clojure
(defn mark []
  (swap! PATHS conj fail))   ; fail 関数自体を番兵として積む

(defn cut []
  (if (seq @PATHS)
    (if (= (peek @PATHS) fail)
      (swap! PATHS pop)      ; 番兵を除去して終了
      (do
        (swap! PATHS pop)
        (recur)))))           ; 番兵より上の要素を再帰的に除去
```

`mark` は `fail` 関数本体を番兵として積む。
`cut` は番兵（`fail` 関数と同一オブジェクト）を見つけるまで PATHS を pop し続ける。

### `true-choose-impl` / `true-choose` / `true-choose_` — 重複スキップ付き選択

```clojure
(defn true-choose-impl
  [visited choices]
  (if (empty? choices)
    (fail)
    (let [[a & more] choices]
      (if (contains? visited a)
        ;; 循環検出 → この選択肢をスキップして残りへ
        (true-choose-impl (conj visited a) more)
        ;; 有効な選択肢 → 残りをバックトラック用に積み、a を実行
        (do
          (when (seq more)
            (let [saved u/*cont*]
              (swap! PATHS conj
                     (fn []
                       (binding [u/*cont* saved]
                         (true-choose-impl (conj visited a) more))))))
          (a))))))

;; エントリポイント（関数版）
(defn true-choose [choices]
  (true-choose-impl #{} choices))

;; エントリポイント（マクロ版）
(defmacro true-choose_ [& choices]
  `(true-choose-impl #{}
                     (list ~@(map (fn [c] `(fn [] ~c)) choices))))
```

`cb` との違い：

| 項目 | `cb` / `choose-bind` | `true-choose-impl` / `true-choose` |
|---|---|---|
| 選択肢の型 | 値のリスト（`'(1 2 3)`） | thunk のリスト（`[(fn [] ...) ...]`） |
| 循環スキップ | なし | `visited` セットで同一 thunk を重複排除 |
| 主な用途 | 値を束縛して CPS body を実行 | 各 thunk を独立した継続として実行 |
| 動的リストへの対応 | `cb`（関数）で対応 | `true-choose`（関数）で対応 |
| 静的マクロ版 | `choose-bind` | `true-choose_` |

> **`true-choose_` の制約**:
> `true-choose_` は `& choices` で静的な複数引数を取る。
> `(map ...)` のような実行時に決まる動的リストを渡すと、リスト全体が1つの thunk に包まれ CPS チェーンが途切れる。
> 動的リストには `true-choose`（関数）または `true-choose-impl` を直接使うこと。

### `true-choose-simple` / `true-choose2` — Scheme `true-choose` の CPS トレース

`true-choose-impl` から `visited` による重複 thunk スキップを取り除いたシンプル版。

```clojure
;; 関数版
(defn true-choose-simple
  [choices]   ; thunk のリスト
  (if (empty? choices)
    (fail)
    (let [[a & more] choices]
      (when (seq more)
        (let [saved u/*cont*]
          (swap! PATHS conj
                 (fn []
                   (binding [u/*cont* saved]
                     (true-choose-simple more))))))
      (a))))   ; 先頭 thunk を直接呼ぶ

;; マクロ版（静的な選択肢を thunk に包んで渡す）
(defmacro true-choose2 [& choices]
  `(true-choose-simple
     (list ~@(map (fn [c] `(fn [] ~c)) choices))))
```

**Scheme との対応**:

| Scheme `true-choose` | Clojure `true-choose-simple` |
|---|---|
| `choices` は値のリスト | `choices` は thunk のリスト |
| 各 choice を `(lambda () (cc choice))` に包む | 呼び出し元が thunk を生成して渡す |
| `call/cc` で呼び出し元に戻る | `*cont*` の保存・復元で CPS チェーンを維持 |
| 全選択肢を PATHS に積んで `fail` を呼ぶ | 残りだけ PATHS に積んで先頭を直接呼ぶ（等価） |

**`true-choose-impl` との差異**:

| 項目 | `true-choose-impl` | `true-choose-simple` |
|---|---|---|
| `visited` 引数 | あり（`#{}` で初期化） | なし |
| 同一 thunk の重複スキップ | あり | なし（全て実行） |
| 実用上の dead code | `map #(...)` で毎回新規 thunk を生成するため常に dead | 該当なし |

---

## 応用例 — `chap22/nondetermin.clj`

### `parlor-trick` — 合計値の探索（P303 chap22.4）

```clojure
(u/=defn parlor-trick [sum]
         (reset! c/PATHS [])
         (u/=bind [n1 n2] (two-numbers)
                  (if (= sum (+ n1 n2))
                    (format "the sum of %s %s" n1 n2)
                    (c/fail))))
```

```
(parlor-trick 4)   ; → "the sum of 1 3"
(parlor-trick 100) ; → [end]
```

`two-numbers` が `(=values n1 n2)` で2値を返すため、`=bind` のパラメータは `[n1 n2]`。

### `descent` — 木の経路探索（P305 chap22.4）

再帰でのリセット問題を避けるため、エントリポイントと再帰実装を分離している。

```clojure
;; 再帰実装（PATHS をリセットしない）
(u/=defn descent-impl [n1 n2]
         (cond
           (= n1 n2) (u/=values (list n2))
           (kids n1) (c/choose-bind n (kids n1)
                                    (u/=bind [p] (descent-impl n n2)
                                             (u/=values (cons n1 p))))
           :else (c/fail)))

;; エントリポイント（PATHS をリセットしてから委譲）
(u/=defn descent [n1 n2]
         (reset! c/PATHS [])
         (descent-impl n1 n2))
```

```
(descent 'a 'g) ; → (a c f g)
(c/fail)        ; → [end]
(descent 'a 'd) ; → (a b d)
(c/fail)        ; → (a c d)
```

### `find-boxes` 系 — キャンディ箱探索（P307-308 chap22.5）

| 関数 | 概要 |
|---|---|
| `find-boxes` | 総当たり探索（全件列挙） |
| `find-boxes2` | 疑似カット（表示をバイパスするだけ、探索は総当たり） |
| `find-boxes3` | `rand-coins` によるランダムなコイン配置 |
| `find-boxes4` | 全発見後に `actual:` を表示（ただし余分な枚挙が残る） |
| `find-boxes5` | `mark`/`cut` で1都市1コインを前提とした枝刈り |
| `find-boxes6` | `rand-coins` との組み合わせ（1都市1コイン前提のため都市重複で問題あり） |
| `find-boxes7` | 都市ごとのコイン数を事前計算し、全発見時に `cut` → 都市重複にも対応 |

#### `mark` の配置（`gen-sent-candy-log4`）

```clojure
(c/choose-bind c cities
               (c/mark)              ; ← 都市を選んだ直後、店・箱の選択の直前
               (c/choose-bind s stores
                              (c/choose-bind b boxes
                                             (u/=values (list c s b)))))
```

`mark` を都市ループの内側に置くことで、`cut` が除去するのは「その都市の残りの店・箱の継続」のみとなり、他の都市の継続は保持される。

#### `find-boxes7` — 都市重複対応

```clojure
(u/=defn find-boxes7 [cities store-num box-num]
         (reset! c/PATHS [])
         (let [coins          (rand-coins cities store-num box-num)
               coins-per-city (frequencies (map first coins))
               hit-per-city   (atom {})
               hit            (atom 0)
               cnt            (atom 0)]
           ...
           (when (>= (get @hit-per-city city 0)
                     (get coins-per-city city 0))
             (c/cut))             ; その都市のコインをすべて見つけたときだけ cut
           (when (>= @hit (count coins))
             (println "actual:" @cnt)
             (reset! c/PATHS [])) ; 全発見後に PATHS を空にして即終了
           ...))
```

`coins-per-city` で都市ごとのコイン数を事前計算し、
`hit-per-city` で発見数を追跡することで、複数コインが同一都市に存在する場合にも正しく動作する。

```
(nondetermin/find-boxes7 '[LA NY BOS WA] 3 2)
; coins: [(BOS 1 1) (LA 1 1) (NY 1 1) (LA 2 1)]
; total: 24
; (LA 1 1) C
; (LA 1 2) (LA 2 1) C
; (NY 1 1) C
; (BOS 1 1) C
; actual: 5
; [end]
```

---

## CPS マクロ — `common/util.clj`

chap20 で開発した CPS マクロ群を共通ライブラリとして配置。

| マクロ | 役割 |
|--------|------|
| `=defn` | CPS 関数の定義（再帰対応・完全修飾シンボル） |
| `=bind` | 継続のバインド（body 内で外側の継続を復元） |
| `=values` | 継続の呼び出し（`(*cont* retvals...)`） |
| `=fn` / `=fncall` / `=apply` | 無名 CPS 関数の生成・呼び出し |

---

## テスト詳細

| テストファイル | 対象 | テスト数 |
|---|---|---|
| `chap22/common_test.clj` | `fail` / `cb` / `choose-bind` / `mark` / `cut` / `true-choose-impl` / `true-choose` / `true-choose_` / `true-choose-simple` / `true-choose2` / `choose` | 33 |
| `common/util_test.clj` | `=defn` / `=bind` / `=values` / `=fn` / `=fncall` / `=apply` | 11 |

合計: **44 tests, 94 assertions**

### `chap22/common_test.clj` — テスト内容

| テスト名 | 検証内容 |
|---|---|
| `fail-empty-test` | PATHS 空 → `failsym` を返す |
| `fail-nonempty-test` | 先頭の関数を pop して呼ぶ・戻り値を返す |
| `cb-empty-choices-test` | choices 空 → `fail` → `failsym` |
| `cb-single-choice-test` | 先頭の値で `fnc` を呼ぶ・Clojure では失敗継続が積まれる |
| `cb-multiple-choices-test` | 残りの継続を PATHS に積む・`*cont*` の保存と復元 |
| `choose-bind-test` | 束縛・body 実行・`fail` による次選択肢取得 |
| `mark-increments-count-test` | PATHS の要素数が1増える |
| `mark-pushes-fail-test` | 積まれる番兵が `fail` 関数本体 |
| `mark-multiple-test` | 複数回 `mark` で番兵が複数積まれる |
| `cut-empty-test` | PATHS 空 → `nil` を返し PATHS は変化なし |
| `cut-sentinel-only-test` | 番兵のみ → pop して PATHS が空になる |
| `cut-entries-above-sentinel-test` | 番兵より上を全除去・番兵より下は保持 |
| `mark-cut-roundtrip-test` | mark→cut で PATHS が元に戻る・都市継続への移行 |
| `mark-cut-multiple-cities-test` | 都市ごとの mark/cut 繰り返し |
| `true-choose-impl-empty-test` | choices 空 → `fail` → `failsym` |
| `true-choose-impl-single-test` | thunk が1つ → 呼ばれて結果を返す |
| `true-choose-impl-multiple-test` | 先頭を実行し残りを PATHS に積む・`fail` で次の thunk・`*cont*` の保存と復元 |
| `true-choose-impl-dedup-test` | 同一 thunk オブジェクトは `visited` により2回目以降スキップ |
| `true-choose-fn-test` | 関数版エントリポイント・`fail` で次選択肢・全消尽で `failsym` |
| `true-choose-macro-first-choice-test` | マクロ版・先頭の静的選択肢を実行 |
| `true-choose-macro-fail-test` | マクロ版・`fail` で次の静的選択肢を取得 |
| `true-choose-macro-empty-test` | マクロ版・引数なし → `failsym` |
| `true-choose-simple-empty-test` | choices 空 → `fail` → `failsym` |
| `true-choose-simple-single-test` | thunk が1つ → 呼ばれて結果を返す |
| `true-choose-simple-multiple-test` | 先頭を実行し残りを PATHS に積む・`fail` で次の thunk・`*cont*` の保存と復元 |
| `true-choose-simple-no-dedup-test` | `visited` なし → 同一 thunk も3回すべて実行（`true-choose-impl` との差異） |
| `true-choose2-first-choice-test` | マクロ版・先頭の静的選択肢を実行 |
| `true-choose2-fail-test` | マクロ版・`fail` で次の静的選択肢を取得 |
| `true-choose2-empty-test` | マクロ版・引数なし → `failsym` |
| `choose-empty-test` | choices 空 → `fail` → `failsym` |
| `choose-single-test` | 1つのとき直接実行・PATHS に積まれない（`cb` との差異） |
| `choose-multiple-test` | 先頭を実行・残りが別エントリとして積まれる・`fail` で次選択肢 |
| `choose-no-cont-save-test` | `*cont*` を保存しない（`cb` との差異） |

**`testing` ブロック間の PATHS 共有に注意**:
`use-fixtures :each` は `deftest` 単位でリセットするが、同一 `deftest` 内の `testing` ブロック間では PATHS が引き継がれる。
絶対値で状態を検証するテストは `deftest` を分割すること（`mark-*`・`true-choose-macro-*` テストを参照）。

---

## 拡張例 — `chap22/nondetermin2.clj`

### `bf-path` / `path` — 幅優先経路探索（P308 chap22.6）

パスを `(現在ノード ... 起点)` の逆順リストとしてキューに積む。
目的地に到達したとき `(reverse path)` で順方向の経路を返す。

#### `bf-path_` / `path_` — 原著直訳版（バグあり）

```clojure
;; buggy
(defn bf-path_ [dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]
      (if (= node dest)
        (rest (reverse path))          ; ← rest で起点を落としてしまう
        (recur dest
               (concat (rest queue)
                       (map #(cons % path)
                            (n/kids node))))))))

(defn path_ [node1 node2]
  (bf-path_ node2 (list (list node1))))
```

```
(n2/path_ 'a 'g) ; → (c f g)   ← 起点 a が落ちている
(n2/path_ 'a 'd) ; → (b d)
(n2/path_ 'a 'z) ; → nil
```

原著 Scheme 版の `(cdr (reverse path))` をそのまま翻訳したもの。
`descent` が `(a c f g)` を返すのに対して起点が欠落しており、**原著のバグ**と判断した。

#### `bf-path` / `path` — 修正版

```clojure
(defn bf-path [graph dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]
      (if (= node dest)
        (reverse path)                 ; rest を除去 → 起点を含む
        (recur graph dest
               (concat (rest queue)
                       (map #(cons % path)
                            (graph node))))))))

(defn path [graph node1 node2]
  (reset! c/PATHS [])
  (bf-path graph node2 (list (list node1))))
```

```
(n2/path n/kids  'a 'g) ; → (a c f g)
(n2/path n2/kids2 'a 'e) ; → (a d e)
```

`graph` を引数で受け取る形に変更し、`kids2`（循環グラフ）にも使える。

`descent`（深さ優先・CPS）との比較：

| | `descent` | `path` |
|---|---|---|
| 探索順 | 深さ優先（PATHS スタック） | 幅優先（キュー） |
| 結果形式 | `(a c f g)`（両端含む） | `(a c f g)`（両端含む） |
| 複数経路 | `fail` で順次取得 | 最短1つのみ |
| 実装方式 | CPS + バックトラック | 純粋な再帰（CPS 不要） |

---

### `kids2` — 循環のある有向グラフ

```clojure
(defn kids2 [n]
  (case n
    a '(b d)
    b '(c)
    c '(a)    ; a → b → c → a という循環
    d '(e)
    '()))
```

`kids` グラフ（非循環）と異なり、`a → b → c → a` という循環を含む。
`descent-impl`（`kids` ハードコード版）をそのまま使うと StackOverflowError になる。

---

### `descent-impl2` / `descent2` — 循環グラフ対応の経路探索

`visited` セットを引数で引き回すことで循環を検出しバックトラックする。

```clojure
;; 再帰実装（PATHS をリセットしない）
(u/=defn descent-impl2 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)    ; 循環検出 → バックトラック

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/choose-bind n (graph n1)
                            (u/=bind [p] (descent-impl2 graph n n2 visited#)
                                     (u/=values (cons n1 p)))))

           :else
           (c/fail)))

;; エントリポイント（PATHS をリセットしてから委譲）
(u/=defn descent2 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl2 graph n1 n2 #{}))
```

#### 設計のポイント

| 項目 | 内容 |
|---|---|
| `graph` を引数で受け取る | グラフ関数を外部から注入できる（依存性注入） |
| `visited` セットを引数で引き回す | 純粋関数的に循環を検出（グローバル状態不使用） |
| `visited = #{}` で初期化 | 空セットから開始し、訪問したノードを都度追加していく |
| `descent-impl`/`descent2` の分割 | 再帰呼び出しで `reset!` が走る問題を回避（`nondetermin.clj` と同じパターン） |

#### `cond` の評価順

1. `(= n1 n2)` — 目的地に到達 → 経路を返す
2. `(contains? visited n1)` — 既訪問 → `fail`（循環検出）
3. `(graph n1)` — 子ノードあり → 選択肢を展開
4. `:else` — 子ノードなし → `fail`（行き詰まり）

```
;; 循環なしグラフ（kids）
(n2/descent2 n/kids 'a 'g)   ; → (a c f g)
(c/fail)                       ; → [end]

;; 循環ありグラフ（kids2） — a→b→c→a の循環を検出してバックトラック
;; a→d→e の経路で到達できる
(n2/descent2 n2/kids2 'a 'e) ; → (a d e)
(c/fail)                       ; → [end]
```

---

### `descent-impl3` / `descent3` — `c/true-choose` を使った実装

`choose-bind` の代わりに `c/true-choose` を用いた実装。
各子ノードへの遷移を thunk に包んで `map` で生成し、`true-choose` に渡す。

```clojure
(u/=defn descent-impl3 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/true-choose
               (map (fn [n]
                      #(u/=bind [p] (descent-impl3 graph n n2 visited#)
                                (u/=values (cons n1 p))))
                    (graph n1))))

           :else
           (c/fail)))

(u/=defn descent3 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl3 graph n1 n2 #{}))
```

`descent-impl2` との比較：

| 項目 | `descent-impl2` | `descent-impl3` |
|---|---|---|
| 選択オペレータ | `c/choose-bind` | `c/true-choose` |
| 選択肢の渡し方 | 値リスト（`(graph n1)`） | thunk のリスト（`map` で生成） |
| 動作の違い | なし（同じ経路を返す） | なし |

```
(n2/descent3 n/kids 'a 'g)    ; → (a c f g)
(c/fail)                        ; → [end]
(n2/descent3 n2/kids2 'a 'e)  ; → (a d e)
(c/fail)                        ; → [end]
```

---

### `descent-impl4` / `descent4` — `c/true-choose-impl` を直接使った実装

`c/true-choose_` マクロを使おうとすると `(map ...)` 全体が1つの thunk に包まれ CPS チェーンが途切れる。
そのため `c/true-choose-impl` を直接呼び出すことで正しく動作させる。

```clojure
(u/=defn descent-impl4 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/true-choose-impl #{}
               (map (fn [n]
                      #(u/=bind [p] (descent-impl4 graph n n2 visited#)
                                (u/=values (cons n1 p))))
                    (graph n1))))

           :else
           (c/fail)))

(u/=defn descent4 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl4 graph n1 n2 #{}))
```

> **`c/true-choose_` が使えない理由**:
> `(c/true-choose_ (map (fn [n] #(...)) (graph n1)))` と書くと
> → `(true-choose-impl #{} (list (fn [] (map (fn [n] #(...)) (graph n1)))))` に展開される。
> `map` 全体が1つの thunk になるため、呼ぶと lazy seq が返るだけで CPS 継続が呼ばれない。
> `true-choose-impl` の `visited` 引数に `#{}` を渡すことで `true-choose` と同等に使える。

```
(n2/descent4 n/kids 'a 'g)    ; → (a c f g)
(c/fail)                        ; → [end]
(n2/descent4 n2/kids2 'a 'e)  ; → (a d e)
(c/fail)                        ; → [end]
```

---

### `path-impl` / `path-scheme` — Scheme `path` の CPS 翻訳

原著 Scheme の `path`（`true-choose` + `neighbors` を使った非決定的探索）を Clojure CPS に翻訳したもの。

```scheme
;; 原著 Scheme 版
(define (path node1 node2)
  (cond ((null? (neighbors node1)) (fail))
        ((memq node2 (neighbors node1)) (list node2))
        (else (let ((n (true-choose (neighbors node1))))
                (cons n (path n node2))))))
```

```clojure
;; Clojure CPS 翻訳版
(u/=defn path-impl [graph node1 node2 visited]
         (let [nbrs (graph node1)]
           (cond
             (contains? visited node1)
             (c/fail)            ; 循環検出（Scheme 版にはない）

             (empty? nbrs)
             (c/fail)

             (some #{node2} nbrs)
             (u/=values (list node2))

             :else
             (let [visited# (conj visited node1)]
               (c/true-choose-simple
                 (map (fn [n]
                        #(u/=bind [p] (path-impl graph n node2 visited#)
                                  (u/=values (cons n p))))
                      nbrs))))))

(u/=defn path-scheme [graph node1 node2]
         (reset! c/PATHS [])
         (u/=bind [p] (path-impl graph node1 node2 #{})
                  (u/=values (cons node1 p))))
```

**Scheme 版との差異**:

| 項目 | Scheme `path` | Clojure `path-scheme` |
|---|---|---|
| ゴール検出 | `(memq node2 (neighbors node1))` | `(some #{node2} nbrs)`（同等） |
| 選択オペレータ | `(true-choose (neighbors node1))` — 値を渡す | `(c/true-choose-simple (map ...))` — thunk を渡す |
| 循環検出 | なし | `visited` セットで検出 |
| `node1` を結果に含める | 含まれない | `path-scheme` 側で `(cons node1 p)` |

**`descent3` との比較**:

| 項目 | `descent3` | `path-scheme` |
|---|---|---|
| ゴール判定 | `(= n1 n2)` — 今いるノードがゴールか | `(some #{node2} nbrs)` — 隣接にゴールがあるか |
| `node1 = node2` のケース | `(a)` を即返す | `[end]`（隣接にないため fail） |
| `cond` の順序 | ゴール → 循環 → 子あり | 循環 → 空 → ゴール |

> `path-impl` は println によるトレース出力を含む（デバッグ用）。

```
(n2/path-scheme n/kids  'a 'g) ; → (a c f g)
(c/fail)                        ; → [end]
(n2/path-scheme n2/kids2 'a 'e) ; → (a d e)
(c/fail)                         ; → [end]
```

---

## Scheme 版との比較

### `bf-path` — 幅優先経路探索

原著 Scheme 版の `bf-path` は `call/cc` を使わない純粋な BFS アルゴリズムであり、Clojure に直接翻訳できる。

```scheme
(define (path node1 node2)
  (bf-path node2 (list (list node1))))

(define (bf-path dest queue)
  (if (null? queue)
    '@
    (let* ((path (car queue))
           (node (car path)))
      (if (eq? node dest)
        (cdr (reverse path))           ; ← バグ: cdr で起点を落としてしまう
        (bf-path dest
                 (append (cdr queue)
                         (map (lambda (n) (cons n path))
                              (neighbors node))))))))
```

パスは `(現在ノード ... 起点)` の逆順リストとしてキューに積む。
目的地に到達したとき `(reverse path)` で順方向の経路を返すのが正しいが、
原著では `(cdr (reverse path))` となっており起点が欠落する。
同章の `descent` が `(a c f g)` を返すのに対して `(c f g)` となり、結果の形式が揃わない。

Clojure 版は `nondetermin2.clj` の `bf-path_`（原著直訳・バグあり）と
`bf-path`（修正版）として実装済み（前節参照）。

---

### `true-choose` — 真の非決定性（Scheme `call/cc` 版）

原著 Scheme 版は `call/cc` を2箇所で使い、コード変換なしに非決定性を実現する。

```scheme
(define (true-choose choices)
  (call-with-current-continuation
    (lambda (cc)
      ;; cc = 「この選択肢の値を受け取って残りの計算を続ける」継続
      (set! *paths* (append *paths*
                            (map (lambda (choice)
                                   (lambda () (cc choice)))
                                 choices)))
      (fail))))

(define fail)
(call-with-current-continuation
  (lambda (cc)
    ;; cc = トップレベル（REPL）への継続
    (set! fail
          (lambda ()
            (if (null? *paths*)
              (cc failsym)          ; 全選択肢消尽 → REPL に failsym を返す
              (let ((p1 (car *paths*)))
                (set! *paths* (cdr *paths*))
                (p1)))))))
```

**`call/cc` の役割**

| 箇所 | キャプチャする継続 | 用途 |
|---|---|---|
| `true-choose` 内 | 呼び出し元の文脈全体 | 後で特定の選択肢の値として再開するため |
| `fail` 初期化 | トップレベル（REPL）への戻り口 | 全選択肢消尽時に `failsym` を返すため |

**Clojure では実装できない理由**

| 機能 | Scheme | Clojure / JVM |
|---|---|---|
| `call/cc` | 言語組み込み | **存在しない** |
| 実行スタックの取り出し | 可能 | JVM が非公開 |
| トップレベル継続のキャプチャ | `call/cc` で可能 | 不可能 |

JVM は実行スタック全体を継続オブジェクトとして取り出す手段を提供していない。

**CPS 版との本質的な違い**

```scheme
;; Scheme true-choose: 通常のコードのままで使える
(+ 1 (true-choose '(2 3 4)))
; → 3（最初の選択肢）
; fail → 4、fail → 5、fail → '@
```

```clojure
;; Clojure CPS: すべての関数を CPS スタイルで書く必要がある
(u/=bind [x] (c/choose-bind x '(2 3 4) (u/=values x))
         (u/=values (+ 1 x)))
```

`call/cc` は「コード変換なしに任意の場所で継続をキャプチャできる」点が強みであり、
これが On Lisp で Scheme 版を「真の非決定性」と呼ぶ理由である。
CPS はその代償として **すべての関数を CPS スタイルで書く** 制約を課す。

---

## 原著（Common Lisp）との差異

| 項目 | 原著 CL | Clojure 版 |
|---|---|---|
| 継続スタック | `(defvar *paths* nil)` — リスト | `(def PATHS (atom []))` — ベクタ（`peek`/`pop` で右端 = top） |
| `cut` の実装 | 再帰（末尾最適化あり） | `recur` による末尾再帰 |
| `(rest '(x))` の評価 | `nil`（falsy）→ 単一選択肢では PATHS に積まれない | `()`（truthy）→ 失敗継続が積まれる（動作への影響なし） |
| `*cont*` の管理 | 動的変数 `special variable` | `^:dynamic` Var + `binding` |
| 選択肢のリセット | トップレベル関数で管理 | `(reset! c/PATHS [])` をエントリポイントで明示 |

---

## 参考文献

- Paul Graham『On Lisp』第22章 — Nondeterminism

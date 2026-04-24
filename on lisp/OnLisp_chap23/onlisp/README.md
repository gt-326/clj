# On Lisp — 第22〜23章 Clojure 実装

Paul Graham 著『On Lisp』第22章（非決定性）・第23章（ATN パーサー）を Clojure で実装・探究したプロジェクト。

CPS（継続渡しスタイル）とバックトラッキングを基盤に、拡張遷移ネットワーク（ATN）パーサーを構築する。

## テスト実行

```
lein test
```

119 テスト・196 アサーション（すべてパス）

---

## プロジェクト構成

```
src/onlisp/
├── core.clj                                    # REPL 用エントリポイント（atn1/atn2/atn3/atn9 を require、atn4〜8 はコメントアウト）
├── common/
│   ├── util.clj                               # CPS マクロ基盤（=defn / =bind / =values）
│   ├── util2.clj                              # バックトラック実装①: PATHS atom 版
│   └── util3.clj                              # バックトラック実装②: *k-fail* dynamic var 版
└── chap23/
    ├── atn1.clj                               # 全 defnode バリアント用例（comment ブロック）— S-V 単純文法
    ├── atn2.clj                               # 全バリアント × mods サブネット用例（comment ブロック）— u2/choose_ バグ例含む
    ├── atn3.clj                               # defnode (fast) — mods/pp/np サブネット（comment ブロック）
    ├── atn4.clj                               # defnode (fast) — 完全 ATN (mods/pp/np/s) ※コメントアウト中
    ├── atn5.clj                               # defnode-slow — 完全 ATN ※コメントアウト中
    ├── atn6.clj                               # defnode-slow2 — 完全 ATN ※コメントアウト中（down2 アリティ問題あり→後述）
    ├── atn7.clj                               # defnode-slow3 — 完全 ATN ※コメントアウト中
    ├── atn8.clj                               # defnode-slow4 — 完全 ATN (down3/jump3 使用) ※コメントアウト中
    ├── atn9.clj                               # defnode-slow5 — 完全 ATN (down3/jump3 使用) ★現在 active
    └── common/
        ├── layer1/core.clj                     # defnode マクロ群（6バリアント）
        ├── layer2/
        │   ├── opr.clj                         # category / up / down / jump + down2/3 / jump2/3 / up2 + 辞書
        │   └── reg.clj                         # setr / getr / pushr レジスタ操作
        └── layer3/parser.clj                   # with-parses マクロ（6バリアント）

test/onlisp/
├── common/
│   ├── util_test.clj                          # CPS マクロ（47 テスト）
│   ├── util2_test.clj                         # PATHS 版バックトラック（23 テスト）
│   └── util3_test.clj                         # *k-fail* 版バックトラック（36 テスト）
└── chap23/common/
    ├── layer1/core_test.clj                   # defnode バリアント（17 テスト）
    ├── layer2/
    │   ├── opr_test.clj                       # 辞書操作（12 テスト）
    │   └── reg_test.clj                       # レジスタ操作（5 テスト）
    └── layer3/parser_test.clj                 # パーサー統合テスト（20 テスト）
```

---

## アーキテクチャ全体像

```
[ CPS 基盤 ]          util.clj
    ↓
[ バックトラック ]     util2.clj (PATHS atom) / util3.clj (*k-fail* binding)
    ↓
[ ATN Layer 1 ]       defnode マクロ — ノード定義
[ ATN Layer 2 ]       category / up / down / jump / setr / getr / pushr
[ ATN Layer 3 ]       with-parses — パース実行インターフェース
```

---

## CPS 基盤 — `common/util.clj`

`call/cc` を持たない Clojure/JVM で非決定性を実現するための継続渡しスタイル基盤。

| マクロ | 役割 |
|---|---|
| `=defn` | CPS 関数を定義（マクロ + 関数のペアを生成。完全修飾シンボルで異ネームスペースから呼び出し可能） |
| `=bind` | 継続をバインドして CPS 式を実行（body 内で外側の継続を自動復元） |
| `=values` | 継続を呼び出す（`(*cont* retvals...)`） |
| `=fn` / `=fncall` / `=apply` | 無名 CPS 関数の生成・呼び出し |

### `=defn` の設計

```clojure
;; =defn は 2 つのものを生成する
(u/=defn foo [x]
  body)

;; ↓ 展開結果（概略）
(declare =foo)                           ; 関数の前方宣言
(defmacro foo [x]
  (list 'ns/=foo 'onlisp.common.util/*cont* x))  ; (foo x) → (=foo *cont* x)
(defn =foo [*cont* x]
  body)
```

`(foo x)` と書くだけで CPS 呼び出し `(=foo *cont* x)` に展開される。
完全修飾シンボルを使うことで、異なるネームスペースから呼び出しても正しく解決される。

### バグ版 `=defn_` との違い

```clojure
;; バグ版: defn → defmacro の順（再帰関数でコンパイルエラー）
;; 修正版: declare → defmacro → defn の順（前方宣言で解決）
```

---

## バックトラック実装

### util2.clj — PATHS atom 版

```
PATHS (atom [])  ← 継続のスタック（ベクタ、右端が top）

fail() → PATHS が空なら failsym を返す
       → 非空なら top を pop して呼ぶ（thunk を消費）

cb() / choose / true-choose-simple
  → 残りの選択肢を PATHS に push → 先頭の選択肢を実行
```

**特性**: PATHS は pop により thunk を消費する。
`choose` でバックトラック継続を積む際、その時点の `*cont*` を保存して復元する。

```clojure
;; choose の実装（重要: ~c で保存、~@c ではない）
(fn [c] `(let [saved# u/*cont*]
            (swap! PATHS conj (fn []
                                (binding [u/*cont* saved#]
                                  ~c)))))  ;; ~c = マクロ呼び出しを単一フォームとして保持
```

> **注意（Clojure vs Common Lisp）**:
> `(rest '(x))` = `()` が truthy のため、単一選択肢でも PATHS に継続が積まれる。
> 動作への影響はないが、CL 版より1ステップ余計な `fail` が発生する。

### util3.clj — `*k-fail*` dynamic var 版

```
*k-fail* (^:dynamic)  ← 現在の失敗継続（dynamic binding で管理）

fail() → (*k-fail*) を呼ぶ（PATHS への依存なし）
```

**特性**: `*k-fail*` は binding スコープが続く限り持続する（pop による消費なし）。
これにより **循環**が発生する可能性がある。

```
問題の構造（resolved by saved-k-fail pattern）:

binding [*k-fail* = K]
  arc1 → fail → K が呼ばれる
    arc2 → *cont* = k が呼ばれる
      k の内部: fail → (*k-fail*) = K がまだ有効 → 無限ループ！
```

**解決策**: `saved-k-fail` パターン — 選択肢の最後には外側の `*k-fail*` を使う。

```clojure
(defn true-choose-simple [choices]
  (let [[a & more] choices
        saved-cont   u/*cont*
        saved-k-fail *k-fail*]          ; ← 外側の *k-fail* を保存
    (binding [*k-fail* (if (seq more)
                         (fn []
                           (binding [u/*cont*  saved-cont
                                     *k-fail* saved-k-fail]  ; ← クロージャ内でも復元
                             (true-choose-simple more)))
                         saved-k-fail)] ; ← 最終選択肢: 外側へ委譲
      (a))))
```

### util2 vs util3 の比較

| 項目 | util2 (PATHS atom) | util3 (*k-fail* binding) |
|---|---|---|
| 状態管理 | グローバル atom | thread-local dynamic var |
| thunk の消費 | pop により消費 | binding スコープで持続 |
| 並列パース | PATHS 共有のため不可 | スレッドローカルのため可能 |
| グローバル状態 | `PATHS` atom あり | なし（`*k-fail*` は dynamic） |
| 循環対策 | 不要（pop で消費される） | `saved-k-fail` パターンが必要 |
| mark / cut | `fail` 関数自体を番兵として PATHS に push | `CUT-POINT` atom に `*k-fail*` を保存 |

---

## ATN パーサー — 3層構造

### Layer 1 — `defnode` マクロ群 (`layer1/core.clj`)

ノードを定義するマクロ。内部で `=defn` を使い、CPS 関数として登録する。

| マクロ | シグネチャ | 特徴 |
|---|---|---|
| `defnode` | `[pos arg_regs]` | 基本版。`u2/choose` で選択 |
| `defnode-slow` | `[pos arg_regs]` | `u2/true-choose`（訪問済み thunk をスキップ） |
| `defnode-slow2` | `[pos arg_regs arg_visited]` | atom で訪問管理（バックトラック後も atom に残る） |
| `defnode-slow3` | `[pos arg_regs arg_visited]` | immutable set で訪問管理（arc ごとにスナップショット） |
| `defnode-slow4` | `[pos arg_regs arg_visited arg_sent]` | `o/SENT` グローバル不要（引数で受け渡し） |
| `defnode-slow5` | `[pos arg_regs arg_visited arg_sent]` | `u3/true-choose-simple` 使用（PATHS 不要） |

**slow2 vs slow3 の違い（バックトラック安全性）**:

```
slow2 (atom): arc1 が x-node を訪問済みにすると atom に残る
              → arc2 が x-node へ再入しようとしても blocked → arc2 の探索が制限される

slow3 (set):  arc1 は独立したスナップショットを持つ
              → バックトラック後の arc2 は arc1 前のスナップショットを持つ
              → arc2 も x-node へ再入できる（完全なバックトラック安全性）
```

#### `defnode-declare` — 前方宣言マクロ

循環参照や逆順定義が必要な場合に使用。`defnode` の本体なしでマクロスタブのみを生成する。

```clojure
;; (def np nil) の代わりに使う → Var がマクロとして登録される
(c/defnode-declare np [pos arg_regs])

;; np がマクロとして認識された状態で pp-prep を定義（コンパイル時にマクロ展開される）
(c/defnode pp-prep
  (o/down np pp-np (r/setr op ***)))

;; 後から np の本体を定義（=np 関数が補完される）
(c/defnode np
  ...)
```

> **`(def X nil)` との違い**:
> `(def X nil)` は Var に nil を設定するだけでマクロではない。
> 別ノードのコンパイル時に `(X pos regs)` が関数呼び出しとしてコンパイルされ、
> 後で `defnode X` を定義すると実行時に ArityException が発生する。
> `defnode-declare` は `defmacro` を発行するためコンパイル時に正しく展開される。

#### ノード定義の依存順序

ノードは**参照されるものを先に定義**する必要がある（または `defnode-declare` で前方宣言する）。

```clojure
;; 正しい順序の例（葉から根へ）
(c/defnode mods     (o/up '()))          ; 葉（依存なし）
(c/defnode np-pp    (o/up ...))          ; 葉
(c/defnode np-n     (o/up ...) (o/down _pp np-pp ...))
(c/defnode np-mods  (o/category n np-n ...))
(c/defnode np-det   (o/down mods np-mods ...) (o/jump np-mods ...))
(c/defnode np       (o/category det np-det ...) ...)
```

### Layer 2 — 操作マクロ群 (`layer2/opr.clj`, `layer2/reg.clj`)

#### `opr.clj` — カテゴリ照合とネットワーク遷移

```clojure
;; category: 現在位置の単語がカテゴリに一致すれば次ノードへ
(o/category det np-det (r/setr det ***))
;;   *** = 一致した単語

;; jump: トークンを消費せずに次ノードへ遷移
(o/jump np-det (r/setr det nil))

;; down: サブネットワークを呼び出し、up した値を *** で受け取る
(o/down mods np-mods (r/setr mods ***))
;;   mods サブネットワーク → up した値が *** に束縛 → r/setr でレジスタに格納

;; up: パース結果を返す
(o/up `(~'np (~'det ~(r/getr det)) (~'noun ~(r/getr n))))
```

**`down` の実装（修正済み）**:

```clojure
(defmacro down [sub next & cmds]
  `(u/=bind [~'*** ~'pos ~'arg_regs]           ; *** で up 結果を受け取る
            (~sub ~'pos (cons nil ~'arg_regs))  ; サブネットワーク呼び出し（新フレーム push）
            (~next ~'pos ~(c/compile-cmds cmds)))) ; 継続へ
```

> 修正前: `~'star`（`***` と不一致）、`~'regs`（`arg_regs` と不一致）、
> `(c/gen-reg-data '~cmds)`（存在しない関数）

**`down2` / `down3` の実装**:

```clojure
;; down2: defnode-slow2/3 用
(defmacro down2 [sub next & cmds]
  `(u/=bind [~'*** ~'pos ~'arg_regs]              ; 3引数 bind（arg_visited は受け取らない）
            (~sub ~'pos (cons nil ~'arg_regs) ~'arg_visited)
            (~next ~'pos ~(c/compile-cmds cmds) ~'arg_visited)))

;; down3: defnode-slow4/5 用
(defmacro down3 [sub next & cmds]
  `(u/=bind [~'*** ~'pos ~'arg_regs]
            (~sub ~'pos (cons nil ~'arg_regs) ~'arg_visited ~'arg_sent)
            (~next ~'pos ~(c/compile-cmds cmds) ~'arg_visited ~'arg_sent)))
```

> **設計ポイント（down2 の bind が3引数な理由）**:
> `arg_visited` は `atom`（ミュータブル参照）のため、サブネットワーク内の変更は in-place で反映される。
> 返り値として受け取る必要がなく、レキシカルスコープの `arg_visited` をそのまま使える。
> `up` が `(*cont* result pos (rest arg_regs))` と **3引数**で呼ぶ設計と一致する。
>
> **バグの経緯（atn6 参照）**: bind を `[*** pos arg_regs arg_visited]`（4引数）にすると、
> `up` の3引数呼び出しとアリティ不一致になり ArityException が発生する。

**`jump` の実装（修正済み）**:

```clojure
(defmacro jump [next & cmds]
  `(~next ~'pos ~(c/compile-cmds cmds)))  ; コンパイル時に cmds を展開
```

> 修正前: `(c/gen-reg-data '~cmds)` → 存在しない関数

#### `category` / `down` / `jump` バリアント

| マクロ | 使用対象 | 特徴 |
|---|---|---|
| `category` | `defnode` / `defnode-slow` | `@o/SENT` 参照 |
| `category2` | `defnode-slow2/3` | `arg_visited` を次ノードへ渡す |
| `category3` | `defnode-slow4` | `arg_visited` + `arg_sent` を渡す |
| `category4` | `defnode-slow5` | `u3/fail` 使用 |
| `down` | `defnode` / `defnode-slow` | 3引数 bind (`[*** pos arg_regs]`) |
| `down2` | `defnode-slow2/3` | 同上 + `arg_visited` を sub-network・next へ渡す |
| `down3` | `defnode-slow4/5` | 同上 + `arg_visited`・`arg_sent` を渡す |
| `jump` | `defnode` / `defnode-slow` | トークン消費なし遷移 |
| `jump2` | `defnode-slow2/3` | + `arg_visited` を渡す |
| `jump3` | `defnode-slow4/5` | + `arg_visited`・`arg_sent` を渡す |

#### `reg.clj` — レジスタ操作

レジスタは `(((key1 val1) (key2 val2)) outer-frame)` のネストリスト構造。

| マクロ | 動作 |
|---|---|
| `(r/setr k v regs)` | キー `k` に値 `v` をセット（先頭フレームに追加） |
| `(r/getr k)` | キー `k` の値を取得（複数値なら最初の値を返す） |
| `(r/pushr k v regs)` | キー `k` の値リストに `v` を追加（累積） |

```clojure
;; setr vs pushr の違い
(r/setr subj 'runs '(((subj spot))))   ;→ '(((subj runs) (subj spot)))  ; 追加
(r/pushr subj 'runs '(((subj spot))))  ;→ '(((subj runs spot) (subj spot)))  ; 先頭に cons
```

#### 辞書 (`DICTIONARY` atom)

```clojure
(def DICTIONARY
  (atom {'do    '(aux v)
         'time  '(n v)
         'fly   '(n v)
         'like  '(v prep)
         'the   '(det)
         'arrow '(n)
         ...}))

;; 動的な単語登録
(o/register-word! 'cat '(n))
```

#### `compile-cmds` — コマンド列の展開

```clojure
(c/compile-cmds '((r/setr a b) (r/setr c d)))
;→ (r/setr a b (r/setr c d arg_regs))   ; ネストした式に変換・末尾に arg_regs を付与
```

### Layer 3 — パース実行 (`layer3/parser.clj`)

```clojure
(p/with-parses start-node sentence
  body)
```

| マクロ | 対応 defnode | 特徴 |
|---|---|---|
| `with-parses` | `defnode` | `@o/SENT` と `u2/PATHS` を使用 |
| `with-parses-slow` | `defnode-slow` | + `u2/VISITED` リセット |
| `with-parses-slow2` | `defnode-slow2` | `arg_visited` に `(atom #{})` を渡す |
| `with-parses-slow3` | `defnode-slow3` | `arg_visited` に `#{}` を渡す |
| `with-parses-slow4` | `defnode-slow4` | `@o/SENT` 不要（`arg_sent` で渡す） |
| `with-parses-slow5` | `defnode-slow5` | `u3/*k-fail*` 使用（PATHS 不要） |

---

## 使用例

```clojure
;; シンプルな S-V 文法の定義と実行
(c/defnode end
  (o/up `(~'sentence (~'subject ~(r/getr subj)) (~'verb ~(r/getr v)))))

(c/defnode verb-node
  (o/category verb end (r/setr v ***)))

(c/defnode start
  (o/category noun verb-node (r/setr subj ***)))

(p/with-parses start '(spot runs)
  (println parse))
;→ (sentence (subject spot) (verb runs))
```

```clojure
;; jump の使用例（トークン消費なし）
(c/defnode np-det
  (o/down mods np-mods (r/setr mods ***))   ; mods サブネットワーク経由
  (o/jump np-mods (r/setr mods nil)))        ; または直接 np-mods へ（mods = nil）
```

---

## テスト詳細

### テストファイルと件数

| ファイル | テスト数 | 主な対象 |
|---|---|---|
| `util_test.clj` | 47 | `=defn` / `=bind` / `=values` / 再帰・CPS 動作 |
| `util2_test.clj` | 23 | `fail` / `cb` / `choose` / `mark` / `cut` / `true-choose` |
| `util3_test.clj` | 36 | `*k-fail*` / `cb` / `true-choose-simple` / `saved-k-fail` パターン |
| `core_test.clj` | 17 | `set-register` / `compile-cmds` / `defnode-slow2〜5` / `defnode-declare` |
| `opr_test.clj` | 12 | 辞書 lookup / `register-word!` / fixture による独立性 |
| `reg_test.clj` | 5 | `getr`（単一・複数値）/ `pushr` vs `setr` |
| `parser_test.clj` | 20 | `with-parses〜slow5` 統合テスト / `down` / `jump` 動作確認 / `down2` 回帰テスト |
| **合計** | **160** | |

> `lein test` の実行件数は 117 件（重複カウントの差異による）

### テストのポイント

**`testing` ブロック間の PATHS 共有**:
`use-fixtures :each` は `deftest` 単位でリセットするが、同一 `deftest` 内の `testing` ブロック間では PATHS が引き継がれる。絶対値で状態を検証するテストは `deftest` を分割すること（`mark_*` テストを参照）。

**`defnode-declare` テスト** (`core_test.clj`):
```clojure
;; マクロ登録確認
(is (-> #'decl-forward meta :macro))   ; :macro true であること

;; 前方参照の動作確認
;; 本体定義より先にコンパイルされた decl-user が正常動作すること
(binding [u/*cont* (fn [a b c] [a b c])]
  (is (= [:arrived 1 '()] (decl-user 0 '()))))
```

**`down` / `jump` 修正後の動作確認** (`parser_test.clj`):
```clojure
;; down: サブネットワークの up 結果が *** に束縛されること
(p/with-parses dtest-main '(arrow) ...)  ;→ '(found arrow)

;; jump: トークン消費なく次ノードへ遷移しレジスタがセットされること
(p/with-parses jtest-start '() ...)      ;→ '(jumped nil)
```

---

## 設計上の重要ポイント

### `~c` vs `~@c`（`choose` マクロ）

```clojure
;; バグ版: ~@c でスプライスすると (o/up ...) の o/up が単体式になり
;; "Can't take value of a macro" エラーが発生する
(swap! PATHS conj #(~@c))

;; 正しい版: ~c で単一フォームとして挿入する
(fn [] ~c)
```

### ネームスペース完全修飾

```clojure
;; =defn は異ネームスペースからの呼び出しでも動作するよう
;; マクロ内で完全修飾シンボルを生成する
ns    = (name (ns-name *ns*))
qf    = (symbol ns f-str)           ; 例: 'onlisp.core/=np
qcont = (symbol "onlisp.common.util" "*cont*")
```

---

## ATN ファイルの進化（atn1〜9）

| ファイル | defnode バリアント | ATN 範囲 | 備考 |
|---|---|---|---|
| `atn1` | 全5バリアント | S-V 単純文法 | comment ブロックのみ |
| `atn2` | 全5バリアント | mods サブネット | comment ブロック。`choose_` バグ例含む |
| `atn3` | `defnode` | mods/pp/np サブネット | comment ブロック（s ネット未実装） |
| `atn4` | `defnode` | 完全 ATN | 実行コードあり。現在コメントアウト中 |
| `atn5` | `defnode-slow` | 完全 ATN | 同上 |
| `atn6` | `defnode-slow2` | 完全 ATN | 同上。`down2` アリティ問題あり（後述） |
| `atn7` | `defnode-slow3` | 完全 ATN | 同上 |
| `atn8` | `defnode-slow4` | 完全 ATN | 同上。`down3`/`jump3` 使用 |
| `atn9` | `defnode-slow5` | 完全 ATN | **現在 active**。`down3`/`jump3` 使用 |

---

## 既知の問題

### atn6 — `down2` アリティ不一致（修正済み）

`defnode-slow2` + `down2` において、`down2` の `=bind` を **4引数** (`[*** pos arg_regs arg_visited]`) にしていたことで ArityException が発生していた。

**現象**: `(p/with-parses-slow2 np '(arrows) ...)` で例外。

**原因の連鎖**:
1. `np-det` の arc1 `(down2 mods np-mods ...)` が `*cont*` を **4引数** の関数に差し替える
2. `mods` → `mods-n` と進み、`mods-n` の `up` が `(*cont* result pos (rest arg_regs))` と **3引数**で呼ぶ
3. アリティ不一致 → `ArityException`

```
'(it)  では mods-n まで到達しないため発生しない（it は名詞でないので mods が即 fail）
'(arrows) では arrows が名詞のため mods-n まで進み、up が呼ばれて例外発生
```

**対処**: `down2` の bind を `[*** pos arg_regs]`（3引数）に変更。`arg_visited` は atom のため in-place で共有される。現在の `opr.clj` では修正済み。

---

## 原著との差異

| 項目 | 原著 Common Lisp / Scheme | Clojure 版 |
|---|---|---|
| 継続キャプチャ | `call/cc` で任意の場所から | `=defn` / `=bind` による明示的 CPS |
| 非決定性 | `call/cc` による真の非決定性 | CPS + `*cont*` dynamic var による模倣 |
| 継続スタック | `*paths*` リスト | `PATHS` atom ベクタ（`peek`/`pop`） |
| `(rest '(x))` | `nil`（falsy）→ 1選択肢は積まない | `()`（truthy）→ 1選択肢でも積む（動作に影響なし） |
| `cut` | 再帰 | `recur` による末尾再帰 |
| ATN グラフ構造 | グローバル `*sent*` | `@o/SENT` atom または引数渡し（`defnode-slow4/5`） |

---

## 参考文献

- Paul Graham『On Lisp』第22章 — Nondeterminism
- Paul Graham『On Lisp』第23章 — Parsing with ATNs

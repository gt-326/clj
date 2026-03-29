# On Lisp — Clojure 移植 (chap18 / chap19)

Paul Graham 著『On Lisp』第18章・第19章のコードを Clojure に移植したプロジェクト。
パターンマッチングと Prolog 風クエリエンジンの2系統を実装している。

---

## 構成

```
src/onlisp/
├── common/
│   ├── util1.clj          共通ユーティリティ（match, vars-in, aif 等）
│   └── util2.clj          共通ユーティリティ（with-gensyms, gensym? 等）
├── store.clj              ファクト DB（make-db, db-push, fact, gen-facts）
├── chap18/
│   ├── destructuring.clj  分配束縛（destruc, dbind, with-struct 等）
│   ├── slow.clj           パターンマッチャー 遅い版（if-match）
│   └── quick.clj          パターンマッチャー 速い版（if-match-quick）
└── chap19/
    ├── interpreter.clj    クエリインタープリタ（with-answer）
    └── compiler.clj       クエリコンパイラ（with-answer-compile）

test/onlisp/
├── common/util1_test.clj
├── store_test.clj
├── chap18/quick_test.clj
└── chap19/compiler_test.clj
```

---

## モジュール説明

### common/util1.clj

パターンマッチングとクエリエンジン全体の基盤となるユーティリティ。

| 関数/マクロ | 概要 |
|---|---|
| `match x y [binds]` | 単一化。パターン `x` と値 `y` を照合し、バインディングマップを返す |
| `vars-in expr` | 式中のパターン変数（`?` 始まりシンボル）をセットで収集する |
| `varsym? x` | `?` で始まるシンボルかを判定する |
| `my-binding x binds` | バインディングマップから `x` の値を取り出す |
| `aif`, `aif2`, `acond2` | CL の anaphoric マクロ群 |

### common/util2.clj

コード生成サポート。

| 関数/マクロ | 概要 |
|---|---|
| `with-gensyms syms & body` | 各シンボルを gensym に束縛して body を評価する |
| `simple? x` | アトムまたはクォートリテラルかを判定する（パターン再帰の終端判定） |
| `gensym? s` | `G__` プレフィックスの gensym シンボルかを判定する |
| `need-to-quote? x` | コード生成時に追加クォートが不要かを判定する |

### store.clj

Prolog 風のファクト DB（インメモリ atom）。

| 関数/マクロ | 概要 |
|---|---|
| `make-db` | 空の DB マップを作成する |
| `clear-db [db]` | DB をリセットする |
| `db-push key val [db]` | キーに値を追加する（`conj` で先頭に積む） |
| `db-query key [db]` | `{:val list :found bool}` を返す |
| `fact pred & args` | `(fact painter hogarth william english)` のように事実を登録するマクロ |
| `gen-facts` | サンプルデータ（painter / dates）を投入する |

サンプルデータ（`gen-facts` で投入）:

```
painter: canale antonio venetian
         hogarth william english
         reynolds joshua english

dates:   canale  1697 1768
         hogarth 1697 1772
         reynolds 1723 1792
```

### chap18/destructuring.clj

CL の `destructuring-bind` に相当する分配束縛マクロ群。

| 関数/マクロ | 概要 |
|---|---|
| `destruc pat seq fnc n is-nested` | バインディング式のリストを生成する（`drop` で末尾処理） |
| `destruc2 pat seq fnc n is-nested` | `destruc` の入れ子対応版（`nth` で末尾処理） |
| `call_destruc / call_destruc2` | `destruc / destruc2` の呼び出しラッパー |
| `dbind pat seq & body` | `destruc` を使った分配束縛マクロ |
| `with-matrix`, `with-array`, `with-struct` | 行列・配列・レコードへの分配束縛マクロ |

### chap18/slow.clj — パターンマッチャー（遅い版）

`if-match` マクロ。`util1/match` を**実行時**に呼び出す単純な実装。
`seq` はコンパイル時に固定される。

```clojure
(if-match (?x ?y) '(hi ho) [?x ?y])
;=> [hi ho]
```

### chap18/quick.clj — パターンマッチャー（速い版）

`if-match-quick` / `if-match-quick2` マクロ。パターンマッチのコードを**コンパイル時**に生成する高速版。
入れ子パターンにも対応。`destruc2` でパターンをフラット化し、`gen-match` で `let` の入れ子を構築する。

| マクロ/関数 | seq の評価タイミング | 備考 |
|---|---|---|
| `if-match-quick` | コンパイル時固定 | `slow/if-match` の高速版 |
| `if-match-quick2` | 実行時（変数・式を渡せる） | `pat-match2` / `match2-rt` を使用 |

```clojure
;; コンパイル時 seq
(if-match-quick (?x ?y) '(hi ho) [?x ?y] "else")
;=> [hi ho]

;; 実行時 seq（変数を渡せる）
(defn abab7 [s else]
  (if-match-quick2 (?x ?y ?z) s [?x ?y ?z] else))
(abab7 '(1 2 3) "else")
;=> [1 2 3]
```

### chap19/interpreter.clj — クエリインタープリタ

`with-answer` マクロ。クエリを**マクロ展開時**に `interpret-query` で評価し、
結果のバインディングマップをリテラルとして展開コードに埋め込む。

```clojure
(store/gen-facts)

(with-answer (painter hogarth ?x ?y) [?x ?y])
;=> ([william english])

(with-answer (and (painter ?x _ _) (dates ?x 1697 _)) ?x)
;=> (hogarth canale)

(abab str)
;=> ("reynolds")   ; 誕生年がヴェネチア人と重複しない英国人画家
```

### chap19/compiler.clj — クエリコンパイラ

`with-answer-compile` マクロ。クエリを**コンパイル時**にパターンマッチコードへ変換し、
実行時に `volatile!` で結果を蓄積する高速版。

```clojure
;; ns ロード時に gen-facts が自動投入される
(with-answer-compile (painter 'hogarth ?x ?y) (list ?x ?y))
;=> [(william english)]

(with-answer-compile
  (and (painter ?x _ _) (dates ?x _ ?d) (clj (< 1770 ?d 1800)))
  (list ?x ?d))
;=> [(reynolds 1792) (hogarth 1772)]

(abab 'english 1697)
;=> [[hogarth william 1697 1772]]
```

クエリ演算子:

| 演算子 | 概要 |
|---|---|
| `and` | 全節が成功した場合のみマッチ |
| `or` | いずれか1節が成功すればマッチ（全節を試みる） |
| `not` | 節がマッチしない場合のみ通過 |
| `clj` | 任意の Clojure 式をガード条件として埋め込む |

---

## インタープリタ vs コンパイラ

| | `with-answer`（interpreter） | `with-answer-compile`（compiler） |
|---|---|---|
| クエリ評価 | マクロ展開時に `interpret-query` を実行 | コンパイル時に `pat-match` コードを生成 |
| 速度 | 実行時にバインドを解決（遅い） | コンパイル済みコードを直接実行（速い） |
| `seq` 渡し | コンパイル時のみ | コンパイル時のみ |
| DB 参照 | マクロ展開時（`interpret-query` 内） | マクロ展開時（`compile-simple` 内） |

---

## テスト

```
lein test
```

| テストファイル | 対象 | tests / assertions |
|---|---|---|
| `common/util1_test.clj` | `match`, `varsym?`, `vars-in` | 8 / 41 |
| `store_test.clj` | `make-db`, `clear-db`, `db-query`, `db-push`, `fact`, `gen-facts` | 6 / 25 |
| `chap18/quick_test.clj` | `if-match-quick`, `if-match-quick2`, `abab4`〜`abab7` | 9 / 22 |
| `chap19/compiler_test.clj` | `with-answer-compile`, `abab` | 6 / 13 |

---

## 依存関係

```
util1, util2
    └── store
    └── chap18/destructuring
            └── chap18/slow   (if-match)
            └── chap18/quick  (if-match-quick / if-match-quick2)
                        └── chap19/interpreter  (with-answer)
                        └── chap19/compiler     (with-answer-compile)
```

---

## License

Copyright © 2026

Eclipse Public License 2.0

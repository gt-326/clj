# On Lisp — Clojure 移植 (chap18 / chap19 / chap24)

Paul Graham 著『On Lisp』第18章・第19章・第24章のコードを Clojure に移植したプロジェクト。
パターンマッチング、Prolog 風クエリエンジン（コンパイル型）、継続ベース Prolog インタープリタ／コンパイラの3系統を実装している。

---

## 構成

```
src/onlisp/
├── common/
│   ├── util1.clj          共通ユーティリティ（match, vars-in, aif 等）
│   ├── util2.clj          共通ユーティリティ（with-gensyms, gensym? 等）
│   ├── util3.clj          CPS / バックトラック・ユーティリティ（=defn, =bind, fail 等）
│   └── util4.clj          gensym 変数用ユーティリティ（match2, fullbind2 等）
├── store.clj              ファクト DB（make-db, db-push, fact, gen-facts）
├── chap18/
│   ├── destructuring.clj  分配束縛（destruc, dbind, with-struct 等）
│   ├── slow.clj           パターンマッチャー 遅い版（if-match）
│   └── quick.clj          パターンマッチャー 速い版（if-match-quick）
├── chap19/
│   ├── interpreter.clj    クエリインタープリタ（with-answer）
│   └── compiler.clj       クエリコンパイラ（with-answer-compile）
└── chap24/
    └── prolog/
        ├── interpreter.clj  Prolog インタープリタ（with-inference）
        ├── compiler.clj     Prolog コンパイラ（with-inference2）
        └── compiler2.clj    Prolog 拡張コンパイラ（with-inference3 / cut / clj / is）

test/onlisp/
├── common/
│   ├── util1_test.clj
│   └── util4_test.clj
├── store_test.clj
├── chap18/quick_test.clj
├── chap19/compiler_test.clj
└── chap24/prolog/
    ├── interpreter_test.clj
    ├── compiler_test.clj
    └── compiler2_test.clj
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
| `my-binding x binds` | バインディングマップから `x` の値を取り出す（チェーン展開対応） |
| `aif`, `aif2`, `acond2` | CL の anaphoric マクロ群 |

### common/util2.clj

コード生成サポート。

| 関数/マクロ | 概要 |
|---|---|
| `with-gensyms syms & body` | 各シンボルを gensym に束縛して body を評価する |
| `simple? x` | アトムまたはクォートリテラルかを判定する（パターン再帰の終端判定） |
| `gensym? s` | `G__` プレフィックスの gensym シンボルかを判定する |
| `need-to-quote? x` | コード生成時に追加クォートが不要かを判定する |

### common/util3.clj

CPS（継続渡しスタイル）とバックトラックの基盤。chap20・chap22 の実装を移植したもの。

**CPS マクロ群（chap20）:**

| 関数/マクロ | 概要 |
|---|---|
| `*cont*` | 現在の継続を保持するダイナミック Var（初期値: `identity`） |
| `=defn name params & body` | CPS 関数を定義する。内部関数 `=name` とマクロ `name` を同時に生成する |
| `=fn params & body` | 無名 CPS 関数を生成する |
| `=bind [params] expr & body` | `expr` の継続として `(fn [params] body)` を `*cont*` に束縛して実行する |
| `=values & retvals` | 現在の継続 `*cont*` に値を渡す |

**バックトラック機構（chap22）:**

| 関数/マクロ | 概要 |
|---|---|
| `*paths*` | バックトラック選択肢を積むスタック（`atom []`） |
| `fail` | `*paths*` から次の選択肢を取り出して実行する。空なら `[end]` を返す |
| `conc1f obj` | `obj` をリスト末尾に追加する関数を返すマクロ |
| `cb fnc choices` | `choices` を1つずつ `fnc` に渡し、残りを `*paths*` に積む |
| `choose-bind param choices & body` | `cb` のマクロラッパー |
| `choose & choices` | 各選択肢を `*paths*` に積み、最初の選択肢を実行する |

### common/util4.clj

gensym 変数（`G__` プレフィックス）専用の単一化とバインド展開ユーティリティ。
chap24 のコンパイラ系（`compiler.clj` / `compiler2.clj`）で使用される。
`util1` の `?` 変数系と対比して、コード生成時の gensym 変数を扱う。

| 関数/マクロ | 概要 |
|---|---|
| `varsym? x` | `G__` プレフィックスの gensym シンボルかを判定する |
| `fullbind2 x binds` | gensym 変数のバインディングをチェーン展開して最終値を返す |
| `match2 x y [binds]` | gensym 変数を用いた単一化。パターン `x` と値 `y` を照合しバインディングマップを返す |

### store.clj

Prolog 風のファクト DB（インメモリ atom）。chap19 で使用。

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

### chap24/prolog/interpreter.clj — Prolog インタープリタ

`with-inference` マクロ。`util3` の CPS / バックトラック機構を基盤とした
**実行時** Prolog インタープリタ。ルールはダイナミック Var `*rlist*` に蓄積される。

**ファクト・ルール登録:**

```clojure
;; ファクト（本体なし）
(<- (painter hogarth william english))
(<- (dates hogarth 1697 1772))

;; 本体付きルール（単節）
(<- (english-painter ?x) (painter ?x _ english))

;; 本体付きルール（複数節）
(<- (same-birth ?x ?y)
    (dates ?x ?b _)
    (dates ?y ?b _))
```

**クエリ実行:**

```clojure
;; 単一述語
(with-inference (painter ?x _ english)
  (println ?x))
;=> hogarth
;   reynolds

;; and クエリ
(with-inference (and (painter ?x ?y english) (dates ?x ?b ?d))
  (println ?x ?y ?b ?d))
;=> hogarth william 1697 1772
;   reynolds joshua 1723 1792

;; not クエリ（not 内の変数は束縛されない）
(with-inference (and (painter ?x _ english)
                     (dates ?x ?b _)
                     (not (and (painter ?x2 _ venetian)
                               (dates ?x2 ?b _))))
  (println ?x))
;=> reynolds

;; ルールを使ったクエリ
(<- (english-painter ?x) (painter ?x _ english))
(with-inference (english-painter ?x) (println ?x))
;=> hogarth
;   reynolds
```

クエリ演算子:

| 演算子 | 概要 |
|---|---|
| `and` | 全節が成功した場合のみマッチ。明示的継続渡しで実装 |
| `or` | いずれか1節が成功すればマッチ（全節を試みる） |
| `not` | 節がマッチしない場合のみ通過（否定即失敗）。`not` 内の変数は外に束縛されない |

主要な内部関数:

| 関数/マクロ | 概要 |
|---|---|
| `<- con & ant` | ファクト・ルールを `*rlist*` に登録するマクロ |
| `with-inference query & body` | クエリを実行し、各解でボディを評価するマクロ |
| `prove-query cont expr binds` | クエリを種別（and/or/not/単純）にディスパッチする |
| `prove-and cont clauses binds` | 明示的継続渡しで全節を順に証明する |
| `prove-simple cont query binds` | `*rlist*` の各ルールを試みる |
| `prove-or cont clauses binds` | 各節をバックトラックで試みる |
| `prove-not cont expr binds` | 否定即失敗を実装する |
| `implies cont r query binds` | ルール `r` でクエリを照合し成功なら本体を証明する |
| `change-vars r` | ルール中の変数をすべて新しい gensym にリネームする |
| `fullbind x binds` | バインディングをチェーン展開して最終値を返す |
| `rep_ x` | クエリ中の `_` を gensym に置き換える |

### chap24/prolog/compiler.clj — Prolog コンパイラ

`with-inference2` マクロ。インタープリタ版（`with-inference`）と同じクエリ構文・同じ機能を持ちながら、
ルールを**コンパイル時**に `=fn` クロージャへ変換するコード生成型の Prolog エンジン。
変数には `?` シンボルではなく `with-gensyms` で生成した gensym（`G__` プレフィックス）を使用し、`util4/match2` で照合する。

**ファクト・ルール登録:**

```clojure
(<- (painter hogarth william english))
(<- (english-painter ?x) (painter ?x _ english))
(<- (same-birth ?x ?y) (dates ?x ?b _) (dates ?y ?b _))
```

**クエリ実行:**（構文・結果はインタープリタ版と同一）

```clojure
(with-inference2 (painter ?x _ english) (println ?x))
;=> hogarth
;   reynolds

(with-inference2 (append ?x (c d) (a b c d)) (println ?x))
;=> (a b)
```

主要な内部関数/マクロ:

| 関数/マクロ | 概要 |
|---|---|
| `<- con & ant` | ファクト・ルールを `*rules*` に登録するマクロ |
| `with-inference2 query & body` | クエリをコード生成して実行するマクロ |
| `rule-fn r` | ルールを `=fn` クロージャに変換する |
| `my-form x` | パターン要素を Clojure コードに変換する（gensym 変数・リテラル・クォート） |
| `gen-query cont goal binds` | クエリを種別（and/or/not/単純）にコード生成する |
| `gen-and`, `gen-or`, `gen-not` | 各演算子のコード生成 |
| `gen-simple cont goal binds` | `*rules*` の各ルールを試みるコードを生成する |

### chap24/prolog/compiler2.clj — Prolog 拡張コンパイラ

`with-inference3` マクロ。`compiler.clj` を拡張し、`cut`・`clj`・`is` ゴールを追加。
カット制御のために `paths` パラメータを `=fn` / `gen-*` 全関数にスレッドする。

**新規ゴール:**

| ゴール | 概要 |
|---|---|
| `cut` | カットポイントまでのバックトラック選択肢を廃棄する |
| `clj expr` | 任意の Clojure 式をガード条件として評価する（`with-binds` で変数解決） |
| `is var expr` | Clojure 式の結果を Prolog 変数にバインドする（`match2` で代入） |

**使用例:**

```clojure
;; cut — sculptor に一意に絞り込む
(<- (artist ?x) (sculptor ?x) (cut))
(<- (artist ?x) (painter ?x))
(with-inference3 (artist ?x) (println ?x))
;=> hepworth    ; cut により sculptor の解のみ。painter 節は試みない

;; not-equal — cut + fail パターン
(<- (not-equal ?x ?x) (cut) (fail))
(<- (not-equal ?x ?y))
(with-inference3 (not-equal 'a 'b) (println true))
;=> true

;; clj — Clojure 式をガード条件に
(<- (ordered ?lst) (clj (apply <= ?lst)))
(with-inference3 (ordered '(1 2 3)) (println true))
;=> true

;; is — 算術（階乗）
(<- (factorial 0 1))
(<- (factorial ?n ?r)
    (clj (pos? ?n))
    (is ?m (dec ?n))
    (factorial ?m ?s)
    (is ?r (* ?n ?s)))
(with-inference3 (factorial 5 ?r) (println ?r))
;=> 120
```

主要な内部関数/マクロ:

| 関数/マクロ | 概要 |
|---|---|
| `<- con & ant` | ファクト・ルールを `*rules*` に登録するマクロ |
| `with-inference3 query & body` | クエリをコード生成して実行するマクロ |
| `rule-fn r paths` | `paths` パラメータを加えた `=fn` クロージャを生成する |
| `with-binds binds & body` | バインディングマップを解決して body を評価するマクロ |
| `gen-is cont var expr binds paths` | `is` ゴールのコード生成 |
| `gen-cut cont paths` | `cut` ゴールのコード生成 |
| `gen-clj cont expr binds paths` | `clj` ゴールのコード生成 |

---

## chap24 三層アーキテクチャ

chap24 には Prolog エンジンが3段階で実装されている。

| | `with-inference` | `with-inference2` | `with-inference3` |
|---|---|---|---|
| ファイル | `interpreter.clj` | `compiler.clj` | `compiler2.clj` |
| 変数体系 | `?x`（util1/match） | gensym（util4/match2） | gensym（util4/match2） |
| ルール評価 | 実行時 match | コード生成（=fn） | コード生成（=fn + paths） |
| `cut` | なし | なし | あり |
| `clj` | なし | なし | あり |
| `is` | なし | なし | あり |
| クエリ構文 | 共通（`and` / `or` / `not`） | ← 同左 | ← 同左 + `cut` / `clj` / `is` |

---

## chap19 vs chap24 — クエリエンジンの比較

| | chap19 `with-answer`／`with-answer-compile` | chap24 `with-inference` 系 |
|---|---|---|
| 実装方式 | DB ベース（`store.clj`） | `*rlist*` / `*rules*` ベース（ダイナミック Var） |
| ルール定義 | `fact` マクロ（DB に格納） | `<-` マクロ（リストに追加） |
| クエリ評価 | マクロ展開時（interpreter）/ コンパイル時（compiler） | 実行時 CPS |
| バックトラック | なし | `*paths*` スタックによる深さ優先探索 |
| 本体付きルール | なし（ファクトのみ） | あり（`<-` で複数節ルールを定義可能） |
| `not` | あり（compiler のみ） | あり（否定即失敗） |
| 結果の取り出し | 戻り値（コレクション） | ボディの副作用（atom 等） |

---

## テスト

```
lein test
```

| テストファイル | 対象 | tests / assertions |
|---|---|---|
| `common/util1_test.clj` | `match`, `varsym?`, `vars-in` | 10 / 51 |
| `common/util4_test.clj` | `match2`, `fullbind2`, `varsym?` | 13 / 73 |
| `store_test.clj` | `make-db`, `clear-db`, `db-query`, `db-push`, `fact`, `gen-facts` | 6 / 25 |
| `chap18/quick_test.clj` | `if-match-quick`, `if-match-quick2`, `abab4`〜`abab7` | 9 / 22 |
| `chap19/compiler_test.clj` | `with-answer-compile`, `abab` | 6 / 13 |
| `chap24/prolog/interpreter_test.clj` | `with-inference`、ルール定義、append / member / eats 等 | 12 / 39 |
| `chap24/prolog/compiler_test.clj` | `with-inference2`、ルール定義、append / member / eats 等 | 11 / 28 |
| `chap24/prolog/compiler2_test.clj` | `with-inference3`、cut / clj / is、factorial / quick-sort 等 | 17 / 47 |
| **合計** | | **84 / 298** |

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

util1, util2, util3
    └── chap24/prolog/interpreter  (with-inference)

util1, util2, util3, util4
    └── chap24/prolog/compiler     (with-inference2)
    └── chap24/prolog/compiler2    (with-inference3)
```

---

## License

Copyright © 2026

Eclipse Public License 2.0

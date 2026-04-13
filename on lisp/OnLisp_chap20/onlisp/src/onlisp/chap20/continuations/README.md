# chap20 / continuations

On Lisp 第20章 (P.274) の継続渡しスタイル (CPS) マクロ群を
Clojure で2通りの方法で実装したファイル群です。

```
continuations/
├── atom.clj       継続を atom で保持する実装
└── dynamic.clj    継続を ^:dynamic Var + binding で保持する実装
```

---

## 概要：CPS マクロとは

通常の関数呼び出しでは、戻り値を呼び出し元に返す。
CPS では「次に何をするか（継続）」を関数に渡し、関数はその継続を呼び出す。

```
通常:  (add5 10) → 15
CPS:   (add5 cont 10) → cont(15)  ;; 継続に結果を渡す
```

`=defn`、`=bind`、`=values` の3つが中心的なマクロで、
`=fn`、`=fncall`、`=apply` が補助的に使われる。

---

## 2つの実装の核心的な違い

| | atom.clj | dynamic.clj |
|---|---|---|
| 継続の保持方法 | `(def cont (atom identity))` | `(def ^:dynamic *cont* identity)` |
| 継続の設定 | `(reset! cont fn)` — グローバル破壊的更新 | `(binding [*cont* fn] ...)` — スコープ付き |
| `=bind` 後の状態 | `cont` が新しい fn のまま残る | `*cont*` が元の値に自動的に戻る |
| ネストした `=bind` | inner が outer の継続を上書き・消去する | binding スタックで独立管理される |
| スレッド安全性 | 非安全（グローバル共有状態） | 安全（スレッドローカル） |
| `=values` の展開 | `((deref cont) ...)` | `(*cont* ...)` |

---

## マクロごとの実装比較

### 継続変数の定義

```clojure
;; atom.clj
(def cont (atom identity))        ; atom — deref が必要

;; dynamic.clj
(def ^:dynamic *cont* identity)   ; dynamic Var — 直接参照
```

---

### `=defn` — CPS 関数の定義

`=name` という内部関数と、`name` というマクロを同時に定義する。

```clojure
;; atom.clj
(defmacro =defn [name params & body]
  (let [n        (symbol (str "=" name))
        cont-sym `cont]         ; バッククォートで完全修飾シンボルを取得
    `(do
       (defn ~n [~'cont ~@params] ~@body)
       (defmacro ~name ~params
         (list '~n '~cont-sym ~@params)))))
```

```clojure
;; dynamic.clj
(defmacro =defn [sname params & body]
  (let [f (symbol (str "=" (name sname)))]
    `(do
       (defn ~f [~'*cont* ~@params] ~@body)
       (defmacro ~sname ~params
         `(~'~f *cont* ~~@params)))))
```

**違いのポイント**:

- `atom.clj` は `cont-sym = \`cont` でコンパイル時に
  `onlisp.chap20.continuations.atom/cont` に完全修飾し、
  `(list '~n '~cont-sym ~@params)` でシンボルとして展開する。
  展開先の名前空間で `cont` が未定義になる問題をこの手法で回避した。

- `dynamic.clj` はネストしたバッククォート `` `(~'~f *cont* ~~@params) `` を使う。
  `*cont*` は `^:dynamic` Var のため、`binding` の動的スコープ機構により
  呼び出し時点の束縛が正しく参照される。

**展開結果の比較**（`(add5 10)` の場合）:

```clojure
;; atom.clj が生成するマクロの展開
(=add5 onlisp.chap20.continuations.atom/cont 10)
;;      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 完全修飾シンボル（atom オブジェクト）

;; dynamic.clj が生成するマクロの展開
(=add5 *cont* 10)
;;     ^^^^^^ 現在の動的束縛値
```

---

### `=bind` — 継続を設定して式を評価する

**最も重要な違いがあるマクロ。**

```clojure
;; atom.clj — グローバルに reset! してから expr を評価
(defmacro =bind [params expr & body]
  `(do
     (reset! cont (fn ~params (do ~@body)))
     ~expr))
```

```clojure
;; dynamic.clj — binding スコープ内で expr を評価
(defmacro =bind [params expr & body]
  `(binding
     [*cont* (fn ~params ~@body)]
     ~expr))
```

**展開結果の比較**（`(=bind [y] (=values 7) (* y 10))` の場合）:

```clojure
;; atom.clj の展開
(do
  (reset! onlisp.chap20.continuations.atom/cont
          (fn [y] (do (* y 10))))   ; グローバルに書き換え
  (=values 7))                      ; expr を実行 → cont(7) が呼ばれる
;; =bind が終わっても cont = (fn [y] (* y 10)) のまま残る

;; dynamic.clj の展開
(binding
  [onlisp.chap20.continuations.dynamic/*cont*
   (fn [y] (* y 10))]               ; スコープ内だけ変える
  (=values 7))                      ; binding 内で expr を実行 → *cont*(7)
;; =bind が終わると *cont* は自動的に元の値に戻る
```

---

### `=values` — 継続を呼び出す

```clojure
;; atom.clj — atom を deref して呼び出す
(defmacro =values [& retvals]
  `((deref cont) ~@retvals))

;; dynamic.clj — dynamic Var を直接呼び出す
(defmacro =values [& retvals]
  `(*cont* ~@retvals))
```

---

### `=fn` — 無名 CPS 関数

```clojure
;; atom.clj
(defmacro =fn [params & body]
  `(fn [~'cont ~@params] ~@body))

;; dynamic.clj
(defmacro =fn [params & body]
  `(fn [~'*cont* ~@params] ~@body))
```

どちらも継続を第1引数として受け取る関数を生成する。
`~'cont` / `~'*cont*` はパラメータ名（名前空間修飾できない）のため
クォートシンボルとして埋め込む。

---

### `=fncall` / `=apply` — CPS 関数の呼び出し

```clojure
;; atom.clj
(defmacro =fncall [fnc & params] `(~fnc cont ~@params))
(defmacro =apply  [fnc & args]   `(apply ~fnc cont ~@args))

;; dynamic.clj
(defmacro =fncall [fnc & params] `(~fnc *cont* ~@params))
(defmacro =apply  [fnc & args]   `(apply ~fnc *cont* ~@args))
```

`cont` / `*cont*` は呼び出し時点の継続を第1引数として渡す。

---

## ネストした `=bind` の挙動比較

```clojure
;; 共通のコード構造
(=bind [_y]
  (=bind [_z]          ; inner
    (=values :done)
    :inner-body)
  :outer-body)
```

### atom.clj の実行フロー

```
1. outer =bind: cont ← outer-fn  (reset!)
2. inner =bind: cont ← inner-fn  (reset! — outer-fn が上書きされる)
3. =values :done: (deref cont)(:done) = inner-fn(:done) → :inner-body 実行
4. outer-fn は失われているため :outer-body は実行されない
5. =bind 終了後: cont = inner-fn のまま残る  ← 状態の汚染
```

### dynamic.clj の実行フロー

```
1. outer =bind: (binding [*cont* outer-fn] ...)  スコープ開始
2. inner =bind: (binding [*cont* inner-fn] ...)  ネストして開始
3. =values :done: (*cont*)(:done) = inner-fn(:done) → :inner-body 実行
4. inner binding スコープ終了 → *cont* = outer-fn に戻る
5. outer binding スコープ終了 → *cont* = identity に戻る
6. :outer-body は実行されない（atom と同じ）
7. =bind 終了後: *cont* = identity  ← クリーン
```

**実行結果は同じ**（inner body のみ実行、outer body は実行されない）だが、
**終了後の状態が異なる**。

---

## テストコードの違い

```
test/onlisp/chap20/continuations/
├── atom_test.clj
└── dynamic_test.clj
```

### セットアップ

```clojure
;; atom_test.clj — use-fixtures で毎テスト前にリセット必須
(use-fixtures :each
  (fn [f]
    (reset! a/cont identity)   ; =bind の副作用をクリア
    (f)))

;; dynamic_test.clj — fixtures 不要
;; binding のスコープが自動解放されるため、テスト間で状態が漏れない
```

### 状態の確認方法

```clojure
;; atom_test.clj — atom を deref
@a/cont          ; 現在の継続

;; dynamic_test.clj — Var を直接参照
d/*cont*         ; 現在の継続（binding スコープに従う）
```

### `=bind` 後の状態検証

```clojure
;; atom_test.clj — cont が書き換わったまま残ることを確認
(let [cont-before @a/cont]
  (a/=bind [y] (a/=values 99) (identity y))
  (is (not (identical? cont-before @a/cont))
      "=bind 後、cont は新しい fn のまま残る"))

;; dynamic_test.clj — *cont* が identity に戻ることを確認
(let [cont-before d/*cont*]
  (d/=bind [y] (d/=values 99) (identity y))
  (is (identical? cont-before d/*cont*)
      "=bind 後、*cont* は同一の identity オブジェクトに戻る"))
```

### 繰り返し `=bind` の影響

```clojure
;; atom_test.clj — testing ブロックをまたぐ際に手動リセットが必要
(testing "=bind を繰り返すたびに cont が別の fn に変わる"
  (reset! a/cont identity)   ; ← 前の testing の副作用を消す
  ...)

;; dynamic_test.clj — 手動リセット不要
(testing "=bind を繰り返しても *cont* は毎回 identity に戻る"
  (d/=bind [_] (d/=values 1) :body1)
  (is (= identity d/*cont*))   ; 自動で戻っている
  (d/=bind [_] (d/=values 2) :body2)
  (is (= identity d/*cont*))   ; 2回目も自動で戻っている
  ...)
```

---

## まとめ

`atom.clj` は継続の仕組みを**グローバルな可変状態**で素朴に実装したもので、
CPS の動作原理を理解するための教材として機能する。
ただし継続が蓄積・汚染されるため、そのままでは実用に耐えない。

`dynamic.clj` は Clojure の `^:dynamic` + `binding` を使い、
継続の**スコープ管理をランタイムに委ねる**実装。スレッドローカルで安全であり、
chap22 以降の実用的な CPS 処理（コルーチン、バックトラック等）の基盤となる。

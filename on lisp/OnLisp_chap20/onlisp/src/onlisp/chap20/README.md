# chap20 / continuations.clj 採用判断メモ

`continuations.clj` を chap20 の残り節、および chap22・chap23 の基礎として
正式採用することの妥当性を検討した記録。

---

## 結論：条件付きで妥当、ただし要注意点あり

---

## ファイル構成

```
chap20/
├── continuations.clj        正式採用版（^:dynamic + binding）
└── continuations/
    ├── atom.clj             学習用（継続を atom で保持する実装）
    ├── dynamic.clj          学習用（atom との比較対象）
    └── README.md            両実装の比較ドキュメント
```

`continuations.clj` は `dynamic.clj` の内容を namespace のみ変更したもの。
`atom.clj` / `dynamic.clj` は比較学習用として残す。

---

## 章ごとの要求と continuations.clj の適合性

### chap20 の残り節

**問題なし。**

`=bind` / `=values` / `=defn` の基本的な CPS チェーンは `binding` スコープ内で
完結するため、`continuations.clj` で十分に動作する。

---

### chap22（コルーチン）

**要注意。**

On Lisp のコルーチンは「継続をキューに保存し、後で別のコンテキストから呼び出す」
構造をとる。

```
1. fork : 現在の *cont* をプロセスキューに退避
2. yield: 次のプロセスの継続を取り出して呼ぶ
3.        退避した継続を後から resume する
```

`binding` の特性上、`=bind` のスコープを**抜けた後**に保存済みの継続を呼ぶと、
その時点の `*cont*` はすでに外側の値（`identity`）に戻っている。

```clojure
;; 問題が起きるパターン（概念図）
(def saved (atom nil))

(c/=bind [v]
  (do (reset! saved c/*cont*)   ; ここでは (fn [v] ...) が捕捉できる
      (c/=values 1))
  (println "got" v))

;; =bind スコープ外から resume する場合
(@saved 2)   ; (fn [v] ...) の body 内で c/=values が呼ばれると
             ; その時点の c/*cont* = identity になっている
```

これが問題になるかどうかは、**プロセス再開のコードも CPS スタイルで
書かれているか**による。スケジューラ自身を `=bind` でラップすれば
`*cont*` は正しく設定される。

---

### chap23（バックトラック）

**要注意（chap22 と同じ構造的問題）。**

`choose` / `fail` はチョイスポイントのスタックに継続を積み、`fail` 時に
取り出して呼ぶ。キューから取り出した継続を呼ぶ際、そこが新たな
`binding` スコープ内でなければ `*cont*` は `identity` に戻っている。

---

## atom.clj と dynamic.clj の根本的な違い

On Lisp 原書の `*cont*` は CL の**特殊変数（global mutable）**であり、
`setq` で書き換えられる。これは動作としては `atom.clj` の `reset!` に近い。

| | Lisp 原書 | atom.clj | dynamic.clj |
|---|---|---|---|
| 継続の設定 | `(setq *cont* fn)` | `(reset! cont fn)` | `(binding [*cont* fn] ...)` |
| スコープ | グローバル可変 | グローバル可変 | レキシカルスコープ |
| chap22/23 との親和性 | ◎ | ◎ | △（スコープ管理が必要） |

---

## chap22 / chap23 を実装する際の方針

### 基本方針

`continuations.clj` を採用したまま進める。
以下の点に留意しながら実装する。

### 1. プロセス再開・バックトラックのエントリポイントを必ず `binding` でラップする

スケジューラや `fail` のコードが `*cont*` を設定した上で継続を呼ぶ構造にする。

```clojure
;; 正しいパターン：resume 自体を CPS スタイルで書く
(c/=defn resume [saved-cont result]
  (c/=fncall saved-cont result))
```

### 2. `*cont*` が意図せず `identity` になった場合のサイン

実装中に `*cont*` が `identity` になる現象が起きたら、
**`=bind` スコープ外で `=values` を呼んでいる**サイン。
スコープ設計を見直す。

### 3. それでも解決しない場合の代替案

- `continuations.clj` に `set!` ベースの primitive を追加する
- chap22/23 専用に `atom.clj` 方式に切り替える

---

## chap22 / chap23 での require

```clojure
(ns onlisp.chap22.xxx
  (:require [onlisp.chap20.continuations :as c]))

;; CPS 関数の定義
(c/=defn my-fn [x]
  (c/=values (* x 2)))

;; 継続の束縛
(c/=bind [result]
  (my-fn 5)
  (println "result:" result))
```

---

## まとめ

- **chap20 の残り節**：問題なく動作する
- **chap22/23**：正しく CPS スタイルで書けば機能する可能性が高い
- **摩擦が生じる場合**：On Lisp 原書がグローバル書き換え前提の箇所で
  スコープ設計の見直しが必要になる

`continuations.clj` の採用は妥当な出発点であり、
問題が顕在化した時点で代替策（`atom.clj` 方式への切り替え等）を検討する。

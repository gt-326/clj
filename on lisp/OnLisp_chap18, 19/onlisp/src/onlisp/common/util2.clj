(ns onlisp.common.util2
  (:require
    [onlisp.common.util1 :as util1]))


;; [ P149 chap11 ]

(defmacro with-gensyms
  [syms & body]
  `(let [~@(mapcat (fn [s] `(~s (gensym))) syms)]
     ~@body))


(comment

  ;; syms の各シンボルを gensym に束縛して body を評価する
  (with-gensyms (a b c) [a b c])
  ;=> [G__xxx G__yyy G__zzz]  （実行ごとに異なる）

  )


;; [ P248 - 247 chap18 ]

;; パターンマッチ処理においてネストを再帰しない要素かを判定する。
;; アトム（シンボル・数値・nil 等）またはクォートリテラルであれば simple とみなす。
;; gen-match / gen-match-rt で「このペアは match2 で処理できるか」の判断に使われる。
(defn simple?
  [x]
  (or (util1/cl-atom? x)
      (= (first x) 'quote)))


(comment

  ;; アトム → true
  (simple? 'a)   ;=> true
  (simple? 42)   ;=> true

  ;; クォートリテラル → true
  (simple? ''n)  ;=> true

  ;; ネストしたパターン → false（再帰が必要）
  (simple? '(?x ?y))  ;=> false

  )


(defn gensym?
  [s]
  (and (symbol? s)
       ;; "G__" は Clojure の gensym が生成するデフォルトプレフィックス。
       ;; 文字列操作をする前に長さが 3 以上であることをガードする。
       (>= (count (name s)) 3)
       (= "G__" (subs (name s) 0 3))))


(comment

  ;; Clojure のデフォルト gensym プレフィックス "G__" で判定する
  (gensym? (gensym))  ;=> true
  (gensym? 'foo)      ;=> false
  (gensym? '?x)       ;=> false

  )


;; コード生成時に x に追加のクォートが不要かを判定する。
;; true → そのまま ~x で使える（アトム・nil・空、またはすでに quote 付き）
;; false → データとして扱うには 'x が必要（通常のリスト）
;; match2 の :else 分岐で生成コードの quote 付与を切り替えるために使われる。
(defn need-to-quote?
  [x]
  (or (not (and (seqable? x) (seq x)))
      (= (first x) 'quote)))


(comment

  ;; 数値・nil など非シーケンス → true（そのまま使える）
  (need-to-quote? 42)   ;=> true
  (need-to-quote? nil)  ;=> true

  ;; すでに quote 付き → true（追加のクォート不要）
  (need-to-quote? ''n)  ;=> true

  ;; 通常のリスト → false（データとして扱うにはクォートが必要）
  (need-to-quote? '(a b))  ;=> false

  )

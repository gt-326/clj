(ns onlisp.chap19.interpreter
  (:require
    [onlisp.common.util1 :as util1]
    [onlisp.store :as store]))


(defn lookup
  ([pred args]
   (lookup pred args {}))
  ([pred args binds]
   (do
     ;; (println "- lookup -")
     (mapcat
       (fn [x]
         ;; match の結果を保持させないと、うまく動かなかった
         (let [test (util1/match x args binds)]
           (util1/aif2 test (list it))))
       ((store/db-query pred) :val)))))


(comment

  foo.core> (lookup 'painter '(?x ?y english))
  - lookup -
  ({?x reynolds, ?y joshua} {?x hogarth, ?y william})


  foo.core> (map
             #(util1/match % '(?x ?y english) {})
             ((store/db-query 'painter) :val))
  (nil {?x reynolds, ?y joshua})

  foo.core> (mapcat
             #(util1/match % '(?x ?y english) {})
             ((store/db-query 'painter) :val))
  ([?x reynolds] [?y joshua])


  ;;   (aif2 {:x reynolds, :y joshua} (list it))
  ;;   => Syntax error compiling at (src/foo/core.clj:205:1).
  ;;      Unable to resolve symbol: reynolds in this context

  (util1/aif2 {:x 'reynolds, :y "joshua"} (list it))
  => ({:x reynolds, :y "joshua"})

  )


(declare interpret-query)


(defn interpret-not
  [clause binds]
  (do
    ;; (println "- not -")

    (when-not (seq (interpret-query clause binds))
      (list binds))))


(defn interpret-or
  [clauses binds]
  (do
    ;; (println "- or -")

    (mapcat
      #(interpret-query % binds)
      clauses)))


(defn interpret-and
  [clauses binds]
  (do
    ;; (println "- and -")

    (if-not (seq clauses)
      (list binds)
      (mapcat
        #(interpret-query (first clauses) %)
        (interpret-and (rest clauses) binds)))))


(defn interpret-query
  ([expr] (interpret-query expr {}))
  ([[opr & other] binds]

   ;; (println "_:" opr other)

   (case opr
     and (interpret-and
           ;; 結果の要素順を整えるためのもの
           (reverse other)
           binds)
     or (interpret-or other binds)
     not (interpret-not (first other) binds)
     (lookup opr other binds))))


(comment

   foo.core> (interpret-query '(and (painter ?x ?y ?z) (dates ?x 1697 ?w)))
   - and -
   - and -
   - and -
   - lookup -
   - lookup -
   - lookup -
   - lookup -
   ({?x hogarth, ?y william, ?z english, ?w 1772}
    {?x canale, ?y antonio, ?z venetian, ?w 1768})

)


;; interpret-query をコンパイル時に呼び出し、結果をリテラルとして展開コードに埋め込むマクロ。
;; with-answer-compile（実行時にコードを生成・実行）とは対照的に、
;; クエリの評価はマクロ展開時に完了する。
;; for でバインディングマップを走査し、パターン変数を :let で束縛して body を評価する。

(defmacro with-answer
  [query & body]
  (let [binds (gensym)]
    `(for [~binds '~(set (interpret-query query))
           :let [~@(mapcat
                     ;; キーワードではなく、シンボルをキーにした
                     ;; (fn [v] `(~(symbol v) (my-binding '~(symbol v) ~binds)))
                     (fn [v] `(~v (util1/my-binding '~v ~binds)))
                     (util1/vars-in query))]]
       (do ~@body))))


(defn abab
  [fnc]
  (with-answer
    (and (painter ?x _ english)
         (dates ?x ?b _)
         (not
           (and (painter ?x2 _ venetian)
                (dates ?x2 ?b _))))
    (fnc ?x)))


(comment

  (onlisp.store/gen-facts)

  onlisp.core=> (onlisp.chap19.interpreter/with-answer (painter hogarth ?x ?y) [?x ?y])
  ([william english])

  onlisp.core=> (onlisp.chap19.interpreter/with-answer (and (painter ?x _ _) (dates ?x 1697 _)) ?x)
  (hogarth canale)


  onlisp.core=> (onlisp.chap19.interpreter/with-answer (or (dates ?x ?y 1772) (dates ?x ?y 1792)) [?x ?y])
  ([hogarth 1697] [reynolds 1723])

  onlisp.core=> (onlisp.chap19.interpreter/abab str)
  ("reynolds")

  onlisp.core=> (onlisp.chap19.interpreter/abab #(seq (str %)))
  ((\r \e \y \n \o \l \d \s))

  )

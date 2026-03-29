(ns onlisp.chap19.compiler
  (:require
    [onlisp.chap18.quick :as quick]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]
    [onlisp.store :as store]))


;; ns 読み込み時（= コンパイル時）に DB を投入する
(store/gen-facts)


(declare compile-query)


(defn compile-and
  [clauses body]
  ;; reduce で内側から外側へネストを積み上げるため、clauses を逆順にして渡す。
  ;; (and A B C) → compile-query A (compile-query B (compile-query C body))
  (reduce
    (fn [acc clause] (compile-query clause acc))
    body
    (reverse clauses)))


(defn compile-not
  [q body]
  (let [found (gensym "found")]
    `(let [~found (volatile! false)]
       ~(compile-query (first q) `(vreset! ~found true))
       (when-not @~found
         ~body))))


(defn compile-or
  [clauses body]
  (when (seq clauses)
    (let [gbod     (gensym)
          vars     (util1/vars-in body)
          vars-vec (vec vars)]
      `(letfn [(~gbod ~vars-vec ~body)]
         ~@(map
             (fn [clause]
               (compile-query
                 clause
                 `(~gbod
                   ;; 複数の条件をまたぐ場合（painter と dates など）に、
                   ;; 未割り当てのまま gensym したシンボルが出力されるのを防止したい。
                   ~@(map
                       (fn [v] `(when-not (util2/gensym? ~v) ~v))
                       vars-vec))))
             clauses)))))


(defn compile-simple
  ([opr other body] (compile-simple opr other body nil))
  ([opr other body else]

   ;; =========================================================================
   ;; 修正後（body は letfn に1回だけ書かれ、各 pat-match から呼ぶ）:
   ;; (letfn [(body123 [?x ?b] <body>)]     ← body は1回
   ;;        (list
   ;;         (pat-match other fact1 (body123 ?x ?b) nil)
   ;;         (pat-match other fact2 (body123 ?x ?b) nil)
   ;;         (pat-match other fact3 (body123 ?x ?b) nil)))
   ;; =========================================================================
   (let [facts    ((store/db-query opr) :val)
         gbod     (gensym "body")
         vars     (util1/vars-in body)
         vars-vec (vec vars)]
     `(letfn [(~gbod ~vars-vec ~body)]
        (list
          ~@(for [fact# facts]
              `(quick/pat-match ~other ~fact# (~gbod ~@vars-vec) ~else)))))))


(defn compile-query
  ([[opr & other] body] (compile-query opr other body nil))
  ([opr other body else]
   (case opr
     and (compile-and other body)
     or  (compile-or other body)
     not (compile-not other body)
     ;; clj: 任意の Clojure 式をクエリに埋め込むエスケープハッチ。
     ;; (clj expr) → (if expr body) を生成し、expr が falsy なら body をスキップする。
     clj `(if ~(first other) ~body)
     (compile-simple opr other body else))))


;; compile-query が生成したコードをその場で実行し、マッチした結果をベクタで返すマクロ。
;; 3段構成:
;;   1. volatile! による結果蓄積用アキュムュレータ（acc）を用意する
;;   2. with-gensyms でクエリ内のパターン変数を gensym に束縛し、
;;      compile-query が生成したコードを展開・実行する
;;   3. マッチのたびに body を評価して acc に追加し、最後に @acc を返す

(defmacro with-answer-compile
  [query & body]
  (let [acc (gensym "acc")
        ;; vars-in が返すシンボルのセットを、with-gensyms に渡せるシンボルリストに変換する
        q (map symbol (util1/vars-in query))]
    `(let [~acc (volatile! [])]
       (util2/with-gensyms
         (~@q)
         ~(compile-query query `(vswap! ~acc conj (do ~@body))))
       @~acc)))


(defn abab
  [country y_born]
  (with-answer-compile
    (and
      (painter ?x ?y country)    ; country は実行時評価
      (dates ?x y_born ?y_dead)) ; y_born は実行時評価
    [?x ?y y_born ?y_dead]))


(comment

  onlisp.core=> (onlisp.chap19.compiler/abab 'english 1697)
  [[hogarth william 1697 1772]]

  onlisp.core=> (onlisp.chap19.compiler/abab 'venetian 1697)
  [[canale antonio 1697 1768]]

  onlisp.core=> (onlisp.chap19.compiler/abab 'english 1723)
  [[reynolds joshua 1723 1792]]

  )


(comment


  (defn compile-or_
    [clauses body]
    (when (seq clauses)
      (let [gbod     (gensym)
            vars     (util1/vars-in body)
            vars-vec (vec vars)]
        `(letfn [(~gbod ~vars-vec ~body)]
           ~@(map
              (fn [clause]
                (let [clause-vars (util1/vars-in clause)]
                  (compile-query
                   clause
                   `(~gbod
                     ;; 複数の条件をまたぐ場合（painter と dates など）に、
                     ;; 未割り当てのまま gensym したシンボルが出力されるのを防止したい
                     ~@(map
                        (fn [v] `(when (contains? ~clause-vars ~v) ~v))
                        vars-vec)))))
              clauses)))))

  ;; compile-or_ を使用すると、間違った挙動になる（２つ目の要素が nil で上書きされている）
  (onlisp.chap19.compiler/with-answer-compile
    (and
     (painter ?x ?y _)        ; ?y = william が束縛される
     (or
      (dates ?x ?b _)        ; ?y は clause に無い
      (dates ?x _ ?d)))      ; ?y は clause に無い
    [?x ?y ?b ?d])


  #_=> [[reynolds nil 1723 nil] [reynolds nil nil 1792] [hogarth nil 1697 nil] [hogarth nil nil 1772] [canale nil 1697 nil] [canale nil nil 1768]]

  ;; 正しい挙動
  #_=> [[reynolds joshua 1723 nil] [reynolds joshua nil 1792] [hogarth william 1697 nil] [hogarth william nil 1772] [canale antonio 1697 nil] [canale antonio nil 1768]]


  ;; =========================================================================
  ;; body を letfn で1度だけ定義し、各ファクトの pat-match から呼び出す。
  ;; インラインで body を各コピーに埋め込むと、ネストが深いほど指数的にコードが膨張する。
  ;; =========================================================================
  ;; 修正前（body がファクト数だけコピーされる）:
  ;; (list
  ;;   (pat-match other fact1 <body> nil)   ← body が3コピー
  ;;   (pat-match other fact2 <body> nil)
  ;;   (pat-match other fact3 <body> nil))
  ;; =========================================================================

  (defn compile-simple_
    ([opr other body] (compile-simple_ opr other body nil))
    ([opr other body else]
     (concat
      ;; ガード
      '(list)
      ;; 結果を返したいので、doseq ではなく for を使っている
      (for [fact# ((store/db-query opr) :val)]
        ;; other に抽出条件を保持する
        `(quick/pat-match ~other ~fact# ~body ~else)))))


  onlisp.core=> (onlisp.chap19.compiler/with-answer-compile
                  (painter 'hogarth ?x ?y)
                  (list ?x ?y))

  #_=> [(william english)]

  onlisp.core=> (onlisp.chap19.compiler/with-answer-compile
                  (and
                   (painter ?x _ 'english)
                   (dates ?x ?b _)
                   (not
                    (and
                     (painter ?x2 _ 'venetian)
                     (dates ?x2 ?b _)))))
  #_=> [reynolds]

  onlisp.core=> (onlisp.chap19.compiler/with-answer-compile
                  (and
                   (painter ?x _ _)
                   (dates ?x _ ?d)
                   (clj
                    (< 1770 ?d 1800)))
                  (list ?x ?d))

  #_=> [(reynolds 1792) (hogarth 1772)]


  onlisp.core=> (let [n 70]
                  (onlisp.chap19.compiler/with-answer-compile
                    (and
                     (dates ?x ?b ?d)
                     (clj
                      (> (- ?d ?b) n)))
                    (format "%s lived over %d years." ?x n)))

  #_=> ["hogarth lived over 70 years." "canale lived over 70 years."]

  )

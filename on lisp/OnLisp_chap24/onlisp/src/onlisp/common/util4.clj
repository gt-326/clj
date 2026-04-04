(ns onlisp.common.util4
  (:require
    [clojure.set]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]))


;; [ P342 chap24 ]

;; gensym を変数とみなす版
(defn varsym?
  [x]
  (and (symbol? x)
       (util2/gensym? x)))  ; G__ プレフィックスの判定


;; ========================================================
;; varsym? を再定義し、既出の関数（util1/match）の挙動を変えたい
;; ========================================================

(defn match2
  ([x y] (match2 x y {}))
  ([x y binds]
   (util1/acond2
     ;; [args: variables]
     nil

     ;; [args: clauses]
     (or (= x y) (= x '_) (= y '_)) binds

     (util1/my-binding x binds) (match2 it y binds)
     (util1/my-binding y binds) (match2 x it binds)

     (varsym? x)
     ;; キーワードではなく、シンボルをキーにした
     ;; (assoc binds (keyword (apply str (name x)))

     (do
       ;; (println x ":" y ":" binds)

       (if (map? binds)
         (assoc binds (symbol (apply str (name x)))
                (if (varsym? y)
                  (symbol (apply str (name y)))
                  y))

         (assoc (first binds) (symbol (apply str (name x)))
                (if (varsym? y)
                  (symbol (apply str (name y)))
                  y))))


     (varsym? y)
     ;; キーワードではなく、シンボルをキーにした
     ;; (assoc binds (keyword (apply str (name y)))
     (assoc binds (symbol (apply str (name y)))
            (if (varsym? x)
              (symbol (apply str (name x)))
              x))

     (and (seqable? x)
          (seqable? y)
          (match2 (first x) (first y) binds))
     (match2 (next x) (next y) it)

     :else nil)))


(comment

  ;; パターン変数へのマッチ → バインディングマップを返す
  (match2 '(?x ?y) '(hi ho))
  ;=> {?x hi, ?y ho}

  ;; 既存の binds を引き継ぐ
  (match2 '(?x ?y) '(hi ho) '{?x hi})
  ;=> {?x hi, ?y ho}

  ;; ワイルドカード _ は何にでもマッチし、束縛しない
  (match2 '(?x _ ?z) '(1 2 3))
  ;=> {?x 1, ?z 3}

  ;; リテラルを含むパターン
  (match2 '(a ?x b) '(a 1 b))
  ;=> {?x 1}

  ;; 既存の binds と矛盾する場合 → nil（マッチ失敗）
  (match2 '(?x ?y) '(hi ho) '{?x bye})
  ;=> nil

  ;; リテラルが一致しない場合 → nil
  (match2 '(?x a) '(1 b))
  ;=> nil

  )

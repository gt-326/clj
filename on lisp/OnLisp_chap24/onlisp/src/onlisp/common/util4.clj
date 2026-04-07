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


;; P342

(defn fullbind2
  [x b]
  (cond
    ;; P342 で再定義されている varsym? を使用する
    ;; (util1/varsym? x)
    (varsym? x)

    ;;    (util1/aif
    ;;      (util1/my-binding x b)
    ;;      (fullbind2 it b)
    ;;      (gensym))

    (if (contains? b x)
      (fullbind2 (get b x) b)  ; バインド済み（nil でも）→ 追跡
      (gensym))                ; 真の未束縛変数 → gensym プレースホルダ

    (util1/cl-atom? x)
    x

    ;; ここに追加: (. rest-part) チェーンを seq として解決
    (and (sequential? x)
         ;; 擬似「ドット対」記法への対応
         (= '. (first x)))
    (let [resolved (fullbind2 (second x) b)]
      (cond
        (nil? resolved)        nil
        (sequential? resolved) resolved
        :else                  (list resolved)))

    :else
    (cons
      (fullbind2 (first x) b)
      (fullbind2 (rest x) b))))


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

     ;; nil-value binding:
     ;; my-binding は nil 値のバインディングを見落とす（aif の falsy 判定のため）
     ;; contains? で「バインド済み」かを正確に判定して追跡する
     (and (varsym? x) (contains? binds x))
     (match2 (get binds x) y binds)

     (and (varsym? y) (contains? binds y))
     (match2 x (get binds y) binds)

     (varsym? x)
     (do
       ;; キーワードではなく、シンボルをキーにした
       ;; (assoc binds (keyword (apply str (name x)))

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

     ;; 擬似「ドット対」記法への対応
     (and (sequential? y)
          (= 2 (count y))
          (= '. (first y))
          (varsym? (second y)))
     (assoc binds (symbol (name (second y))) (seq x))

     (and (seqable? x) (seq x)
          (seqable? y) (seq y)
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

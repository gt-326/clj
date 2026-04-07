(ns onlisp.chap24.prolog.interpreter
  (:require
    [clojure.walk :as walk]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util3 :as util3]))


(declare rep_)


;; P335

(def ^:dynamic *rlist* (atom nil))


(defmacro <-
  [con & ant]
  (let [ant
        ;; バッククォートが and を 名前空間付きの clojure.core/and に解決していた。
        ;; 名前空間付きだと prove-query の条件分岐に引っ掛からなかった。
        ;; (if (= (count ant) 1)
        ;;   (first ant)
        ;;   `(and ~@ant))
        (cond
          (= (count ant) 1) (first ant)        ; ルール本体が1つ
          (empty? ant)      '(and)             ; ファクト（本体なし）← quote 使用
          :else             (list* 'and ant))  ; 本体が複数 ← list* で構築
        ]
    `(count
       ((util3/conc1f
          ;; 追加したい新ルール
          (rep_ (cons '~ant '~con)))
        *rlist*))))


(defn change-vars
  [r]
  (walk/postwalk-replace
    (into {}
          (vec
            (map
              (fn [v] [v (symbol (str "?" (gensym)))])
              (util1/vars-in r))))
    r))


(declare prove-query)
(declare prove-and)


;; match の戻り値: nil（失敗）/ {}（変数なし成功）/ {?x val ...}（変数あり成功）
;; → (map? result) で成否を正しく判定できる。

(util3/=defn implies [CONT r query binds]
             (let [r2 (change-vars r)
                   result (util1/match query (rest r2) binds)]

               ;; aif2 が match の戻り値を正しく扱えない
               ;; (util1/aif2
               ;;   (util1/match query (rest r2) binds)
               ;;   (prove-query CONT (first r2) it)
               ;;   (util3/fail))
               (if (map? result)
                 (prove-query CONT (first r2) result)
                 (util3/fail))))


;; P334

;; =defn をつかっているが、マクロ部分を活用していない。
;; 差し障りがないものは、原著に倣ってそのまま =defn を使って定義している。

(util3/=defn prove-simple [CONT query binds]
             (util3/choose-bind r (deref *rlist*)
                                (implies CONT r query binds)))


(util3/=defn prove-or [CONT clauses binds]
             (util3/choose-bind c clauses
                                (prove-query CONT c binds)))


(util3/=defn prove-not [CONT expr binds]
             (let [save-paths (deref util3/*paths*)]
               (reset! util3/*paths* nil)
               (util3/choose
                 (util3/=bind [b]
                              ;; 失敗用継続を使う
                              (prove-query util3/*cont* expr binds)

                              (reset! util3/*paths* save-paths)
                              (util3/fail))
                 (do
                   (reset! util3/*paths* save-paths)
                   ;; 外側の継続を呼ぶ（原著の記述どおりに util3/=values を使うとこうなる）
                   (util3/=values CONT binds)
                   ;; (CONT binds)
                   ))))


(defn prove-and
  [CONT clauses binds]
  (if (empty? clauses)
    (util3/=values CONT binds)
    ;; (CONT binds)

    (prove-query
      #(prove-and CONT (rest clauses) %)
      (first clauses) binds)))


(defn prove-query
  [CONT expr binds]
  (case (first expr)
    and (prove-and CONT (rest expr) binds)
    or  (prove-or CONT (rest expr) binds)
    not (prove-not CONT (first (rest expr)) binds)
    (prove-simple CONT expr binds)))


;; P332 [ top level ]

(defn rep_
  [x]
  (if (util1/cl-atom? x)
    (if (= x '_) (gensym "?") x)
    (cons
      (rep_ (first x))
      (rep_ (rest x)))))


(defn fullbind
  [x b]
  (cond
    (util1/varsym? x)
    (if (contains? b x)
      (fullbind (get b x) b)   ; バインド済み（nil でも）→ 追跡
      (gensym))                ; 真の未束縛変数 → gensym プレースホルダ

    (util1/cl-atom? x)
    x

    ;; ここに追加: (. rest-part) チェーンを seq として解決
    (and (sequential? x)
         ;; 擬似「ドット対」記法への対応
         (= '. (first x)))
    (let [resolved (fullbind (second x) b)]
      (cond
        (nil? resolved)        nil
        (sequential? resolved) resolved
        :else                  (list resolved)))

    :else
    (cons
      (fullbind (first x) b)
      (fullbind (rest x) b))))


(defmacro with-inference
  [query & body]
  `(do
     (reset! util3/*paths* nil)
     (util3/=bind
       [~'binds]
       (prove-query util3/*cont* '~(rep_ query)
                    ;; 引数：binds の初期値に nil を渡すとうまくいかなかった。
                    ;; nil
                    {})

       (let [~@(mapcat
                 (fn [v] `[~v (fullbind '~v ~'binds)])
                 (util1/vars-in query))]
         ~@body
         (util3/fail)))))


(comment

  (do
    (reset! onlisp.chap24.prolog.interpreter/*rlist* nil)

    (onlisp.chap24.prolog.interpreter/<- (painter hogarth  william english))
    (onlisp.chap24.prolog.interpreter/<- (painter reynolds joshua  english))
    (onlisp.chap24.prolog.interpreter/<- (painter canale   antonio venetian))

    (onlisp.chap24.prolog.interpreter/<- (dates hogarth  1697 1772))
    (onlisp.chap24.prolog.interpreter/<- (dates reynolds 1723 1792))
    (onlisp.chap24.prolog.interpreter/<- (dates canale   1697 1768))

    @onlisp.chap24.prolog.interpreter/*rlist*)


  ;; ステップ 1：最もシンプルなクエリ（変数なし）
  (onlisp.chap24.prolog.interpreter/with-inference (painter hogarth william english)
    (println "matched!"))

  ;; ステップ 2：変数1つ
  (onlisp.chap24.prolog.interpreter/with-inference (painter hogarth ?x english)
    (println ?x))
  ;; => william

  ;; ステップ 3：複数の解を列挙
  (onlisp.chap24.prolog.interpreter/with-inference (painter ?x ?y english)
    (println ?x ?y))
  ;; => hogarth  william
  ;;    reynolds joshua

  ;; ステップ 4：and クエリ
  (onlisp.chap24.prolog.interpreter/with-inference (and (painter ?x _ english)
                                                 (dates ?x ?b ?d))
    (println ?x ?b ?d))
  ;; => hogarth 1697 1772
  ;;    reynolds 1723 1792

  ;; ステップ 5：not クエリ
  (onlisp.chap24.prolog.interpreter/with-inference (and (painter ?x ?y ?z)
                                                 (dates ?x _ ?d)
                                                 (not (dates ?x 1723 _)))
    (println ?x ?d))
  ;; => hogarth 1772
  ;;    canale 1768

  ;; ステップ 6：or クエリ
  (onlisp.chap24.prolog.interpreter/with-inference (or (dates ?x 1697 _)
                                                (dates ?x ?b 1793))
    (println ?x))
  ;; => hogarth
  ;;    canale
  )


(comment

  ;; 6. ルールのテスト（発展）

  (do
    (reset! onlisp.chap24.prolog.interpreter/*rlist* nil)

    (onlisp.chap24.prolog.interpreter/<- (likes robin cats))
    (onlisp.chap24.prolog.interpreter/<- (likes kim cats))
    (onlisp.chap24.prolog.interpreter/<- (likes gima dogs))

    (onlisp.chap24.prolog.interpreter/<- (likes sandy ?x) (likes ?x cats))
    (onlisp.chap24.prolog.interpreter/<- (likes denny ?x) (likes ?x dogs))

    @onlisp.chap24.prolog.interpreter/*rlist*)

    (onlisp.chap24.prolog.interpreter/with-inference (likes sandy ?x)
      (println ?x))
  ;; => robin
  ;;    kim

    (onlisp.chap24.prolog.interpreter/with-inference (likes denny ?x)
      (println ?x))
    ;; => gima
  )


;; append

(comment

  (do
    (reset! onlisp.chap24.prolog.interpreter/*rlist* nil)

    (onlisp.chap24.prolog.interpreter/<- (append nil ?xs ?xs))
    (onlisp.chap24.prolog.interpreter/<- (append (?x . ?xs) ?ys (?x . ?zs))
                                         (append ?xs ?ys ?zs))

    @onlisp.chap24.prolog.interpreter/*rlist*)

  (onlisp.chap24.prolog.interpreter/with-inference (append ?x (c d) (a b c d))
    (println (format "Left: %s" ?x)))
  ;; #_=> Left: (a b)
  ;; [end]

  (onlisp.chap24.prolog.interpreter/with-inference (append (a b) ?x (a b c d))
    (println (format "Right: %s" ?x)))
  ;; #_=> Right: (c d)
  ;; [end]

  (onlisp.chap24.prolog.interpreter/with-inference (append (a b) (c d) ?x)
    (println (format "Whole: %s" ?x)))
  ;; #_=> Whole: (a b c d)
  ;; [end]

  (onlisp.chap24.prolog.interpreter/with-inference (append ?x ?y (a b c))
    (println (format "Left: %s Right: %s" ?x ?y)))
  ;; #_=> Left: null Right: (a b c)
  ;; Left: (a) Right: (b c)
  ;; Left: (a b) Right: (c)
  ;; Left: (a b c) Right: null
  ;; [end]

  )


;; member/first-a

(comment

  (do
    (reset! onlisp.chap24.prolog.interpreter/*rlist* nil)

    ;; member
    (onlisp.chap24.prolog.interpreter/<- (member ?x (?x . ?rest)))
    (onlisp.chap24.prolog.interpreter/<- (member ?x (_ . ?rest))
                                      (member ?x ?rest))

    ;; first-a
    (onlisp.chap24.prolog.interpreter/<- (first-a (a _)))

    @onlisp.chap24.prolog.interpreter/*rlist*)


  (onlisp.chap24.prolog.interpreter/with-inference (member a (a b))
    (println true))
  ;; #_=> true
  ;; [end]

  (onlisp.chap24.prolog.interpreter/with-inference (and (first-a ?lst)
                                                      (member b ?lst))
    (println ?lst))
  ;; #_=> (a b)
  ;; [end]

  )

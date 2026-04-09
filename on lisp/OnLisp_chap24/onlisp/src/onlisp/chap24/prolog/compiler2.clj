(ns onlisp.chap24.prolog.compiler2
  (:require
    [onlisp.chap24.prolog.compiler :as pc]
    [onlisp.chap24.prolog.interpreter :as pi]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]
    [onlisp.common.util3 :as util3]
    [onlisp.common.util4 :as util4]))


(declare gen-query)
(declare gen-and)


;; P348

(def ^:dynamic *rules* (atom nil))


(defn rule-fn
  [ant con]
  (util2/with-gensyms
    (my-val fact binds paths) ; paths を追加
    `(util3/=fn
       (~fact ~binds ~paths)
       (util2/with-gensyms
         ~(util1/vars-in (list ant con))
         (let [~my-val
               ;; P342 で再定義されている varsym? を使用する版の match を使用している
               (util4/match2
                 ~fact
                 (list '~(first con) ~@(map pc/my-form (rest con)))
                 ~binds)]
           (if (map? ~my-val)
             ~(gen-query
                ;; util3/=fn 内部で生成される、無名関数の第一引数の名前と合致させる必要がある
                'cont_
                ant my-val
                ;; paths を追加
                paths)
             (util3/fail)))))))


(defmacro <-
  [con & ant]
  (let [ant
        (cond
          (= (count ant) 1) (first ant)        ; ルール本体が1つ
          (empty? ant)      '(and)             ; ファクト（本体なし）← quote 使用
          :else             (list* 'and ant))  ; 本体が複数 ← list* で構築
        ]

    ;; (println con ":" ant)

    `(count
       ((util3/conc1f
          ;; 追加したい新ルール
          ~(rule-fn (pi/rep_ ant) (pi/rep_ con)))
        *rules*))))


(util3/=defn prove [CONT query binds paths]  ; paths を追加
             (util3/choose-bind r (deref *rules*)
                                (util3/=fncall r CONT query binds
                                               ;; paths を追加
                                               paths)))


;; P349

(defn gen-not
  [CONT expr binds paths]
  ;; paths を追加
  (let [gpaths     (gensym)
        fail-cont  `(fn [~'b]
                      (reset! util3/*paths* ~gpaths)
                      (util3/fail))]
    `(let [~gpaths (deref util3/*paths*)]
       (reset! util3/*paths* nil)
       (util3/choose
         ;; 失敗用継続を使う
         ~(gen-query fail-cont expr binds
                     ;; paths を追加
                     paths)
         (do
           (reset! util3/*paths* ~gpaths)
           ;; 外側の継続を呼ぶ（原著の記述どおりに util3/=values を使うとこうなる）
           (util3/=values ~CONT ~binds)
           ;; (~CONT ~binds)
           )))))


(defn gen-or
  [CONT clauses binds paths]
  ;; paths を追加
  `(util3/choose
     ~@(map
         (fn [c] (gen-query CONT c binds
                            ;; paths を追加
                            paths))
         clauses)))


(defn gen-and
  [CONT clauses binds paths]
  ;; paths を追加
  (if (empty? clauses)
    `(~CONT ~binds)
    (let [gb (gensym)]
      (gen-query
        `(fn [~gb] ~(gen-and CONT (rest clauses) gb
                             ;; paths を追加
                             paths))
        (first clauses)
        binds
        ;; paths を追加
        paths))))


(defmacro with-binds
  [binds expr]
  `(let [~@(mapcat
             (fn [v] `(~v (util4/fullbind2 ~v ~binds)))
             (util1/vars-in expr))]
     ~expr))


(defn gen-clj
  [CONT expr binds]
  `(if (with-binds ~binds ~expr)
     (util3/=values ~CONT ~binds)
     (util3/fail)))


(defn gen-is
  [CONT expr1 expr2 binds]
  (let [gb (gensym)]
    `(let [~gb (util4/match2
                 ~expr1
                 (with-binds ~binds ~expr2)
                 ~binds)]
       (if (map? ~gb)
         (util3/=values ~CONT ~gb)   ; 新しい binds（変数が束縛された）を渡す
         (util3/fail)))))


(comment

  (defn gen-is_
    [CONT expr1 expr2 binds]
    `(do
       ;; ~(println "bbb:" CONT ":" expr1 ":" expr2 ":" binds)

       ;; aif2 が「戻り値がリスト [value win-flag] 形式」という前提で設計されている。
       ;; match2 が「成功時はマップ、失敗時は nil」という異なる規約を持っている。
       ;; rule-fn や gen-clj では (map? result) パターンが使われている。

       (util1/aif2
        (util4/match2
         ~expr1
         (with-binds ~binds ~expr2)
         ~binds)
        (util3/=values ~CONT ~'it)
        (util3/fail))))

  )


;; P348

(defn gen-query
  [CONT expr binds paths]
  ;; paths を追加
  (case (first expr)
    and (gen-and CONT (rest expr) binds paths)
    or  (gen-or CONT (rest expr) binds paths)
    not (gen-not CONT (first (rest expr)) binds paths)

    cut `(do
           (reset! util3/*paths* ~paths)
           (util3/=values ~CONT ~binds))
    clj (gen-clj CONT (first (rest expr)) binds)
    is  (gen-is CONT (first (rest expr))
                (first (rest (rest expr))) binds)

    `(prove ~CONT
            (list '~(first expr) ~@(map pc/my-form (rest expr)))
            ~binds
            ;; paths を追加
            (deref util3/*paths*))))


(defmacro with-inference3
  [query & body]
  (let [rep-query  (pi/rep_ query)           ; _ → gensym 変換を一度だけ実行
        all-vars   (util1/vars-in rep-query) ; _ 置換後も含む全変数（with-gensyms 用）
        user-vars  (util1/vars-in query)     ; ユーザー定義変数のみ（let 公開用）
        gb (gensym)]
    `(util2/with-gensyms
       ~all-vars                             ; _ 由来の gensym も束縛する
       (reset! util3/*paths* nil)
       (util3/=bind
         [~gb]
         ~(gen-query
            'onlisp.common.util3/*cont*
            rep-query
            {}
            '(deref onlisp.common.util3/*paths*))
         (let [~@(mapcat
                   (fn [v] `[~v (util4/fullbind2 ~v ~gb)])
                   user-vars)]               ; ユーザー変数だけ公開
           ~@body
           (util3/fail))))))


;; ===================

;; minimum [cut] (P346)

(comment

  ;; cut なし
  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (minimum ?x ?y ?x)
                                       (clj (<= ?x ?y)))

    (onlisp.chap24.prolog.compiler2/<- (minimum ?x ?y ?y)
                                       (clj (> ?x ?y)))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (minimum 1 2 ?x)
    (println ?x))
  ;; #_=> 1
  ;; [end]

  ;; cut あり
  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (minimum ?x ?y ?x)
                                       (clj (<= ?x ?y))
                                       (cut))

    (onlisp.chap24.prolog.compiler2/<- (minimum ?x ?y ?y)
                                       (clj (> ?x ?y)))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (minimum 1 -2 ?x)
    (println ?x))
  ;; #_=> -2
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (minimum 1 2 ?y)
    (println ?y))
  ;; #_=> 1
  ;; [end]
  )


;; artist [cut] (P346 - 357)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (artist ?x) (sculptor ?x))
    (onlisp.chap24.prolog.compiler2/<- (artist ?x) (painter ?x))

    (onlisp.chap24.prolog.compiler2/<- (painter 'klee))
    (onlisp.chap24.prolog.compiler2/<- (painter 'soutine))
    (onlisp.chap24.prolog.compiler2/<- (sculptor 'hepworth))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (artist ?x)
    (println ?x))
  ;; #_=> hepworth
  ;; klee
  ;; soutine
  ;; [end]

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (artist ?x) (sculptor ?x) (cut))
    (onlisp.chap24.prolog.compiler2/<- (artist ?x) (painter ?x))

    (onlisp.chap24.prolog.compiler2/<- (painter 'klee))
    (onlisp.chap24.prolog.compiler2/<- (painter 'soutine))
    (onlisp.chap24.prolog.compiler2/<- (sculptor 'hepworth))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (artist ?x)
    (println ?x))
  ;; #_=> hepworth
  ;; [end]
  )


;; not-equal [cut] (P357)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (not-equal ?x ?x)
                                       (cut)
                                       (fail))
    (onlisp.chap24.prolog.compiler2/<- (not-equal ?x ?y))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (not-equal 'a 'a)
    (println true))
  ;; #_=> [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (not-equal '(a a) '(a b))
    (println true))
  ;; #_=> true
  ;; [end]

  )


;; ordered [clj] (P350)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (ordered (?x)))
    (onlisp.chap24.prolog.compiler2/<- (ordered (?x ?y . ?ys))
                                       (clj (<= ?x ?y))
                                       (ordered (?y . ?ys)))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (ordered '(1 2 3))
    (println true))
  ;; #_=> true
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (ordered '(1 3 2))
    (println true))
  ;; #_=> [end]

  )


;; factorial [clj、is] (P351)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (factorial 0 1))
    (onlisp.chap24.prolog.compiler2/<- (factorial ?n ?f)
                                       (clj (> ?n 0))
                                       (is ?n1 (- ?n 1))
                                       (factorial ?n1 ?f1)
                                       (is ?f (* ?n ?f1)))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (factorial 8 ?x)
    (println ?x))
  ;; #_=> 40320
  ;; [end]

  )


;; quick-sort (P351 - 352)

;; ================================

;; エラーの原因

;; (onlisp.chap24.prolog.compiler2/<- nil ?y nil nil)

;; <- マクロは第1引数をルールのヘッド（con）として受け取ります。

;; ┌───────────────┬────────────────────────────────────────┐
;; │     引数      │              受け取った値                │
;; ├───────────────┼────────────────────────────────────────┤
;; │ con（ヘッド）  │ nil（Clojureのnil値）                   │
;; ├───────────────┼────────────────────────────────────────┤
;; │ ant（ボディ）  │ (?y nil nil) → (and ?y nil nil) に変換  │
;; └───────────────┴────────────────────────────────────────┘

;; ボディ内の ?y が単体のシンボルとして gen-query に渡され、そこで：

;; (case (first expr) ...)  ; expr = ?y（シンボル）

;; (first 'some-symbol) → シンボルから ISeq を生成しようとして：

;; Don't know how to create ISeq from: clojure.lang.Symbol

;; ---
;; 問題の2行

;; ❌ ヘッドが nil（無効）
;; (<- nil ?y nil nil)

;; ❌ 述語名が nil、引数が nil
;; (<- (nil nil))

;; これらは Prolog のルールとして意味をなしません。おそらく意図していたのは：

;; ✓ quick-sort の基底ケース
;; (<- (quick-sort nil nil))

;; ✓ partition の基底ケース（リストが空のとき）
;; (<- (partition nil ?y nil nil))

;; ================================


(comment

  ;; 1 〜 3 の対応が必要だった

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (append nil ?ys ?ys))
    (onlisp.chap24.prolog.compiler2/<- (append (?x . ?xs) ?ys (?x . ?zs))
                                       (append ?xs ?ys ?zs))

    ;; 1. 追加
    (onlisp.chap24.prolog.compiler2/<- (quick-sort nil nil))

    (onlisp.chap24.prolog.compiler2/<- (quick-sort (?x . ?xs) ?ys)
                                       (partition ?xs ?x ?littles ?bigs)
                                       (quick-sort ?littles ?ls)
                                       (quick-sort ?bigs ?bs)
                                       (append ?ls (?x . ?bs) ?ys))

    ;; 2. 追加
    (onlisp.chap24.prolog.compiler2/<- (partition nil ?y nil nil))

    (onlisp.chap24.prolog.compiler2/<- (partition (?x . ?xs) ?y (?x . ?ls) ?bs)
                                       (clj (<= ?x ?y))
                                       (partition ?xs ?y ?ls ?bs))
    (onlisp.chap24.prolog.compiler2/<- (partition (?x . ?xs) ?y ?ls (?x . ?bs))
                                       (clj (> ?x ?y))
                                       (partition ?xs ?y ?ls ?bs))

    ;; 3. コメントアウト
    ;; (onlisp.chap24.prolog.compiler2/<- nil ?y nil nil)

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (quick-sort '(4 1 3 2) ?x)
    (println ?x))
  ;; #_=> (1 2 3 4)
  ;; [end]

  )


;; echo (P352 - 353)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (echo)
                                       (is ?x (read))
                                       (echo ?x))
    (onlisp.chap24.prolog.compiler2/<- (echo 'sayonara!)
                                       (cut))
    (onlisp.chap24.prolog.compiler2/<- (echo ?x)
                                       (clj (do
                                              (println
                                               (format "input: %s"
                                                       (clojure.string/upper-case ?x)))
                                              ?x))
                                       (is ?y (read))
                                       (cut)
                                       (echo ?y))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (echo))

  ;; hi
  ;; input: HI
  ;; ho
  ;; input: HO
  ;; sayonara!
  ;; [end]

  )


;; ====================================
;; 既出（デグれ、漏れがないことの確認のため）
;; ====================================

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (painter hogarth  william english))
    (onlisp.chap24.prolog.compiler2/<- (painter reynolds joshua  english))
    (onlisp.chap24.prolog.compiler2/<- (painter canale   antonio venetian))

    (onlisp.chap24.prolog.compiler2/<- (dates hogarth  1697 1772))
    (onlisp.chap24.prolog.compiler2/<- (dates reynolds 1723 1792))
    (onlisp.chap24.prolog.compiler2/<- (dates canale   1697 1768))

    @onlisp.chap24.prolog.compiler2/*rules*)


  ;; ステップ 1：最もシンプルなクエリ（変数なし）
  (onlisp.chap24.prolog.compiler2/with-inference3 (painter hogarth william english)
    (println "matched!"))

  ;; ステップ 2：変数1つ
  (onlisp.chap24.prolog.compiler2/with-inference3 (painter hogarth ?x english)
    (println ?x))
  ;; => william

  ;; ステップ 3：複数の解を列挙
  (onlisp.chap24.prolog.compiler2/with-inference3 (painter ?x ?y english)
    (println ?x ?y))
  ;; => hogarth  william
  ;;    reynolds joshua

  ;; ステップ 4：and クエリ
  (onlisp.chap24.prolog.compiler2/with-inference3 (and (painter ?x _ english)
                                                 (dates ?x ?b ?d))
    (println ?x ?b ?d))
  ;; => hogarth 1697 1772
  ;;    reynolds 1723 1792

  ;; ステップ 5：not クエリ
  (onlisp.chap24.prolog.compiler2/with-inference3 (and (painter ?x ?y ?z)
                                                 (dates ?x _ ?d)
                                                 (not (dates ?x 1723 _)))
    (println ?x ?d))
  ;; => hogarth 1772
  ;;    canale 1768

  ;; ステップ 6：or クエリ
  (onlisp.chap24.prolog.compiler2/with-inference3 (or (dates ?x 1697 _)
                                                (dates ?x ?b 1793))
    (println ?x))
  ;; => hogarth
  ;;    canale
  )


(comment

  ;; 6. ルールのテスト（発展）

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (likes robin cats))
    (onlisp.chap24.prolog.compiler2/<- (likes kim cats))
    (onlisp.chap24.prolog.compiler2/<- (likes gima dogs))

    (onlisp.chap24.prolog.compiler2/<- (likes sandy ?x) (likes ?x cats))
    (onlisp.chap24.prolog.compiler2/<- (likes denny ?x) (likes ?x dogs))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (likes sandy ?x)
    (println ?x))
  ;; => robin
  ;;    kim

  (onlisp.chap24.prolog.compiler2/with-inference3 (likes denny ?x)
    (println ?x))
  ;; => gima
  )


;; painter (P336 - 337)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (painter ?x)
                                       (hungry ?x)
                                       (smells-of ?x turpentine))

    (onlisp.chap24.prolog.compiler2/<- (hungry ?x)
                                       (or
                                        (gaunt ?x)
                                        (eats-ravenously ?x)))

    (onlisp.chap24.prolog.compiler2/<- (gaunt raoul))
    (onlisp.chap24.prolog.compiler2/<- (smells-of raoul turpentine))
    (onlisp.chap24.prolog.compiler2/<- (painter rubens))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (onlisp.chap24.prolog.compiler2/with-inference3 (painter ?x)
    (println ?x))

  ;; #_=> raoul
  ;; rubens
  ;; [end]

  )


;; eats (P337 - 338)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (eats ?x ?f) (glutton ?x))
    (onlisp.chap24.prolog.compiler2/<- (glutton hubert))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (eats ?x spinach)
    (println ?x))

  ;; #_=> hubert
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (eats ?x ?y)
    (println [?x ?y]))

  ;; #_=> [hubert G__4880]
  ;; [end]


  (onlisp.chap24.prolog.compiler2/<- (eats monster bad-children))
  (onlisp.chap24.prolog.compiler2/<- (eats warhol candy))

  (onlisp.chap24.prolog.compiler2/with-inference3 (eats ?x ?y)
    (println (format "%s eats %s" ?x (if (onlisp.common.util2/gensym? ?y) 'everything ?y))))

  ;; #_=> hubert eats everything
  ;; monster eats bad-children
  ;; warhol eats candy
  ;; [end]

  )


;; append (P338 - 339)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (append nil ?xs ?xs))
    (onlisp.chap24.prolog.compiler2/<- (append (?x . ?xs) ?ys (?x . ?zs))
                                       (append ?xs ?ys ?zs))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (append ?x (c d) (a b c d))
    (println (format "Left: %s" ?x)))
  ;; #_=> Left: (a b)
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (append (a b) ?x (a b c d))
    (println (format "Right: %s" ?x)))
  ;; #_=> Right: (c d)
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (append (a b) (c d) ?x)
    (println (format "Whole: %s" ?x)))
  ;; #_=> Whole: (a b c d)
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (append ?x ?y (a b c))
    (println (format "Left: %s Right: %s" ?x ?y)))
  ;; #_=> Left: null Right: (a b c)
  ;; Left: (a) Right: (b c)
  ;; Left: (a b) Right: (c)
  ;; Left: (a b c) Right: null
  ;; [end]

  )


;; member/first-a (P339 - 340)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    ;; member
    (onlisp.chap24.prolog.compiler2/<- (member ?x (?x . ?rest)))
    (onlisp.chap24.prolog.compiler2/<- (member ?x (_ . ?rest))
                                      (member ?x ?rest))

    ;; first-a
    (onlisp.chap24.prolog.compiler2/<- (first-a (a _)))

    @onlisp.chap24.prolog.compiler2/*rules*)


  (onlisp.chap24.prolog.compiler2/with-inference3 (member a (a b))
    (println true))
  ;; #_=> true
  ;; [end]

  (onlisp.chap24.prolog.compiler2/with-inference3 (and (first-a ?lst)
                                                      (member b ?lst))
    (println ?lst))
  ;; #_=> (a b)
  ;; [end]

  )


;; all-elements (P340 - 341)

(comment

  (do
    (reset! onlisp.chap24.prolog.compiler2/*rules* nil)

    (onlisp.chap24.prolog.compiler2/<- (all-elements ?x nil))
    (onlisp.chap24.prolog.compiler2/<- (all-elements ?x (?x . ?rest))
                                       (all-elements ?x ?rest))

    @onlisp.chap24.prolog.compiler2/*rules*)

  (try
    (doseq [a '(() (1) (2 3) (3 4 5) (4 5 6 7))]
      (onlisp.chap24.prolog.compiler2/with-inference3 (all-elements a ?x)
        (if (< (count ?x) 4)
          (println a ":" ?x)
          (throw (Exception. "quit")))))
    (catch Exception e (.getMessage e)))
  ;; #_=> () : nil
  ;; () : (a)
  ;; () : (a a)
  ;; () : (a a a)
  ;; "quit"

  )

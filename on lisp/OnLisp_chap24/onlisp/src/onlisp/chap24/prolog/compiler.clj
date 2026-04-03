(ns onlisp.chap24.prolog.compiler
  (:require
    [onlisp.chap24.prolog.interpreter :as pi]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]
    [onlisp.common.util3 :as util3]
    [onlisp.common.util4 :as util4]))


(declare gen-query)
(declare gen-and)
(declare my-form)


;; P345

(def ^:dynamic *rules* (atom nil))


(defn rule-fn
  [ant con]
  (util2/with-gensyms
    (my-val fact binds) ; win を削除
    `(util3/=fn
       (~fact ~binds)
       (util2/with-gensyms
         ~(util1/vars-in (list ant con))
         (let [~my-val (util4/match2
                         ~fact
                         (list '~(first con) ~@(map my-form (rest con)))
                         ~binds)]
           (if (map? ~my-val)
             ~(gen-query '*cont* ant my-val)
             (util3/fail)))))))


(defmacro <-
  [con & ant]
  (let [ant
        (cond
          (= (count ant) 1) (first ant)        ; ルール本体が1つ
          (empty? ant)      '(and)             ; ファクト（本体なし）← quote 使用
          :else             (list* 'and ant))  ; 本体が複数 ← list* で構築
        ]
    `(count
       ((util3/conc1f
          ;; 追加したい新ルール
          ~(rule-fn (pi/rep_ ant) (pi/rep_ con)))
        *rules*))))


;; P343

(defn my-form
  [pat]
  (cond
    (util1/varsym? pat) pat          ; ?変数 → そのまま（with-gensyms で束縛済み）
    (symbol? pat)       `'~pat       ; 定数シンボル → クォートして自己評価させる
    (util2/simple? pat) pat          ; 数値・nil 等 → 自己評価なのでそのまま
    :else `(cons
             ~(my-form (first pat))
             ~(my-form (rest pat)))))


(defn prove
  [CONT query binds]
  (util3/cb (fn [r] (r CONT query binds))
            (deref *rules*)))


(defn gen-not
  [CONT expr binds]
  (let [gpaths     (gensym)
        fail-cont  `(fn [~'b]
                      (reset! util3/*paths* ~gpaths)
                      (util3/fail))]
    `(let [~gpaths (deref util3/*paths*)]
       (reset! util3/*paths* nil)
       (util3/choose
         ;; 失敗用継続を使う
         ~(gen-query fail-cont expr binds)
         (do
           (reset! util3/*paths* ~gpaths)
           ;; 外側の継続を呼ぶ
           (~CONT ~binds))))))


(defn gen-or
  [CONT clauses binds]
  `(util3/choose
     ~@(map
         (fn [c] (gen-query CONT c binds))
         clauses)))


(defn gen-and
  [CONT clauses binds]
  (if (empty? clauses)
    `(~CONT ~binds)
    (let [gb (gensym)]
      (gen-query
        `(fn [~gb] ~(gen-and CONT (rest clauses) gb))
        (first clauses)
        binds))))


(defn gen-query
  [CONT expr binds]
  (case (first expr)
    and (gen-and CONT (rest expr) binds)
    or  (gen-or CONT (rest expr) binds)
    not (gen-not CONT (first (rest expr)) binds)
    `(prove ~CONT (list '~(first expr) ~@(map my-form (rest expr)))
            ~binds)))


;; P342

(defn fullbind2
  [x b]
  (cond
    ;; (util1/varsym? x)
    (util4/varsym? x)

    (util1/aif
      (util1/my-binding x b)
      (fullbind2 it b)
      (gensym))

    (util1/cl-atom? x)
    x

    :else
    (cons
      (fullbind2 (first x) b)
      (fullbind2 (rest x) b))))


(defmacro with-inference2
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
         ~(gen-query 'util3/*cont* rep-query {})
         (let [~@(mapcat
                   (fn [v] `[~v (fullbind2 ~v ~gb)])
                   user-vars)]               ; ユーザー変数だけ公開
           ~@body
           (util3/fail))))))


(comment

  (do
    (reset! onlisp.chap24.prolog.compiler/*rules* nil)

    (onlisp.chap24.prolog.compiler/<- (painter hogarth  william english))
    (onlisp.chap24.prolog.compiler/<- (painter reynolds joshua  english))
    (onlisp.chap24.prolog.compiler/<- (painter canale   antonio venetian))

    (onlisp.chap24.prolog.compiler/<- (dates hogarth  1697 1772))
    (onlisp.chap24.prolog.compiler/<- (dates reynolds 1723 1792))
    (onlisp.chap24.prolog.compiler/<- (dates canale   1697 1768))

    @onlisp.chap24.prolog.compiler/*rules*)


  ;; ステップ 1：最もシンプルなクエリ（変数なし）
  (onlisp.chap24.prolog.compiler/with-inference2 (painter hogarth william english)
    (println "matched!"))

  ;; ステップ 2：変数1つ
  (onlisp.chap24.prolog.compiler/with-inference2 (painter hogarth ?x english)
    (println ?x))
  ;; => william

  ;; ステップ 3：複数の解を列挙
  (onlisp.chap24.prolog.compiler/with-inference2 (painter ?x ?y english)
    (println ?x ?y))
  ;; => hogarth  william
  ;;    reynolds joshua

  ;; ステップ 4：and クエリ
  (onlisp.chap24.prolog.compiler/with-inference2 (and (painter ?x _ english)
                                                 (dates ?x ?b ?d))
    (println ?x ?b ?d))
  ;; => hogarth 1697 1772
  ;;    reynolds 1723 1792

  ;; ステップ 5：not クエリ
  (onlisp.chap24.prolog.compiler/with-inference2 (and (painter ?x ?y ?z)
                                                 (dates ?x _ ?d)
                                                 (not (dates ?x 1723 _)))
    (println ?x ?d))
  ;; => hogarth 1772
  ;;    canale 1768

  ;; ステップ 6：or クエリ
  (onlisp.chap24.prolog.compiler/with-inference2 (or (dates ?x 1697 _)
                                                (dates ?x ?b 1793))
    (println ?x))
  ;; => hogarth
  ;;    canale
  )


(comment

  ;; 6. ルールのテスト（発展）

  (do
    (reset! onlisp.chap24.prolog.compiler/*rules* nil)

    (onlisp.chap24.prolog.compiler/<- (likes robin cats))
    (onlisp.chap24.prolog.compiler/<- (likes kim cats))
    (onlisp.chap24.prolog.compiler/<- (likes gima dogs))

    (onlisp.chap24.prolog.compiler/<- (likes sandy ?x) (likes ?x cats))
    (onlisp.chap24.prolog.compiler/<- (likes denny ?x) (likes ?x dogs))

    @onlisp.chap24.prolog.compiler/*rules*)

    (onlisp.chap24.prolog.compiler/with-inference2 (likes sandy ?x)
      (println ?x))
  ;; => robin
  ;;    kim

    (onlisp.chap24.prolog.compiler/with-inference2 (likes denny ?x)
      (println ?x))
    ;; => gima
  )

(ns onlisp.chap24.prolog.compiler
  (:require
    [onlisp.chap24.prolog.interpreter :as pi]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]
    [onlisp.common.util3 :as util3]))


(declare gen-query)
(declare gen-and)
(declare my-form)


;; P345

(def ^:dynamic *rules* (atom nil))


(defn rule-fn
  [ant con]
  (util2/with-gensyms
    (my-val win fact binds)
    `(util3/=fn
       (~fact ~binds)
       (util2/with-gensyms
         ~(util1/vars-in (list ant con))
         (util1/multiple-value-bind
           (~my-val ~win)
           (util1/match
             ~fact
             (list
               '~(first con)
               ~@(map my-form (rest cons)))
             ~binds)
           (if ~win
             ~(gen-query ant my-val)
             (fail)))))))


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
  (if (util2/simple? pat)
    pat
    `(cons
       ~(my-form (first pat))
       ~(my-form (rest pat)))))


(util3/=defn prove [CONT query binds]
             (util3/choose-bind r *rules*
                                (util3/=fncall r query binds)))


(defn gen-not
  [CONT expr binds]
  (let [gpaths (gensym)]
    `(let [~gpaths (deref util3/*paths*)]
       (reset! util3/*paths* nil)
       (util3/choose
         (util3/=bind [b]
                      ;; 失敗用継続を使う
                      ~(gen-query util3/*cont* expr binds)
                      (reset! util3/*paths* ~gpaths)
                      (util3/fail))
         (do
           (reset! util3/*paths* ~gpaths)
           ;; 外側の継続を呼ぶ
           (CONT binds))))))


(defn gen-or
  [CONT clauses binds]
  `(util3/choose
     ~@(map
         (fn [c] (gen-query CONT c binds))
         clauses)))


(defn gen-and
  [CONT clauses binds]
  (if (empty? clauses)
    `(CONT binds)
    (let [gb (gensym)]
      `(util3/=bind [~'gb]
                    ~(gen-query CONT (first clauses) binds)
                    ~(gen-and CONT (rest clauses) gb)))))


(defn gen-query
  [CONT expr & binds]
  (case (first expr)
    and (gen-and CONT (rest expr) binds)
    or  (gen-or CONT (rest expr) binds)
    not (gen-not CONT (first (rest expr)) binds)
    `(prove CONT (list '~(first expr) ~@(map my-form (rest expr)))
            ~binds)))


;; P342


(defmacro with-inference2
  [query & body]
  (let [vars (util1/vars-in query)
        gb (gensym)]
    `(with-gensyms
       ~vars
       (reset! *paths* nil)
       (util3/=bind
         [~gb]
         ~(gen-query (pi/rep_ query))
         (let [~@(mapcat
                   (fn [v] `[~v (pi/fullbind '~v ~gb)])
                   (util1/vars-in query))]
           ~@body
           (util3/fail))))))

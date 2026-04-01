(ns onlisp.chap18.destructuring
  (:require
    [onlisp.common.util2 :as util2]))


(defn destruc
  [arg_pat arg_seq fnc n is-nested]
  (when (seq arg_pat)
    (let [more (cond (= (first arg_pat) '&) (first (rest arg_pat))
                     ;; last item of nested seq
                     (and is-nested (empty? (rest arg_pat))) (first arg_pat)
                     (fnc arg_pat) arg_pat
                     :else nil)
          ;; gensym したシンボル（var）にはクォートを掛けたくない
          target (if (util2/need-to-quote? arg_seq) arg_seq `'~arg_seq)]

      (if more
        `((~more (drop ~n ~target)))

        (let [rec (destruc (rest arg_pat) arg_seq fnc (inc n) is-nested)
              p (first arg_pat)]
          (if (fnc p)
            (cons `(~p (nth ~target ~n nil)) rec)
            (let [var (gensym)]
              (concat
                (cons `(~var (nth ~target ~n  nil)) rec)
                ;; nested seq
                (destruc p var fnc 0 true)))))))))


;; 入れ子にも対応しているので、「destruct」にたいして「2」としている

(defn destruc2
  [arg_pat arg_seq fnc n is-nested]
  (when (seq arg_pat)
    (let [more (cond (= (first arg_pat) '&) (first (rest arg_pat))
                     ;; last item of nested seq
                     (and is-nested (empty? (rest arg_pat))) (first arg_pat)
                     (fnc arg_pat) arg_pat
                     :else nil)
          ;; gensym したシンボル（var）にはクォートを掛けたくない
          target (if (util2/need-to-quote? arg_seq) arg_seq `'~arg_seq)]

      (if more
        ;; 入れ子対応 ============================
        (if (symbol? more)
          `((~more ~(if (= (first arg_pat) '&)
                      `(drop ~n ~target)
                      `(nth ~target ~n nil))))
          (let [var (gensym)]

            (cons
              `(~var (nth ~target ~n nil))
              (destruc2 more var fnc 0 true))))
        ;; ======================================

        (let [rec (destruc2 (rest arg_pat) arg_seq fnc (inc n) is-nested)
              p (first arg_pat)]
          (if (fnc p)
            (cons `(~p (nth ~target ~n nil)) rec)
            (let [var (gensym)]
              (concat
                (cons `(~var (nth ~target ~n nil)) rec)
                ;; nested seq
                (destruc2 p var fnc 0 true)))))))))


;; =============================

(defn call_destruc
  [arg_pat arg_seq & [fnc n]]
  (let [fnc (or fnc #(or (not (and (seqable? %) (seq %)))
                         (and (coll? %) (= (first %) 'quote))))
        n (or n 0)]

    (destruc arg_pat arg_seq fnc n false)))


(defn call_destruc2
  [arg_pat arg_seq & [fnc n]]
  (let [fnc (or fnc #(or (not (and (seqable? %) (seq %)))
                         (and (coll? %) (= (first %) 'quote))))
        n (or n 0)]
    ;; 違い
    (destruc2 arg_pat arg_seq fnc n false)))


;; =============================

(defn dbind-ex
  [binds body]
  (if (empty? binds)
    `(do ~@body)
    `(let [~@(mapcat
               #(if (list? (first %)) (first %) %)
               binds)]
       ~(dbind-ex
          (mapcat
            #(when (list? (first %)) (rest %))
            binds)
          body))))


;; =============================

;; 原著に倣い実装したもの。
;; この dbind を利用したする [onlisp.chap18.quick/match3] だが、
;; そのマクロの利用は必須ではない。

(defmacro dbind
  [arg_pat arg_seq & body]
  (let [gseq (gensym)]
    `(let [~gseq ~arg_seq]
       ~(dbind-ex
          ;; 切替られる
          (call_destruc arg_pat gseq)
          ;; (call_destruc2 arg_pat gseq)
          body))))


;; =============================

(comment

(dbind-ex '((a (nth seq 0))
            ((g2 (nth seq 1))
             (b (nth g2 0))
             (c (drop 1 g2)))
            (d (drop 2 seq)))
          '((list a b c d)))


;; => (clojure.core/let
;;     [a (nth seq 0) g2 (nth seq 1) d (drop 2 seq)]
;;     (clojure.core/let [b (nth g2 0) c (drop 1 g2)] (do (list a b c d))))



(call_destruc '(a b c) '(1 (x y z) 2 3))


;; => ((a (clojure.core/nth '(1 (x y z) 2 3) 0))
;;     (b (clojure.core/nth '(1 (x y z) 2 3) 1))
;;     (c (clojure.core/nth '(1 (x y z) 2 3) 2)))


(call_destruc '(a (b c) & d) 'seq)


;; => ((a (clojure.core/nth seq 0))
;;     (G__10053 (clojure.core/nth 'seq 1))
;;     (d (clojure.core/drop 2 seq))
;;     (b (clojure.core/nth G__10053 0))
;;     (c (clojure.core/drop 1 G__10053)))

(call_destruc '(a (b c) & d) '(1 (x y z) 2 3))


;; => ((a (clojure.core/nth '(1 (x y z) 2 3) 0))
;;     (G__10056 (clojure.core/nth '(1 (x y z) 2 3) 1))
;;     (d (clojure.core/drop 2 '(1 (x y z) 2 3)))
;;     (b (clojure.core/nth G__10056 0))
;;     (c (clojure.core/drop 1 G__10056)))


(macroexpand-1 '(dbind (a (b c) & d) (1 (2 3) 4) (list a b c d)))


;; => (clojure.core/let
;;     [a
;;      (clojure.core/nth '(1 (2 3) 4) 0)
;;      G__10059
;;      (clojure.core/nth '(1 (2 3) 4) 1)
;;      d
;;      (clojure.core/drop 2 '(1 (2 3) 4))
;;      b
;;      (clojure.core/nth G__10059 0)
;;      c
;;      (clojure.core/drop 1 G__10059)]
;;     (do (list a b c d)))
  )


;; =============

(defmacro with-matrix
  [pats ar & body]
  (let [gar (gensym)
        row (atom -1)
        col (atom -1)]
    `(let [~gar ~ar
           ~@(mapcat
               (fn [pat]
                 (do
                   (swap! row inc)
                   (reset! col -1)
                   (mapcat
                     (fn [p]
                       (do
                         (swap! col inc)
                         `(~p (get-in ~gar ~[@row @col]))))
                     pat)))
               pats)]
       ~@body)))


(defmacro with-array
  [pat ar & body]
  (let [gar (gensym)]
    `(let [~gar ~ar
           ~@(mapcat
               (fn [[sym & idx]]
                 `(~sym (get-in ~gar ~(vec idx))))
               pat)]
       ~@body)))


(defmacro with-struct
  [fields struct & body]
  (let [gs (gensym)]
    `(let [~gs ~struct
           ~@(mapcat
               (fn [f]
                 `(~f (~(keyword f) ~gs)))
               fields)]
       ~@body)))


(comment

  (defrecord visitor
      [name title firm])

  (def theo (->visitor "Theodebert" 'king 'franks))

  )

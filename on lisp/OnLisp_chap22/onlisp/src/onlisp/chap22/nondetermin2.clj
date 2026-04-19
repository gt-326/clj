(ns onlisp.chap22.nondetermin2
  (:require
    [onlisp.chap22.common :as c]
    [onlisp.chap22.nondetermin :as n]
    [onlisp.common.util :as u]))


;; [ P305 chap22.4 ]

;; (defn kids
;;  [n]
;;  (case n
;;    a '(b c)
;;    b '(d e)
;;    c '(d f)
;;    f '(g)

;; ガードが必要
;; Execution error (IllegalArgumentException) at foo.core/kids (REPL:840).
;; No matching clause: z
;;    '()))


;; [ P308 chap22.6 ]

(defn bf-path
  [dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]
      (if (= node dest)
        (rest (reverse path))
        (recur dest
               (concat (rest queue)
                       (map #(cons % path)
                            (n/kids node))))))))


(defn path
  [node1 node2]
  (bf-path node2 (list (list node1))))


(comment

;;  onlisp.core=> (n2/path 'a 'g)
;;  (c f g)

;;  onlisp.core=> (n2/path 'a 'd)
;;  (b d)

;;  onlisp.core=> (n2/path 'a 'z)
;;  nil

  )


;; Clojure CPS: コード全体を CPS スタイルに変換する必要がある
(u/=bind [x] (c/choose-bind x '(2 3 4) (u/=values x))
         (u/=values (+ 1 x)))


(comment

;;  onlisp.core=> (u/=bind [x] (c/choose-bind x '(2 3 4) (u/=values x))
;;                         (u/=values (+ 1 x)))
;;  #_=> 3

;;  onlisp.core=> (c/fail)
;;  4

;;  onlisp.core=> (c/fail)
;;  5

;;  onlisp.core=> (c/fail)
;;  [end]

  )


;; ループのある有向グラフ

(defn kids2
  [n]
  (case n
    a '(b d)
    b '(c)
    c '(a)
    d '(e)
    '()))


;; 再帰実装
(u/=defn descent-impl2 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)   ; 循環検出 → バックトラック

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/choose-bind n (graph n1)
                            (u/=bind [p] (descent-impl2 graph n n2 visited#)
                                     (u/=values (cons n1 p)))))

           :else
           (c/fail)))


;; エントリポイント
(u/=defn descent2 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl2 graph n1 n2 #{}))


(comment


;;  onlisp.core=> (n2/descent2 n/kids 'a 'g)
;;  (a c f g)

;;  onlisp.core=> (c/fail)
;;  [end]

;; 循環経路に捕まっていない

;;  onlisp.core=> (n2/descent2 n2/kids2 'a 'e)
;;  (a d e)

;;  onlisp.core=> (c/fail)
;;  [end]

;;  onlisp.core=> (n2/descent2 n2/kids2 'a 'g)
;;  [end]

  )

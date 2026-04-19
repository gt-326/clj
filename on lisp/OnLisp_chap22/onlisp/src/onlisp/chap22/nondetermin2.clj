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


;; ループのある有向グラフ

(defn kids2
  [n]
  (case n
    a '(b d)
    b '(c)
    c '(a)
    d '(e)
    '()))


;; buggy
(defn bf-path_
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


;; buggy
(defn path_
  [node1 node2]
  (bf-path_ node2 (list (list node1))))


;; 決定的探索
;; 以下のように、幅優先探索であれば、循環経路を回避できる。

(defn bf-path
  [graph dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]

      ;; (println path ":" node)

      (if (= node dest)
        ;; (rest (reverse path))
        (reverse path)
        (recur
          graph
          dest
          (concat (rest queue)
                  (map #(cons % path)
                       (graph node))))))))


;; loop/recur 版：

(defn bf-path2
  [graph dest queue]
  (loop [q queue]
    (when (seq q)
      (let [path (first q)
            node (first path)
            node-kids (graph node)]
        (if (= node dest)
          (reverse path)
          (recur
            (concat
              (rest q)
              (map #(cons % path) node-kids))))))))


(defn path
  [graph node1 node2]
  (reset! c/PATHS [])
  ;; (bf-path graph node2 (list (list node1)))
  (bf-path2 graph node2 (list (list node1))))


(comment

;;  onlisp.core=> (n2/path_ 'a 'g)
;;  (c f g)

;;  onlisp.core=> (n2/path_ 'a 'd)
;;  (b d)

;;  onlisp.core=> (n2/path_ 'a 'z)
;;  nil


;;  onlisp.core=> (n2/path n/kids 'a 'g)
;;  (a c f g)

;;  onlisp.core=> (n2/path n2/kids2 'a 'e)
;;  (a d e)

  )


;; こういう書き方もできるよ、というくらいの意味で、ベクタ版をば。

(defn bf-path3
  [graph dest queue]
  (loop [q queue]
    (when (seq q)
      (let [path (first q)
            node (peek path)        ; first → peek（末尾が現在ノード）
            node-kids (graph node)]
        (if (= node dest)
          path                      ; reverse 不要（変換が必要なら、呼び出し元でしてもらう）
          (recur
            (concat
              (rest q)
              (map #(conj path %)   ; cons % path → conj path %
                   node-kids))))))))


(defn path3
  [graph node1 node2]
  (reset! c/PATHS [])
  (bf-path3 graph node2 (list [node1])))  ; (list node1) → [node1]（ベクタ）



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


;; 非決定的探索
;; 以下のように、深さ優先探索でも、一度通った経路かどうかの判断を用意しておけば、
;; 「真の choose」同様の挙動を実現できた。

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

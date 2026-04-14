(ns onlisp.core
  (:require
    [onlisp.chap20.continuations :as c]
    [onlisp.chap20.continuations.atom :as a]
    [onlisp.chap20.continuations.dynamic :as d]
    [onlisp.chap20.continuations2 :as c2]))


;; [ P275 chap20.2 ]

;; message を単独で実行しようとすると、エラーになる。

(c/=defn message []
         (c/=values 'hello 'there))


;; でも、baz のなかで呼び出す形にすると、
;; *cont* の値が上書き（identity -> (fn [m n] (c/=values (list m n)))）
;; されるので、複数個の引数を受け取れるようになるのだが…。

;; ※原著どおりの c/=bind_ の実装だと、この例を実行できなかった。

(c/=defn baz []
         (c/=bind [m n] (message)
                  (c/=values (list m n))))


;; onlisp.core=> (baz)
;; (hello there)


(def foo
  (let [f identity]
    (let [g (fn [x] (f (list 'a x)))]
      (fn [x] (g (list 'b x))))))


(c/=defn add1 [x]
         (c/=values (inc x)))


(let [fnc (c/=fn [n] (add1 n))]
  (c/=bind [y] (c/=fncall fnc 9)
           (format "9 + 1 = %s" y)))


;; #_=> "9 + 1 = 10"


(c/=defn bar [x]
         (c/=values (str "bar: " x)))


(c/=defn baz [x]
         (c/=values (str "baz: " (inc x))))


(c/=defn foo [x]
         (c/=bind [y] (bar x)
                  (println (format "Ho %s" y))
                  (c/=bind [z] (baz x)
                           (println (format "Hum %s." z))
                           (c/=values x y z))))


;; onlisp.core=> (foo 1)
;; Ho bar: 1
;; Hum. baz: 2
;; (1 "bar: 1" "baz: 2")



;; [ P278 chap20.2 ]


(defn dft
  [tree]
  (cond
    (not (list? tree)) (print tree)
    (seq tree)         (do (dft (first tree))
                           (dft (rest tree)))))


(def saved (atom '()))


(c/=defn restart []
         (if-let [[fnc & remaining] (seq @saved)]
           (do (reset! saved remaining)
               (fnc))
           (c/=values '[done])))


;; ===========================================================

;; dft-node_（=values 使用）の限界
;; saved に積むラムダ: (fn [] (dft-node_ rest))
;; → 継続を持たない
;; → =bind スコープ外から呼ばれると c/*cont* = identity → 生の値

;; buggy1
(c/=defn dft-node_ [tree]
         (if (list? tree)
           (if (empty? tree)
             (restart)
             (do
               ;; ガードが必要
               (if (seq (rest tree))
                 ;; PUSH
                 (reset!
                   saved
                   (cons
                     (fn [] (dft-node_ (rest tree)))
                     (deref saved))))

               (dft-node_ (first tree))))
           (c/=values tree)))


;; dft-node（fnc-cont 明示）の正しさ
;; saved に積むラムダ: (fn [] (dft-node fnc-cont rest))
;; → 継続をクロージャでキャプチャ済み
;; → どこから呼ばれても fnc-cont(leaf) = ペア

(c/=defn dft-node [fnc-cont tree]
         (cond
           (not (list? tree)) (fnc-cont tree)
           (empty? tree)      (restart)
           :else              (do
                                (when (seq (rest tree))
                                  (swap! saved conj
                                         (fn [] (dft-node fnc-cont (rest tree)))))
                                (dft-node fnc-cont (first tree)))))


;; ===========================================================


;; works
(c/=defn dft2_ [tree]
         (do
           (reset! saved '())
           (c/=bind
             [node]
             (dft-node_ tree) ; buggy
             (if (= node '[done])
               node
               (do
                 (print node " ")
                 (restart))))))


(c/=defn dft2 [fnc-cont tree]
         (do
           (reset! saved '())
           (c/=bind
             [node]
             (dft-node fnc-cont tree)
             (if (= node '[done])
               node
               (do
                 (print node " ")
                 (restart))))))


(def t1 '(a (b (d h)) (c e (f i) g)))
(def t2 '(1 (2 (3 6 7)) 4 5))


(def a
  (c/=bind [node1] (dft-node c/*cont* t1)
           (if (= node1 '[done])
             node1
             (c/=bind [node2] (dft-node c/*cont* t2)
                      [node1 node2]))))


(c/=defn dft3 [t1 t2]
         (do
           (reset! saved '())
           (c/=bind [node1] (dft-node c/*cont* t1)
                    (c/=bind [node2] (dft-node c/*cont* t2)
                             (if (= node2 '[done])
                               node2
                               (do
                                 (print (list node1 node2) " ")
                                 (restart)))))))


(comment

  onlisp.core=> (dft3 t1 t2)
  (a 1)  (b 1)  (b 2)  (b 3)  (b 6)  (b 7)  (b 4)  (b 5)  (d 1)  (d 2)  (d 3)  (d 6)  (d 7)  (d 4)  (d 5)  (h 1)  (h 2)  (h 3)  (h 6)  (h 7)  (h 4)  (h 5)  (c 1)  (c 2)  (c 3)  (c 6)  (c 7)  (c 4)  (c 5)  (e 1)  (e 2)  (e 3)  (e 6)  (e 7)  (e 4)  (e 5)  (f 1)  (f 2)  (f 3)  (f 6)  (f 7)  (f 4)  (f 5)  (i 1)  (i 2)  (i 3)  (i 6)  (i 7)  (i 4)  (i 5)  (g 1)  (g 2)  (g 3)  (g 6)  (g 7)  (g 4)  (g 5)  (a 2)  (a 3)  (a 6)  (a 7)  (a 4)  (a 5)  [done]

  )


(comment

  ;; buggy2
  (def b
    (c/=bind [node1] (dft-node_ t1)  ;; buggy
             (if (= node1 '[done])
               node1
               (c/=bind [node2] (dft-node c/*cont* t2)
                        [node1 node2]))))


  ;; buggy3
  (def c
    (c/=bind [node1] (dft-node identity t1)  ;; buggy
             (if (= node1 '[done])
               node1
               (c/=bind [node2] (dft-node identity t2)  ;; buggy
                        [node1 node2]))))


  ;; buggy4
  (def d
    (c/=bind [node1] (dft-node_ t1)  ;; buggy
             (if (= node1 '[done])
               node1
               (c/=bind [node2] (dft-node_ t2)  ;; buggy
                        [node1 node2]))))


  ;; buggy5
  (def e
    (c/=bind [node1] (dft-node c/*cont* t1)
             (if (= node1 '[done])
               node1
               (c/=bind [node2] (dft-node_ t2) ;; buggy
                        [node1 node2]))))

  ;; buggy6
  (c/=defn dft4 [t1 t2]
           (do
             (reset! saved '())
             (c/=bind [node1] (dft-node_ t1)  ;; buggy
                      (c/=bind [node2] (dft-node c/*cont* t2)
                               (if (= node2 '[done])
                                 node2
                                 (do
                                   (print (list node1 node2) " ")
                                   (restart)))))))
  )


;; [ P279 chap20.3 ]

(defn rev-cps
  ([lst] (rev-cps lst identity))
  ([lst fnc]
   (loop [x lst
          cont identity]
     (if (empty? x)
       (cont x)
       (recur
         (rest x)
         (fn [w]
           (cont
             (concat w (list
                         (if (list? (first x))
                           ;; 入れ子対応
                           (rev-cps (first x) fnc)
                           (fnc (first x))))))))))))


(comment

  onlisp.core=> (rev-cps (range 10))
  (9 8 7 6 5 4 3 2 1 0)

  onlisp.core=> (rev-cps '(1 3 2 (5 4 (7 6))))
  (((6 7) 4 5) 2 3 1)

  onlisp.core=> (rev-cps '(1 (2 3 (4 5) 6) 7) inc)
  (8 (7 (6 5) 4 3) 2)

  onlisp.core=> (rev-cps '(1 (2 3 (4 5) 6) 7) str)
  ("7" ("6" ("5" "4") "3" "2") "1")
  )

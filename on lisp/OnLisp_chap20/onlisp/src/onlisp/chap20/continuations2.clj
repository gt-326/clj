(ns onlisp.chap20.continuations2
  (:require
    [onlisp.chap20.continuations :as c]))


;; [ P278 chap20.2 ]

;; 現状
;; 問題点は2つです。

;; 1. ネストした if → cond でフラットにできる
;; 2. '[done] の返り値 → dft は副作用（print）のための関数で、
;;    空リストの戻り値を呼び出し元は使わない。CPS 版からの混入

(defn dft
  [tree]
  (if (list? tree)
    (if (empty? tree)
      '[done]          ; ← CPS 版の番兵値を誤って流用。誰も使わない
      (do
        (dft (first tree))
        (dft (rest tree))))
    (print tree)))


(def saved (atom '()))


;; 現状
;; 改善できる点は3つあります。

;; 1. (deref saved) → @saved（Clojure 慣用句）
;; 2. if + let → if-let + 分割束縛（POP の意図をコードで表現）
;; 3. ; POP コメント不要になる

(c/=defn restart []
         (if (seq (deref saved))
           (let [fnc (first (deref saved))]
             ;; POP
             (reset! saved (rest (deref saved)))
             (fnc))
           (c/=values '[done])))


;; 現状
;;  改善できる点は4つです。

;;   1. ネストした if → cond
;;   2. else のない if → when
;;   3. (reset! saved (conj @saved ...)) → (swap! saved conj ...)
;;   4. ;;ガードが必要 コメント → when で意図が自明になるので削除

(c/=defn dft-node [fnc-cont tree]
         (if (list? tree)
           (if (empty? tree)
             (restart)
             (do
               ;; ガードが必要
               (if (seq (rest tree))
                 (reset!
                   saved
                   (conj
                     @saved
                     (fn [] (dft-node fnc-cont (rest tree))))))

               (dft-node fnc-cont (first tree))))
           (fnc-cont tree)))


(c/=defn dft2 [fnc-cont tree]
         (do
           (reset! saved '())
           (c/=bind
             [node]
             (dft-node fnc-cont tree)
             (if (= node '[done])
               node
               (do
                 (print node)
                 (restart))))))


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


(def t1 '(a (b (d h)) (c e (f i) g)))
(def t2 '(1 (2 (3 6 7)) 4 5))


(def b
  (c/=bind [node1] (dft-node c/*cont* t1)
           (if (= node1 '[done])
             node1
             (c/=bind [node2] (dft-node c/*cont* t2)
                      [node1 node2]))))


;; [ P279 chap20.3 ]

(defn revc3
  ([lst] (revc3 lst identity))
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
                           (revc3 (first x) fnc)
                           (fnc (first x))))))))))))


(comment

  onlisp.core=> (revc3 (range 10))
  (9 8 7 6 5 4 3 2 1 0)

  onlisp.core=> (revc3 '(1 3 2 (5 4 (7 6))))
  (((6 7) 4 5) 2 3 1)
  )

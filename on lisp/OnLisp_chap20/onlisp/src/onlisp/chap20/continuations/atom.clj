(ns onlisp.chap20.continuations.atom)


;; [ P274 chap20 ]

(def cont (atom identity))


;; OK
(defmacro =defn
  [name params & body]
  (let [n        (symbol (str "=" name))
        cont-sym `cont]   ; コンパイル時に onlisp.chap20.continuations.atom/cont に修飾
    `(do
       (defn ~n [~'cont ~@params] ~@body)
       (defmacro ~name ~params
         (list '~n '~cont-sym ~@params)))))


;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[clojure.core$identity 0x24117a39 "clojure.core$identity@24117a39"]

;;  onlisp.core=> (onlisp.chap20.continuations.atom/=defn add5 [x]
;;                  (onlisp.chap20.continuations.atom/=values (+ x 5)))
;;  #_=> #'onlisp.core/add5

;;  onlisp.core=> (add5 10)
;;  15


;; OK
(defmacro =bind
  [params expr & body]
  `(do
     (reset! cont (fn ~params (do ~@body)))
     ~expr))


;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[clojure.core$identity 0x24117a39 "clojure.core$identity@24117a39"]

;;  onlisp.core=> (@onlisp.chap20.continuations.atom/cont 1)
;;  1

;;  onlisp.core=> (onlisp.chap20.continuations.atom/=bind [y]
;;                  ((deref onlisp.chap20.continuations.atom/cont) 1)
;;                  (println "y =" y) (inc y))
;;  #_=> y = 1
;;  2

;; cont に束縛された式が変わっている
;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[onlisp.core$eval2835$fn__2836 0x7e369b61 "onlisp.core$eval2835$fn__2836@7e369b61"]


;; OK
(defmacro =values
  [& retvals]
  `((deref cont) ~@retvals))


;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[clojure.core$identity 0x24117a39 "clojure.core$identity@24117a39"]

;;  onlisp.core=> (onlisp.chap20.continuations.atom/=values 1)
;;  1


(defmacro =fn
  [params & body]
  `(fn [~'cont ~@params]
     ~@body))


(defmacro =fncall
  [fnc & params]
  `(~fnc cont ~@params))


;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[clojure.core$identity 0x24117a39 "clojure.core$identity@24117a39"]

;;  onlisp.core=> (onlisp.chap20.continuations.atom/=defn add1 [x]
;;                  (onlisp.chap20.continuations.atom/=values (inc x)))
;;  #'onlisp.core/add1

;;  onlisp.core=> (add1 10)
;;  11

;;  onlisp.core=> (let [f (onlisp.chap20.continuations.atom/=fn [n] (add1 n))]
;;                  (onlisp.chap20.continuations.atom/=bind
;;                    [y]
;;                    (onlisp.chap20.continuations.atom/=fncall f 9)
;;                    (println "9 + 1 =" y)))
;;  #_=> 9 + 1 = 10
;;  nil

;;  cont に束縛された式が変わっている
;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[onlisp.core$eval2914$fn__2917 0x6f8d5253 "onlisp.core$eval2914$fn__2917@6f8d5253"]


;; あまり使いでがない気がする・・・。

(defmacro =apply
  [fnc & args]
  `(apply ~fnc cont ~@args))


(comment

  (onlisp.chap20.continuations.atom/=defn sum-two [x y]
    (onlisp.chap20.continuations.atom/=values (+ x y)))

  (let [pairs [[1 2] [3 4] [5 6]]]
    (doseq [pair pairs]
      (onlisp.chap20.continuations.atom/=bind [s]
        (onlisp.chap20.continuations.atom/=apply =sum-two pair)
               (println pair "->" s))))
  ;; [1 2] -> 3
  ;; [3 4] -> 7
  ;; [5 6] -> 11

  )


;;  onlisp.core=> @onlisp.chap20.continuations.atom/cont
;;  #object[clojure.core$identity 0x24117a39 "clojure.core$identity@24117a39"]

;;  onlisp.core=> (onlisp.chap20.continuations.atom/=defn aaa [x] (a/=values (str x)))
;;  #'onlisp.core/aaa

;;  onlisp.core=> (aaa 10)
;;  "10"

;;  onlisp.core=> (let [f (onlisp.chap20.continuations.atom/=fn [& n] (str1 n))]
;;                  (onlisp.chap20.continuations.atom/=bind
;;                   [y]
;;                   (onlisp.chap20.continuations.atom/=fncall f 9 10)
;;                   y))
;;  #_=> "(9 10)"

;; ===================================================================

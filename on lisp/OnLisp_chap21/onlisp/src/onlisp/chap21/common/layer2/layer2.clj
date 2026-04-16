(ns onlisp.chap21.common.layer2
  (:require
    [onlisp.chap21.common.layer1 :as l1]))


;; [ P283 chap21.1 ]


(defmacro fork
  [expr pri]
  `(let [expr# '~expr
         p# (l1/make-proc
              :state (fn [& g#] (do ~expr (l1/pick-process)))
              :pri   ~pri)]

     ;; (println "cnt procs [before]:" (count @PROCS))
     (swap! l1/PROCS conj p#)
     ;; (println "cnt procs [after]:" (count @PROCS))

     ;; can not understand what this part means
     expr#))


(defn arbitrator
  [test cont]
  (do
    ;; 1回の swap! で両フィールドを更新
    (swap! l1/PROC #(assoc % :wait test :state cont))
    ;; 2回 swap!（非原子的）
    ;; (swap! l1/PROC assoc :wait test)
    ;; (swap! l1/PROC assoc :state cont)

    ;; (println "cnt procs [before]:" (count @PROCS))
    (swap! l1/PROCS conj @l1/PROC)
    ;; (println "cnt procs [after]:" (count @PROCS))

    (l1/pick-process)))


(defmacro wait
  [param test & body]
  `(arbitrator
     ;; test -> :wait
     (fn [] ~test)
     ;; cont -> :state
     (fn [~param] (do ~@body))))


(defmacro yield
  [& body]
  `(arbitrator
     ;; test
     nil
     ;; cont
     (fn [x#] (do ~@body))))


(defn setpri
  [n]
  (swap! l1/PROC assoc :pri n))


(defn halt
  ([]
   (halt nil))
  ([val]
   (throw (ex-info (str l1/HALT) {:val val}))))


(defn kill
  ;; kill は副作用のみを目的とする手続きであり、返り値は nil に統一する。
  ;; 原著 CL でも返り値は意図されておらず、呼び出し側が使うことを想定していない。
  ;; pick-process や swap! の戻り値を透過させると state 関数の型に依存した
  ;; 偶発的な値が漏れ出るため、kill 側で明示的に nil を返す。
  ([]
   (kill nil))
  ([obj]
   ;; (println "obj:" obj)
   (if obj
     (swap! l1/PROCS #(remove #{obj} %))
     (l1/pick-process))
   nil))

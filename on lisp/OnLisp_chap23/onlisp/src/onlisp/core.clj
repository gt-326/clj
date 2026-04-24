(ns onlisp.core
  (:require
    [onlisp.chap23.atn1 :as atn1]
    [onlisp.chap23.atn2 :as atn2]
    [onlisp.chap23.atn3 :as atn3]
    [onlisp.chap23.atn4 :as atn4]
    ;; [onlisp.chap23.atn5 :as atn5]
    ;; [onlisp.chap23.atn6 :as atn6]
    ;; [onlisp.chap23.atn7 :as atn7]
    ;; [onlisp.chap23.atn8 :as atn8]
    [onlisp.chap23.atn9 :as atn9]
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]
    [onlisp.common.util3 :as u3]))


(defmacro me
  [expr]
  `(clojure.pprint/pprint (macroexpand '~expr)))


(defmacro me1
  [expr]
  `(clojure.pprint/pprint (macroexpand-1 '~expr)))


(defmacro bench
  [expr]
  `(let [start# (System/nanoTime) result# ~expr]
     {:result result# :elapsed (- (System/nanoTime) start#)}))


(defmacro nif
  [expr pos zero neg]
  (let [x# expr]
    `(case (if (= 0 ~x#) ~x# (quot ~x# (Math/abs ~x#)))
       1 ~pos
       0 ~zero
       -1 ~neg)))


(comment

  ;; ============================================================
  ;;  atn4 (defnode / fast) vs atn9 (defnode-slow5) ベンチマーク
  ;;  REPL で評価して使用する
  ;; ============================================================

  ;; ウォームアップ（JIT コンパイルを促す）
  (dotimes [_ 200] (with-out-str (atn4/foo)))
  (dotimes [_ 200] (with-out-str (atn9/foo)))

  ;; 計測（IO オーバーヘッドを with-out-str で除去）
  (let [n  1000
        r1 (bench (dotimes [_ n] (with-out-str (atn4/foo))))
        r2 (bench (dotimes [_ n] (with-out-str (atn9/foo))))]
    (println (format "atn4 : %,d ns  (%,d ns/call)" (:elapsed r1) (quot (:elapsed r1) n)))
    (println (format "atn9 : %,d ns  (%,d ns/call)" (:elapsed r2) (quot (:elapsed r2) n)))
    (println (format "ratio: %.2fx  (atn9 / atn4)" (/ (double (:elapsed r2)) (:elapsed r1)))))

  )

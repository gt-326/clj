(ns onlisp.chap21.common.layer1.stat
  (:require
    [onlisp.chap21.common.layer1.core :as c]))


;; [ P283 chap21.1 ]


(declare pick-process)


;; ^:dynamic を付与する必要はなかった

(def PROC (atom nil))  ; 実行中のプロセス
(def PROCS (atom nil)) ; 中断されているプロセスのリスト
(def HALT (gensym))    ; プロセス中断であることを示す目印（通常の例外と区別したい）


(def DEFAULT-PROC
  (c/make-proc
    :state
    (fn [& x]
      (do
        (print "\n[ REPL multi-process ]>> ")
        (flush)
        (println (eval (read)))
        (pick-process)))))


(defmacro multiple-value-bind
  [binds expr & body]
  `(let [;; gensym 部分
         ~@(mapcat #(list (symbol %) '(gensym)) binds)
         ;; bind 部分
         [~@(map #(symbol %) binds)]
         (if ~(sequential? expr) ~expr (list ~expr))]
     ~@body))


(defn most-urgent-process
  []
  (loop [[proc1 max val1 :as acc] [DEFAULT-PROC -1 true]
         p @PROCS]
    (if (empty? p)
      [proc1 val1]
      (let [current-p (first p)
            pri (:pri current-p)
            w (:wait current-p)
            v (or (not w) (when (fn? w) (w)))]
        (recur
          (if (and pri (> pri max) v)
            ;; 最も優先度の高いプロセスを返す
            [current-p pri v]
            acc)
          (rest p))))))


(defn pick-process
  []
  (multiple-value-bind
    (p v)
    (most-urgent-process)

    ;; 実行中のプロセスを上書きする
    (reset! PROC p)
    ;; 中断されているプロセスのリストから除く
    (swap! PROCS #(remove #{p} %))

    ;; :wait が nil（待機条件なし）のときは nil を渡す。
    ;; :wait が関数のときは、その評価結果 v（wait 条件の戻り値）を渡す。
    ;; 以前は v = true（Boolean）が無条件に渡されており、
    ;; 数値関数などを :state に置いた場合に ClassCastException が発生していた。

    ;; ((:state p) v)
    ((:state p) (when (:wait p) v))))

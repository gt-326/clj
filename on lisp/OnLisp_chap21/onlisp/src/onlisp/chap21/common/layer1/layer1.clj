(ns onlisp.chap21.common.layer1)


;; [ P283 chap21.1 ]

(defrecord Proc
  [pri state wait])


(defn make-proc
  [& {:keys [pri state wait]
      :as p}]
  (map->Proc p))


(declare pick-process)


;; ^:dynamic を付与する必要はなかった

(def HALT (gensym))
(def PROCS (atom nil))
(def PROC (atom nil))


(def DEFAULT-PROC
  (make-proc
    :state
    (fn [& x]
      (do
        (print "\n[ REPL multi-process ]>> ")
        (flush)
        (println (eval (read)))
        (pick-process)))))


(defmacro multiple-value-bind
  [binds seq & body]
  `(let [;; gensym 部分
         ~@(mapcat #(list (symbol %) '(gensym)) binds)
         ;; bind 部分
         [~@(map #(symbol %) binds)]
         (if ~(sequential? seq) ~seq (list ~seq))]
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

        ;; (println "v:" (list pri max (> pri max) w v val1))
        ;; (println "v:" (list  pri (> pri max) v))

        (recur
          (if (and pri (> pri max) v)
            [current-p pri v]
            acc)
          (rest p))))))


(defn pick-process
  []
  (multiple-value-bind
    (p v)
    (most-urgent-process)

    ;; (println "p:" p v)
    ;; (println "p2:" (:state p) ":" (:wait p))

    (reset! PROC p)
    (swap! PROCS #(remove #{p} %))

    ;; :wait が nil（待機条件なし）のときは nil を渡す。
    ;; :wait が関数のときは、その評価結果 v（wait 条件の戻り値）を渡す。
    ;; 以前は v = true（Boolean）が無条件に渡されており、
    ;; 数値関数などを :state に置いた場合に ClassCastException が発生していた。

    ;; ((:state p) v)
    ((:state p) (when (:wait p) v))))

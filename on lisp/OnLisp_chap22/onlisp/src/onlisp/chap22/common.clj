(ns onlisp.chap22.common
  (:require
    [onlisp.common.util :as u]))


;; [ P301 chap22.4 ]

;; ^:dynamic を付与する必要はなかった

(def PATHS (atom []))


(def failsym '[end])


(defn fail
  []
  (if (empty? @PATHS)
    failsym
    (let [fnc (peek @PATHS)]
      ;; POP
      (swap! PATHS pop)
      (fnc))))


(defn cb
  [fnc choices]
  (if (seq choices)
    (do
      (when (rest choices)
        ;; 積んだ時点の *cont* を保存し、呼ばれたときに復元する。
        ;; *cont* は動的変数のため、保存しないと fail から呼ばれる際に
        ;; 呼び出し元の *cont*（通常 identity）で上書きされてしまう。
        (let [saved u/*cont*]
          (swap! PATHS conj (fn []
                              (binding [u/*cont* saved]
                                (cb fnc (rest choices)))))))

      (fnc (first choices)))
    (fail)))


(defmacro choose-bind
  [param choices & body]
  `(cb (fn [~param] ~@body) ~choices))


(defmacro choose
  [& choices]
  (if (seq choices)
    `(do
       ~@(map
           (fn [c] `(swap! PATHS conj #(~@c)))
           (reverse (rest choices)))

       ~(first choices))
    `(fail)))


;; [ P307 chap22.5 ]

(defn mark
  []
  (swap! PATHS conj fail))


(defn cut
  []
  (when (seq @PATHS)
    (if (= (peek @PATHS) fail)
      (swap! PATHS pop)
      (do
        (swap! PATHS pop)
        (recur)))))

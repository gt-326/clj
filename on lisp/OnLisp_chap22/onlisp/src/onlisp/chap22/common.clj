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


;; [ P310 chap22.6 ]


(defn true-choose-impl
  [visited choices]
  (if (empty? choices)
    (fail)
    (let [[a & more] choices]
      (if (contains? visited a)
        ;; 循環検出 → この選択肢をスキップして残りへ
        (true-choose-impl (conj visited a) more)
        ;; 有効な選択肢 → 残りをバックトラック用に積み、a を実行
        (do
          (when (seq more)
            (let [saved u/*cont*]
              (swap! PATHS conj
                     (fn []
                       (binding [u/*cont* saved]
                         (true-choose-impl (conj visited a) more))))))
          (a))))))


;; エントリーポイント（関数版）
(defn true-choose
  [choices]
  (true-choose-impl #{} choices))


;; エントリーポイント（マクロ版）
(defmacro true-choose_
  [& choices]
  `(true-choose-impl #{}
                     (list ~@(map (fn [c] `(fn [] ~c)) choices))))


;; true-choose の visited dedup は同一オブジェクトの thunk が重複している場合のみ有効です。
;; path-impl / descent-impl3 のように map #(...) で毎回新規 thunk を生成する場合は
;; 常に dead code です。

;; よって、現在のすべての呼び出し元（path-impl、descent-impl3、descent-impl4）において
;; true-choose と true-choose-simple は等価です。true-choose-simple はその visited を
;; 取り除いたシンプルな版です。

(defn true-choose-simple
  [choices]
  (if (empty? choices)
    (fail)
    (let [[a & more] choices]  ; thunk のリスト（値ではない）
      (when (seq more)
        ;; Scheme が call/cc でやっていることを、
        ;; Clojure では *cont* の保存・復元で代替しています
        (let [saved u/*cont*]
          (swap! PATHS conj
                 (fn []
                   (binding [u/*cont* saved]
                     (true-choose-simple more))))))

      ;; 先頭 thunk を直接呼ぶ
      (a))))


(defmacro true-choose2
  [& choices]
  `(true-choose-simple
     (list ~@(map (fn [c] `(fn [] ~c)) choices))))

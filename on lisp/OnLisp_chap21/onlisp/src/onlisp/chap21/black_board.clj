(ns onlisp.chap21.black-board)


;; chap21.2 (P288)

;; ^:dynamic を付与しなくても、=defn 版でも動作した


(def BBOARD (atom nil))


(defn claim
  [& f]
  (when (seq f)
    (swap! BBOARD conj f)))


(defn unclaim
  [& f]
  (when (seq f)
    (swap! BBOARD #(remove #{f} %))))


(defn check
  [& f]
  (when (seq f)
    ;; seq は空コレクションを nil に変換するため、wait の条件判定が正しく機能します。
    ;; また first と異なり全マッチを返すため原著 CL の動作に揃います。
    (seq (filter #(= % f) @BBOARD))))

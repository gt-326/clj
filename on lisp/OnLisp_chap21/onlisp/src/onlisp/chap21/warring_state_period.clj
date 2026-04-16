(ns onlisp.chap21.warring-state-period)


;; chap21.2 (P289)

;; 取る
(defn my-take
  [c]
  (println
    ;; （支配から解き放って）自由にする
    (format "Liberating %s." c)))


;; 要塞化する
(defn fortify
  [c]
  (println
    ;; （財務を）再編成する
    (format "Rebuilding %s." c)))


;; 略奪する
(defn loot
  [c]
  (println
    ;; 国有化する
    (format "Nationalizing %s." c)))


;; 身代金を要求する
(defn ransom
  [c]
  (println
    ;; （財務を）再編成する
    (format "Refinancing %s." c)))

(ns onlisp.chap21.common.layer1.core)


;; [ P283 chap21.1 ]

(defrecord Proc
  [pri state wait])


(defn make-proc
  [& {:keys [pri state wait]
      :as p}]
  (map->Proc p))
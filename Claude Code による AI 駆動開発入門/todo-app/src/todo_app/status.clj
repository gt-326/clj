(ns todo-app.status
  (:require
    [clojure.string :as str]))


(def stat-keys [:todo :doing :pending :done])
(def stat-vals ["未着手" "進行中" "保留" "完了"])


;; label-by-key: {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
(def label-by-key (zipmap stat-keys stat-vals))


;; label-by-num: {0 "未着手", 1 "進行中", 2 "保留", 3 "完了"}
(def label-by-num
  (into
    (sorted-map)
    (zipmap (range) stat-vals)))


(defn gen-msg
  [status]
  (str/join " / " (map #(str/join ":" %) status)))


;; msg-statuses: "0:未着手 / 1:進行中 / 2:保留 / 3:完了"
(def msg-statuses (gen-msg label-by-num))


;; msg-update-statuses: "1:進行中 / 2:保留 / 3:完了"
(def msg-update-statuses (gen-msg (rest label-by-num)))

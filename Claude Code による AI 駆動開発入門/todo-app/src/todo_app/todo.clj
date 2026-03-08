(ns todo-app.todo
  (:require
    [clojure.string :as str]))


(def stat-keys [:todo :doing :pending :done])
(def stat-vals ["未着手" "進行中" "保留" "完了"])


;; status-labels: {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
(def status-labels (zipmap stat-keys stat-vals))


;; valid-statuses: {0 "未着手", 1 "進行中", 2 "保留", 3 "完了"}
(def valid-statuses
  (into
    (sorted-map)
    (zipmap (range) stat-vals)))


(defn gen-msg
  [status]
  (str/join " / " (map #(str/join ":" %) status)))


;; msg-statuses: "0:未着手 / 1:進行中 / 2:保留 / 3:完了"
(def msg-statuses (gen-msg valid-statuses))


;; msg-update-statuses: "1:進行中 / 2:保留 / 3:完了"
(def msg-update-statuses (gen-msg (rest valid-statuses)))


(defn now
  []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yy-MM-dd HH:mm")))


(defn add-todo
  [data title]
  (let [id   (:next-id data)
        todo {:id id
              :title title
              :status :todo
              :start-at nil
              :end-at nil}]

    (-> data
        (update :todos conj todo)
        (update :next-id inc))))


(defn update-status
  [data id stat-num]
  (update data :todos
          (fn [todos]
            (mapv (fn [todo]
                    (if (= (:id todo) id)
                      (let [stat-key (stat-keys stat-num)]
                        (cond-> (assoc todo :status stat-key)
                          (= stat-key :doing) (assoc :start-at (now))
                          (= stat-key :done) (assoc :end-at (now))))
                      todo))
                  todos))))


(defn delete-todo
  [data id]
  (update data :todos
          (fn [todos]
            (filterv #(not= (:id %) id) todos))))

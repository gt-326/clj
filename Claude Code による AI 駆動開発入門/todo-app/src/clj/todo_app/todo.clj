(ns todo-app.todo
  (:require
    [todo-app.status :as status]))


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
  [data id stat-num datetime]
  (update data :todos
          (fn [todos]
            (mapv (fn [todo]
                    (if (= (:id todo) id)
                      (let [stat-key (get status/stat-keys stat-num)]
                        (cond-> (assoc todo :status stat-key)
                          (= stat-key :pending) (assoc :end-at nil)
                          (= stat-key :doing) (assoc :start-at datetime :end-at nil)
                          (= stat-key :done) (assoc :end-at datetime)))
                      todo))
                  todos))))


(defn delete-todo
  [data id]
  (update data :todos
          (fn [todos]
            (filterv #(not= (:id %) id) todos))))

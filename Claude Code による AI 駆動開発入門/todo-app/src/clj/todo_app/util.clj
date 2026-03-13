(ns todo-app.util)


(defn parse-num
  [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _
      nil)))


(defn now
  []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yy-MM-dd HH:mm")))


(defn select-data
  [data-field condition todo-data]
  (if condition
    (filterv #(= (data-field %) condition) todo-data)
    ;; 全て
    todo-data))

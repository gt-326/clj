(ns todo-app.store
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))


(def data-file
  (delay
    (let [url (.getResource (.getContextClassLoader (Thread/currentThread))
                            "todo_app/store.clj")]
      (if (and url (= "jar" (.getProtocol url)))
        (let [jar-path (-> url .getPath (str/split #"!") first)
              jar-file (java.io.File. (java.net.URI. jar-path))]  ; ← URI 経由
          (str (.getParent jar-file)
               ;; "/" (str/replace (.getName jar-file) #"\.jar$" ".edn")
               "/log/todo.edn"))
        (str "./log/todo.edn")))))


(def default-data {:next-id 1 :todos []})


(defn initialize-store!
  []
  (let [file (java.io.File. @data-file)]
    (when-not (.exists file)
      (.mkdirs (.getParentFile file))
      (spit file (pr-str default-data)))))


(defn load-todos
  []
  (try
    (edn/read-string (slurp @data-file))
    (catch Exception _
      default-data)))


(defn save-todos!
  [data]
  (spit @data-file (pr-str data)))

(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))


(def data-file
  ;; (str (System/getProperty "user.home") "/.todo.edn")
  (str "./log/todo.edn"))


(defn load-todos
  []
  (if (.exists (java.io.File. data-file))
    (try
      (edn/read-string (slurp data-file))
      (catch Exception _
        {:next-id 1 :todos []}))
    {:next-id 1 :todos []}))


(defn save-todos!
  [data]
  (spit data-file (pr-str data)))


(defn add-todo
  [data title]
  (let [id   (:next-id data)
        todo {:id id :title title :done false}]
    (-> data
        (update :todos conj todo)
        (update :next-id inc))))


(defn mark-done
  [data id]
  (update data :todos
          (fn [todos]
            (mapv (fn [todo]
                    (if (= (:id todo) id)
                      (assoc todo :done true)
                      todo))
                  todos))))


(defn delete-todo
  [data id]
  (update data :todos
          (fn [todos]
            (filterv #(not= (:id %) id) todos))))


(defn print-todos
  [todos]
  (if (empty? todos)
    (println "タスクはありません。")
    (doseq [{:keys [id title done]} todos]
      (let [status (if done "[x]" "[ ]")]
        (println (format "%s %3d. %s" status id title))))))


(defn parse-id
  [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _
      nil)))


(defn print-help
  []
  (println "")
  (println "TODO App - 使い方:")
  (println "  add <タスク名>  タスクを追加する")
  (println "  list            タスク一覧を表示する")
  (println "  done <id>       タスクを完了にする")
  (println "  delete <id>     タスクを削除する")
  (println "  help            このヘルプを表示する")
  (println ""))


(defn run-command
  [cmd rest-args]
  (case cmd
    "add"
    (let [title (str/join " " rest-args)]
      (if (str/blank? title)
        (println "エラー: タスク名を入力してください。")
        (let [data     (load-todos)
              new-data (add-todo data title)]
          (save-todos! new-data)
          (println (format "タスクを追加しました: %s" title)))))

    "list"
    (let [data (load-todos)]
      (print-todos (:todos data)))

    "done"
    (let [id (some-> (first rest-args) parse-id)]
      (if (nil? id)
        (println "エラー: 有効な ID を指定してください。")
        (let [data     (load-todos)
              todos    (:todos data)
              found?   (some #(= (:id %) id) todos)
              new-data (mark-done data id)]
          (if found?
            (do (save-todos! new-data)
                (println (format "タスク %d を完了にしました。" id)))
            (println (format "エラー: ID %d のタスクが見つかりません。" id))))))

    "delete"
    (let [id (some-> (first rest-args) parse-id)]
      (if (nil? id)
        (println "エラー: 有効な ID を指定してください。")
        (let [data     (load-todos)
              todos    (:todos data)
              found?   (some #(= (:id %) id) todos)
              new-data (delete-todo data id)]
          (if found?
            (do (save-todos! new-data)
                (println (format "タスク %d を削除しました。" id)))
            (println (format "エラー: ID %d のタスクが見つかりません。" id))))))

    (print-help)))


(defn -main
  [& args]
  (if (empty? args)
    ;; mode: repl
    (do
      (println "TODO App へようこそ。help でコマンド一覧を表示します。")
      (loop []
        (print "todo> ")
        (flush)
        (let [line (read-line)]
          (when (some? line)                        ; Ctrl+D (EOF) で終了
            (let [tokens    (str/split (str/trim line) #"\s+")
                  cmd       (first tokens)
                  rest-args (rest tokens)]
              (when-not (str/blank? line)
                (if (contains? #{"exit" "quit"} cmd)
                  (do (println "さようなら。")
                      (System/exit 0))
                  (run-command cmd rest-args))))
            (recur)))))

    ;; mode: simple
    (let [cmd      (first args)
          rest-args (rest args)]
      (run-command cmd rest-args))))

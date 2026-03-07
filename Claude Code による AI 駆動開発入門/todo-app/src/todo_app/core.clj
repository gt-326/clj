(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))


(def data-file
  ;; (str (System/getProperty "user.home") "/.todo.edn")
  (str "./log/todo.edn"))


(def state_TODO 0)
(def state_DOING 1)
(def state_PENDING 2)
(def state_DONE 3)

(def status-titles ["未着手" "進行中" "保留" "完了"])


;; valid-statuses: {0 "未着手", 1 "進行中", 2 "保留", 3 "完了"}
(def valid-statuses (zipmap (iterate inc state_TODO) status-titles))


;; msg-statuses: "0:未着手 / 1:進行中 / 2:保留 / 3:完了"
(def msg-statuses (str/join " / " (map #(str/join ":" %) valid-statuses)))


;; msg-update-statuses: "1:進行中 / 2:保留 / 3:完了"
(def msg-update-statuses (str/join " / " (map #(str/join ":" %) (rest valid-statuses))))


(defn now
  []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yy-MM-dd HH:mm")))


(defn migrate-todo
  [todo]
  ;; 旧フォーマット {:done true/false} → {:status "..."} に変換
  (if (contains? todo :done)
    (-> todo
        (assoc :status (if (:done todo) state_DONE state_PENDING))
        (dissoc :done))
    todo))


(defn load-todos
  []
  (if (.exists (java.io.File. data-file))
    (try
      (let [data (edn/read-string (slurp data-file))]
        (update data :todos #(mapv migrate-todo %)))
      (catch Exception _
        {:next-id 1 :todos []}))
    {:next-id 1 :todos []}))


(defn save-todos!
  [data]
  (spit data-file (pr-str data)))


(defn add-todo
  [data title]
  (let [id   (:next-id data)
        todo {:id id :title title :status state_TODO :start-at nil :end-at nil}]

    (-> data
        (update :todos conj todo)
        (update :next-id inc))))


(defn update-status
  [data id status]
  (update data :todos
          (fn [todos]
            (mapv (fn [todo]
                    (if (= (:id todo) id)
                      (cond-> (assoc todo :status status)
                        (= status state_DOING) (assoc :start-at (now))
                        (= status state_DONE) (assoc :end-at (now)))
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
    (doseq [{:keys [id title status start-at end-at]} todos]
      (println
        (format "[%s] %3d. %s [%s  %s]"
                (if (= status state_TODO) "　" (subs (get valid-statuses status) 0 1))
                id
                title
                (if start-at (str "開始:" start-at) "")
                (if end-at   (str "終了:" end-at) ""))))))


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
  (println "  add <タスク名>              タスクを追加する（初期ステータス: 未着手）")
  (println "  list [番号]                 タスク一覧を表示する（番号指定でフィルタリング）")
  (println "   " msg-statuses)
  (println "  update <id> <番号>          ステータスを更新する")
  (println "   " msg-update-statuses)
  (println "  delete <id>                 タスクを削除する")
  (println "  help                        このヘルプを表示する")
  (println "  exit / quit                 終了する")
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
    (let [data       (load-todos)
          status-num (some-> (first rest-args) parse-id)
          filter-label (get valid-statuses status-num)]
      (cond
        (and (some? status-num) (nil? filter-label))
        (println "エラー: ステータスは" msg-statuses "で指定してください。")

        filter-label
        (print-todos (filterv #(= (:status %) status-num) (:todos data)))

        :else
        (print-todos (:todos data))))

    "update"
    (let [id          (some-> (first rest-args) parse-id)
          status-num  (some-> (second rest-args) parse-id)
          status-label (get valid-statuses status-num)]
      (cond
        (nil? id)
        (println "エラー: 有効な ID を指定してください。")

        (nil? status-label)
        (println "エラー: ステータスは" msg-update-statuses "で指定してください。")

        (not (pos? status-num))
        (println "エラー: ステータスは" msg-update-statuses "で指定してください。")

        :else
        (let [data     (load-todos)
              todos    (:todos data)
              found?   (some #(= (:id %) id) todos)
              new-data (update-status data id status-num)]
          (if found?
            (do (save-todos! new-data)
                (println (format "タスク %d を「%s」にしました。" id status-label)))
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

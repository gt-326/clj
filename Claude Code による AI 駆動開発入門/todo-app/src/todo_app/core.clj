(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))


(def data-file
  ;; (str (System/getProperty "user.home") "/.todo.edn")
  (str "./log/todo.edn"))


(def stat-keys [:todo :doing :pending :done])
(def stat-vals ["未着手" "進行中" "保留" "完了"])


;; status-labels: {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
(def status-labels
  (into (hash-map) (map vector stat-keys stat-vals)))


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


(defn format-todos
  [todos]
  (if (empty? todos)
    "タスクはありません。"
    (str/join "\n"
              (map (fn [{:keys [id title status start-at end-at]}]
                     (format "[%s] %3d. %s [%s  %s]"
                             (if (= status :todo)
                               "　"
                               (subs (status-labels status) 0 1))
                             id
                             title
                             (if start-at (str "開始:" start-at) "")
                             (if end-at   (str "終了:" end-at) "")))
                   todos))))


(defn parse-id
  [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _
      nil)))


(defn format-help
  []
  (str/join "\n"
            [""
             "TODO App - 使い方:"
             "  add <タスク名>              タスクを追加する（初期ステータス: 未着手）"
             "  list [番号]                 タスク一覧を表示する（番号指定でフィルタリング）"
             (str "   " msg-statuses)
             "  update <id> <番号>          ステータスを更新する"
             (str "   " msg-update-statuses)
             "  delete <id>                 タスクを削除する"
             "  help                        このヘルプを表示する"
             "  exit / quit                 終了する"
             ""]))


(defn run-command
  [data-atom cmd rest-args]
  (let [data @data-atom]
    (case cmd
      "add"
      (let [title (str/join " " rest-args)]
        (if (str/blank? title)
          "エラー: タスク名を入力してください。"
          (let [new-data (add-todo data title)]
            ;; 更新
            (reset! data-atom new-data)
            (save-todos! new-data)
            (format "タスクを追加しました: %s" title))))

      "list"
      (let [stat-num (some-> (first rest-args) parse-id)
            filter-label (get valid-statuses stat-num)]
        (cond
          (and (some? stat-num) (nil? filter-label))
          (str "エラー: ステータスは [ " msg-statuses " ] で指定してください。")

          filter-label
          (format-todos (filterv #(= (:status %) (stat-keys stat-num)) (:todos data)))

          :else
          (format-todos (:todos data))))

      "update"
      (let [id          (some-> (first rest-args) parse-id)
            status-num  (some-> (second rest-args) parse-id)
            status-label (get valid-statuses status-num)]
        (cond
          (nil? id)
          "エラー: 有効な ID を指定してください。"

          (nil? status-label)
          (str "エラー: ステータスは [ " msg-update-statuses " ] で指定してください。")

          (not (pos? status-num))
          (str "エラー: ステータスは [ " msg-update-statuses " ] で指定してください。")

          :else
          (let [todos    (:todos data)
                found?   (some #(= (:id %) id) todos)
                new-data (update-status data id status-num)]
            (if found?
              (do
                ;; 更新
                (reset! data-atom new-data)
                (save-todos! new-data)
                (format "タスク %d を「%s」にしました。" id status-label))
              (format "エラー: ID %d のタスクが見つかりません。" id)))))

      "delete"
      (let [id (some-> (first rest-args) parse-id)]
        (if (nil? id)
          "エラー: 有効な ID を指定してください。"
          (let [todos    (:todos data)
                found?   (some #(= (:id %) id) todos)
                new-data (delete-todo data id)]
            (if found?
              (do
                ;; 更新
                (reset! data-atom new-data)
                (save-todos! new-data)
                (format "タスク %d を削除しました。" id))
              (format "エラー: ID %d のタスクが見つかりません。" id)))))

      (format-help))))


(defn -main
  [& args]
  ;; 起動時に1回だけ読み込む
  (let [data-atom (atom (load-todos))]
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
                    (println (run-command data-atom cmd rest-args)))))
              (recur)))))

      ;; mode: simple
      (let [cmd      (first args)
            rest-args (rest args)]
        (println (run-command data-atom cmd rest-args))))))

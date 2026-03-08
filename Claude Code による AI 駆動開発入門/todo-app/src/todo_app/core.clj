(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.string :as str]
    [todo-app.store :as store]
    [todo-app.todo :as todo]))


(defn format-todos
  [todos]
  (if (empty? todos)
    "タスクはありません。"
    (str/join "\n"
              (map (fn [{:keys [id title status start-at end-at]}]
                     (format "[%s] %3d. %s [%s  %s]"
                             (if (= status :todo)
                               "　"
                               (subs (todo/status-labels status) 0 1))
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
             (str "   " todo/msg-statuses)
             "  update <id> <番号>          ステータスを更新する"
             (str "   " todo/msg-update-statuses)
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
          (let [new-data (todo/add-todo data title)]
            ;; 更新
            (reset! data-atom new-data)
            (store/save-todos! new-data)
            (format "タスクを追加しました: %s" title))))

      "list"
      (let [stat-num (some-> (first rest-args) parse-id)
            filter-label (get todo/valid-statuses stat-num)]
        (cond
          (and (some? stat-num) (nil? filter-label))
          (str "エラー: ステータスは [ " todo/msg-statuses " ] で指定してください。")

          filter-label
          (format-todos (filterv #(= (:status %) (todo/stat-keys stat-num)) (:todos data)))

          :else
          (format-todos (:todos data))))

      "update"
      (let [id          (some-> (first rest-args) parse-id)
            status-num  (some-> (second rest-args) parse-id)
            status-label (get todo/valid-statuses status-num)]
        (cond
          (nil? id)
          "エラー: 有効な ID を指定してください。"

          (nil? status-label)
          (str "エラー: ステータスは [ " todo/msg-update-statuses " ] で指定してください。")

          (not (pos? status-num))
          (str "エラー: ステータスは [ " todo/msg-update-statuses " ] で指定してください。")

          :else
          (let [todos    (:todos data)
                found?   (some #(= (:id %) id) todos)
                new-data (todo/update-status data id status-num)]
            (if found?
              (do
                ;; 更新
                (reset! data-atom new-data)
                (store/save-todos! new-data)
                (format "タスク %d を「%s」にしました。" id status-label))
              (format "エラー: ID %d のタスクが見つかりません。" id)))))

      "delete"
      (let [id (some-> (first rest-args) parse-id)]
        (if (nil? id)
          "エラー: 有効な ID を指定してください。"
          (let [todos    (:todos data)
                found?   (some #(= (:id %) id) todos)
                new-data (todo/delete-todo data id)]
            (if found?
              (do
                ;; 更新
                (reset! data-atom new-data)
                (store/save-todos! new-data)
                (format "タスク %d を削除しました。" id))
              (format "エラー: ID %d のタスクが見つかりません。" id)))))

      (format-help))))


(defn -main
  [& args]
  ;; ファイルの存在チェック（なければ生成する）
  (store/initialize-store!)
  ;; 起動時に初期化・1回だけ読み込む
  (let [data-atom (atom (store/load-todos))]
    (if (empty? args)
      ;; mode: repl
      (do
        (println "TODO App へようこそ。help でコマンド一覧を表示します。")
        (loop []
          (print "todo> ")
          (flush)
          (let [line (read-line)]
            (when (some? line) ; Ctrl+D (EOF) で終了
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

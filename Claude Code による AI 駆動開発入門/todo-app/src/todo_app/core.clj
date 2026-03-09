(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.string :as str]
    [todo-app.status :as status]
    [todo-app.store :as store]
    [todo-app.todo :as todo]))


(defn parse-id
  [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _
      nil)))


(defn parse-command
  [cmd rest-args datetime]
  (case cmd
    "add"
    (let [title (str/join " " rest-args)]
      (if (str/blank? title)
        {:error "エラー: タスク名を入力してください。"}
        {:cmd cmd :title title}))

    "list"
    (let [status-num (some-> (first rest-args) parse-id)
          status-key (get status/stat-keys status-num)]
      (if (and (some? status-num) (nil? status-key))
        {:error (str "エラー: ステータスは [ " status/msg-statuses " ] で指定してください。")}
        {:cmd cmd :filter-status-key status-key}))

    "update"
    (let [id           (some-> (first rest-args) parse-id)
          status-num   (some-> (second rest-args) parse-id)
          status-label (status/valid-statuses status-num)]
      (cond
        (nil? id)
        {:error "エラー: 有効な ID を指定してください。"}

        (nil? status-label)
        {:error
         (str "エラー: ステータスは [ " status/msg-update-statuses " ] で指定してください。")}

        (not (pos? status-num))
        {:error
         (str "エラー: ステータスは [ " status/msg-update-statuses " ] で指定してください。")}

        :else
        {:cmd cmd
         :id id
         :status-label status-label
         :status-num status-num
         :now datetime}))

    "delete"
    (let [id (some-> (first rest-args) parse-id)]
      (if (nil? id)
        {:error "エラー: 有効な ID を指定してください。"}
        {:cmd cmd :id id}))

    ;; default
    {:cmd "help"}))


(defn validate-input
  [data-atom cmd rest-args datetime]
  (let [result (parse-command cmd rest-args  datetime)]
    (if (:error result)
      result
      (assoc result :data-atom data-atom))))


(defn execute-command!
  [validated]
  (if (or (:error validated) (= "help" (:cmd validated)))
    validated
    (let [cmd (:cmd validated)
          data-atom (:data-atom validated)]
      (case cmd
        "add"
        (let [new-data (todo/add-todo @data-atom (:title validated))]
          ;; ファイルへ書き込み
          (store/save-todos! new-data)
          ;; atom 更新
          (reset! data-atom new-data)
          validated)

        "list"
        (let [todos (:todos @data-atom)
              result (if (:filter-status-key validated)
                       (filterv #(= (:status %) (:filter-status-key validated)) todos)
                       todos)]
          (assoc validated :todos result))

        ;; default (update/delete)
        (let [todos  (:todos @data-atom)
              id     (:id validated)]
          (if (some #(= (:id %) id) todos)
            (let [new-data (if (= "delete" cmd)
                             (todo/delete-todo @data-atom id)
                             (todo/update-status
                               @data-atom
                               id
                               (:status-num validated)
                               (:now validated)))]
              ;; ファイルへ書き込み
              (store/save-todos! new-data)
              ;; atom 更新
              (reset! data-atom new-data)

              (assoc validated :found? true))
            (assoc validated :found? false)))))))


(defn format-todos
  [todos]
  (if (empty? todos)
    "タスクはありません。"
    (str/join "\n"
              (map (fn [{:keys [id title status start-at end-at]}]
                     (format "[%s] %3d. %s [%s  %s]"
                             (if (= status :todo)
                               "　"
                               (subs (status/status-labels status) 0 1))
                             id
                             title
                             (if start-at (str "開始:" start-at) "")
                             (if end-at   (str "終了:" end-at) "")))
                   todos))))


(defn format-help
  []
  (str/join "\n"
            [""
             "TODO App - 使い方:"
             "  add <タスク名>              タスクを追加する（初期ステータス: 未着手）"
             "  list [番号]                 タスク一覧を表示する（番号指定でフィルタリング）"
             (str "   " status/msg-statuses)
             "  update <id> <番号>          ステータスを更新する"
             (str "   " status/msg-update-statuses)
             "  delete <id>                 タスクを削除する"
             "  help                        このヘルプを表示する"
             "  exit / quit                 終了する"
             ""]))


(defn format-result
  [result]
  (if (:error result)
    (:error result)
    (case (:cmd result)
      "add"    (format "タスクを追加しました: %s" (:title result))
      "list"   (format-todos (:todos result))
      "update" (if (:found? result)
                 (format "タスク %d を「%s」にしました。" (:id result) (:status-label result))
                 (format "エラー: ID %d のタスクが見つかりません。" (:id result)))
      "delete" (if (:found? result)
                 (format "タスク %d を削除しました。" (:id result))
                 (format "エラー: ID %d のタスクが見つかりません。" (:id result)))
      "help"   (format-help))))


(defn run-command
  [data-atom cmd rest-args datetime]
  (-> (validate-input data-atom cmd rest-args datetime)
      execute-command!
      format-result
      println))


(defn now
  []
  (.format (java.time.LocalDateTime/now)
           (java.time.format.DateTimeFormatter/ofPattern "yy-MM-dd HH:mm")))


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
                    (run-command data-atom cmd rest-args (now)))))
              (recur)))))

      ;; mode: simple
      (let [cmd      (first args)
            rest-args (rest args)]
        (run-command data-atom cmd rest-args (now))))))

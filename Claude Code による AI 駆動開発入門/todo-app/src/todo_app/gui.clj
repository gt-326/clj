(ns todo-app.gui
  (:require
    [clojure.string :as str]
    [seesaw.core :as s]
    [seesaw.table :as st]
    [todo-app.status :as status]
    [todo-app.store :as store]
    [todo-app.todo :as todo]))


(def table-columns
  [{:key :id       :text "ID"}
   {:key :title    :text "タスク名"}
   {:key :status   :text "ステータス"}
   {:key :start-at :text "開始日時"}
   {:key :end-at   :text "終了日時"}])


(defn todo->row
  [{:keys [id title status start-at end-at]}]
  {:id       id
   :title    title
   ;; {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
   :status   (status/label-by-key status)
   :start-at (or start-at "")
   :end-at   (or end-at "")})


(defn refresh-table!
  [data table status-key]
  (let [todos    (:todos data)
        filtered (if status-key
                   (filterv #(= (:status %) status-key) todos)
                   ;; 全て
                   todos)]
    (st/clear! table)
    (doseq [row (map todo->row filtered)]
      (st/insert-at! table (st/row-count table) row))))


(defn selected-id
  [table]
  (let [row (.getSelectedRow table)]
    (when (>= row 0)
      (:id (st/value-at table row)))))


(defn- label->key
  [status-map-data selected-label]
  (let [status-pair (->> status-map-data
                         (filter (fn [[_ v]] (= v selected-label))))]
    (if (empty? status-pair)
      nil
      (key (first status-pair)))))


(defn run
  [app-state fnc-now]
  (s/native!)

  (let [;; 入力エリア
        title-field  (s/text :columns 25)
        add-btn      (s/button :text "追加")

        ;; フィルタエリア
        filter-combo (s/combobox :model ["全て" "未着手" "進行中" "保留" "完了"])

        ;; タスク一覧テーブル
        todo-table   (s/table
                       :model [:columns table-columns
                               :rows   (mapv todo->row (:todos @app-state))]
                       :show-grid? true
                       :fills-viewport-height? true)

        ;; 操作エリア
        status-combo (s/combobox :model ["進行中" "保留" "完了"])
        update-btn   (s/button :text "更新")
        delete-btn   (s/button :text "削除")

        ;; レイアウト
        top-panel    (s/horizontal-panel
                       :border 5
                       :items [(s/label :text "タスク名:") title-field add-btn])

        filter-panel (s/horizontal-panel
                       :border 5
                       :items [(s/label :text "フィルタ:") filter-combo])

        bottom-panel (s/horizontal-panel
                       :border 5
                       :items [(s/label :text "ステータス:") status-combo
                               update-btn delete-btn])

        main-panel   (s/border-panel
                       :north  (s/vertical-panel :items [top-panel filter-panel])
                       :center (s/scrollable todo-table)
                       :south  bottom-panel)

        frame        (s/frame
                       :title    "TODO App"
                       :content  main-panel
                       :on-close :exit
                       :size     [640 :by 480])]

    (letfn [(do-refresh
              [data filter-status]
              (refresh-table! data todo-table filter-status))]

      ;; 追加
      (s/listen add-btn :action
                (fn [_]
                  (let [title (str/trim (s/text title-field))]
                    (when-not (str/blank? title)
                      (let [new-data (todo/add-todo @app-state title)
                            status-label (s/selection filter-combo)
                            ;; {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
                            status-key (label->key status/label-by-key status-label)]
                        ;; ファイルへ書き込み
                        (store/save-todos! new-data)
                        ;; atom 更新
                        (reset! app-state new-data)
                        ;; 画面
                        (s/text! title-field "")
                        (do-refresh @app-state status-key))))))

      ;; 更新
      (s/listen update-btn :action
                (fn [_]
                  (when-let [id (selected-id todo-table)]
                    (let [new-status (s/selection status-combo)
                          ;; {0 "未着手", 1 "進行中", 2 "保留", 3 "完了"}
                          stat-num (label->key status/label-by-num new-status)
                          new-data (todo/update-status
                                     @app-state
                                     id
                                     stat-num
                                     (fnc-now))
                          status-label (s/selection filter-combo)
                          status-key (label->key status/label-by-key status-label)]

                      ;; ファイルへ書き込み
                      (store/save-todos! new-data)
                      ;; atom 更新
                      (reset! app-state new-data)
                      ;; 画面
                      (do-refresh @app-state status-key)))))

      ;; 削除
      (s/listen delete-btn :action
                (fn [_]
                  (when-let [id (selected-id todo-table)]
                    (let [new-data (todo/delete-todo @app-state id)
                          status-label (s/selection filter-combo)
                          ;; {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
                          status-key (label->key status/label-by-key status-label)]
                      ;; ファイルへ書き込み
                      (store/save-todos! new-data)
                      ;; atom 更新
                      (reset! app-state new-data)
                      ;; 画面
                      (do-refresh @app-state status-key)))))

      ;; フィルタ
      (s/listen filter-combo :action
                (fn [_]
                  (let [status-label (s/selection filter-combo)
                        ;; {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
                        status-key (label->key status/label-by-key status-label)]
                    ;; 画面
                    (do-refresh @app-state status-key))))


      ;; 初期状態: 未選択なので無効化
      (s/config! [status-combo update-btn delete-btn] :enabled? false)

      ;; 選択状態の変化を監視
      (-> todo-table
          .getSelectionModel
          (.addListSelectionListener
            (reify javax.swing.event.ListSelectionListener
              (valueChanged
                [_ e]
                ;; getValueIsAdjusting が true の間はドラッグ中の中間イベント
                ;; false になった確定時だけ処理する
                (when-not (.getValueIsAdjusting e)
                  (let [selected? (>= (.getSelectedRow todo-table) 0)]
                    (s/config! [status-combo update-btn delete-btn]
                               :enabled? selected?)))))))

      (s/show! frame))))

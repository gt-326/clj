(ns todo-app.core
  (:require
    [clojure.string :as str]))


;; ─── 定数 ─────────────────────────────────────────────────────
(def status-options ["未着手" "進行中" "保留" "完了"])
(def filter-options (into ["全て"] status-options))


;; JSON から返るステータス文字列 → 表示ラベル
(def key->label
  {"todo" "未着手" "doing" "進行中" "pending" "保留" "done" "完了"})


;; 表示ラベル → フィルタ番号（"全て" は含まない → nil）
(def label->filter-num
  {"未着手" 0 "進行中" 1 "保留" 2 "完了" 3})


;; 表示ラベル → 更新番号
(def label->stat-num
  {"未着手" 0 "進行中" 1 "保留" 2 "完了" 3})


;; ─── 状態 ─────────────────────────────────────────────────────
(def state (atom {:todos []}))


;; ─── DOM ヘルパー ─────────────────────────────────────────────
(defn by-id
  [id]
  (.getElementById js/document id))


(defn set-text!
  [el text]
  (set! (.-textContent el) text))


;; ─── エラー表示 ───────────────────────────────────────────────
(defn show-error!
  [msg]
  (let [el (by-id "error-area")]
    (set! (.-className el) "error")
    (set-text! el msg)))


(defn clear-error!
  []
  (let [el (by-id "error-area")]
    (set! (.-className el) "")
    (set-text! el "")))


;; ─── 前方参照 ─────────────────────────────────────────────────
(declare load-todos!)


;; ─── API ──────────────────────────────────────────────────────
(defn fetch!
  [method url body callback]
  (let [opts (cond-> {:method  method
                      :headers {"Content-Type" "application/json"}}
               body (assoc :body (js/JSON.stringify (clj->js body))))]
    (-> (js/fetch url (clj->js opts))
        (.then (fn [resp]
                 (if (= (.-status resp) 204)
                   (do (clear-error!) (callback nil))
                   (-> (.json resp)
                       (.then (fn [data]
                                (let [clj-data (js->clj data :keywordize-keys true)]
                                  (if (:error clj-data)
                                    (show-error! (:error clj-data))
                                    (do (clear-error!) (callback clj-data))))))))))
        (.catch (fn [e] (show-error! (str "通信エラー: " e)))))))


;; ─── フィルタ ─────────────────────────────────────────────────
(defn current-filter-num
  []
  (get label->filter-num (.-value (by-id "filter-select"))))


;; ─── レンダリング ─────────────────────────────────────────────
(defn make-status-select
  [todo-id current-label status]
  (let [sel (.createElement js/document "select")]
    (set! (.-className sel) (str "status-select status-" status))
    (doseq [label status-options]
      (let [opt (.createElement js/document "option")]
        (set-text! opt label)
        (when (= label current-label)
          (set! (.-selected opt) true))
        (.appendChild sel opt)))
    (.addEventListener sel "change"
                       (fn []
                         (let [new-label (.-value sel)
                               stat-num  (get label->stat-num new-label)]
                           (fetch! "PATCH" (str "/todos/" todo-id) {"status" stat-num}
                                   (fn [_] (load-todos! (current-filter-num)))))))
    sel))


(defn make-todo-item
  [{:keys [id title status start-at end-at]}]
  (let [label (get key->label status status)
        li    (.createElement js/document "li")
        span  (.createElement js/document "span")
        span2  (.createElement js/document "span")
        span3  (.createElement js/document "span")
        btn   (.createElement js/document "button")]

    (println "aaa:" status)
    ;; (set! (.-className li)   (str "todo-item status-" label))
    (set! (.-className li)   (str "todo-item status-" status))

    (set! (.-className span) "todo-title")
    (set-text! span title)
    (set! (.-className btn) "delete-btn")
    (set-text! btn "×")
    (.addEventListener btn "click"
                       (fn []
                         (fetch! "DELETE" (str "/todos/" id) nil
                                 (fn [_] (load-todos! (current-filter-num))))))

    ;; レイアウト
    (.appendChild li btn)
    (.appendChild li (make-status-select id label status))
    (.appendChild li span)

    (when start-at
      (set! (.-className span2) "todo-start")
      (set-text! span2 (str "[ 開始：" start-at " ]"))
      (.appendChild li span2))

    (when end-at
      (set! (.-className span3) "todo-end")
      (set-text! span3 (str "[ 終了：" end-at " ]"))
      (.appendChild li span3))

    li))


(defn render-todos!
  [todos]
  (let [ul    (by-id "todo-list")
        stats (by-id "stats")]
    (set! (.-innerHTML ul) "")
    (if (empty? todos)
      (let [li (.createElement js/document "li")]
        (set! (.-className li) "empty-msg")
        (set-text! li "タスクはありません。")
        (.appendChild ul li))
      (doseq [todo todos]
        (.appendChild ul (make-todo-item todo))))
    (set-text! stats (str (count todos) " 件"))))


;; ─── データ取得・再描画 ───────────────────────────────────────
(defn load-todos!
  [filter-num]
  (let [url (if filter-num
              (str "/todos?status=" filter-num)
              "/todos")]
    (fetch! "GET" url nil
            (fn [todos]
              (swap! state assoc :todos todos)
              (render-todos! todos)))))


;; ─── DOM 構築 ─────────────────────────────────────────────────
(defn build-ui!
  []
  (set! (.-innerHTML (by-id "app"))
        (str "<div class='container'>"
             "  <h1>TODO アプリ</h1>"
             "  <div id='error-area'></div>"
             "  <div class='todo-form'>"
             "    <input id='title-input' type='text' placeholder='タスクを入力…' />"
             "    <button id='add-btn'>追加</button>"
             "  </div>"
             "  <div style='text-align:right; margin-bottom:8px;'>"
             "    <label>フィルタ: "
             "      <select id='filter-select'>"
             (str/join "" (map #(str "<option>" % "</option>") filter-options))
             "      </select>"
             "    </label>"
             "  </div>"
             "  <div class='stats' id='stats'></div>"
             "  <ul class='todo-list' id='todo-list'></ul>"
             "</div>")))


;; ─── 初期化 ──────────────────────────────────────────────────
(defn init!
  []
  (build-ui!)
  ;; 追加ボタン
  (.addEventListener (by-id "add-btn") "click"
                     (fn []
                       (let [input (by-id "title-input")
                             title (str/trim (.-value input))]
                         (when-not (str/blank? title)
                           (fetch! "POST" "/todos" {"title" title}
                                   (fn [_]
                                     (set! (.-value input) "")
                                     (load-todos! (current-filter-num))))))))
  ;; Enter キーで追加
  (.addEventListener (by-id "title-input") "keydown"
                     (fn [e]
                       (when (= (.-key e) "Enter")
                         (.click (by-id "add-btn")))))
  ;; フィルタ変更
  (.addEventListener (by-id "filter-select") "change"
                     (fn [] (load-todos! (current-filter-num))))
  ;; 初期データ取得
  (load-todos! nil))


(.addEventListener js/window "load" init!)

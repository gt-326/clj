(ns todo-app.core
  (:require
    [clojure.string :as str]
    [reagent.core :as r]
    [reagent.dom  :as rdom]))


;; ─── 定数 ─────────────────────────────────────────────────────
(def status-options ["未着手" "進行中" "保留" "完了"])
(def filter-options (into ["全て"] status-options))


(def key->label
  {"todo" "未着手" "doing" "進行中" "pending" "保留" "done" "完了"})


(def label->filter-num
  {"未着手" 0 "進行中" 1 "保留" 2 "完了" 3})


(def label->stat-num
  {"未着手" 0 "進行中" 1 "保留" 2 "完了" 3})


;; ─── 状態 ─────────────────────────────────────────────────────
(def state
  (r/atom {:todos        []
           :error        nil
           :filter-label "全て"}))


;; ─── エラー操作 ────────────────────────────────────────────────
(defn show-error!
  [msg]
  (swap! state assoc :error msg))


(defn clear-error!
  []
  (swap! state assoc :error nil))


;; ─── フィルタ ─────────────────────────────────────────────────
(defn current-filter-num
  []
  (get label->filter-num (:filter-label @state)))


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


;; ─── データ取得 ───────────────────────────────────────────────
(defn load-todos!
  [filter-num]
  (let [url (if filter-num
              (str "/todos?status=" filter-num)
              "/todos")]
    (fetch! "GET" url nil
            (fn [todos]
              (swap! state assoc :todos todos)))))


;; ─── コンポーネント ───────────────────────────────────────────
(defn status-select
  [todo-id current-label status]
  [:select {:class     (str "status-select status-" status)
            :value     current-label
            :on-change (fn [e]
                         (let [new-label (.. e -target -value)
                               stat-num  (get label->stat-num new-label)]
                           (if (zero? stat-num)
                             (set! (.. e -target -value) current-label)
                             (fetch!
                               "PATCH"
                               (str "/todos/" todo-id) {"status" stat-num}
                               (fn [_] (load-todos! (current-filter-num)))))))}
   (for [label status-options]
     ^{:key label}
     [:option {:value label} label])])


(defn todo-item
  [{:keys [id title status start-at end-at]}]
  (let [label (get key->label status status)]
    [:li {:class (str "todo-item status-" status)}
     [:button {:class    "delete-btn"
               :on-click (fn []
                           (fetch! "DELETE" (str "/todos/" id) nil
                                   (fn [_] (load-todos! (current-filter-num)))))}
      "×"]
     [status-select id label status]
     [:span {:class "todo-title"} title]
     (when start-at
       [:span {:class "todo-start"} (str "[ 開始：" start-at " ]")])
     (when end-at
       [:span {:class "todo-end"} (str "[ 終了：" end-at " ]")])]))


(defn todo-list
  []
  (let [todos (:todos @state)]
    [:ul {:class "todo-list"}
     (if (empty? todos)
       [:li {:class "empty-msg"} "タスクはありません。"]
       (for [todo todos]
         ^{:key (:id todo)}
         [todo-item todo]))]))


(defn add-form
  []
  (let [title (r/atom "")]
    (fn []
      [:div {:class "todo-form"}
       [:input {:type        "text"
                :placeholder "タスクを入力…"
                :value       @title
                :on-change   (fn [e] (reset! title (.. e -target -value)))
                :on-key-down (fn [e]
                               (when (= (.-key e) "Enter")
                                 (when-not (str/blank? @title)
                                   (fetch! "POST" "/todos" {"title" @title}
                                           (fn [_]
                                             (reset! title "")
                                             (load-todos! (current-filter-num)))))))}]
       [:button {:on-click (fn []
                             (when-not (str/blank? @title)
                               (fetch! "POST" "/todos" {"title" @title}
                                       (fn [_]
                                         (reset! title "")
                                         (load-todos! (current-filter-num))))))}
        "追加"]])))


(defn filter-bar
  []
  [:div {:style {:text-align "right" :margin-bottom "8px"}}
   [:label "フィルタ: "
    [:select {:value     (:filter-label @state)
              :on-change (fn [e]
                           (let [label (.. e -target -value)]
                             (swap! state assoc :filter-label label)
                             (load-todos! (get label->filter-num label))))}
     (for [label filter-options]
       ^{:key label}
       [:option {:value label} label])]]])


(defn app
  []
  [:div {:class "container"}
   [:h1 "TODO アプリ（Reagent）"]
   (when-let [err (:error @state)]
     [:div {:class "error"} err])
   [add-form]
   [filter-bar]
   [:div {:class "stats"}
    (str (count (:todos @state)) " 件")]
   [todo-list]])


;; ─── 初期化 ──────────────────────────────────────────────────
(defn init!
  []
  (rdom/render [app] (.getElementById js/document "app"))
  (load-todos! nil))


(.addEventListener js/window "load" init!)

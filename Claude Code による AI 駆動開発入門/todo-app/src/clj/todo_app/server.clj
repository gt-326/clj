(ns todo-app.server
  (:require
    [clojure.string :as str]
    [compojure.core :refer [routes GET POST PATCH DELETE]]
    [compojure.route :as route]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as resp]
    [todo-app.status :as status]
    [todo-app.store  :as store]
    [todo-app.todo   :as todo]
    [todo-app.util :as util]))


(defn- make-handler
  [app-state]
  (-> (routes
        ;; 一覧取得（?status=1 でフィルタ）
        (GET "/todos" [status]
             (let [todos      (:todos @app-state)
                   status-num (some-> status util/parse-num)
                   status-key (get status/stat-keys status-num)  ; nil なら全件
                   result (util/select-data :status status-key todos)]
               {:status 200 :body result}))

        ;; 1件取得
        (GET "/todos/:id" [id]
             (let [todos (:todos @app-state)
                   id-num (Integer/parseInt id)
                   todo   (first
                            (util/select-data :id id-num todos))]
               (if todo
                 {:status 200 :body todo}
                 {:status 404 :body {:error (str "ID " id-num " のタスクが見つかりません")}})))

        ;; タスク追加
        (POST "/todos" req
              (let [title (get-in req [:body "title"])]
                (if (str/blank? title)
                  {:status 400 :body {:error "title は必須です"}}
                  (let [new-data (todo/add-todo @app-state title)]
                    (store/save-todos! new-data)
                    (reset! app-state new-data)
                    {:status 201 :body (last (:todos @app-state))}))))

        ;; ステータス更新
        (PATCH "/todos/:id" [id :as req]
               (let [id (Integer/parseInt id)
                     stat-num (get-in req [:body "status"])
                     label    (status/label-by-num stat-num)]
                 (cond
                   (nil? label)
                   {:status 400 :body {:error
                                       (str
                                         "status は 1〜3 で指定してください ["
                                         status/msg-update-statuses
                                         "]")}}

                   (zero? stat-num)
                   {:status 400 :body {:error "0:未着手 への変更は不可です"}}

                   ;; 存在チェック
                   (nil? (first (util/select-data :id id (:todos @app-state))))
                   {:status 404 :body {:error (str "ID " id " のタスクが見つかりません")}}

                   :else
                   (let [new-data (todo/update-status @app-state id stat-num (util/now))]
                     (store/save-todos! new-data)
                     (reset! app-state new-data)
                     {:status 200 :body {:id id :status label}}))))

        ;; タスク削除
        (DELETE "/todos/:id" [id]
                (let [id (Integer/parseInt id)
                      todos (:todos @app-state)]
                  ;; 存在チェック
                  (if (nil? (first (util/select-data :id id todos)))
                    {:status 404 :body {:error (str "ID " id " のタスクが見つかりません")}}
                    (let [new-data (todo/delete-todo @app-state id)]
                      (store/save-todos! new-data)
                      (reset! app-state new-data)
                      {:status 204}))))


        (GET "/"        [] (resp/resource-response "public/index.html"         {:root ""}))
        (GET "/vanilla" [] (resp/resource-response "public/index.html"         {:root ""}))
        (GET "/reagent" [] (resp/resource-response "public/reagent/index.html" {:root ""}))

        ;; 静的ファイル（JS/CSS）を classpath から配信
        (route/resources "/")

        (route/not-found {:status 404 :body {:error "not found"}}))

      wrap-json-response
      (wrap-json-body {:keywords? false})
      wrap-params))


(defn run
  [data-atom]
  (jetty/run-jetty
    (make-handler data-atom)           ; ハンドラに渡す
    {:port 3000 :join? true}))

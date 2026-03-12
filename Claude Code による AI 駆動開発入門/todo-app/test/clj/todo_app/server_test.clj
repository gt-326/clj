(ns todo-app.server-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [cheshire.core :as json]
    [ring.mock.request :as mock]
    [todo-app.server :as server]
    [todo-app.store :as store]))


(def empty-data {:next-id 1 :todos []})

(defn- app
  [app-state]
  (#'server/make-handler app-state))

(defn- parse-body
  [resp]
  (when-let [body (:body resp)]
    (json/parse-string body true)))

(defn- json-request
  [method path body-map]
  (-> (mock/request method path)
      (mock/header "content-type" "application/json")
      (mock/body (json/generate-string body-map))))


;;; GET /todos

(deftest get-todos-empty-test
  (let [handler (app (atom empty-data))
        resp    (handler (mock/request :get "/todos"))]
    (testing "空の場合 200 で空配列"
      (is (= 200 (:status resp)))
      (is (= [] (parse-body resp))))))


(deftest get-todos-returns-all-test
  (let [todos   [{:id 1 :title "A" :status :todo  :start-at nil :end-at nil}
                 {:id 2 :title "B" :status :doing :start-at nil :end-at nil}]
        handler (app (atom (assoc empty-data :todos todos)))
        resp    (handler (mock/request :get "/todos"))]
    (testing "200 で全件返す"
      (is (= 200 (:status resp)))
      (is (= 2 (count (parse-body resp)))))))


(deftest get-todos-filter-by-status-test
  (let [todos   [{:id 1 :title "A" :status :todo  :start-at nil :end-at nil}
                 {:id 2 :title "B" :status :doing :start-at nil :end-at nil}]
        handler (app (atom (assoc empty-data :todos todos)))
        resp    (handler (mock/request :get "/todos?status=0"))]
    (testing "status=0 (未着手) でフィルタ → 1件"
      (is (= 200 (:status resp)))
      (is (= 1 (count (parse-body resp)))))))


;;; GET /todos/:id

(deftest get-todo-by-id-found-test
  (let [todos   [{:id 1 :title "A" :status :todo :start-at nil :end-at nil}]
        handler (app (atom (assoc empty-data :todos todos)))
        resp    (handler (mock/request :get "/todos/1"))]
    (testing "存在する id → 200"
      (is (= 200 (:status resp))))
    (testing "タイトルが正しい"
      (is (= "A" (:title (parse-body resp)))))))


(deftest get-todo-by-id-not-found-test
  (let [handler (app (atom empty-data))
        resp    (handler (mock/request :get "/todos/99"))]
    (testing "存在しない id → 404"
      (is (= 404 (:status resp))))))


;;; POST /todos

(deftest post-todo-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [handler (app (atom empty-data))
          resp    (handler (json-request :post "/todos" {"title" "テスト"}))]
      (testing "追加成功 → 201"
        (is (= 201 (:status resp))))
      (testing "追加されたタスクのタイトルが正しい"
        (is (= "テスト" (:title (parse-body resp))))))))


(deftest post-todo-blank-title-test
  (let [handler (app (atom empty-data))
        resp    (handler (json-request :post "/todos" {"title" ""}))]
    (testing "title が空文字 → 400"
      (is (= 400 (:status resp))))))


(deftest post-todo-no-title-test
  (let [handler (app (atom empty-data))
        resp    (handler (json-request :post "/todos" {}))]
    (testing "title キーなし → 400"
      (is (= 400 (:status resp))))))


;;; PATCH /todos/:id

(deftest patch-todo-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [todos   [{:id 1 :title "A" :status :todo :start-at nil :end-at nil}]
          handler (app (atom (assoc empty-data :todos todos)))
          resp    (handler (json-request :patch "/todos/1" {"status" 1}))]
      (testing "ステータス更新 → 200"
        (is (= 200 (:status resp))))
      (testing "更新後のステータスラベルが正しい"
        (is (= "進行中" (:status (parse-body resp))))))))


(deftest patch-todo-not-found-test
  (let [handler (app (atom empty-data))
        resp    (handler (json-request :patch "/todos/99" {"status" 1}))]
    (testing "存在しない id → 404"
      (is (= 404 (:status resp))))))


(deftest patch-todo-invalid-status-test
  (let [todos   [{:id 1 :title "A" :status :todo :start-at nil :end-at nil}]
        handler (app (atom (assoc empty-data :todos todos)))
        resp    (handler (json-request :patch "/todos/1" {"status" 9}))]
    (testing "無効なステータス番号 → 400"
      (is (= 400 (:status resp))))))


(deftest patch-todo-status-zero-test
  (let [todos   [{:id 1 :title "A" :status :doing :start-at nil :end-at nil}]
        handler (app (atom (assoc empty-data :todos todos)))
        resp    (handler (json-request :patch "/todos/1" {"status" 0}))]
    (testing "status=0 (未着手) への変更 → 400"
      (is (= 400 (:status resp))))))


;;; DELETE /todos/:id

(deftest delete-todo-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [todos   [{:id 1 :title "A" :status :todo :start-at nil :end-at nil}]
          handler (app (atom (assoc empty-data :todos todos)))
          resp    (handler (mock/request :delete "/todos/1"))]
      (testing "削除成功 → 204"
        (is (= 204 (:status resp)))))))


(deftest delete-todo-not-found-test
  (let [handler (app (atom empty-data))
        resp    (handler (mock/request :delete "/todos/99"))]
    (testing "存在しない id → 404"
      (is (= 404 (:status resp))))))

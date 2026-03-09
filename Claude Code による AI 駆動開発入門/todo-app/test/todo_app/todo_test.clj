(ns todo-app.todo-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [todo-app.todo :as todo]))


(def empty-data {:next-id 1 :todos []})
(def dt "26-03-07 20:00")


;;; add-todo

(deftest add-todo-test
  (testing "タスクが1件追加される"
    (let [result (todo/add-todo empty-data "買い物をする")]
      (is (= 1 (count (:todos result))))))

  (testing "追加されたタスクのフィールドが正しい"
    (let [result (todo/add-todo empty-data "買い物をする")
          added  (first (:todos result))]
      (is (= {:id 1 :title "買い物をする" :status :todo :start-at nil :end-at nil}
             added))))

  (testing "next-id がインクリメントされる"
    (let [result (todo/add-todo empty-data "タスク")]
      (is (= 2 (:next-id result)))))

  (testing "2件目は id=2 になる"
    (let [data   (todo/add-todo empty-data "タスク1")
          result (todo/add-todo data "タスク2")]
      (is (= 2 (:id (second (:todos result))))))))


;;; update-status

(deftest update-status-doing-test
  (let [data (todo/add-todo empty-data "タスク")]
    (testing ":doing に更新 → start-at が設定され end-at が nil"
      (let [result (todo/update-status data 1 1 dt)
            todo1  (first (:todos result))]
        (is (= :doing (:status todo1)))
        (is (= dt (:start-at todo1)))
        (is (nil? (:end-at todo1)))))))


(deftest update-status-done-test
  (let [data  (-> empty-data
                  (todo/add-todo "タスク")
                  (todo/update-status 1 1 "26-03-07 09:00"))]
    (testing ":done に更新 → end-at が設定される"
      (let [result (todo/update-status data 1 3 dt)
            todo1  (first (:todos result))]
        (is (= :done (:status todo1)))
        (is (= dt (:end-at todo1)))
        ;; start-at は doing のときに設定された値のまま
        (is (= "26-03-07 09:00" (:start-at todo1)))))))


(deftest update-status-pending-test
  (let [data  (-> empty-data
                  (todo/add-todo "タスク")
                  (todo/update-status 1 3 "26-03-07 21:00"))] ; まず :done に
    (testing ":pending に更新 → end-at が nil にリセットされる"
      (let [result (todo/update-status data 1 2 dt)
            todo1  (first (:todos result))]
        (is (= :pending (:status todo1)))
        (is (nil? (:end-at todo1)))))))


(deftest update-status-not-found-test
  (let [data (todo/add-todo empty-data "タスク")]
    (testing "存在しない id を指定しても todos は変わらない"
      (let [result (todo/update-status data 99 1 dt)]
        (is (= (:todos data) (:todos result)))))))


(deftest update-status-other-task-unchanged-test
  (let [data (-> empty-data
                 (todo/add-todo "タスク1")
                 (todo/add-todo "タスク2"))]
    (testing "更新対象以外のタスクは変化しない"
      (let [result (todo/update-status data 1 1 dt)]
        (is (= :todo (:status (second (:todos result)))))))))


;;; delete-todo

(deftest delete-todo-test
  (let [data (-> empty-data
                 (todo/add-todo "タスク1")
                 (todo/add-todo "タスク2"))]
    (testing "指定した id のタスクが削除される"
      (let [result (todo/delete-todo data 1)]
        (is (= 1 (count (:todos result))))
        (is (= 2 (:id (first (:todos result)))))))

    (testing "存在しない id の削除は todos を変えない"
      (let [result (todo/delete-todo data 99)]
        (is (= 2 (count (:todos result))))))

    (testing "全件削除後は空になる"
      (let [result (-> data
                       (todo/delete-todo 1)
                       (todo/delete-todo 2))]
        (is (empty? (:todos result)))))))

(ns todo-app.status-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [todo-app.status :as status]))


(deftest stat-keys-test
  (testing "stat-keys はステータスキーワードのベクター"
    (is (= [:todo :doing :pending :done] status/stat-keys))))


(deftest stat-vals-test
  (testing "stat-vals は表示名のベクター"
    (is (= ["未着手" "進行中" "保留" "完了"] status/stat-vals))))


(deftest label-by-key-test
  (testing "label-by-key はキーワード→表示名のマップ"
    (is (= {:todo "未着手" :doing "進行中" :pending "保留" :done "完了"}
           status/label-by-key))))


(deftest label-by-num-test
  (testing "label-by-num は番号→表示名のソート済みマップ"
    (is (= {0 "未着手" 1 "進行中" 2 "保留" 3 "完了"}
           status/label-by-num)))
  (testing "sorted-map なのでキーが昇順"
    (is (= [0 1 2 3] (keys status/label-by-num)))))


(deftest msg-statuses-test
  (testing "全ステータスを / 区切りで列挙"
    (is (= "0:未着手 / 1:進行中 / 2:保留 / 3:完了"
           status/msg-statuses))))


(deftest msg-update-statuses-test
  (testing "update で指定可能なステータス（未着手=0 を除く）"
    (is (= "1:進行中 / 2:保留 / 3:完了"
           status/msg-update-statuses))))


(deftest get-key-by-label-test
  (testing "表示名からキーワードを返す"
    (is (= :todo    (status/get-key-by-label "未着手")))
    (is (= :doing   (status/get-key-by-label "進行中")))
    (is (= :pending (status/get-key-by-label "保留")))
    (is (= :done    (status/get-key-by-label "完了"))))
  (testing "存在しない表示名は nil を返す"
    (is (nil? (status/get-key-by-label "不明")))
    (is (nil? (status/get-key-by-label "")))))


(deftest get-num-by-label-test
  (testing "表示名から番号を返す"
    (is (= 0 (status/get-num-by-label "未着手")))
    (is (= 1 (status/get-num-by-label "進行中")))
    (is (= 2 (status/get-num-by-label "保留")))
    (is (= 3 (status/get-num-by-label "完了"))))
  (testing "存在しない表示名は nil を返す"
    (is (nil? (status/get-num-by-label "不明")))
    (is (nil? (status/get-num-by-label "")))))

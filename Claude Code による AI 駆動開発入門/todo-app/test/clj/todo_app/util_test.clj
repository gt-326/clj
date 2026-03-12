(ns todo-app.util-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [todo-app.util :as util]))


;;; parse-num

(deftest parse-num-valid-test
  (testing "有効な整数文字列は整数を返す"
    (is (= 0  (util/parse-num "0")))
    (is (= 1  (util/parse-num "1")))
    (is (= 42 (util/parse-num "42")))
    (is (= -1 (util/parse-num "-1")))))


(deftest parse-num-invalid-test
  (testing "無効な文字列は nil を返す"
    (is (nil? (util/parse-num "abc")))
    (is (nil? (util/parse-num "")))
    (is (nil? (util/parse-num "1.5")))
    (is (nil? (util/parse-num "1a")))))


;;; now

(deftest now-format-test
  (testing "now は \"yy-MM-dd HH:mm\" 形式の文字列を返す"
    (is (re-matches #"\d{2}-\d{2}-\d{2} \d{2}:\d{2}" (util/now)))))


;;; select-data

(def todos
  [{:id 1 :title "A" :status :todo}
   {:id 2 :title "B" :status :doing}
   {:id 3 :title "C" :status :todo}])


(deftest select-data-no-condition-test
  (testing "condition が nil のとき全件返す"
    (is (= todos (util/select-data :status nil todos)))))


(deftest select-data-with-condition-test
  (testing "condition に一致するものだけ返す"
    (let [result (util/select-data :status :todo todos)]
      (is (= 2 (count result)))
      (is (every? #(= :todo (:status %)) result))))

  (testing "一致しない condition は空ベクターを返す"
    (is (= [] (util/select-data :status :done todos)))))


(deftest select-data-by-id-test
  (testing ":id フィールドでも絞り込める"
    (let [result (util/select-data :id 2 todos)]
      (is (= 1 (count result)))
      (is (= "B" (:title (first result)))))))


(deftest select-data-returns-vector-test
  (testing "戻り値はベクター"
    (is (vector? (util/select-data :status :todo todos)))
    (is (vector? (util/select-data :status nil todos)))))

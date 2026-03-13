(ns todo-app.store-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [todo-app.store :as store])
  (:import [java.io File]))


;; テストごとに一時ファイルパスを差し替えるフィクスチャ
;;
;; with-redefs で store/data-file（delay）を新しい delay に置き換えると、
;; @store/data-file が一時ファイルのパスを返すようになる。

(defn- temp-edn-path
  "システム一時ディレクトリに一時ファイルパスを作る（ファイルは作らない）"
  []
  (let [tmp (File/createTempFile "todo-store-test" ".edn")]
    (.delete tmp)   ; ← 存在しない状態で開始
    (.getAbsolutePath tmp)))


(defn- with-temp-store [f]
  (let [path (temp-edn-path)]
    (with-redefs [store/data-file (delay path)]
      (f))
    (let [file (File. path)]
      (when (.exists file) (.delete file)))))


(use-fixtures :each with-temp-store)


;;; initialize-store!

(deftest initialize-store-creates-file-test
  (testing "ファイルが存在しない場合、初期データで生成される"
    (store/initialize-store!)
    (is (.exists (File. @store/data-file))))

  (testing "生成されたファイルの内容は default-data"
    (store/initialize-store!)
    (is (= store/default-data (store/load-todos)))))


(deftest initialize-store-no-overwrite-test
  (testing "ファイルが既に存在する場合、上書きしない"
    (let [existing {:next-id 5
                    :todos [{:id 4 :title "既存タスク"
                             :status :done :start-at nil :end-at nil}]}]
      (spit @store/data-file (pr-str existing))
      (store/initialize-store!)
      (is (= existing (store/load-todos))))))


;;; load-todos

(deftest load-todos-valid-edn-test
  (testing "正常な EDN を読み込める"
    (let [data {:next-id 2
                :todos [{:id 1 :title "タスク"
                         :status :todo :start-at nil :end-at nil}]}]
      (spit @store/data-file (pr-str data))
      (is (= data (store/load-todos))))))


(deftest load-todos-broken-file-test
  (testing "EDN が壊れている場合は default-data にフォールバック"
    ;; "{" は閉じていないマップ → edn/read-string が例外を投げる
    (spit @store/data-file "{")
    (is (= store/default-data (store/load-todos)))))


(deftest load-todos-missing-file-test
  (testing "ファイルが存在しない場合は default-data にフォールバック"
    ;; フィクスチャがファイルを削除した状態で開始するので、そのままテスト
    (is (= store/default-data (store/load-todos)))))


;;; save-todos!

(deftest save-todos-test
  (testing "保存したデータをそのまま読み返せる"
    (let [data {:next-id 3
                :todos [{:id 1 :title "保存テスト"
                         :status :doing :start-at "26-03-07 10:00" :end-at nil}]}]
      (store/save-todos! data)
      (is (= data (store/load-todos))))))


(deftest save-todos-overwrites-test
  (testing "2回保存すると2回目の内容で上書きされる"
    (store/save-todos! {:next-id 1 :todos []})
    (let [data {:next-id 5 :todos []}]
      (store/save-todos! data)
      (is (= data (store/load-todos))))))

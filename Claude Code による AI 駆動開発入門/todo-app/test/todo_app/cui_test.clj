(ns todo-app.cui-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [todo-app.cui :as core]
    [todo-app.store :as store]))


(def dt "26-03-07 20:00")
(def empty-data {:next-id 1 :todos []})

(defn make-atom
  ([]      (atom empty-data))
  ([data]  (atom data)))



;;; parse-command: add

(deftest parse-command-add-test
  (testing "単語1つのタスク名"
    (is (= {:cmd "add" :title "買い物"}
           (core/parse-command "add" ["買い物"] dt))))

  (testing "スペース区切りのタスク名はスペースで結合"
    (is (= {:cmd "add" :title "買い物 掃除 洗濯"}
           (core/parse-command "add" ["買い物" "掃除" "洗濯"] dt))))

  (testing "タスク名が空（引数なし）→ エラー"
    (is (contains? (core/parse-command "add" [] dt) :error)))

  (testing "タスク名が空文字列 → エラー"
    (is (contains? (core/parse-command "add" [""] dt) :error))))


;;; parse-command: list
;;
;; NOTE: status-num が nil や範囲外のとき、現在の実装では
;;        (stat-keys nil) / (stat-keys 9) を呼び出すため
;;        NullPointerException / IndexOutOfBoundsException が発生する。
;;        下記テストはその修正後を前提として「あるべき動作」を記述している。

(deftest parse-command-list-test
  (testing "引数なし → filter-status-key が nil"
    (is (= {:cmd "list" :filter-status-key nil}
           (core/parse-command "list" [] dt))))

  (testing "有効なステータス番号でフィルタ"
    (is (= {:cmd "list" :filter-status-key :todo}
           (core/parse-command "list" ["0"] dt)))
    (is (= {:cmd "list" :filter-status-key :done}
           (core/parse-command "list" ["3"] dt))))

  (testing "無効なステータス番号 → エラー"
    (is (contains? (core/parse-command "list" ["9"] dt) :error)))

  (testing "数値でない引数 → filter-status-key が nil（無視）"
    ;; parse-id が nil を返すため status-num = nil → フィルタなし扱い
    (is (= {:cmd "list" :filter-status-key nil}
           (core/parse-command "list" ["abc"] dt)))))


;;; parse-command: update

(deftest parse-command-update-test
  (testing "有効な id・ステータス番号"
    (let [result (core/parse-command "update" ["1" "1"] dt)]
      (is (= "update"  (:cmd result)))
      (is (= 1         (:id result)))
      (is (= "進行中"  (:status-label result)))
      (is (= 1         (:status-num result)))
      (is (= dt        (:now result)))))

  (testing "ステータス 2 (保留) も有効"
    (is (= 2 (:status-num (core/parse-command "update" ["5" "2"] dt)))))

  (testing "ステータス 3 (完了) も有効"
    (is (= 3 (:status-num (core/parse-command "update" ["5" "3"] dt)))))

  (testing "ステータス 0 (未着手) への変更は不可 → エラー"
    (is (contains? (core/parse-command "update" ["1" "0"] dt) :error)))

  (testing "ステータス番号が範囲外 → エラー"
    (is (contains? (core/parse-command "update" ["1" "9"] dt) :error)))

  (testing "ステータス番号が未指定 → エラー"
    (is (contains? (core/parse-command "update" ["1"] dt) :error)))

  (testing "id が数値でない → エラー"
    (is (contains? (core/parse-command "update" ["abc" "1"] dt) :error)))

  (testing "id が未指定 → エラー"
    (is (contains? (core/parse-command "update" [] dt) :error))))


;;; parse-command: delete

(deftest parse-command-delete-test
  (testing "有効な id"
    (is (= {:cmd "delete" :id 1}
           (core/parse-command "delete" ["1"] dt))))

  (testing "id が数値でない → エラー"
    (is (contains? (core/parse-command "delete" ["abc"] dt) :error)))

  (testing "id が未指定 → エラー"
    (is (contains? (core/parse-command "delete" [] dt) :error))))


;;; parse-command: help / unknown

(deftest parse-command-help-test
  (testing "\"help\" コマンド"
    (is (= {:cmd "help"}
           (core/parse-command "help" [] dt))))

  (testing "不明コマンドは help 扱い"
    (is (= {:cmd "help"}
           (core/parse-command "unknown" [] dt)))))


;;; format-todos

(deftest format-todos-empty-test
  (testing "タスクが空のとき"
    (is (= "タスクはありません。" (core/format-todos [])))))


(deftest format-todos-todo-test
  (let [todos [{:id 1 :title "未着手タスク" :status :todo :start-at nil :end-at nil}]
        line  (core/format-todos todos)]
    (testing "未着手は全角スペース"
      (is (str/includes? line "[　]")))
    (testing "タイトルを含む"
      (is (str/includes? line "未着手タスク")))
    (testing "日時フィールドは空"
      (is (str/includes? line "[  ]")))))


(deftest format-todos-doing-test
  (let [todos [{:id 2 :title "進行中タスク" :status :doing :start-at "26-03-07 10:00" :end-at nil}]
        line  (core/format-todos todos)]
    (testing "進行中は「進」"
      (is (str/includes? line "[進]")))
    (testing "開始日時を含む"
      (is (str/includes? line "開始:26-03-07 10:00")))))


(deftest format-todos-done-test
  (let [todos [{:id 3 :title "完了タスク" :status :done
                :start-at "26-03-07 09:00" :end-at "26-03-07 11:00"}]
        line  (core/format-todos todos)]
    (testing "完了は「完」"
      (is (str/includes? line "[完]")))
    (testing "終了日時を含む"
      (is (str/includes? line "終了:26-03-07 11:00")))))


(deftest format-todos-multiple-test
  (let [todos [{:id 1 :title "A" :status :todo  :start-at nil :end-at nil}
               {:id 2 :title "B" :status :doing :start-at dt  :end-at nil}]
        result (core/format-todos todos)]
    (testing "複数タスクは改行区切り"
      (is (= 2 (count (str/split-lines result)))))))


;;; format-result

(deftest format-result-error-test
  (testing "エラーは :error の値をそのまま返す"
    (is (= "エラーです" (core/format-result {:error "エラーです"})))))


(deftest format-result-add-test
  (testing "add 成功メッセージ"
    (is (= "タスクを追加しました: 買い物"
           (core/format-result {:cmd "add" :title "買い物"})))))


(deftest format-result-list-test
  (testing "list 結果は format-todos を通る"
    (is (= "タスクはありません。"
           (core/format-result {:cmd "list" :todos []})))))


(deftest format-result-update-test
  (testing "update 成功"
    (is (= "タスク 1 を「進行中」にしました。"
           (core/format-result {:cmd "update" :id 1 :status-label "進行中" :found? true}))))
  (testing "update 失敗（id 未検出）"
    (is (= "エラー: ID 99 のタスクが見つかりません。"
           (core/format-result {:cmd "update" :id 99 :status-label "進行中" :found? false})))))


(deftest format-result-delete-test
  (testing "delete 成功"
    (is (= "タスク 1 を削除しました。"
           (core/format-result {:cmd "delete" :id 1 :found? true}))))
  (testing "delete 失敗（id 未検出）"
    (is (= "エラー: ID 99 のタスクが見つかりません。"
           (core/format-result {:cmd "delete" :id 99 :found? false})))))


(deftest format-result-help-test
  (testing "help にはコマンド名が含まれる"
    (let [help (core/format-result {:cmd "help"})]
      (is (str/includes? help "add"))
      (is (str/includes? help "list"))
      (is (str/includes? help "update"))
      (is (str/includes? help "delete")))))


;;; execute-command! — store/save-todos! をモック化

(deftest execute-command-add-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [da     (make-atom)
          result (core/execute-command! {:cmd "add" :title "テスト" :data-atom da})]
      (testing "戻り値の :cmd は \"add\""
        (is (= "add" (:cmd result))))
      (testing "atom に1件追加される"
        (is (= 1 (count (:todos @da)))))
      (testing "追加されたタイトルが正しい"
        (is (= "テスト" (:title (first (:todos @da)))))))))


(deftest execute-command-list-no-filter-test
  (let [todos [{:id 1 :title "A" :status :todo  :start-at nil :end-at nil}
               {:id 2 :title "B" :status :doing :start-at dt  :end-at nil}]
        da    (make-atom (assoc empty-data :todos todos))
        result (core/execute-command! {:cmd "list" :filter-status-key nil :data-atom da})]
    (testing "フィルタなしは全件返る"
      (is (= 2 (count (:todos result)))))))


(deftest execute-command-list-with-filter-test
  (let [todos [{:id 1 :title "A" :status :todo  :start-at nil :end-at nil}
               {:id 2 :title "B" :status :doing :start-at dt  :end-at nil}]
        da    (make-atom (assoc empty-data :todos todos))
        result (core/execute-command! {:cmd "list" :filter-status-key :todo :data-atom da})]
    (testing ":todo でフィルタすると1件"
      (is (= 1 (count (:todos result)))))
    (testing "フィルタされたタスクが正しい"
      (is (= "A" (:title (first (:todos result))))))))


(deftest execute-command-update-found-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [todos [{:id 1 :title "タスク" :status :todo :start-at nil :end-at nil}]
          da    (make-atom (assoc empty-data :todos todos))
          result (core/execute-command!
                   {:cmd "update" :id 1 :status-num 1 :status-label "進行中"
                    :now dt :data-atom da})]
      (testing ":found? が true"
        (is (true? (:found? result))))
      (testing "atom のステータスが更新される"
        (is (= :doing (:status (first (:todos @da)))))))))


(deftest execute-command-update-not-found-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [da    (make-atom)
          result (core/execute-command!
                   {:cmd "update" :id 99 :status-num 1 :status-label "進行中"
                    :now dt :data-atom da})]
      (testing ":found? が false"
        (is (false? (:found? result)))))))


(deftest execute-command-delete-found-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [todos [{:id 1 :title "タスク" :status :todo :start-at nil :end-at nil}]
          da    (make-atom (assoc empty-data :todos todos))
          result (core/execute-command! {:cmd "delete" :id 1 :data-atom da})]
      (testing ":found? が true"
        (is (true? (:found? result))))
      (testing "atom からタスクが消える"
        (is (= 0 (count (:todos @da))))))))


(deftest execute-command-delete-not-found-test
  (with-redefs [store/save-todos! (fn [_] nil)]
    (let [da    (make-atom)
          result (core/execute-command! {:cmd "delete" :id 99 :data-atom da})]
      (testing ":found? が false"
        (is (false? (:found? result)))))))


(deftest execute-command-error-passthrough-test
  (testing "エラーマップはそのまま返す（副作用なし）"
    (let [result (core/execute-command! {:error "エラー"})]
      (is (= "エラー" (:error result))))))


(deftest execute-command-help-passthrough-test
  (testing "help コマンドはそのまま返す（副作用なし）"
    (let [result (core/execute-command! {:cmd "help"})]
      (is (= "help" (:cmd result))))))

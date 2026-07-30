(ns paip.gps-ver02-test
  (:require
    [clojure.test :refer :all]
    [paip.gps-ver02 :as gps]))


;; *ops* の root binding をテスト間で漏らさないためのフィクスチャ
;; （my-use が alter-var-root で root を書き換えるため）

(use-fixtures :each
  (fn [f]
    (let [snapshot gps/*ops*]
      (try
        (f)
        (finally
          (alter-var-root #'gps/*ops* (constantly snapshot)))))))


;; [ 4.11.1 ] executing マーカー関連のヘルパー

(deftest starts-with-test
  (is (true? (gps/starts-with '(executing foo) 'executing)))
  (is (false? (gps/starts-with '(foo bar) 'executing)))
  (is (nil? (gps/starts-with '() 'executing))))


(deftest executing?-test
  (testing "個々の達成済み事実（(executing action)の形）に対しては正しく判定できる"
    (is (true? (gps/executing? '(executing (move a b))))))

  (testing "通常の事実には偽"
    (is (false? (gps/executing? '(son-at-home))))))


(deftest convert-op-test
  (testing "add-listの先頭にexecutingマーカーを付与する"
    (let [op (gps/->op 'drive '(a) '(son-at-school) '(b))
          converted (gps/convert-op op)]
      (is (= (list (list 'executing 'drive) 'son-at-school)
             (:add-list converted)))))

  (testing "既知の挙動: executing?はadd-list全体でなく個々の要素を想定した述語のため、convert-opの二重付与防止チェックは機能せず、変換済みのopに再度適用すると重複して付与される"
    (let [already-converted (gps/->op 'drive '(a) (list (list 'executing 'drive) 'son-at-school) '(b))
          converted-again (gps/convert-op already-converted)]
      (is (= (list (list 'executing 'drive) (list 'executing 'drive) 'son-at-school)
             (:add-list converted-again))))))


(deftest make-op2-test
  (let [op (gps/make-op2 'drive-son-to-school
                         '(son-at-home car-works)
                         '(son-at-school)
                         '(son-at-home))]
    (is (= '(son-at-home car-works) (:preconds op)))
    (is (= (list (list 'executing 'drive-son-to-school) 'son-at-school) (:add-list op)))
    (is (= '(son-at-home) (:del-list op)))))


(deftest member-equal-test
  (is (= '(1 3 5 7) (gps/member-equal 1 '(0 2 4 6 1 3 5 7))))
  (is (nil? (gps/member-equal 10 '(0 2 4 6 1 3 5 7)))))


(deftest appropriate?-test
  (let [op (gps/make-op2 'drive-son-to-school
                         '(son-at-home car-works)
                         '(son-at-school)
                         '(son-at-home))]
    (is (some? (gps/appropriate? 'son-at-school op)))
    (is (nil? (gps/appropriate? 'have-money op)))))


;; achieve / apply-op / achieve-all の単体テスト

(deftest achieve-test
  (testing "ゴールがすでに状態に含まれていれば、その位置からの残り状態を返す"
    (is (= '(son-at-home car-works)
           (gps/achieve '(son-at-home car-works) 'son-at-home nil gps/school-ops))))

  (testing "オペレータの連鎖でゴールを達成できる"
    (let [result (gps/achieve '(son-at-home car-needs-battery have-money have-phone-book)
                              'son-at-school nil gps/school-ops)]
      (is (some? result))
      (is (some? (gps/member-equal 'son-at-school result)))))

  (testing "達成する手段がなければnil"
    (is (nil? (gps/achieve '(son-at-home car-needs-battery have-money)
                           'son-at-school nil gps/school-ops)))))


(deftest apply-op-test
  (let [op (gps/make-op2 'drive-son-to-school
                         '(son-at-home car-works)
                         '(son-at-school)
                         '(son-at-home))]
    (testing "前提条件を満たしていれば、executingマーカー付きで状態が更新される"
      (let [result (gps/apply-op '(son-at-home car-works) 'son-at-school op nil gps/school-ops)]
        (is (some? (gps/member-equal 'son-at-school result)))
        (is (some? (gps/member-equal (list 'executing 'drive-son-to-school) result)))
        (is (nil? (gps/member-equal 'son-at-home result)))))

    (testing "前提条件を満たせなければnil"
      (is (nil? (gps/apply-op '(son-at-home) 'son-at-school op nil gps/school-ops))))))


(deftest achieve-all-test
  (testing "すべてのゴールを達成できれば、達成後の状態を返す"
    (is (some? (gps/achieve-all '(son-at-home car-needs-battery have-money have-phone-book)
                                '(son-at-school) nil gps/school-ops))))

  (testing "達成できなければnil"
    (is (nil? (gps/achieve-all '(son-at-home car-needs-battery have-money)
                               '(son-at-school) nil gps/school-ops))))

  (testing "シブリング目標の打ち消しをsubsetチェックで検出し、nilを返す"
    (is (nil? (gps/achieve-all '(son-at-home car-needs-battery have-money have-phone-book)
                               '(have-money son-at-school) nil gps/school-ops)))))


;; gps_2（4.11以降の統合エントリポイント）

(deftest gps_2-basic-test
  (is (= '((start)
           (executing look-up-number)
           (executing telephone-shop)
           (executing tell-shop-problem)
           (executing give-shop-money)
           (executing shop-installs-battery)
           (executing drive-son-to-school))
         (gps/gps_2 '(son-at-home car-needs-battery have-money have-phone-book)
                    '(son-at-school)
                    gps/school-ops)))

  (is (= '((start) (executing drive-son-to-school))
         (gps/gps_2 '(son-at-home car-works) '(son-at-school) gps/school-ops))))


;; [ 4.7 ] シブリング目標を打ち消す問題
;; ver01のgpsと異なり、achieve-allのsubsetチェックにより正しく失敗として検出できる

(deftest gps_2-sibling-goal-clobbering-test
  (is (= '() (gps/gps_2 '(son-at-home car-needs-battery have-money have-phone-book)
                        '(have-money son-at-school)
                        gps/school-ops))))


;; [ 4.8 ] 調べることなく跳躍する問題

(deftest gps_2-look-before-leap-test
  (is (= '() (gps/gps_2 '(son-at-home car-needs-battery have-money have-phone-book)
                        '(son-at-school have-money)
                        gps/school-ops))))


;; [ 4.9 ] 再帰的な副目標問題
;; goal-stackによる循環検出のおかげで、ver01のようなStackOverflowErrorは起きず、
;; 正しく失敗として検出される（school-opsにはすでに循環を生むask-phone-numberが含まれている）

(deftest gps_2-recursive-subgoal-test
  (is (= '() (gps/gps_2 '(son-at-home car-needs-battery have-money)
                        '(son-at-school)
                        gps/school-ops))))


;; [ 4.11 ] 行動を要求しない目標

(deftest gps_2-no-action-needed-test
  (is (= '((start))
         (gps/gps_2 '(son-at-home) '(son-at-home) gps/school-ops))))


;; *ops* / my-use （ダイナミックスコープの利便性と例外安全性）

(deftest my-use-and-dynamic-ops-test
  (testing "my-useはroot bindingを書き換え、以後opsを省略したgps_2呼び出しに引き継がれる"
    (is (= (count gps/school-ops) (gps/my-use gps/school-ops)))
    (is (= '((start) (executing drive-son-to-school))
           (gps/gps_2 '(son-at-home car-works) '(son-at-school)))))

  (testing "gps_2にopsを明示すれば、その回だけ一時的に上書きされ、終了後はroot bindingへ戻る"
    (let [limited (list (first gps/school-ops))]
      (is (= '((start) (executing drive-son-to-school))
             (gps/gps_2 '(son-at-home car-works) '(son-at-school) limited)))
      (is (= (count gps/school-ops) (count gps/*ops*)))))

  (testing "binding内で例外が起きてもroot bindingは正しく巻き戻る"
    (is (thrown? Exception
          (binding [gps/*ops* gps/school-ops]
            (throw (ex-info "boom" {})))))
    (is (= (count gps/school-ops) (count gps/*ops*)))))

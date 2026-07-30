(ns paip.gps-ver01-test
  (:require
    [clojure.test :refer :all]
    [paip.gps-ver01 :as gps]))


(def full-state
  #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book})


;; [ 4.1 ~ 4.5 ] 基本要素の単体テスト

(deftest appropriate?-test
  (testing "add-listにgoalが含まれていれば適合するオペレータと判定する"
    (is (true? (gps/appropriate?
                 'son-at-school
                 (gps/make-op 'drive-son-to-school
                              '(son-at-home car-works)
                              #{'son-at-school}
                              #{'son-at-home})))))

  (testing "add-listにgoalが含まれていなければ適合しない"
    (is (false? (gps/appropriate?
                  'have-money
                  (gps/make-op 'drive-son-to-school
                               '(son-at-home car-works)
                               #{'son-at-school}
                               #{'son-at-home}))))))


(deftest apply-op-test
  (testing "前提条件をすべて満たしていれば、状態を書き換えて真を返す"
    (let [state (atom #{'son-at-home 'car-works})
          op (gps/make-op 'drive-son-to-school
                          '(son-at-home car-works)
                          #{'son-at-school}
                          #{'son-at-home})]
      (is (some? (gps/apply-op op gps/school-ops state gps/achieve)))
      (is (contains? @state 'son-at-school))
      (is (not (contains? @state 'son-at-home)))))

  (testing "前提条件を満たせなければ、状態を変更せずnilを返す"
    (let [state (atom #{'son-at-home})
          op (gps/make-op 'drive-son-to-school
                          '(son-at-home car-works)
                          #{'son-at-school}
                          #{'son-at-home})]
      (is (nil? (gps/apply-op op gps/school-ops state gps/achieve)))
      (is (= #{'son-at-home} @state)))))


(deftest achieve-test
  (testing "ゴールがすでに状態に含まれていれば真"
    (is (true? (gps/achieve 'son-at-home gps/school-ops (atom full-state)))))

  (testing "オペレータの連鎖でゴールを達成できる"
    (is (some? (gps/achieve 'son-at-school gps/school-ops (atom full-state)))))

  (testing "達成する手段がなければ偽"
    (is (nil? (gps/achieve 'son-at-school gps/school-ops
                           (atom #{'son-at-home 'car-needs-battery 'have-money}))))))


;; [ 4.1 ~ 4.5 ] gps の基本ケース

(deftest gps-basic-test
  (is (= 'SOLVED
         (gps/gps full-state '(son-at-school) gps/school-ops)))

  (is (nil?
        (gps/gps #{'son-at-home 'car-needs-battery 'have-money}
                 '(son-at-school) gps/school-ops)))

  (is (= 'SOLVED
         (gps/gps #{'son-at-home 'car-works} '(son-at-school) gps/school-ops))))


;; [ 4.6 ~ 4.7 ] シブリング目標を打ち消す問題

(deftest gps-sibling-goal-clobbering-test
  (testing "打ち消しが起きないケースは正しく解ける"
    (is (= 'SOLVED
           (gps/gps #{'son-at-home 'have-money 'car-works}
                    '(have-money son-at-school) gps/school-ops))))

  (testing "gpsはシブリング目標の打ち消しを検出できず、誤ってSOLVEDを返す（既知のバグ）"
    (is (= 'SOLVED
           (gps/gps full-state '(have-money son-at-school) gps/school-ops))))

  (testing "achieve-all?/gps_1.2は達成後の状態を再検証し、打ち消しを正しく検出する"
    (is (nil?
          (gps/gps_1.2 full-state '(have-money son-at-school) gps/school-ops)))))


;; [ 4.8 ] 調べることなく跳躍する問題

(deftest gps-look-before-leap-test
  (testing "gpsはゴール順序次第で本来失敗すべきケースを正しくnilと判定する"
    (is (nil?
          (gps/gps full-state '(son-at-school have-money) gps/school-ops))))

  (testing "gps_1.2でも同様にnil"
    (is (nil?
          (gps/gps_1.2 full-state '(son-at-school have-money) gps/school-ops)))))


;; [ 4.9 ] 再帰的な副目標問題

(deftest gps-recursive-subgoal-test
  (let [school-ops2 (cons
                      (gps/make-op 'ask-phone-number
                                   '(in-communication-with-shop)
                                   #{'know-phone-number})
                      gps/school-ops)]
    (testing "循環したオペレータ定義はStackOverflowErrorを引き起こす（既知の制限）"
      (is (thrown? StackOverflowError
            (gps/gps #{'son-at-home 'car-needs-battery 'have-money}
                     '(son-at-school) school-ops2))))))


;; [ 4.10 ] デバッグ用フラグ

(deftest debug-flags-test
  (gps/undebug)

  (testing "debugでidを追加できる"
    (gps/debug :a :b)
    (is (= #{:a :b} @gps/dbg-ids)))

  (testing "undebugで指定したidだけ取り除ける"
    (gps/undebug :a)
    (is (= #{:b} @gps/dbg-ids)))

  (testing "引数なしのundebugは何もしない（common/utils.cljの実装とは異なるver01固有の挙動）"
    (gps/undebug)
    (is (= #{:b} @gps/dbg-ids))))

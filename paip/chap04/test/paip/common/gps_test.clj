(ns paip.common.gps-test
  (:require
    [clojure.test :refer :all]
    [paip.banana :as banana]
    [paip.block :as block]
    [paip.common.gps :as gps]
    [paip.maze :as maze]
    [paip.other :as other]))


;; *ops* の root binding をテスト間で漏らさないためのフィクスチャ
;; （my-use が alter-var-root で root を書き換えるため）

(use-fixtures :each
  (fn [f]
    (let [snapshot gps/*ops*]
      (try
        (f)
        (finally
          (alter-var-root #'gps/*ops* (constantly snapshot)))))))


;; [ 4.13.1 ] gps_2：cl-atom? で除去するだけなので、消し忘れた事実（末尾の (at 25)）が
;; 結果に残ってしまう「微妙なバグ」がある

(deftest gps_2-maze-test
  (is (= '((start)
           (executing (move from 1 to 2))
           (executing (move from 2 to 3))
           (executing (move from 3 to 4))
           (executing (move from 4 to 9))
           (executing (move from 9 to 8))
           (executing (move from 8 to 7))
           (executing (move from 7 to 12))
           (executing (move from 12 to 11))
           (executing (move from 11 to 16))
           (executing (move from 16 to 17))
           (executing (move from 17 to 22))
           (executing (move from 22 to 23))
           (executing (move from 23 to 24))
           (executing (move from 24 to 19))
           (executing (move from 19 to 20))
           (executing (move from 20 to 25))
           (at 25))
         (gps/gps_2 false '((at 1)) '((at 25)) maze/maze-ops))))


(deftest gps_2-banana-test
  (is (= '((start)
           (executing push-chair-from-door-to-middle-room)
           (executing climb-on-chair)
           (executing drop-ball)
           (executing grasp-bananas)
           (executing eat-bananas))
         (gps/gps_2 false
                    '(at-door on-floor has-ball hungry chair-at-door)
                    '(not-hungry)
                    banana/banana-ops))))


;; [ 4.13.2 ] gps_3：action? でフィルタするので、gps_2 の「微妙なバグ」（末尾の (at 25)）が解消される

(deftest gps_3-maze-fixes-subtle-bug-test
  (is (= '((start)
           (executing (move from 1 to 2))
           (executing (move from 2 to 3))
           (executing (move from 3 to 4))
           (executing (move from 4 to 9))
           (executing (move from 9 to 8))
           (executing (move from 8 to 7))
           (executing (move from 7 to 12))
           (executing (move from 12 to 11))
           (executing (move from 11 to 16))
           (executing (move from 16 to 17))
           (executing (move from 17 to 22))
           (executing (move from 22 to 23))
           (executing (move from 23 to 24))
           (executing (move from 24 to 19))
           (executing (move from 19 to 20))
           (executing (move from 20 to 25)))
         (gps/gps_3 false '((at 1)) '((at 25)) maze/maze-ops))))


(deftest gps_3-block-basic-test
  (is (= '((start) (executing (move a from table to b)))
         (gps/gps_3 false
                    '((a on table) (b on table) (space on a) (space on b) (space on table))
                    '((a on b) (b on table))
                    (block/make-block-ops '(a b)))))

  (is (= '((start)
           (executing (move a from b to table))
           (executing (move b from table to a)))
         (gps/gps_3 true
                    '((a on b) (b on table) (space on a) (space on table))
                    '((b on a))
                    (block/make-block-ops '(a b))))))


(deftest gps_3-block-sibling-goal-clobbering-test
  (let [ops (block/make-block-ops '(a b c))]
    (testing "打ち消しが起きない順序では解ける"
      (is (= '((start)
               (executing (move a from b to table))
               (executing (move b from c to a))
               (executing (move c from table to b)))
             (gps/gps_3 false
                        '((a on b) (b on c) (c on table) (space on a) (space on table))
                        '((b on a) (c on b))
                        ops))))

    (testing "ゴール順序を入れ替えると「シブリング目標を打ち消す」問題により解けなくなる（既知のバグ）"
      (is (= '()
             (gps/gps_3 false
                        '((a on b) (b on c) (c on table) (space on a) (space on table))
                        '((c on b) (b on a))
                        ops))))))


;; [ 4.13.3 ] gps_4：printflg を持たず、'(start) を状態に付加しない。executing? のみで抽出する

(deftest gps_4-maze-test
  (is (= '((executing (move from 1 to 2))
           (executing (move from 2 to 3))
           (executing (move from 3 to 4))
           (executing (move from 4 to 9))
           (executing (move from 9 to 8))
           (executing (move from 8 to 7))
           (executing (move from 7 to 12))
           (executing (move from 12 to 11))
           (executing (move from 11 to 16))
           (executing (move from 16 to 17))
           (executing (move from 17 to 22))
           (executing (move from 22 to 23))
           (executing (move from 23 to 24))
           (executing (move from 24 to 19))
           (executing (move from 19 to 20))
           (executing (move from 20 to 25)))
         (gps/gps_4 '((at 1)) '((at 25)) maze/maze-ops))))


(deftest find-path-uses-gps_4-test
  (gps/my-use maze/maze-ops)

  (testing "gps_4 を介して迷路の経路を求められる"
    (is (= '(1 2 3 4 9 8 7 12 11 16 17 22 23 24 19 20 25)
           (maze/find-path 1 25))))

  (testing "開始点と終了点が同じ場合は単一要素のリスト"
    (is (= '(1) (maze/find-path 1 1))))

  (testing "逆方向に辿った経路は往路の逆順と一致する"
    (is (= (maze/find-path 1 25) (reverse (maze/find-path 25 1))))))


;; [ 4.14.2 ] gps_5：achieve-all2（ゴール順序の入れ替え）により、シブリング目標の打ち消しを回避できる

(deftest gps_5-block-avoids-sibling-goal-clobbering-test
  (let [start '((c on a) (a on table) (b on table) (space on c) (space on b) (space on table))
        ops (block/make-block-ops '(a b c))]

    (testing "単一ゴール"
      (is (= '((start)
               (executing (move c from a to b))
               (executing (move c from b to table)))
             (gps/gps_5 false start '((c on table)) ops))))

    (testing "gps_3 では打ち消しが起きうる順序でも、ゴール順序を入れ替えて解を見つけられる"
      (is (= '((start)
               (executing (move c from a to b))
               (executing (move c from b to table))
               (executing (move a from table to c))
               (executing (move a from c to b)))
             (gps/gps_5 false start '((c on table) (a on b)) ops))))))


;; [ 4.14.3 ] gps_6：achieve-all4（ゴール順序の入れ替え + 前提条件充足数によるオペレータの並べ替え）

(deftest gps_6-block-finds-shorter-solution-test
  (let [start '((c on a) (a on table) (b on table) (space on c) (space on b) (space on table))
        ops (block/make-block-ops '(a b c))]
    (testing "gps_5 より短い手順（2手）を発見する"
      (is (= '((start)
               (executing (move c from a to table))
               (executing (move a from table to b)))
             (gps/gps_6 false start '((c on table) (a on b)) ops))))))


(deftest gps_6-block-goal-order-independent-test
  (let [start '((a on b) (b on c) (c on table) (space on a) (space on table))
        ops (block/make-block-ops '(a b c))
        expected '((start)
                   (executing (move a from b to table))
                   (executing (move b from c to a))
                   (executing (move c from table to b)))]
    (testing "元のゴール順序"
      (is (= expected (gps/gps_6 false start '((b on a) (c on b)) ops))))

    (testing "ゴール順序を入れ替えても同じ解が得られる"
      (is (= expected (gps/gps_6 false start '((c on b) (b on a)) ops))))))


(deftest gps_6-sussman-anomaly-test
  (let [start '((c on a) (a on table) (b on table) (space on c) (space on b) (space on table))
        ops (block/make-block-ops '(a b c))]
    (testing "サスマンのアノマリー：ゴール順序を入れ替えても解けない（既知の限界）"
      (is (= '() (gps/gps_6 false start '((a on b) (b on c)) ops)))
      (is (= '() (gps/gps_6 false start '((b on c) (a on b)) ops))))))


(deftest gps_6-other-unreachable-goal-test
  (testing "存在しないゴール（have-momey という綴りミス）はゴール順序によらず失敗する"
    (is (= '() (gps/gps_6 false '(son-at-home have-money car-works)
                          '(son-at-school have-momey) other/school-ops)))
    (is (= '() (gps/gps_6 false '(son-at-home have-money car-works)
                          '(have-momey son-at-school) other/school-ops)))))


;; my-use / *ops* のダイナミックスコープ（root binding の書き換えと一時上書き）

(deftest my-use-and-dynamic-ops-test
  (let [ops-ab (block/make-block-ops '(a b))]
    (testing "my-useはroot bindingを書き換え、以後opsを省略した呼び出しに引き継がれる"
      (is (= (count ops-ab) (gps/my-use ops-ab)))
      (is (= '((start) (executing (move a from table to b)))
             (gps/gps_3 false
                        '((a on table) (b on table) (space on a) (space on b) (space on table))
                        '((a on b) (b on table))))))

    (testing "gps_3にopsを明示すれば、その回だけ一時的に上書きされ、終了後はroot bindingへ戻る"
      (is (= '() (gps/gps_3 false
                            '((a on table) (b on table) (space on a) (space on b) (space on table))
                            '((a on b) (b on table))
                            '())))
      (is (= ops-ab gps/*ops*)))

    (testing "binding内で例外が起きてもroot bindingは正しく巻き戻る"
      (is (thrown? Exception
            (binding [gps/*ops* '()]
              (throw (ex-info "boom" {})))))
      (is (= ops-ab gps/*ops*)))))

(ns paip.common.gps-sub-test
  (:require
    [clojure.test :refer :all]
    [paip.common.gps-sub :as sub]))


(defn- op
  "テスト用にオペレータのマップを組み立てるヘルパー。
  make-op/make-op2 と同様、convert-opでexecutingマーカーを付与する。"
  [action preconds adds & [dels]]
  (sub/convert-op {:action action :preconds preconds :add-list adds :del-list (or dels '())}))


;; [ 4.11.1 ] executing マーカー関連のヘルパー

(deftest starts-with-test
  (is (true? (sub/starts-with '(executing foo) 'executing)))
  (is (false? (sub/starts-with '(foo bar) 'executing)))
  (is (nil? (sub/starts-with '() 'executing))))


(deftest executing?-test
  (is (true? (sub/executing? '(executing (move a b)))))
  (is (false? (sub/executing? '(son-at-home)))))


(deftest convert-op-test
  (let [raw {:action 'drive :preconds '(a) :add-list '(son-at-school) :del-list '(b)}
        converted (sub/convert-op raw)]
    (testing "add-listの先頭にexecutingマーカーを付与する"
      (is (= (list (list 'executing 'drive) 'son-at-school) (:add-list converted))))

    (testing "既知の挙動: executing?はadd-list全体でなく個々の要素を想定した述語のため、convert-opの二重付与防止チェックは機能せず、変換済みのopに再度適用すると重複して付与される"
      (let [converted-again (sub/convert-op converted)]
        (is (= (list (list 'executing 'drive) (list 'executing 'drive) 'son-at-school)
               (:add-list converted-again)))))))


;; [ 4.11.2 ] 基本要素

(deftest member-equal-test
  (is (= '(1 3 5 7) (sub/member-equal 1 '(0 2 4 6 1 3 5 7))))
  (is (nil? (sub/member-equal 10 '(0 2 4 6 1 3 5 7)))))


(deftest appropriate?-test
  (let [drive (op 'drive-son-to-school '(son-at-home car-works) '(son-at-school) '(son-at-home))]
    (is (some? (sub/appropriate? 'son-at-school drive)))
    (is (nil? (sub/appropriate? 'have-money drive)))))


(deftest apply-op-test
  (let [drive (op 'drive-son-to-school '(son-at-home car-works) '(son-at-school) '(son-at-home))]
    (testing "前提条件を満たしていれば、executingマーカー付きで状態が更新される"
      (let [result (sub/apply-op '(son-at-home car-works) 'son-at-school drive nil (list drive))]
        (is (some? (sub/member-equal 'son-at-school result)))
        (is (some? (sub/member-equal (list 'executing 'drive-son-to-school) result)))
        (is (nil? (sub/member-equal 'son-at-home result)))))

    (testing "前提条件を満たせなければnil"
      (is (nil? (sub/apply-op '(son-at-home) 'son-at-school drive nil (list drive)))))))


(deftest achieve-test
  (let [drive (op 'drive-son-to-school '(son-at-home car-works) '(son-at-school) '(son-at-home))]
    (testing "ゴールがすでに状態に含まれていれば、その位置からの残り状態を返す"
      (is (= '(son-at-home car-works)
             (sub/achieve '(son-at-home car-works) 'son-at-home nil (list drive)))))

    (testing "オペレータの連鎖でゴールを達成できる"
      (is (some? (sub/member-equal 'son-at-school
                   (sub/achieve '(son-at-home car-works) 'son-at-school nil (list drive))))))

    (testing "ゴールスタックに同じゴールが積まれていれば循環とみなしnil"
      (is (nil? (sub/achieve '(son-at-home) 'son-at-school '(son-at-school) (list drive)))))

    (testing "達成する手段がなければnil"
      (is (nil? (sub/achieve '(son-at-home) 'son-at-school nil (list drive)))))))


;; シブリング目標の打ち消し（4.6〜4.7）を示すための最小構成のオペレータ群。
;; make-g1 は前提条件 a を消費してしまうため、(g1 g2) の順序では
;; make-g2 が必要とする a が残っておらず失敗する。

(def clobbering-ops
  (list (op 'make-g1 '(a) '(g1) '(a))
        (op 'make-g2 '(b a) '(g2))))


(deftest achieve-all-test
  (testing "すべてのゴールを達成できれば、達成後の状態を返す"
    (is (some? (sub/achieve-all '(a b) '(g2 g1) nil clobbering-ops))))

  (testing "達成できなければnil"
    (is (nil? (sub/achieve-all '(b) '(g1) nil clobbering-ops))))

  (testing "シブリング目標の打ち消し: g1を先に達成するとaが消え、g2の前提条件を満たせず失敗する"
    (is (nil? (sub/achieve-all '(a b) '(g1 g2) nil clobbering-ops)))))


;; [ 4.13.2 ]

(deftest action?-test
  (is (true? (sub/action? '(start))))
  (is (true? (sub/action? '(executing (move a b)))))
  (is (false? (sub/action? '(son-at-home)))))


;; [ 4.14.2 ] ゴール順序の入れ替えでシブリング目標の打ち消しを回避する

(deftest orderings-test
  (is (= '((a)) (sub/orderings '(a))))
  (is (= '((a b) (b a)) (sub/orderings '(a b)))))


(deftest achieve-all2-and-achieve-all4-avoid-sibling-goal-clobbering-test
  (testing "achieve-allはゴール順序をそのまま使うため、この順序では失敗する"
    (is (nil? (sub/achieve-all '(a b) '(g1 g2) nil clobbering-ops))))

  (testing "achieve-all2はゴール順序の入れ替えを試すため、(g2 g1)の順序を見つけて成功する"
    (is (some? (sub/achieve-all2 '(a b) '(g1 g2) nil clobbering-ops))))

  (testing "achieve-all4も同様にゴール順序の入れ替えを試すため成功する"
    (is (some? (sub/achieve-all4 '(a b) '(g1 g2) nil clobbering-ops)))))


;; 「短い手続きを発見する」（4.14.3）を示すための最小構成のオペレータ群。
;; near は前提条件をすべて満たしているのに対し、far はget-p2/get-p3を
;; 経由すれば達成できてしまうため、遠回りな解が採用されうる。

(def shortcut-ops
  (list (op 'far '(p1 p2 p3) '(z))
        (op 'get-p2 '() '(p2))
        (op 'get-p3 '() '(p3))
        (op 'near '(p1) '(z))))


(deftest count-if-test
  (is (= 3 (sub/count-if odd? '(1 2 3 4 5)))))


(deftest sorted-appropriate-ops-test
  (is (= '(near far)
         (map :action (sub/sorted-appropriate-ops 'z '(p1) shortcut-ops)))))


(deftest achieve2-prefers-fewer-unmet-preconditions-test
  (testing "achieveはopsの並び順どおりに試すため、遠回りなfar経由の解が採用される"
    (let [result (sub/achieve '(p1) 'z nil shortcut-ops)]
      (is (some? (sub/member-equal (list 'executing 'far) result)))
      (is (nil? (sub/member-equal (list 'executing 'near) result)))))

  (testing "achieve2は現在の状態に対する未充足の前提条件数が少ないオペレータを優先するため、nearが採用される"
    (let [result (sub/achieve2 '(p1) 'z nil shortcut-ops)]
      (is (some? (sub/member-equal (list 'executing 'near) result)))
      (is (nil? (sub/member-equal (list 'executing 'far) result))))))


(deftest achieve-all3-uses-achieve2-test
  (testing "achieve-allはachieveと同様に遠回りなfar経由の解を採用する"
    (is (some? (sub/member-equal (list 'executing 'far)
                 (sub/achieve-all '(p1) '(z) nil shortcut-ops)))))

  (testing "achieve-all3は各ゴールにachieve2を使うため、nearが採用される"
    (let [result (sub/achieve-all3 '(p1) '(z) nil shortcut-ops)]
      (is (some? (sub/member-equal (list 'executing 'near) result)))
      (is (nil? (sub/member-equal (list 'executing 'far) result))))))

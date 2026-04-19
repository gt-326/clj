(ns onlisp.chap22.common-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap22.common :as c]
    [onlisp.common.util :as u]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PATHS はグローバルな atom のため、各テスト前後に reset! でリセットする。
;; *cont* は ^:dynamic Var + binding で管理されるためリセット不要。

(defn reset-paths!
  [f]
  (reset! c/PATHS [])
  (f)
  (reset! c/PATHS []))

(use-fixtures :each reset-paths!)


;; =====================================================
;; fail — バックトラックのエントリポイント
;; =====================================================

(deftest fail-empty-test
  (testing "PATHS が空のとき failsym を返す"
    (is (= c/failsym (c/fail)))))


(deftest fail-nonempty-test
  (testing "PATHS が非空のとき先頭の関数を pop して呼ぶ"
    (let [called (atom false)]
      (swap! c/PATHS conj (fn [] (reset! called true) :ok))
      (c/fail)
      (is (true? @called)  "積んだ関数が呼ばれる")
      (is (empty? @c/PATHS) "pop 後 PATHS は空になる")))

  (testing "fail は先頭の関数の戻り値を返す"
    (swap! c/PATHS conj (fn [] :result))
    (is (= :result (c/fail)))))


;; =====================================================
;; cb — choose-bind の実装
;; =====================================================
;;
;; 注意: Common Lisp では (rest '(42)) = nil が falsy のため
;;       単一選択肢のとき PATHS に積まれない。
;;       Clojure では (rest '(42)) = () が truthy のため
;;       単一選択肢でも「失敗継続」が PATHS に積まれる。
;;       ただし失敗継続は呼ばれると fail するだけなので動作に支障はない。

(deftest cb-empty-choices-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (binding [u/*cont* identity]
      (is (= c/failsym (c/cb identity '()))))))


(deftest cb-single-choice-test
  (testing "choices が1つのとき先頭の値で fnc を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 42 (c/cb identity '(42))))))

  (testing "choices が1つのとき Clojure では失敗継続が PATHS に積まれる"
    ;; (rest '(42)) = () は Clojure では truthy のため when が実行される
    (binding [u/*cont* identity]
      (let [before (count @c/PATHS)]
        (c/cb identity '(42))
        (is (= (inc before) (count @c/PATHS))
            "失敗継続が1つ積まれる（呼ばれると fail するだけ）")))))


(deftest cb-multiple-choices-test
  (testing "choices が複数のとき残りの継続を PATHS に積む"
    (binding [u/*cont* identity]
      (let [before (count @c/PATHS)]
        (c/cb identity '(1 2 3))
        (is (= (inc before) (count @c/PATHS))
            "残り (2 3) の継続が1つ積まれる"))))

  (testing "choices が複数のとき先頭の値で fnc を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 1 (c/cb identity '(1 2 3))))))

  (testing "PATHS に積んだ継続を fail で呼ぶと次の選択肢が実行される"
    (binding [u/*cont* identity]
      (c/cb identity '(1 2 3))
      (is (= 2 (c/fail)) "fail で次の選択肢 2 が得られる")))

  (testing "cb は *cont* を積んだ時点の値で保存・復元する"
    ;; cb が fail から呼ばれる際も、積んだ時点の *cont* が使われる
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        (c/cb u/*cont* '(10 20 30)))
      (c/fail)
      (c/fail)
      (is (= [10 20 30] @results)
          "保存された *cont* で各選択肢が処理される"))))


;; =====================================================
;; choose-bind マクロ
;; =====================================================

(deftest choose-bind-test
  (testing "choose-bind で選択肢の先頭が変数に束縛される"
    (binding [u/*cont* identity]
      (is (= 10 (c/choose-bind x '(10 20 30) x)))))

  (testing "choose-bind の body は束縛変数を使える"
    (binding [u/*cont* identity]
      (is (= 20 (c/choose-bind x '(10 20 30) (* x 2))))))

  (testing "choose-bind の残りの選択肢を fail で取得できる"
    (binding [u/*cont* identity]
      (c/choose-bind x '(1 2 3) x)
      (is (= 2 (c/fail)) "fail で次の選択肢 2 が得られる"))))


;; =====================================================
;; mark — 番兵を積む
;; =====================================================

(deftest mark-increments-count-test
  (testing "mark は PATHS の要素数を1増やす"
    (let [before (count @c/PATHS)]
      (c/mark)
      (is (= (inc before) (count @c/PATHS))))))


(deftest mark-pushes-fail-test
  (testing "mark が積む番兵は fail 関数そのもの"
    (c/mark)
    (is (= c/fail (peek @c/PATHS)))))


(deftest mark-multiple-test
  (testing "mark を複数回呼ぶと番兵が複数積まれる"
    (c/mark)
    (c/mark)
    (is (= 2 (count @c/PATHS)))))


;; =====================================================
;; cut — 番兵まで PATHS をクリアする
;; =====================================================

(deftest cut-empty-test
  (testing "PATHS が空のとき cut は何もしない（nil を返す）"
    (is (nil? (c/cut)))
    (is (empty? @c/PATHS))))


(deftest cut-sentinel-only-test
  (testing "番兵だけのとき cut は番兵を pop して PATHS が空になる"
    (c/mark)
    (c/cut)
    (is (empty? @c/PATHS))))


(deftest cut-entries-above-sentinel-test
  (testing "番兵より上に要素があるとき cut はすべて除去する"
    (c/mark)
    (swap! c/PATHS conj (fn [] :a))
    (swap! c/PATHS conj (fn [] :b))
    (c/cut)
    (is (empty? @c/PATHS) "番兵も含めてすべて除去される"))

  (testing "番兵より下の要素は cut で除去されない"
    (swap! c/PATHS conj (fn [] :preserved))
    (c/mark)
    (swap! c/PATHS conj (fn [] :above))
    (c/cut)
    (is (= 1 (count @c/PATHS)) "番兵より下の要素は残る")
    (is (= :preserved ((peek @c/PATHS))) "残った要素は番兵より下のもの")))


;; =====================================================
;; mark / cut の組み合わせ
;; =====================================================

(deftest mark-cut-roundtrip-test
  (testing "mark → cut で PATHS が元の状態に戻る"
    (let [before (vec @c/PATHS)]
      (c/mark)
      (c/cut)
      (is (= before (vec @c/PATHS)))))

  (testing "mark → 複数 push → cut → fail で番兵より下の継続が実行される"
    (let [called (atom nil)]
      ;; 番兵より下の継続（都市継続に相当）
      (swap! c/PATHS conj (fn [] (reset! called :next) :next))
      ;; 番兵
      (c/mark)
      ;; 番兵より上の継続（残りの箱に相当）
      (swap! c/PATHS conj (fn [] :box2))
      (swap! c/PATHS conj (fn [] :box3))
      ;; cut で番兵より上をすべて除去
      (c/cut)
      ;; fail で番兵より下の継続を呼ぶ
      (c/fail)
      (is (= :next @called) "cut 後の fail で下位の継続が呼ばれる"))))


(deftest mark-cut-multiple-cities-test
  (testing "都市ごとに mark/cut を繰り返せる"
    (let [log (atom [])]
      ;; 都市1: mark → 箱選択 → hit → cut → 次の都市へ
      (swap! c/PATHS conj (fn []
                            ;; 都市2: mark → 箱選択
                            (c/mark)
                            (swap! c/PATHS conj (fn [] (swap! log conj :city2-box2) :city2-box2))
                            (swap! log conj :city2-box1)
                            :city2-box1))
      (c/mark)
      (swap! c/PATHS conj (fn [] (swap! log conj :city1-box2) :city1-box2))
      (swap! log conj :city1-box1)

      ;; 都市1でコイン発見 → cut
      (c/cut)

      ;; 都市2へ移行
      (c/fail)

      (is (= [:city1-box1 :city2-box1] @log)
          "都市1の残り選択肢をスキップして都市2に移行できる"))))

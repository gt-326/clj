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


;; =====================================================
;; true-choose-impl — visited による重複スキップ付き選択
;; =====================================================

(deftest true-choose-impl-empty-test
  (testing "choices が空のとき fail → failsym を返す"
    (binding [u/*cont* identity]
      (is (= c/failsym (c/true-choose-impl #{} []))))))


(deftest true-choose-impl-single-test
  (testing "thunk が1つのとき呼ばれて結果を返す"
    (binding [u/*cont* identity]
      (is (= 42 (c/true-choose-impl #{} [(fn [] (u/*cont* 42))]))))))


(deftest true-choose-impl-multiple-test
  (testing "thunk が複数のとき先頭を実行し残りを PATHS に積む"
    (binding [u/*cont* identity]
      (let [before (count @c/PATHS)]
        (c/true-choose-impl #{} [(fn [] (u/*cont* 1))
                                 (fn [] (u/*cont* 2))
                                 (fn [] (u/*cont* 3))])
        (is (= (inc before) (count @c/PATHS))
            "残り2つが1つの継続としてまとめて積まれる"))))

  (testing "先頭の thunk が実行される"
    (binding [u/*cont* identity]
      (is (= 1 (c/true-choose-impl #{} [(fn [] (u/*cont* 1))
                                        (fn [] (u/*cont* 2))])))))

  (testing "fail で次の thunk が実行される"
    (binding [u/*cont* identity]
      (c/true-choose-impl #{} [(fn [] (u/*cont* 1))
                               (fn [] (u/*cont* 2))
                               (fn [] (u/*cont* 3))])
      (is (= 2 (c/fail)) "fail で2番目の選択肢が得られる")))

  (testing "*cont* を積んだ時点の値で保存・復元する"
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        (c/true-choose-impl #{} [(fn [] (u/*cont* 10))
                                 (fn [] (u/*cont* 20))
                                 (fn [] (u/*cont* 30))]))
      (c/fail)
      (c/fail)
      (is (= [10 20 30] @results)
          "保存された *cont* で各 thunk が処理される"))))


(deftest true-choose-impl-dedup-test
  (testing "同一 thunk オブジェクトは visited により2回目以降スキップされる"
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (c/fail))]
      (binding [u/*cont* identity]
        (c/true-choose-impl #{} [t t t]))
      (is (= [:t] @log)
          "t は visited に追加され1回しか呼ばれない"))))


;; =====================================================
;; true-choose — 関数版エントリポイント
;; =====================================================

(deftest true-choose-fn-test
  (testing "thunk リストを受け取り先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 10 (c/true-choose [(fn [] (u/*cont* 10))
                                (fn [] (u/*cont* 20))])))))

  (testing "fail で次の選択肢が得られる"
    (binding [u/*cont* identity]
      (c/true-choose [(fn [] (u/*cont* 10))
                      (fn [] (u/*cont* 20))])
      (is (= 20 (c/fail)))))

  (testing "全選択肢を使い切ると failsym を返す"
    (binding [u/*cont* identity]
      (c/true-choose [(fn [] (u/*cont* 1))])
      (c/fail)   ; 残りの空継続を消費
      (is (= c/failsym (c/fail))))))


;; =====================================================
;; true-choose_ — マクロ版エントリポイント
;; =====================================================

(deftest true-choose-macro-first-choice-test
  (testing "静的な選択肢を thunk に包んで先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 10 (c/true-choose_ (u/*cont* 10)
                                (u/*cont* 20)))))))


(deftest true-choose-macro-fail-test
  (testing "fail で次の静的選択肢が得られる"
    (binding [u/*cont* identity]
      (c/true-choose_ (u/*cont* 10)
                      (u/*cont* 20))
      (is (= 20 (c/fail))))))


(deftest true-choose-macro-empty-test
  (testing "引数なしのとき failsym を返す"
    ;; 各 deftest の前後で PATHS が fixture によりリセットされるため
    ;; 他テストの残留継続による干渉を受けない
    (binding [u/*cont* identity]
      (is (= c/failsym (c/true-choose_))))))


;; =====================================================
;; true-choose-simple — Scheme true-choose の CPS トレース（visited なし）
;; =====================================================
;;
;; true-choose-impl から visited dedup を取り除いたシンプル版。
;; Scheme では call/cc で各 choice を継続に包むが、
;; Clojure では呼び出し元が thunk を渡し、*cont* の保存・復元で代替する。

(deftest true-choose-simple-empty-test
  (testing "choices が空のとき fail → failsym を返す"
    (binding [u/*cont* identity]
      (is (= c/failsym (c/true-choose-simple []))))))


(deftest true-choose-simple-single-test
  (testing "thunk が1つのとき呼ばれて結果を返す"
    (binding [u/*cont* identity]
      (is (= 42 (c/true-choose-simple [(fn [] (u/*cont* 42))]))))))


(deftest true-choose-simple-multiple-test
  (testing "thunk が複数のとき先頭を実行し残りを PATHS に1エントリとして積む"
    (binding [u/*cont* identity]
      (let [before (count @c/PATHS)]
        (c/true-choose-simple [(fn [] (u/*cont* 1))
                               (fn [] (u/*cont* 2))
                               (fn [] (u/*cont* 3))])
        (is (= (inc before) (count @c/PATHS))
            "残り2つが1つの継続としてまとめて積まれる"))))

  (testing "先頭の thunk が実行される"
    (binding [u/*cont* identity]
      (is (= 1 (c/true-choose-simple [(fn [] (u/*cont* 1))
                                      (fn [] (u/*cont* 2))])))))

  (testing "fail で次の thunk が実行される"
    (binding [u/*cont* identity]
      (c/true-choose-simple [(fn [] (u/*cont* 1))
                             (fn [] (u/*cont* 2))
                             (fn [] (u/*cont* 3))])
      (is (= 2 (c/fail)) "fail で2番目の選択肢が得られる")))

  (testing "*cont* を積んだ時点の値で保存・復元する"
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        (c/true-choose-simple [(fn [] (u/*cont* 10))
                               (fn [] (u/*cont* 20))
                               (fn [] (u/*cont* 30))]))
      (c/fail)
      (c/fail)
      (is (= [10 20 30] @results)
          "保存された *cont* で各 thunk が処理される"))))


(deftest true-choose-simple-no-dedup-test
  (testing "同一 thunk オブジェクトでも dedup せずすべて実行される（true-choose-impl との差異）"
    ;; true-choose-impl は visited で同一 thunk をスキップするが、
    ;; true-choose-simple は visited を持たないため3回すべて実行される。
    ;; 各 t が内部で (c/fail) を呼ぶため、呼び出し元での fail は不要。
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (c/fail))]
      (binding [u/*cont* identity]
        (c/true-choose-simple [t t t]))
      (is (= [:t :t :t] @log)
          "visited がないため同一 thunk も3回すべて実行される"))))


;; =====================================================
;; true-choose2 — マクロ版エントリポイント（true-choose-simple 委譲）
;; =====================================================

(deftest true-choose2-first-choice-test
  (testing "静的な選択肢を thunk に包んで先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 10 (c/true-choose2 (u/*cont* 10)
                                (u/*cont* 20)))))))


(deftest true-choose2-fail-test
  (testing "fail で次の静的選択肢が得られる"
    (binding [u/*cont* identity]
      (c/true-choose2 (u/*cont* 10)
                      (u/*cont* 20))
      (is (= 20 (c/fail))))))


(deftest true-choose2-empty-test
  (testing "引数なしのとき failsym を返す"
    (binding [u/*cont* identity]
      (is (= c/failsym (c/true-choose2))))))


;; =====================================================
;; choose — 直接フォームを選択するマクロ
;; =====================================================
;;
;; cb / choose-bind との主な違い:
;;   - 各 choice は値ではなく「直接評価される式（リスト形式）」を取る
;;   - *cont* を保存しない（fail から呼ばれる時点の *cont* を使う）
;;   - 単一選択肢のとき PATHS には何も積まれない（cb では積まれる）

(deftest choose-empty-test
  (testing "choices が空のとき fail → failsym を返す"
    (is (= c/failsym (c/choose)))))


(deftest choose-single-test
  (testing "choices が1つのとき先頭の式を直接実行する"
    (is (= 42 (c/choose (identity 42)))))

  (testing "choices が1つのとき PATHS には何も積まれない"
    ;; choose: (rest '(form)) = () → (seq ()) = nil → push なし
    ;; cb:     (rest '(x))   = () が truthy          → push あり（挙動の差異）
    (let [before (count @c/PATHS)]
      (c/choose (identity 42))
      (is (= before (count @c/PATHS))
          "単一選択肢では PATHS は変化しない"))))


(deftest choose-multiple-test
  (testing "choices が複数のとき先頭を実行する"
    (is (= 1 (c/choose (identity 1) (identity 2) (identity 3)))))

  (testing "choices が複数のとき残りが PATHS に別エントリとして積まれる"
    (let [before (count @c/PATHS)]
      (c/choose (identity 1) (identity 2) (identity 3))
      (is (= (+ before 2) (count @c/PATHS))
          "残り2つがそれぞれ独立したエントリとして積まれる")))

  (testing "fail で次の選択肢が得られる"
    (c/choose (identity 1) (identity 2) (identity 3))
    (is (= 2 (c/fail)) "fail で次の選択肢 2 が得られる"))

  (testing "fail を繰り返すと全選択肢を順に取得できる"
    (c/choose (identity 10) (identity 20) (identity 30))
    (is (= 20 (c/fail)))
    (is (= 30 (c/fail)))))


(deftest choose-no-cont-save-test
  (testing "choose は *cont* を保存しない（cb/choose-bind との違い）"
    ;; cb は積んだ時点の *cont* を binding で保存・復元するが、
    ;; choose は #(~@c) を積むだけで *cont* を保存しない。
    ;; fail から呼ばれたとき、動的変数 *cont* はそのときの値（通常 identity）を使う。
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        ;; binding 内: 先頭の式 (u/*cont* 10) のみここで評価される
        (c/choose (u/*cont* 10) (u/*cont* 20) (u/*cont* 30)))
      ;; binding を抜けた後: *cont* は identity に戻っている
      ;; fail → #(u/*cont* 20) が呼ばれるが *cont* = identity のため results に追加されない
      (c/fail)
      (c/fail)
      (is (= [10] @results)
          "保存されないため binding 内で実行された先頭分のみ記録される")))

  (testing "cb は *cont* を保存するため binding 外の fail でも全選択肢が記録される（対比）"
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        (c/cb u/*cont* '(10 20 30)))
      (c/fail)
      (c/fail)
      (is (= [10 20 30] @results)
          "cb は保存された *cont* で各選択肢を処理する"))))

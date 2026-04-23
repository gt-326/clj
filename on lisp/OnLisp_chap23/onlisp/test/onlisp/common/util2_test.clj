(ns onlisp.common.util2_test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PATHS・VISITED はグローバルな atom のため、各テスト前後に reset! でリセットする。
;; *cont* は ^:dynamic Var + binding で管理されるためリセット不要。

(defn reset-paths!
  [f]
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{})
  (f)
  (reset! u2/PATHS [])
  (reset! u2/VISITED #{}))


(use-fixtures :each reset-paths!)


;; =====================================================
;; fail — バックトラックのエントリポイント
;; =====================================================

(deftest fail-empty-test
  (testing "PATHS が空のとき failsym を返す"
    (is (= u2/failsym (u2/fail)))))


(deftest fail-nonempty-test
  (testing "PATHS が非空のとき先頭の関数を pop して呼ぶ"
    (let [called (atom false)]
      (swap! u2/PATHS conj (fn [] (reset! called true) :ok))
      (u2/fail)
      (is (true? @called)  "積んだ関数が呼ばれる")
      (is (empty? @u2/PATHS) "pop 後 PATHS は空になる")))

  (testing "fail は先頭の関数の戻り値を返す"
    (swap! u2/PATHS conj (fn [] :result))
    (is (= :result (u2/fail)))))


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
      (is (= u2/failsym (u2/cb identity '()))))))


(deftest cb-single-choice-test
  (testing "choices が1つのとき先頭の値で fnc を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 42 (u2/cb identity '(42))))))

  (testing "choices が1つのとき Clojure では失敗継続が PATHS に積まれる"
    ;; (rest '(42)) = () は Clojure では truthy のため when が実行される
    (binding [u/*cont* identity]
      (let [before (count @u2/PATHS)]
        (u2/cb identity '(42))
        (is (= (inc before) (count @u2/PATHS))
            "失敗継続が1つ積まれる（呼ばれると fail するだけ）")))))


(deftest cb-multiple-choices-test
  (testing "choices が複数のとき残りの継続を PATHS に積む"
    (binding [u/*cont* identity]
      (let [before (count @u2/PATHS)]
        (u2/cb identity '(1 2 3))
        (is (= (inc before) (count @u2/PATHS))
            "残り (2 3) の継続が1つ積まれる"))))

  (testing "choices が複数のとき先頭の値で fnc を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 1 (u2/cb identity '(1 2 3))))))

  (testing "PATHS に積んだ継続を fail で呼ぶと次の選択肢が実行される"
    (binding [u/*cont* identity]
      (u2/cb identity '(1 2 3))
      (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる")))

  (testing "cb は *cont* を積んだ時点の値で保存・復元する"
    ;; cb が fail から呼ばれる際も、積んだ時点の *cont* が使われる
    (let [results (atom [])]
      (binding [u/*cont* (fn [x] (swap! results conj x) x)]
        (u2/cb u/*cont* '(10 20 30)))
      (u2/fail)
      (u2/fail)
      (is (= [10 20 30] @results)
          "保存された *cont* で各選択肢が処理される"))))


;; =====================================================
;; choose-bind マクロ
;; =====================================================

(deftest choose-bind-test
  (testing "choose-bind で選択肢の先頭が変数に束縛される"
    (binding [u/*cont* identity]
      (is (= 10 (u2/choose-bind x '(10 20 30) x)))))

  (testing "choose-bind の body は束縛変数を使える"
    (binding [u/*cont* identity]
      (is (= 20 (u2/choose-bind x '(10 20 30) (* x 2))))))

  (testing "choose-bind の残りの選択肢を fail で取得できる"
    (binding [u/*cont* identity]
      (u2/choose-bind x '(1 2 3) x)
      (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる"))))


;; =====================================================
;; choose マクロ — 先頭を直接実行し残りを PATHS に積む
;; =====================================================
;;
;; choose は cb/choose-bind と異なり、choices を値のリストではなく
;; 評価対象の「フォーム」として受け取る。
;;   先頭フォームを直接展開し、残りは #(~@c) で thunk 化して PATHS に push する。
;;
;; defnode で複数アークを持つノードを定義するときに使われる:
;;   (u2/choose (o/category noun ...) (o/up ...))
;;
;; 注意: 各 choice は関数呼び出し形式（リスト）でなければならない。
;;       リテラルや変数単体は #(~@c) でうまく thunk 化できない。

(deftest choose-no-choices-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (is (= u2/failsym (u2/choose)))))


(deftest choose-single-choice-test
  (testing "choices が1つのとき先頭の選択肢を直ちに評価する"
    (is (= 42 (u2/choose (identity 42)))))

  (testing "choices が1つのとき PATHS への push はない"
    (let [before (count @u2/PATHS)]
      (u2/choose (identity 42))
      (is (= before (count @u2/PATHS))))))


(deftest choose-multiple-choices-test
  (testing "choices が複数のとき先頭の選択肢を直ちに評価する"
    (is (= 1 (u2/choose (identity 1) (identity 2) (identity 3)))))

  (testing "choices が複数のとき残りが PATHS に積まれる"
    (let [before (count @u2/PATHS)]
      (u2/choose (identity 1) (identity 2) (identity 3))
      (is (= (+ before 2) (count @u2/PATHS)) "2件積まれる")))

  (testing "fail で残りの選択肢が順に得られる"
    (u2/choose (identity 1) (identity 2) (identity 3))
    (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる")
    (is (= 3 (u2/fail)) "もう一度 fail で選択肢 3 が得られる")))


;; =====================================================
;; mark — 番兵を積む
;; =====================================================

(deftest mark-increments-count-test
  (testing "mark は PATHS の要素数を1増やす"
    (let [before (count @u2/PATHS)]
      (u2/mark)
      (is (= (inc before) (count @u2/PATHS))))))


(deftest mark-pushes-fail-test
  (testing "mark が積む番兵は fail 関数そのもの"
    (u2/mark)
    (is (= u2/fail (peek @u2/PATHS)))))


(deftest mark-multiple-test
  (testing "mark を複数回呼ぶと番兵が複数積まれる"
    (u2/mark)
    (u2/mark)
    (is (= 2 (count @u2/PATHS)))))


;; =====================================================
;; cut — 番兵まで PATHS をクリアする
;; =====================================================

(deftest cut-empty-test
  (testing "PATHS が空のとき cut は何もしない（nil を返す）"
    (is (nil? (u2/cut)))
    (is (empty? @u2/PATHS))))


(deftest cut-sentinel-only-test
  (testing "番兵だけのとき cut は番兵を pop して PATHS が空になる"
    (u2/mark)
    (u2/cut)
    (is (empty? @u2/PATHS))))


(deftest cut-entries-above-sentinel-test
  (testing "番兵より上に要素があるとき cut はすべて除去する"
    (u2/mark)
    (swap! u2/PATHS conj (fn [] :a))
    (swap! u2/PATHS conj (fn [] :b))
    (u2/cut)
    (is (empty? @u2/PATHS) "番兵も含めてすべて除去される"))

  (testing "番兵より下の要素は cut で除去されない"
    (swap! u2/PATHS conj (fn [] :preserved))
    (u2/mark)
    (swap! u2/PATHS conj (fn [] :above))
    (u2/cut)
    (is (= 1 (count @u2/PATHS)) "番兵より下の要素は残る")
    (is (= :preserved ((peek @u2/PATHS))) "残った要素は番兵より下のもの")))


;; =====================================================
;; mark / cut の組み合わせ
;; =====================================================

(deftest mark-cut-roundtrip-test
  (testing "mark → cut で PATHS が元の状態に戻る"
    (let [before (vec @u2/PATHS)]
      (u2/mark)
      (u2/cut)
      (is (= before (vec @u2/PATHS)))))

  (testing "mark → 複数 push → cut → fail で番兵より下の継続が実行される"
    (let [called (atom nil)]
      ;; 番兵より下の継続（都市継続に相当）
      (swap! u2/PATHS conj (fn [] (reset! called :next) :next))
      ;; 番兵
      (u2/mark)
      ;; 番兵より上の継続（残りの箱に相当）
      (swap! u2/PATHS conj (fn [] :box2))
      (swap! u2/PATHS conj (fn [] :box3))
      ;; cut で番兵より上をすべて除去
      (u2/cut)
      ;; fail で番兵より下の継続を呼ぶ
      (u2/fail)
      (is (= :next @called) "cut 後の fail で下位の継続が呼ばれる"))))


;; =====================================================
;; true-choose — visited による循環回避
;; =====================================================

(deftest true-choose-skips-duplicate-thunk-test
  (testing "同一 thunk オブジェクトが重複するリストで、2回目以降はスキップされる"
    ;; [t t t] の場合：
    ;;   1. t を実行 → visited=#{t} でバックトラック継続を積む
    ;;   2. t が fail → 継続を pop → (true-choose-impl #{t} [t t])
    ;;   3. t ∈ #{t} → スキップ → (true-choose-impl #{t} [t])
    ;;   4. t ∈ #{t} → スキップ → (true-choose-impl #{t} [])
    ;;   5. 空 → fail
    ;; 結果: t は 1 回しか呼ばれない
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (u2/fail))]
      (binding [u/*cont* identity]
        (u2/true-choose [t t t]))
      (is (= [:t] @log)
          "同一 thunk は visited により 2 回目以降スキップされる"))))


(deftest true-choose-cross-node-cycle-not-prevented-test
  (testing "true-choose 単独では cross-node サイクルを防げない"
    ;; defnode は呼び出しごとに (fn [] arc) という新しい thunk を生成する。
    ;; true-choose の local visited は thunk identity で比較するため、
    ;; 毎回新規の thunk オブジェクトを検出できない。
    (let [depth (atom 0)
          limit 6
          nodeA (fn nodeA []
                  (when (< @depth limit)
                    (swap! depth inc)
                    (u2/true-choose [(fn [] (nodeA))])))]
      (binding [u/*cont* identity]
        (nodeA))
      (is (= limit @depth)
          "true-choose だけではサイクルが防がれず limit まで到達する"))))


(deftest node-visited-prevents-cross-node-cycle-test
  (testing "node-visited? + mark-visited! で [node pos] を追跡すると cross-node サイクルが防げる"
    ;; defnode の展開形を手動で再現:
    ;;   (if (node-visited? 'nodeA pos) (fail)
    ;;     (do (mark-visited! 'nodeA pos) (true-choose [thunk])))
    (let [depth (atom 0)
          limit 6
          nodeA (fn nodeA [pos]
                  (if (u2/node-visited? ['nodeA pos])
                    (u2/fail)
                    (do
                      (u2/mark-visited! ['nodeA pos])
                      (swap! depth inc)
                      (when (< @depth limit)
                        (u2/true-choose [(fn [] (nodeA pos))])))))]
      (binding [u/*cont* identity]
        (nodeA 0))
      (is (= 1 @depth)
          "[nodeA 0] が VISITED に登録され、再入が 1 回でブロックされる"))))


;; =====================================================
;; true-choose-simple — 訪問管理なしのシンプルな非決定的選択
;; =====================================================
;;
;; true-choose-simple は thunk のリストを受け取り、先頭を実行して残りを PATHS に積む。
;; true-choose（visited による thunk identity dedup あり）との違い:
;;   1. visited による重複検出をしない → 同一 thunk も毎回実行される
;;   2. 単一選択肢のとき PATHS への push をしない
;;      （more が nil → (seq nil) = nil → when がスキップされる）
;;
;; defnode-slow2/3/4 のアーク実行に使われる。

(deftest true-choose-simple-empty-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (binding [u/*cont* identity]
      (is (= u2/failsym (u2/true-choose-simple '()))))))


(deftest true-choose-simple-single-test
  (testing "choices が1つのとき先頭の thunk を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 42 (u2/true-choose-simple [(fn [] 42)])))))

  (testing "choices が1つのとき PATHS への push は行われない"
    ;; (when (seq more) ...) において more = nil → (seq nil) = nil → push なし
    ;; cb の (when (rest choices) ...) とは異なる（rest '(42) = () は truthy）
    (binding [u/*cont* identity]
      (let [before (count @u2/PATHS)]
        (u2/true-choose-simple [(fn [] 42)])
        (is (= before (count @u2/PATHS)))))))


(deftest true-choose-simple-multiple-test
  (testing "choices が複数のとき先頭の thunk を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 1 (u2/true-choose-simple [(fn [] 1) (fn [] 2) (fn [] 3)])))))

  (testing "choices が複数のとき残りの継続が1つ PATHS に積まれる"
    (binding [u/*cont* identity]
      (let [before (count @u2/PATHS)]
        (u2/true-choose-simple [(fn [] 1) (fn [] 2) (fn [] 3)])
        (is (= (inc before) (count @u2/PATHS)) "残り (2 3) の継続が1つ積まれる"))))

  (testing "fail で残りの選択肢が順に実行される"
    (binding [u/*cont* identity]
      (u2/true-choose-simple [(fn [] 1) (fn [] 2) (fn [] 3)])
      (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる")
      (is (= 3 (u2/fail)) "もう一度 fail で選択肢 3 が得られる"))))


(deftest true-choose-simple-no-visited-dedup-test
  (testing "true-choose-simple は thunk identity による dedup をしない（true-choose と対比）"
    ;; true-choose   [t t t] → t は 1 回しか呼ばれない（同一 thunk は visited でスキップ）
    ;; true-choose-simple [t t t] → t は 3 回すべて呼ばれる（dedup なし）
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (u2/fail))]
      (binding [u/*cont* identity]
        (u2/true-choose-simple [t t t]))
      (is (= [:t :t :t] @log)
          "visited による dedup がないため同一 thunk も毎回実行される"))))


;; =====================================================
;; true-choose2 マクロ — true-choose-simple のマクロ版ラッパー
;; =====================================================
;;
;; `(true-choose2 arc1 arc2)`
;; → `(true-choose-simple (list (fn [] arc1) (fn [] arc2)))`

(deftest true-choose2-basic-test
  (testing "choices を thunk 化して true-choose-simple に渡し先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 1 (u2/true-choose2 (identity 1) (identity 2) (identity 3))))))

  (testing "fail で次の選択肢が得られる"
    (binding [u/*cont* identity]
      (u2/true-choose2 (identity 1) (identity 2) (identity 3))
      (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる"))))


;; =====================================================
;; true-choose_ マクロ — true-choose（visited dedup あり）のマクロ版ラッパー
;; =====================================================
;;
;; `(true-choose_ arc1 arc2)`
;; → `(true-choose-impl #{} (list (fn [] arc1) (fn [] arc2)))`

(deftest true-choose_-basic-test
  (testing "choices を thunk 化して true-choose-impl に渡し先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 1 (u2/true-choose_ (identity 1) (identity 2) (identity 3))))))

  (testing "fail で次の選択肢が得られる"
    (binding [u/*cont* identity]
      (u2/true-choose_ (identity 1) (identity 2) (identity 3))
      (is (= 2 (u2/fail)) "fail で次の選択肢 2 が得られる"))))


;; =====================================================
;; mark / cut の組み合わせ（複数都市シナリオ）
;; =====================================================

(deftest mark-cut-multiple-cities-test
  (testing "都市ごとに mark/cut を繰り返せる"
    (let [log (atom [])]
      ;; 都市1: mark → 箱選択 → hit → cut → 次の都市へ
      (swap! u2/PATHS conj (fn []
                             ;; 都市2: mark → 箱選択
                             (u2/mark)
                             (swap! u2/PATHS conj (fn [] (swap! log conj :city2-box2) :city2-box2))
                             (swap! log conj :city2-box1)
                             :city2-box1))
      (u2/mark)
      (swap! u2/PATHS conj (fn [] (swap! log conj :city1-box2) :city1-box2))
      (swap! log conj :city1-box1)

      ;; 都市1でコイン発見 → cut
      (u2/cut)

      ;; 都市2へ移行
      (u2/fail)

      (is (= [:city1-box1 :city2-box1] @log)
          "都市1の残り選択肢をスキップして都市2に移行できる"))))

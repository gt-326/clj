(ns onlisp.common.util3-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.common.util :as u]
    [onlisp.common.util3 :as u3]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; util3 にはグローバルな PATHS atom がなく、*k-fail* は ^:dynamic Var のため
;; binding スコープを抜けると自動的に元の値に戻る。
;;
;; リセットが必要なのは:
;;   VISITED   — cross-node サイクル防止用の global atom
;;   CUT-POINT — mark/cut で使う atom

(defn reset-state!
  [f]
  (reset! u3/VISITED #{})
  (reset! u3/CUT-POINT (fn [] u3/failsym))
  (f)
  (reset! u3/VISITED #{})
  (reset! u3/CUT-POINT (fn [] u3/failsym)))


(use-fixtures :each reset-state!)


;; =====================================================
;; fail — 失敗継続の呼び出し
;; =====================================================
;;
;; util2: PATHS から pop して呼ぶ（pop により thunk が消費される）
;; util3: *k-fail* を直接呼ぶ（binding スコープにより持続している）

(deftest fail-initial-test
  (testing "初期状態（*k-fail* = (fn [] failsym)）のとき failsym を返す"
    (is (= u3/failsym (u3/fail)))))


(deftest fail-with-custom-k-fail-test
  (testing "binding で *k-fail* をカスタム関数に差し替えると、fail はそれを呼ぶ"
    (let [called (atom false)]
      (binding [u3/*k-fail* (fn [] (reset! called true) :custom-result)]
        (is (= :custom-result (u3/fail)))
        (is (true? @called))))))


;; =====================================================
;; cb — choose-bind の実装
;; =====================================================
;;
;; util2.cb との重要な差異:
;;   util2: 単一選択肢でも (rest '(x)) = () が truthy のため PATHS に push する。
;;   util3: 単一選択肢のとき (seq (rest [x])) = nil → *k-fail* は変化しない。
;;
;;   util2: fail を外部から呼べば次の選択肢を取得できる（PATHS が永続するため）。
;;   util3: fail を外部から呼ぶと外側の *k-fail* が呼ばれる。
;;          → 複数選択肢の列挙は =bind を使った CPS スタイルで行う。

(deftest cb-empty-choices-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (binding [u/*cont* identity]
      (is (= u3/failsym (u3/cb identity '()))))))


(deftest cb-single-choice-test
  (testing "choices が1つのとき先頭の値で fnc を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 42 (u3/cb identity '(42))))))

  (testing "choices が1つのとき *k-fail* に変化はない（util2 と異なり積まれない）"
    ;; util2: PATHS に「再 fail する継続」が積まれる
    ;; util3: saved-k-fail（= 外側の *k-fail*）がそのまま使われる
    (let [outer-called (atom false)]
      (binding [u/*cont* identity
                u3/*k-fail* (fn [] (reset! outer-called true) u3/failsym)]
        (u3/cb identity '(42))
        (u3/fail))
      (is (true? @outer-called)
          "単一選択肢後の fail は外側の *k-fail* に直接委譲される"))))


(deftest cb-multiple-choices-cps-test
  (testing "=bind 内で cb を使うと全選択肢が順に処理される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/cb u/*cont* '(1 2 3))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2 3] @results))))

  (testing "cb は *cont* を積んだ時点の値で保存・復元する"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/cb u/*cont* '(10 20 30))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [10 20 30] @results)
          "保存された *cont* で各選択肢が処理される"))))


;; =====================================================
;; choose-bind マクロ
;; =====================================================

(deftest choose-bind-test
  (testing "choose-bind で選択肢の先頭が変数に束縛される"
    (binding [u/*cont* identity]
      (is (= 10 (u3/choose-bind x '(10 20 30) x)))))

  (testing "choose-bind の body は束縛変数を使える"
    (binding [u/*cont* identity]
      (is (= 20 (u3/choose-bind x '(10 20 30) (* x 2))))))

  (testing "=bind 内で choose-bind を使うと全選択肢が処理される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/choose-bind y '(1 2 3) (u/=values (* y 10)))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [10 20 30] @results)))))


;; =====================================================
;; choose マクロ — true-choose-simple への委譲
;; =====================================================
;;
;; util2.choose は PATHS に直接 push するが、
;; util3.choose は true-choose-simple に委譲する。
;; 複数選択肢の列挙は =bind を使った CPS スタイルで行う。

(deftest choose-no-choices-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (is (= u3/failsym (u3/choose)))))


(deftest choose-single-choice-test
  (testing "choices が1つのとき先頭の選択肢を直ちに評価する"
    (is (= 42 (u3/choose (identity 42))))))


(deftest choose-multiple-choices-test
  (testing "choices が複数のとき先頭の選択肢を直ちに評価する"
    (is (= 1 (u3/choose (identity 1) (identity 2) (identity 3)))))

  (testing "=bind 内で fail を呼ぶと全選択肢が順に処理される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/choose (u/=values 1) (u/=values 2) (u/=values 3))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2 3] @results)))))


;; =====================================================
;; true-choose-simple — 訪問管理なしのシンプルな非決定的選択
;; =====================================================
;;
;; PATHS（util2）と *k-fail*（util3）の本質的差異:
;;   util2: thunk を PATHS に push → fail で pop（消費）→ 再呼び出しなし
;;   util3: *k-fail* に binding → スコープが続く限り有効
;;          saved-k-fail により使用済み選択肢の再呼び出しを防ぐ

(deftest true-choose-simple-empty-test
  (testing "choices が空のとき fail を呼ぶ → failsym を返す"
    (is (= u3/failsym (u3/true-choose-simple '())))))


(deftest true-choose-simple-single-test
  (testing "choices が1つのとき先頭の thunk を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 42 (u3/true-choose-simple [(fn [] 42)])))))

  (testing "choices が1つのとき *k-fail* に変化はなく外側の *k-fail* に委譲される"
    ;; more = nil → saved-k-fail がそのまま使われる（util2 と同様に push なし）
    (let [outer-called (atom false)]
      (binding [u3/*k-fail* (fn [] (reset! outer-called true) u3/failsym)]
        (u3/true-choose-simple [(fn [] (u3/fail))]))
      (is (true? @outer-called)
          "単一選択肢が fail を呼ぶと外側の *k-fail* に委譲される"))))


(deftest true-choose-simple-multiple-test
  (testing "choices が複数のとき先頭の thunk を呼ぶ"
    (binding [u/*cont* identity]
      (is (= 1 (u3/true-choose-simple [(fn [] 1) (fn [] 2) (fn [] 3)])))))

  (testing "=bind 内で fail を呼ぶと全選択肢が順に実行される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/true-choose-simple [(fn [] (u/=values 1))
                                       (fn [] (u/=values 2))
                                       (fn [] (u/=values 3))])
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2 3] @results)))))


(deftest true-choose-simple-no-cycle-test
  (testing "=bind 継続内から fail を呼んでも選択肢が循環しない（saved-k-fail の効果）"
    ;; 修正前は: arc2 → *cont*(k) → fail → *k-fail* = K2（arc2 が再び呼ばれる → 無限ループ）
    ;; 修正後は: arc2 → *cont*(k) → fail → saved-k-fail（外側の *k-fail* に正しく進む）
    (let [arc2-call-count (atom 0)
          results         (atom [])]
      (u/=bind [x]
               (u3/true-choose-simple [(fn [] (u/=values 1))
                                       (fn []
                                         (swap! arc2-call-count inc)
                                         (u/=values 2))])
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2] @results))
      (is (= 1 @arc2-call-count)
          "arc2 は 1 回のみ呼ばれる（循環しない）"))))


(deftest true-choose-simple-no-visited-dedup-test
  (testing "true-choose-simple は thunk identity による dedup をしない（true-choose と対比）"
    ;; true-choose        [t t t] → t は 1 回のみ（visited により重複スキップ）
    ;; true-choose-simple [t t t] → t は 3 回すべて（dedup なし）
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (u3/fail))]
      (binding [u/*cont* identity]
        (u3/true-choose-simple [t t t]))
      (is (= [:t :t :t] @log)
          "visited による dedup がないため同一 thunk も毎回実行される"))))


;; =====================================================
;; true-choose2 マクロ — true-choose-simple のマクロ版ラッパー
;; =====================================================

(deftest true-choose2-basic-test
  (testing "choices を thunk 化して true-choose-simple に渡し先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 1 (u3/true-choose2 (identity 1) (identity 2) (identity 3))))))

  (testing "=bind 内で fail を呼ぶと全選択肢が処理される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/true-choose2 (u/=values 1) (u/=values 2) (u/=values 3))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2 3] @results)))))


;; =====================================================
;; true-choose — visited による循環回避
;; =====================================================

(deftest true-choose-skips-duplicate-thunk-test
  (testing "同一 thunk オブジェクトが重複するリストで、2回目以降はスキップされる"
    ;; [t t t] の場合:
    ;;   1. t を実行 → visited=#{t}
    ;;   2. fail → (true-choose-impl #{t} [t t]) → t ∈ visited → スキップ
    ;;   3. → (true-choose-impl #{t} [t])   → t ∈ visited → スキップ
    ;;   4. → (true-choose-impl #{t} [])    → fail
    ;; 結果: t は 1 回しか呼ばれない
    (let [log (atom [])
          t   (fn [] (swap! log conj :t) (u3/fail))]
      (binding [u/*cont* identity]
        (u3/true-choose [t t t]))
      (is (= [:t] @log)
          "同一 thunk は visited により 2 回目以降スキップされる"))))


(deftest true-choose-cross-node-cycle-not-prevented-test
  (testing "true-choose 単独では cross-node サイクルを防げない"
    ;; defnode は呼び出しごとに新しい thunk を生成するため、
    ;; true-choose の local visited はサイクルを検出できない。
    (let [depth (atom 0)
          limit 6
          nodeA (fn nodeA []
                  (when (< @depth limit)
                    (swap! depth inc)
                    (u3/true-choose [(fn [] (nodeA))])))]
      (binding [u/*cont* identity]
        (nodeA))
      (is (= limit @depth)
          "true-choose だけではサイクルが防がれず limit まで到達する"))))


(deftest node-visited-prevents-cross-node-cycle-test
  (testing "node-visited? + mark-visited! で [node pos] を追跡すると cross-node サイクルが防げる"
    ;; defnode-slow の展開形を手動で再現:
    ;;   (if (node-visited? 'nodeA pos) (fail)
    ;;     (do (mark-visited! 'nodeA pos) (true-choose [thunk])))
    (let [depth (atom 0)
          limit 6
          nodeA (fn nodeA [pos]
                  (if (u3/node-visited? ['nodeA pos])
                    (u3/fail)
                    (do
                      (u3/mark-visited! ['nodeA pos])
                      (swap! depth inc)
                      (when (< @depth limit)
                        (u3/true-choose [(fn [] (nodeA pos))])))))]
      (binding [u/*cont* identity]
        (nodeA 0))
      (is (= 1 @depth)
          "[nodeA 0] が VISITED に登録され、再入が 1 回でブロックされる"))))


;; =====================================================
;; true-choose_ マクロ — true-choose のマクロ版ラッパー
;; =====================================================

(deftest true-choose_-basic-test
  (testing "choices を thunk 化して true-choose-impl に渡し先頭を実行する"
    (binding [u/*cont* identity]
      (is (= 1 (u3/true-choose_ (identity 1) (identity 2) (identity 3))))))

  (testing "=bind 内で fail を呼ぶと全選択肢が処理される"
    (let [results (atom [])]
      (u/=bind [x]
               (u3/true-choose_ (u/=values 1) (u/=values 2) (u/=values 3))
               (do (swap! results conj x)
                   (u3/fail)))
      (is (= [1 2 3] @results)))))


;; =====================================================
;; mark / cut — [ P307 chap22.5 ]
;; =====================================================
;;
;; util2 との対比:
;;   util2: mark = fail を PATHS に push（番兵）; cut = 番兵まで PATHS を pop
;;   util3: mark = 現在の *k-fail* を CUT-POINT に保存; cut = *k-fail* を CUT-POINT に set!
;;
;; 制約: cut の set! は binding フレーム内（cb/true-choose-simple のアーク内）からのみ有効。

(deftest mark-saves-k-fail-test
  (testing "mark は現在の *k-fail* を CUT-POINT に保存する"
    (let [checkpoint-fn (fn [] :checkpoint)]
      (binding [u3/*k-fail* checkpoint-fn]
        (u3/mark)
        (is (= checkpoint-fn @u3/CUT-POINT))))))


(deftest cut-restores-k-fail-test
  (testing "cut は *k-fail* を CUT-POINT まで巻き戻し、fail が CUT-POINT の継続を呼ぶ"
    (let [outer-called (atom false)]
      (binding [u3/*k-fail* (fn [] (reset! outer-called true) u3/failsym)]
        (u3/mark)                                        ;; CUT-POINT = outer-fn
        (binding [u3/*k-fail* (fn [] :inner-alternative)]
          (u3/cut)                                       ;; set! *k-fail* = @CUT-POINT
          (u3/fail)))                                    ;; → outer-fn が呼ばれる
      (is (true? @outer-called)))))


(deftest mark-cut-discards-alternatives-test
  (testing "mark → cut で mark 以降の選択肢を廃棄し、外側の *k-fail* に委譲する"
    ;; true-choose-simple の binding[*k-fail*=K] スコープ内から cut を呼ぶことで、
    ;; arc2（K が表す選択肢）をスキップして CUT-POINT（mark 時の *k-fail*）に委譲できる。
    (let [log (atom [])]
      (binding [u3/*k-fail* (fn [] (swap! log conj :outer) u3/failsym)]
        (u3/mark)
        (u3/true-choose-simple
          [(fn []
             (u3/cut)                             ;; arc2 をスキップして outer-fn へ
             (swap! log conj :arc1-after-cut)
             (u3/fail))                           ;; → outer-fn が呼ばれる
           (fn [] (swap! log conj :arc2))]))      ;; 呼ばれない
      (is (= [:arc1-after-cut :outer] @log)
          "cut 後の fail は arc2 ではなく outer の *k-fail* に委譲される"))))

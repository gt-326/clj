(ns onlisp.chap20.continuations.dynamic-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.chap20.continuations.dynamic :as d]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; dynamic.clj の *cont* は ^:dynamic Var + binding で管理される。
;; =bind のスコープを抜けると *cont* は自動的に元の値に戻るため、
;; atom.clj のようなグローバルリセット（use-fixtures）は不要。
;;
;;  atom.clj  → (reset! cont fn)   … スコープを抜けても cont は汚染されたまま
;;  dynamic.clj → (binding [*cont* fn] …) … スコープを抜けると自動的に戻る


;; テスト用 CPS 関数
(d/=defn add5 [x]
  (d/=values (+ x 5)))

(d/=defn add1 [x]
  (d/=values (inc x)))

(d/=defn sum-two [x y]
  (d/=values (+ x y)))


;; =====================================================
;; *cont* の初期状態
;; =====================================================

(deftest *cont*-initial-test
  (testing "*cont* の初期値は identity"
    (is (= identity d/*cont*))))


;; =====================================================
;; =values — *cont* を直接呼び出す
;; =====================================================

(deftest =values-test
  (testing "identity の状態で =values を呼ぶ → 引数をそのまま返す"
    (is (= 42   (d/=values 42)))
    (is (= :ok  (d/=values :ok)))
    (is (= "hi" (d/=values "hi"))))

  (testing "=values 呼び出しの前後で *cont* は変化しない"
    (let [cont-before d/*cont*]
      (d/=values 1)
      (is (identical? cont-before d/*cont*)
          "*cont* は =values 単体では変化しない"))))


;; =====================================================
;; =defn — CPS 関数の定義と呼び出し
;; =====================================================

(deftest =defn-test
  (testing "=defn で定義した CPS 関数を呼び出す"
    (is (= 15 (add5 10)))
    (is (= 1  (add1 0)))
    (is (= 7  (add5 2))))

  (testing "=defn の呼び出し前後で *cont* は変化しない"
    (let [cont-before d/*cont*]
      (add5 10)
      (is (identical? cont-before d/*cont*)
          "*cont* は =defn の呼び出しだけでは変化しない"))))


;; =====================================================
;; =bind — binding スコープで *cont* を管理する ★ dynamic の特性
;; =====================================================
;;
;;  atom.clj との違い:
;;    atom.clj  → =bind が cont を reset! する。スコープを抜けても変化が残る。
;;    dynamic.clj → =bind が binding でスコープ内だけ *cont* を変える。抜けると戻る。

(deftest =bind-scoped-test
  (testing "=bind の body が正しく実行される"
    (let [result (atom nil)]
      (d/=bind [y]
        (d/=values 7)
        (reset! result (* y 10)))
      (is (= 70 @result))))

  (testing "=bind 実行後、*cont* は identity に自動的に戻る（atom.clj とは異なる）"
    ;;  atom.clj では reset! のため =bind 後も cont が書き換わったまま残る。
    ;;  dynamic.clj では binding のスコープが自動解放されるため戻る。
    (let [cont-before d/*cont*]              ; identity
      (d/=bind [y]
        (d/=values 99)
        (identity y))
      (let [cont-after d/*cont*]             ; identity に戻っている
        (is (= identity cont-before))
        (is (= identity cont-after))
        (is (identical? cont-before cont-after)
            "=bind 後、*cont* は同一の identity オブジェクトに戻る（dynamic の特性）"))))

  (testing "=bind スコープ内では *cont* が変わる（body から確認）"
    ;;  body は binding フォーム内で呼ばれるため、
    ;;  実行時の *cont* は新しい fn になっている。
    (let [cont-in-body (atom nil)]
      (d/=bind [_y]
        (d/=values :trigger)
        (reset! cont-in-body d/*cont*))      ; body 内で *cont* を捕捉
      (is (not (identical? identity @cont-in-body))
          "=bind スコープ内では *cont* は identity でない")))

  (testing "=bind を繰り返しても *cont* は毎回 identity に戻る（手動リセット不要）"
    ;;  atom.clj では =bind ごとに cont が書き換わり累積するため、
    ;;  testing ブロック間で手動リセットが必要だった。
    ;;  dynamic.clj では自動的に戻るため、その必要がない。
    (d/=bind [_y1] (d/=values 1) :body1)
    (is (= identity d/*cont*) "1回目の =bind 後、identity に戻る")

    (d/=bind [_y2] (d/=values 2) :body2)
    (is (= identity d/*cont*) "2回目の =bind 後、identity に戻る")

    (d/=bind [_y3] (d/=values 3) :body3)
    (is (= identity d/*cont*) "3回目の =bind 後も identity に戻る")))


;; =====================================================
;; ネストした =bind — binding スコープが独立する ★ dynamic の特性
;; =====================================================
;;
;;  実行の流れ（dynamic.clj）:
;;    1. outer =bind: binding [*cont* ← outer-fn] でスコープ開始
;;    2. expr（inner =bind）: binding [*cont* ← inner-fn] でネスト
;;    3. =values :inner-value: inner-fn(:inner-value) を呼ぶ → inner body 実行
;;    4. inner binding スコープ終了 → *cont* は outer-fn に戻る
;;    5. outer binding スコープも終了 → *cont* は identity に戻る
;;
;;  inner body のみ実行される点は atom.clj と同じだが、
;;  終了後に *cont* が identity に戻る点が異なる。
;;  atom.clj では *cont* が inner-fn のまま残る。

(deftest =bind-nested-scoped-test
  (testing "ネストした =bind でも inner body のみ実行される（atom と同じ）"
    (let [outer-called (atom false)
          inner-called (atom false)]
      (d/=bind [_y]
        ;; expr: 内側の =bind が *cont* を inner-fn でシャドウ
        (d/=bind [_z]
          (d/=values :inner-value)       ; inner-fn(:inner-value) → inner body 実行
          (reset! inner-called true))
        ;; outer body: inner =bind の expr が戻った後に *cont* = outer-fn だが、
        ;; outer の expr 全体が返った時点で outer body は呼ばれない
        (reset! outer-called true))
      (is (true?  @inner-called) "inner body は実行される")
      (is (false? @outer-called) "outer body は実行されない")))

  (testing "ネスト後、*cont* は identity に戻る（atom.clj と異なる ★）"
    ;;  atom.clj: cont = inner-fn のまま残る（外部から呼べてしまう）
    ;;  dynamic.clj: binding スタックが巻き戻り identity に戻る（安全）
    (d/=bind [_y]
      (d/=bind [_z]
        (d/=values 1)
        :inner-done)
      :outer-done)
    (is (= identity d/*cont*)
        "*cont* は identity に自動的に戻る（atom では inner-fn のまま残る）")))


;; =====================================================
;; =fn + =fncall — CPS クロージャ
;; =====================================================

(deftest =fn-=fncall-test
  (testing "=fn で作った CPS クロージャを =fncall で呼ぶ"
    (let [result (atom nil)
          f      (d/=fn [n] (add1 n))]
      (d/=bind [y]
        (d/=fncall f 9)
        (reset! result y))
      (is (= 10 @result))))

  (testing "=fncall 後も *cont* は identity に戻る（=bind の自動スコープ解放）"
    (let [cont-before d/*cont*
          f           (d/=fn [n] (add1 n))]
      (d/=bind [_y]
        (d/=fncall f 1)
        :done)
      (is (identical? cont-before d/*cont*)
          "*cont* は =bind 前の状態に戻っている"))))


;; =====================================================
;; =apply — シーケンス引数で CPS 関数を呼ぶ
;; =====================================================

(deftest =apply-test
  (testing "=apply でペアを展開して sum-two を呼ぶ"
    (let [results (atom [])]
      (doseq [pair [[1 2] [3 4] [5 6]]]
        (d/=bind [s]
          (d/=apply =sum-two pair)
          (swap! results conj s)))
      (is (= [3 7 11] @results))))

  (testing "=apply 後も *cont* は identity に戻る"
    (let [cont-before d/*cont*]
      (d/=bind [_s]
        (d/=apply =sum-two [10 20])
        :done)
      (is (identical? cont-before d/*cont*)
          "=apply を含む =bind も *cont* を元に戻す"))))

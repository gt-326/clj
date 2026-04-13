(ns onlisp.chap20.continuations.atom-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap20.continuations.atom :as a]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; atom.clj の cont はグローバルな可変状態。
;; =bind を呼ぶたびに cont が書き換わるため、
;; 各テスト前に identity にリセットして独立性を保つ。
;;
;; （dynamic.clj では binding のスコープが自動的に
;;   元に戻るため、このようなリセットは不要。）

(use-fixtures :each
  (fn [f]
    (reset! a/cont identity)
    (f)))


;; テスト用 CPS 関数
(a/=defn add5 [x]
  (a/=values (+ x 5)))

(a/=defn add1 [x]
  (a/=values (inc x)))

(a/=defn sum-two [x y]
  (a/=values (+ x y)))


;; =====================================================
;; cont の初期状態
;; =====================================================

(deftest cont-initial-test
  (testing "cont の初期値は identity"
    (is (= identity @a/cont))))


;; =====================================================
;; =values — cont を直接呼び出す
;; =====================================================

(deftest =values-test
  (testing "identity の状態で =values を呼ぶ → 引数をそのまま返す"
    (is (= 42   (a/=values 42)))
    (is (= :ok  (a/=values :ok)))
    (is (= "hi" (a/=values "hi"))))

  (testing "=values 呼び出しの前後で cont は変化しない"
    (let [cont-before @a/cont]
      (a/=values 1)
      (is (identical? cont-before @a/cont)
          "cont は =values 単体では変化しない"))))


;; =====================================================
;; =defn — CPS 関数の定義と呼び出し
;; =====================================================

(deftest =defn-test
  (testing "=defn で定義した CPS 関数を呼び出す"
    (is (= 15 (add5 10)))
    (is (= 1  (add1 0)))
    (is (= 7  (add5 2))))

  (testing "=defn の呼び出し前後で cont は変化しない"
    (let [cont-before @a/cont]
      (add5 10)
      (is (identical? cont-before @a/cont)
          "cont は =defn の呼び出しだけでは変化しない"))))


;; =====================================================
;; =bind — cont をグローバルに書き換える ★ atom の特性
;; =====================================================
;;
;;  dynamic.clj との違い:
;;    atom.clj  → =bind が cont を reset! する。スコープを抜けても変化が残る。
;;    dynamic.clj → =bind が binding でスコープ内だけ *cont* を変える。抜けると戻る。

(deftest =bind-mutates-cont-test
  (testing "=bind の body が正しく実行される"
    (let [result (atom nil)]
      (a/=bind [y]
        (a/=values 7)
        (reset! result (* y 10)))
      (is (= 70 @result))))

  (testing "=bind 実行後、cont が書き換わったまま残る（identity に戻らない）"
    ;;  dynamic.clj では binding のスコープが自動解放されるため
    ;;  =bind 後に *cont* は元の identity に戻る。
    ;;  atom.clj では reset! のため戻らない。
    (let [cont-before @a/cont]               ; identity
      (a/=bind [y]
        (a/=values 99)
        (identity y))
      (let [cont-after @a/cont]              ; fn[y](identity y) のまま残る
        (is (fn? cont-before))
        (is (fn? cont-after))
        (is (not (identical? cont-before cont-after))
            "=bind 後、cont は新しい fn のまま残る（atom の特性）"))))

  (testing "=bind を繰り返すたびに cont が別の fn に変わる"
    ;; 前の testing で cont が書き換わっているため、ここで明示的にリセット
    ;; （use-fixtures :each は deftest 単位でリセットするため）
    (reset! a/cont identity)
    (is (= identity @a/cont) "リセット後は identity")

    (a/=bind [y] (a/=values 1) (* y 2))
    (let [cont-1 @a/cont]
      (is (not (identical? identity cont-1)) "1回目の =bind 後、identity ではない")

      (a/=bind [z] (a/=values 2) (* z 3))
      (let [cont-2 @a/cont]
        (is (not (identical? identity cont-2)) "2回目の =bind 後も identity ではない")
        (is (not (identical? cont-1 cont-2))   "1回目と2回目で cont は別物")))))


;; =====================================================
;; ネストした =bind — inner が outer を上書きする ★ atom の特性
;; =====================================================
;;
;;  dynamic.clj ではネストしても binding のスコープが独立しているが、
;;  atom.clj では内側の =bind が cont を reset! してしまうため、
;;  外側の =bind が設定した継続（outer body）は上書きされ、実行されない。

(deftest =bind-nested-overwrites-test
  (testing "内側の =bind が cont を上書きし、outer body は実行されない"
    ;;
    ;;  実行の流れ（atom.clj）:
    ;;    1. outer =bind: cont ← outer-fn
    ;;    2. expr（inner =bind）: cont ← inner-fn  ← outer-fn が上書きされる
    ;;    3. =values :inner-value: inner-fn(:inner-value) を呼ぶ → inner body 実行
    ;;    4. outer-fn は失われているため、outer body は実行されない
    ;;
    ;;  dynamic.clj との違い:
    ;;    dynamic.clj では binding のスコープが独立しているため
    ;;    inner =bind が終われば *cont* は outer-fn に戻る。
    ;;    （ただし outer body 自体の自動呼び出しはない）
    ;;
    (let [outer-called (atom false)
          inner-called (atom false)]
      (a/=bind [_y]
        ;; expr: 内側の =bind が cont を inner-fn で上書き
        (a/=bind [_z]
          (a/=values :inner-value)     ; inner-fn(:inner-value) → inner body 実行
          (reset! inner-called true))
        ;; outer body: cont はすでに inner-fn に変わっているため到達しない
        (reset! outer-called true))
      (is (true?  @inner-called) "inner body は実行される")
      (is (false? @outer-called) "outer body は実行されない（cont が上書きされたため）")))

  (testing "ネスト後の cont は inner-fn のまま残る（outer-fn は失われる）"
    (a/=bind [_y]
      (a/=bind [_z]
        (a/=values 1)
        :inner-done)
      :outer-done)
    (is (not (= identity @a/cont))
        "cont は inner-fn のまま残り identity には戻らない")))


;; =====================================================
;; =fn + =fncall — CPS クロージャ
;; =====================================================

(deftest =fn-=fncall-test
  (testing "=fn で作った CPS クロージャを =fncall で呼ぶ"
    (let [result (atom nil)
          f      (a/=fn [n] (add1 n))]
      (a/=bind [y]
        (a/=fncall f 9)
        (reset! result y))
      (is (= 10 @result))))

  (testing "=fncall の後も cont が書き換わっている（=bind の副作用）"
    (let [cont-before @a/cont
          f           (a/=fn [n] (add1 n))]
      (a/=bind [_y]
        (a/=fncall f 1)
        :done)
      (is (not (identical? cont-before @a/cont))
          "=bind が cont を書き換えた状態が残る"))))


;; =====================================================
;; =apply — シーケンス引数で CPS 関数を呼ぶ
;; =====================================================

(deftest =apply-test
  (testing "=apply でペアを展開して sum-two を呼ぶ"
    (let [results (atom [])]
      (doseq [pair [[1 2] [3 4] [5 6]]]
        (a/=bind [s]
          (a/=apply =sum-two pair)
          (swap! results conj s)))
      (is (= [3 7 11] @results))))

  (testing "=apply 後も cont が書き換わっている"
    (let [cont-before @a/cont]
      (a/=bind [_s]
        (a/=apply =sum-two [10 20])
        :done)
      (is (not (identical? cont-before @a/cont))
          "=apply を含む =bind も cont を書き換える"))))

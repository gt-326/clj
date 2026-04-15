(ns onlisp.common.util-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.common.util :as u]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; util.clj の *cont* は ^:dynamic Var + binding で管理される。
;; =bind のスコープを抜けると *cont* は自動的に元の値に戻るため、
;; atom.clj のようなグローバルリセット（use-fixtures）は不要。
;;
;; util.clj が dynamic.clj に対して追加する内容:
;;   - =defn_（buggy）: defn → defmacro の展開順。再帰関数でコンパイルエラーになる。
;;   - =defn （正しい）: declare → defmacro → defn の展開順。再帰関数に対応。
;;   - =bind_（buggy）: body 内 *cont* = 内側の継続。=values 呼び出しが自己参照になる。
;;   - =bind （正しい）: body 内 *cont* = 外側の継続。body 内で =values を呼べる。


;; テスト用 CPS 関数
(u/=defn add5 [x]
         (u/=values (+ x 5)))


(u/=defn add1 [x]
         (u/=values (inc x)))


(u/=defn sum-two [x y]
         (u/=values (+ x y)))


;; =defn による再帰 CPS 関数
;;
;; =defn の展開順（正しい）:
;;   1. (declare =countdown)     ← =countdown を前方宣言（参照可能にする）
;;   2. (defmacro countdown ...) ← マクロを先に定義
;;   3. (defn =countdown ...)    ← body コンパイル時に countdown マクロが定義済み
;;                                  (countdown ...) → (=countdown *cont* ...) に展開される

(u/=defn countdown [n acc]
         (if (zero? n)
           (u/=values acc)
           (countdown (dec n) (conj acc n))))


;; NOTE: =bind_ (buggy) を使ったツリー再帰（fib など）は無限ループになる。
;;
;; 理由:
;;   =bind_ は (binding [*cont* (fn [b] ... (u/*cont* result) ...)] expr) に展開される。
;;   (fn [b] ...) が呼ばれる時点でも binding スコープは active なため、
;;   その中の (u/*cont* result) は fn [b] 自身を指してしまい、無限ループになる。
;;
;; =bind (正しい版) では body 実行前に *cont* が外側の継続に復元されるため、
;; この自己参照問題は解消される。ただし、ツリー再帰は指数的に深いコールスタックを
;; 積むため、n が大きい場合は StackOverflowError になる可能性がある。


;; =defn_ による非再帰 CPS 関数（これは問題なく動作する）
;;
;; =defn_ の展開順（バグあり）:
;;   1. (defn =f  [...] body)   ← body 内で f を参照するが、f マクロはまだ未定義
;;   2. (defmacro f [...] ...)  ← マクロが後から定義される
;;
;; 自分自身を呼ばないため、マクロの定義順序は関係しない。

(u/=defn_ add5-nr [x]
          (u/=values (+ x 5)))


;; =====================================================
;; *cont* の初期状態
;; =====================================================

(deftest *cont*-initial-test
  (testing "*cont* の初期値は identity"
    (is (= identity u/*cont*))))


;; =====================================================
;; =values — *cont* を直接呼び出す
;; =====================================================

(deftest =values-test
  (testing "identity の状態で =values を呼ぶ → 引数をそのまま返す"
    (is (= 42   (u/=values 42)))
    (is (= :ok  (u/=values :ok)))
    (is (= "hi" (u/=values "hi"))))

  (testing "=values 呼び出しの前後で *cont* は変化しない"
    (let [cont-before u/*cont*]
      (u/=values 1)
      (is (identical? cont-before u/*cont*)
          "*cont* は =values 単体では変化しない"))))


;; =====================================================
;; =defn — マクロ展開形の検証（完全修飾シンボル）
;; =====================================================
;;
;; =defn の修正ポイント:
;;   旧: ネストしたバックティックで非修飾シンボルを展開
;;       → 別ネームスペースからの呼び出しで
;;         "Unable to resolve symbol: =add5 in this context" が発生
;;   新: list 形式で完全修飾シンボルを埋め込む
;;       → どのネームスペースから呼ばれても解決できる
;;
;; 既存の =defn-test は同一 ns 内からの呼び出しのみ検証しており、
;; 旧実装でも通過してしまう。
;; このテストでは macroexpand-1 で展開形を直接検証する。

(deftest =defn-expansion-qualified-test
  (testing "=defn のマクロ展開形に完全修飾シンボルが含まれる"
    ;; 非修飾シンボル '(add5 10) では test runner の *ns* に依存するため、
    ;; 完全修飾シンボルで macroexpand-1 を呼ぶことで *ns* に依存しない検証ができる。
    (let [expanded (macroexpand-1 '(onlisp.common.util-test/add5 10))]
      ;; 期待される展開形:
      ;; (onlisp.common.util-test/=add5 onlisp.common.util/*cont* 10)
      (is (= 'onlisp.common.util-test/=add5
             (first expanded))
          "関数シンボルが定義ネームスペース（util-test）で修飾されている")
      (is (= 'onlisp.common.util/*cont*
             (second expanded))
          "*cont* が onlisp.common.util で修飾されている")))

  (testing "多引数 =defn の展開形でも完全修飾シンボルが使われる"
    (let [expanded (macroexpand-1 '(onlisp.common.util-test/sum-two 3 4))]
      (is (= 'onlisp.common.util-test/=sum-two (first expanded)))
      (is (= 'onlisp.common.util/*cont*        (second expanded))))))


;; =====================================================
;; =defn — CPS 関数の定義と呼び出し
;; =====================================================

(deftest =defn-test
  (testing "=defn で定義した CPS 関数を呼び出す"
    (is (= 15 (add5 10)))
    (is (= 1  (add1 0)))
    (is (= 7  (add5 2))))

  (testing "=defn の呼び出し前後で *cont* は変化しない"
    (let [cont-before u/*cont*]
      (add5 10)
      (is (identical? cont-before u/*cont*)
          "*cont* は =defn の呼び出しだけでは変化しない"))))


;; =====================================================
;; =bind — body 内で外側の継続を復元する
;; =====================================================
;;
;; =bind_ (buggy) の展開:
;;   (binding [*cont* (fn params body)] expr)
;;   → body 実行時も *cont* = 内側の継続のまま
;;   → body 内で (=values ...) を呼ぶと *cont* が自己参照になる
;;
;; =bind (正しい) の展開:
;;   (let [outer# *cont*]
;;     (binding [*cont* (fn params (binding [*cont* outer#] body))]
;;       expr))
;;   → body 実行時に *cont* = 外側の継続に復元される
;;   → body 内で (=values ...) を呼べる

(deftest =bind-scoped-test
  (testing "=bind の body が正しく実行される"
    (let [result (atom nil)]
      (u/=bind [y]
               (u/=values 7)
               (reset! result (* y 10)))
      (is (= 70 @result))))

  (testing "=bind 実行後、*cont* は identity に自動的に戻る"
    (let [cont-before u/*cont*]
      (u/=bind [y]
               (u/=values 99)
               (identity y))
      (let [cont-after u/*cont*]
        (is (= identity cont-before))
        (is (= identity cont-after))
        (is (identical? cont-before cont-after)
            "=bind 後、*cont* は同一の identity オブジェクトに戻る"))))

  (testing "=bind の body 内では *cont* は外側の継続に戻る"
    (let [cont-before u/*cont*
          cont-in-body (atom nil)]
      (u/=bind [_y]
               (u/=values :trigger)
               (reset! cont-in-body u/*cont*))
      (is (identical? cont-before @cont-in-body)
          "=bind の body 内では *cont* は外側の継続と同じ")))

  (testing "=bind の body 内で =values を呼べる"
    (let [result (atom nil)]
      (u/=bind [m n]
               (u/=values 'hello 'there)
               (reset! result (list m n)))
      (is (= '(hello there) @result))))

  (testing "=bind を繰り返しても *cont* は毎回 identity に戻る"
    (u/=bind [_y1] (u/=values 1) :body1)
    (is (= identity u/*cont*) "1回目の =bind 後、identity に戻る")

    (u/=bind [_y2] (u/=values 2) :body2)
    (is (= identity u/*cont*) "2回目の =bind 後、identity に戻る")

    (u/=bind [_y3] (u/=values 3) :body3)
    (is (= identity u/*cont*) "3回目の =bind 後も identity に戻る")))


;; =====================================================
;; ネストした =bind — binding スコープが独立する
;; =====================================================
;;
;; 実行の流れ:
;;   1. outer =bind: binding [*cont* ← outer-fn] でスコープ開始
;;   2. expr（inner =bind）: binding [*cont* ← inner-fn] でネスト
;;   3. =values :inner-value: inner-fn(:inner-value) を呼ぶ → inner body 実行
;;   4. inner binding スコープ終了 → *cont* は outer-fn に戻る
;;   5. outer binding スコープも終了 → *cont* は identity に戻る

(deftest =bind-nested-scoped-test
  (testing "ネストした =bind でも inner body のみ実行される"
    (let [outer-called (atom false)
          inner-called (atom false)]
      (u/=bind [_y]
               (u/=bind [_z]
                        (u/=values :inner-value)
                        (reset! inner-called true))
               (reset! outer-called true))
      (is (true?  @inner-called) "inner body は実行される")
      (is (false? @outer-called) "outer body は実行されない")))

  (testing "ネスト後、*cont* は identity に戻る"
    (u/=bind [_y]
             (u/=bind [_z]
                      (u/=values 1)
                      :inner-done)
             :outer-done)
    (is (= identity u/*cont*)
        "*cont* は identity に自動的に戻る")))


;; =====================================================
;; =fn + =fncall — CPS クロージャ
;; =====================================================

(deftest =fn-=fncall-test
  (testing "=fn で作った CPS クロージャを =fncall で呼ぶ"
    (let [result (atom nil)
          f      (u/=fn [n] (add1 n))]
      (u/=bind [y]
               (u/=fncall f 9)
               (reset! result y))
      (is (= 10 @result))))

  (testing "=fncall 後も *cont* は identity に戻る"
    (let [cont-before u/*cont*
          f           (u/=fn [n] (add1 n))]
      (u/=bind [_y]
               (u/=fncall f 1)
               :done)
      (is (identical? cont-before u/*cont*)
          "*cont* は =bind 前の状態に戻っている"))))


;; =====================================================
;; =apply — シーケンス引数で CPS 関数を呼ぶ
;; =====================================================

(deftest =apply-test
  (testing "=apply でペアを展開して sum-two を呼ぶ"
    (let [results (atom [])]
      (doseq [pair [[1 2] [3 4] [5 6]]]
        (u/=bind [s]
                 (u/=apply =sum-two pair)
                 (swap! results conj s)))
      (is (= [3 7 11] @results))))

  (testing "=apply 後も *cont* は identity に戻る"
    (let [cont-before u/*cont*]
      (u/=bind [_s]
               (u/=apply =sum-two [10 20])
               :done)
      (is (identical? cont-before u/*cont*)
          "=apply を含む =bind も *cont* を元に戻す"))))


;; =====================================================
;; =defn_ — 非再帰関数への適用（問題なし）
;; =====================================================

(deftest =defn_-non-recursive-test
  (testing "=defn_ は非再帰関数には問題なく使える"
    (is (= 15 (add5-nr 10)))
    (is (= 1  (add5-nr -4)))))


;; =====================================================
;; =defn — 再帰 CPS 関数（declare + defmacro-first により正しく動作）
;; =====================================================
;;
;; countdown マクロが先に定義されるため、
;; =countdown の body 内 (countdown (dec n) ...) は
;; コンパイル時に (=countdown *cont* (dec n) ...) に展開される。

(deftest =defn-recursive-test
  (testing "=defn による再帰: countdown が正しく動く"
    (let [result (atom nil)]
      (u/=bind [r]
               (countdown 3 [])
               (reset! result r))
      (is (= [3 2 1] @result))))

  (testing "=defn による再帰: countdown 0 → 空ベクタをそのまま返す"
    (let [result (atom nil)]
      (u/=bind [r]
               (countdown 0 [])
               (reset! result r))
      (is (= [] @result))))

  (testing "=defn による再帰: countdown で複数の値を連続確認"
    (let [results (atom [])]
      (doseq [n [1 2 3 4 5]]
        (u/=bind [r]
                 (countdown n [])
                 (swap! results conj r)))
      (is (= [[1] [2 1] [3 2 1] [4 3 2 1] [5 4 3 2 1]] @results)))))


;; =====================================================
;; =defn_ — 再帰関数での展開順バグ（eval で分離して検証）
;; =====================================================
;;
;; =defn_ の展開: (defn =f [...] body) → (defmacro f [...] ...)
;;
;; body がコンパイルされる時点で f マクロが未定義のため、
;; body 内の (f ...) が未解決シンボルとしてコンパイルエラーになる。
;;
;; テストファイルのトップレベルに置くとファイルのロード自体が失敗するため、
;; eval で実行時にコンパイルを走らせて例外を捕捉している。

(deftest =defn_-recursive-fails-test
  (testing "=defn_ による再帰: 定義時にコンパイルエラーが発生する"
    (is (thrown? Exception
          (eval
            '(onlisp.common.util/=defn_ countdown-bad [n acc]
                                        (if (zero? n)
                                          (onlisp.common.util/=values acc)
                                          (countdown-bad (dec n) (conj acc n)))))))))

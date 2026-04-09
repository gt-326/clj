(ns onlisp.common.util4-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.common.util4 :as util4]))


;; util4 は gensym（G__ プレフィックス）を Prolog 変数とみなす。
;; ? プレフィックスの varsym?（util1）と対比:
;;
;;   util1/varsym? : ?x, ?y, ?foo → true
;;   util4/varsym? : G__xxx（gensym の出力）→ true
;;
;; match2 / fullbind2 は Prolog コンパイラ（compiler.clj / compiler2.clj）内の
;; with-gensyms で生成された変数に対して使用される。


;; =====================================================
;; varsym?
;; =====================================================

(deftest varsym?-test
  (testing "G__ プレフィックスの gensym シンボルは true"
    (is (true? (util4/varsym? (gensym))))
    (is (true? (util4/varsym? 'G__1)))
    (is (true? (util4/varsym? 'G__foo))))

  (testing "? プレフィックスのシンボルは false（util1/varsym? の担当）"
    (is (false? (util4/varsym? '?x)))
    (is (false? (util4/varsym? '?foo))))

  (testing "通常シンボルは false"
    (is (false? (util4/varsym? 'x)))
    (is (false? (util4/varsym? 'foo)))
    (is (false? (util4/varsym? '_))))

  (testing "シンボル以外は false"
    (is (false? (util4/varsym? nil)))
    (is (false? (util4/varsym? 42)))
    (is (false? (util4/varsym? "G__x")))
    (is (false? (util4/varsym? '(G__1 G__2))))))


;; =====================================================
;; fullbind2
;; =====================================================

(deftest fullbind2-atom-test
  (testing "アトムはそのまま返す"
    (is (= 'a  (util4/fullbind2 'a  {})))
    (is (= 42  (util4/fullbind2 42  {})))
    (is (nil?  (util4/fullbind2 nil {}))))

  (testing "? 変数（util1 スタイル）も binds になければアトム扱いで gensym を返す"
    ;; varsym? が false なので cl-atom? も false（symbol? ではない）
    ;; → else 分岐で再帰。最終的にアトムとして処理される
    (is (= '?x (util4/fullbind2 '?x {})))))


(deftest fullbind2-gensym-var-test
  (testing "バインド済み gensym 変数 → 値を解決"
    (let [g (gensym)]
      (is (= 'hi    (util4/fullbind2 g {g 'hi})))
      (is (= '(1 2) (util4/fullbind2 g {g '(1 2)})))))

  (testing "nil にバインド済みの gensym 変数 → nil を返す（contains? 対応）"
    (let [g (gensym)]
      (is (nil? (util4/fullbind2 g {g nil})))))

  (testing "未束縛の gensym 変数 → gensym プレースホルダを返す（symbol? のみ検証）"
    (let [g (gensym)]
      (is (symbol? (util4/fullbind2 g {})))))

  (testing "変数チェーンを追跡（G1 → G2 → 値）"
    (let [g1 (gensym) g2 (gensym)]
      (is (= 42 (util4/fullbind2 g1 {g1 g2, g2 42})))))

  (testing "変数が nil チェーン途中にある場合（G1 → nil）"
    (let [g1 (gensym) g2 (gensym)]
      (is (nil? (util4/fullbind2 g1 {g1 g2, g2 nil}))))))


(deftest fullbind2-list-test
  (testing "リスト内の gensym 変数を再帰的に解決"
    (let [g1 (gensym) g2 (gensym)]
      (is (= '(a b) (util4/fullbind2 (list g1 g2) {g1 'a, g2 'b})))))

  (testing "一部が未束縛のリスト → 未束縛は gensym プレースホルダ"
    (let [g1 (gensym) g2 (gensym)]
      (let [result (util4/fullbind2 (list g1 g2) {g1 'a})]
        (is (= 'a (first result)))
        (is (symbol? (second result))))))

  (testing "ネストしたリスト内の変数を再帰的に解決"
    (let [g1 (gensym) g2 (gensym)]
      (is (= '(a (b)) (util4/fullbind2 (list g1 (list g2)) {g1 'a, g2 'b}))))))


(deftest fullbind2-dot-pair-test
  (testing "ドット対 (. G__rest) 構造 → バインド値をそのまま返す"
    (let [g (gensym)]
      (is (= '(a b) (util4/fullbind2 (list '. g) {g '(a b)})))))

  (testing "ドット対 (. G__rest) が nil にバインド → nil を返す"
    (let [g (gensym)]
      (is (nil? (util4/fullbind2 (list '. g) {g nil})))))

  (testing "ドット対 (. G__rest) がアトムにバインド → (atom) でラップして返す"
    (let [g (gensym)]
      (is (= '(a) (util4/fullbind2 (list '. g) {g 'a}))))))


;; =====================================================
;; match2 — リテラルの同一性
;; =====================================================

(deftest match2-equal-test
  (testing "同一アトム → 空の binds を返す"
    (is (= {} (util4/match2 'a 'a)))
    (is (= {} (util4/match2 42 42)))
    (is (= {} (util4/match2 nil nil))))

  (testing "不一致アトム → nil"
    (is (nil? (util4/match2 'a 'b)))
    (is (nil? (util4/match2 1 2))))

  (testing "同一リスト → 空の binds を返す"
    (is (= {} (util4/match2 '(1 2) '(1 2)))))

  (testing "空リスト同士の一致"
    (is (= {} (util4/match2 '() '())))))


;; =====================================================
;; match2 — ワイルドカード _
;; =====================================================

(deftest match2-wildcard-test
  (testing "_ は任意の値にマッチし、束縛しない"
    (is (= {} (util4/match2 '_ 'anything)))
    (is (= {} (util4/match2 'anything '_))))

  (testing "_ を含むリストパターン（gensym 変数と組み合わせ）"
    (let [g1 (gensym) g3 (gensym)
          result (util4/match2 (list g1 '_ g3) '(1 2 3))]
      (is (= 1 (get result g1)))
      (is (= 3 (get result g3)))
      (is (= 2 (count result))))))


;; =====================================================
;; match2 — gensym 変数のバインディング
;; =====================================================

(deftest match2-gensym-var-test
  (testing "x 側の gensym 変数が値にバインドされる"
    (let [g (gensym)
          result (util4/match2 g 'hi)]
      (is (map? result))
      (is (= 'hi (get result g)))))

  (testing "y 側の gensym 変数が値にバインドされる"
    (let [g (gensym)
          result (util4/match2 'hi g)]
      (is (map? result))
      (is (= 'hi (get result g)))))

  (testing "両辺が gensym 変数 → x が y の名前にバインドされる"
    (let [g1 (gensym) g2 (gensym)
          result (util4/match2 g1 g2)]
      (is (map? result))
      ;; x 側 (g1) が y の名前 (g2) を指す
      (is (= g2 (get result g1)))))

  (testing "複数の gensym 変数をリスト内でマッチ"
    (let [g1 (gensym) g2 (gensym)
          result (util4/match2 (list g1 g2) '(hi ho))]
      (is (= 'hi (get result g1)))
      (is (= 'ho (get result g2)))))

  (testing "リテラルを含むパターンでの gensym 変数マッチ"
    (let [g (gensym)
          result (util4/match2 (list 'a g 'b) '(a 1 b))]
      (is (= 1 (get result g))))))


;; =====================================================
;; match2 — 既存 binds の引き継ぎ
;; =====================================================

(deftest match2-binds-test
  (testing "既存の binds を引き継いでマッチ継続"
    (let [g1 (gensym) g2 (gensym)
          result (util4/match2 (list g1 g2) '(hi ho) {g1 'hi})]
      (is (= 'hi (get result g1)))
      (is (= 'ho (get result g2)))))

  (testing "既存の binds と矛盾する → nil"
    (let [g1 (gensym) g2 (gensym)]
      (is (nil? (util4/match2 (list g1 g2) '(hi ho) {g1 'bye})))))

  (testing "空マップを渡す → 通常通りマッチ"
    (let [g (gensym)
          result (util4/match2 g 1 {})]
      (is (= 1 (get result g))))))


;; =====================================================
;; match2 — マッチ失敗
;; =====================================================

(deftest match2-failure-test
  (testing "リテラルが一致しない → nil"
    (let [g (gensym)]
      (is (nil? (util4/match2 (list g 'a) '(1 b))))))

  (testing "長さが異なる → nil"
    (let [g1 (gensym) g2 (gensym)]
      (is (nil? (util4/match2 (list g1 g2) '(1 2 3))))))

  (testing "アトムとリストのミスマッチ → nil"
    (is (nil? (util4/match2 'a '(1 2))))))


;; =====================================================
;; match2 — nil バインド済み変数（contains? 対応）
;; =====================================================

(deftest match2-nil-binding-test
  (testing "nil にバインド済みの gensym 変数を同じ nil に再マッチ → 成功"
    (let [g (gensym)
          binds {g nil}]
      (is (= binds (util4/match2 g nil binds)))))

  (testing "nil にバインド済みの gensym 変数を別の値に再マッチ → 失敗"
    (let [g (gensym)
          binds {g nil}]
      (is (nil? (util4/match2 g 'something binds)))))

  (testing "y 側の gensym 変数が nil にバインド済みの場合も同様"
    (let [g (gensym)
          binds {g nil}]
      (is (= binds (util4/match2 nil g binds)))
      (is (nil? (util4/match2 'something g binds))))))


;; =====================================================
;; match2 — ドット対パターン
;; =====================================================

(deftest match2-dot-pair-test
  (testing "Y 側ドット対 (. G__) はリスト全体を gensym 変数にバインドする"
    ;; (. G__rest) はリスト x 全体を G__rest にバインドする
    ;; Prolog の (?x . ?rest) パターンの残部マッチに対応
    (let [g (gensym)
          y-pat (list '. g)   ; (. G__xxx)
          result (util4/match2 '(a b c) y-pat)]
      (is (map? result))
      (is (= '(a b c) (get result g)))))

  (testing "Y 側ドット対 (. G__) にリスト全体がバインドされる（別例）"
    (let [g (gensym)
          result (util4/match2 '(1 2) (list '. g))]
      (is (= '(1 2) (get result g)))))

  (testing "X 側ドット対 (. G__) がバインド済み値で解決されて再マッチ"
    ;; x = (. G__) で G__ が (a b c) にバインド済み → (a b c) として y とマッチ
    (let [g (gensym)
          x-pat (list '. g)
          binds {g '(a b c)}]
      (is (= binds (util4/match2 x-pat '(a b c) binds)))))

  (testing "X 側ドット対 (. G__) が未束縛の場合はそのまま再マッチに渡す"
    ;; G__ が未束縛 → (second x-pat) = G__ として y にマッチ → G__ を y にバインド
    (let [g (gensym)
          x-pat (list '. g)
          result (util4/match2 x-pat '(a b c) {})]
      (is (map? result))
      (is (= '(a b c) (get result g))))))


;; =====================================================
;; match2 — ネストしたリストのマッチ
;; =====================================================

(deftest match2-nested-test
  (testing "1段ネストのマッチ"
    (let [g1 (gensym) g2 (gensym)
          result (util4/match2 (list g1 (list g2)) '(1 (2)))]
      (is (= 1 (get result g1)))
      (is (= 2 (get result g2)))))

  (testing "2段ネストのマッチ"
    (let [g1 (gensym) g2 (gensym) g3 (gensym)
          result (util4/match2 (list g1 (list g2 (list g3))) '(1 (2 (3))))]
      (is (= 1 (get result g1)))
      (is (= 2 (get result g2)))
      (is (= 3 (get result g3)))))

  (testing "リテラルとのネストマッチ"
    (let [g (gensym)
          result (util4/match2 (list 'a (list g 'c)) '(a (b c)))]
      (is (= 'b (get result g))))))

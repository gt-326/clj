(ns onlisp.common.util1-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.common.util1 :as util1]))


;; =====================================================
;; varsym?
;; =====================================================

(deftest varsym?-test
  (testing "? で始まるシンボルは true"
    (is (true? (util1/varsym? '?x)))
    (is (true? (util1/varsym? '?y)))
    (is (true? (util1/varsym? '?foo))))

  (testing "? で始まらないシンボルは false"
    (is (false? (util1/varsym? 'x)))
    (is (false? (util1/varsym? 'foo)))
    (is (false? (util1/varsym? '_))))

  (testing "シンボル以外は false"
    (is (false? (util1/varsym? nil)))
    (is (false? (util1/varsym? 42)))
    (is (false? (util1/varsym? "?x")))))


;; =====================================================
;; vars-in
;; =====================================================

(deftest vars-in-test
  (testing "単一のパターン変数（アトムとして渡す）"
    (is (= '#{?x} (util1/vars-in '?x))))

  (testing "フラットなパターン変数の収集"
    (is (= '#{?x} (util1/vars-in '(?x))))
    (is (= '#{?x ?y} (util1/vars-in '(?x ?y))))
    (is (= '#{?x ?y ?z} (util1/vars-in '(?x ?y ?z)))))

  (testing "リテラルを含む場合はパターン変数のみ抽出"
    (is (= '#{?x ?y} (util1/vars-in '(?x a ?y)))))

  (testing "ネストしたパターンを再帰的に収集"
    (is (= '#{?x ?y ?z} (util1/vars-in '(?x (?y ?z))))))

  (testing "パターン変数なし → nil"
    (is (nil? (util1/vars-in '(a b c))))))


;; =====================================================
;; match — アトムの同一性チェック
;; =====================================================

(deftest match-equal-test
  (testing "同一アトム → 空の binds を返す"
    (is (= {} (util1/match 'a 'a)))
    (is (= {} (util1/match 42 42)))
    (is (= {} (util1/match nil nil))))

  (testing "不一致アトム → nil"
    (is (nil? (util1/match 'a 'b)))
    (is (nil? (util1/match 1 2))))

  (testing "同一リスト → 空の binds を返す"
    (is (= {} (util1/match '(1 2) '(1 2)))))

  (testing "空リスト同士の一致"
    (is (= {} (util1/match '() '())))))


;; =====================================================
;; match — パターン変数のバインディング
;; =====================================================

(deftest match-pattern-var-test
  (testing "パターン変数がアトムにバインドされる"
    (is (= '{?x 42} (util1/match '?x 42)))
    (is (= '{?x hi} (util1/match '?x 'hi))))

  (testing "パターン変数がリストにバインドされる"
    (is (= '{?x (1 2)} (util1/match '?x '(1 2)))))

  (testing "両辺がパターン変数 → 左が右のシンボルにバインドされる"
    (is (= '{?x ?y} (util1/match '?x '?y))))

  (testing "複数パターン変数のマッチ"
    (is (= '{?x hi ?y ho} (util1/match '(?x ?y) '(hi ho)))))

  (testing "リテラルを含むパターン"
    (is (= '{?x 1} (util1/match '(a ?x b) '(a 1 b))))))


;; =====================================================
;; match — ワイルドカード _
;; =====================================================

(deftest match-wildcard-test
  (testing "_ は任意のアトムにマッチし束縛しない"
    (is (= {} (util1/match '_ 'anything)))
    (is (= {} (util1/match 'anything '_))))

  (testing "_ を含むリストパターン"
    (is (= '{?x 1 ?z 3} (util1/match '(?x _ ?z) '(1 2 3)))))

  (testing "複数の _ を含むパターン"
    (is (= '{?x 2} (util1/match '(_ ?x _) '(1 2 3))))))


;; =====================================================
;; match — 既存 binds の引き継ぎ
;; =====================================================

(deftest match-binds-test
  (testing "既存の binds を引き継いでマッチ継続"
    (is (= '{?x hi ?y ho} (util1/match '(?x ?y) '(hi ho) '{?x hi}))))

  (testing "既存の binds と矛盾する → nil"
    (is (nil? (util1/match '(?x ?y) '(hi ho) '{?x bye}))))

  (testing "空マップを渡す → 通常通りマッチ"
    (is (= '{?x 1} (util1/match '?x 1 {})))))


;; =====================================================
;; match — マッチ失敗
;; =====================================================

(deftest match-failure-test
  (testing "リテラルが一致しない → nil"
    (is (nil? (util1/match '(?x a) '(1 b)))))

  (testing "長さが異なる（パターンより長い）→ nil"
    (is (nil? (util1/match '(?x ?y) '(1 2 3)))))

  (testing "アトムとリストのミスマッチ → nil"
    (is (nil? (util1/match 'a '(1 2))))))


;; =====================================================
;; match — ネスト
;; =====================================================

(deftest match-nested-test
  (testing "1段ネストのマッチ"
    (is (= '{?x 1 ?y 2} (util1/match '(?x (?y)) '(1 (2))))))

  (testing "2段ネストのマッチ"
    (is (= '{?x 1 ?y 2 ?z 3} (util1/match '(?x (?y (?z))) '(1 (2 (3))))))))


;; =====================================================
;; match — nil バインド済み変数 (contains? 対応)
;; =====================================================

(deftest match-nil-binding-test
  (testing "nil にバインド済みの変数を同じ nil に再マッチ → 成功"
    (is (= '{?x nil} (util1/match '?x nil '{?x nil}))))

  (testing "nil にバインド済みの変数を別の値に再マッチ → 失敗"
    (is (nil? (util1/match '?x 'something '{?x nil}))))

  (testing "右辺変数が nil にバインド済みの場合も同様"
    (is (= '{?y nil} (util1/match nil '?y '{?y nil})))
    (is (nil? (util1/match 'something '?y '{?y nil})))))


;; =====================================================
;; match — ドット対パターン (. ?rest) / (?x . ?rest)
;; =====================================================

(deftest match-dot-pair-test
  (testing "(. ?rest) パターン：リスト全体を ?rest に束縛"
    (is (= '{?rest (a b c)} (util1/match '(a b c) '(. ?rest))))
    (is (= '{?rest (1 2)}   (util1/match '(1 2)   '(. ?rest)))))

  (testing "(?x . ?rest) パターン：先頭と残りを分離して束縛"
    (is (= '{?x a ?rest (b c)} (util1/match '(a b c) '(?x . ?rest))))
    (is (= '{?x a ?rest nil}   (util1/match '(a)     '(?x . ?rest)))))

  (testing "空リスト / nil はドット対パターンにマッチしない（nil ガード）"
    (is (nil? (util1/match '() '(?x . ?rest))))
    (is (nil? (util1/match nil  '(?x . ?rest))))))



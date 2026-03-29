(ns onlisp.chap18.quick-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.chap18.quick :as quick]))


;; =====================================================
;; if-match-quick（コンパイル時 seq）
;; =====================================================

(deftest if-match-quick-basic-test
  (testing "単一パターン変数のマッチ"
    (is (= 1
           (quick/if-match-quick (?x) (1) ?x "else"))))

  (testing "複数パターン変数のマッチ"
    (is (= [1 2]
           (quick/if-match-quick (?x ?y) (1 2) [?x ?y] "else"))))

  (testing "then にリストを直接書くと関数呼び出しになり ClassCastException が発生する"
    (is (thrown? ClassCastException
                 (quick/if-match-quick (?x ?y) (1 2) (?x ?y) "else"))))

  (testing "ローカル変数を then に含む"
    (let [n 3]
      (is (= '(1 2 3)
             (quick/if-match-quick (?x ?y) (1 2) (list ?x ?y n) "else"))))))


(deftest if-match-quick-lenient-test
  (testing "要素数がパターンより少ない → nil 補完（lenient matching）"
    (is (= '[hi ho nil]
           (quick/if-match-quick (?x ?y ?z) (hi ho) [?x ?y ?z] "else"))))

  (testing "空リスト () → then に入り ?x = nil（pat-match の seq() 修正の回帰テスト）"
    (is (= nil
           (quick/if-match-quick (?x) () ?x "else")))))


(deftest if-match-quick-wildcard-test
  (testing "ワイルドカード _ は任意の要素にマッチし束縛しない"
    ;; seq 中のシンボルは '(a b) として quote されるため var 解決エラーにならない
    (is (= 'b
           (quick/if-match-quick (_ ?x) (a b) ?x "else"))))

  (testing "複数の _ を含むパターン"
    (is (= 2
           (quick/if-match-quick (_ ?x _) (1 2 3) ?x "else")))))


(deftest if-match-quick-literal-test
  ;; パターン中に bare シンボルを直接書くと var 解決エラーになる。
  ;; 数値リテラルまたはローカル変数・quoted シンボルでリテラルマッチを表現する。

  (testing "数値リテラルが一致 → then を返す"
    (is (= 1
           (quick/if-match-quick (?x 42) (1 42) ?x "else"))))

  (testing "数値リテラルが不一致 → else を返す"
    (is (= "else"
           (quick/if-match-quick (?x 42) (1 99) ?x "else"))))

  (testing "ローカル変数のリテラルマッチ → 一致で then"
    (let [n 3]
      (is (= 1
             (quick/if-match-quick (?x n) (1 3) ?x "else")))))

  (testing "ローカル変数のリテラルマッチ → 不一致で else"
    (let [n 3]
      (is (= "else"
             (quick/if-match-quick (?x n) (1 9) ?x "else"))))))


(deftest if-match-quick-local-var-test
  (testing "quoted シンボルとローカル変数の混在"
    (let [n 3]
      (is (= '(1 3)
             (quick/if-match-quick (?x 'n n) (1 n 3) (list ?x n) "else")))))

  (testing "quoted リストをリテラルとしてマッチ"
    (let [n 3 m "m"]
      (is (= '(1 3 "m")
             (quick/if-match-quick (?x n 'n m '(a b)) (1 3 'n "m" '(a b)) (list ?x n m) "else"))))))


;; =====================================================
;; abab4 / abab5 / abab6（if-match-quick の実用例）
;; =====================================================

(deftest abab4-test
  (testing "ローカル変数と quoted シンボルの混在パターンが常にマッチ"
    (is (= '(1 3 "m")
           (quick/abab4)))))


(deftest abab5-test
  (testing "seq の末尾要素を関数引数として渡す → マッチ成功"
    (is (= '(1 3 "m" 100)
           (quick/abab5 100 "abab5"))))

  (testing "末尾要素が一致しない → else を返す"
    (is (= "abab5"
           (quick/abab5 101 "abab5")))))


(deftest abab6-test
  (testing "seq とパターンが固定のため常にマッチ → then をそのまま返す"
    (is (= "matched"
           (quick/abab6 "matched" "else"))))

  (testing "then に任意の値を渡せる"
    (is (= 42
           (quick/abab6 42 "else")))))


;; =====================================================
;; if-match-quick2（実行時 seq）
;; =====================================================

(deftest abab7-basic-test
  (testing "3要素 → すべて束縛"
    (is (= [1 2 3]
           (quick/abab7 '(1 2 3) "abab7"))))

  (testing "2要素（lenient）→ 不足分は nil"
    (is (= '[hi ho nil]
           (quick/abab7 '(hi ho) "abab7"))))

  (testing "空リスト → (coll? s) = true により then に入り [nil nil nil] を返す
  ;; if-match-quick（compile-time）と統一された挙動。"
    (is (= '[nil nil nil]
           (quick/abab7 '() "abab7")))))

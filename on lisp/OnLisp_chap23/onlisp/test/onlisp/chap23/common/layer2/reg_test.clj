(ns onlisp.chap23.common.layer2.reg-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.chap23.common.layer2.reg :as r]))


;; =====================================================
;; getr — レジスタから値を取り出す
;; =====================================================
;;
;; 値が1つのとき: そのまま返す（リストにしない）
;; 値が複数のとき: リストで返す
;; キーが存在しないとき: nil を返す

(deftest getr-not-found-test
  (testing "キーが存在しないとき nil を返す"
    (is (nil? (r/getr v '(((subj spot))))))))


(deftest getr-single-value-test
  (testing "値が1つのとき、リストにせずそのまま返す"
    (is (= 'spot
           (r/getr subj '(((subj spot))))))))


(deftest getr-multiple-values-test
  (testing "値が複数のとき、リストで返す"
    (is (= '(runs spot)
           (r/getr subj '(((subj runs spot))))))))


;; =====================================================
;; pushr — レジスタに値を積む
;; =====================================================
;;
;; setr との違い:
;;   setr: 既存の値を無視して新規エントリを先頭に追加
;;   pushr: 既存の値リストの先頭に新しい値を追加

(deftest pushr-vs-setr-test
  (testing "setr は既存エントリの前に新規エントリを追加する"
    (is (= '(((subj runs) (subj spot)))
           (r/setr subj 'runs '(((subj spot)))))))

  (testing "pushr は既存の値リストの先頭に値を追加する"
    (is (= '(((subj runs spot) (subj spot)))
           (r/pushr subj 'runs '(((subj spot))))))))


(deftest pushr-new-key-test
  (testing "存在しないキーへの pushr は setr 相当になる"
    (is (= '(((v runs) (subj spot)))
           (r/pushr v 'runs '(((subj spot))))))))

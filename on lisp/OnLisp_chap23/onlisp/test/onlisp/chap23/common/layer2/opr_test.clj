(ns onlisp.chap23.common.layer2.opr-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap23.common.layer2.opr :as o]))


(def initial-dictionary @o/DICTIONARY)

(defn reset-dictionary! [f]
  (f)
  (reset! o/DICTIONARY initial-dictionary))

(use-fixtures :each reset-dictionary!)


;; =====================================================
;; dictionary-word — 単語カテゴリの lookup
;; =====================================================

(deftest dictionary-word-aux-v-test
  (testing "do / does / did は aux と v の両方に属する"
    (is (= '(aux v) (o/dictionary-word 'do)))
    (is (= '(aux v) (o/dictionary-word 'does)))
    (is (= '(aux v) (o/dictionary-word 'did)))))


(deftest dictionary-word-n-v-test
  (testing "time / times / fly / flies は n と v の両方に属する"
    (is (= '(n v) (o/dictionary-word 'time)))
    (is (= '(n v) (o/dictionary-word 'times)))
    (is (= '(n v) (o/dictionary-word 'fly)))
    (is (= '(n v) (o/dictionary-word 'flies)))))


(deftest dictionary-word-v-prep-test
  (testing "like は v と prep の両方に属する"
    (is (= '(v prep) (o/dictionary-word 'like)))))


(deftest dictionary-word-v-test
  (testing "liked / likes は v のみ"
    (is (= '(v) (o/dictionary-word 'liked)))
    (is (= '(v) (o/dictionary-word 'likes)))))


(deftest dictionary-word-det-test
  (testing "a / an / the は det"
    (is (= '(det) (o/dictionary-word 'a)))
    (is (= '(det) (o/dictionary-word 'an)))
    (is (= '(det) (o/dictionary-word 'the)))))


(deftest dictionary-word-n-test
  (testing "arrow / arrows は n のみ"
    (is (= '(n) (o/dictionary-word 'arrow)))
    (is (= '(n) (o/dictionary-word 'arrows)))))


(deftest dictionary-word-pron-test
  (testing "代名詞は pron"
    (is (= '(pron) (o/dictionary-word 'i)))
    (is (= '(pron) (o/dictionary-word 'you)))
    (is (= '(pron) (o/dictionary-word 'he)))
    (is (= '(pron) (o/dictionary-word 'she)))
    (is (= '(pron) (o/dictionary-word 'him)))
    (is (= '(pron) (o/dictionary-word 'her)))
    (is (= '(pron) (o/dictionary-word 'it)))))


(deftest dictionary-word-noun-verb-test
  (testing "spot は noun、runs は verb（ATN サンプル用の単語）"
    (is (= '(noun) (o/dictionary-word 'spot)))
    (is (= '(verb) (o/dictionary-word 'runs)))))


(deftest dictionary-word-unknown-test
  (testing "辞書にない単語は空リストを返す"
    (is (= '() (o/dictionary-word 'unknown)))
    (is (= '() (o/dictionary-word 'foo)))
    (is (= '() (o/dictionary-word 'xyz)))))


;; =====================================================
;; register-word! — 動的な単語登録
;; =====================================================

(deftest register-word-new-test
  (testing "新規単語を登録できる"
    (o/register-word! 'cat '(n))
    (is (= '(n) (o/dictionary-word 'cat)))))

(deftest register-word-overwrite-test
  (testing "既存単語のカテゴリを上書きできる"
    (o/register-word! 'spot '(n noun))
    (is (= '(n noun) (o/dictionary-word 'spot)))))

(deftest register-word-isolation-test
  (testing "登録はテスト間で引き継がれない（fixture による reset 確認）"
    (is (= '() (o/dictionary-word 'cat)))))

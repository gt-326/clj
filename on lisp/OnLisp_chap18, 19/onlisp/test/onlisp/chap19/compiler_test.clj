(ns onlisp.chap19.compiler-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.chap19.compiler :as compiler]))


;; compiler.clj の ns ロード時に (store/gen-facts) が呼ばれ、
;; *default-db* に以下のファクトが登録される（conj による逆順）。
;;
;;   painter: (reynolds joshua english) (hogarth william english) (canale antonio venetian)
;;   dates:   (reynolds 1723 1792)      (hogarth 1697 1772)       (canale 1697 1768)
;;
;; compile-simple は *default-db* をマクロ展開時（コンパイル時）に読み取り、
;; ファクトをコードに焼き込む。テストコードのコンパイル前に DB が確定している必要があるが、
;; (require [onlisp.chap19.compiler]) が先に実行されるため問題ない。


;; =====================================================
;; abab（AND クエリの実用例）
;; =====================================================

(deftest abab-test
  (testing "英国人画家 × 1697 年生まれ → hogarth のみ"
    (is (= '[[hogarth william 1697 1772]]
           (compiler/abab 'english 1697))))

  (testing "ヴェネチア人画家 × 1697 年生まれ → canale のみ"
    (is (= '[[canale antonio 1697 1768]]
           (compiler/abab 'venetian 1697))))

  (testing "英国人画家 × 1723 年生まれ → reynolds のみ"
    (is (= '[[reynolds joshua 1723 1792]]
           (compiler/abab 'english 1723))))

  (testing "該当なし → 空ベクタ"
    (is (= []
           (compiler/abab 'french 1697)))))


;; =====================================================
;; with-answer-compile — 単一述語
;; =====================================================

(deftest with-answer-compile-simple-test
  (testing "hogarth にマッチ → 1件"
    (is (= '[(william english)]
           (compiler/with-answer-compile
             (painter 'hogarth ?x ?y)
             (list ?x ?y)))))

  (testing "全英国人画家を列挙（ファクトの逆順）"
    (is (= '[reynolds hogarth]
           (compiler/with-answer-compile
             (painter ?x _ 'english)
             ?x))))

  (testing "マッチなし → 空ベクタ"
    (is (= []
           (compiler/with-answer-compile
             (painter 'nobody ?x ?y)
             (list ?x ?y))))))


;; =====================================================
;; with-answer-compile — and
;; =====================================================

(deftest with-answer-compile-and-test
  (testing "painter と dates の結合（英国人のみ）"
    (is (= '[[reynolds joshua 1723 1792]
             [hogarth william 1697 1772]]
           (compiler/with-answer-compile
             (and
               (painter ?x ?y 'english)
               (dates ?x ?b ?d))
             [?x ?y ?b ?d]))))

  (testing "全画家の生没年"
    (is (= '[[reynolds 1723 1792]
             [hogarth  1697 1772]
             [canale   1697 1768]]
           (compiler/with-answer-compile
             (and
               (painter ?x _ _)
               (dates ?x ?b ?d))
             [?x ?b ?d])))))


;; =====================================================
;; with-answer-compile — not
;; =====================================================

(deftest with-answer-compile-not-test
  (testing "誕生年がヴェネチア人と重複しない英国人画家"
    ;; hogarth（1697）は canale（1697）と重複 → 除外
    ;; reynolds（1723）は重複なし → 残る
    (is (= '[reynolds]
           (compiler/with-answer-compile
             (and
               (painter ?x _ 'english)
               (dates ?x ?b _)
               (not
                 (and
                   (painter ?x2 _ 'venetian)
                   (dates ?x2 ?b _))))
             ?x)))))


;; =====================================================
;; with-answer-compile — clj
;; =====================================================

(deftest with-answer-compile-clj-test
  (testing "没年が 1770 〜 1800 の画家（reynolds と hogarth）"
    (is (= '[(reynolds 1792) (hogarth 1772)]
           (compiler/with-answer-compile
             (and
               (painter ?x _ _)
               (dates ?x _ ?d)
               (clj (< 1770 ?d 1800)))
             (list ?x ?d)))))

  (testing "70 歳超えの画家（ローカル変数 n を参照）"
    ;; reynolds: 1792-1723 = 69 → 対象外
    ;; hogarth:  1772-1697 = 75 → 対象
    ;; canale:   1768-1697 = 71 → 対象
    (let [n 70]
      (is (= ["hogarth lived over 70 years."
              "canale lived over 70 years."]
             (compiler/with-answer-compile
               (and
                 (dates ?x ?b ?d)
                 (clj (> (- ?d ?b) n)))
               (format "%s lived over %d years." ?x n)))))))


;; =====================================================
;; with-answer-compile — or
;; =====================================================

(deftest with-answer-compile-or-test
  (testing "各画家の誕生年 OR 没年（gensym? チェックで未束縛変数を nil に）"
    ;; or 節ごとに1件ずつ、全画家分で合計6件
    (is (= '[[reynolds joshua 1723 nil]
             [reynolds joshua nil 1792]
             [hogarth  william 1697 nil]
             [hogarth  william nil 1772]
             [canale   antonio 1697 nil]
             [canale   antonio nil 1768]]
           (compiler/with-answer-compile
             (and
               (painter ?x ?y _)
               (or
                 (dates ?x ?b _)
                 (dates ?x _ ?d)))
             [?x ?y ?b ?d])))))

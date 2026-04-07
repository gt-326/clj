(ns onlisp.chap24.prolog.interpreter-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap24.prolog.interpreter :as pi]))


;; with-inference は各マッチでボディを実行し、全解消費後に [end] を返す。
;; テストでは (atom []) にマッチ結果を蓄積して検証する。
;;
;; *rlist* に以下のファクトを登録（conc1f による末尾追加 → 挿入順）:
;;
;;   painter: (canale antonio venetian)
;;            (hogarth william english)
;;            (reynolds joshua english)
;;   dates:   (canale 1697 1768)
;;            (hogarth 1697 1772)
;;            (reynolds 1723 1792)

(defn setup-facts
  []
  (reset! pi/*rlist* nil)
  (pi/<- (painter canale antonio venetian))
  (pi/<- (painter hogarth william english))
  (pi/<- (painter reynolds joshua english))
  (pi/<- (dates canale 1697 1768))
  (pi/<- (dates hogarth 1697 1772))
  (pi/<- (dates reynolds 1723 1792)))


(use-fixtures :each (fn [f] (setup-facts) (f)))


;; =====================================================
;; with-inference — 単一述語
;; =====================================================

(deftest with-inference-simple-test
  (testing "変数なし：hogarth にマッチ → 1件"
    (let [n (atom 0)]
      (pi/with-inference (painter hogarth william english)
                         (swap! n inc))
      (is (= 1 @n))))

  (testing "変数あり：英国人画家の名前（挿入順）"
    (let [results (atom [])]
      (pi/with-inference (painter ?x _ english)
                         (swap! results conj ?x))
      (is (= '[hogarth reynolds] @results))))

  (testing "変数あり：全画家の名前・姓・国籍"
    (let [results (atom [])]
      (pi/with-inference (painter ?x ?y ?z)
                         (swap! results conj [?x ?y ?z]))
      (is (= '[[canale antonio venetian]
               [hogarth william english]
               [reynolds joshua english]]
             @results))))

  (testing "マッチなし → 結果なし"
    (let [results (atom [])]
      (pi/with-inference (painter nobody ?x ?y)
                         (swap! results conj ?x))
      (is (= [] @results)))))


;; =====================================================
;; with-inference — and
;; =====================================================

(deftest with-inference-and-test
  (testing "painter と dates の結合（英国人のみ）"
    (let [results (atom [])]
      (pi/with-inference (and (painter ?x ?y english)
                              (dates ?x ?b ?d))
                         (swap! results conj [?x ?y ?b ?d]))
      (is (= '[[hogarth william 1697 1772]
               [reynolds joshua 1723 1792]]
             @results))))

  (testing "全画家の生没年"
    (let [results (atom [])]
      (pi/with-inference (and (painter ?x _ _)
                              (dates ?x ?b ?d))
                         (swap! results conj [?x ?b ?d]))
      (is (= '[[canale 1697 1768]
               [hogarth 1697 1772]
               [reynolds 1723 1792]]
             @results)))))


;; =====================================================
;; with-inference — or
;; =====================================================

(deftest with-inference-or-test
  (testing "or: 英国人またはヴェネチア人（全3画家）"
    ;; or の第1節で英国人2名、第2節でヴェネチア人1名
    (let [results (atom [])]
      (pi/with-inference (or (painter ?x _ english)
                             (painter ?x _ venetian))
                         (swap! results conj ?x))
      (is (= '[hogarth reynolds canale] @results))))

  (testing "and + or: 英国人で生年が1697か1723"
    (let [results (atom [])]
      (pi/with-inference (and (painter ?x _ english)
                              (or (dates ?x 1697 _)
                                  (dates ?x 1723 _)))
                         (swap! results conj ?x))
      (is (= '[hogarth reynolds] @results)))))


;; =====================================================
;; with-inference — not
;; =====================================================

(deftest with-inference-not-test
  (testing "1697年生まれでない英国人画家"
    ;; hogarth(1697) は除外、reynolds(1723) は通過
    (let [results (atom [])]
      (pi/with-inference (and (painter ?x _ english)
                              (dates ?x ?b _)
                              (not (dates ?x 1697 _)))
                         (swap! results conj ?x))
      (is (= '[reynolds] @results))))

  (testing "生年がヴェネチア人と重複しない英国人画家"
    ;; hogarth(1697) は canale(1697) と重複 → 除外
    ;; reynolds(1723) は重複なし → 残る
    (let [results (atom [])]
      (pi/with-inference (and (painter ?x _ english)
                              (dates ?x ?b _)
                              (not (and (painter ?x2 _ venetian)
                                        (dates ?x2 ?b _))))
                         (swap! results conj ?x))
      (is (= '[reynolds] @results)))))


;; =====================================================
;; with-inference — 本体付きルール
;; =====================================================

(deftest with-inference-rule-test
  (testing "単体ルール：英国人画家"
    ;; (<- head body) で 1節ルールを登録
    (pi/<- (english-painter ?x) (painter ?x _ english))
    (let [results (atom [])]
      (pi/with-inference (english-painter ?x)
                         (swap! results conj ?x))
      (is (= '[hogarth reynolds] @results))))

  (testing "複合ルール：生年が同じ画家のペア"
    ;; (<- head body1 body2 ...) で複数節ルールを登録（内部で and に変換）
    ;; canale(1697) と同じ生年: canale 自身 + hogarth(1697)
    (pi/<- (same-birth ?x ?y)
           (dates ?x ?b _)
           (dates ?y ?b _))
    (let [results (atom [])]
      (pi/with-inference (same-birth canale ?y)
                         (swap! results conj ?y))
      (is (= '[canale hogarth] @results)))))


;; =====================================================
;; with-inference — ドット対パターン (append)
;; =====================================================

(defn setup-append
  []
  (reset! pi/*rlist* nil)
  (pi/<- (append nil ?xs ?xs))
  (pi/<- (append (?x . ?xs) ?ys (?x . ?zs))
         (append ?xs ?ys ?zs)))


(deftest with-inference-append-test

  (testing "append: 左部分リストを求める（?x (c d) = (a b c d)）"
    (setup-append)
    (let [results (atom [])]
      (pi/with-inference (append ?x (c d) (a b c d))
                         (swap! results conj ?x))
      (is (= ['(a b)] @results))))

  (testing "append: 右部分リストを求める（(a b) ?x = (a b c d)）"
    (setup-append)
    (let [results (atom [])]
      (pi/with-inference (append (a b) ?x (a b c d))
                         (swap! results conj ?x))
      (is (= ['(c d)] @results))))

  (testing "append: 結合リストを求める（(a b) (c d) = ?x）"
    (setup-append)
    (let [results (atom [])]
      (pi/with-inference (append (a b) (c d) ?x)
                         (swap! results conj ?x))
      (is (= ['(a b c d)] @results))))

  (testing "append: 全分割を列挙（?x ?y = (a b c)）"
    (setup-append)
    (let [results (atom [])]
      (pi/with-inference (append ?x ?y (a b c))
                         (swap! results conj [?x ?y]))
      (is (= [[nil '(a b c)]
              ['(a) '(b c)]
              ['(a b) '(c)]
              ['(a b c) nil]]
             @results)))))


;; =====================================================
;; with-inference — ドット対パターン (member / first-a)
;; =====================================================

(defn setup-member
  []
  (reset! pi/*rlist* nil)
  (pi/<- (member ?x (?x . ?rest)))
  (pi/<- (member ?x (_ . ?rest))
         (member ?x ?rest))
  (pi/<- (first-a (a _))))


(deftest with-inference-member-test

  (testing "member: リスト先頭要素のマッチ（1件）"
    (setup-member)
    (let [n (atom 0)]
      (pi/with-inference (member a (a b))
                         (swap! n inc))
      (is (= 1 @n))))

  (testing "member + first-a: ?lst の推論"
    (setup-member)
    (let [results (atom [])]
      (pi/with-inference (and (first-a ?lst)
                              (member b ?lst))
                         (swap! results conj ?lst))
      (is (= ['(a b)] @results)))))

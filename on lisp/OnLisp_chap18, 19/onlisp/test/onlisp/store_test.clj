(ns onlisp.store-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.store :as store]))


;; 各テストは binding で *default-db* を新鮮な atom に差し替えて実行する。
;; db-push の 2-arity 版が db-query を *default-db* で呼ぶため、
;; binding によって両者が同じ atom を参照することを保証する。


;; =====================================================
;; make-db
;; =====================================================

(deftest make-db-test
  (testing "空のマップを返す"
    (is (= {} (store/make-db)))))


;; =====================================================
;; clear-db
;; =====================================================

(deftest clear-db-test
  (testing "データを追加した後に clear-db → DB が空になる"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (is (true? (:found (store/db-query 'painter))))
      (store/clear-db)
      (is (= {} @store/*default-db*))))

  (testing "clear-db は何度呼んでも空のまま"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/clear-db)
      (store/clear-db)
      (is (= {} @store/*default-db*)))))


;; =====================================================
;; db-query
;; =====================================================

(deftest db-query-test
  (testing "存在しないキー → :val nil, :found false"
    (binding [store/*default-db* (atom (store/make-db))]
      (let [result (store/db-query 'unknown-key)]
        (is (= nil (:val result)))
        (is (= false (:found result))))))

  (testing "存在するキー → :val にリスト, :found true"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (let [result (store/db-query 'painter)]
        (is (true? (:found result)))
        (is (coll? (:val result))))))

  (testing "キーが異なれば独立して管理される"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (is (true?  (:found (store/db-query 'painter))))
      (is (false? (:found (store/db-query 'dates)))))))


;; =====================================================
;; db-push
;; =====================================================

(deftest db-push-test
  (testing "1回 push → キーのリストに1要素"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (is (= '((canale antonio venetian))
             (:val (store/db-query 'painter))))))

  (testing "2回 push → 後から追加した値が先頭に来る（conj の prepend 挙動）"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (store/db-push 'painter '(hogarth william english))
      (is (= '((hogarth william english) (canale antonio venetian))
             (:val (store/db-query 'painter))))))

  (testing "3回 push → 逆順に積まれる"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (store/db-push 'painter '(hogarth william english))
      (store/db-push 'painter '(reynolds joshua english))
      (is (= '((reynolds joshua english)
               (hogarth william english)
               (canale antonio venetian))
             (:val (store/db-query 'painter))))))

  (testing "異なるキーへの push は独立している"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/db-push 'painter '(canale antonio venetian))
      (store/db-push 'dates   '(canale 1697 1768))
      (is (= '((canale antonio venetian))
             (:val (store/db-query 'painter))))
      (is (= '((canale 1697 1768))
             (:val (store/db-query 'dates)))))))


;; =====================================================
;; fact（マクロ）
;; =====================================================

(deftest fact-test
  (testing "fact は args のリストを返す"
    (binding [store/*default-db* (atom (store/make-db))]
      (is (= '(canale antonio venetian)
             (store/fact painter canale antonio venetian)))))

  (testing "fact を呼ぶと DB にエントリが追加される"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/fact painter canale antonio venetian)
      (is (true? (:found (store/db-query 'painter))))
      (is (= '((canale antonio venetian))
             (:val (store/db-query 'painter))))))

  (testing "複数の fact 呼び出しが蓄積される"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/fact painter canale  antonio venetian)
      (store/fact painter hogarth william english)
      (store/fact painter reynolds joshua  english)
      (is (= 3 (count (:val (store/db-query 'painter))))))))


;; =====================================================
;; gen-facts（初期データ投入）
;; =====================================================

(deftest gen-facts-test
  (testing "gen-facts 後に painter が3件登録される"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/gen-facts)
      (let [result (store/db-query 'painter)]
        (is (true? (:found result)))
        (is (= 3 (count (:val result)))))))

  (testing "gen-facts 後に dates が3件登録される"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/gen-facts)
      (let [result (store/db-query 'dates)]
        (is (true? (:found result)))
        (is (= 3 (count (:val result)))))))

  (testing "gen-facts 後の painter の内容（逆順）"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/gen-facts)
      (is (= '((reynolds joshua english)
               (hogarth william english)
               (canale antonio venetian))
             (:val (store/db-query 'painter))))))

  (testing "gen-facts 後の dates の内容（逆順）"
    (binding [store/*default-db* (atom (store/make-db))]
      (store/gen-facts)
      (is (= '((reynolds 1723 1792)
               (hogarth  1697 1772)
               (canale   1697 1768))
             (:val (store/db-query 'dates)))))))

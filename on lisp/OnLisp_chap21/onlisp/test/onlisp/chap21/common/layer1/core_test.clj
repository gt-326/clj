(ns onlisp.chap21.common.layer1.core-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [onlisp.chap21.common.layer1.core :as c]))


;; =====================================================
;; make-proc
;; =====================================================

(deftest make-proc-test
  (testing "make-proc はフィールドを正しく設定する"
    (let [p (c/make-proc :pri 5 :state str :wait nil)]
      (is (= 5   (:pri  p)))
      (is (= str (:state p)))
      (is (nil?  (:wait p)))))

  (testing ":wait を省略すると nil になる"
    (let [p (c/make-proc :pri 3 :state identity)]
      (is (nil? (:wait p)))))

  (testing "Proc レコードを返す"
    (let [p (c/make-proc :pri 1 :state str :wait nil)]
      (is (record? p)))))

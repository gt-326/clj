(ns reversi.board-test
  (:require [clojure.test :refer :all]
;;            [reversi.core :refer :all]
            [reversi.board :refer :all]))

(deftest board-test1
  (testing ""
    (is (= (board :disk) 1))))

(deftest board-test2
  (testing ""
    (is (= (board :stack) 2))))

(deftest board-test3
  (testing ""
    (is (= (board :sp) 3))))

(deftest update-disk-test
  (testing ""
    (is (= (assoc-in init_disk [0 1] 100)
           [[3 100 3 3 3 3 3 3 3] [3 0 0 0 0 0 0 0 0] [3 0 0 0 0 0 0 0 0] [3 0 0 0 0 0 0 0 0] [3 0 0 2 1 0 0 0 0] [3 0 0 1 2 0 0 0 0] [3 0 0 0 0 0 0 0 0] [3 0 0 0 0 0 0 0 0] [3 0 0 0 0 0 0 0 0] [3 3 3 3 3 3 3 3 3 3]]
           ))))

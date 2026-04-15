(ns onlisp.chap21.black-board-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap21.black-board :as b]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; BBOARD はグローバルな atom のため、
;; 各テスト前後に reset! でリセットする。

(defn reset-state!
  [f]
  (reset! b/BBOARD nil)
  (f)
  (reset! b/BBOARD nil))


(use-fixtures :each reset-state!)


;; =====================================================
;; claim
;; =====================================================
;;
;; fixture は deftest 単位でリセットするため、
;; 各 testing ブロックの先頭で BBOARD を明示的にリセットする。

(deftest claim-test
  (testing "claim は引数をひとまとまりのエントリとして BBOARD に追加する"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (is (= 1 (count @b/BBOARD))))

  (testing "異なる claim が蓄積される"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (b/claim 'open  'door1)
    (is (= 2 (count @b/BBOARD))))

  (testing "引数なしの claim は BBOARD を変更しない"
    (reset! b/BBOARD nil)
    (b/claim)
    (is (nil? @b/BBOARD))))


;; =====================================================
;; unclaim
;; =====================================================

(deftest unclaim-test
  (testing "unclaim は指定エントリを BBOARD から除去する"
    (reset! b/BBOARD nil)
    (b/claim   'knock 'door1)
    (b/unclaim 'knock 'door1)
    (is (nil? (seq @b/BBOARD))))

  (testing "unclaim は他のエントリを残す"
    (reset! b/BBOARD nil)
    (b/claim   'knock 'door1)
    (b/claim   'open  'door1)
    (b/unclaim 'knock 'door1)
    (is (= 1 (count @b/BBOARD))))

  (testing "存在しないエントリを unclaim しても BBOARD は壊れない"
    (reset! b/BBOARD nil)
    (b/claim   'knock 'door1)
    (b/unclaim 'open  'door1)   ; 存在しない
    (is (= 1 (count @b/BBOARD))))

  (testing "引数なしの unclaim は BBOARD を変更しない"
    (reset! b/BBOARD nil)
    (b/claim   'knock 'door1)
    (b/unclaim)
    (is (= 1 (count @b/BBOARD)))))


;; =====================================================
;; check
;; =====================================================

(deftest check-test
  (testing "BBOARD が空のとき nil を返す"
    (reset! b/BBOARD nil)
    (is (nil? (b/check 'knock 'door1))))

  (testing "マッチするエントリがないとき nil を返す"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (is (nil? (b/check 'open 'door1))))

  (testing "マッチするエントリがあるとき truthy な値を返す"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (is (b/check 'knock 'door1)))

  (testing "check の結果はマッチしたエントリのシーケンス（全マッチを返す）"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (b/claim 'knock 'door2)
    ;; door1 のみにマッチ → 1件
    (is (= 1 (count (b/check 'knock 'door1)))))

  (testing "引数なしの check は nil を返す"
    (reset! b/BBOARD nil)
    (b/claim 'knock 'door1)
    (is (nil? (b/check)))))

(ns onlisp.chap21.fnc-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap21.black-board     :as b]
    [onlisp.chap21.common.layer1.stat :as s]
    [onlisp.chap21.fnc :as mproc]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PROCS / PROC / BBOARD / OPEN-DOORS はグローバルな atom のため、
;; 各テスト前後に reset! でリセットする。
;;
;; ballet / barbarians はプログラム終了後に DEFAULT-PROC（stdin 待ち）に
;; 遷移するため、with-in-str で halt フォームを供給して停止させる。
;;
;; ballet / barbarians は通常 defn で定義されており、
;; (mproc/ballet) / (mproc/barbarians) で直接呼び出せる。

(defn reset-state!
  [f]
  (reset! s/PROCS           nil)
  (reset! s/PROC            nil)
  (reset! b/BBOARD          nil)
  (reset! mproc/OPEN-DOORS  nil)
  (f)
  (reset! s/PROCS           nil)
  (reset! s/PROC            nil)
  (reset! b/BBOARD          nil)
  (reset! mproc/OPEN-DOORS  nil))


(use-fixtures :each reset-state!)


;; =====================================================
;; ped / pedestrian
;; =====================================================
;;
;; pedestrian は OPEN-DOORS の先頭要素を待機条件とする。
;; OPEN-DOORS に扉があれば即座に入室メッセージを出力し、
;; 空なら stdin 待ちになる（halt で停止させる）。

(deftest ped-test
  (testing "OPEN-DOORS に扉があるとき入室メッセージを表示する"
    (reset! mproc/OPEN-DOORS ['door1])
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ped)))]
      (is (.contains output "Entering : door1"))))

  (testing "OPEN-DOORS が空のとき入室せず halt で停止する"
    (reset! mproc/OPEN-DOORS nil)
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ped)))]
      (is (not (.contains output "Entering"))))))


;; =====================================================
;; ballet （統合テスト）
;; =====================================================
;;
;; visitor と host が blackboard 経由で協調し、
;; 各扉で Approach → Open → Enter → Close の順に処理される。
;;
;; 扉間のスケジューリング順序は実行ごとに異なる場合があるが、
;; 同一扉内での処理順（Approach → Open → Enter → Close）は
;; blackboard の wait 条件により保証される。
;;
;; ballet 終了後の BBOARD には visitor が最後に claim した
;; ('inside door) エントリが残留する（unclaim されない）。

(deftest ballet-output-test
  (testing "door1 と door2 の Approach/Open/Enter/Close をすべて出力する"
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ballet)))]
      (is (.contains output "Approach"))
      (is (.contains output "Open"))
      (is (.contains output "Enter"))
      (is (.contains output "Close"))
      (is (.contains output "door1"))
      (is (.contains output "door2")))))


(deftest ballet-order-test
  (testing "door1: Approach は Open より先に出力される"
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ballet)))]
      (is (< (.indexOf output "Approach door1")
             (.indexOf output "Open door1")))))

  (testing "door1: Open は Enter より先に出力される"
    (reset! b/BBOARD nil)
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ballet)))]
      (is (< (.indexOf output "Open door1")
             (.indexOf output "Enter door1")))))

  (testing "door1: Enter は Close より先に出力される"
    (reset! b/BBOARD nil)
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ballet)))]
      (is (< (.indexOf output "Enter door1")
             (.indexOf output "Close door1")))))

  (testing "door2: Approach → Open → Enter → Close の順に出力される"
    (reset! b/BBOARD nil)
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/ballet)))]
      (is (< (.indexOf output "Approach door2")
             (.indexOf output "Open door2")
             (.indexOf output "Enter door2")
             (.indexOf output "Close door2"))))))


(deftest ballet-BBOARD-test
  (testing "ballet 終了後 BBOARD に残留エントリがある（inside は unclaim されない）"
    (with-out-str
      (with-in-str "(onlisp.chap21.common.layer2/halt)"
        (mproc/ballet)))
    ;; visitor は ('inside door) を claim して終了し、unclaim しない
    (is (some #(= (first %) 'inside) @b/BBOARD))))


;; =====================================================
;; barbarians （統合テスト）
;; =====================================================
;;
;; capture は Liberating → setpri(1) → yield → (再開) Rebuilding の順。
;; plunder は Nationalizing → Refinancing の順。
;;
;; capture は yield で優先度を 1000 → 1 に下げるため、
;; plunder（pri=980）が先に完了し、最終出力順は:
;;   Liberating → Nationalizing → Refinancing → Rebuilding

(deftest barbarians-output-test
  (testing "Liberating/Nationalizing/Refinancing/Rebuilding をすべて出力する"
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/barbarians)))]
      (is (.contains output "Liberating"))
      (is (.contains output "Nationalizing"))
      (is (.contains output "Refinancing"))
      (is (.contains output "Rebuilding")))))


(deftest barbarians-order-test
  (testing "出力順は Liberating → Nationalizing → Refinancing → Rebuilding"
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/barbarians)))]
      (is (< (.indexOf output "Liberating")
             (.indexOf output "Nationalizing")
             (.indexOf output "Refinancing")
             (.indexOf output "Rebuilding")))))

  (testing "setpri + yield により Rebuilding は Nationalizing より後に出力される"
    (let [output (with-out-str
                   (with-in-str "(onlisp.chap21.common.layer2/halt)"
                     (mproc/barbarians)))]
      (is (< (.indexOf output "Nationalizing")
             (.indexOf output "Rebuilding"))))))

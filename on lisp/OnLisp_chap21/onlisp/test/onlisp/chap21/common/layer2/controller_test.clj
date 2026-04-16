(ns onlisp.chap21.common.layer2-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap21.common.layer1 :as l1]
    [onlisp.chap21.common.layer2 :as l2]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PROCS / PROC はグローバルな atom のため、
;; 各テスト前後に reset! でリセットする。
;;
;; arbitrator / wait / yield は PROC が設定済みであることを前提とする。
;; テスト内では reset! l1/PROC で明示的に設定してから呼び出す。

(defn reset-state!
  [f]
  (reset! l1/PROCS nil)
  (reset! l1/PROC  nil)
  (f)
  (reset! l1/PROCS nil)
  (reset! l1/PROC  nil))


(use-fixtures :each reset-state!)


;; halt シグナルで停止させるヘルパー
(defn run-until-halt
  [thunk]
  (try
    (thunk)
    (catch clojure.lang.ExceptionInfo e
      (when-not (= (str l1/HALT) (.getMessage e))
        (throw e)))))


;; =====================================================
;; fork
;; =====================================================

(deftest fork-test
  (testing "fork は PROCS にプロセスを1件追加する"
    (reset! l1/PROCS nil)
    (l2/fork (+ 1 2) 5)
    (is (= 1 (count @l1/PROCS))))

  (testing "fork で登録したプロセスの :pri が正しい"
    (reset! l1/PROCS nil)
    (l2/fork (+ 1 2) 7)
    (is (= 7 (:pri (first @l1/PROCS)))))

  (testing "fork で登録したプロセスは :wait nil を持つ"
    (reset! l1/PROCS nil)
    (l2/fork (+ 1 2) 5)
    (is (nil? (:wait (first @l1/PROCS)))))

  (testing "fork を複数回呼ぶと PROCS に蓄積される"
    (reset! l1/PROCS nil)
    (l2/fork (+ 1 2) 1)
    (l2/fork (+ 3 4) 2)
    (l2/fork (+ 5 6) 3)
    (is (= 3 (count @l1/PROCS)))))


;; =====================================================
;; halt
;; =====================================================

(deftest halt-throws-test
  (testing "halt は ExceptionInfo を投げる"
    (is (thrown? clojure.lang.ExceptionInfo (l2/halt))))

  (testing "halt のメッセージは HALT の文字列表現"
    (try
      (l2/halt)
      (catch clojure.lang.ExceptionInfo e
        (is (= (str l1/HALT) (.getMessage e))))))

  (testing "halt は引数なしで {:val nil} を含む ExceptionInfo を投げる"
    (try
      (l2/halt)
      (catch clojure.lang.ExceptionInfo e
        (is (= {:val nil} (ex-data e))))))

  (testing "halt val は {:val val} を含む ExceptionInfo を投げる"
    (try
      (l2/halt :stop)
      (catch clojure.lang.ExceptionInfo e
        (is (= {:val :stop} (ex-data e)))))))


;; =====================================================
;; setpri
;; =====================================================

(deftest setpri-test
  (testing "setpri は PROC の :pri を変更する"
    (reset! l1/PROC (l1/make-proc :pri 5 :state str :wait nil))
    (l2/setpri 10)
    (is (= 10 (:pri @l1/PROC))))

  (testing "setpri は :state と :wait を変更しない"
    (reset! l1/PROC (l1/make-proc :pri 5 :state str :wait nil))
    (l2/setpri 99)
    (is (= str  (:state @l1/PROC)))
    (is (nil?   (:wait  @l1/PROC)))))


;; =====================================================
;; kill
;; =====================================================

(deftest kill-with-arg-test
  (testing "(kill obj) は指定プロセスを PROCS から除去する"
    (let [p1 (l1/make-proc :pri 1 :state str :wait nil)
          p2 (l1/make-proc :pri 2 :state str :wait nil)]
      (reset! l1/PROCS [p1 p2])
      (l2/kill p1)
      (is (not (some #{p1} @l1/PROCS)))
      (is (some #{p2} @l1/PROCS))))

  (testing "(kill obj) は nil を返す"
    (let [p (l1/make-proc :pri 1 :state str :wait nil)]
      (reset! l1/PROCS [p])
      (is (nil? (l2/kill p)))))

  (testing "(kill obj) は存在しないプロセスを渡しても PROCS を壊さない"
    (let [p1 (l1/make-proc :pri 1 :state str :wait nil)
          p2 (l1/make-proc :pri 2 :state str :wait nil)]
      (reset! l1/PROCS [p1])
      (l2/kill p2)                      ; p2 は PROCS にいない
      (is (= 1 (count @l1/PROCS))))))   ; p1 は残る


(deftest kill-no-arg-test
  (testing "(kill) は次のプロセスを実行する"
    (let [log (atom [])]
      (reset! l1/PROCS
              [(l1/make-proc :pri 5
                             :state (fn [_] (swap! log conj :executed) (l2/halt))
                             :wait nil)])
      (run-until-halt l2/kill)
      (is (= [:executed] @log)))))


;; =====================================================
;; arbitrator
;; =====================================================
;;
;; arbitrator は「現在プロセス（PROC）の継続を保存してキューに戻す」核心部分。
;;
;; テストの設計:
;;   PROC に初期プロセスを設定し、stopper（高優先度・halt）を PROCS に置く。
;;   arbitrator を呼ぶと:
;;     1. PROC の :wait と :state が更新される
;;     2. 更新された PROC が PROCS に追加される
;;     3. pick-process が呼ばれ → stopper が実行されて halt
;;   halt 後に PROCS を確認すると、arbitrator が追加したプロセスが残っている。

(deftest arbitrator-updates-proc-test
  (testing "arbitrator は PROC の :wait と :state を更新して PROCS に追加する"
    (let [new-wait (fn [] false)
          new-cont (fn [_] (l2/halt))
          stopper  (l1/make-proc :pri 99 :state (fn [_] (l2/halt)) :wait nil)]
      (reset! l1/PROC  (l1/make-proc :pri 5 :state str :wait nil))
      (reset! l1/PROCS [stopper])
      (run-until-halt #(l2/arbitrator new-wait new-cont))
      ;; stopper(pri=99) が先に実行されて halt
      ;; arbitrator が追加した PROC（pri=5, wait=new-wait）は PROCS に残る
      (is (some #(and (= new-wait (:wait %))
                      (= new-cont (:state %)))
                @l1/PROCS))))

  (testing "arbitrator 後、PROC の :wait と :state が指定した値に変わっている"
    (let [new-wait (fn [] true)
          new-cont (fn [_] (l2/halt))
          stopper  (l1/make-proc :pri 99 :state (fn [_] (l2/halt)) :wait nil)]
      (reset! l1/PROC  (l1/make-proc :pri 5 :state str :wait nil))
      (reset! l1/PROCS [stopper])
      ;; stopper が halt するより前に PROC を確認するため、
      ;; halt 後でも PROCS に残ったエントリで検証する
      (run-until-halt #(l2/arbitrator new-wait new-cont))
      (is (some #(= new-wait (:wait %)) @l1/PROCS)))))


;; =====================================================
;; wait
;; =====================================================
;;
;; wait の設計:
;;   (wait param test & body)
;;   → arbitrator に :wait=(fn[]test), :state=(fn[param] body) を渡す
;;
;; テスト1: 条件が true のプロセスは wait 後すぐに継続が実行される
;; テスト2: 条件が false のあいだはブロックし、true になると再開する
;;
;; テスト2 のプロセス構成:
;;   p1(pri=2): wait でフラグ待ち（最初は false）
;;   p2(pri=1): フラグを true にして pick-process へ → p1 が再開
;;
;; 実行順序:
;;   p1(pri=2) → wait → arbitrator → pick-process
;;   → p2(pri=1) 選択（p1 の :wait が false のため）
;;   → p2: flag=true, pick-process
;;   → p1 選択（:wait が true に変化）
;;   → p1 継続: :resumed をログ, halt

(deftest wait-passes-condition-result-test
  ;; wait の test 式は (fn [] test) で包まれるため、
  ;; test に関数リテラルを渡すと二重に包まれて関数オブジェクト自体が val になる。
  ;; test には「評価結果として param に渡したい値」を直接書く。
  (testing "wait は条件の評価結果を param に束縛して body を実行する"
    (let [received (atom :not-set)]
      (reset! l1/PROCS
              [(l1/make-proc :pri 5
                             :state (fn [_]
                                      (l2/wait result :condition-met
                                               (reset! received result)
                                               (l2/halt)))
                             :wait nil)])
      (run-until-halt l1/pick-process)
      (is (= :condition-met @received)))))


(deftest wait-blocks-until-condition-test
  (testing "wait は条件が false のあいだプロセスを待機させ、true になると再開する"
    (let [flag (atom false)
          log  (atom [])]
      (reset! l1/PROCS
              [;; p1(pri=2): flag が true になるまで待機
               (l1/make-proc :pri 2
                             :state (fn [_]
                                      (l2/wait _ @flag
                                               (swap! log conj :resumed)
                                               (l2/halt)))
                             :wait nil)
               ;; p2(pri=1): flag を true にして pick-process へ渡す
               (l1/make-proc :pri 1
                             :state (fn [_]
                                      (reset! flag true)
                                      (l1/pick-process))
                             :wait nil)])
      (run-until-halt l1/pick-process)
      ;; p1 が wait → p2 が flag=true → p1 が再開
      (is (= [:resumed] @log)))))


;; =====================================================
;; yield
;; =====================================================
;;
;; yield の設計:
;;   (yield & body)
;;   → arbitrator に :wait=nil, :state=(fn[x#] body) を渡す
;;
;; yield 単体では同じプロセスがすぐ再選択されるため（優先度不変）、
;; setpri で優先度を下げてから yield することで他プロセスに制御が移る。
;; これは barbarians 例（setpri 1 → yield）と同じパターン。
;;
;; 実行順序:
;;   p1(pri=5): :before ログ → setpri 1 → yield
;;   → arbitrator: p1 を pri=1 で再キュー → pick-process
;;   → p2(pri=3) 選択（pri=3 > pri=1）→ :p2 ログ → pick-process
;;   → p1(pri=1) 選択 → :after ログ → halt

(deftest yield-with-setpri-test
  (testing "setpri + yield で低優先度プロセスに実行権を渡し、後で再開する"
    (let [log (atom [])]
      (reset! l1/PROCS
              [(l1/make-proc :pri 5
                             :state (fn [_]
                                      (swap! log conj :p1-before)
                                      (l2/setpri 1)
                                      (l2/yield
                                        (swap! log conj :p1-after)
                                        (l2/halt)))
                             :wait nil)
               (l1/make-proc :pri 3
                             :state (fn [_]
                                      (swap! log conj :p2)
                                      (l1/pick-process))
                             :wait nil)])
      (run-until-halt l1/pick-process)
      (is (= [:p1-before :p2 :p1-after] @log)))))

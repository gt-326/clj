(ns onlisp.chap21.common.layer1.stat-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [onlisp.chap21.common.layer1.core :as c]
    [onlisp.chap21.common.layer1.stat :as s]
    [onlisp.chap21.common.layer2.controller :as con]))


;; =====================================================
;; セットアップ
;; =====================================================
;;
;; PROCS / PROC はグローバルな atom のため、
;; 各テスト前後に reset! でリセットする。
;;
;; pick-process は PROCS が空になると DEFAULT-PROC（stdin 待ち）に
;; 遷移するため、テスト内では state 関数の末尾で (con/halt) を呼び
;; ExceptionInfo を catch して停止させる。

(defn reset-state!
  [f]
  (reset! s/PROCS nil)
  (reset! s/PROC  nil)
  (f)
  (reset! s/PROCS nil)
  (reset! s/PROC  nil))


(use-fixtures :each reset-state!)


;; pick-process を halt シグナルで停止させるヘルパー
(defn run-until-halt
  [thunk]
  (try
    (thunk)
    (catch clojure.lang.ExceptionInfo e
      (when-not (= (str s/HALT) (.getMessage e))
        (throw e)))))


;; =====================================================
;; multiple-value-bind
;; =====================================================
;;
;; most-urgent-process は [proc val] を返す。
;; multiple-value-bind でこれを (p v) に分解するのが
;; pick-process 内での使われ方。

(deftest multiple-value-bind-test
  (testing "ベクタを複数変数に分解できる"
    (s/multiple-value-bind (a b) [10 20]
                           (is (= 10 a))
                           (is (= 20 b))))

  (testing "3要素も分解できる"
    (s/multiple-value-bind (x y z) [:foo :bar :baz]
                           (is (= :foo x))
                           (is (= :bar y))
                           (is (= :baz z))))

  (testing "most-urgent-process の [proc val] を分解できる"
    (let [p (c/make-proc :pri 5 :state str :wait nil)]
      (reset! s/PROCS [p])
      (s/multiple-value-bind (proc val) (s/most-urgent-process)
                             (is (= p proc))
                             ;; most-urgent-process は :wait nil のとき v = (or (not nil) ...) = true を返す
                             ;; pick-process が (when (:wait p) v) で nil に変換する
                             (is (true? val))))))


;; =====================================================
;; most-urgent-process
;; =====================================================

(deftest most-urgent-process-empty-test
  (testing "PROCS が空のとき DEFAULT-PROC を返す"
    (reset! s/PROCS nil)
    (let [[p v] (s/most-urgent-process)]
      (is (= s/DEFAULT-PROC p))
      (is (true? v)))))


(deftest most-urgent-process-priority-test
  (testing ":pri が最大のプロセスを選択する"
    (let [p1 (c/make-proc :pri 1 :state str :wait nil)
          p5 (c/make-proc :pri 5 :state str :wait nil)
          p3 (c/make-proc :pri 3 :state str :wait nil)]
      (reset! s/PROCS [p1 p5 p3])
      (let [[p _] (s/most-urgent-process)]
        (is (= 5 (:pri p))))))

  (testing ":pri が同じなら先頭のプロセスが選ばれる"
    (let [p-a (c/make-proc :pri 5 :state (fn [_] :a) :wait nil)
          p-b (c/make-proc :pri 5 :state (fn [_] :b) :wait nil)]
      (reset! s/PROCS [p-a p-b])
      (let [[p _] (s/most-urgent-process)]
        (is (= p-a p))))))


(deftest most-urgent-process-wait-test
  (testing ":wait fn が false を返すプロセスはスキップされる"
    (let [p-hi (c/make-proc :pri 10 :state str :wait (fn [] false))
          p-lo (c/make-proc :pri  3 :state str :wait nil)]
      (reset! s/PROCS [p-hi p-lo])
      (let [[p _] (s/most-urgent-process)]
        (is (= 3 (:pri p))))))

  (testing ":wait fn が nil を返すプロセスはスキップされる"
    (let [p-hi (c/make-proc :pri 10 :state str :wait (fn [] nil))
          p-lo (c/make-proc :pri  3 :state str :wait nil)]
      (reset! s/PROCS [p-hi p-lo])
      (let [[p _] (s/most-urgent-process)]
        (is (= 3 (:pri p))))))

  (testing ":wait fn が truthy な値を返すとき val にその値が入る"
    (let [p (c/make-proc :pri 5 :state str :wait (fn [] :condition-met))]
      (reset! s/PROCS [p])
      (let [[proc val] (s/most-urgent-process)]
        (is (= p proc))
        (is (= :condition-met val)))))

  (testing "全プロセスの :wait が falsy のとき DEFAULT-PROC を返す"
    (let [p (c/make-proc :pri 10 :state str :wait (fn [] false))]
      (reset! s/PROCS [p])
      (let [[proc _] (s/most-urgent-process)]
        (is (= s/DEFAULT-PROC proc))))))


;; =====================================================
;; pick-process
;; =====================================================
;;
;; pick-process は state 関数実行後に再帰的に pick-process を呼ぶ。
;; PROCS が空になると DEFAULT-PROC（stdin 待ち）に遷移するため、
;; state 関数の末尾で (con/halt) を呼んで停止させる。

(deftest pick-process-executes-test
  (testing "最高優先度のプロセスが実行される"
    (let [log (atom [])]
      (reset! s/PROCS
              [(c/make-proc :pri 3 :state (fn [_] (swap! log conj :p3) (con/halt)) :wait nil)
               (c/make-proc :pri 7 :state (fn [_] (swap! log conj :p7) (con/halt)) :wait nil)
               (c/make-proc :pri 1 :state (fn [_] (swap! log conj :p1) (con/halt)) :wait nil)])
      (run-until-halt s/pick-process)
      (is (= [:p7] @log))))

  (testing "実行後、そのプロセスが PROCS から除去される"
    (let [p (c/make-proc :pri 5 :state (fn [_] (con/halt)) :wait nil)]
      (reset! s/PROCS [p])
      (run-until-halt s/pick-process)
      (is (not (some #{p} @s/PROCS)))))

  (testing "実行後、PROC に実行したプロセスが設定される"
    (let [p (c/make-proc :pri 5 :state (fn [_] (con/halt)) :wait nil)]
      (reset! s/PROCS [p])
      (run-until-halt s/pick-process)
      (is (= p @s/PROC)))))


(deftest pick-process-val-test
  (testing ":wait nil のとき state に nil が渡る"
    (let [received (atom :not-set)]
      (reset! s/PROCS
              [(c/make-proc :pri 5
                            :state (fn [v] (reset! received v) (con/halt))
                            :wait nil)])
      (run-until-halt s/pick-process)
      (is (nil? @received))))

  (testing ":wait fn のとき state に条件の評価結果が渡る"
    (let [received (atom :not-set)]
      (reset! s/PROCS
              [(c/make-proc :pri 5
                            :state (fn [v] (reset! received v) (con/halt))
                            :wait (fn [] :condition-value))])
      (run-until-halt s/pick-process)
      (is (= :condition-value @received))))

  (testing ":wait fn が truthy なコレクションを返すとき、そのまま渡る"
    (let [received (atom :not-set)]
      (reset! s/PROCS
              [(c/make-proc :pri 5
                            :state (fn [v] (reset! received v) (con/halt))
                            :wait (fn [] '(knock door1)))])
      (run-until-halt s/pick-process)
      (is (= '(knock door1) @received)))))


(deftest pick-process-sequential-test
  ;; 協調マルチタスクの実行順序を確認する。
  ;; 各プロセスは末尾で (pick-process) を呼んで次へ渡し、
  ;; 最後のプロセスだけ (halt) で停止する。
  ;; halt 例外を途中で投げると後続プロセスが実行されないため、
  ;; チェーンの末尾のみ halt にする必要がある。
  (testing "複数プロセスが優先度順に逐次実行される"
    (let [log (atom [])]
      (reset! s/PROCS
              [(c/make-proc :pri 1
                            :state (fn [_]
                                     (swap! log conj :p1)
                                     (con/halt))        ; 末尾のみ halt
                            :wait nil)
               (c/make-proc :pri 3
                            :state (fn [_]
                                     (swap! log conj :p3)
                                     (s/pick-process)) ; 次のプロセスへ
                            :wait nil)
               (c/make-proc :pri 2
                            :state (fn [_]
                                     (swap! log conj :p2)
                                     (s/pick-process)) ; 次のプロセスへ
                            :wait nil)])
      (run-until-halt s/pick-process)
      ;; p3(pri=3) → p2(pri=2) → p1(pri=1, halt) の順
      (is (= [:p3 :p2 :p1] @log)))))

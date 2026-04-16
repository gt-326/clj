(ns onlisp.chap21.common.layer2.controller
  (:require
    [onlisp.chap21.common.layer1.core :as c]
    [onlisp.chap21.common.layer1.stat :as s]))


;; [ P283 chap21.1 ]


(defmacro fork
  [expr pri]
  `(let [expr# '~expr
         p# (c/make-proc
              :state (fn [& g#] (do ~expr (s/pick-process)))
              :pri   ~pri)]
     ;; 新しいプロセスが追加される
     (swap! s/PROCS conj p#)

     ;; この戻り値は、呼び出し元の program では活用されていない。
     ;; 確認したいときには、print を有効にする。
     ;; (println "pushed proc : " expr#)
     expr#))


(defn setpri
  [n]
  ;; 実行中のプロセスの優先度を変更する
  (swap! s/PROC assoc :pri n))


(defn halt
  ([]
   (halt nil))
  ([val]
   ;; プロセス中断用の例外を投げる
   (throw (ex-info (str s/HALT) {:val val}))))


(defn kill
  ;; kill は副作用のみを目的とする手続きであり、返り値は nil に統一する。
  ;; 原著 CL でも返り値は意図されておらず、呼び出し側が使うことを想定していない。
  ;; pick-process や swap! の戻り値を透過させると state 関数の型に依存した
  ;; 偶発的な値が漏れ出るため、kill 側で明示的に nil を返す。
  ([]
   (kill nil))
  ([obj]
   (if obj
     ;; 中断されているプロセスのリストから対象を除外する
     (swap! s/PROCS #(remove #{obj} %))
     (s/pick-process))
   nil))


;; ======================

(defn arbitrator
  [test cont]
  (do
    ;; 1回の swap! で両フィールドを更新
    (swap! s/PROC #(assoc % :wait test :state cont))
    ;; 実行中のプロセスを「中断されているプロセスのリスト」に追加する
    (swap! s/PROCS conj @s/PROC)
    (s/pick-process)))


(defmacro wait
  [param test & body]
  `(arbitrator
     ;; [arg1: test] -> :wait
     (fn []
       ;; テスト式が「真」を返すまで、その時点の処理が中断される。
       ~test)
     ;; [arg2: cont] -> :state
     (fn [~param] (do ~@body))))


(defmacro yield
  [& body]
  `(arbitrator
     nil
     (fn [x#] (do ~@body))))


;; ======================

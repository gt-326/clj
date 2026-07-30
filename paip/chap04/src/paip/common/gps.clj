(ns paip.common.gps
  (:require
    [clojure.pprint]
    [paip.common.gps-sub :as sub]
    [paip.common.utils :as utils]))


;; =================

(defrecord op
  [action preconds add-list del-list])


(defn make-op
  ([act pre adds] (make-op act pre adds '()))
  ([act pre adds dels]
   (sub/convert-op (->op act pre adds dels))))


(def ^:dynamic *ops*
  "現在使用可能なオペレータ群。gps_* 呼び出し中だけ binding で束縛され、
  呼び出しを抜けると（例外時も含めて）自動的に元へ戻る。
  my-use は root binding 自体を書き換えるので、次回以降 ops を省略した
  gps_* 呼び出しにも引き継がれる。"
  nil)


(defn my-use
  [oplist]
  (alter-var-root #'*ops* (constantly oplist))
  (count *ops*))


;; =================

(defn gps_2
  [printflg state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves
          (sub/achieve-all (cons '(start) state) goals nil *ops*)
          rslt
          (remove utils/cl-atom? achieves)]

      (println "\n[ result ]")

      (when printflg
        (clojure.pprint/pprint rslt))
      rslt)))


;; [ 4.13.2 ]

;; (defn action?
;;   [x]
;;   (or
;;     (= x '(start)) (executing? x)))


(defn gps_3
  [printflg state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves
          (sub/achieve-all (cons '(start) state) goals nil *ops*)
          rslt
          ;; 改修ポイント
          ;; (remove utils/cl-atom? achieves)
          (filter sub/action? achieves)]

      (println "\n[ result ]")

      (when printflg
        (clojure.pprint/pprint rslt))
      rslt)))


;; [ 4.13.3 ]

(defn gps_4
  [state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves
          (sub/achieve-all state goals nil *ops*)
          rslt
          ;; 改修ポイント
          ;; '(start) を抽出対象とする action? を使用していない
          ;; (filter action? achieves)
          (filter sub/executing? achieves)]

      (println "\n[ result ]")

      rslt)))


;; [ 4.14.2 ]

;; (defn orderings
;;   [s]
;;   (if (> (count s) 1)
;;     (list s (reverse s))
;;     (list s)))

;; (defn achieve-all2
;;   [state goals goal-stack ops]
;;   (some #(achieve-all state % goal-stack ops) (orderings goals)))


(defn gps_5
  [printflg state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves
          (sub/achieve-all2 (cons '(start) state) goals nil *ops*)
          rslt
          (filter sub/action? achieves)]

      (println "\n[ result ]")

      (when printflg
        (clojure.pprint/pprint rslt))

      rslt)))


;; [ 4.14.3 ]

(defn gps_6
  [printflg state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves
          ;; 改修ポイント
          ;; (sub/achieve-all2 (cons '(start) state) goals nil *ops*)
          (sub/achieve-all4 (cons '(start) state) goals nil *ops*)
          rslt
          (filter sub/action? achieves)]

      (println "\n[ result ]")

      (when printflg
        (clojure.pprint/pprint rslt))

      rslt)))

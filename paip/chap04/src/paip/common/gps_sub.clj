(ns paip.common.gps-sub
  (:require
    ;;   [clojure.pprint]
    [clojure.set :as s]
    [paip.common.utils :as utils]))


;; [ 4.11.1 ]

(defn starts-with
  [add-list x]
  (and
    (seq add-list)
    (= (first add-list) x)))


(defn executing?
  [add-list]
  (starts-with add-list 'executing))


(defn convert-op
  [op]
  (let [add-list (:add-list op)
        action (:action op)]
    (if (executing? add-list)
      op
      (assoc op :add-list (cons (list 'executing action) add-list)))))


;; (defrecord op
;;   [action preconds add-list del-list])


;; (defn make-op
;;   ([act pre adds] (make-op act pre adds '()))
;;   ([act pre adds dels]
;;    (convert-op (->op act pre adds dels))))


;; [ 4.11.2 ]

(defn member-equal
  [item lst]
  (when (seq lst)
    (if (= item (first lst))
      lst
      (recur item (rest lst)))))


;;  paip.core=> (v2/member-equal 1 '(0 2 4 6 1 3 5 7))
;;  (1 3 5 7)


(defn appropriate?
  [goal op]
  (member-equal goal (:add-list op)))


;;  paip.core=> (def a (v2/make-op2
;;                      'drive-son-to-school
;;                      '(son-at-home car-works)
;;                      '(0 2 4 6 1 3 5 7)
;;                      '(son-at-home)))
;;  #_=>        #_=>        #_=>        #_=> #'paip.core/a

;;  paip.core=> (v2/appropriate? 1 a)
;;  (1 3 5 7)

;;  paip.core=> (v2/appropriate? 10 a)
;;  nil


(declare achieve-all)


(defn apply-op
  [state goal op goal-stack ops]
  (let [action (:action op)
        preconds (:preconds op)
        add-list (:add-list op)
        del-list (:del-list op)

        cnt (count goal-stack)]

    (utils/dbg-indent cnt :gps "Consider: %s" action)

    (let [state2 (achieve-all state preconds (cons goal goal-stack) ops)]
      (when state2

        (utils/dbg-indent cnt :gps "Action: %s" action)

        (concat
          (remove #(member-equal % del-list) state2)
          add-list)))))


(defn achieve
  [state goal goal-stack ops]
  (let [cnt (count goal-stack)]

    (utils/dbg-indent cnt :gps "Goal: %s" goal)

    (cond
      (member-equal goal state) state
      (member-equal goal goal-stack) nil
      :else (some
              #(apply-op state goal % goal-stack ops)
              (filter #(appropriate? goal %) ops)))))


(defn achieve-all
  [state goals goal-stack ops]
  (let [current-state (atom state)
        ;; current-state-set (set state)
        flg1 (every?
               (fn [g]
                 (reset! current-state
                         (achieve @current-state g goal-stack ops)))
               goals)
        flg2 (s/subset? goals (set @current-state))]

    (when (and flg1 flg2)
      @current-state)))


;; (def ^:dynamic *ops*
;;   "現在使用可能なオペレータ群。gps_2 呼び出し中だけ binding で束縛され、
;;   呼び出しを抜けると（例外時も含めて）自動的に元へ戻る。
;;   my-use は root binding 自体を書き換えるので、次回以降 ops を省略した
;;   gps_2 呼び出しにも引き継がれる。"
;;   nil)
;;
;;
;; (defn my-use
;;   [oplist]
;;   (alter-var-root #'*ops* (constantly oplist))
;;   (count *ops*))


;; [ 4.13.2 ]

(defn action?
  [x]
  (or
    (= x '(start)) (executing? x)))


;; [ 4.14.2 ]

(defn orderings
  [s]
  (if (> (count s) 1)
    (list s (reverse s))
    (list s)))


(defn achieve-all2
  [state goals goal-stack ops]
  (some #(achieve-all state % goal-stack ops) (orderings goals)))


;; [ 4.14.3 ]

(def count-if (comp count filter))


;; 満たしている前提条件の数が多いオペレータから降順に並べる

(defn sorted-appropriate-ops
  [goal state ops]
  (let [copyed (filter #(appropriate? goal %) ops)
        fnc (fn [op]
              (count-if
                (fn [precond]
                  (not (member-equal precond state)))
                (:preconds op)))]
    (sort-by fnc < copyed)))


(defn achieve2
  [state goal goal-stack ops]
  (let [cnt (count goal-stack)]

    (utils/dbg-indent cnt :gps "Goal: %s" goal)

    (cond
      (member-equal goal state) state
      (member-equal goal goal-stack) nil
      :else (some
              #(apply-op state goal % goal-stack ops)
              (filter #(appropriate? goal %)
                      ;; 変更点
                      ;; ops
                      (sorted-appropriate-ops goal state ops))))))


(defn achieve-all3
  [state goals goal-stack ops]
  (let [current-state (atom state)
        flg1 (every?
               (fn [g]
                 (reset! current-state
                         (achieve2 @current-state g goal-stack ops)))
               goals)
        flg2 (s/subset? goals (set @current-state))]

    (when (and flg1 flg2)
      @current-state)))


(defn achieve-all4
  [state goals goal-stack ops]
  (some #(achieve-all3 state % goal-stack ops) (orderings goals)))

(ns paip.gps-ver02
  (:require
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


(defrecord op
  [action preconds add-list del-list])


(defn make-op2
  ([act pre adds] (make-op2 act pre adds '()))
  ([act pre adds dels]
   ;; 改修ポイント
   ;; (->op act pre adds dels)
   (convert-op (->op act pre adds dels))))


;; [ 4.11.2 ]

(def school-ops
  (list (make-op2
          'drive-son-to-school
          '(son-at-home car-works)
          '(son-at-school)
          '(son-at-home))

        (make-op2
          'shop-installs-battery
          '(car-needs-battery shop-knows-problem shop-has-money)
          '(car-works))

        (make-op2
          'tell-shop-problem
          '(in-communication-with-shop)
          '(shop-knows-problem))

        (make-op2
          'telephone-shop
          '(know-phone-number)
          '(in-communication-with-shop))

        (make-op2
          'look-up-number
          '(have-phone-book)
          '(know-phone-number))

        (make-op2
          'give-shop-money
          '(have-money)
          '(shop-has-money)
          '(have-money))

        ;; 追加分
        (make-op2
          'ask-phone-number
          '(in-communication-with-shop)
          '(know-phone-number))))


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

    ;; (println "flg:" flg1 flg2)
    ;; (println "flg2:" flg2 goals (set @current-state))

    (when (and flg1 flg2)
      @current-state)))


;; =======

(comment

  ;; paip.common.utils へ移動

  (defn cons?
    [x]
    (and (coll? x) (seqable? x) (seq x)))


  (defn cl-atom?
    [x]
    (not (cons? x)))

  ;; =======

  ;; achieve-all が例外を投げると、
  ;; gps_2_ 内部で「現在の状態を復元」できない、という事象が発生する。
  ;; ^:dynamic + binding で実装を変更（利便性維持、my-use は binding のラッパーとして存続）

  (def OPS (atom nil))


  (defn my-use_
    [oplist]
    (do
      (reset! OPS oplist)
      (count @OPS)))

  (defn gps_2_
    [state goals & ops]

    ;; 一旦、現在の状態を待避
    (let [current-ops @OPS]
      ;; デバッグ行をリセットする
      (reset! utils/line-no 0)

      ;; 指定された ops を適用する
      (reset! OPS (if (seq ops) (first ops) current-ops))

      (let [achieves (achieve-all (cons '(start) state) goals nil @OPS)
            rslt (remove utils/cl-atom? achieves)]

        ;; 現在の状態を復元
        (reset! OPS current-ops)

        (println "\n[ result ]")
        ;; (clojure.pprint/pprint rslt)
        rslt)))

  )


(def ^:dynamic *ops*
  "現在使用可能なオペレータ群。gps_2 呼び出し中だけ binding で束縛され、
  呼び出しを抜けると（例外時も含めて）自動的に元へ戻る。
  my-use は root binding 自体を書き換えるので、次回以降 ops を省略した
  gps_2 呼び出しにも引き継がれる。"
  nil)


(defn my-use
  [oplist]
  (alter-var-root #'*ops* (constantly oplist))
  (count *ops*))


(defn gps_2
  [state goals & ops]
  (binding [*ops* (if (seq ops) (first ops) *ops*)]
    ;; デバッグ行をリセットする
    (reset! utils/line-no 0)

    (let [achieves (achieve-all (cons '(start) state) goals nil *ops*)
          rslt (remove utils/cl-atom? achieves)]
      (println "\n[ result ]")
      ;; (clojure.pprint/pprint rslt)
      rslt)))


;; =======

;;  paip.core=> v2/*ops*
;;  nil

;;  paip.core=> (v2/my-use v2/school-ops)
;;  7

;;  paip.core=> (:add-list (first v2/*ops*))
;;  (executing drive-son-to-school son-at-school)

(comment

;;  paip.core=> (utils/debug :gps)
;;  #{:gps}

;;  (v2/my-use v2/school-ops)

  ;; case:1
  (gps_2
   '(son-at-home car-needs-battery have-money have-phone-book)
   '(son-at-school))

  ;; case:2 「シブリング目標を打ち消す」問題（4.7）
  (gps_2
   '(son-at-home car-needs-battery have-money have-phone-book)
   '(have-money son-at-school))

  ;; case:3 「調べることなく跳躍する」問題（4.8）
  (gps_2
   '(son-at-home car-needs-battery have-money have-phone-book)
   '(son-at-school have-money))

  ;; case:4 「再帰的な副目標」問題（4.9）
  (gps_2
   '(son-at-home car-needs-battery have-money)
   '(son-at-school))

  ;; case:5
  (gps_2
   '(son-at-home car-works)
   '(son-at-school))

  ;; case:6 「行動を要求しない」問題（4.11）
  (gps_2
   '(son-at-home)
   '(son-at-home))

;;  paip.core=> ;
;;  #{}

  )

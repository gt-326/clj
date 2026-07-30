(ns paip.gps-ver01
  (:require
    [clojure.java.io]
    [clojure.set]))


;; [ 4.1 ~ 4.5 ]


(defn appropriate?
  "引数 op の :add-list に goal が含まれているかどうかを判断する。
  それを以てオペレータが目標に適合するかを判定する。"
  [goal op]
  (contains? (:add-list op) goal))


(comment

  (def STATE
    "現在の状態：「条件」を保持している。"
    (atom nil))


  (def OPS
    "オペレータ群：適用可能なオペレータを保持している。"
    (atom nil))


  (declare achieve_)

  (defn apply-op_
    "引数 op の前提条件すべてに achive を適用し、すべての前提条件が満足するなら、
  オペレータを現在の状態に適用する。"
    [op]
    (when (every? achieve_ (:preconds op))
      (println (list "Executing: " (:action op)))
      (swap! STATE #(clojure.set/union
                     (clojure.set/difference % (:del-list op))
                     (:add-list op)))))

  (defn achieve_
    "１．「現在の状態」に、引数 goal が含まれているか？
  ２．各オペレータをフィルタリングした結果のうち、apply-op に該当するものがあるか？"
    [goal]
    (or (contains? @STATE goal)
        (some apply-op_ (filter #(appropriate? goal %) @OPS))))

  )


(defn apply-op
  [op ops state achieve-fnc]
  (when (every? #(achieve-fnc % ops state) (:preconds op))
    (println (list "Executing: " (:action op)))
    (swap! state #(clojure.set/union
                    (clojure.set/difference % (:del-list op))
                    (:add-list op)))))


(defn achieve
  [goal ops state]
  (or (contains? @state goal)
      (some #(apply-op % ops state achieve) (filter #(appropriate? goal %) ops))))


(defn gps
  [state goals ops]
  (let [state-atom (atom state)]
    (when (every? #(achieve % ops state-atom) goals)
      'SOLVED)))


(defrecord op
  [action preconds add-list del-list])


(defn make-op
  ([act pre adds] (make-op act pre adds nil))
  ([act pre adds dels]
   (->op act pre adds dels)))


(def school-ops
  (list (make-op
          'drive-son-to-school
          '(son-at-home car-works)
          #{'son-at-school}
          #{'son-at-home})

        (make-op
          'shop-installs-battery
          '(car-needs-battery shop-knows-problem shop-has-money)
          #{'car-works})

        (make-op
          'tell-shop-problem
          '(in-communication-with-shop)
          #{'shop-knows-problem})

        (make-op
          'telephone-shop
          '(know-phone-number)
          #{'in-communication-with-shop})

        (make-op
          'look-up-number
          '(have-phone-book)
          #{'know-phone-number})

        (make-op
          'give-shop-money
          '(have-money)
          #{'shop-has-money}
          #{'have-money})))


(comment

;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book}
;;               '(son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing  look-up-number)
;;  (Executing:  telephone-shop)
;;  (Executing:  tell-shop-problem)
;;  (Executing:  give-shop-money)
;;  (Executing:  shop-installs-battery)
;;  (Executing:  drive-son-to-school)
;;  SOLVED


;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-needs-battery 'have-money}
;;               '(son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> nil


;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-works}
;;               '(son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  drive-son-to-school)
;;  SOLVED

  )


;; [ 4.6 ]（have-money son-at-school、打ち消しなし）
;; [ 4.7 ]（gps、シブリング目標打ち消し）

(comment

;;  paip.core=> (v1/gps
;;               #{'son-at-home 'have-money 'car-works}
;;               '(have-money son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  drive-son-to-school)
;;  SOLVED


  ;; buggy

;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book}
;;               '(have-money son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  look-up-number)
;;  (Executing:  telephone-shop)
;;  (Executing:  tell-shop-problem)
;;  (Executing:  give-shop-money)
;;  (Executing:  shop-installs-battery)
;;  (Executing:  drive-son-to-school)
;;  SOLVED

  )


;; (defn achieve_
;;  [goal]
;;  (or (contains? @STATE goal)
;;      (some apply-op_ (filter #(appropriate? goal %) @OPS))))


;; (defn achieve
;;   [goal ops state]
;;   (or (contains? @state goal)
;;       (some #(apply-op % ops state achieve) (filter #(appropriate? goal %) ops))))


(defn achieve-all?
  [goals ops state-atom]
  (and
    (every? #(achieve % ops state-atom) goals)
    (every? #(contains? @state-atom %) goals)))


(defn gps_1.2
  [state goals ops]
  (let [state-atom (atom state)]
    (when (achieve-all? goals ops state-atom)
      'SOLVED)))


(comment

;; nil を返すようになった

;;  paip.core=> (v1/gps_1.2
;;               #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book}
;;               '(have-money son-at-school)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  look-up-number)
;;  (Executing:  telephone-shop)
;;  (Executing:  tell-shop-problem)
;;  (Executing:  give-shop-money)
;;  (Executing:  shop-installs-battery)
;;  (Executing:  drive-son-to-school)
;;  nil

  )


;; [ 4.8 ]（gps、gps_1.2 調べずに跳躍）

(comment

;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book}
;;               '(son-at-school have-money)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  look-up-number)
;;  (Executing:  telephone-shop)
;;  (Executing:  tell-shop-problem)
;;  (Executing:  give-shop-money)
;;  (Executing:  shop-installs-battery)
;;  (Executing:  drive-son-to-school)
;;  nil

;;  paip.core=> (v1/gps_1.2
;;               #{'son-at-home 'car-needs-battery 'have-money 'have-phone-book}
;;               '(son-at-school have-money)
;;               v1/school-ops)
;;  #_=>        #_=>        #_=> (Executing:  look-up-number)
;;  (Executing:  telephone-shop)
;;  (Executing:  tell-shop-problem)
;;  (Executing:  give-shop-money)
;;  (Executing:  shop-installs-battery)
;;  (Executing:  drive-son-to-school)
;;  nil

  )


;; [ 4.9 ]（循環オペレータ）

(comment

;;  paip.core=> (def school-ops2 (cons
;;                                (v1/make-op
;;                                 'ask-phone-number
;;                                 '(in-communication-with-shop)
;;                                 #{'know-phone-number})
;;                                v1/school-ops))
;;  #_=>        #_=>        #_=>        #_=>        #_=> #'paip.core/school-ops2

;;  paip.core=> (v1/gps
;;               #{'son-at-home 'car-needs-battery 'have-money}
;;               '(son-at-school)
;;               school-ops2)
;;  #_=>        #_=>        #_=> Execution error (StackOverflowError) at paip.gps-ver01/appropriate? (gps_ver01.clj:16).
;;  null

  )


;; [ 4.10 ]

(def dbg-ids (atom #{}))


(defn debug
  [& ids]
  (let [set_ids (set ids)]
    (reset! dbg-ids (clojure.set/union @dbg-ids set_ids))))


(defn undebug
  [& ids]
  (let [set_ids (set ids)]
    (when (seq set_ids)
      (reset! dbg-ids (clojure.set/difference @dbg-ids set_ids)))))


(comment

;;  paip.core=> @v1/dbg-ids
;;  #{}

;;  paip.core=> (v1/debug "id1" "id2")
;;  #{"id1" "id2"}

;;  paip.core=> @v1/dbg-ids
;;  #{"id1" "id2"}

;;  paip.core=> (v1/undebug)
;;  nil

;;  paip.core=> (v1/undebug "id3")
;;  #{"id1" "id2"}

;;  paip.core=> @v1/dbg-ids
;;  #{"id1" "id2"}

;;  paip.core=> (v1/undebug "id1")
;;  #{"id2"}

;;  paip.core=> @v1/dbg-ids
;;  #{"id2"}

  )


(defn dbg
  [id format-str & args]
  (when (contains? @dbg-ids id)
    (println)
    (println (apply format format-str args))))


(defmacro dbg-indent
  [cnt id format-str & args]
  `(let [indent# ~(apply str
                         (concat
                           (repeat cnt " ")
                           (list format-str)))]
     (dbg ~id indent# ~@args)))


(comment

;;  paip.core=> (v1/dbg-indent 10 "id1" "format: %s %s %s" 1 2 3)
;;
;;  format: 1 2 3
;;  nil


  (defn dbg_
    [id format-str & args]
    (with-open [debug-writer (clojure.java.io/writer "debug.log")]
      (binding [*out* debug-writer]
        (when (contains? @dbg-ids id)
          (println)
          (println (apply format format-str args))))))


  (defn dbg-indent_
    [cnt id format-str & args]
    (with-open [debug-writer (clojure.java.io/writer "debug.log")]
      (binding [*out* debug-writer]
        (when (contains? @dbg-ids id)
          (println)
          (dotimes [_ cnt] (print " "))
          (println (apply format format-str args))))))

  )

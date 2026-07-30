(ns paip.maze
  (:require
    [paip.common.gps :as gps]
    [paip.common.utils :as utils]))


;; [ 4.13.1 ]

(defn make-m-op
  [here there]
  (gps/make-op
    `(~'move ~'from ~here ~'to ~there)
    `((~'at ~here))
    `((~'at ~there))
    `((~'at ~here))))


;; (maze/make-m-op 1 25)
;; #paip.common.gps.op{:action (move from 1 to 25), :preconds ((at 1)), :add-list ((at 25)), :del-list ((at 1))}


(defn make-maze-ops
  [[here there]]
  (list (make-m-op here there)
        (make-m-op there here)))


;; (mapcat maze/make-maze-ops '([1 2] [2 3]))
;; (#paip.common.gps.op{:action (move from 1 to 2), :preconds ((at 1)), :add-list ((at 2)), :del-list ((at 1))} #paip.common.gps.op{:action (move from 2 to 1), :preconds ((at 2)), :add-list ((at 1)), :del-list ((at 2))} #paip.common.gps.op{:action (move from 2 to 3), :preconds ((at 2)), :add-list ((at 3)), :del-list ((at 2))} #paip.common.gps.op{:action (move from 3 to 2), :preconds ((at 3)), :add-list ((at 2)), :del-list ((at 3))})

;; (clojure.pprint/pprint (mapcat maze/make-maze-ops '([1 2] [2 3])))
;; ({:action (move from 1 to 2),
;;   :preconds ((at 1)),
;;   :add-list ((at 2)),
;;   :del-list ((at 1))}
;;  {:action (move from 2 to 1),
;;   :preconds ((at 2)),
;;   :add-list ((at 1)),
;;   :del-list ((at 2))}
;;  {:action (move from 2 to 3),
;;   :preconds ((at 2)),
;;   :add-list ((at 3)),
;;   :del-list ((at 2))}
;;  {:action (move from 3 to 2),
;;   :preconds ((at 3)),
;;   :add-list ((at 2)),
;;   :del-list ((at 3))})
;; nil


(def maze-ops
  (mapcat
    make-maze-ops
    '([1 2] [2 3] [3 4] [4 9] [9 14]
            [9 8] [8 7] [7 12] [12 13] [12 11]
            [11 6] [11 16] [16 17] [17 22] [21 22]
            [22 23] [23 18] [23 24] [24 19] [19 20]
            [20 15] [15 10] [10 5] [20 25])))


(comment

  (paip.common.gps/my-use paip.maze/maze-ops)
  ;;   48

  (paip.common.gps/gps_2 false
                         '((at 1))
                         '((at 25)))
  ;;   #_=>
  ;;   [ result ]
  ;;   ((start) (executing (move from 1 to 2)) (executing (move from 2 to 3)) (executing (move from 3 to 4)) (executing (move from 4 to 9)) (executing (move from 9 to 8)) (executing (move from 8 to 7)) (executing (move from 7 to 12)) (executing (move from 12 to 11)) (executing (move from 11 to 16)) (executing (move from 16 to 17)) (executing (move from 17 to 22)) (executing (move from 22 to 23)) (executing (move from 23 to 24)) (executing (move from 24 to 19)) (executing (move from 19 to 20)) (executing (move from 20 to 25))

  ;;   (at 25))  <--- 「start と executing の形式以外に、
  ;;                   ある n に対して形式（at n）が含まれている」

  )


;; [ 4.13.2 ]

;; gps_2 -> gps_3 に変更することで、上記の「微妙なバグ」を解消している。

(comment

  (utils/debug :gps)
  ;;#{:gps}

  (paip.common.gps/gps_3 false
                          '((at 1))
                          '((at 25)))
  ;;  #_=>
  ;;  [ debug ]
  ;;  1 : Goal: (at 25)
  ;;  2 : Consider: (move from 20 to 25)
  ;;  3 :  Goal: (at 20)
  ;;  4 :  Consider: (move from 19 to 20)
  ;;  5 :   Goal: (at 19)
  ;;  6 :   Consider: (move from 24 to 19)
  ;;  7 :    Goal: (at 24)
  ;;  8 :    Consider: (move from 23 to 24)
  ;;  9 :     Goal: (at 23)
  ;; 10 :     Consider: (move from 22 to 23)
  ;; 11 :      Goal: (at 22)
  ;; 12 :      Consider: (move from 17 to 22)
  ;; 13 :       Goal: (at 17)
  ;; 14 :       Consider: (move from 16 to 17)
  ;; 15 :        Goal: (at 16)
  ;; 16 :        Consider: (move from 11 to 16)
  ;; 17 :         Goal: (at 11)
  ;; 18 :         Consider: (move from 12 to 11)
  ;; 19 :          Goal: (at 12)
  ;; 20 :          Consider: (move from 7 to 12)
  ;; 21 :           Goal: (at 7)
  ;; 22 :           Consider: (move from 8 to 7)
  ;; 23 :            Goal: (at 8)
  ;; 24 :            Consider: (move from 9 to 8)
  ;; 25 :             Goal: (at 9)
  ;; 26 :             Consider: (move from 4 to 9)
  ;; 27 :              Goal: (at 4)
  ;; 28 :              Consider: (move from 3 to 4)
  ;; 29 :               Goal: (at 3)
  ;; 30 :               Consider: (move from 2 to 3)
  ;; 31 :                Goal: (at 2)
  ;; 32 :                Consider: (move from 1 to 2)
  ;; 33 :                 Goal: (at 1)
  ;; 34 :                Action: (move from 1 to 2)
  ;; 35 :               Action: (move from 2 to 3)
  ;; 36 :              Action: (move from 3 to 4)
  ;; 37 :             Action: (move from 4 to 9)
  ;; 38 :            Action: (move from 9 to 8)
  ;; 39 :           Action: (move from 8 to 7)
  ;; 40 :          Action: (move from 7 to 12)
  ;; 41 :         Action: (move from 12 to 11)
  ;; 42 :        Action: (move from 11 to 16)
  ;; 43 :       Action: (move from 16 to 17)
  ;; 44 :      Action: (move from 17 to 22)
  ;; 45 :     Action: (move from 22 to 23)
  ;; 46 :    Action: (move from 23 to 24)
  ;; 47 :   Action: (move from 24 to 19)
  ;; 48 :  Action: (move from 19 to 20)
  ;; 49 : Action: (move from 20 to 25)
  ;;
  ;;[ result ]
  ;;((start) (executing (move from 1 to 2)) (executing (move from 2 to 3)) (executing (  move from 3 to 4)) (executing (move from 4 to 9)) (executing (move from 9 to 8)) (executing (move from 8 to 7)) (executing (move from 7 to 12)) (executing (move from 12 to 11)) (executing (move from 11 to 16)) (executing (move from 16 to 17)) (executing (move from 17 to 22)) (executing (move from 22 to 23)) (executing (move from 23 to 24)) (executing (move from 24 to 19)) (executing (move from 19 to 20)) (executing (move from 20 to 25)))

  )


;; [ 4.13.3 ]

;; gps_3 -> （gps_4 を利用する）find-path に変更している。
;; 挙動が変わらないことを確認するためのもの。



(defn destination
  "Find the Y in (executing (move from X to Y))"
  [[_ [_ _ _ _ a]]]
  a)


;; (destination '(executing (move from 20 to 25)))
;; #_=> 25

(defn find-path
  [start end]
  (let [rslts (gps/gps_4 `((~'at ~start)) `((~'at ~end)))]
    (if (= start end)
      ;; 実行例のひとつ、開始点と終了点が同じケースへの対応したもの
      (list start)
      (when (seq rslts)
        (cons
          start
          (map
            destination
            ;; 原著では、ここで '(start) を削除しているが、
            ;; gps_4 で '(start) を対象に含めていないので、除去する必要もなし。
            ;; (remove #(= '(start) %) rslts)
            rslts))))))


(comment


  (paip.common.gps/my-use paip.maze/maze-ops)
  ;;   48

  (paip.maze/find-path 1 25)
  ;;
  ;;   [ result ]
  ;;   (1 2 3 4 9 8 7 12 11 16 17 22 23 24 19 20 25)

  (paip.maze/find-path 1 1)
  ;;
  ;;   [ result ]
  ;;   (1)

  (= (paip.maze/find-path 1 25) (reverse (paip.maze/find-path 25 1)))
  ;;
  ;;   [ result ]
  ;;
  ;;   [ result ]
  ;;   true


  )

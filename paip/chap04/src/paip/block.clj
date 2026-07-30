(ns paip.block
  (:require
    [paip.common.gps :as gps]
    [paip.common.utils :as utils]))


;; [ 4.14.1 ]

(defn move-ons
  [a b c]
  (if (= b 'table)
    `((~a ~'on ~c))
    `((~a ~'on ~c) (~'space ~'on ~b))))


;; paip.core=> (block/move-ons 1 2 3)
;; ((1 on 3) (space on 2))

;; paip.core=> (block/move-ons 1 'table 3)
;; ((1 on 3))


(defn move-op
  [a b c]
  (gps/make-op
    `(~'move ~a ~'from ~b ~'to ~c)
    `((~'space ~'on ~a) (~'space ~'on ~c) (~a ~'on ~b))
    (move-ons a b c)
    (move-ons a c b)))


;; paip.core=> (block/move-op 1 2 3)
;; #paip.common.gps.op{:action (move 1 from 2 to 3), :preconds ((space on 1) (space on 3) (1 on 2)), :add-list ((executing (move 1 from 2 to 3)) (1 on 3) (space on 2)), :del-list ((1 on 2) (space on 3))}

;; paip.core=> (block/move-op 1 'table 3)
;; #paip.common.gps.op{:action (move 1 from table to 3), :preconds ((space on 1) (space on 3) (1 on table)), :add-list ((executing (move 1 from table to 3)) (1 on 3)), :del-list ((1 on table) (space on 3))}


(defn make-block-ops
  [blocks]
  (let [ops (atom '())]
    (doseq [a blocks]
      (doseq [b blocks]
        (when (not (= a b))
          (doseq [c blocks]
            (when (not (or (= c a) (= c b)))
              (swap! ops conj (move-op a b c))))
          (swap! ops conj (move-op a 'table b))
          (swap! ops conj (move-op a b 'table)))))
    @ops))


(comment

  (paip.common.gps/my-use (paip.block/make-block-ops '(a b)))
  ;; 4

  (utils/debug :gps)
  (utils/undebug)

  (paip.common.gps/gps_3 false
                          '((a on table) (b on table) (space on a) (space on b) (space on table))
                          '((a on b) (b on table)))

  ;;  #_=>
  ;;  [ result ]
  ;;  ((start) (executing (move a from table to b)))

  (paip.common.gps/gps_3 true
   '((a on b) (b on table) (space on a) (space on table))
   '((b on a)))

  ;;  #_=>
  ;;  [ result ]
  ;;  ((start)
  ;;   (executing (move a from b to table))
  ;;   (executing (move b from table to a)))
  ;;  ((start) (executing (move a from b to table)) (executing (move b from table to a)))

  )


;; [ 4.14.2 ]


(comment

  (paip.common.gps/my-use (paip.block/make-block-ops '(a b c)))
  ;; 18

  (paip.common.gps/gps_3 false
            '((a on b) (b on c) (c on table) (space on a) (space on table))
            '((b on a) (c on b)))

  ;;  #_=>
  ;; [ result ]
  ;; ((start)
  ;;  (executing (move a from b to table))
  ;;  (executing (move b from c to a))
  ;;  (executing (move c from table to b)))

  ;; error : 「シブリング目標を打ち消す」状況が発生している。

  (paip.common.gps/gps_3 false
            '((a on b) (b on c) (c on table) (space on a) (space on table))
            '((c on b) (b on a)))

  ;;  #_=>
  ;; [ result ]
  ;; ()

  ;; （新しい achieve-all2 を使用している）gps/gps_5 を使用する

  (paip.common.gps/gps_5 false
            '((c on a) (a on table) (b on table) (space on c)
              (space on b) (space on table))
            '((c on table)))

  ;; [ result ]
  ;; ((start)
  ;;  (executing (move c from a to b))
  ;;  (executing (move c from b to table)))

  ;; 「シブリング目標を打ち消す」状況を回避している。

  (paip.common.gps/gps_5
   false
            '((c on a) (a on table) (b on table) (space on c)
              (space on b) (space on table))
            '((c on table) (a on b)))

  ;; [ result ]
  ;; ((start)
  ;;  (executing (move c from a to b))
  ;;  (executing (move c from b to table))
  ;;  (executing (move a from table to c))
  ;;  (executing (move a from c to b)))

  )


;; [ 4.14.3 ]


(comment

  (paip.common.gps/my-use (paip.block/make-block-ops '(a b c)))
  ;; 18

  ;; 「短い手続きを発見する」実装

  (paip.common.gps/gps_6 false
            '((c on a) (a on table) (b on table) (space on c)
              (space on b) (space on table))
            '((c on table) (a on b)))

  ;; [ result ]
  ;; ((start)
  ;;  (executing (move c from a to table))
  ;;  (executing (move a from table to b)))


  (paip.common.gps/gps_6 false
            '((a on b) (b on c) (c on table) (space on a) (space on table))
            '((b on a) (c on b)))

  ;; [ result ]
  ;; ((start)
  ;;  (executing (move a from b to table))
  ;;  (executing (move b from c to a))
  ;;  (executing (move c from table to b)))


  ;; ゴール状態を変更しても、結果に影響しないことを確認できた。

  (paip.common.gps/gps_6 false
             '((a on b) (b on c) (c on table) (space on a) (space on table))
             '((c on b) (b on a)))  ;; <----------changed


  ;; [ result ]
  ;; ((start)
  ;;  (executing (move a from b to table))
  ;;  (executing (move b from c to a))
  ;;  (executing (move c from table to b)))


  ;; Sussman のアノマリー
  ;; 目標の順序を入れ変えても解決出来ない問題がある、という例

  (def start '((c on a) (a on table) (b on table) (space on c)
               (space on b) (space on table)))


  (utils/debug :gps)
  (utils/undebug)

  (paip.common.gps/gps_6 false start '((a on b) (b on c)))

  ;; [ debug ]
  ;;  1 : Goal: (a on b)
  ;;  2 : Consider: (move a from table to b)
  ;;  3 :  Goal: (space on a)
  ;;  4 :  Consider: (move c from a to table)
  ;;  5 :   Goal: (space on c)
  ;;  6 :   Goal: (space on table)
  ;;  7 :   Goal: (c on a)
  ;;  8 :  Action: (move c from a to table)
  ;;  9 :  Goal: (space on b)
  ;; 10 :  Goal: (a on table)
  ;; 11 : Action: (move a from table to b)
  ;; 12 : Goal: (b on c)
  ;; 13 : Consider: (move b from table to c)
  ;; 14 :  Goal: (space on b)
  ;; 15 :  Consider: (move c from b to table)
  ;; 16 :   Goal: (space on c)
  ;; 17 :   Goal: (space on table)
  ;; 18 :   Goal: (c on b)
  ;; 19 :   Consider: (move c from table to b)
  ;; 20 :    Goal: (space on c)
  ;; 21 :    Goal: (space on b)
  ;; 22 :   Consider: (move c from a to b)
  ;; 23 :    Goal: (space on c)
  ;; 24 :    Goal: (space on b)
  ;; 25 :  Consider: (move c from b to a)
  ;; 26 :   Goal: (space on c)
  ;; 27 :   Goal: (space on a)
  ;; 28 :   Goal: (c on b)
  ;; 29 :   Consider: (move c from table to b)
  ;; 30 :    Goal: (space on c)
  ;; 31 :    Goal: (space on b)
  ;; 32 :   Consider: (move c from a to b)
  ;; 33 :    Goal: (space on c)
  ;; 34 :    Goal: (space on b)
  ;; 35 :  Consider: (move a from b to table)
  ;; 36 :   Goal: (space on a)
  ;; 37 :   Goal: (space on table)
  ;; 38 :   Goal: (a on b)
  ;; 39 :  Action: (move a from b to table)
  ;; 40 :  Goal: (space on c)
  ;; 41 :  Goal: (b on table)
  ;; 42 : Action: (move b from table to c)
  ;; 43 : Goal: (b on c)
  ;; 44 : Consider: (move b from table to c)
  ;; 45 :  Goal: (space on b)
  ;; 46 :  Goal: (space on c)
  ;; 47 :  Goal: (b on table)
  ;; 48 : Action: (move b from table to c)
  ;; 49 : Goal: (a on b)
  ;; 50 : Consider: (move a from table to b)
  ;; 51 :  Goal: (space on a)
  ;; 52 :  Consider: (move c from a to table)
  ;; 53 :   Goal: (space on c)
  ;; 54 :   Consider: (move b from c to table)
  ;; 55 :    Goal: (space on b)
  ;; 56 :    Goal: (space on table)
  ;; 57 :    Goal: (b on c)
  ;; 58 :   Action: (move b from c to table)
  ;; 59 :   Goal: (space on table)
  ;; 60 :   Goal: (c on a)
  ;; 61 :  Action: (move c from a to table)
  ;; 62 :  Goal: (space on b)
  ;; 63 :  Goal: (a on table)
  ;; 64 : Action: (move a from table to b)
  ;;
  ;;[ result ]
  ;;()


  (paip.common.gps/gps_6 false start '((b on c) (a on b)))

  ;;  [ debug ]
  ;;  1 : Goal: (b on c)
  ;;  2 : Consider: (move b from table to c)
  ;;  3 :  Goal: (space on b)
  ;;  4 :  Goal: (space on c)
  ;;  5 :  Goal: (b on table)
  ;;  6 : Action: (move b from table to c)
  ;;  7 : Goal: (a on b)
  ;;  8 : Consider: (move a from table to b)
  ;;  9 :  Goal: (space on a)
  ;; 10 :  Consider: (move c from a to table)
  ;; 11 :   Goal: (space on c)
  ;; 12 :   Consider: (move b from c to table)
  ;; 13 :    Goal: (space on b)
  ;; 14 :    Goal: (space on table)
  ;; 15 :    Goal: (b on c)
  ;; 16 :   Action: (move b from c to table)
  ;; 17 :   Goal: (space on table)
  ;; 18 :   Goal: (c on a)
  ;; 19 :  Action: (move c from a to table)
  ;; 20 :  Goal: (space on b)
  ;; 21 :  Goal: (a on table)
  ;; 22 : Action: (move a from table to b)
  ;; 23 : Goal: (a on b)
  ;; 24 : Consider: (move a from table to b)
  ;; 25 :  Goal: (space on a)
  ;; 26 :  Consider: (move c from a to table)
  ;; 27 :   Goal: (space on c)
  ;; 28 :   Goal: (space on table)
  ;; 29 :   Goal: (c on a)
  ;; 30 :  Action: (move c from a to table)
  ;; 31 :  Goal: (space on b)
  ;; 32 :  Goal: (a on table)
  ;; 33 : Action: (move a from table to b)
  ;; 34 : Goal: (b on c)
  ;; 35 : Consider: (move b from table to c)
  ;; 36 :  Goal: (space on b)
  ;; 37 :  Consider: (move c from b to table)
  ;; 38 :   Goal: (space on c)
  ;; 39 :   Goal: (space on table)
  ;; 40 :   Goal: (c on b)
  ;; 41 :   Consider: (move c from table to b)
  ;; 42 :    Goal: (space on c)
  ;; 43 :    Goal: (space on b)
  ;; 44 :   Consider: (move c from a to b)
  ;; 45 :    Goal: (space on c)
  ;; 46 :    Goal: (space on b)
  ;; 47 :  Consider: (move c from b to a)
  ;; 48 :   Goal: (space on c)
  ;; 49 :   Goal: (space on a)
  ;; 50 :   Goal: (c on b)
  ;; 51 :   Consider: (move c from table to b)
  ;; 52 :    Goal: (space on c)
  ;; 53 :    Goal: (space on b)
  ;; 54 :   Consider: (move c from a to b)
  ;; 55 :    Goal: (space on c)
  ;; 56 :    Goal: (space on b)
  ;; 57 :  Consider: (move a from b to table)
  ;; 58 :   Goal: (space on a)
  ;; 59 :   Goal: (space on table)
  ;; 60 :   Goal: (a on b)
  ;; 61 :  Action: (move a from b to table)
  ;; 62 :  Goal: (space on c)
  ;; 63 :  Goal: (b on table)
  ;; 64 : Action: (move b from table to c)
  ;;
  ;;[ result ]
  ;;()

  )

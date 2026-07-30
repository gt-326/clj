(ns paip.banana
  (:require
    [paip.common.gps :as gps]))


;; [ 4.12 ]

(def banana-ops
  (list (gps/make-op
          'climb-on-chair
          '(chair-at-middle-room at-middle-room on-floor)
          '(at-bananas on-chair)
          '(chair-at-middle-room on-floor))

        (gps/make-op
          'push-chair-from-door-to-middle-room
          '(chair-at-door at-door)
          '(chair-at-middle-room at-middle-room)
          '(chair-at-door at-door))

        (gps/make-op
          'walk-from-door-to-middle-room
          '(at-door on-floor)
          '(at-middle-room)
          '(at-door))

        (gps/make-op
          'grasp-bananas
          '(at-bananas empty-handed)
          '(has-bananas)
          '(empty-handed))

        (gps/make-op
          'drop-ball
          '(has-ball)
          '(empty-handed)
          '(has-ball))

        (gps/make-op
          'eat-bananas
          '(has-bananas)
          '(empty-handed not-hungry)
          '(has-bananas hungry))))


(comment

  (paip.common.gps/my-use banana/banana-ops)
  ;;   6

  (utils/debug :gps)
  ;;   #{:gps}

  (paip.common.gps/gps_2 false
                         '(at-door on-floor has-ball hungry chair-at-door)
                         '(not-hungry))
  ;;   #_=>
  ;;   [ debug ]
  ;;   1 : Goal: not-hungry
  ;;   2 : Consider: eat-bananas
  ;;   3 :  Goal: has-bananas
  ;;   4 :  Consider: grasp-bananas
  ;;   5 :   Goal: at-bananas
  ;;   6 :   Consider: climb-on-chair
  ;;   7 :    Goal: chair-at-middle-room
  ;;   8 :    Consider: push-chair-from-door-to-middle-room
  ;;   9 :     Goal: chair-at-door
  ;;   10 :     Goal: at-door
  ;;   11 :    Action: push-chair-from-door-to-middle-room
  ;;   12 :    Goal: at-middle-room
  ;;   13 :    Goal: on-floor
  ;;   14 :   Action: climb-on-chair
  ;;   15 :   Goal: empty-handed
  ;;   16 :   Consider: drop-ball
  ;;   17 :    Goal: has-ball
  ;;   18 :   Action: drop-ball
  ;;   19 :  Action: grasp-bananas
  ;;   20 : Action: eat-bananas
  ;;
  ;;   [ result ]
  ;;   ((start) (executing push-chair-from-door-to-middle-room) (executing climb-on-chair) (executing drop-ball) (executing grasp-bananas) (executing eat-bananas))

)

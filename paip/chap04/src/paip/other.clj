(ns paip.other
  (:require
    [paip.common.gps :as gps]
    [paip.common.utils :as utils]))


;; [ 4.16 ]

(def school-ops
  (list
    ;; 追加分
    (gps/make-op
      'taxi-son-to-school
      '(son-at-home have-money)
      '(son-at-school)
      '(son-at-home have-money))

    (gps/make-op
      'drive-son-to-school
      '(son-at-home car-works)
      '(son-at-school)
      '(son-at-home))

    (gps/make-op
      'shop-installs-battery
      '(car-needs-battery shop-knows-problem shop-has-money)
      '(car-works))

    (gps/make-op
      'tell-shop-problem
      '(in-communication-with-shop)
      '(shop-knows-problem))

    (gps/make-op
      'telephone-shop
      '(know-phone-number)
      '(in-communication-with-shop))

    (gps/make-op
      'look-up-number
      '(have-phone-book)
      '(know-phone-number))

    (gps/make-op
      'give-shop-money
      '(have-money)
      '(shop-has-money)
      '(have-money))

    (gps/make-op
      'ask-phone-number
      '(in-communication-with-shop)
      '(know-phone-number))))


;; 「跳躍もしなければ調べもしない」問題

(comment

  (paip.common.gps/my-use paip.other/school-ops)
  ;; 8

  (utils/debug :gps)
  ;; #{:gps}

  (paip.common.gps/gps_6 false
                          '(son-at-home have-money car-works)
                          '(son-at-school have-money))

  ;;  [ debug ]
  ;;  1 : Goal: son-at-school
  ;;  2 : Consider: taxi-son-to-school
  ;;  3 :  Goal: son-at-home
  ;;  4 :  Goal: have-money
  ;;  5 : Action: taxi-son-to-school
  ;;  6 : Goal: have-money
  ;;  7 : Goal: have-money
  ;;  8 : Goal: son-at-school
  ;;  9 : Consider: taxi-son-to-school
  ;;  10 :  Goal: son-at-home
  ;;  11 :  Goal: have-money
  ;;  12 : Action: taxi-son-to-school
  ;;
  ;;  ;;  [ result ]
  ;;  ()


  (paip.common.gps/gps_6 false
                          '(son-at-home have-money car-works)
                          '(have-money son-at-school))

  ;;  [ debug ]
  ;;  1 : Goal: have-money
  ;;  2 : Goal: son-at-school
  ;;  3 : Consider: taxi-son-to-school
  ;;  4 :  Goal: son-at-home
  ;;  5 :  Goal: have-money
  ;;  6 : Action: taxi-son-to-school
  ;;  7 : Goal: son-at-school
  ;;  8 : Consider: taxi-son-to-school
  ;;  9 :  Goal: son-at-home
  ;;  10 :  Goal: have-money
  ;;  11 : Action: taxi-son-to-school
  ;;  12 : Goal: have-money
  ;;
  ;;  [ result ]
  ;;  ()

  )

(ns onlisp.chap21.fnc
  (:require
    [onlisp.chap21.black-board :as b]
    [onlisp.chap21.common.layer2.controller :as c]
    [onlisp.chap21.common.layer2.generator :as g]
    [onlisp.chap21.warring-state-period :as w]))


;; ======================

;; P287

(def OPEN-DOORS (atom nil))


(defn pedestrian
  []
  (c/wait
    d                        ; param
    (first @OPEN-DOORS)      ; test
    (println "Entering :" d) ; & body
    ))


(g/program ped ()
           (c/fork (pedestrian) 1))


;; ======================

;; P288

(defn visitor
  [door]
  (do
    ;; [step1]
    (print "\nApproach" door ". ")
    ;; BB_1_0 : nil
    ;; BB_2_0 : ((inside door1))

    (b/claim 'knock door)
    ;; BB_1_1 : ((knock door1))
    ;; BB_2_1 : ((knock door2) (inside door1))

    (c/wait
      ;; param
      d

      ;; test
      ;; ※ リストの先頭が open になったら、body を実行する（BB_1_4, BB_2_4）
      (b/check 'open door)

      ;; & body =================

      ;; [step3]
      (print "Enter" door ". ")
      ;; BB_1_5 : ((open door1) (knock door1))
      ;; BB_2_5 : ((open door2) (knock door2) (inside door1))

      (b/unclaim 'knock door)
      ;; BB_1_6 : ((open door1))
      ;; BB_2_6 : ((open door2) (inside door1))

      (b/claim 'inside door)
      ;; BB_1_7 : ((inside door1) (open door1))
      ;; BB_2_7 : ((inside door2) (open door2) (inside door1))

      ;; =========================
      )))


(defn host
  [door]
  (do
    (c/wait
      ;; param1
      k

      ;; test1
      ;;  ※ リストの先頭が knock になったら、body1 を実行する（BB_1_1, BB_2_1）
      (b/check 'knock door)

      ;; & body1 ===================

      ;; [step2]
      (print "Open" door ". ")
      ;; BB_1_3 : ((knock door1))
      ;; BB_2_3 : ((knock door2) (inside door1))

      (b/claim 'open door)
      ;; BB_1_4 : ((open door1) (knock door1))
      ;; BB_2_4 : ((open door2) (knock door2) (inside door1))

      (c/wait
        ;; param2
        g

        ;; test2
        ;; ※ リストの先頭が inside になったら、body2 を実行する（BB_1_7, BB_2_7）
        (b/check 'inside door)

        ;; & body2 -----------------

        ;; [step4]
        (print "Close" door ". ")
        ;; BB_1_8 : ((inside door1) (open door1))
        ;; BB_2_8 : ((inside door2) (open door2) (inside door1))

        (b/unclaim 'open door)
        ;; BB_1_9 : ((inside door1)) -> BB_2_0 へ
        ;; BB_2_9 : ((inside door2) (inside door1))

        ;; -------------------------
        ;; =========================
        ))))


(g/program ballet ()
           (c/fork (visitor 'door1) 4)
           (c/fork (host 'door1) 3)
           (c/fork (visitor 'door2) 2)
           (c/fork (host 'door2) 1))


;; onlisp.core=> (fnc/ballet)

;; Approach door1 . Open door1 . Enter door1 . Close door1 .
;; Approach door2 . Open door2 . Enter door2 . Close door2 .

;; [ REPL multi-process ]>>


;; ======================

;; P289

;; 制圧する
(defn capture
  [city]
  (do
    (w/my-take city)
    ;; pri: 1000 -> 1
    (c/setpri 1)
    (c/yield
      (w/fortify city))))


;; 略奪する
(defn plunder
  [city]
  (do
    (w/loot city)
    (w/ransom city)))


(g/program barbarians ()
           (c/fork (capture 'TOKYO) 1000)
           (c/fork (plunder 'TOKYO) 980))


;; ======================

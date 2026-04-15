(ns onlisp.chap21.fnc
  (:require
    [onlisp.chap21.black-board :as b]
    [onlisp.chap21.common.layer2 :as l2]
    [onlisp.chap21.common.layer3 :as l3]))


;; ======================

;; P287

(def OPEN-DOORS (atom nil))


(defn pedestrian
  []
  (l2/wait d (first @OPEN-DOORS)
           (println "Entering :" d)))


(l3/program ped ()
            (l2/fork (pedestrian) 1))


;; ======================

;; P288

(defn visitor
  [door]
  (do
    (print "\nApproach" door ". ")
    (b/claim 'knock door)
    (l2/wait d (b/check 'open door)
             (print "Enter" door ". ")
             (b/unclaim 'knock door)
             (b/claim 'inside door))))


(defn host
  [door]
  (do
    (l2/wait k (b/check 'knock door)
             (print "Open" door ". ")
             (b/claim 'open door)
             (l2/wait g (b/check 'inside door)

                      (print "Close" door ".")
                      (b/unclaim 'open door)))))


(l3/program ballet ()
            (l2/fork (visitor 'door1) 1)
            (l2/fork (host 'door1) 1)

            (l2/fork (visitor 'door2) 1)
            (l2/fork (host 'door2) 1))


;; ======================

;; P289

(defn capture
  [city]
  (do
    (b/my-take city)
    ;; pri: 100 -> 1
    (l2/setpri 1)
    (l2/yield
      (b/fortify city))))


(defn plunder
  [city]
  (do
    (b/loot city)
    (b/ransom city)))


(l3/program barbarians ()
            (l2/fork (capture 'TOKYO) 1000)
            (l2/fork (plunder 'TOKYO) 980))


;; ======================

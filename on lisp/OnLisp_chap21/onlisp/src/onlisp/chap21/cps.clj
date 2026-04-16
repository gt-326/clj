(ns onlisp.chap21.cps
  (:require
    [onlisp.chap21.black-board :as b]
    [onlisp.chap21.common.layer2.controller :as c]
    [onlisp.chap21.common.layer2.generator :as g]
    [onlisp.chap21.warring-state-period :as w]
    [onlisp.common.util :as u]))


;; ======================

;; P287

(def OPEN-DOORS (atom nil))


(u/=defn pedestrian
         []
         (c/wait d (first @OPEN-DOORS)
                 (println "Entering :" d)))


(g/program-cps ped ()
               (c/fork (pedestrian) 1))


;; ======================

;; P288

(u/=defn visitor
         [door]
         (do
           (print "\nApproach" door ". ")
           (b/claim 'knock door)
           (c/wait d (b/check 'open door)
                   (print "Enter" door ". ")
                   (b/unclaim 'knock door)
                   (b/claim 'inside door))))


(u/=defn host
         [door]
         (do
           (c/wait k (b/check 'knock door)
                   (print "Open" door ". ")
                   (b/claim 'open door)
                   (c/wait g (b/check 'inside door)

                           (print "Close" door ".")
                           (b/unclaim 'open door)))))


(g/program-cps ballet ()
               (c/fork (visitor 'door1) 1)
               (c/fork (host 'door1) 1)

               (c/fork (visitor 'door2) 1)
               (c/fork (host 'door2) 1))


;; ======================

;; P289

(u/=defn capture
         [city]
         (do
           (w/my-take city)
           ;; pri: 100 -> 1
           (c/setpri 1)
           (c/yield
             (w/fortify city))))


(u/=defn plunder
         [city]
         (do
           (w/loot city)
           (w/ransom city)))


(g/program-cps barbarians ()
               (c/fork (capture 'ROME) 100)
               (c/fork (plunder 'ROME) 98))


;; ======================

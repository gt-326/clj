(ns onlisp.core
  (:require
    [onlisp.chap21.common.layer2 :as l2]
    [onlisp.chap21.cps :as cps]
    [onlisp.chap21.fnc :as fnc]))


(comment

  ;;  onlisp.core=> (fnc/ped)

  ;;  [ REPL multi-process ]>> (swap! fnc/OPEN-DOORS conj 'door2)
  ;;  (door2)
  ;;  Entering : door2

  ;;  [ REPL multi-process ]>> (swap! fnc/OPEN-DOORS conj 'door3)
  ;;  (door3 door2)

  ;;  [ REPL multi-process ]>> (l2/halt "aiueo")
  ;;  "[On Lisp] [chap.21 multi process] system halt: {:val \"aiueo\"}"

  )


(comment

  ;;  onlisp.core=> (cps/ballet)

  ;;  Approach door2 . Open door2 . Enter door2 . Close door2 .
  ;;  Approach door1 . Open door1 . Enter door1 . Close door1 .
  ;;  [ REPL multi-process ]>> (l2/halt)
  ;;  "[On Lisp] [chap.21 multi process] system halt: {:val nil}"

  )


(comment

  ;;  onlisp.core=> (cps/barbarians)
  ;;  Liberating ROME.
  ;;  Nationalizing ROME.
  ;;  Refinancing ROME.
  ;;  Rebuilding ROME.

  ;;  [ REPL multi-process ]>> (l2/halt "sashisuseso")
  ;;  "[On Lisp] [chap.21 multi process] system halt: {:val \"sashisuseso\"}"

  )

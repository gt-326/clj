(ns marubatsu.core
  (:require [marubatsu.computer :as com])
  (:use [marubatsu.board]))

;; (play 0) -- computer vs human
;; (play 1) -- computer vs computer

(let [init_board [\0 \0 \0 \0 \0 \0 \0 \0 \0]]
  (defn play
    ([] (play 0))
    ([mode]
     (marubatsu-repl init_board mode))))



(def turn [\1 \2])
(def init_board [\0 \0 \0 \0 \0 \0 \0 \0 \0])
(def board [(com/think2 init_board \1)
            (com/think2 init_board \2)])

(defn play2
  ([] (play2 0))
  ([mode]
   (let [turn_int (rand-int 2)]

     (marubatsu-repl2 (board turn_int) mode (turn turn_int))
     )))

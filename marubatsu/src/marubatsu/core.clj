(ns marubatsu.core
  (:use [marubatsu.board]))

;; (play 0) -- computer vs human
;; (play 1) -- computer vs computer

(let [init_board [\0 \0 \0 \0 \0 \0 \0 \0 \0]]
  (defn play
    ([] (play 0))
    ([mode]
     (marubatsu-repl init_board mode))))

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

(def board2 [(com/think3 init_board \1)
            (com/think3 init_board \2)])

(defn play2
  ([] (play2 0))
  ([mode]
   (let [turn_int (rand-int 2)]

     (marubatsu-repl2 (board turn_int) mode (turn turn_int))
     )))

(defn play3
  ([] (play3 0))
  ([mode]
   (let [turn_int (rand-int 2)]

     (marubatsu-repl3 (board2 turn_int) mode (turn turn_int))
     )))

;;==========================

(defn gen-board [n] (vec (repeat (* n n) \0)))

(defn gen-win-pattern [n]
  (concat
   ;; yoko
   (partition n (range (* n n)))

   ;; tate
   (for [i (range n)]
     (map #(+ i %) (range 0 (* n n) n)))

   ;; naname
   (list
    (range 0 (* n n) (inc n))
    (range (dec n) (dec (* n n)) (dec n)))
   ))

(defn play4
  ([] (play4 {}))
  ([{mode :mode, size :size, :or {mode 0, size 3}}]

   (if (not (contains? (set (range 3 26 2)) size))
     ;; error
     (println
      (str "error: \n"
           "valid sizes are " (vec (range 3 26 2))))

     (let [win-pttrns (gen-win-pattern size)
           init-board (gen-board size)
           turn-char ([\1 \2] (rand-int 2))
           board (com/think4 win-pttrns init-board turn-char)]

       (marubatsu-repl4
        win-pttrns
        board
        mode
        turn-char
        size)))
))

;;==========================

;;aaa
;; 新ルール「一定時間経過すると、石が消滅する」を導入してみる

(defn play5 [] (str "play5"))

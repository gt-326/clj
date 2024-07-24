(ns reversi.core
  (:use [reversi.board])
;;  (:require [reversi.board])

;; error
;;  (:refer [reversi.board])
  )

(defn foo2
  "I don't do a whole lot."
  [x]
  ;;(println x "Hello, World!")

  (str x " " WALL " " WALL)
  ;;(str x reversi.board/WALL reversi.board/WALL)
  )

(defn print_board [board]
  (let [cnt (atom -1)]

    ;; CUI print board
    (print
     (str
      ;; 列
      "    A B C D E F G H \n"
      (apply str
             (for [line board]
               (do
                 (reset! cnt (inc @cnt))
                 (str
                  ;; 行　0, 9 のときには行番号を表示しない
                  (if (contains? #{0 9} @cnt) " " @cnt)
                  (apply str
                         (map #(condp = %
                                 BLACK " O"
                                 WHITE " X"
                                 EMPTY " ."
                                 " #") line))
                  "\n"))))))
    ))

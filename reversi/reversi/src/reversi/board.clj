(ns reversi.board)

;;=============

;; 盤面の大きさ
(def BOARD_SIZE 8)

;;=============

;; マスの状態
(def EMPTY 0)  ;; .
(def BLACK 1)  ;; O
(def WHITE 2)  ;; X
(def WALL  3)  ;; #

;; #########
;; #........
;; #........
;; #........
;; #...XO...
;; #...OX...
;; #........
;; #........
;; #........
;; ##########

;; 91 個
(def NUM_DISK
  (inc
   (* (+ 1 BOARD_SIZE)
      (+ 2 BOARD_SIZE))))

;;=============

;; Stackは、返した石を覚えておくためのものです。
;; 石を返すときには以下の情報をこの順番に格納していきます。

　;; 返した石の位置１
　;; 返した石の位置２
　;; ...
　;; 返した石の位置ｎ

　;; 着手位置
　;; 相手の色
　;; 返した石の数

;; 逆に１手戻すときには、この情報を逆から読み込んでいきます。
;; 相手の色を格納しておくのは、１手戻すときには返した石が相手の色になるからです。

;; 1344 個
(def NUM_STACK
  (*
   (* BOARD_SIZE BOARD_SIZE)

   (+ 3
      (* 3 (- BOARD_SIZE 2)))))

;;=============

;; struct _Board
;; {
;;  int Disk[NUM_DISK];
;;  int Stack[NUM_STACK];
;;  int *Sp;
;;  };

(def board
  {:disk 1
   :stack 2
   :sp 3})

(def board2
  {:disk [[ 3, 3, 3, 3, 3, 3, 3, 3, 3 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 0, 0, 0, 2, 1, 0, 0, 0 ]
          [ 3, 0, 0, 0, 1, 2, 0, 0, 0 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
          [ 3, 3, 3, 3, 3, 3, 3, 3, 3, 3 ]]
   :stack 2
   :sp 3})

(def init_disk
  [[ 3, 3, 3, 3, 3, 3, 3, 3, 3 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 2, 1, 0, 0, 0 ]
   [ 3, 0, 0, 0, 1, 2, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 3, 3, 3, 3, 3, 3, 3, 3, 3 ]])

(def init_disk2
  [[ 3, 3, 3, 3, 3, 3, 3, 3, 3 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 1, 0, 0, 0 ]
   [ 3, 0, 0, 0, 2, 0, 2, 0, 0 ]
   [ 3, 0, 0, 0, 2, 1, 0, 0, 0 ]
   [ 3, 0, 0, 0, 1, 2, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 3, 3, 3, 3, 3, 3, 3, 3, 3 ]])

;;=============

;; マスの位置

(def PASS -1)
(def NOMOVE -2)

(def A1 10) ;; [1 1]
(def B1 11) ;; [1 2]
(def C1 12) ;; [1 3]
(def D1 13) ;; [1 4]
(def E1 14) ;; [1 5]
(def F1 15) ;; [1 6]
(def G1 16) ;; [1 7]
(def H1 17) ;; [1 8]

(def A2 19) ;; [2 1]
(def B2 20) ;; [2 2]
(def C2 21) ;; [2 3]
(def D2 22) ;; [2 4]
(def E2 23) ;; [2 5]
(def F2 24) ;; [2 6]
(def G2 25) ;; [2 7]
(def H2 26) ;; [2 8]

(def A3 28) ;; [3 1]
(def B3 29) ;; [3 2]
(def C3 30) ;; [3 3]
(def D3 31) ;; [3 4]
(def E3 32) ;; [3 5]
(def F3 33) ;; [3 6]
(def G3 34) ;; [3 7]
(def H3 35) ;; [3 8]

(def A4 37) ;; [4 1]
(def B4 38) ;; [4 2]
(def C4 39) ;; [4 3]
(def D4 40) ;; [4 4]
(def E4 41) ;; [4 5]
(def F4 42) ;; [4 6]
(def G4 43) ;; [4 7]
(def H4 44) ;; [4 8]

(def A5 46) ;; [5 1]
(def B5 47) ;; [5 2]
(def C5 48) ;; [5 3]
(def D5 49) ;; [5 4]
(def E5 50) ;; [5 5]
(def F5 51) ;; [5 6]
(def G5 52) ;; [5 7]
(def H5 53) ;; [5 8]

(def A6 55) ;; [6 1]
(def B6 56) ;; [6 2]
(def C6 57) ;; [6 3]
(def D6 58) ;; [6 4]
(def E6 59) ;; [6 5]
(def F6 60) ;; [6 6]
(def G6 61) ;; [6 7]
(def H6 62) ;; [6 8]

(def A7 64) ;; [7 1]
(def B7 65) ;; [7 2]
(def C7 66) ;; [7 3]
(def D7 67) ;; [7 4]
(def E7 68) ;; [7 5]
(def F7 69) ;; [7 6]
(def G7 70) ;; [7 7]
(def H7 71) ;; [7 8]

(def A8 73) ;; [8 1]
(def B8 74) ;; [8 2]
(def C8 75) ;; [8 3]
(def D8 76) ;; [8 4]
(def E8 77) ;; [8 5]
(def F8 78) ;; [8 6]
(def G8 79) ;; [8 7]
(def H8 80) ;; [8 8]

(def POS {
          "a1" [1 1]
          "b1" [1 2]
          "c1" [1 3]
          "d1" [1 4]
          "e1" [1 5]
          "f1" [1 6]
          "g1" [1 7]
          "h1" [1 8]

          "a2" [2 1]
          "b2" [2 2]
          "c2" [2 3]
          "d2" [2 4]
          "e2" [2 5]
          "f2" [2 6]
          "g2" [2 7]
          "h2" [2 8]

          "a3" [3 1]
          "b3" [3 2]
          "c3" [3 3]
          "d3" [3 4]
          "e3" [3 5]
          "f3" [3 6]
          "g3" [3 7]
          "h3" [3 8]

          "a4" [4 1]
          "b4" [4 2]
          "c4" [4 3]
          "d4" [4 4]
          "e4" [4 5]
          "f4" [4 6]
          "g4" [4 7]
          "h4" [4 8]

          "a5" [5 1]
          "b5" [5 2]
          "c5" [5 3]
          "d5" [5 4]
          "e5" [5 5]
          "f5" [5 6]
          "g5" [5 7]
          "h5" [5 8]

          "a6" [6 1]
          "b6" [6 2]
          "c6" [6 3]
          "d6" [6 4]
          "e6" [6 5]
          "f6" [6 6]
          "g6" [6 7]
          "h6" [6 8]

          "a7" [7 1]
          "b7" [7 2]
          "c7" [7 3]
          "d7" [7 4]
          "e7" [7 5]
          "f7" [7 6]
          "g7" [7 7]
          "h7" [7 8]

          "a8" [8 1]
          "b8" [8 2]
          "c8" [8 3]
          "d8" [8 4]
          "e8" [8 5]
          "f8" [8 6]
          "g8" [8 7]
          "h8" [8 8] })

;;=============

(def DIR_UP_LEFT (- 2 (- BOARD_SIZE))) ;; -10
(def DIR_UP (- 1 (- BOARD_SIZE)))      ;; -9
(def DIR_UP_RIGHT (- BOARD_SIZE))      ;; -8
(def DIR_LEFT -1)
(def DIR_RIGHT 1)
(def DIR_DOWN_LEFT BOARD_SIZE)         ;; 8
(def DIR_DOWN	(+ 1 BOARD_SIZE))        ;; 9
(def DIR_DOWN_RIGHT	(+ 2 BOARD_SIZE))  ;; 10


(def dirs [[ -1 -1 ] [  0 -1 ]
           [  1 -1 ] [ -1  0 ]
           [  1  0 ] [ -1  1 ]
           [  0  1 ] [  1  1 ]])

(defn bar [board [i j] [k h] color]
  (loop [flipped []
         x (+ i k)
         y (+ j h)]
    (if (or (< x 0)
            (< y 0)
            (> x (inc BOARD_SIZE))
            (> y (inc BOARD_SIZE))
            (= EMPTY ((board x) y)))
      []
      (if (= color ((board x) y))
        flipped
        (recur
         (conj flipped [x y])
         (+ x k)
         (+ y h))))))

;;=============

(defn Board_Disk [board key [a b]]
  (((board key) a) b))

(def count-if (comp count filter))

(defn Board_CountDisks [board color]
  (apply + (map (fn [line] (count-if #(= % color) line)) board)))





;; 未完了=================

(defn Board_Unflip [log]
  (dosync (ref-set log (subvec @log 2))))

(defn Board_CountFlips [vec]
  (count (apply concat vec)))

;; true/false
(defn Board_CanFlip [board [i j] [k h] color]
  (loop [flipped []
         x (+ i k)
         y (+ j h)]
    (if (or (< x 0)
            (< y 0)
            (> x (inc BOARD_SIZE))
            (> y (inc BOARD_SIZE))
            (= EMPTY ((board x) y)))
      []
      (if (= color ((board x) y))
        flipped
        (recur
         (conj flipped [x y])
         (+ x k)
         (+ y h))))))

(defn Board_Reverse [board]
  (for [line board]
    (map #(condp = %
            BLACK WHITE
            WHITE BLACK
            %) line)))

;; 未テスト=================

(defn Board_Pos [x y]
  (+
   (inc x)
   (* (inc y)
      (inc BOARD_SIZE))))

(defn Board_X [in_pos]
  (dec (rem in_pos (inc BOARD_SIZE))))

(defn Board_Y [in_pos]
  (dec (quot in_pos (inc BOARD_SIZE))))

(defn Board_OpponentColor [color]
  ;; WALL = BLACK + WHITE
  (- WALL color))


;; オリジナル=================

(defn reversi-repl []

  ;; 初期表示
  (disp
   (gen_board_part init_disk)
   (gen_score_part BLACK {:black 2 :white 2})
   "bbb")

  (print "Enter [ q, u, a1 - h8 ]> ")
  (flush)

  (loop [cmd (read-line)
         log (list init_disk)
         turn BLACK]

    　　(condp contains? cmd
          #{"q" "quit"} "Bye!"
          #{"u" "undo"}

          (let [errflg (>= 1 (count log))
                log-undo (if errflg log (rest log))
                stones (cnt-stones (first log-undo))
                turn-undo (if errflg turn (- WALL turn))]

            (if errflg
              ;; === got incorrect cmd in ===
              (println "you can't undo now.")

              ;; 表示
              (disp
               (gen_board_part (first log-undo))
               (gen_score_part turn stones)
               (gen_status_part "undo")))

            (print "Enter [ q, u, a1 - h8 ]> ")
            (flush)

            ;; turn and wait cmd
            (recur (read-line) log-undo turn-undo))

          #{"A1" "B1" "C1" "D1" "E1" "F1" "G1" "H1" "A2" "B2" "C2" "D2" "E2" "F2" "G2" "H2" "A3" "B3" "C3" "D3" "E3" "F3" "G3" "H3" "A4" "B4" "C4" "D4" "E4" "F4" "G4" "H4" "A5" "B5" "C5" "D5" "E5" "F5" "G5" "H5" "A6" "B6" "C6" "D6" "E6" "F6" "G6" "H6" "A7" "B7" "C7" "D7" "E7" "F7" "G7" "H7" "A8" "B8" "C8" "D8" "E8" "F8" "G8" "H8"
            "a1" "b1" "c1" "d1" "e1" "f1" "g1" "h1" "a2" "b2" "c2" "d2" "e2" "f2" "g2" "h2" "a3" "b3" "c3" "d3" "e3" "f3" "g3" "h3" "a4" "b4" "c4" "d4" "e4" "f4" "g4" "h4" "a5" "b5" "c5" "d5" "e5" "f5" "g5" "h5" "a6" "b6" "c6" "d6" "e6" "f6" "g6" "h6" "a7" "b7" "c7" "d7" "e7" "f7" "g7" "h7" "a8" "b8" "c8" "d8" "e8" "f8" "g8" "h8"}

          (let [errflg (>= 1 (count log))
                log-undo (if errflg log (rest log))
                stones (cnt-stones (first log-undo))]

            ;; === got correct cmd ===


            ;; 再描画
            (disp
             (gen_board_part (first log))
             (gen_score_part turn {:black 2 :white 2})
             "aaa")

            (print "Enter [ q, u, a1 - h8 ]> ")
            (flush)

            ;; playing game

            ;; turn and wait cmd
            (recur (read-line)
                   ;; new board
                   log
                   ;; change
                   (- WALL turn)))

          　(do
              ;; === got incorrect cmd ===

              (println "i do not know that command.")
              (print "Enter [ q, u, a1 - h8 ]> ")
              (flush)

              ;; wait cmd
              (recur (read-line)
                     log
                     turn))
          )))


(defn gen_board_part [board]
  (loop [i 0
         body "   A B C D E F G H \n"]
    (if (>= i (count board))
      (apply str body)
      (recur
       (inc i)
       (str body
            (if (contains? #{0 9} i) " " i)
            (apply str (map #(condp = %
                               BLACK " O"
                               WHITE " X"
                               EMPTY " ."
                               " #") (board i)))
            "\n")))))

(defn gen_score_part [color cnt_stones]
  (let [isMyTurn #(if (= color %) "*" " ")]
    (str "\n"
         "BLACK" "[" (isMyTurn BLACK) "] :"
         (cnt_stones :black) "\n"
         "WHITE" "[" (isMyTurn WHITE) "] :"
         (cnt_stones :white) "\n\n")))


(defn gen_status_part
  ([cnt_stones turn]
   (gen_status_part
    (if (= 0 (cnt_stones :empty))
      "GameOver"
      "Playing")))
  ([status]
   (str "status: [ " status " ]")))

(defn disp [board score status]
  (print (str score board status "\n\n")))

(defn cnt-stones [board]
  (letfn [(cnt_color [c]
            (count-if #(= c %) (apply concat board)))]

    {:black (cnt_color BLACK)
     :white (cnt_color WHITE)
     :empty (cnt_color EMPTY)}))

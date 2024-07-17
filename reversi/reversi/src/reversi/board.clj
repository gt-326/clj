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
   (+ 3 (* 3 (- BOARD_SIZE 2)))
   (* BOARD_SIZE BOARD_SIZE)))

;;=============

(def board
  {:disk 1
   :stack 2
   :sp 3})

(def init_disk
  [[ 3, 3, 3, 3, 3, 3, 3, 3, 3 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 0, 0, 0, 0, 0, 0 ]
   [ 3, 0, 0, 2, 1, 0, 0, 0, 0 ]
   [ 3, 0, 0, 1, 2, 0, 0, 0, 0 ]
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

;;=============

(def DIR_UP_LEFT (- 2 (- BOARD_SIZE))) ;; -10
(def DIR_UP (- 1 (- BOARD_SIZE)))      ;; -9
(def DIR_UP_RIGHT (- BOARD_SIZE))      ;; -8
(def DIR_LEFT -1)
(def DIR_RIGHT 1)
(def DIR_DOWN_LEFT BOARD_SIZE)         ;; 8
(def DIR_DOWN	(+ 1 BOARD_SIZE))        ;; 9
(def DIR_DOWN_RIGHT	(+ 2 BOARD_SIZE))  ;; 10

;;=============

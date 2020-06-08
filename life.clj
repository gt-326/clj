;---
; Inspired by the snakes that have gone before:
;
; Stuart Halloway and Aaron Bedra "Programming Clojure, Second Edition",
;  published by The Pragmatic Bookshelf. http://www.pragmaticprogrammer.com/titles/shcloj2
; Abhishek Reddy's snake: http://www.plt1.com/1070/even-smaller-snake/
; Mark Volkmann's snake: http://www.ociweb.com/mark/programming/ClojureSnake.html 
;
;---

(ns examples.life
　(:import (java.awt Color Dimension)
　　　　　　(javax.swing JPanel JFrame Timer JOptionPane)
　　　　　　(java.awt.event ActionListener KeyEvent KeyListener MouseListener)) 
　(:gen-class))

; ----------------------------------------------------------
; macro
; ----------------------------------------------------------

(defmacro alambda
　[param state & body]
　　`(letfn [(~'self [~param ~state] ~@body)] ~'self))

(defmacro create-nested-vector
　[fnc]
　　`(vec (for [~'h (range 0 HEIGHT)]
　　　(vec (for [~'w (range 0 WIDTH)]
　　　　(~fnc [~'h ~'w] HEIGHT WIDTH))))))

; ----------------------------------------------------------
; functional model
; ----------------------------------------------------------

(def HEIGHT 50)
(def WIDTH 50)
(def POINT-SIZE 10)
(def TURN-MILLIS 200)

(def STATE-DEAD 0)
(def STATE-BORN 1)
(def STATE-ALIVE 2)
(def STATE-ADD 3)

(def TEMPLATE
  {
   ;; glider
   "0" #{ [0 1]
       　[1 2]
       　[2 0] [2 1] [2 2] }

   ;; gosper-glider-gun
   "1" #{ [1 5] [1 6]
       　[2 5] [2 6]
       　[11 5] [11 6] [11 7]
       　[12 4] [12 8]
       　[13 3] [13 9]
       　[14 3] [14 9]
       　[15 6]
       　[16 4] [16 8]
       　[17 5] [17 6] [17 7]
       　[18 6]
       　[21 3] [21 4] [21 5]
       　[22 3] [22 4] [22 5]
       　[23 2] [23 6]
       　[25 1] [25 2] [25 6] [25 7]
       　[35 3] [35 4]
       　[36 3] [36 4] } })

;----------------------------------------------------------

(def NEIGHBOUR-IDXES
　(create-nested-vector
　　(fn [trgt-idx h_max w_max]
　　　(filter
　　　　(fn [[x y]] (and (< -1 x h_max) (< -1 y w_max) (not (= [x y] trgt-idx))))
　　　　(map
　　　　　#(map + trgt-idx %)
　　　　　(vals { :1 [-1 -1] :2 [ 0 -1] :3 [ 1 -1]
　　　　　　　　　　:8 [-1 0] :0 [ 0 0] :4 [ 1 0]
　　　　　　　　　　:7 [-1 1] :6 [ 0 1] :5 [ 1 1] }))))))

(defn fn-calc-init-state
　[stat type]
　　(fn [trgt-idx & _]
　　　(if (or (empty? stat) (nil? type))
　　　　(rand-int 2)
　　　　(if (contains? (set stat) trgt-idx) STATE-BORN STATE-DEAD))))

(defn fn-calc-next-state
　[stat neighbours]
　　(fn [trgt-idx & _]
　　　(let [n-idxes (get-in neighbours trgt-idx)]
　　　　(case (count (remove zero? (map #(get-in stat %) n-idxes)))
　　　　　2 (if (= (get-in stat trgt-idx) STATE-DEAD) STATE-DEAD STATE-ALIVE)
　　　　　3 STATE-BORN
　　　　　STATE-DEAD))))

(defn fn-calc-state
　[stat cell]
　　(fn [trgt-idx & _]
　　　(let [cell-stat-current (get-in stat trgt-idx)]
　　　　(if (= cell trgt-idx)
　　　　　(if (= cell-stat-current STATE-ADD) STATE-DEAD STATE-ADD)
　　　　　cell-stat-current))))

; ----------------------------------------------------------
; mutable model
; ----------------------------------------------------------

(defn update-stat
　([cells val] (dosync (alter cells assoc :pause val)))
　([cells key fnc] (dosync (alter cells assoc key (create-nested-vector fnc)))))

; ----------------------------------------------------------
; gui
; ----------------------------------------------------------

(def cell-color {
　STATE-DEAD (Color. 255 255 255)
　STATE-BORN (Color. 210 50 90)
　STATE-ALIVE (Color. 110 50 95)
　STATE-ADD (Color. 255 140 0) })

(defn opt-position
　[mode h w]
　　(case mode
　　 "0" (fn [[i j]] (vector i j))
　　 "1" (fn [[i j]] (vector (- h 1 i) j))
　　 "2" (fn [[i j]] (vector (- h 1 i) (- w 1 j)))
　　 "3" (fn [[i j]] (vector i (- w 1 j)))
　　 "4" (fn [[i j]] (vector j i))))

(defn point-to-screen-rect
　[[i j] size] 
　　(map #(* size %) [i j 1 1]))

(defn point-to-state-idx
　[[i j] size]
　　(map #(- (+ (quot % size) (if (zero? (rem % size)) 0 1)) 1) [i j]))

(defn fill-point
　[g pt h w color]
　　(let [[x y h w] (point-to-screen-rect pt POINT-SIZE)]
　　　(.setColor g color)
　　　(.fillRect g y x h w)))

(defmulti paint (fn [g object & _] (:type object)))

(defmethod paint :cells
　[g {:keys [height width states]}]
　　(doseq [h (range 0 height) w (range 0 width)]
　　　(let [color (get cell-color (get-in states [h w]))]
　　　　(fill-point g [h w] height width color))))

(defn get-dots-max-idx
　[state]
　　(inc (last (sort (map (fn [[i j]] (if (> i j) i j)) state)))))

; ----------------------------------------------------------

(defn create-params
　[pos idx1 idx2] 
　　(if (and (not (nil? pos)) (not (= "0" pos)))
　　　(list pos idx1 idx2)))

(defn create-param-lists [dot dot-idx grid h-idx w-idx]
　(remove nil? (list (create-params dot dot-idx dot-idx)
　　　　　　　　　　　　(create-params grid h-idx w-idx))))

(defn create-cells
　[h_max w_max [dot-type grid-pos dot-pos]]
　　{ :type :cells
　　　:pause false
　　　:height h_max
　　　:width w_max
　　　:states
　　　　(let [ state (if dot-type
　　　　　(let [ tmp (TEMPLATE (str dot-type))
　　　　　　　　　dot-idx (get-dots-max-idx tmp)
　　　　　　　　　param-lst (create-param-lists dot-pos dot-idx grid-pos h_max w_max) ]

　　　　　　;; anaphoric macro
　　　　　　((alambda params rslt (if (empty? params) rslt
 　　　　　　 (self
　　　　　 　 　(rest params)
　　　　　　  　(let [[pos h w] (first params)] (map (opt-position (str pos) h w) rslt)))))
　　　　   　param-lst tmp))) ]

　　　　　;; init-states
　　　　　(create-nested-vector (fn-calc-init-state state dot-type))) })

; ----------------------------------------------------------

(defn game-panel
　[frame cells neighbours height width]
　　(proxy [JPanel ActionListener KeyListener MouseListener] []
　　　(paintComponent [g]
　　　　(proxy-super paintComponent g)
　　　　(paint g @cells))

　　　(actionPerformed [e]
　　　　(if (not (:pause @cells))
　　　　　(update-stat cells :states (fn-calc-next-state (:states @cells) neighbours)))
　　　　(.repaint this))

　　　(getPreferredSize []
　　　　(Dimension. (* (inc width) POINT-SIZE)
　　　　(* (inc height) POINT-SIZE)))

　　　(keyPressed [e]
　　　　(if (= (.getKeyCode e) KeyEvent/VK_SPACE)
　　　　　(update-stat cells (not (:pause @cells)))))

　　　(keyReleased [e])
　　　(keyTyped [e])

　　　(mouseClicked [e]
　　　　(let [ x (.x (.getPoint e)) y (.y (.getPoint e))
　　　　　　　　clicked-point (point-to-state-idx [y x] POINT-SIZE)]
　　　　　(update-stat cells :states (fn-calc-state (:states @cells) clicked-point)))
　　　　　(.repaint this))

　　　(mouseEntered [e])
　　　(mouseExited [e])
　　　(mousePressed [e])
　　　(mouseReleased [e])))

(defn -main
　[& args]
　　(let [
   　frame (JFrame. "Life")
　　　cells (ref (create-cells HEIGHT WIDTH args))
　　　panel (game-panel frame cells NEIGHBOUR-IDXES HEIGHT WIDTH)
　　　timer (Timer. TURN-MILLIS panel)]

　　　(doto panel
　　　　(.setFocusable true)
　　　　(.addMouseListener panel)
　　　　(.addKeyListener panel))

　　　(doto frame
　　　　(.add panel)
　　　　(.pack)
　　　　(.setVisible true))

　　　(.start timer)
　　　[cells timer]))

; ----------------------------------------------------------

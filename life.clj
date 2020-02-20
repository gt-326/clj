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
; functional model
; ----------------------------------------------------------

(def height 50)
(def width 50)
(def point-size 10)
(def turn-millis 200)

(def state-dead 0)
(def state-born 1)
(def state-alive 2)
(def state-add 3)

(def neighbours {
　"1" [-1 -1] "2" [ 0 -1] "3" [ 1 -1]
　"8" [-1  0]             "4" [ 1  0]
　"7" [-1  1] "6" [ 0  1] "5" [ 1  1] })

(def template
  {
   ;; glider
   "0" #{ [0 1]
       　[1 2]
       　[2 0] [2 1] [2 2] }

   ;;gosper-glider-gun
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

(defn get-target-state
　[stat i j]
　　(nth (nth stat i) j))

(defn get-neighbour-state-idx
　[i j]
　　(for [cell (vals neighbours)] (map + (list i j) cell)))

(defn get-neighbour-states
　[stat i j h_max w_max]
　　(for [[h w] (get-neighbour-state-idx i j)]
　　　(if (or (> 0 h) (>= h h_max) (> 0 w) (>= w w_max))
　　　　state-dead
　　　　(get-target-state stat h w))))

(defn get-next-states
　[stat h_max w_max]
　　(for [h (range 0 h_max)]
　　　(for [w (range 0 w_max)]
　　　　(let [n-states (get-neighbour-states stat h w h_max w_max)]
　　　　　(case (count (remove zero? n-states))
　　　　　　2 (if (= (get-target-state stat h w) state-dead) state-dead state-alive)
　　　　　　3 state-born
　　　　　　state-dead)))))

; ----------------------------------------------------------
; mutable model
; ----------------------------------------------------------

(defn update-states
　[d]
　　(dosync (alter d assoc :states
　　　(get-next-states (:states @d) (:height @d) (:width @d)))))

(defn toggle-pause
　[d]
　　(dosync (alter d assoc :pause (not (:pause @d)))))

(defn add-cell
　[d [i j]]
　　(let [current-states (:states @d) h_max (:height @d) w_max (:width @d)]
　　　(dosync (alter d assoc :states
　　　　(for [h (range 0 h_max)]
　　　　　(for [w (range 0 w_max)]
　　　　　　(let [c-state (get-target-state current-states h w)]
　　　　　　　(if (and (= i h) (= j w))
　　　　　　　　(if (= c-state state-add) state-dead state-add)
　　　　　　　　c-state))))))))

; ----------------------------------------------------------
; gui
; ----------------------------------------------------------

(def cell-color {
　state-dead (Color. 255 255 255)
　state-born (Color. 210 50 90)
　state-alive (Color. 110 50 95)
　state-add (Color. 255 140 0) })

(defn opt-position
　[mode h w]
　　(case mode
　　 "0" (fn [[i j]] (vector i j))
　　 "1" (fn [[i j]] (vector (- h 1 i) j))
　　 "2" (fn [[i j]] (vector (- h 1 i) (- w 1 j)))
　　 "3" (fn [[i j]] (vector i (- w 1 j)))
　　 "4" (fn [[i j]] (vector j i))))

(defn point-to-screen-rect
　[size [i j]] 
　　(map #(* size %) [i j 1 1]))

(defn point-to-state-idx
　[i j size]
　　(map
　　　#(- (+ (quot % size) (if (zero? (rem % size)) 0 1)) 1)
　　　(list j i)))

(defn fill-point
　[g pt h w color]
　　(let [[x y h w] (point-to-screen-rect point-size pt)]
　　　(.setColor g color)
　　　(.fillRect g y x h w)))

(defmulti paint (fn [g object & _] (:type object)))

(defmethod paint :cells
　[g {:keys [height width states]}]
　　(doseq [h (range 0 height) w (range 0 width)]
　　　(let [color (get cell-color (get-target-state states h w))]
　　　　(fill-point g (list h w) height width color))))

; ----------------------------------------------------------
; macro
; ----------------------------------------------------------

(defmacro alambda [param state & body]
　`(letfn [(~'self ~(vec (list param state)) ~@body)]
　~'self))

(defmacro get-init-states [type stat h_max w_max]
　`(for [~'h (range 0 ~h_max)]
　　(for [~'w (range 0 ~w_max)]
　　　(if (or (nil? ~type) (empty? ~stat)) 
　　　　(rand-int 2)
　　　　(if (contains? (set ~stat) (list ~'h ~'w)) state-born　state-dead)))))

; ----------------------------------------------------------

(defn get-dots-max-idx [state]
　(inc (last (sort (map (fn [[i j]] (if (> i j) i j)) state)))))

(defn create-cells
　[h_max w_max [dot-type grid-pos dot-pos]]
　　{ :type :cells
　　　:pause false
　　　:height h_max
　　　:width w_max
　　　:states
　　　　(let [state (if dot-type
　　　　　(let [tmp (template (str dot-type))
　　　　　　param-lst (remove nil? (list
　　　　　　　;; dot　
　　　　　　　(if (and (not (nil? dot-pos)) (not (= "0" dot-pos)))
　　　　　　　　(let [idx (get-dots-max-idx tmp)]
　　　　　　　　　(list dot-pos idx idx)))

　　　　　　　;; grid
　　　　　　　(if (and (not (nil? grid-pos)) (not (= "0" grid-pos)))
　　　　　　　　(list grid-pos h_max w_max))))]

　　　　　　;; anaphoric macro
　　　　　　((alambda params rslt
　　　　　　　(if (empty? params) rslt
　　　　　　　　(self (rest params)
　　　　　　　　　(let [[pos h w] (first params)]
　　　　　　　　　　(map (opt-position (str pos) h w) rslt)))))
　　　　　　　param-lst tmp)))]

　　　　　;; init-states
　　　　　(get-init-states dot-type state h_max w_max)) })

(defn game-panel
　[frame cells height width]
　　(proxy [JPanel ActionListener KeyListener MouseListener] []
　　　(paintComponent [g]
　　　　(proxy-super paintComponent g)
　　　　(paint g @cells))

　　　(actionPerformed [e]
　　　　(if (not (:pause @cells)) (update-states cells))
　　　　(.repaint this))

　　　(getPreferredSize []
　　　　(Dimension. (* (inc width) point-size)
　　　　(* (inc height) point-size)))

　　　(keyPressed [e]
　　　　(if (= (.getKeyCode e) KeyEvent/VK_SPACE)
　　　　　(dosync (toggle-pause cells))))

　　　(keyReleased [e])
　　　(keyTyped [e])

　　　(mouseClicked [e]
　　　　(let [x (.x (.getPoint e)) y　(.y (.getPoint e))]
　　　　　(add-cell cells (point-to-state-idx x y point-size)))
　　　　(.repaint this))

　　　(mouseEntered [e])
　　　(mouseExited [e])
　　　(mousePressed [e])
　　　(mouseReleased [e])))

(defn -main [& args]
　(let [
   frame (JFrame. "Life")
　　cells (ref (create-cells height width args))
　　panel (game-panel frame cells height width)
　　timer (Timer. turn-millis panel)]

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

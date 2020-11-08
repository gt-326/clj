;---
; Inspired by the robots that have gone before:
;
; Conrad Barski, M.D. "Land of Lisp"
;  published by no starch press. http://landoflisp.com/
;
;---

(ns examples.robots
　(:import (java.awt Color Dimension)
　　　　　　(javax.swing JPanel JFrame Timer JOptionPane)
　　　　　　(java.awt.event ActionListener KeyEvent KeyListener))
　(:gen-class))

; ----------------------------------------------------------
; functional model
; ----------------------------------------------------------

(def POINT-SIZE 10)
(def TURN-MILLIS 500)

(def STATE-FIELD 0)
(def STATE-DEAD 1)
(def STATE-ALIVE 2)
(def STATE-PLAYER 3)

(def ROBOTS 3)

(def WIDTH 64)
(def HEIGHT 16)
(def OFFSET 1)

(def DIRECTIONS
　{ "q" (- (+ WIDTH OFFSET)),
　　"w" (- WIDTH),
　　"e" (- (- WIDTH OFFSET)),
　　"a" (- OFFSET),
　　"d" OFFSET,
　　"z" (- WIDTH OFFSET),
　　"x" WIDTH,
　　"c" (+ WIDTH OFFSET),

　　;; (t)eleport
   "t" (rand-int (* WIDTH HEIGHT)) })

(def IDXES
　(vec (for [y (range HEIGHT) x (range WIDTH)] [y x])))

(def INIT-MAP
　(vec (for [y (range HEIGHT)]
　　(vec (for [x (range WIDTH)] STATE-FIELD)))))

; ----------------------------------------------------------

(defn make-monsters
　[num w h]
　　(map
　　　#(vec (list false %))
　　　(take num (set (take (+ num 10) (repeatedly #(rand-int (* w h))))))))

(defn manhattan
　[pos mpos width]
　　(+
　　　(Math/abs (- (rem mpos width) (rem pos width)))
　　　(Math/abs (- (quot mpos width) (quot pos width)))))

(defn cnt-if
　[fnc lst] ((comp count filter) fnc lst))

(defn next-stat
　[pos monsters dirs height width]
　　(for [ [m_flg m_pos] monsters
　　　:let [flg (< 1 (cnt-if (fn [[_ p]] (or m_flg (= p m_pos))) monsters))] ]
　　　(if flg
　　　　;; dead
　　　　[flg m_pos]

　　　　;; alive
　　　　[flg (:mpos-new (first (sort-by :manhattan <
　　　　　　　　(for [ d dirs
　　　　　　　　　:let [ new-mpos (+ (val d) m_pos)
      　　             distance (manhattan pos new-mpos width) ]
　　　　　　　　　:when (and (<= 0 new-mpos) (< new-mpos (* height width))) ]
　　　　　　　　　{:manhattan distance :mpos-new new-mpos}))))])))

; ----------------------------------------------------------
; mutable model
; ----------------------------------------------------------

(defn create-map-data
　[init player monsters]
　　(let [data (atom init)]
　　　;; player
　　　(swap! data assoc-in (IDXES player) STATE-PLAYER)
　　　;; monsters
　　　(doseq [[flg mpos] monsters]
　　　　(swap! data assoc-in (IDXES mpos) (if flg STATE-DEAD STATE-ALIVE)))
　　　@data))

(defn update-stat
　([cells key val] (dosync (alter cells assoc key val))))

; ----------------------------------------------------------
; gui
; ----------------------------------------------------------

(def cell-color {
　STATE-FIELD (Color. 255 255 255)
　STATE-DEAD (Color. 0 0 0)
　STATE-ALIVE (Color. 255 140 0)
　STATE-PLAYER (Color. 0 128 0) })

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

; ----------------------------------------------------------

(defn create-cells
　[h_max w_max]
　　{ :type :cells
　　　:pause true
　　　:height h_max
　　　:width w_max
　　　:states INIT-MAP })

; ----------------------------------------------------------

(defn game-panel
　[frame cells player monsters height width]
　　(proxy [JPanel ActionListener KeyListener] []
　　　(paintComponent [g]
　　　　(proxy-super paintComponent g)
　　　　(paint g @cells))

　　　(actionPerformed [e]
　　　　(when (not (:pause @cells))
　　　　　(when (= ROBOTS (cnt-if (fn [[f p]] f) @monsters))
　　　　　　(update-stat cells :pause true)
　　　　　　(JOptionPane/showMessageDialog frame "player-wins"))

　　　　　(when (< 1 (cnt-if (fn [[f p]] (= p @player)) @monsters))
　　　　　　(update-stat cells :pause true)
　　　　　　(JOptionPane/showMessageDialog frame "player-loses"))

　　　　　;; update monsters
　　　　　(reset! monsters (next-stat @player @monsters DIRECTIONS HEIGHT WIDTH))
　　　　　(update-stat cells :states (create-map-data INIT-MAP @player @monsters)))
　　　　(.repaint this))

　　　(getPreferredSize []
　　　　(Dimension. (* (inc width) POINT-SIZE)
　　　　(* (inc height) POINT-SIZE)))

　　　(keyPressed [e]
　　　　(if (= (.getKeyCode e) KeyEvent/VK_SPACE)
　　　　　(update-stat cells :pause (not (:pause @cells)))
　　　　　(let [ ch (str (.getKeyChar e))
　　　　　　　　　num (DIRECTIONS ch)
　　　　　　　　　ppos (+ @player (if (nil? num) 0 num)) ]
　　　　　　(if (= "l" ch)
　　　　　　　;; (l)eave
　　　　　　　(do
　　　　　　　　(reset! player 0)
　　　　　　　　(reset! monsters ())
　　　　　　　　(JOptionPane/showMessageDialog frame "bye!"))
　　　　　　　;; update player
　　　　　　　(reset! player (if (<= 0 ppos)
                               (rem ppos (* WIDTH HEIGHT))
                               (dec (+ ppos (* WIDTH HEIGHT)))))))))

　　　(keyReleased [e])
　　　(keyTyped [e])))

(defn -main
　[& args]
　　(let [
　　　player (atom 544)
　　　monsters (atom (make-monsters ROBOTS WIDTH HEIGHT))
　　　frame (JFrame. "Robots")

　　　cells (ref (create-cells HEIGHT WIDTH))
　　　panel (game-panel frame cells player monsters HEIGHT WIDTH)
　　　timer (Timer. TURN-MILLIS panel) ]

　　　(doto panel
　　　　(.setFocusable true)
　　　　(.addKeyListener panel))

　　　(doto frame
　　　　(.add panel)
　　　　(.pack)
　　　　(.setVisible true))

　　　(.start timer)
　　　[cells timer]))

; ----------------------------------------------------------

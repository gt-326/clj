;---
; Inspired by the evolution that have gone before:
;
; Conrad Barski, M.D. "Land of Lisp"
;  published by no starch press. http://landoflisp.com/
;
;---

(ns examples.evo
　(:import (java.awt Color Dimension)
　　　　　　(javax.swing JPanel JFrame Timer JOptionPane)
　　　　　　(java.awt.event ActionListener KeyEvent KeyListener))
　(:gen-class))

; ----------------------------------------------------------
; functional model
; ----------------------------------------------------------

(def HEIGHT 60)
(def WIDTH 20)
(def POINT-SIZE 10)
(def TURN-MILLIS 200)

(def STATE-FIELD 0)
(def STATE-DEAD 1)
(def STATE-ALIVE 2)
(def STATE-PLANT 3)

(def JUNGLE '(45 10 10 10))

(def PLANT-ENERGY 80)
(def REPRODUCTION-ENERGY 200)

(def INIT-MAP
　(vec (for [y (range HEIGHT)]
　　(vec (for [x (range WIDTH)] STATE-FIELD)))))

;;===============

(defn random-plant
　[left top width height]
　　[ (mod (+ top (rand-int height)) height)
　　　(mod (+ left (rand-int width)) width) ])

(defn make-animal
　([w h] (make-animal
　　　　(bit-shift-right w 1)
　　　　(bit-shift-right h 1)
　　　　500
　　　　0
　　　　0
　　　　(vec (take 8 (repeatedly #(inc (rand-int 10)))))))
　([x y e cd d g] { :x x :y y :energy e :cnt-dead cd  :dir d :genes (vec g) }))

(defn turn
　[animal]
　　(let [x (rand-int (apply + (animal :genes)))]
　　　(assoc animal :dir
　　　　(mod
　　　　　(+ (animal :dir)
　　　　　　(loop [result 1 [car & rest] (animal :genes) ]
　　　　　　　(if (nil? car)
　　　　　　　　result
　　　　　　　　(if (< (- x car) 0) 0 (recur (+ result (- x car)) rest)))))
　　　　(count (animal :genes))))))

(defn move
　[h w animal]
　　(let [ dir (animal :dir) ]
　　　(if (<= (animal :energy) 0)
　　　　(assoc animal :cnt-dead (inc (animal :cnt-dead)))
　　　　(merge animal
　　　　　{ :x (mod (+ (animal :x)
　　　　　　　(cond
　　　　　　　　(#{2 3 4} dir) 1
　　　　　　　　(#{1 5} dir) 0
　　　　　　　　:else -1)) w)

　　　　　　:y (mod (+ (animal :y)
　　　　　　　(cond
　　　　　　　　(#{0 1 2} dir) -1
　　　　　　　　(#{4 5 6} dir) 1
　　　　　　　　:else 0)) h)

　　　　　　:energy (dec (animal :energy)) }))))

(defn remove-dead
　[animals]
　　(for [a animals :when (or (> (a :energy) 0) (> 5 (a :cnt-dead)))] a))

(defn reproduce
　[animal r-energy]
　　(let [e (animal :energy)]
　　　(if (< e r-energy)
　　　　(list animal)
　　　　(let [ animal-half (assoc animal :energy (bit-shift-right e 1))
　　　　　　　animal-nu animal-half
　　　　　　　genes (animal :genes)
　　　　　　　mutation (rand-int (count genes))
　　　　　　　val (max 1 (+ (nth genes mutation) (rand-int 3) -1))]
　　　　　(list
　　　　　　animal-half
　　　　　　(assoc animal-nu :genes (assoc genes mutation val)))))))

(defn eat
　[flg animal energy]
　　(if flg
　　　(assoc animal :energy (+ energy (animal :energy)))
　　　animal))

; ----------------------------------------------------------
; mutable model
; ----------------------------------------------------------

(defn add-plants
　[plants jungle w h]
　　(do
　　　(swap! plants conj (apply random-plant jungle))
　　　(swap! plants conj (random-plant 0 0 w h))))

(defn update-world
　[animals plants]
　　(do
　　　(reset! animals (apply concat
　　　　(for [a0 (remove-dead @animals)
　　　　　:let [ a1 (move HEIGHT WIDTH (turn a0))
　　　　　　　　　pos [(a1 :y) (a1 :x)]
　　　　　　　　　flg (contains? @plants pos) ]]
　　　　　(do
　　　　　　(if flg (swap! plants disj pos))
　　　　　　(reproduce (eat flg a1 PLANT-ENERGY) REPRODUCTION-ENERGY)))))
　　　(add-plants plants JUNGLE WIDTH HEIGHT)))

(defn create-map-data
　[init animals plants]
　　(let [data (atom init)]
　　　(doseq [a animals :let [state (if (<= (a :energy) 0) STATE-DEAD STATE-ALIVE)]]
　　　　(swap! data assoc-in [(a :y) (a :x)] state))
　　　(doseq [p plants] (swap! data assoc-in p STATE-PLANT))
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
　STATE-PLANT (Color. 0 128 0) })

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
　　　:pause false
　　　:height h_max
　　　:width w_max
　　　:states INIT-MAP })

; ----------------------------------------------------------

(defn game-panel
　[frame cells animals plants height width]
　　(proxy [JPanel ActionListener KeyListener] []
　　　(paintComponent [g]
　　　　(proxy-super paintComponent g)
　　　　(paint g @cells))

　　　(actionPerformed [e]
　　　　(if (not (:pause @cells))
　　　　　(do
　　　　　　(update-world animals plants)
　　　　　　(update-stat cells :states (create-map-data INIT-MAP (set @animals) @plants))))
　　　　(.repaint this))

　　　(getPreferredSize []
　　　　(Dimension. (* (inc width) POINT-SIZE)
　　　　(* (inc height) POINT-SIZE)))

　　　(keyPressed [e]
　　　　(if (= (.getKeyCode e) KeyEvent/VK_SPACE)
　　　　　(update-stat cells :pause (not (:pause @cells)))))

　　　(keyReleased [e])
　　　(keyTyped [e])
　　　(mouseClicked [e])
　　　(mouseEntered [e])
　　　(mouseExited [e])
　　　(mousePressed [e])
　　　(mouseReleased [e])))

(defn -main
　[& args]
　　(let [
　　　animals (atom (list (make-animal WIDTH HEIGHT)))
　　　plants (atom #{})
　　　frame (JFrame. "Evolution")
　　　cells (ref (create-cells HEIGHT WIDTH))
　　　panel (game-panel frame cells animals plants HEIGHT WIDTH)
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

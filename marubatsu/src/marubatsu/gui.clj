(ns marubatsu.gui
  (:import
   (java.awt Color Dimension Font)
   (java.awt.event KeyListener MouseListener MouseAdapter)
   (javax.swing JPanel JFrame JLabel JOptionPane border.LineBorder))

  (:require [marubatsu.computer :as com]
            [marubatsu.board :as brd]))

;; ---------------------------------------------------------
;; constant variables
;; ---------------------------------------------------------

(def LABEL-SIZE 120)

; ----------------------------------------------------------
; mutable model
; ----------------------------------------------------------

(defn upd-status [board log turn current]
  (let [current-status (first current)]
    ;; 現在のボードの状態を更新
    (reset! board current)

    ;; undo 用の情報
    (reset! log
            (conj @log {:i (:i current-status)
                        ;; 相手の手番を設定する
                        :t (com/get_turn_next turn)}))

    ;; 現在のボードの状態
    ;; {:b board-new
    ;;  :t turn
    ;;  :i idx
    ;;  :s score
    ;;  :l lives-new}
    current-status))

; ----------------------------------------------------------
; gui
; ----------------------------------------------------------

(defn print-board [labels info]
  (doseq [[idx l] (map-indexed vector labels)]
    (if (zero? ((:l info) idx))
      (do
        (.setText l "")
        (.setBackground l Color/GRAY))

      (do
        (.setText l
                  (if (= \1 ((:b info) idx))
                    "O" "X"))
        (.setBackground l
                        (if (< 2 ((:l info) idx))
                          Color/WHITE Color/GRAY))
        (.setForeground l
                        (if (= 1 ((:l info) idx))
                          Color/WHITE)))
      )))

(defn show-result [frame t-start turn cnt]
  ;; ゲーム終了表示
  (do
    (JOptionPane/showMessageDialog
     frame
     (str
      "\n[ the lead : " (brd/conv_to_OX t-start) " ]"
      " [ end : " (brd/conv_to_OX turn) " wins ]"
      " [ cnt : " cnt " ]"
      ))

    ;; 操作抑制
    (.setEnabled frame false)))

(defn gen-click-fnc [frame labels board log win-pttrns size t-start]
  (fn [l turn]
    (loop [cnt 0
           t turn
           ;; human: idx
           i (Integer/parseInt (.getName l))]

      (if (< cnt 2)
        (let [current
              (first
               (filter #(= i (:i (first %))) (rest @board)))

              ;; 更新処理
              b-print
              (upd-status board log turn current)]

          ;; ボード表示
          (print-board labels b-print)

          (if (brd/win2? win-pttrns (:b b-print) #(= t %) size)
            ;; 終了表示
            (show-result frame t-start t (count @log))
            ;; 処理継続(手番交代)
            (recur
             (inc cnt)
             (com/get_turn_next t)
             ;; computer: idx
             (brd/random-choosing-from-bests (rest @board))))
          )))
    ))

(defn fnc-quit [frame]
  (do
    (.setEnabled frame false)
    (JOptionPane/showMessageDialog frame "[ quit : O lose ]")))

(defn game-panel [size frame labels board log all-board]
  (proxy [JPanel KeyListener] []
    (getPreferredSize []
      (Dimension. size size))

    (keyPressed [e]
      (condp = (.getKeyCode e)
        81 (fnc-quit frame)
        nil))

    ;; 空のイベントを配置しないとエラーになる
    (keyReleased [e])
    (keyTyped [e])
    ))

(defn gen-label [frame size id]
  (proxy [JLabel MouseListener] []
    ;; ここで設定しても、反映されなかったプロパティー
    ;; (getBackground [] Color/RED)
    ;; (getOpaque [] true)

    ;; ここで設定すると、あとで値を変えられなくなる
    ;; (getText [] id)

    (getName [] id)
    (getHorizontalAlignment [] JLabel/CENTER)
    (getPreferredSize [] (Dimension. size size))
    (getBorder [] (LineBorder. Color/BLUE 1 true))
    (getFont [] (Font. "ＭＳ ゴシック" Font/BOLD 50))
    ))

(defn gen-click-event [fnc]
  (proxy [MouseAdapter] []
    (mouseClicked [e]
      (let [;; 以下でも可能。キャストは不要。
            ;;(.getComponent e)
            ;;(cast JLabel (.getComponent e))
            label (.getSource e)]

        ;; 未選択のパネルか？
        (if (empty? (.getText label))
          (fnc label \1))))
    ))

(defn start-game [win-pttrns all-board mode t-start size]
  (let [board (atom all-board)
        log (atom [])

        frame (JFrame. "MaruBatsu")

        labels
        (vec
         (map
          #(gen-label frame LABEL-SIZE (str %))
          (range (* size size))))

        panel
        (game-panel
         (+ (* LABEL-SIZE size) 40)
         frame labels board log all-board)

        click-fnc
        (gen-click-fnc
         frame labels board log win-pttrns size t-start)

        fnc-action (gen-click-event click-fnc)]

    (doseq [l labels]
      (doto l

        ;; ここで設定しても、ちゃんと反映される
        ;; (.setPreferredSize (Dimension. 120 120))
        ;; (.setBorder (LineBorder. Color/BLUE 1 true))
        ;; (.setFont (Font. "ＭＳ ゴシック" Font/BOLD 100))

        (.setBackground Color/GRAY)
        (.setOpaque true)
        (.setText "")

        ;; listener
        (.addMouseListener fnc-action)))

    (doto panel
      ;; これを設定しないと、キーイベントを感知しない
      (.setFocusable true)
      (.addKeyListener panel))

    (doseq [l labels]
      (doto panel (.add l)))

    (doto frame
      (.add panel)
      (.pack)
      (.setResizable true)
      (.setVisible true))

    (if (= mode 0)
      ;; [ human vs computer ]

      (if (= t-start \2)
        ;; computer 先手
        (let [current
              (first
               (sort-by #((first %) :s) > (rest all-board)))

              b-print
              (upd-status board log t-start current)]

          ;; ボード表示
          (print-board labels b-print)))

      ;; [ computer vs computer ]
      (loop [board all-board
             turn t-start
             idx nil
             ;; ゲーム終了時のメッセージ用
             log []]

          (let [turn-n (com/get_turn_next turn)
                board-rest (rest board)
                current
                (first
                 (if idx
                   ;; 人間が先手のときの１手目、
                   ;; または、人間、コンピュータ両方の２手目以降の手
                   (filter #(= idx (:i (first %))) board-rest)

                   ;; コンピュータ先手のときの１手目
                   (sort-by #((first %) :s) > board-rest)))

                board-curr (:b (first current))
                idx-curr (:i (first current))]

            ;; ゲーム終了判定
            (if (brd/win2? win-pttrns board-curr #(= turn %) size)
              (do
                ;; ボード表示
                (print-board labels (first current))
                ;; 終了表示
                (show-result frame t-start turn (count log)))

              ;; change turn
              (recur
               current
               turn-n
               (brd/random-choosing-from-bests (rest current))

               ;; undo 用の情報
               (conj log {:i idx-curr
                          ;; 相手の手番を設定する
                          :t turn-n}))
              ))))
    ))

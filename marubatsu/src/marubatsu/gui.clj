(ns marubatsu.gui
  (:import
   (java.awt Color Dimension Font)
   (java.awt.event KeyListener MouseListener MouseAdapter)
   (javax.swing JPanel JFrame JLabel JOptionPane border.LineBorder))

  (:require [marubatsu.computer :as com]
            [marubatsu.board :as brd]
            [clojure.core.async :as ca]))

;; ---------------------------------------------------------
;; constant variables
;; ---------------------------------------------------------

(def LABEL-SIZE 120)

;; ----------------------------------------------------------
;; functional model
;; ----------------------------------------------------------

(defn mode-1 [all-board turn-int win-pttrns size

              fnc-finish]
  (loop [board (all-board turn-int)
         turn ([\1 \2] turn-int)
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

        ;; finish
        (fnc-finish current turn-int turn log)

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

;; ----------------------------------------------------------
;; mutable model
;; ----------------------------------------------------------

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

(defn fnc-undo [board log all-board]
  (let [n (- (count @log) 2)]
    ;;　idx が負数でない場合
    (if (not (neg? n))
      ;; 更新処理をおこなう
      (let [log-undo (vec (take n @log))]
        ;; undo 用の情報
        (reset! log log-undo)
        ;; ボードの情報（特定の手まで、開始時点から「完全読み」を辿る）
        (reset! board (brd/rewind all-board log-undo))))

    (first @board)))

(defn first-hand-computer [[board log] turn-int all-board]
  ;; computer 先手
  (let [t-start ([\1 \2] turn-int)
        current
        (first
         (sort-by #((first %) :s) > (rest all-board)))]

    (upd-status board log t-start current)))

;; ----------------------------------------------------------
;; gui
;; ----------------------------------------------------------

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

(defn show-result [frame labels t-start turn cnt]
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
    (doseq [l labels]
      (.setEnabled l false))
    ))

(defn gen-click-fnc [[board log t-start]
                     frame labels win-pttrns size]
  (fn [l turn]

    ;; 「コンピュータが考えている感じ」を出すためにタイマーを使っているが、
    ;; 通常の loop ではなく、go-loop を用いる必要がある
    (;;loop
     ca/go-loop [cnt 0
                 t turn
                 ;; human: idx
                 i (Integer/parseInt (.getName l))]

      (if (< cnt 2)
        (let [current
              (first
               (filter #(= i (:i (first %))) (rest @board)))

              ;; 更新処理
              b-print
              (upd-status board log t current)]

          ;; ボード表示
          (print-board labels b-print)

          (if (brd/win2? win-pttrns (:b b-print) #(= t %) size)
            ;; 終了表示
            (show-result frame labels ([\1 \2] @t-start) t (count @log))

            (do
              ;; 「コンピュータが考えている」感じを出すためのタイマー
              (ca/<! (ca/timeout 1000))

              ;; 処理継続(手番交代)
              (recur
               (inc cnt)
               (com/get_turn_next t)
               ;; computer: idx
               (brd/random-choosing-from-bests (rest @board)))))
          )))
    ))

(defn game-panel
  [[board log t-start] size frame labels all-board]
  (proxy [JPanel KeyListener] []
    (getPreferredSize []
      (Dimension. size size))

    (keyPressed [e]
      (let [msg (condp = (.getKeyCode e)
                  81 (do
                       ;; 操作抑制
                       (doseq [l labels]
                         (.setEnabled l false))

                       "[ quit : O lose ]")

                  82 (let [turn-new (rand-int 2)
                           a-board (all-board turn-new)]
                       ;; 先手を更新
                       (reset! t-start turn-new)

                       ;; 操作抑制を解除
                       (doseq [l labels]
                         (.setEnabled l true))

                       ;; ボード再表示
                       (print-board
                        labels
                        (if (= \1 ([\1 \2] @t-start))
                          ;; human
                          (do
                            (reset! board a-board)
                            (reset! log [])
                            (first @board))

                          ;; computer
                          (first-hand-computer
                           [board log] @t-start a-board)))

                       "Reset")

                  85 (do
                       ;; ボード再表示
                       (print-board
                        labels
                        (fnc-undo board log (all-board @t-start)))
                       "Undo")

                  ;; [ Q, R, U ] 以外のキー入力
                  nil)]

        (if (not (nil? msg))
          (JOptionPane/showMessageDialog frame msg))))

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

(defn fnc-finish [frame labels]
  (fn [current turn-int turn log]
    (do
      ;; ボード表示
      (print-board labels (first current))
      ;; 終了表示
      (show-result frame labels ([\1 \2] turn-int) turn (count log)))))

(defn start-game [win-pttrns all-board mode turn-int size]
  (let [board (atom (all-board turn-int))
        log (atom [])
        t-start (atom turn-int)

        frame (JFrame. "MaruBatsu")

        labels
        (vec
         (map
          #(gen-label frame LABEL-SIZE (str %))
          (range (* size size))))

        panel
        (game-panel
         [board log t-start]
         (+ (* LABEL-SIZE size) 40) frame labels all-board)

        click-fnc
        (gen-click-fnc
         [board log t-start]
         frame labels win-pttrns size)

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
      (if (= \2 ([\1 \2] @t-start))
        ;; computer 先手
        (print-board
         labels
         (first-hand-computer
          [board log]
          @t-start (all-board turn-int))))

      ;; [ computer vs computer ]
      (mode-1
       all-board turn-int win-pttrns size
       ;; ゲーム終了時の表示
       (fnc-finish frame labels)))
    ))

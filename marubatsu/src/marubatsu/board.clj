(ns marubatsu.board
  (:require [marubatsu.computer :as com]))

;;=============

(def idxes {"a1" 0
            "b1" 1
            "c1" 2
            "a2" 3
            "b2" 4
            "c2" 5
            "a3" 6
            "b3" 7
            "c3" 8
            "q"  9
            "u"  10

            "A1" 0
            "B1" 1
            "C1" 2
            "A2" 3
            "B2" 4
            "C2" 5
            "A3" 6
            "B3" 7
            "C3" 8

            "Q"  9
            "U"  10})

(defn put-stone [board idx color]
  (assoc board idx color))

(defn conv-num [board]
  (map
   #(condp = %
      \1 \0
      \2 \X
      \.)
   board))

(defn print-board [[a b c d e f g h i]]
  (print
   (format
    "\n    A B C \n  # # # #\n1 # %c %c %c \n2 # %c %c %c \n3 # %c %c %c \n"
    a b c d e f g h i)))

(let [win-patterns '([0 1 2] [3 4 5] [6 7 8] ;; 横
                     [0 3 6] [1 4 7] [2 5 8] ;; 縦
                     [0 4 8] [2 4 6])]       ;; 斜
  (defn win? [board opr]
    (some
     #(= 3 (count %))
     (map
      #(for [idx %
             :let [stone (nth board idx)]
             :when (opr stone)]
         idx)
      win-patterns))))

;;=============

(defn marubatsu-repl
  ([b mode] (marubatsu-repl b mode ([\1 \2] (rand-int 2))))

  ([b mode t]
   (if (= mode 0)
     (do
       ;; print
       (print-board (conv-num b))))

   (loop [turn t
          log (list b)
          idx
          (if (= mode 0)
            (if (= turn \1)

              ;; human
              (do
                (print "\nEnter [ q, u, a1 - c3 ]> ")
                (flush)

                (idxes (read-line)))

              ;; computer [think]
              (do
                (println "\n[ computer's turn ]")

                (:i
                 (first
                  (first
                   ;; スコアで並べる
                   (sort-by
                    #((first %) :s)
                    >
                    (vec
                     (rest
                      (com/think2 (first log) turn)
                      ))))))
                ))

            ;; computer [think]
            (:i
             (first
              (first
               ;; スコアで並べる
               (sort-by
                #((first %) :s)
                >
                (vec
                 (rest
                  (com/think2 (first log) turn)
                  ))))))

            )]

     (let [board_current (first log)]
       (if (or
            ;; 不正な入力
            (nil? ((set (range 9)) idx))
            ;; すでに埋まっているマス
            (#{\1 \2} (nth board_current idx)))

         ;; illegal, quit, undo
         (let [flg_undo (= 10 idx)
               log_undo (if (<= 3 (count log))
                          (rest (rest log))
                          log)]

           (if (= 9 idx)
             (do
               ;;(println log)
               (print-board (conv-num (first log)))
               (println "\n[ quit : O lose ]"))

             (do
               (println (str "\n[ " (if flg_undo "undo" "illegal") " ]"))

               ;; print
               (print-board
                (conv-num
                 (first (if flg_undo log_undo log))))

               (print "\nEnter [ q, u, a1 - c3 ]> ")
               (flush)

               (recur
                turn
                (if flg_undo log_undo log)
                (idxes (read-line))))))


         (let [board_new
               (put-stone board_current idx turn)

               log_new (conj log board_new)

               turn_new ([\1 \2] (- 2 (Integer/parseInt (str turn))))
               ]

           ;; ゲーム終了判定
           (if (win? board_new #(= turn %))

             ;; 終了１
             (do
               ;; print
               (print-board (conv-num board_new))

               (println
                (str
                 "\n[ the lead : "
                 (if (= \1 t) "O" "X")
                 " ] "

                 "[ end : " (if (= \1 turn)
                              "O" "X")  " wins ]")))

             (if (nil? ((set board_new) \0))
               ;; 終了２
               (do
                 ;; print
                 (print-board (conv-num board_new))

                 (println
                  (str
                   "\n[ the lead : "
                   (if (= \1 t) "O" "X")
                   " ] "
                   "[ end : draw ]")

                  ))

               ;; 継続
               (do
                 (if (= mode 0)
                   (do
                     ;; print
                     (print-board (conv-num board_new))

                     (if (= turn_new \1)
                       (do
                         (print "\nEnter [ q, u, a1 - c3 ]> ")
                         (flush))
                       (do
                         (println "\n[ computer's turn ]"))
                       )))

                 ;; turn and wait cmd
                 (recur
                  turn_new
                  log_new

                  (if (and (= mode 0)
                           (= turn_new \1))

                    ;; human
                    (idxes (read-line))

                    ;; computer [think]
                    (:i
                     (first
                      (first
                       ;; スコアで並べる
                       (sort-by
                        #((first %) :s)
                        >
                        (vec
                         (rest
                          (com/think2 (first log_new) turn_new)
                          ))))))
                    )))
               )))
         )))
   ))

(defn marubatsu-repl2 [all_board mode turn_start]
  (if (= mode 0)
    (do
      ;; print
      (print-board (conv-num (:b (first all_board))))))

  (loop [board all_board
         turn turn_start
         ;; undo 用の情報
         log []
         idx (if (= mode 0)
               (if (= turn \2)
                 ;; computer
                 (do
                   (println "\n[ computer's turn ]"))

                 ;; human
                 (do
                   (print "\nEnter [ q, u, a1 - c3 ]> ")
                   (flush)
                   ;; 入力値を idx に変換する
                   (idxes (read-line)))))
         ]

    ;;=====================
    ;; illegal, quit, undo
    ;;=====================
    (if (= 9 idx)
      ;; quit
      (do
        (print-board (conv-num (:b (first board))))
        (println "\n[ quit : O lose ]"))

      (if (= 10 idx)
        ;; undo
        (let [n (- (count log) 2)
              flg (> 0 n)
              ;; ベクタに変換しないと、ヘンな挙動になる
              log_undo (if flg log (vec (take n log)))
              board_undo
              (if flg
                board
                ;; 完全読みから指した手を辿る
                (first
                 (reduce
                  (fn [b {idx :i turn :t}]
                    (cons
                     (first
                      (filter
                       #(and
                         (= idx (:i (first %)))
                         (= turn (:t (first %))))

                       (rest (first b))))
                     b))
                  (list all_board)
                  log_undo)))
              ]

          ;; print
          (println "\n[ undo ]")
          (print-board (conv-num (:b (first board_undo))))

          (print "\nEnter [ q, u, a1 - c3 ]> ")
          (flush)

          (recur
           board_undo
           turn
           log_undo
           (idxes (read-line))))

        (if (and
             (not (nil? idx))
             (#{\1 \2} (nth (:b (first board)) idx)))

          ;; illegal
          (do
            (println "\n[ illegal ]")

            ;; print
            (print-board (conv-num (:b (first board))))

            (print "\nEnter [ q, u, a1 - c3 ]> ")
            (flush)

            (recur
             board
             turn
             log
             (idxes (read-line))))

          ;;======================
          ;; common
          ;;======================
          (let [board_rest (rest board)
                current
                (first
                 (if (nil? idx)
                   ;; computer
                   (sort-by #((first %) :s) > board_rest)

                   ;; human
                   (filter #(= idx (:i (first %))) board_rest)))
                board_current (:b (first current))
                idx_current (:i (first current))

                turn_new
                ([\1 \2] (- 2 (Integer/parseInt (str turn))))]

            ;; ゲーム終了判定
            (if (win? board_current #(= turn %))
              ;; 終了１
              (do
                ;; print
                (print-board (conv-num board_current))

                (println
                 (str
                  "\n[ the lead : "
                  (if (= \1 turn_start) "O" "X")
                  " ] "

                  "[ end : " (if (= \1 turn)
                               "O" "X")  " wins ]")))

              (if (nil? ((set board_current) \0))
                ;; 終了２
                (do
                  (print-board (conv-num board_current))

                  (println
                   (str
                    "\n[ the lead : "
                    (if (= \1 turn_start) "O" "X")
                    " ] [ end : draw ]")))

                ;; ゲーム継続
                (do
                  (if (= mode 0)
                    (do
                      ;; print
                      (print-board (conv-num board_current))

                      (if (= turn_new \1)
                        (do
                          (print "\nEnter [ q, u, a1 - c3 ]> ")
                          (flush))
                        (do
                          (println "\n[ computer's turn ]"))
                        )))

                  ;; turn and wait cmd
                  (recur
                   current
                   turn_new

                   ;; undo 用の情報
                   (conj log
                         {:i idx_current
                          ;; 相手の手番を設定する
                          :t ([\1 \2] (- 2 (Integer/parseInt (str turn))))
                          })

                   (if (and
                        (= mode 0)
                        (= turn_new \1))

                     ;; human
                     (idxes (read-line))

                     ;; computer
                     (:i
                      (first
                       (first
                        ;; スコアで並べる
                        (sort-by #((first %) :s) > (rest current)))))

                     ))))
              )))))
    ))

;;========================

(defn disp_rslt [board msg]
  (do
    ;; print
    (print-board (conv-num board))
    (println msg)))

(defn conv_input_to_idx
  ([] (conv_input_to_idx nil nil))
  ([board status]
   (do
     ;; print
     (if status
       (println (str "\n[ " status " ]")))

     (if board
       (print-board (conv-num board)))

     (print "\nEnter [ q, u, a1 - c3 ]> ")
     (flush)
     ;; 入力値を idx に変換する
     (idxes (read-line)))))

(defn get_idx [mode turn boardPrint data]
  (do
    (if (= mode 0)
      (do
        ;; print
        (print-board (conv-num boardPrint))

        (if (= turn \2)
          ;; computer
          (println "\n[ computer's turn ]"))))

    (if (and
         (= mode 0)
         (= turn \1))

      ;; human
      (conv_input_to_idx)

      ;; computer
      (if data
        (:i
         (first
          (first
           ;; スコアで並べる
           (sort-by #((first %) :s) > data))))))
    ))

(defn rewind [board log]
  (first
   (reduce
    (fn [b {idx :i turn :t}]
      (cons
       (first
        (filter
         #(and
           (= idx (:i (first %)))
           (= turn (:t (first %))))

         (rest (first b))))
       b))
    ;; acc
    (list board)
    ;; keys
    log)))

(defn conv_to_OX [turn]
  (if (= \1 turn) "O" "X"))

;; (defn get_turn_next [turn]
;;   ([\1 \2] (- 2 (Integer/parseInt (str turn)))))

;;------------------------

(defn marubatsu-repl3 [all_board mode turn_start]
  (loop [board all_board
         turn turn_start
         idx (get_idx mode turn (:b (first all_board)) nil)
         ;; undo 用の情報
         log []]

    ;;=====================
    ;; illegal, quit, undo
    ;;=====================
    (if (= 9 idx)
      ;; quit
      (disp_rslt (:b (first board)) "\n[ quit : O lose ]")

      (if (= 10 idx)
        ;; undo
        (let [n (- (count log) 2)
              flg (> 0 n)
              ;; ベクタに変換しないと、ヘンな挙動になる
              log_undo (if flg log (vec (take n log)))
              board_undo
              (if flg
                ;; 現在の状態のまま
                board
                ;; 特定の手まで、開始時点から「完全読み」を辿る
                (rewind all_board log_undo))]

          (recur
           board_undo
           turn
           (conv_input_to_idx (:b (first board_undo)) "undo")
           log_undo))

        (if (and
             ;; human
             (= mode 0) (= turn \1)
             (or
              ;; [ a1 - c3 ] 以外の入力
              (nil? idx)
              ;; すでに埋まっているマス
              (#{\1 \2} (nth (:b (first board)) idx))))

          ;; illegal
          (recur
           board
           turn
           (conv_input_to_idx (:b (first board)) "illegal")
           log)

          ;;======================
          ;; common
          ;;======================
          (let [board_rest (rest board)
                current
                (first
                 (if idx
                   ;; human
                   (filter #(= idx (:i (first %))) board_rest)
                   ;; computer
                   (sort-by #((first %) :s) > board_rest)))

                board_curr (:b (first current))
                idx_curr (:i (first current))
                turn_n (com/get_turn_next turn)]

            ;; ゲーム終了判定
            (if (win? board_curr #(= turn %))
              ;; 終了１
              (disp_rslt
               board_curr
               (str
                "\n[ the lead : " (conv_to_OX turn_start) " ] "
                "[ end : " (conv_to_OX turn) " wins ]"))

              (if (nil? ((set board_curr) \0))
                ;; 終了２
                (disp_rslt
                 board_curr
                 (str
                  "\n[ the lead : " (conv_to_OX turn_start) " ] "
                  "[ end : draw ]"))

                ;; turn and wait cmd
                (recur
                 current
                 turn_n
                 (get_idx mode turn_n board_curr (rest current))
                 ;; undo 用の情報
                 (conj log {:i idx_curr
                            ;; 相手の手番を設定する
                            :t turn_n})))

              ))
          )))))

;;========================

(defn disp-rslt3 [fnc-prt msg]
  (do
    ;; print
    (fnc-prt)
    (println msg)))

(defn get-idx3 [fnc-prt fnc mode turn data]
  (do
    (if (= mode 0)
      (do
        ;; print
        (fnc-prt)

        (if (= turn \2)
          ;; computer
          (println "\n[ computer's turn ]"))))

    (if (and
         (= mode 0)
         (= turn \1))

      ;; human
      ((fnc nil nil))

      ;; computer
      (if data
        (:i
         (first
          (first
           ;; スコアで並べる
           (sort-by #((first %) :s) > data))))))
    ))

(defn win2? [win-pttrns board opr size]
  (some
   #(= size (count %))
   (map
    #(for [idx %
           :let [stone (nth board idx)]
           :when (opr stone)]
       idx)
    win-pttrns)))

;;------------------------

(defn char-range [start end]
  (map char (range (int start) (inc (int end)))))

(defn gen-line [head lst]
  (reduce
   (fn [acc c] (str acc c " "))
   head
   lst))

;; カッコ悪いとこ

(defn gen-frame [n]
  (str
   ;; [ 1st line    :     A B C ]
   (gen-line "\n    " (take n (char-range \A \Z)))

   ;; [ 2nd line    :   # # # # ]
   (gen-line "\n  " (repeat (inc n) "#"))

   ;; [ other lines : n # 0 0 0 ]
   (apply str
          (for [i (range 1 (inc n))]
            (gen-line (str "\n" i " # ") (repeat n "%c"))))
   "\n"))

(defn gen-frame2 [n]
  (apply str
   `(
     ;; [ 1st line    :     A B C ]
     "\n    " ~@(interleave
                 (take n (char-range \A \Z))
                 (repeat n " "))

     ;; [ 2nd line    :   # # # # ]
     "\n  " ~@(repeat (inc n) "# ")

     ;; [ other lines : n # 0 0 0 ]
     ~@(for [i (range 1 (inc n))]
         (apply str "\n" i " # " (repeat n "%c ")))

     ;; 末尾
     "\n")))

(defn gen-frame3 [n]
  (str
   ;; [ 1st line    :     A B C ]
   (apply str "\n    "
          (interleave
           (take n (char-range \A \Z))
           (repeat n " ")))

   ;; [ 2nd line    :   # # # # ]
   (apply str "\n  "
          (repeat (inc n) "# "))

   ;; [ other lines : n # 0 0 0 ]
   (apply str
          (for [i (range 1 (inc n))]
            (apply str "\n" i " # " (repeat n "%c "))))
   ;; 末尾
   "\n"))

;; いちばん平易な書き方かな、と。

(defn gen-frame4 [n]
  (apply
   str
   (concat
    ;; [ 1st line    :     A B C ]
    "\n    " (interleave
              (take n (char-range \A \Z))
              (repeat n " "))

    ;; [ 2nd line    :   # # # # ]
    "\n  " (repeat (inc n) "# ")

    ;; [ other lines : n # 0 0 0 ]
    (for [i (range 1 (inc n))]
      (apply str "\n" i " # " (repeat n "%c ")))

    ;; 末尾
    "\n")))

(defn gen-frame4 [n]
  (apply
   str
   (concat
    ;; [ 1st line    :     A B C ]
    "\n     " (interleave
               (take n (char-range \A \Z))
               (repeat n " "))

    ;; [ 2nd line    :   # # # # ]
    "\n   " (repeat (inc n) "# ")

    ;; [ other lines : n # 0 0 0 ]
    (for [i (range 1 (inc n))]
      (apply str (format "\n%2d # " i) (repeat n "%c ")))

    ;; 末尾
    "\n")))

(defn gen-idx-keys [n ch-s ch-e]
  (apply concat
         (for [i (range 1 (inc n))]
           (for [ch (take n (char-range ch-s ch-e))]
             (str ch i)))))

;; カッコ悪いとこ、名前も未定

(defn baz3 [n]
  (list
   (interleave (gen-idx-keys n \a \z) (iterate inc 0))
   (interleave (gen-idx-keys n \A \Z) (iterate inc 0))

   '("Q" -1 "q" -1 "U" -2 "u" -2)))

(defn gen-maps [n]
  (apply sorted-map
   (apply concat
          '("Q" -1 "q" -1 "U" -2 "u" -2)
          (for [ [starg end] [[\a \z] [\A \Z]] ]
            (interleave
             (gen-idx-keys n starg end)
             (iterate inc 0)))
          )))

;;------------------------

(defn print-board2_ [frame data]
  (print
   (apply
    format
    (cons frame data))))

(defn print-board2 [frame data]
  (print
   (apply format frame data)))

(defn conv-input-to-idx3
  ([idx last-pos] (conv-input-to-idx3 idx last-pos nil nil))
  ([idx last-pos fnc-prt status]
   (do
     ;; print
     (if status
       (println (str "\n[ " status " ]")))

     (if fnc-prt
       (fnc-prt))

     (print (str "\nEnter [ q, u, a1 - " last-pos " ]> "))
     (flush)

     ;; 入力値を idx に変換する
     (idx (read-line))
     )))

;;------------------------

(defn marubatsu-repl4 [win-pttrns all-board mode turn-start size]

  (let [;;idxes (baz3 size)
        ;;idx (apply sorted-map (apply concat idxes))
        idxes (gen-maps size)
        pos-last (last (gen-idx-keys size \a \z))
        fnc (fn [f s] #(conv-input-to-idx3 idxes pos-last f s))

        frame (gen-frame4 size)
        fnc-prt (fn [b] #(print-board2 frame (conv-num b)))]

    ;; 処理本体

    (loop [board all-board
           turn turn-start
           idx (get-idx3
                (fnc-prt (:b (first all-board)))
                fnc
                mode
                turn
                nil)

           ;; undo 用の情報
           log []]

      ;;=====================
      ;; illegal, quit, undo
      ;;=====================
      (if (= -1 idx)
        ;; quit
        (disp-rslt3
         (fnc-prt (:b (first board))) "\n[ quit : O lose ]")

        (if (= -2 idx)
          ;; undo
          (let [n (- (count log) 2)
                flg (> 0 n)
                ;; ベクタに変換しないと、ヘンな挙動になる
                log-undo (if flg log (vec (take n log)))
                board-undo
                (if flg
                  ;; 現在の状態のまま
                  board
                  ;; 特定の手まで、開始時点から「完全読み」を辿る
                  (rewind all-board log-undo))]

            (recur
             board-undo
             turn
             ((fnc (fnc-prt (:b (first board-undo))) "undo"))
             log-undo))

          (if (and
               ;; human
               (= mode 0) (= turn \1)
               (or
                ;; [ a1 - c3 ] 以外の入力
                (nil? idx)
                ;; すでに埋まっているマス
                (#{\1 \2} (nth (:b (first board)) idx))))

            ;; illegal
            (recur
             board
             turn
             ((fnc (fnc-prt (:b (first board))) "illegal"))
             log)

            ;;======================
            ;; common
            ;;======================
            (let [board-rest (rest board)
                  current
                  (first
                   (if idx
                     ;; human
                     (filter #(= idx (:i (first %))) board-rest)
                     ;; computer
                     (sort-by #((first %) :s) > board-rest)))

                  board-curr (:b (first current))
                  idx-curr (:i (first current))
                  turn-n (com/get_turn_next turn)]

              ;; ゲーム終了判定
              (if (win2? win-pttrns board-curr #(= turn %) size)
                ;; 終了１
                (disp-rslt3
                 (fnc-prt board-curr)
                 (str
                  "\n[ the lead : " (conv_to_OX turn-start) " ] "
                  "[ end : " (conv_to_OX turn) " wins ]"))

                (if (nil? ((set board-curr) \0))
                  ;; 終了２
                  (disp-rslt3
                   (fnc-prt board-curr)
                   (str
                    "\n[ the lead : " (conv_to_OX turn-start) " ] "
                    "[ end : draw ]"))

                  ;; turn and wait cmd
                  (recur
                   current
                   turn-n

                   (get-idx3
                    (fnc-prt board-curr)
                    fnc
                    mode
                    turn-n
                    (rest current))

                   ;; undo 用の情報
                   (conj log {:i idx-curr
                              ;; 相手の手番を設定する
                              :t turn-n})))

                ))
            ))))))


;;========================

(defn conv-num2 [info]
  (loop [idx 0
         life (:l info)
         board (:b info)]

    (if (empty? life)
      board
      (recur
       (inc idx)
       (rest life)
       (assoc board idx
              (if (zero? (first life))
                ;; life:0 消える
                \.

                ;; 要調整：済
                ;; 2 だと、相手の消える石（小文字）が表示されない
                ;; 3 だと、自分と相手の消える石がともに表示される
                ;; （どっちが先に消えるのか、分かりにくい。当然、自分の石のほうが先に消えるんだが…）

                (if (< 2 (first life))
                  (if (= \1 (board idx)) \O \X)
                  (if (= \1 (board idx)) \o \x)))
       ))
      )))

(defn random-choosing-from-bests [data]
  (let [
        ;; スコアでソートした先頭の手を取得する
        best-choice
        (first
         (sort-by #((first %) :s) > data))

        ;; 最高スコアを取得する
        high-score (:s (first best-choice))

        ;; 最高スコアを持つ、すべての手を取得する
        bests
        (vec (filter #(= high-score (:s (first %))) data))

        ;; スコアが同じ場合、ランダムに手を選ぶ
        idx (rand-int (count bests))
        ]

    ;; 最高スコアの手のフィールド idx を返す
    (:i (first (bests idx)))
    ))

(defn get-idx4 [fnc-prt fnc mode turn data]
  (do
    (if (= mode 0)
      (do
        ;; print
        (fnc-prt)

        (if (= turn \2)
          ;; computer
          (println "\n[ computer's turn ]"))))

    (if (and
         (= mode 0)
         (= turn \1))

      ;; human
      ((fnc nil nil))

      ;; computer
      (if data
        (random-choosing-from-bests data)))
    ))

;;------------------------

(defn marubatsu-repl5 [win-pttrns all-board mode t-start size]

  (let [idxes (gen-maps size)
        pos-last (last (gen-idx-keys size \a \z))
        fnc (fn [f s] #(conv-input-to-idx3 idxes pos-last f s))

        frame (gen-frame4 size)
        fnc-prt (fn [b] #(print-board2 frame (conv-num2 b)))
        ]

    (loop [board all-board
           turn t-start
           idx (get-idx4
                (fnc-prt (first all-board))
                fnc
                mode
                turn
                nil)

           ;; undo 用の情報
           log []]

      ;;=====================
      ;; illegal, quit, undo
      ;;=====================
      (if (= -1 idx)
        ;; quit
        (disp-rslt3
         (fnc-prt (first board))
         "\n[ quit : O lose ]")

        (if (= -2 idx)
          ;; undo
          (let [n (- (count log) 2)
                flg (> 0 n)
                ;; ベクタに変換しないと、ヘンな挙動になる
                log-undo (if flg log (vec (take n log)))
                board-undo
                (if flg
                  ;; 現在の状態のまま
                  board
                  ;; 特定の手まで、開始時点から「完全読み」を辿る
                  (rewind all-board log-undo))]

            (recur
             board-undo
             turn
             ((fnc (fnc-prt (first board-undo)) "undo"))
             log-undo))

          (if (and
               ;; human
               (= mode 0) (= turn \1)
               (or
                ;; [ a1 - c3 ] 以外の入力
                (nil? idx)
                ;; すでに埋まっているマス
                (#{\1 \2} (nth (:b (first board)) idx))))

            ;; illegal
            (recur
             board
             turn
             ((fnc (fnc-prt (first board)) "illegal"))
             log)

            ;;======================
            ;; common
            ;;======================
            (let [turn-n (com/get_turn_next turn)

                  board-rest (rest board)

                  current
                  (first
                   (if idx
                     ;; human
                     (filter #(= idx (:i (first %))) board-rest)
                     ;; computer
                     (sort-by #((first %) :s) > board-rest)))

                  board-curr (:b (first current))
                  idx-curr (:i (first current))]

              ;; ゲーム終了判定
              (if (win2? win-pttrns board-curr #(= turn %) size)
                ;; 終了１
                (disp-rslt3
                 (fnc-prt (first current))
                 (str
                  "\n[ the lead : " (conv_to_OX t-start) " ]"
                  " [ end : " (conv_to_OX turn) " wins ]"))

                (if (nil? ((set board-curr) \0))
                  ;; 終了２
                  (disp-rslt3
                   (fnc-prt (first current))
                   (str
                    "\n[ the lead : " (conv_to_OX t-start) " ]"
                    " [ end : draw ]"))

                  ;; turn and wait cmd
                  (recur
                   current
                   turn-n
                   (get-idx4
                    (fnc-prt (first current))
                    fnc
                    mode
                    turn-n
                    (rest current))

                   ;; undo 用の情報
                   (conj log {:i idx-curr
                              ;; 相手の手番を設定する
                              :t turn-n})))

                ))
            ))))))

;;========================

;; カウントを導入
;; think5 以降、「引き分け」はないので処理を削除した。

(defn marubatsu-repl6 [win-pttrns all-board mode t-start size]

  (let [idxes (gen-maps size)
        pos-last (last (gen-idx-keys size \a \z))
        fnc (fn [f s] #(conv-input-to-idx3 idxes pos-last f s))

        frame (gen-frame4 size)
        fnc-prt (fn [b] #(print-board2 frame (conv-num2 b)))
        ]

    (loop [board all-board
           turn t-start
           idx (get-idx4
                (fnc-prt (first all-board))
                fnc
                mode
                turn
                nil)

           ;; undo 用の情報
           log []]

      ;;=====================
      ;; illegal, quit, undo
      ;;=====================
      (if (= -1 idx)
        ;; quit
        (disp-rslt3
         (fnc-prt (first board))
         "\n[ quit : O lose ]")

        (if (= -2 idx)
          ;; undo
          (let [n (- (count log) 2)
                flg (neg? n)
                ;; ベクタに変換しないと、ヘンな挙動になる
                log-undo (if flg log (vec (take n log)))
                board-undo
                (if flg
                  ;; 現在の状態のまま
                  board
                  ;; 特定の手まで、開始時点から「完全読み」を辿る
                  (rewind all-board log-undo))]

            (recur
             board-undo
             turn
             ((fnc (fnc-prt (first board-undo)) "undo"))
             log-undo))

          (if (and
               ;; human
               (= mode 0) (= turn \1)
               (or
                ;; [ a1 - c3 ] 以外の入力
                (nil? idx)
                ;; すでに埋まっているマス
                (#{\1 \2} (nth (:b (first board)) idx))))

            ;; illegal
            (recur
             board
             turn
             ((fnc (fnc-prt (first board)) "illegal"))
             log)

            ;;======================
            ;; common
            ;;======================
            (let [turn-n (com/get_turn_next turn)
                  board-rest (rest board)
                  current
                  (first
                   (if idx
                     ;; human
                     (filter #(= idx (:i (first %))) board-rest)
                     ;; computer
                     (sort-by #((first %) :s) > board-rest)))

                  board-curr (:b (first current))
                  idx-curr (:i (first current))]

              ;; ゲーム終了判定
              (if (win2? win-pttrns board-curr #(= turn %) size)
                ;; 終了１
                (disp-rslt3
                 (fnc-prt (first current))
                 (str
                  "\n[ the lead : " (conv_to_OX t-start) " ]"
                  " [ end : " (conv_to_OX turn) " wins ]"
                  " [ cnt : " (inc (count log)) " ]"
                  ))

                ;; turn and wait cmd
                (recur
                 current
                 turn-n
                 (get-idx4
                  (fnc-prt (first current))
                  fnc
                  mode
                  turn-n
                  (rest current))

                 ;; undo 用の情報
                 (conj log {:i idx-curr
                            ;; 相手の手番を設定する
                            :t turn-n}))
                ))
            ))))))

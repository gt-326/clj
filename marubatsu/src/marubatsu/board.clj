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

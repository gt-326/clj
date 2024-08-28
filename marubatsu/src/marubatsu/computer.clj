(ns marubatsu.computer)

;; ==============

(defn canPutIdxes [board]
  (for [i (range (count board))
        :when (= \0 (board i))]  i))

;;-----------------

(defn get_board_child [board turn]
  (loop [idxes (canPutIdxes board)
         result '()]
    (if (empty? idxes)
      result
      (recur
       (rest idxes)
       (conj result {:idx (first idxes)
                     :board (assoc board (first idxes) turn)}))
      )))

;;=================

(defn get_current_scores [board idxes my_stone]
  (map
   #(condp = (board %)
      my_stone 1
      \0 0
      -1)
   idxes))

(defn get_guard_points [board idxes opponent_stone]
  (map
   #(if (= opponent_stone (board %)) 1 0)
   idxes))

(let [lines
      '([0 1 2] [3 4 5] [6 7 8] ;; 横
        [0 3 6] [1 4 7] [2 5 8] ;; 縦
        [0 4 8] [2 4 6])]       ;; 斜

  (defn get_lines_to_win [board]
    (for [idx (canPutIdxes board)
          :let [vec_positions
                (for [l lines
                      :when (some #(= % idx) l)] l)]]
      {:idx idx :lines vec_positions})))

;;=================

(let [base_score [3 2 3 2 4 2 3 2 3]]
  (defn get_position_scores [board turn i]
    (for [position_info (get_lines_to_win board)
          :let [idx (position_info :idx)
                situation_scores
                (for [idxes (position_info :lines)]
                  (apply + (get_current_scores board idxes turn)))]
          :when (= idx i)]
      {:idx idx
       :score (+ (apply + situation_scores)
                 (base_score idx))})
    ))

(let [base_score [3 2 3 2 4 2 3 2 3]]
  (defn get_position_scores2 [board turn i]
    (for [position_info (get_lines_to_win board)
          :let [idx (position_info :idx)

                situation_scores
                (for [idxes (position_info :lines)]
                  (apply + (get_current_scores board idxes turn)))

                ;; 相手の王手をガードしたときのポイント
                guard_scores
                (for [idxes (position_info :lines)]
                  (apply + (get_guard_points
                            board
                            idxes
                            ([\1 \2]
                             (- 2 (Integer/parseInt (str turn)))))))]
          :when (= idx i)]

      {:idx idx
       :score (+ (base_score idx)
                 ;; 手のスコアを合計すると、他の手との差があいまいになる
                 ;;(apply + situation_scores)

                 (first (sort > situation_scores))

                 (if (< 1 (first (sort > guard_scores))) 4 0)
                 )
       }
      )
    ))

;;=================

;; 弱い

(defn think
  ([board turn] (think board turn -1 -1))
  ([board turn idx score]
   (concat
    (list {:b board
           :t turn
           :i idx
           :s score})
    (map
     #(think
       (% :board)
       ([\1 \2] (- 2 (Integer/parseInt (str turn))))
       (% :idx)

       (:score
        ;; 先頭のみ
        (first
         ;; スコアの算出方法に改良の余地あり？
         (get_position_scores
          board
          turn
          (% :idx)))))

     (get_board_child board turn)))
   ))

;;=================

;; 強い（けれど、引き分けにしかならない）
;; 自分の勝ちよりも、相手の王手への対応を優先するケースがみられる

(defn think2
  ([board turn] (think2 board turn -1 -1))
  ([board turn idx score]
   (concat
    (list {:b board
           :t turn
           :i idx
           :s score})
    (map
     #(think2
       (% :board)
       ([\1 \2] (- 2 (Integer/parseInt (str turn))))
       (% :idx)

       (:score
        ;; 先頭のみ
        (first
         ;; 改良箇所
         (get_position_scores2
          board
          turn
          (% :idx)))))

     (get_board_child board turn)))
   ))

;;=================

(defn get_turn_next [turn]
  ([\1 \2] (- 2 (Integer/parseInt (str turn)))))

(defn get_current_scores2 [board idxes my_stone]
  (map
   #(condp = (board %)
      my_stone 2
      \0 1
      0)
   idxes))

(let [base_score [1 0 1 0 2 0 1 0 1]]
  (defn get_position_scores3 [board turn i]
    (for [position_info (get_lines_to_win board)
          :let [idx (position_info :idx)

                ;; 相手の王手をガードしたときのポイント
                guard_score
                (first
                 (sort >
                       (for [idxes (position_info :lines)]
                         (apply + (get_guard_points
                                   board
                                   idxes
                                   (get_turn_next turn))))))

                ;; 取りうる手なかでの最高スコア
                situation_score
                (first
                 (sort >
                       (for [idxes (position_info :lines)]
                         (apply + (get_current_scores2
                                   board
                                   idxes
                                   turn)))))]

          :when (= idx i)]

      {:idx idx
       :score (+ (base_score idx)
                 ;; 相手の王手に対応したら：３ポイント
                 (if (< 1 guard_score) 3 0)

                 ;; 自分が勝ちになる手なら：４ポイント（以上）を加算する
                 situation_score
                 (if (< 4 situation_score) 4 0))}
      )))

;;-----------------

;; 相手の王手への対応よりも、自分の勝ちを優先するよう改修した

(defn think3
  ([board turn] (think3 board turn -1 -1))
  ([board turn idx score]
   (concat
    (list {:b board
           :t turn
           :i idx
           :s score})
    (map
     #(think3
       (% :board)
       (get_turn_next turn)
       (% :idx)

       (:score
        (first
         ;; 改良箇所
         (get_position_scores3
          board
          turn
          (% :idx)))))

     (get_board_child board turn)))
   ))

;;=================

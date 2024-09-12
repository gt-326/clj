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

(defn get-lines-to-win2 [lines board]
  (for [idx (canPutIdxes board)
        :let [vec-positions
              (for [l lines
                    :when (some #(= % idx) l)] l)]]
    {:idx idx :lines vec-positions}))

(defn get-guard-points2 [board idxes opponent-stone]
  (map
   #(if (= opponent-stone (board %)) 1 0)
   idxes))

(defn get-current-scores3 [board idxes my-stone]
  (map
   #(condp = (board %)
      my-stone 2
      \0 1
      0)
   idxes))

(defn get-position-scores4 [lines base-score size board turn i]
  (for [position-info (get-lines-to-win2 lines board)
        :let [idx (position-info :idx)

              ;; 相手の王手をガードしたときのポイント
              guard-score
              (first
               (sort >
                     (for [idxes (position-info :lines)]
                       (apply +
                              ;; 改修箇所
                              (get-guard-points2
                               board
                               idxes
                               (get_turn_next turn))))))

              ;; 取りうる手なかでの最高スコア
              situation-score
              (first
               (sort >
                     (for [idxes (position-info :lines)]
                       (apply +
                              ;; 改修箇所
                              (get-current-scores3
                               board
                               idxes
                               turn)))))]

        :when (= idx i)]

    ;;=== 要調整  ===

    {:idx idx
     :score (+ (base-score idx)
               ;; 相手の王手に対応したら
               ;;(if (< 1 guard-score) 3 0)
               (if (<= (dec size) guard-score) size 0)

               ;; 自分が勝ちになる手なら
               situation-score
               ;;(if (< 4 situation-score) 4 0)
               (if (< (* 2 (dec size)) situation-score)
                 (inc size) 0)
               )}
    ))

;; 四つ角と中央にポイントを付与している

(defn gen-base-score [n]
  (loop [idxes (list
                ;; 四つ角：１点
                0
                (dec n)
                (- (* n n) n)
                (dec (* n n))

                ;; 中央：２点
                (quot (* n n) 2)
                (quot (* n n) 2))

         score (vec (repeat (* n n) 0))]

    (if (empty? idxes)
      score
      (recur
       (rest idxes)
       (update score (first idxes) inc)))))

;; 斜めにポイントを付与している

(defn gen-base-score2 [n]
  (loop [idxes (concat
                (range 0 (* n n) (inc n))
                (range (dec n) (dec (* n n)) (dec n)))

         score (vec (repeat (* n n) 0))]

    (if (empty? idxes)
      score
      (recur
       (rest idxes)
       (update score (first idxes) inc)))))

;;-----------------

;; ボードサイズを可変長にするよう改修した

(defn think4
  ([win-patterns board turn]
   (think4 win-patterns board turn -1 -1))

  ([win-patterns board turn idx score]
   (let [size (int (Math/sqrt (count board)))]
     (concat
      (list {:b board
             :t turn
             :i idx
             :s score})
      (map
       #(think4
         win-patterns
         (% :board)
         (get_turn_next turn)
         (% :idx)

         (:score
          (first
           ;; 改良箇所
           (get-position-scores4
            win-patterns
            (gen-base-score2 size)
            size
            board
            turn
            (% :idx)))))

       (get_board_child board turn))))
   ))

;;=================

;; 新ルール「一定時間経過すると、石が消滅する」を導入してみる

(defn update-lives [lives]
  (vec
   (map #(if (zero? %) 0 (dec %)) lives)))

(defn update-board [board lives num]
  (loop [idx 0
         l lives
         b board]

    (if (empty? l)
      b
      (recur
       (inc idx)
       (rest l)
       (assoc b idx (if (= num (first l)) \0 (b idx)))))))

(defn update-board2 [board lives fnc]
  (loop [idx 0
         l lives
         b board]

    (if (empty? l)
      b
      (recur
       (inc idx)
       (rest l)
       (assoc b idx (if (fnc (first l) (b idx)) \0 (b idx)))))))

(defn get-board-child2 [board lives life-max turn]
  (loop [idxes (canPutIdxes board)
         result '()]
    (if (empty? idxes)
      result
      (recur
       (rest idxes)
       (conj result {:idx (first idxes)
                     :board (assoc board (first idxes) turn)
                     ;; 改修箇所
                     :lives (assoc lives (first idxes) life-max)
                     }))
      )))

;;-----------------

;; 石が消える新ルールを導入したことで分かったが、
;; 「つぎのターンでリーチになる」、そういう手があるので、
;; それを検出するための関数群（foo、bar、baz は履歴として残している）。

(defn foo [board lives turn]
  (loop [idx 0
         l lives]

    (if (and
         ;; life:2 でないと引っ掛からない
         (= 2 (first l))
         (= (board idx) turn))
      idx
      (if (empty? l)
        nil
        (recur (inc idx) (rest l))))
    ))

;; foo
(defn search-vanishing-idx [board lives turn]
  (loop [idx 0
         l lives]

    (if (and
         ;; life:2 でないと引っ掛からない
         (= 2 (first l))
         (= (board idx) turn))
      idx
      (if (empty? l)
        nil
        (recur (inc idx) (rest l))))
    ))

;;-----------------

(defn bar_ [board live pattern turn]

  (let [vanishing-idx (foo board live turn)]

    ;; 相手の石のうち、つぎのつぎのターンで消えるものがあるか？
    (if (nil? vanishing-idx)
      '()

      ;; その石を含む、tate yoko naname をピックアップ
      (filter
       (fn [l] (some #(= vanishing-idx %) l))
       pattern)
      )
    ))

(defn bar [idx pattern]
  ;; 相手の石のうち、つぎのつぎのターンで消えるものがあるか？
  (if (nil? idx)
    '()

    ;; その石を含む、tate yoko naname をピックアップ
    (filter
     (fn [l] (some #(= idx %) l))
     pattern)
    ))

(defn bar2 [idxes pattern]
  (if (nil? (first idxes))
    '()

    ;; 最後の処理結果として、リストの先頭を取得する
    (first
     (reduce
      (fn [pttrn idx]
        ;; 処理結果を履歴として保持している
        (cons
         (filter
          (fn [l] (some #(= idx %) l))
          (first pttrn))
         pttrn))

      ;; acc
      (list pattern)

      ;; list
      idxes))
))

(defn bar2-2 [idxes pattern]
  (if (nil? (first idxes))
    '()
    (first
     (reduce
      (fn [pttrn idx]
        (filter
         (fn [l] (some #(= idx %) l))
         pttrn))
      ;; acc
      pattern
      ;; list
      idxes))
    ))

(defn bar3 [list-idx pattern]
  (loop [idxes list-idx
         rslt pattern]

    (if (empty? idxes)
      rslt
      (if (nil? (first idxes))
        '()
        (recur
         (rest idxes)
         (filter
          ;;(fn [l] (some (fn [i] (= (first idxes) i)) l))
          (fn [l] (some #(= (first idxes) %) l))
          rslt))))))

;; bar3
(defn update-l [list-idx pattern]
  (loop [idxes list-idx
         rslt pattern]

    (if (empty? idxes)
      rslt
      (if (nil? (first idxes))
        '()

        (recur
         (rest idxes)
         (filter
          (fn [l] (some #(= (first idxes) %) l))
          rslt))
         ))))

;;-----------------

(defn baz [board lives pattern turn idx]
  (let [vanishing-idx (foo board lives turn)

        ;; ナイーブ
        ;;idxes (bar vanishing-idx pattern)
        ;;lines-new (filter (fn [l] (some #(= idx %) l)) idxes)

        ;; シンプル（同じ関数を、必要な回数分呼び出している）
        ;;idxes (bar vanishing-idx pattern)
        ;;lines-new (bar idx idxes)

        ;; reduce 版（不必要に過去の処理結果を保持する）
        lines-new (bar2 [vanishing-idx idx] pattern)

        ;; loop/recur 版（処理結果を上書きする）
        ;;lines-new (bar3 [vanishing-idx idx] pattern)
        ]

    (some
     ;; 自分の石：１、空白：１をリーチ対象とする
     #(= 3 %)
     (map
      #(apply +
              (get-current-scores3
               ;; life:1 の自石を除いた状態
               board
               %
               turn))
      lines-new))
    ))

;; baz
(defn has-reach? [size board turn lines]
  (let [scores
        (map
         #(apply +
                 (get-current-scores3 board % turn))
         lines)]
    (some
     ;; ポイント
     ;; [ 相手の石 ]＋[ 空白 ]＋( [ 自分の石 ]：n ）
     #(= (* 2 (- size 2)) %)
     scores)
    )
)

(defn get-position-scores5
  [lines base-score size board lives turn i]

  (let [
        ;; つぎに置く石（idx）と入れ替わりで消える life:1 の石
        ;; それをないものとして、それぞれの手のスコアを計算する
        board-next
        (update-board2
         board
         lives
         #(and (= %1 1) (= %2 turn)))

        ;; 相手のターン
        turn-next
        (get_turn_next turn)

        ;; 相手の消える石の idx を取得する
        vanishing-idx
        (search-vanishing-idx board lives turn-next)
        ]

    (for [position-info (get-lines-to-win2 lines board-next)
          :let [idx (position-info :idx)

                ;; 相手の王手をガードしたときのポイント
                guard-score
                (first
                 (sort >
                       (for [idxes (position-info :lines)]
                         (apply +
                                ;; 改修箇所
                                (get-guard-points2
                                 board
                                 idxes
                                 turn-next)))))

                ;; 取りうる手なかでの最高スコア
                situation-score
                (first
                 (sort >
                       (for [idxes (position-info :lines)]
                         (apply +
                                ;; 改修箇所
                                (get-current-scores3
                                 ;; life:1 の自石を除いた状態
                                 board-next
                                 idxes
                                 turn)))))

                ;; リーチになるか？
                lines-update
                (update-l [vanishing-idx idx] lines)

                reach-flg
                (has-reach? size board-next turn-next lines-update)
                ]

          :when (= idx i)]

      {:idx idx
       :score (+ (base-score idx)
                 ;; リーチポイント
                 ;; 相手の王手に対応したときより低いポイントを設定する
                 (if reach-flg (dec size) 0)

                 ;; 相手の王手に対応したら
                 ;; 自分の勝ちより低いポイントを設定する
                 (if (<= (dec size) guard-score) size 0)

                 ;; 自分が勝ちになる手なら
                 situation-score
                 (if (< (* 2 (dec size)) situation-score)
                   (inc size) 0))
       }
      )))

;;-----------------

(defn think5
  ([win-patterns board lives size turn]
   (think5 win-patterns board lives size turn -1 -1))

  ([win-patterns board lives size turn idx score]
   (let [
         ;; 1. dec lives
         lives-new (update-lives lives)
         ;; 2. update board
         board-new (update-board board lives-new 0)
         ;; 要調整：済
         life-max (- (* size size) (dec size))
         ]

     (concat

      ;; ※think4 までは、このハッシュマップデータには関数呼び出し時に
      ;; 引数に渡された値（board、lives）を設定していたが、新ルール下
      ;; では、それがデッドエンド状態になる原因になるみたい。

      ;; 以下のように、更新した値（-new）を設定すると解消するみたい。

      (list {:b ;;board
                board-new
             :t turn
             :i idx
             :s score

             ;; 改修箇所
             :l ;;lives
                lives-new
             })

      ;; 再帰：
      (map
       #(think5
         win-patterns
         (% :board)

         ;; 改修箇所
         (% :lives)
         size

         (get_turn_next turn)
         (% :idx)

         (:score
          (first
           ;; 改修箇所
           (get-position-scores5
            win-patterns
            (gen-base-score2 size)
            size

            ;; 改良箇所
            board-new
            lives-new

            turn
            (% :idx))))
         )

       (get-board-child2 board-new lives-new life-max turn))
      ))
   ))

;;=================

;; 遅延評価を導入したもの

(defn think6
  ([win-patterns board lives size turn]
   (think6 win-patterns board lives size turn -1 -1))

  ([win-patterns board lives size turn idx score]
   (let [life-max (- (* size size) (dec size))
         ;; 1. dec lives
         lives-new (update-lives lives)
         ;; 2. update board
         board-new (update-board board lives-new 0)]

     ;; 改修箇所
     (lazy-cat

      (list {:b board-new
             :t turn
             :i idx
             :s score
             :l lives-new})
      (map

       (fn [{:keys [board lives idx]}]
         (think6
          win-patterns
          board
          lives
          size
          (get_turn_next turn)
          idx

          (:score
           (first
            (get-position-scores5
             win-patterns
             (gen-base-score2 size)
             size
             board-new
             lives-new
             turn
             idx)))
          ))

       (get-board-child2 board-new lives-new life-max turn))
      ))))

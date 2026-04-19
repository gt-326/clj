(ns onlisp.chap22.nondetermin
  (:require
    [onlisp.chap22.common :as c]
    [onlisp.common.util :as u]))


;; [ P303 chap22.4 ]


;; *cont* のデフォルトは identity なので、
;; two-numbers を単独で実行しようとすると、エラーになる。

(u/=defn two-numbers []
         (c/choose-bind n1 '(1 2)
                        (c/choose-bind n2 '(3 4)
                                       (u/=values n1 n2))))


(comment

  ;;  onlisp.core=> (two-numbers)
  ;;  Execution error (ArityException) at onlisp.core/=two-numbers$fn$fn (form-init12722559506133341568.clj:4).
  ;;  Wrong number of args (2) passed to: clojure.core/identity

  )


;; =bind で、*cont* の値を引数を２つ取る式に書き換えた上で、
;; two-numbers を呼び出しているので、上記エラーが起こることはない、という流れ。

(u/=defn parlor-trick [sum]
         (reset! c/PATHS [])
         (u/=bind [n1 n2] (two-numbers)
                  (if (= sum (+ n1 n2))
                    (format "the sum of %s %s" n1 n2)
                    (c/fail))))


(comment

;;  onlisp.core=> (parlor-trick 100)
;;  [end]
;;  onlisp.core=> (parlor-trick 0)
;;  [end]
;;  onlisp.core=> (parlor-trick 4)
;;  "the sum of 1 3"

  )


;; [ P305 chap22.4 ]

(defn kids
  [n]
  (case n
    a '(b c)
    b '(d e)
    c '(d f)
    f '(g)

    ;; ガードが必要
    ;; Execution error (IllegalArgumentException) at foo.core/kids (REPL:840).
    ;; No matching clause: z
    '()))


;; 再帰実装（PATHS をリセットしない）
(u/=defn descent-impl [n1 n2]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (kids n1)
           (c/choose-bind n (kids n1)
                          (u/=bind [p] (descent-impl n n2)
                                   (u/=values (cons n1 p))))
           :else
           (c/fail)))


;; エントリポイント（PATHS をリセットしてから再帰実装に委譲）
(u/=defn descent [n1 n2]
         (reset! c/PATHS [])
         (descent-impl n1 n2))


(comment

;;  onlisp.core=> (kids 'a)
;;  (b c)
;;  onlisp.core=> (kids 'z)
;;  ()

;;  onlisp.core=> (descent 'a 'g)
;;  (a c f g)
;;  onlisp.core=> (c/fail)
;;  [end]

;;  onlisp.core=> (descent 'a 'd)
;;  (a b d)
;;  onlisp.core=> (c/fail)
;;  (a c d)
;;  onlisp.core=> (c/fail)
;;  [end]

  )


;; [ P305 - 308 chap22.5 ]


(u/=defn gen-sent-candy-log []
         (c/choose-bind c '(LA NY BOS) ; 街
                        (c/choose-bind s '(1 2) ; 店
                                       (c/choose-bind b '(1 2) ; 箱
                                                      (u/=values (list c s b))))))


(comment

;;  onlisp.core=> (gen-sent-candy-log)
;;  (LA 1 1)
;;  onlisp.core=> (c/fail)
;;  (LA 1 2)
;;  onlisp.core=> (c/fail)
;;  (LA 2 1)
;;  onlisp.core=> (c/fail)
;;  (LA 2 2)
;;  onlisp.core=> (c/fail)
;;  (NY 1 1)
;;  onlisp.core=> (c/fail)
;;  (NY 1 2)
;;  onlisp.core=> (c/fail)
;;  (NY 2 1)
;;  onlisp.core=> (c/fail)
;;  (NY 2 2)
;;  onlisp.core=> (c/fail)
;;  (BOS 1 1)
;;  onlisp.core=> (c/fail)
;;  (BOS 1 2)
;;  onlisp.core=> (c/fail)
;;  (BOS 2 1)
;;  onlisp.core=> (c/fail)
;;  (BOS 2 2)
;;  onlisp.core=> (c/fail)
;;  [end]

  )


;; コインの入ってる箱が分かっているなら、探索なんかしないでいいだろ、と思わなくもないが…。
;; 「コインの入ってる箱は、１つの街に１箱だけ」という前提。

(defn coin?
  [x]
  (.contains ['(LA 1 2) '(NY 1 1) '(BOS 2 1)] x))


;; 総当たり。
;; １つの街でコインの入った箱が見つかった後も、その街の残りの箱を探索する。

(u/=defn find-boxes []
         (reset! c/PATHS [])
         (u/=bind [triple] (gen-sent-candy-log)
                  (let [t triple]
                    (print t (if (coin? t) "C \n" ""))
                    (c/fail))))


;; コイン入りの箱が見つかったら、探索対象を次の街に移す。
;; その機能「カット」を模したらこんな感じかな、というもの。
;; …ただ、けっきょく表示処理をバイパスしているだけのもので、総当たりには変わりない。

(let [city (atom nil)]
  (u/=defn find-boxes2 []
           (reset! c/PATHS [])
           (u/=bind [triple] (gen-sent-candy-log)
                    (let [t triple
                          city-current (first t)
                          flg (coin? t)]

                      (when-not (= city-current @city)
                        (print t (if flg "C \n" ""))
                        (reset! city (if flg city-current "")))

                      (c/fail)))))


(comment

;;  onlisp.core=> (find-boxes)
;;  (LA 1 1) (LA 1 2) C
;;  (LA 2 1) (LA 2 2) (NY 1 1) C
;;  (NY 1 2) (NY 2 1) (NY 2 2) (BOS 1 1) (BOS 1 2) (BOS 2 1) (BOS 2 1) C
;;  (BOS 2 2) [end]

;;  onlisp.core=> (find-boxes2)
;;  (LA 1 1) (LA 1 2) C
;;  (NY 1 1) C
;;  (BOS 1 1) (BOS 1 2) (BOS 2 1) C
;;  [end]

  )


;; ==================


;; そもそも coin? の作りが気に入らない。
;; 自分なりのヤツを。

(defn rand-coins
  [cities store-num box-num]
  (let [coins (atom [])
        cnt-cities (count cities)]
    ;; ざっくり 20 回も回せば、被りなく必要な件数を取得できるだろう、と。
    (doseq [_ (range 20)
            :let [coin `(~(get cities (int (rand cnt-cities)))
                         ~(inc (int (rand store-num)))
                         ~(inc (int (rand box-num))))]]
      ;; 登録済みのやつと被ってなければ、追加する
      (when-not (.contains @coins coin)
        (swap! coins conj coin)))
    ;; 登録済みのやつを逆順にして、そこから街の数だけ取得する
    (vec (take cnt-cities (reverse @coins)))))


(u/=defn gen-sent-candy-log2 [cities store-num box-num]
         (let [stores (map inc (range store-num))
               boxes (map inc (range box-num))]

           (c/choose-bind c
                          cities ; 街
                          (c/choose-bind s
                                         stores ; 店
                                         (c/choose-bind b
                                                        boxes ; 箱
                                                        (u/=values (list c s b)))))))


(comment

;;  onlisp.core=> (core/rand-coins '[LA NY BOS] 2 2)
;;  [(BOS 1 2) (LA 1 2) (LA 1 1)]

;;  onlisp.core=> (core/rand-coins '[LA NY BOS WA] 3 2)
;;  [(BOS 1 2) (LA 2 2) (WA 2 2) (WA 3 2)]

;;  onlisp.core=> (core/gen-sent-candy-log2 '[LA NY BOS WA] 3 2)
;;  (LA 1 1)

;;  onlisp.core=> (c/fail)
;;  (LA 1 2)

  )


(u/=defn find-boxes3 [cities store-num box-num]
         (reset! c/PATHS [])
         ;; 「コインの入っている箱」を毎回ランダムに決めている
         (let [coins (rand-coins cities store-num box-num)]

           (println "coins:" coins)

           (u/=bind [triple] (gen-sent-candy-log2 cities store-num box-num)
                    (let [t triple]
                      (print t (if (.contains coins t) "C \n" ""))
                      (c/fail)))))


;; うまいこと動いているみたい。
;; 固定値を前提とする coins? とはちがい、rand-coins はより本物にちかいランダム性をもつ。

(comment

;;  onlisp.core=> (core/find-boxes3 '[LA NY BOS WA] 1 2)
;;  coins: [(LA 1 1) (BOS 1 1) (WA 1 1) (LA 1 2)]
;;  (LA 1 1) C
;;  (LA 1 2) C
;;  (NY 1 1) (NY 1 2) (BOS 1 1) C
;;  (BOS 1 2) (WA 1 1) C
;;  (WA 1 2) [end]

;;  onlisp.core=> (core/find-boxes3 '[LA NY BOS WA] 3 2)
;;  coins: [(NY 2 1) (BOS 1 1) (BOS 2 2) (LA 2 1)]
;;  (LA 1 1) (LA 1 2) (LA 2 1) C
;;  (LA 2 2) (LA 3 1) (LA 3 2) (NY 1 1) (NY 1 2) (NY 2 1) C
;;  (NY 2 2) (NY 3 1) (NY 3 2) (BOS 1 1) C
;;  (BOS 1 2) (BOS 2 1) (BOS 2 2) C
;;  (BOS 3 1) (BOS 3 2) (WA 1 1) (WA 1 2) (WA 2 1) (WA 2 2) (WA 3 1) (WA 3 2) [end]

  )


;; すべて見つけたら、その時点で処理を終了する。
;; …ように見せているが、けっきょく全件なめていることが見てとれる（何度も actual が…）。

(u/=defn find-boxes4 [cities store-num box-num]
         (reset! c/PATHS [])
         ;; 「コインの入っている箱」を毎回ランダムに決めている
         (let [coins (rand-coins cities store-num box-num)
               hit (atom 0)
               cnt (atom 0)]

           (println "coins:" coins)
           (println "total:" (* (count cities) store-num box-num))

           (u/=bind [triple] (gen-sent-candy-log2 cities store-num box-num)
                    (let [t triple]
                      (if (>= @hit (count coins))
                        (println "actual:" @cnt)
                        (do
                          (swap! cnt inc)
                          (print t (if (.contains coins t)
                                     (do
                                       (swap! hit inc)
                                       "C \n")
                                     ""))))
                      (c/fail)))))


(comment

;;  onlisp.core=> (core/find-boxes4 '[LA NY BOS WA] 3 2)
;;  coins: [(LA 3 2) (BOS 1 2) (BOS 3 2) (NY 1 1)]
;;  total: 24
;;  (LA 1 1) (LA 1 2) (LA 2 1) (LA 2 2) (LA 3 1) (LA 3 2) C
;;  (NY 1 1) C
;;  (NY 1 2) (NY 2 1) (NY 2 2) (NY 3 1) (NY 3 2) (BOS 1 1) (BOS 1 2) C
;;  (BOS 2 1) (BOS 2 2) (BOS 3 1) (BOS 3 2) C
;;  actual: 18
;;  actual: 18
;;  actual: 18
;;  actual: 18
;;  actual: 18
;;  actual: 18
;;  [end]

  )


;; mark / cut を試してみる

(defn mark
  []
  (swap! c/PATHS conj c/fail))


(defn cut
  []
  (if (seq @c/PATHS)
    (if (= (peek @c/PATHS) c/fail)
      (swap! c/PATHS pop)
      (do
        (swap! c/PATHS pop)
        (cut)))))


;; 「コインの入ってる箱は、１つの街に１箱だけ」という前提にもとづいている。

(u/=defn gen-sent-candy-log3 []
         (c/choose-bind c '(LA NY BOS) ; 街

                        ;; 都市ループの 内側（店・箱の選択の直前）に置く必要があります。
                        (mark)

                        (c/choose-bind s '(1 2) ; 店
                                       (c/choose-bind b '(1 2) ; 箱
                                                      (u/=values (list c s b))))))


(u/=defn find-boxes5 []
         (reset! c/PATHS [])
         (u/=bind [triple] (gen-sent-candy-log3)
                  (let [t triple]
                    (print t (if (coin? t)
                               (do
                                 ;; 当たりの箱を見つかった時点で cut を呼ぶ
                                 (cut)
                                 "C \n")
                               ""))
                    (c/fail))))


;; うまく動いた。

(comment

;;  onlisp.core=> (core/find-boxes5)
;;  (LA 1 1) (LA 1 2) C
;;  (NY 1 1) C
;;  (BOS 1 1) (BOS 1 2) (BOS 2 1) C
;;  [end]

  )


;; はたして、原著の cut が本物にちかいランダム性にも対応するのだろうか？

(u/=defn gen-sent-candy-log4 [cities store-num box-num]
         (let [stores (map inc (range store-num))
               boxes (map inc (range box-num))]
           (c/choose-bind c
                          cities ; 街
                          ;; mark は都市ループの 内側（店・箱の選択の直前）に置く。
                          ;; 共通部品に配置したものを呼び出す。
                          (c/mark)
                          (c/choose-bind s
                                         stores ; 店
                                         (c/choose-bind b
                                                        boxes ; 箱
                                                        (u/=values (list c s b)))))))


(u/=defn find-boxes6 [cities store-num box-num]
         (reset! c/PATHS [])
         ;; 「コインの入っている箱」を毎回ランダムに決めている
         (let [coins (rand-coins cities store-num box-num)
               hit (atom 0)
               cnt (atom 0)]

           (println "coins:" coins)
           (println "total:" (* (count cities) store-num box-num))

           (u/=bind [triple] (gen-sent-candy-log4 cities store-num box-num)
                    (let [t triple]
                      (swap! cnt inc)
                      (if (.contains coins t)
                        (do
                          ;; 共通部品に配置したものを呼び出す。
                          (c/cut)
                          (swap! hit inc)
                          (print t "C \n")
                          (when (>= @hit (count coins))
                            (println "actual:" @cnt)
                            (reset! c/PATHS [])))
                        (print t ""))
                      (c/fail)))))


;; coins に都市の重複があると、挙動がおかしくなった。
;; ４つの箱すべてを開けないのに処理を打ち切っているように見える。
;; また、actual: も出力されていない。

(comment

;;  onlisp.core=> (core/find-boxes6 '[LA NY BOS WA] 3 2)
;;  coins: [(BOS 2 1) (WA 2 1) (LA 1 2) (WA 1 2)]
;;  total: 24
;;  (LA 1 1) (LA 1 2) C
;;  (NY 1 1) (NY 1 2) (NY 2 1) (NY 2 2) (NY 3 1) (NY 3 2) (BOS 1 1) (BOS 1 2) (BOS 2 1) C
;;  (WA 1 1) (WA 1 2) C
;;  [end]
  )


;; まずは、都市ごとに、何個箱があるのかを取得するところから。
;; その都市の個数分見つかったら、処理の対象を移る。

(comment

;;  onlisp.core=> (core/rand-coins '[LA NY BOS WA] 3 2)
;;  [(NY 2 2) (LA 3 2) (LA 2 2) (NY 3 1)]

;;  onlisp.core=> (let [coins (core/rand-coins '[LA NY BOS WA] 3 2)]
;;                  (println "coins:" coins)
;;                  (frequencies (map first (core/rand-coins '[LA NY BOS WA] 3 2))))
;;  #_=>          #_=> coins: [(LA 1 1) (NY 1 2) (LA 3 1) (LA 1 2)]
;;  {LA 1, WA 2, BOS 1}

;;  onlisp.core=> (let [coins (core/rand-coins '[LA NY BOS WA] 3 2)]
;;                  (println "coins:" coins)
;;                  (frequencies (map first (core/rand-coins '[LA NY BOS WA] 3 2))))
;;  #_=>          #_=> coins: [(LA 1 1) (WA 3 2) (NY 1 2) (WA 2 1)]
;;  {NY 1, BOS 1, LA 1, WA 1}


;; 引数が nil なら 0 として inc を呼ぶ。

;;  onlisp.core=> (map (fnil inc 0) '(1 nil 2))
;;  (2 1 3)

  )


;; cut を呼ぶ条件を、
;; 「コインを見つけたとき」から「その都市のコインをすべて見つけたとき」に変える。



(u/=defn find-boxes7 [cities store-num box-num]
         (reset! c/PATHS [])
         (let [coins          (rand-coins cities store-num box-num)
               coins-per-city (frequencies (map first coins))  ; {LA 1, WA 2, ...}
               hit-per-city   (atom {})
               hit            (atom 0)
               cnt            (atom 0)]

           (println "coins:" coins)
           (println "total:" (* (count cities) store-num box-num))

           (u/=bind [triple] (gen-sent-candy-log4 cities store-num box-num)
                    (let [t    triple
                          city (first t)]
                      (swap! cnt inc)
                      (if-not (.contains coins t)
                        (print t "")

                        ;; コイン入りの箱が見つかった場合
                        (do
                          (swap! hit-per-city update city (fnil inc 0))
                          (swap! hit inc)
                          (print t "C \n")
                          ;; この都市のコインをすべて見つけたときだけ cut
                          (when (>= (get @hit-per-city city 0)
                                    (get coins-per-city city 0))
                            ;; 共通部品に配置したものを呼び出す。
                            (c/cut))
                          ;; 全コイン発見なら終了
                          (when (>= @hit (count coins))
                            (println "actual:" @cnt)
                            (reset! c/PATHS []))))
                      (c/fail)))))


(comment

;;  onlisp.core=> (core/find-boxes7 '[LA NY BOS WA] 3 2)
;;  coins: [(BOS 1 1) (LA 1 1) (NY 1 1) (LA 2 1)]
;;  total: 24
;;  (LA 1 1) C
;;  (LA 1 2) (LA 2 1) C
;;  (NY 1 1) C
;;  (BOS 1 1) C
;;  actual: 5
;;  [end]

;;  onlisp.core=> (core/find-boxes7 '[LA NY BOS WA] 3 2)
;;  coins: [(LA 3 2) (LA 2 1) (NY 2 2) (LA 3 1)]
;;  total: 24
;;  (LA 1 1) (LA 1 2) (LA 2 1) C
;;  (LA 2 2) (LA 3 1) C
;;  (LA 3 2) C
;;  (NY 1 1) (NY 1 2) (NY 2 1) (NY 2 2) C
;;  actual: 10
;;  [end]

  )

(ns onlisp.chap22.nondetermin2
  (:require
    [onlisp.chap22.common :as c]
    [onlisp.chap22.nondetermin :as n]
    [onlisp.common.util :as u]))


;; [ P305 chap22.4 ]

;; (defn kids
;;  [n]
;;  (case n
;;    a '(b c)
;;    b '(d e)
;;    c '(d f)
;;    f '(g)

;; ガードが必要
;; Execution error (IllegalArgumentException) at foo.core/kids (REPL:840).
;; No matching clause: z
;;    '()))


;; [ P308 chap22.6 ]


;; ループのある有向グラフ

(defn kids2
  [n]
  (case n
    a '(b d)
    b '(c)
    c '(a)
    d '(e)
    '()))


;; buggy
(defn bf-path_
  [dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]
      (if (= node dest)
        (rest (reverse path))
        (recur dest
               (concat (rest queue)
                       (map #(cons % path)
                            (n/kids node))))))))


;; buggy
(defn path_
  [node1 node2]
  (bf-path_ node2 (list (list node1))))


;; 決定的探索
;; 以下のように、幅優先探索であれば、循環経路を回避できる。

(defn bf-path
  [graph dest queue]
  (when (seq queue)
    (let [path (first queue)
          node (first path)]

      ;; (println path ":" node)

      (if (= node dest)
        ;; (rest (reverse path))
        (reverse path)
        (recur
          graph
          dest
          (concat (rest queue)
                  (map #(cons % path)
                       (graph node))))))))


;; loop/recur 版：

(defn bf-path2
  [graph dest queue]
  (loop [q queue]
    (when (seq q)
      (let [path (first q)
            node (first path)
            node-kids (graph node)]
        (if (= node dest)
          (reverse path)
          (recur
            (concat
              (rest q)
              (map #(cons % path) node-kids))))))))


(defn path
  [graph node1 node2]
  (reset! c/PATHS [])
  ;; (bf-path graph node2 (list (list node1)))
  (bf-path2 graph node2 (list (list node1))))


(comment

;;  onlisp.core=> (n2/path_ 'a 'g)
;;  (c f g)

;;  onlisp.core=> (n2/path_ 'a 'd)
;;  (b d)

;;  onlisp.core=> (n2/path_ 'a 'z)
;;  nil


;;  onlisp.core=> (n2/path n/kids 'a 'g)
;;  (a c f g)

;;  onlisp.core=> (n2/path n2/kids2 'a 'e)
;;  (a d e)

  )


;; こういう書き方もできるよ、というくらいの意味で、ベクタ版をば。

(defn bf-path3
  [graph dest queue]
  (loop [q queue]
    (when (seq q)
      (let [path (first q)
            node (peek path)        ; first → peek（末尾が現在ノード）
            node-kids (graph node)]
        (if (= node dest)
          path                      ; reverse 不要（変換が必要なら、呼び出し元でしてもらう）
          (recur
            (concat
              (rest q)
              (map #(conj path %)   ; cons % path → conj path %
                   node-kids))))))))


(defn path3
  [graph node1 node2]
  (reset! c/PATHS [])
  (bf-path3 graph node2 (list [node1])))  ; (list node1) → [node1]（ベクタ）



;; Clojure CPS: コード全体を CPS スタイルに変換する必要がある
(u/=bind [x] (c/choose-bind x '(2 3 4) (u/=values x))
         (u/=values (+ 1 x)))


(comment

;;  onlisp.core=> (u/=bind [x] (c/choose-bind x '(2 3 4) (u/=values x))
;;                         (u/=values (+ 1 x)))
;;  #_=> 3

;;  onlisp.core=> (c/fail)
;;  4

;;  onlisp.core=> (c/fail)
;;  5

;;  onlisp.core=> (c/fail)
;;  [end]

  )


;; 非決定的探索
;; 以下のように、深さ優先探索でも、一度通った経路かどうかの判断を用意しておけば、
;; 「真の choose」同様の挙動を実現できた。

;; 再帰実装
(u/=defn descent-impl2 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)   ; 循環検出 → バックトラック

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/choose-bind n (graph n1)
                            (u/=bind [p] (descent-impl2 graph n n2 visited#)
                                     (u/=values (cons n1 p)))))

           :else
           (c/fail)))


;; エントリポイント
(u/=defn descent2 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl2 graph n1 n2 #{}))


(comment


;;  onlisp.core=> (n2/descent2 n/kids 'a 'g)
;;  (a c f g)

;;  onlisp.core=> (c/fail)
;;  [end]

;; 循環経路に捕まっていない

;;  onlisp.core=> (n2/descent2 n2/kids2 'a 'e)
;;  (a d e)

;;  onlisp.core=> (c/fail)
;;  [end]

;;  onlisp.core=> (n2/descent2 n2/kids2 'a 'g)
;;  [end]

  )


;; ===================================================
;; 以下、c/true-choose の使用方法を模索する
;; ===================================================


;; c/choose と同じであることを既出のシンプルな関数で確認する

(defn do3
  [x]
  (do
    (reset! c/PATHS [])

    ;; マクロ版: 各フォームが自動的に thunk に包まれる
    ;; (c/true-choose2 (+ x 2) (* x 2) (Math/pow x 2))

    ;; 関数版: #() で明示的に thunk に包む必要がある
    (c/true-choose-simple (list #(+ x 2) #(* x 2) #(Math/pow x 2)))))


(let [x 2]
  (reset! c/PATHS [])

  ;; マクロ版: 各フォームが自動的に thunk に包まれる
  ;; (c/true-choose2 (+ x 1) (+ x 100))

  ;; 関数版: #() で明示的に thunk に包む必要がある
  (c/true-choose-simple (list #(+ x 1) #(+ x 100))))


;; ---------------------------------------------------


;; 再帰実装　その２（関数版 c/true-choose、c/true-choose-simple を使っている）
(u/=defn descent-impl3 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)   ; 循環検出 → バックトラック

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/true-choose
               ;; c/true-choose-simple
               (map (fn [n]
                      #(u/=bind [p] (descent-impl3 graph n n2 visited#)
                                (u/=values (cons n1 p))))
                    (graph n1))))

           :else
           (c/fail)))


;; エントリポイント　その２
(u/=defn descent3 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl3 graph n1 n2 #{}))


(comment

;;  onlisp.core=> (n2/descent3 n/kids 'a 'g)
;;  (a c f g)

;;  onlisp.core=> (c/fail)
;;  [end]

;;    onlisp.core=> (n2/descent3 n2/kids2 'a 'e)
;;  (a d e)

;;  onlisp.core=> (c/fail)
;;  [end]

  )


(comment

;;  ---
;;  なぜ c/true-choose_ を使えないか

;;  true-choose_ の設計:
;;  (c/true-choose_ form1 form2 form3)
;;  → (true-choose-impl #{} (list (fn [] form1) (fn [] form2) (fn [] form3)))
;;  ↑ コンパイル時に各 form を個別に thunk 化する

;;  descent-impl4 の呼び出し:
;;  (c/true-choose_ (map (fn [n] #(...)) (graph n1)))
;;  → (true-choose-impl #{} (list (fn [] (map (fn [n] #(...)) (graph n1)))))
;;  ↑ map 全体が1つの thunk になる → 呼ぶと lazy seq が返るだけ（CPS 継続なし）

  ;; true-choose_ マクロは静的な複数引数を想定しているため、
  ;; (map ...) のような実行時に決まる動的リストを渡すと
  ;; リスト全体が1つの thunk に包まれ CPS チェーンが途切れる。

  ;; true-choose_ が委譲している true-choose-impl を直接呼ぶことで正しく動く。
  )


;; 再帰実装　その３（c/true-choose-impl を直接呼ぶ）
(u/=defn descent-impl4 [graph n1 n2 visited]
         (cond
           (= n1 n2)
           (u/=values (list n2))

           (contains? visited n1)
           (c/fail)   ; 循環検出 → バックトラック

           (graph n1)
           (let [visited# (conj visited n1)]
             (c/true-choose-impl #{}
                                 (map (fn [n]
                                        #(u/=bind [p] (descent-impl4 graph n n2 visited#)
                                                  (u/=values (cons n1 p))))
                                      (graph n1))))

           :else
           (c/fail)))


;; エントリポイント　その３
(u/=defn descent4 [graph n1 n2]
         (reset! c/PATHS [])
         (descent-impl4 graph n1 n2 #{}))


;; =====================================================
;; Scheme 版 path の Clojure CPS 翻訳
;; =====================================================
;;
;; 原著 Scheme:
;;   (define (path node1 node2)
;;     (cond ((null? (neighbors node1)) (fail))
;;           ((memq node2 (neighbors node1)) (list node2))
;;           (else (let ((n (true-choose (neighbors node1))))
;;                   (cons n (path n node2))))))
;;
;; 翻訳上の差異:
;;   - Scheme true-choose は call/cc で値リストをそのまま受け取る
;;   - Clojure true-choose は thunk のリストを受け取る必要がある
;;   - 結果に node1 を含まない（Scheme 版の設計どおり）
;;   - visited を引数で引き回して循環グラフでのオーバーフローを防ぐ

(u/=defn path-impl [graph node1 node2 visited]
         (let [nbrs (graph node1)]
           (do
             (println "  path-impl:" node1 "→" nbrs)
             (cond
               (contains? visited node1)
               (do (println "    cycle at" node1 "→ fail") (c/fail))

               (empty? nbrs)
               (do (println "    empty → fail") (c/fail))

               (some #{node2} nbrs)
               (do (println "    found" node2 "directly")
                   (u/=values (list node2)))

               :else
               (let [visited# (conj visited node1)]
                 (;; c/true-choose
                  c/true-choose-simple

                  ;; thunk のリスト（値ではない）
                  (map (fn [n]
                         #(u/=bind [p] (path-impl graph n node2 visited#)
                                   (u/=values (cons n p))))
                       nbrs)))))))


(u/=defn path-scheme [graph node1 node2]
         (reset! c/PATHS [])
         (println "path-scheme:" node1 "->" node2)
         ;; 以下の呼び出し方では先頭のノードが含まれない
         ;; (path-impl graph node1 node2 #{})

         ;; なので、以下のようにひと工夫
         (u/=bind [p] (path-impl graph node1 node2 #{})
                  (u/=values (cons node1 p))))


(comment

;;  onlisp.core=> (n2/path-scheme n2/kids2 'a 'e)
;;  path-scheme: a -> e
;;  path-impl: a → (b d)
;;  path-impl: b → (c)
;;  path-impl: c → (a)
;;  path-impl: a → (b d)
;;  cycle at a → fail
;;  path-impl: d → (e)
;;  found e directly
;;  (a d e)

  )

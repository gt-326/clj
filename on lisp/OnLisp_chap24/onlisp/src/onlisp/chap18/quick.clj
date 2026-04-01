(ns onlisp.chap18.quick
  (:require
    [clojure.set]
    [onlisp.chap18.destructuring :as dest]
    [onlisp.common.util1 :as util1]
    [onlisp.common.util2 :as util2]))


(comment

  問題は CL の rest と Clojure の coll の構造の違いにあります。

  (defn length-test_
    [pat coll]
    (let [fin (first (first (rest (last coll))))]
      (if (or (coll? fin)
              (= (str fin) "clojure.core/nth"))
        `(= (count ~pat) ~(count coll))
        `(> (count ~pat) ~(- (count coll) 2)))))

  ;;====================================================
  ;;   CL の destruc が返すトップレベルのバインディング:
  ;;====================================================
  ;; [ パターン (a (b c) & d) のとき ]
  ((a (elt gseq 0))
   (nested-gseq (elt gseq 1))   ; ← ネスト分は別処理
   (d (subseq gseq 2)))
  ;; length rest = 3（トップレベルのみ）

  ;;====================================================
  ;; Clojure の destruc が返すフラットなバインディング:
  ;;====================================================
  ;; [ 同じパターンのとき ]
  ((a (nth gseq 0 nil))
   (G__111 (nth gseq 1 nil))     ; ← ネストの gensym もここに
   (d (drop 2 gseq))
   (b (nth G__111 0 nil))        ; ← ネストの展開もここに
   (c (drop 1 G__111)))
  ;; count coll = 5（ネスト展開分が混在）

  CL では (= (length gseq) 3) が生成されて正しい。
  Clojure では (= (count gseq) 5) が生成されてしまい、(1 (2 3) 4) (count=3)
  に対してチェック失敗。
  )


(defn length-test+
  [pat expr coll]
  ;; On Lisp の length-test を Clojure 向けに拡張したもの。
  ;; pat を参照するトップレベルの binding エントリ数（n）を数え、
  ;; expr の種類に応じて3種類のチェックコードを生成する：
  ;;   1. expr がシンボル（実行時変数）→ true           : チェックをスキップ（空リストも許容）
  ;;                                    (coll? ~pat) が nil を弾くため非空チェックは不要。
  ;;                                    if-match-quick と if-match-quick2 の挙動を統一する。
  ;;   2. n ≠ count(expr)              → true          : チェックをスキップ（lenient）
  ;;   3. n = count(expr)              → 厳密な count チェックコードを生成
  ;;      - nth パターンのとき  `(= (count ~expr) ~n)
  ;;      - drop パターンのとき `(> (count ~expr) ~(- n 2))
  (let [top-level (filter #(some #{pat} (second %)) coll)
        n         (count top-level)
        fin       (first (second (last top-level)))]
    (if (symbol? expr)
      true
      (if-not (= n (count expr))
        true
        (if (= (str fin) "clojure.core/nth")
          `(= (count ~expr) ~n)
          `(> (count ~expr) ~(- n 2)))))))


;; 入れ子にも対応しているので、「match1」にたいして「2」としている

(defn match2
  [refs then else]
  ;; dbind を使用していない
  (let [[[pat expr] & coll] refs]
    (cond
      ;; ===============================
      (util2/gensym? pat)
      (do
        ;; (println "-- gensym --")
        ;; 「if-match-quick」で gensym されたシンボルに、ここで実値を束縛する
        `(let [~pat '~expr]
           (if (and
                 ;; seq? で判定するとベクタ、セット、ハッシュマップを弾いてしまう
                 (coll? ~pat)
                 ~(length-test+ pat `'~expr coll))
             ~then
             ~else)))

      ;; ===============================
      (= pat '_)
      (do
        ;; (println "-- _ --")
        then)

      ;; ===============================
      (util1/varsym? pat)
      (do
        ;; (println "-- ?n --")

        `(let [ge# ~expr]
           (if (or (util2/gensym? ~pat) (= ~pat ge#))
             (let [~pat ge#] ~then)
             ~else)))

      ;; ===============================
      :else
      (do
        ;; (println "-- else --")

        `(if (= ~expr
                ;; ※シンボルの場合にはクォートを付けたくない
                (if (symbol? '~pat) ~pat '~pat))
           ~then

           (if (= ~expr
                  (if (util2/need-to-quote? '~pat) ~pat '~pat))
             ~then
             ~else)))

      ;; ===============================
      )))


;; 「dest/dbindを使用するため、ネストパターンをフラット化した実装」

;; match2 の let [[[pat expr] & coll] refs] を dest/dbind に置き換えたバージョン。
;; ネストパターン [[pat expr] & coll] を渡すと dbind 内部の destruc が末尾要素を
;; (drop ...) で生成してしまうため、シンプルな [first-pair & coll] で受け取り、
;; pat / expr は手動で取り出す。

(defn match3
  [refs then else]
  (dest/dbind
    [first-pair & coll]
    refs
    (let [pat  (first first-pair)
          expr (second first-pair)]
      (cond
        ;; ===============================
        (util2/gensym? pat)
        `(let [~pat '~expr]
           (if (and
                 (coll? ~pat)
                 ~(length-test+ pat `'~expr coll))
             ~then
             ~else))

        ;; ===============================
        (= pat '_)
        then

        ;; ===============================
        (util1/varsym? pat)
        `(let [ge# ~expr]
           (if (or (util2/gensym? ~pat) (= ~pat ge#))
             (let [~pat ge#] ~then)
             ~else))

        ;; ===============================
        :else
        `(if (= ~expr
                (if (symbol? '~pat) ~pat '~pat))
           ~then
           (if (= ~expr
                  (if (util2/need-to-quote? '~pat) ~pat '~pat))
             ~then
             ~else))

        ;; ===============================
        ))))


(defn gen-match
  [refs then else step]
  (do
    ;; (println step ":" refs)

    (if (empty? refs)
      then
      (let [then_next (gen-match (rest refs) then else
                                 ;; 処理ステップ
                                 "b")
            pat (first (first refs))]

        (if (util2/simple? pat)
          (do
            ;; 処理ステップ
            ;; (println "d" ":" refs)

            ;; 切替られる
            (match2 refs then_next else)
            ;; (match3 refs then_next else)
            )

          (gen-match (first refs) then_next else
                     ;; 処理ステップ
                     "c"))))))


;; seq なので、シンボル、アトムは前提として想定外。

(defn del-head-quote
  [seq]
  (if (= "quote" (str (first seq)))
    (first (rest seq))
    seq))


(defmacro pat-match
  [pat s then else]
  (if (symbol? pat)
    ;; symbol
    (do
      (match2 `((~pat
                 ;; クォートの有無を平準化　その１
                 ~(del-head-quote s)))
              then else))

    ;; seq
    (util2/with-gensyms (gseq gelse)
                        `(letfn [(~gelse [] ~else)]
                           ~(gen-match (cons
                                         (list
                                           gseq
                                           (or
                                             ;; 文字列を考慮して
                                             (seq (del-head-quote s))
                                             ;; onlisp.chap18.slow と足並みをそろえるため、
                                             ;; 空のリストを許容する。
                                             (del-head-quote s)))
                                         (dest/call_destruc2 pat gseq))
                                       then
                                       `(~gelse)
                                       "a")))))


(defmacro if-match-quick
  ([pat seq then]
   (list 'if-match-quick pat seq then nil))
  ([pat seq then else]
   `(let [~@(mapcat
              (fn [v] `(~(symbol v) (gensym)))
              ;; 元の「onlisp.chap18.show/if-match」では、
              ;; 引数 then の要素を gensym している。
              (util1/vars-in pat))]
      (pat-match ~pat ~seq ~then ~else))))


(comment

  foo.core> (onlisp.chap18.quick/if-match-quick (?x) (1) ?x "if-match-quick")
  1


  foo.core> (onlisp.chap18.quick/if-match-quick (?x ?y ?z) (hi ho) [?x ?y ?z] "if-match-quick")
  [hi ho nil]

  foo.core> (let [n 3]
              (onlisp.chap18.quick/if-match-quick (?x ?y) (1 2) (list ?x ?y n) "if-match-quick"))
  (1 2 3)

  foo.core> (let [n 3 m "m"]
              (onlisp.chap18.quick/if-match-quick (?x n 'n m '(a b)) (1 3 'n "m" '(a b)) (list ?x n m) "if-match-quick"))
  (1 3 "m")


  (let [n 3]
    (onlisp.chap18.quick/if-match-quick (?x 'n n '(a b)) '(1 n 3 (a b)) (list ?x n) "if-match-quick"))
  (1 3)

  (let [n 3]
    (onlisp.chap18.quick/if-match-quick (?x n 'n '(a b)) (1 3 'n '(a b)) (list ?x n) "if-match-quick"))
  (1 3)

  (let [n 3]
    (onlisp.chap18.quick/if-match-quick (?x n 'n '(a b)) '(1 3 'n '(a b)) (list ?x n) "if-match-quick"))
  (1 3)

  )


(defn abab4
  []
  (let [n 3
        m "m"]
    (if-match-quick
      (?x n 'n m '(a b))
      ;; 要素ごとに、個別にクォートするパターン
      (1 3 'n "m" '(a b))
      (list ?x n m)
      "abab4")))


(let [n 3
      m "m"]
  (defn abab5
    [s else]
    (if-match-quick
      (?x n 'n m '(a b) s)
      ;; 先頭にクォートを付与するパターン
      '(1 3 n "m" (a b) 100)
      (list ?x n m s)
      else)))


;; then / else を関数引数として渡す例。
;; seq とパターンはコンパイル時に固定されており、常にマッチする。
;; マッチ時は then に渡した任意の値がそのまま返る。
;; ただし then にパターン変数（?x 等）を含めることはできない
;; （gensym の有効スコープはマクロ展開時に限られるため）。

(let [n 3
      m "m"]
  (defn abab6
    [then else]
    (if-match-quick
      (?x n 'n m '(a b))
      '(1 3 n "m" (a b))
      then
      else)))


(comment

  onlisp.core=> (onlisp.chap18.quick/abab5 100 "abab5")
  (1 3 "m" 100)

  onlisp.core=> (onlisp.chap18.quick/abab5 101 "abab5")
  "abab5"

  ;; seq とパターンが固定のため常にマッチ。then に渡した値がそのまま返る。
  onlisp.core=> (onlisp.chap18.quick/abab6 "matched" "else")
  "matched"

  onlisp.core=> (onlisp.chap18.quick/abab6 42 "else")
  42

  )


;; =====================================================
;; 以下は、関数の引数をどれだけマクロ側にわたせるのか、という試み
;; =====================================================

;; match2 の gensym 分岐のみ変更: '~expr → ~expr（実行時式をそのまま評価）

(defn match2-rt
  [refs then else]
  (let [[[pat expr] & coll] refs]
    (cond
      ;; ===============================
      (util2/gensym? pat)
      ;; seq を実行時に評価するため、'~expr（コンパイル時クォート）ではなく ~expr を使用
      `(let [~pat ~expr]
         (if (and
               (coll? ~pat)
               ~(length-test+ pat expr coll))
           ~then
           ~else))

      ;; ===============================
      (= pat '_)
      then

      ;; ===============================
      (util1/varsym? pat)
      `(let [ge# ~expr]
         (if (or (util2/gensym? ~pat) (= ~pat ge#))
           (let [~pat ge#] ~then)
           ~else))

      ;; ===============================
      :else
      `(if (= ~expr
              (if (symbol? '~pat) ~pat '~pat))
         ~then
         (if (= ~expr
                (if (util2/need-to-quote? '~pat) ~pat '~pat))
           ~then
           ~else))

      ;; ===============================
      )))


(defn gen-match-rt
  [refs then else step]
  (if (empty? refs)
    then
    (let [then_next (gen-match-rt (rest refs) then else "b")
          pat (first (first refs))]
      (if (util2/simple? pat)
        (match2-rt refs then_next else)
        (gen-match-rt (first refs) then_next else "c")))))


;; seq を実行時引数として渡せる pat-match
;; del-head-quote + seq のコンパイル時評価を廃止し、s をそのまま渡す

(defmacro pat-match2
  [pat s then else]
  (if (symbol? pat)
    ;; symbol
    (match2-rt `((~pat ~s))
               then else)

    ;; seq
    (util2/with-gensyms (gseq gelse)
                        `(letfn [(~gelse [] ~else)]
                           ~(gen-match-rt (cons
                                            (list gseq s) ; s 直接（コンパイル時処理なし）
                                            (dest/call_destruc2 pat gseq))
                                          then
                                          `(~gelse)
                                          "a")))))


;; seq を実行時引数として渡せる if-match-quick
;; pat-match2 を使用。seq に変数・式を渡せる。
;; ただし then にパターン変数（?x 等）を含む場合は関数引数にできない
;; （gensym は展開スコープ内でのみ有効なため）。

(defmacro if-match-quick2
  ([pat seq then]
   (list 'if-match-quick2 pat seq then nil))
  ([pat seq then else]
   `(let [~@(mapcat
              (fn [v] `(~(symbol v) (gensym)))
              (util1/vars-in pat))]
      (pat-match2 ~pat ~seq ~then ~else))))


;; if-match-quick2 の使用例
;; seq を実行時引数（関数引数）として渡す


(defn abab7
  [s else]
  (if-match-quick2
    (?x ?y ?z)
    s           ; ← 実行時の seq を受け取る
    [?x ?y ?z]
    else))


(comment

  onlisp.core=> (onlisp.chap18.quick/abab7 '(1 2 3) "abab7")
  [1 2 3]

  ;; lenient: 要素数がパターンより少ない → nil 補完
  onlisp.core=> (onlisp.chap18.quick/abab7 '(hi ho) "abab7")
  [hi ho nil]

  ;; 空リスト: (coll? s) = true により then に入り nil 補完
  ;; if-match-quick（compile-time）と統一された挙動。
  onlisp.core=> (onlisp.chap18.quick/abab7 '() "abab7")
  [nil nil nil]

  )

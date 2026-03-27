(ns onlisp.chap18.slow
  (:require
    [onlisp.common.util1 :as util1]))


(defmacro if-match
  [pat seq then & else]
  (let [;; then がリストのとき `(list ~@then) に変換する。
        ;; そのままでは (?x ?y ?z) が関数呼び出しとして評価されてしまうため。
        t (if (list? then) `(list ~@then) then)]
    `(util1/aif2
       ;; 明示的に list でくるむ必要がある
       (list (util1/match '~pat '~seq))
       (let [~@(mapcat
                 (fn [v]
                   ;; キーワードではなく、シンボルをキーにした
                   ;;  `(~(symbol v) (my-binding ~(keyword v) ~'it)))
                   `(~v (util1/my-binding '~v ~'it)))
                 (util1/vars-in then))]
         ~t)
       (do ~@else))))


(comment

  foo.core> (onlisp.chap18.slow/if-match (?x ?y) (hi ho) ?x)
  hi

  foo.core> (onlisp.chap18.slow/if-match (?x ?y) (hi ho) [?x ?y])
  [hi ho]

  foo.core> (onlisp.chap18.slow/if-match (?x ?y) (hi ho) (?x ?y ?z))
  (hi ho nil)

  )


(defn abab
  [s]
  (if-match (?x ?y ?z) s [?x ?y ?z] "abab"))


(comment

  ;; if-match はコンパイル時に seq を評価するため、
  ;; 関数引数 s はシンボル s 自体としてマッチされる。
  ;; 実行時に渡す値に関わらず、常に else が返される。

  onlisp.core=> (onlisp.chap18.slow/abab '(1 2 3))
  "abab"

  onlisp.core=> (onlisp.chap18.slow/abab '(hi ho))
  "abab"

  )


;; if-match2 の then 部分の展開コードを生成するヘルパー。
;; then に含まれるパターン変数を `(v (it 'v))` の形で let に束縛する。
;; `(it 'v)` は実行時に aif2 が束縛する it（バインディングマップ）から
;; シンボル v の値を取り出す呼び出し。
;; then がリストの場合は先頭に list を付けて関数呼び出しとして評価されるのを防ぐ。

(defn gen-then-part
  [then]
  (let [t (if (list? then) (cons 'list then) then)]
    `(let [~@(mapcat
               ;; キーワードではなく、シンボルをキーにした
               ;; (fn [v] `(~(symbol v) (~(keyword v) ~'it)))
               (fn [v] `(~v (~'it '~v)))
               (util1/vars-in then))]
       ~t)))


;; if-match の改良版。match の実行（実行時）とバインディング展開（コンパイル時）を分離する。
;; test に実行時の match 結果（バインディングマップ）を受け取り、
;; then のパターン変数を :let で束縛して評価する。
;; パターン・seq を実行時に渡せる一方、then はコンパイル時に展開されるため
;; then にパターン変数を含めることができる。

(defmacro if-match2
  [test then & else]
  (list
    'onlisp.common.util1/aif2
    `(list ~test)
    (gen-then-part then)
    (cons 'do else)))


(defn abab2
  [s]
  ;; [ run time ] zone
  (let [test (util1/match '(?x ?y) s)]

    ;; [ read / compile time ] zone
    (if-match2 test [?x ?y ?z] "abab2")))


(defn abab3
  [p s]
  ;; [ run time ] zone
  (let [test (util1/match p s)]

    ;; [ read / compile time ] zone
    (if-match2 test [?x ?y ?z] "abab3")))


(comment

  ;; abab2: パターンはコンパイル時固定、seq を実行時に渡せる
  onlisp.core=> (onlisp.chap18.slow/abab2 '(1 2))
  [1 2 nil]

  onlisp.core=> (onlisp.chap18.slow/abab2 '(hi ho))
  [hi ho nil]

  onlisp.core=> (onlisp.chap18.slow/abab2 '(hi ho hum))
  "abab2"

  ;; abab3: パターン・seq の両方を実行時に渡せる
  onlisp.core=> (onlisp.chap18.slow/abab3 '(?x ?y) '(hi ho))
  [hi ho nil]

  onlisp.core=> (onlisp.chap18.slow/abab3 '(?x ?y) '(hi))
  [hi nil nil]

  )

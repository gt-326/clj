(ns onlisp.common.util1
  (:require
    [clojure.set]))


;; CL の multiple-value-bind を Clojure に移植したもの。
;; 2段階で let を生成する:
;;   1. gensym 部分: binds の各変数名を gensym に束縛（シンボル衝突を防ぐ）
;;   2. bind 部分: seq を分配束縛して各変数に値を割り当てる
;; seq はコンパイル時に sequential? を評価し、
;; シーケンスでなければ (list seq) でくるんで分配束縛に対応させる。

(defmacro multiple-value-bind
  [binds seq & body]
  `(let [;; gensym 部分
         ~@(mapcat #(list (symbol %) '(gensym)) binds)
         ;; bind 部分
         [~@(map #(symbol %) binds)]
         (if ~(sequential? seq) ~seq (list ~seq))]
     ~@body))


;; [ P195 chap14 ]

(defmacro aif
  [test-form then-form & else-form]
  `(let [~'it ~test-form]
     (if ~'it
       ~then-form
       (do ~@else-form))))


(comment

  ;; test が truthy → it に束縛して then を評価
  (aif (+ 1 2) (* it 10))
  ;=> 30

  ;; test が nil → else を評価
  (aif nil (* it 10) "else")
  ;=> "else"

  )


;; [ P201 chap14 ]

(defmacro aif2
  [test & then-else]
  (let [[then else] then-else]
    `(multiple-value-bind
       (~'it ~'win)
       ~test
       (if (or ~'it ~'win) ~then ~else))))


(comment

  ;; test が (list value) → it = value、win = true → then を評価
  (aif2 (list {:a 1}) it "none")
  ;=> {:a 1}

  ;; test が (list nil) → it = nil、win = nil → else を評価
  (aif2 (list nil) it "none")
  ;=> "none"

  ;; test が (list false) → it = false、win = false → else を評価
  (aif2 (list false) it "none")
  ;=> "none"

  ;; test が空リスト → win = nil → else を評価
  (aif2 '() it "none")
  ;=> "none"

  )


(defmacro acond2
  [variables & clauses]
  (when (seq clauses)
    (let [[cl1 cl2] (take 2 clauses)]
      (if (seq variables)
        `(multiple-value-bind
           ~variables
           ~cl1
           (if (or ~@variables)
             (let [~'it ~(first variables)] ~cl2)
             (acond2 ~variables ~@(drop 2 clauses))))

        `(if ~cl1
           (let [~'it ~cl1] ~cl2)
           (acond2 ~variables ~@(drop 2 clauses)))))))


(comment

  ;; 最初の節が (nil false) → 失敗、次の節 (42 true) → 成功、it = 42
  (acond2 (it win)
    (list nil false) "no"
    (list 42 true)  (str "found: " it))
  ;=> "found: 42"

  ;; 全節が失敗 → nil
  (acond2 (it win)
    (list nil false) "no"
    (list nil false) "still no")
  ;=> nil

  )


;; [ P244 - 245 chap18 ]


(defn varsym?
  [x]
  (and (symbol? x)
       (= \?
          (first (name x)))))


(defn my-binding
  [x binds]
  (letfn [(rec-bind
            [x binds]
            (aif
              (get binds x)
              ;; binds から x を直接引いた値（it）が truthy なら即返す。
              ;; falsy の場合は (rest it) を再帰的に探索するフォールバック。
              (or it
                  (rec-bind (rest it) binds))))]
    (rec-bind x binds)))


(defn match
  ([x y] (match x y {}))
  ([x y binds]

   ;; よくよく見てみると、「acond」でこと足りるはずなんだけど、
   ;; なんでだか本のコードは「acond2」を使っている。
   (acond2
     ;; [args: variables]
     nil

     ;; [args: clauses]
     (or (= x y) (= x '_) (= y '_)) binds

     (my-binding x binds) (match it y binds)
     (my-binding y binds) (match x it binds)

     (varsym? x)
     ;; キーワードではなく、シンボルをキーにした
     ;; (assoc binds (keyword (apply str (name x)))
     (assoc binds (symbol (apply str (name x)))
            (if (varsym? y)
              (symbol (apply str (name y)))
              y))

     (varsym? y)
     ;; キーワードではなく、シンボルをキーにした
     ;; (assoc binds (keyword (apply str (name y)))
     (assoc binds (symbol (apply str (name y)))
            (if (varsym? x)
              (symbol (apply str (name x)))
              x))

     (and (seqable? x)
          (seqable? y)
          (match (first x) (first y) binds))
     (match (next x) (next y) it)

     :else nil)))


(comment

  ;; パターン変数へのマッチ → バインディングマップを返す
  (match '(?x ?y) '(hi ho))
  ;=> {?x hi, ?y ho}

  ;; 既存の binds を引き継ぐ
  (match '(?x ?y) '(hi ho) '{?x hi})
  ;=> {?x hi, ?y ho}

  ;; ワイルドカード _ は何にでもマッチし、束縛しない
  (match '(?x _ ?z) '(1 2 3))
  ;=> {?x 1, ?z 3}

  ;; リテラルを含むパターン
  (match '(a ?x b) '(a 1 b))
  ;=> {?x 1}

  ;; 既存の binds と矛盾する場合 → nil（マッチ失敗）
  (match '(?x ?y) '(hi ho) '{?x bye})
  ;=> nil

  ;; リテラルが一致しない場合 → nil
  (match '(?x a) '(1 b))
  ;=> nil

  )


;; [ P248 - 247 chap18 ]

;; CL の consp に相当。空でないコレクションを cons セルの連鎖とみなす。
(defn cons?
  [x]
  (and (coll? x) (seqable? x) (seq x)))


;; CL の atom に相当。cons? でないもの（nil、スカラー、空コレクション）を atom とみなす。
(defn cl-atom?
  [x]
  (not (cons? x)))


(defn vars-in
  ([expr]
   (vars-in expr cl-atom?))
  ([expr chk-fnc]
   (if (chk-fnc expr)
     (when (varsym? expr)
       #{expr})
     (clojure.set/union
       (vars-in (first expr) chk-fnc)
       (vars-in (next expr) chk-fnc)))))


(comment

  ;; フラットなパターン → パターン変数のセットを返す
  (vars-in '(?x ?y ?z))
  ;=> #{?x ?y ?z}

  ;; リテラルを含む場合 → パターン変数のみ抽出
  (vars-in '(?x a ?y))
  ;=> #{?x ?y}

  ;; ネストしたパターン → 再帰的に収集
  (vars-in '(?x (?y ?z)))
  ;=> #{?x ?y ?z}

  ;; パターン変数なし → nil
  (vars-in '(a b c))
  ;=> nil

  ;; 単一のパターン変数
  (vars-in '?x)
  ;=> #{?x}

  )

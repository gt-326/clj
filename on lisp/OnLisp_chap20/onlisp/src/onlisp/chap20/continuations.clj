(ns onlisp.chap20.continuations)


;; [ P274 chap20.2 ]

;; なぜ原著が *cont* の初期値として #'values を設定しているのか、不明。

;; (defn my-values [& args]
;;   (if (seq (rest args))
;;     args
;;     (first args)))

;; (def ^:dynamic *cont* my-values)
(def ^:dynamic *cont* identity)


;; （展開順のバグ: 関数部分 → マクロ部分 の順のため、再帰関数でコンパイルエラーになる）

;; buggy
(defmacro =defn_
  [symbol-name params & body]
  (let [f (symbol (str "=" (name symbol-name)))]
    `(do
       ;; 関数部分
       (defn ~f [~'*cont* ~@params] ~@body)
       ;; マクロ部分
       (defmacro ~symbol-name ~params
         `(~'~f *cont* ~~@params)))))


(defmacro =defn
  [symbol-name params & body]
  (let [f (symbol (str "=" (name symbol-name)))]
    ;; ※マクロにより呼び出される関数:f が、
    ;; 「自分自身（:f）を呼び出す」タイプの関数である場合を考慮すると、
    ;; こういう書き方をする必要がある。
    (declare f)

    `(do
       ;; マクロ部分
       (defmacro ~symbol-name ~params
         `(~'~f *cont* ~~@params))

       ;; 関数部分
       (defn ~f [~'*cont* ~@params] ~@body))))


;; *cont* は ^:dynamic Var + binding で管理している。
;; =bind のスコープを抜けると *cont* は自動的に元の値に戻る、という性質を活用するべく。

;; buggy
(defmacro =bind_
  [params expr & body]
  (do
    `(binding
       [*cont* (fn ~params ~@body)]
       ~expr)))


(defmacro =bind
  [params expr & body]
  `(let [outer# *cont*]
     (binding [*cont* (fn ~params
                        (binding [*cont* outer#]
                          ~@body))]
       ~expr)))


(defmacro =values
  [& retvals]
  `(*cont* ~@retvals))


(defmacro =fn
  [params & body]
  `(fn [~'*cont* ~@params]
     ~@body))


(defmacro =fncall
  [fnc & params]
  `(~fnc *cont* ~@params))


(defmacro =apply
  [fnc & args]
  `(apply ~fnc *cont* ~@args))

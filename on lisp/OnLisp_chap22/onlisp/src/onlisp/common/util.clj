(ns onlisp.common.util)


;; [ P274 chap20.2 ]

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
  (let [f-str  (str "=" (name symbol-name))
        f      (symbol f-str)

        ;; マクロ展開時の現在ネームスペース（=defn を呼び出しているファイルの ns）で
        ;; 完全修飾シンボルを作成する。
        ;; ネストしたバックティック内の非修飾シンボルは呼び出し元ネームスペースで
        ;; 解決されるため、別ネームスペースからの呼び出しで
        ;; "Unable to resolve symbol" エラーが発生する。
        ;; list 形式で完全修飾シンボルを埋め込むことで、どこから呼ばれても解決できる。
        ns     (name (ns-name *ns*))
        qf     (symbol ns f-str)

        ;; *cont* は常に onlisp.common.util で定義される dynamic Var。
        ;; 元の実装ではネストしたバックティックを util.clj のリーダーが処理する際に
        ;; onlisp.common.util/*cont* へ自動修飾されていた。list 形式では明示的に指定する。
        qcont  (symbol "onlisp.common.util" "*cont*")]

    `(do
       ;; =foo を前方宣言（マクロより先に参照可能にする）
       (declare ~f)

       ;; マクロ部分: 完全修飾シンボルで呼び出すことで
       ;; 別ネームスペースからでも解決できる
       (defmacro ~symbol-name [~@params]
         (list '~qf '~qcont ~@params))

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

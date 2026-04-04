(ns onlisp.common.util3)


;; [ P174 chap12 ]

;; 「リスト末尾に破壊的な操作を行う」

(defmacro conc1f
  [obj]
  (let [place (gensym)]
    `(fn [~place]
       (reset!
         ~place
         (reverse (conj (reverse (deref ~place)) ~obj))))))


;; [ P274 chap20 ]

(def ^:dynamic *cont* identity)


;; =bind だけが上記 *cont* を上書きする

(defmacro =bind
  [params expr & body]
  `(binding
     [*cont* (fn ~params ~@body)]
     ~expr))


(defmacro =fn
  [params & body]
  `(fn [~'cont_ ~@params]
     ~@body))


;; 引数の先頭（ローカルスコープ）：cont_
;; ((fn [cont_ a b]
;;    (list (* cont_ 10) a b)) 1 2 3)

;; onlisp.core=> (let [cont_ "aiueo"]
;;                 ((onlisp.common.util3/=fn (a b) (list (* cont_ 10) a b)) 1 2 3))
;; (10 2 3)



;; ===================================================================

;; 「引数 params の先頭に継続がくる」という前提を共有してもらうようにする。

(defmacro =defn
  [name params & body]
  (let [n (symbol (str "=" name))]
    `(do
       ;; 関数部分
       ;; (defn ~n [*cont* ~@params] ~@body)
       (defn ~n [~@params] ~@body)

       ;; マクロ部分
       ;; (defmacro ~name ~params `(~~n *cont* ~~@params))
       (defmacro ~name ~params `(~~n ~~@params)))))


(defmacro =fncall
  [fnc & params]
  ;; `(~fnc *cont* ~@params)
  `(~fnc ~@params))


(defmacro =values
  [& retvals]
  ;; `(*cont* ~@retvals)
  `(~(first retvals) ~@(rest retvals)))


;; ===================================================================



;; [ P301 chap22 ]

(def ^:dynamic *paths* (atom []))


(def failsym '[end])


(defn fail
  []
  (if (empty? (deref *paths*))
    failsym
    (let [fnc (peek (deref *paths*))]
      ;; POP
      (reset! *paths* (pop (deref *paths*)))
      (fnc))))


(defn cb
  [fnc choices]
  (if (seq choices)
    (do
      (when (rest choices)
        (reset!
          *paths*
          ;; PUSH
          (conj
            (deref *paths*)
            (fn [] (cb fnc (rest choices))))))

      (fnc (first choices)))
    (fail)))


(defmacro choose-bind
  [param choices & body]
  `(cb (fn [~param] ~@body) ~choices))


(defmacro choose
  [& choices]
  (if (seq choices)
    `(do
       ~@(map
           (fn [c]
             `(reset!
                *paths*
                (conj
                  (deref *paths*)
                  #(~@c))))
           (reverse (rest choices)))

       ~(first choices))
    `(fail)))

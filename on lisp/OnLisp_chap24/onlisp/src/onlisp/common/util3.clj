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


;; ここで *cont* を直接渡そうとすると、その書き換えがうまくいかない。
;; 「引数 params の先頭に *cont* がくる」という前提を共有してもらうようにする。

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


(defmacro =bind
  [params expr & body]
  `(binding [*cont* (fn ~params ~@body)]
     ~expr))


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

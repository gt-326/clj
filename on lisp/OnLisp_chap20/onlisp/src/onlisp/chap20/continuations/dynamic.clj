(ns onlisp.chap20.continuations.dynamic)


;; [ P274 chap20 ]

(def ^:dynamic *cont* identity)


;; OK
(defmacro =defn
  [sname params & body]
  (let [f (symbol (str "=" (name sname)))]
    (declare f)
    `(do
       ;; マクロ部分
       (defmacro ~sname ~params
         ;; `(~'~f ~*cont* ~~@params)
         `(~'~f *cont* ~~@params))

       ;; 関数部分
       (defn ~f [~'*cont* ~@params] ~@body))))


(defmacro =bind
  [params expr & body]
  `(binding
     [*cont* (fn ~params ~@body)]
     ~expr))


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


(comment


  )

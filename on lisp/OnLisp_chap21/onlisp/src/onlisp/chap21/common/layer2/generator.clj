(ns onlisp.chap21.common.layer2.generator
  (:require
    [onlisp.chap21.common.layer1.stat :as s]
    [onlisp.common.util :as u]))


;; P283

;; =defn をもちいていないバージョン

(defmacro program
  [name args & body]
  `(defn ~name [~@args]
     (try
       ;; 初期化（中断したプロセスを消去する）
       (reset! s/PROCS nil)
       ;; 「fork によりプロセスを PROCS へ登録する」
       ~@body
       ;; pick-process は halt 例外が出るまで内部で相互再帰する。
       (s/pick-process)

       (catch Exception ~'ex
         (if (= (str s/HALT) (.getMessage ~'ex))
           (str "[On Lisp] [chap.21 multi process] system halt: " (.getData ~'ex))
           (str "normal exception: " (.getMessage ~'ex)))))))


(defmacro program-cps
  [name args & body]
  `(u/=defn ~name [~@args]
            (try
              (reset! s/PROCS nil)
              ~@body
              (s/pick-process)

              (catch Exception ~'ex
                (if (= (str s/HALT) (.getMessage ~'ex))
                  (str "[On Lisp] [chap.21 multi process] system halt: " (.getData ~'ex))
                  (str "normal exception: " (.getMessage ~'ex)))))))

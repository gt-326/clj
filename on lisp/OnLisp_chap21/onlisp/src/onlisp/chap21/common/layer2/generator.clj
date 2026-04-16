(ns onlisp.chap21.common.layer3
  (:require
    [onlisp.chap21.common.layer1 :as l1]
    [onlisp.common.util :as u]))


;; P283

;; =defn をもちいていないバージョン

(defmacro program
  [name args & body]
  `(defn ~name [~@args]
     (try
       ;; 初期化（中断したプロセスを消去する）
       (reset! l1/PROCS nil)
       ;; ここでプロセスを追加している想定？
       ~@body
       ;; 優先度に応じて登録されたプロセスを実行する？
       (loop [] (l1/pick-process))

       (catch Exception ~'ex
         (if (= (str l1/HALT) (.getMessage ~'ex))
           (str "[On Lisp] [chap.21 multi process] system halt: " (.getData ~'ex))
           (str "normal exeption: " (.getMessage ~'ex)))))))


(defmacro program-cps
  [name args & body]
  `(u/=defn ~name [~@args]
            (try
              ;; 初期化（中断したプロセスを消去する）
              (reset! l1/PROCS nil)
              ;; ここでプロセスを追加している想定？
              ~@body
              ;; 優先度に応じて登録されたプロセスを実行する？
              (loop [] (l1/pick-process))

              (catch Exception ~'ex
                (if (= (str l1/HALT) (.getMessage ~'ex))
                  (str "[On Lisp] [chap.21 multi process] system halt: " (.getData ~'ex))
                  (str "normal exeption: " (.getMessage ~'ex)))))))

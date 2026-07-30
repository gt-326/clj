(ns paip.common.utils
  (:require
    [clojure.set :refer [union difference]]))


;; [ 4.10 ]

(def dbg-ids (atom #{}))

(def line-no (atom 0))


(defn debug
  [& ids]
  (let [set_ids (set ids)]
    (reset! dbg-ids (union @dbg-ids set_ids))))


(defn undebug
  [& ids]
  (let [set_ids (set ids)]
    (reset! dbg-ids
            (if (seq set_ids)
              (difference @dbg-ids set_ids)
              #{}))))


(defn dbg
  [id format-str & args]
  (when (contains? @dbg-ids id)
    (swap! line-no inc)
    ;; 出力
    (println
      (str
        (if (= @line-no 1) "\n\n[ debug ]\n" "")
        (format "%3d" @line-no) " : " (apply format format-str args)))))


(defmacro dbg-indent
  [cnt id format-str & args]
  `(dbg ~id (apply str
                   (concat
                     (repeat ~cnt " ")
                     (list ~format-str))) ~@args))


;; [ 4.11.1 ]

(defn cons?
  [x]
  (and (coll? x) (seqable? x) (seq x)))


(defn cl-atom?
  [x]
  (not (cons? x)))

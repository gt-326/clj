(ns onlisp.chap23.common.layer1.core
  (:require
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]
    [onlisp.common.util3 :as u3]))


;; [ P318 chap23.4 ]

(defmacro defnode
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs]
            (u2/choose ~@arcs)))


;; 非決定的選択オペレータ u2/true-choose を使っている


(defmacro defnode-slow
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs]
            (if (u2/node-visited? ['~name ~'pos])
              (u2/fail)
              (do
                (u2/mark-visited! ['~name ~'pos])
                ;; 関数版エントリーポイント
                (u2/true-choose
                  (list ~@(map (fn [arc] `(fn [] ~arc)) arcs)))

                ;; マクロ版エントリーポイント
                ;; (u2/true-choose_ ~@arcs)
                ))))


;; 非決定的選択オペレータ u2/true-choose-simple を使っている


;; atom 版

(defmacro defnode-slow2
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs ~'arg_visited]
            (if (contains? @~'arg_visited ['~name ~'pos])
              (u2/fail)
              (do
                (swap! ~'arg_visited conj ['~name ~'pos])
                (u2/true-choose-simple
                  (list ~@(map (fn [arc] `(fn [] ~arc)) arcs)))))))


;; set 版

(defmacro defnode-slow3
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs ~'arg_visited]
            (if (contains? ~'arg_visited ['~name ~'pos])
              (u2/fail)
              (let [~'arg_visited (conj ~'arg_visited ['~name ~'pos])]
                (u2/true-choose-simple
                  (list ~@(map (fn [arc] `(fn [] ~arc)) arcs)))))))


;; o/SENT 不要版

(defmacro defnode-slow4
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs ~'arg_visited ~'arg_sent]
            (if (contains? ~'arg_visited ['~name ~'pos])
              (u2/fail)
              (let [~'arg_visited (conj ~'arg_visited ['~name ~'pos])]
                (u2/true-choose-simple
                  (list ~@(map (fn [arc] `(fn [] ~arc)) arcs)))))))


;; u2/PATHS 不要版

(defmacro defnode-slow5
  [name & arcs]
  `(u/=defn ~name [~'pos ~'arg_regs ~'arg_visited ~'arg_sent]
            (if (contains? ~'arg_visited ['~name ~'pos])
              (u3/fail)
              (let [~'arg_visited (conj ~'arg_visited ['~name ~'pos])]
                (u3/true-choose-simple
                  (list ~@(map (fn [arc] `(fn [] ~arc)) arcs)))))))


(defmacro set-register
  [k v regs]
  `(cons
     (cons
       (cons ~k ~v)
       (first ~regs))
     (rest ~regs)))


;; =============================

;; ^:dynamic を付与する必要はなかった

;; (def REGS (atom ()))


(defn compile-cmds
  [cmds]
  (if (seq cmds)
    `(~@(first cmds) ~(compile-cmds (rest cmds)))

    ;; １．このシンボルを defnode 内部の引数名と一致させる必要がある
    ;; ２．p/with-parses のなかで with-gensyms しているシンボル名と一致させる必要がある
    'arg_regs))


(comment

;;  onlisp.core=> (c/compile-cmds '((r/setr a b) (r/setr c d)))
;;  (r/setr a b (r/setr c d arg_regs))

;;  onlisp.core=> (c/compile-cmds '((r/setr a b) (do (princ "ek!")) (r/setr c d)))
;;  (r/setr a b (do (princ "ek!") (r/setr c d arg_regs)))

  )


;; =============================

;; atn3〜 の用例を実行する際に必要になるノードの「ネットワーク」を作成するために必要

(defmacro defnode-declare
  [symbol-name params]
  (let [f-str (str "=" (name symbol-name))
        f     (symbol f-str)
        ns    (name (ns-name *ns*))
        qf    (symbol ns f-str)
        qcont (symbol "onlisp.common.util" "*cont*")]
    `(do
       (declare ~f)
       (defmacro ~symbol-name [~@params]
         (list '~qf '~qcont ~@params)))))

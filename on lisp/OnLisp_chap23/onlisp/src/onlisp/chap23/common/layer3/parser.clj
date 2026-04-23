(ns onlisp.chap23.common.layer3.parser
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]
    [onlisp.common.util3 :as u3]))


;; [ P322 chap23.4 ]

(defmacro with-gensyms
  [syms & body]
  `(let [~@(apply
             concat
             (map (fn [s] `(~s (gensym))) syms))]
     ~@body))


(defmacro with-parses
  [node sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(do
       (reset! o/SENT ~sent)
       (reset! u2/PATHS [])

       (u/=bind [~'parse ~arg_pos ~arg_regs] (~node 0 '())
                (do
                  (when (= ~arg_pos (count @o/SENT))
                    ~@body)
                  (u2/fail))))))


(defmacro with-parses-slow
  [node sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(do
       (reset! o/SENT ~sent)
       (reset! u2/PATHS [])
       (reset! u2/VISITED #{})

       (u/=bind [~'parse ~arg_pos ~arg_regs] (~node 0 '())
                (do
                  (when (= ~arg_pos (count @o/SENT))
                    ~@body)
                  (u2/fail))))))


;; u/=bind の挙動を振り返り

;;  ((fn [parse pos regs]
;;     (do
;;       (when (= ~pos (count @o/SENT))
;;         ~@body)
;;       (u2/fail)))
;;   (~node 0 '()))


;; arg_regs: atom 版

(defmacro with-parses-slow2
  [node-start sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(do
       (reset! o/SENT ~sent)
       (reset! u2/PATHS [])

       (u/=bind [~'parse ~arg_pos ~arg_regs]
                (~node-start 0 '()
                             ;; c/defnode-slow2 にて =defn で生成する関数の、
                             ;; 引数 arg_visited の初期値として設定する
                             (atom #{}))
                (do
                  (when (= ~arg_pos (count @o/SENT))
                    ~@body)
                  (u2/fail))))))


;; arg_regs: set 版

(defmacro with-parses-slow3
  [node-start sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(do
       (reset! o/SENT ~sent)
       (reset! u2/PATHS [])

       (u/=bind [~'parse ~arg_pos ~arg_regs]
                (~node-start 0 '()
                             ;; c/defnode-slow3 にて =defn で生成する関数の、
                             ;; 引数 arg_visited の初期値として設定する
                             #{})
                (do
                  (when (= ~arg_pos (count @o/SENT))
                    ~@body)
                  (u2/fail))))))


;; o/SENT 不要版

(defmacro with-parses-slow4
  [node-start sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(do
       (reset! u2/PATHS [])

       (u/=bind [~'parse ~arg_pos ~arg_regs]
                (~node-start 0 '()
                             ;; c/defnode-slow4 にて =defn で生成する関数の、
                             ;; 引数 arg_visited の初期値
                             #{}
                             ;; 引数 arg_sent の初期値
                             ~sent)
                (do
                  (when (= ~arg_pos (count ~sent))
                    ~@body)
                  (u2/fail))))))


;; u2/PATHS 不要版

(defmacro with-parses-slow5
  [node-start sent & body]
  (with-gensyms
    (arg_pos
      ;; compile-cmds の終了時に付与しているシンボル名と一致させる必要がある
      arg_regs)
    `(u/=bind [~'parse ~arg_pos ~arg_regs]
              (~node-start 0 '()
                           ;; c/defnode-slow5 にて =defn で生成する関数の、
                           ;; 引数 arg_visited の初期値
                           #{}
                           ;; 引数 arg_sent の初期値
                           ~sent)
              (do
                (when (= ~arg_pos (count ~sent))
                  ~@body)
                (u3/fail)))))


(comment

  ;; 依存される側から先に定義しないと、エラーになったりするみたい

  (c/defnode s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は変数名。o/category 内部で値が束縛される。
  (c/defnode s2
    (o/category verb s3 (r/setr v ***)))

  (c/defnode s
    (o/category noun s2 (r/setr subj ***)))

  ;; 「parse」は変数名。p/with-parses 内部で値が束縛される。

  (p/with-parses s '(spot runs) (println "Parsing: " parse))

  )

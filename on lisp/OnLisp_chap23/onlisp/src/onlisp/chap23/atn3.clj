(ns onlisp.chap23.atn3
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]))


;; ======================================================
;;   修飾語　副ネットワーク
;; ======================================================

(c/defnode mods-n
           (o/category n mods-n (r/pushr mods ***))
           (o/up `(~'n-group ~(r/getr mods))))


(c/defnode mods
           (o/category n mods-n (r/setr mods ***)))


;; ======================================================
;;   前置詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np [pos arg_regs])


;; ----------------

(c/defnode pp-np
           (o/up `(~'pp (~'prep ~(r/getr prep))
                        (~'obj ~(r/getr op)))))


(c/defnode pp-prep
           (o/down np pp-np
                   (r/setr op ***)))


(c/defnode _pp
           (o/category prep pp-prep
                       (r/setr prep ***)))


;; ======================================================
;;   名詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np-n [pos arg_regs])


;; ----------------

(c/defnode pron
           (o/up `(~'np (~'pronoun ~(r/getr n)))))


;; np-n
(c/defnode np-mods
           (o/category n np-n (r/setr n ***)))


;; mods, np-mods
(c/defnode np-det
           (o/down mods np-mods (r/setr mods ***))
           (o/jump np-mods (r/setr mods nil)))


(c/defnode np
           (o/category det np-det (r/setr det ***))
           (o/jump np-det (r/setr det nil))
           (o/category pron pron (r/setr n ***)))


(c/defnode np-pp
           (o/up `(~'np
                   (~'det ~(r/getr det))
                   (~'modifiers ~(r/getr mods))
                   (~'noun ~(r/getr n))
                   ~(r/getr _pp))))


;; _pp
(c/defnode np-n
           (o/up `(~'np
                   (~'det ~(r/getr det))
                   (~'modifiers ~(r/getr mods))
                   (~'noun ~(r/getr n))))

           ;; clojure.pprint/pp との名前被りを防ぐ必要がある
           ;; pp -> _pp
           (o/down _pp np-pp
                   (r/setr _pp ***)))


(comment

  ;; onlisp.core=> (p/with-parses mods
  ;;                 '(arrow time) (println "Parsing: " parse))
  ;; #_=> Parsing:  (n-group (time arrow))
  ;; [end]


  ;; onlisp.core=> (p/with-parses np
  ;;                 '(it) (println "Parsing: " parse))
  ;; #_=> Parsing:  (np (pronoun it))
  ;; [end]


  ;; onlisp.core=> (p/with-parses np
  ;;                 '(arrows) (println parse))
  ;;  #_=> (np (det nil)
  ;;           (modifiers nil)
  ;;           (noun arrows))
  ;; [end]


  ;; onlisp.core=> (p/with-parses np
  ;;                 '(a time fly like him) (println parse))
  ;;  #_=> (np (det a)
  ;;           (modifiers (n-group time))
  ;;           (noun fly)
  ;;           (pp (prep like)
  ;;               (obj (np (pronoun him)))))
  ;; [end]

  )

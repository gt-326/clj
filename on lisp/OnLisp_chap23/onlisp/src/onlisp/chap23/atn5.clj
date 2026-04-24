(ns onlisp.chap23.atn5
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]))


;; ======================================================
;;   修飾語　副ネットワーク
;; ======================================================

(c/defnode-slow mods-n
                (o/category n mods-n (r/pushr mods ***))
                (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow mods
                (o/category n mods-n (r/setr mods ***)))


;; ======================================================
;;   前置詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np [pos arg_regs])


;; ----------------

(c/defnode-slow pp-np
                (o/up `(~'pp (~'prep ~(r/getr prep))
                             (~'obj ~(r/getr op)))))


(c/defnode-slow pp-prep
                (o/down np pp-np
                        (r/setr op ***)))


(c/defnode-slow _pp
                (o/category prep pp-prep
                            (r/setr prep ***)))


;; ======================================================
;;   名詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np-n [pos arg_regs])


;; ----------------

(c/defnode-slow pron
                (o/up `(~'np (~'pronoun ~(r/getr n)))))


;; np-n
(c/defnode-slow np-mods
                (o/category n np-n (r/setr n ***)))


;; mods, np-mods
(c/defnode-slow np-det
                (o/down mods np-mods (r/setr mods ***))
                (o/jump np-mods (r/setr mods nil)))


(c/defnode-slow np
                (o/category det np-det (r/setr det ***))
                (o/jump np-det (r/setr det nil))
                (o/category pron pron (r/setr n ***)))


(c/defnode-slow np-pp
                (o/up `(~'np
                        (~'det ~(r/getr det))
                        (~'modifiers ~(r/getr mods))
                        (~'noun ~(r/getr n))
                        ~(r/getr _pp))))


;; _pp
(c/defnode-slow np-n
                (o/up `(~'np
                        (~'det ~(r/getr det))
                        (~'modifiers ~(r/getr mods))
                        (~'noun ~(r/getr n))))

                ;; clojure.pprint/pp との名前被りを防ぐ必要がある
                ;; pp -> _pp
                (o/down _pp np-pp
                        (r/setr _pp ***)))


;; ======================================================
;;   文にたいするネットワーク
;; ======================================================

(c/defnode-slow s-obj
                (o/up `(~'s
                        (~'mood ~(r/getr mood))
                        (~'subj ~(r/getr subj))
                        (~'vcl
                         (~'aux ~(r/getr aux))
                         (~'v ~(r/getr v)))
                        (~'obj ~(r/getr obj)))))


(c/defnode-slow v
                (o/up `(~'s
                        (~'mood ~(r/getr mood))
                        (~'subj ~(r/getr subj))
                        (~'vcl
                         (~'aux ~(r/getr aux))
                         (~'v ~(r/getr v)))))

                (o/down np s-obj
                        (r/setr obj ***)))


(c/defnode-slow s-subj
                (o/category v v (r/setr aux nil) (r/setr v ***)))


(c/defnode-slow s
                (o/down np s-subj (r/setr mood 'decl) (r/setr subj ***))
                (o/category v v
                            (r/setr mood 'imp)
                            (r/setr subj `(~'np (~'pron ~'you)))
                            (r/setr aux nil)
                            (r/setr v ***)))


;; ======================================================

(p/with-parses-slow mods '(arrow time) (println "Parsing slow: " parse))

(p/with-parses-slow np '(it) (println "Parsing slow: " parse))

(p/with-parses-slow np '(arrows) (println "Parsing slow: " parse))

(p/with-parses-slow np '(a time fly like him) (println "Parsing slow: " parse))

(p/with-parses-slow s '(time flies like an arrow) (println "Parsing slow: " parse))


(comment

;; onlisp.core=> (p/with-parses-slow mods
;;                 '(arrow time) (println "Parsing slow: " parse))
;; #_=> Parsing slow:  (n-group (time arrow))
;; [end]


;; onlisp.core=> (p/with-parses-slow np
;;                 '(it) (println "Parsing slow: " parse))
;; #_=> Parsing slow:  (np (pronoun it))
;; [end]


;; onlisp.core=> (p/with-parses-slow np
;;                 '(arrows) (println parse))
;;  #_=> (np (det nil)
;;           (modifiers nil)
;;           (noun arrows))
;; [end]


;; onlisp.core=> (p/with-parses-slow np
;;                 '(a time fly like him) (println parse))
;;  #_=> (np (det a)
;;           (modifiers (n-group time))
;;           (noun fly)
;;           (pp (prep like)
;;               (obj (np (pronoun him)))))
;; [end]


;;    onlisp.core=> (p/with-parses-slow s
;;                    '(time flies like an arrow) (println "Parsing slow: " parse))
;;    #_=> Parsing slow:  (s (mood decl)
;;            (subj (np (det nil)
;;                      (modifiers (n-group time))
;;                      (noun flies)))
;;            (vcl (aux nil)
;;                 (v like))
;;            (obj (np (det an)
;;                     (modifiers nil)
;;                     (noun arrow))))
;;
;;    (s (mood imp)
;;       (subj (np (pron you)))
;;       (vcl (aux nil)
;;            (v time))
;;       (obj (np (det nil)
;;                (modifiers nil)
;;                (noun flies)
;;                (pp (prep like)
;;                    (obj (np (det an)
;;                             (modifiers nil)
;;                             (noun arrow)))))))
;;   [end]

)

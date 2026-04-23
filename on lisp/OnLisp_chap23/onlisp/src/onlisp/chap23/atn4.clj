(ns onlisp.chap23.atn4
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]))


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


;; ======================================================
;;   文にたいするネットワーク
;; ======================================================

(c/defnode s-obj
           (o/up `(~'s
                   (~'mood ~(r/getr mood))
                   (~'subj ~(r/getr subj))
                   (~'vcl
                    (~'aux ~(r/getr aux))
                    (~'v ~(r/getr v)))
                   (~'obj ~(r/getr obj)))))


(c/defnode v
           (o/up `(~'s
                   (~'mood ~(r/getr mood))
                   (~'subj ~(r/getr subj))
                   (~'vcl
                    (~'aux ~(r/getr aux))
                    (~'v ~(r/getr v)))))

           (o/down np s-obj
                   (r/setr obj ***)))


(c/defnode s-subj
           (o/category v v (r/setr aux nil) (r/setr v ***)))


(c/defnode s
           (o/down np s-subj (r/setr mood 'decl) (r/setr subj ***))
           (o/category v v
                       (r/setr mood 'imp)
                       (r/setr subj `(~'np (~'pron ~'you)))
                       (r/setr aux nil)
                       (r/setr v ***)))


;; ======================================================

(p/with-parses mods '(arrow time) (println "Parsing: " parse))

(p/with-parses np '(it) (println "Parsing: " parse))

(p/with-parses np '(arrows) (println "Parsing: " parse))

(p/with-parses np '(a time fly like him) (println "Parsing: " parse))

(p/with-parses s '(time flies like an arrow) (println "Parsing: " parse))


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


;;    onlisp.core=> (p/with-parses s
;;                    '(time flies like an arrow) (println "Parsing: " parse))
;;    #_=> Parsing:  (s (mood decl)
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

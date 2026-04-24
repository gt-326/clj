(ns onlisp.chap23.atn8
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]))


;; ======================================================
;;   修飾語　副ネットワーク
;; ======================================================

(c/defnode-slow4 mods-n
                 (o/category3 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow4 mods
                 (o/category3 n mods-n (r/setr mods ***)))


;; ======================================================
;;   前置詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np [pos arg_regs arg_visited arg_sent])


;; ----------------

(c/defnode-slow4 pp-np
                 (o/up `(~'pp (~'prep ~(r/getr prep))
                              (~'obj ~(r/getr op)))))


(c/defnode-slow4 pp-prep
                 (o/down3 np pp-np
                          (r/setr op ***)))


(c/defnode-slow4 _pp
                 (o/category3 prep pp-prep
                              (r/setr prep ***)))


;; ======================================================
;;   名詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np-n [pos arg_regs arg_visited arg_sent])


;; ----------------

(c/defnode-slow4 pron
                 (o/up `(~'np (~'pronoun ~(r/getr n)))))


;; np-n
(c/defnode-slow4 np-mods
                 (o/category3 n np-n (r/setr n ***)))


;; mods, np-mods
(c/defnode-slow4 np-det
                 (o/down3 mods np-mods (r/setr mods ***))
                 (o/jump3 np-mods (r/setr mods nil)))


(c/defnode-slow4 np
                 (o/category3 det np-det (r/setr det ***))
                 (o/jump3 np-det (r/setr det nil))
                 (o/category3 pron pron (r/setr n ***)))


(c/defnode-slow4 np-pp
                 (o/up `(~'np
                         (~'det ~(r/getr det))
                         (~'modifiers ~(r/getr mods))
                         (~'noun ~(r/getr n))
                         ~(r/getr _pp))))


;; _pp
(c/defnode-slow4 np-n
                 (o/up `(~'np
                         (~'det ~(r/getr det))
                         (~'modifiers ~(r/getr mods))
                         (~'noun ~(r/getr n))))

                 ;; clojure.pprint/pp との名前被りを防ぐ必要がある
                 ;; pp -> _pp
                 (o/down3 _pp np-pp
                          (r/setr _pp ***)))


;; ======================================================
;;   文にたいするネットワーク
;; ======================================================

(c/defnode-slow4 s-obj
                 (o/up `(~'s
                         (~'mood ~(r/getr mood))
                         (~'subj ~(r/getr subj))
                         (~'vcl
                          (~'aux ~(r/getr aux))
                          (~'v ~(r/getr v)))
                         (~'obj ~(r/getr obj)))))


(c/defnode-slow4 v
                 (o/up `(~'s
                         (~'mood ~(r/getr mood))
                         (~'subj ~(r/getr subj))
                         (~'vcl
                          (~'aux ~(r/getr aux))
                          (~'v ~(r/getr v)))))

                 (o/down3 np s-obj
                          (r/setr obj ***)))


(c/defnode-slow4 s-subj
                 (o/category3 v v (r/setr aux nil) (r/setr v ***)))


(c/defnode-slow4 s
                 (o/down3 np s-subj (r/setr mood 'decl) (r/setr subj ***))
                 (o/category3 v v
                              (r/setr mood 'imp)
                              (r/setr subj `(~'np (~'pron ~'you)))
                              (r/setr aux nil)
                              (r/setr v ***)))


;; ======================================================

(p/with-parses-slow4 mods '(arrow time) (println "Parsing slow4: " parse))

(p/with-parses-slow4 np '(it) (println "Parsing slow4: " parse))

(p/with-parses-slow4 np '(arrows) (println "Parsing slow4: " parse))

(p/with-parses-slow4 np '(a time fly like him) (println "Parsing slow4: " parse))

(p/with-parses-slow4 s '(time flies like an arrow) (println "Parsing slow4: " parse))


(comment

;; onlisp.core=> (p/with-parses-slow4 mods
;;                 '(arrow time) (println "Parsing slow4: " parse))
;; #_=> Parsing slow4:  (n-group (time arrow))
;; [end]


;; onlisp.core=> (p/with-parses-slow4 np
;;                 '(it) (println "Parsing slow4: " parse))
;; #_=> Parsing slow4:  (np (pronoun it))
;; [end]


;; onlisp.core=> (p/with-parses-slow4 np
;;                 '(arrows) (println parse))
;;  #_=> (np (det nil)
;;           (modifiers nil)
;;           (noun arrows))
;; [end]


;; onlisp.core=> (p/with-parses-slow4 np
;;                 '(a time fly like him) (println parse))
;;  #_=> (np (det a)
;;           (modifiers (n-group time))
;;           (noun fly)
;;           (pp (prep like)
;;               (obj (np (pronoun him)))))
;; [end]


;;    onlisp.core=> (p/with-parses-slow4 s
;;                    '(time flies like an arrow) (println "Parsing slow4: " parse))
;;    #_=> Parsing slow4:  (s (mood decl)
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

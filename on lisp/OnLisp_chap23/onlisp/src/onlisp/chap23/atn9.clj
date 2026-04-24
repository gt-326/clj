(ns onlisp.chap23.atn9
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]))


;; ======================================================
;;   修飾語　副ネットワーク
;; ======================================================

(c/defnode-slow5 mods-n
                 (o/category4 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow5 mods
                 (o/category4 n mods-n (r/setr mods ***)))


;; ======================================================
;;   前置詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np [pos arg_regs arg_visited arg_sent])


;; ----------------

(c/defnode-slow5 pp-np
                 (o/up `(~'pp (~'prep ~(r/getr prep))
                              (~'obj ~(r/getr op)))))


(c/defnode-slow5 pp-prep
                 (o/down3 np pp-np
                          (r/setr op ***)))


(c/defnode-slow5 _pp
                 (o/category4 prep pp-prep
                              (r/setr prep ***)))


;; ======================================================
;;   名詞句　副ネットワーク
;; ======================================================

;; 事前に以下を定義する必要がある
(c/defnode-declare np-n [pos arg_regs arg_visited arg_sent])


;; ----------------

(c/defnode-slow5 pron
                 (o/up `(~'np (~'pronoun ~(r/getr n)))))


;; np-n
(c/defnode-slow5 np-mods
                 (o/category4 n np-n (r/setr n ***)))


;; mods, np-mods
(c/defnode-slow5 np-det
                 (o/down3 mods np-mods (r/setr mods ***))
                 (o/jump3 np-mods (r/setr mods nil)))


(c/defnode-slow5 np
                 (o/category4 det np-det (r/setr det ***))
                 (o/jump3 np-det (r/setr det nil))
                 (o/category4 pron pron (r/setr n ***)))


(c/defnode-slow5 np-pp
                 (o/up `(~'np
                         (~'det ~(r/getr det))
                         (~'modifiers ~(r/getr mods))
                         (~'noun ~(r/getr n))
                         ~(r/getr _pp))))


;; _pp
(c/defnode-slow5 np-n
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

(c/defnode-slow5 s-obj
                 (o/up `(~'s
                         (~'mood ~(r/getr mood))
                         (~'subj ~(r/getr subj))
                         (~'vcl
                          (~'aux ~(r/getr aux))
                          (~'v ~(r/getr v)))
                         (~'obj ~(r/getr obj)))))


(c/defnode-slow5 v
                 (o/up `(~'s
                         (~'mood ~(r/getr mood))
                         (~'subj ~(r/getr subj))
                         (~'vcl
                          (~'aux ~(r/getr aux))
                          (~'v ~(r/getr v)))))

                 (o/down3 np s-obj
                          (r/setr obj ***)))


(c/defnode-slow5 s-subj
                 (o/category4 v v (r/setr aux nil) (r/setr v ***)))


(c/defnode-slow5 s
                 (o/down3 np s-subj (r/setr mood 'decl) (r/setr subj ***))
                 (o/category4 v v
                              (r/setr mood 'imp)
                              (r/setr subj `(~'np (~'pron ~'you)))
                              (r/setr aux nil)
                              (r/setr v ***)))


;; ======================================================

(defn foo
  []
  (p/with-parses-slow5 mods '(arrow time) (println "Parsing slow5: " parse))

  (p/with-parses-slow5 np '(it) (println "Parsing slow5: " parse))

  (p/with-parses-slow5 np '(arrows) (println "Parsing slow5: " parse))

  (p/with-parses-slow5 np '(a time fly like him) (println "Parsing slow5: " parse))

  (p/with-parses-slow5 s '(time flies like an arrow) (println "Parsing slow5: " parse)))


(comment

;; onlisp.core=> (p/with-parses-slow5 mods
;;                 '(arrow time) (println "Parsing slow5: " parse))
;; #_=> Parsing slow5:  (n-group (time arrow))
;; [end]


;; onlisp.core=> (p/with-parses-slow5 np
;;                 '(it) (println "Parsing slow5: " parse))
;; #_=> Parsing slow5:  (np (pronoun it))
;; [end]


;; onlisp.core=> (p/with-parses-slow5 np
;;                 '(arrows) (println parse))
;;  #_=> (np (det nil)
;;           (modifiers nil)
;;           (noun arrows))
;; [end]


;; onlisp.core=> (p/with-parses-slow5 np
;;                 '(a time fly like him) (println parse))
;;  #_=> (np (det a)
;;           (modifiers (n-group time))
;;           (noun fly)
;;           (pp (prep like)
;;               (obj (np (pronoun him)))))
;; [end]


;;    onlisp.core=> (p/with-parses-slow5 s
;;                    '(time flies like an arrow) (println "Parsing slow5: " parse))
;;    #_=> Parsing slow5:  (s (mood decl)
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

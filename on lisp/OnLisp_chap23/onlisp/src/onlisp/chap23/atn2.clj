(ns onlisp.chap23.atn2
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]
    [onlisp.chap23.common.layer3.parser :as p]))


(comment

(reset! o/DICTIONARY {'time  '(n v) 'arrow '(n)})


;; ======================================================
;; 高速な：非決定的選択オペレータ u2/choose を使っている
;; ======================================================

;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
(c/defnode mods-n
           (o/category n mods-n (r/pushr mods ***))
           (o/up `(~'n-group ~(r/getr mods))))


(c/defnode mods
           (o/category n mods-n (r/setr mods ***)))


;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
(p/with-parses mods
               '(time arrow) (println "Parsing: " parse))


;;  #_=>          #_=> #'onlisp.core/=mods-n
;;  onlisp.core=>          #_=> #'onlisp.core/=mods
;;  onlisp.core=>          #_=> Parsing:  (n-group (arrow time))
;;  [end]

;; -------------------

;; 当初のバージョン u2/choose_ を使うとエラーになる

;;    #_=>          #_=> #'onlisp.core/=mods-n
;;    onlisp.core=>          #_=> #'onlisp.core/=mods
;;    onlisp.core=>          #_=> Parsing:  (n-group (arrow time))
;;    Execution error (ArityException) at onlisp.core/=mods-n$fn (form-init8036475942761870015.clj:3).
;;    Wrong number of args (3) passed to: clojure.core/identity



;; ======================================================
;; 本物の：非決定的選択オペレータ u2/true-choose を使っている
;; ======================================================

(c/defnode-slow mods-n
                (o/category n mods-n (r/pushr mods ***))
                (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow mods
                (o/category n mods-n (r/setr mods ***)))


(p/with-parses-slow mods
                    '(time arrow) (println "Parsing slow: " parse))


;;  #_=>          #_=> #'onlisp.core/=mods-n
;;  onlisp.core=>          #_=> #'onlisp.core/=mods
;;  onlisp.core=>          #_=> Parsing  slow:  (n-group (arrow time))
;;  [end]


;; ====================================================================
;; シンプルな本物の：非決定的選択オペレータ u2/true-choose-simple を使っている
;; ====================================================================

;; atom 版

(c/defnode-slow2 mods-n
                 (o/category2 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow2 mods
                 (o/category2 n mods-n (r/setr mods ***)))


(p/with-parses-slow2 mods
                     '(time arrow) (println "Parsing slow2: " parse))


;;    #_=>          #_=> #'onlisp.core/=mods-n
;;    onlisp.core=>          #_=> #'onlisp.core/=mods
;;    onlisp.core=>          #_=> Parsing slow2:  (n-group (arrow time))
;;    [end]

;; -------------------

;; set 版

(c/defnode-slow3 mods-n
                 (o/category2 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow3 mods
                 (o/category2 n mods-n (r/setr mods ***)))


(p/with-parses-slow3 mods
                     '(time arrow) (println "Parsing slow3: " parse))


;;    #_=>          #_=> #'onlisp.core/=mods-n
;;    onlisp.core=>          #_=> #'onlisp.core/=mods
;;    onlisp.core=>          #_=> Parsing slow3:  (n-group (arrow time))
;;    [end]


;; -------------------

;; o/SENT 不要版

(c/defnode-slow4 mods-n
                 (o/category3 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow4 mods
                 (o/category3 n mods-n (r/setr mods ***)))


(p/with-parses-slow4 mods
                     '(time arrow) (println "Parsing slow4: " parse))


;;    #_=>          #_=> #'onlisp.core/=mods-n
;;    onlisp.core=>          #_=> #'onlisp.core/=mods
;;    onlisp.core=>          #_=> Parsing slow4:  (n-group (arrow time))
;;    [end]


;; ====================================================================
;; シンプルな本物の：非決定的選択オペレータ u3/true-choose-simple を使っている
;; ====================================================================

;; u2/PATHS 不要版

(c/defnode-slow5 mods-n
                 (o/category4 n mods-n (r/pushr mods ***))
                 (o/up `(~'n-group ~(r/getr mods))))


(c/defnode-slow5 mods
                 (o/category4 n mods-n (r/setr mods ***)))


(p/with-parses-slow5 mods
                     '(time arrow) (println "Parsing slow5: " parse))


;;#_=>          #_=> #'onlisp.core/=mods-n
;;onlisp.core=>          #_=> #'onlisp.core/=mods
;;onlisp.core=>          #_=> Parsing slow5:  (n-group (arrow time))
;;[end]


  )

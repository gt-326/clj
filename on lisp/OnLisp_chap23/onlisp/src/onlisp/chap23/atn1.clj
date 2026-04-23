(ns onlisp.chap23.atn1
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.chap23.common.layer2.opr :as o]
    [onlisp.chap23.common.layer2.reg :as r]))


(comment

  (reset! o/DICTIONARY {'spot '(noun) 'runs '(verb)})

  ;;======================================================
  ;; 高速な：非決定的選択オペレータ u2/choose を使っている
  ;;======================================================

  (c/defnode s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode s2
    (o/category verb s3 (r/setr v ***)))

  (c/defnode s
    (o/category noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses s
    '(spot runs) (println "Parsing: " parse))


;;  #_=> #'onlisp.core/=s3
;;  onlisp.core=>          #_=> #'onlisp.core/=s2
;;  onlisp.core=>          #_=> #'onlisp.core/=s
;;  onlisp.core=>          #_=> Parsing:  (sentence (subject spot) (verb runs))
;;  [end]


  ;;======================================================
  ;; 本物の：非決定的選択オペレータ u2/true-choose を使っている
  ;;======================================================

  (c/defnode-slow s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode-slow s2
    (o/category verb s3 (r/setr v ***)))

  (c/defnode-slow s
    (o/category noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses-slow s
    '(spot runs) (println "Parsing slow: " parse))

  ;;  #_=> #'onlisp.core/=s3
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s2
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s
  ;;  onlisp.core=>          #_=> Parsing slow:  (sentence (subject spot) (verb runs))
  ;;  [end]


  ;;====================================================================
  ;; シンプルな本物の：非決定的選択オペレータ u2/true-choose-simple を使っている
  ;;====================================================================

  ;; atom 版

  (c/defnode-slow2 s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode-slow2 s2
    (o/category2 verb s3 (r/setr v ***)))

  (c/defnode-slow2 s
    (o/category2 noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses-slow2 s
    '(spot runs) (println "Parsing slow2: " parse))

  ;;  #_=> #'onlisp.core/=s3
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s2
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s
  ;;  onlisp.core=>          #_=> Parsing slow2:  (sentence (subject spot) (verb runs))
  ;;  [end]

  ;;-------------------

  ;; set 版

  (c/defnode-slow3 s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode-slow3 s2
    (o/category2 verb s3 (r/setr v ***)))

  (c/defnode-slow3 s
    (o/category2 noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses-slow3 s
    '(spot runs) (println "Parsing slow3: " parse))

  ;;  #_=> #'onlisp.core/=s3
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s2
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s
  ;;  onlisp.core=>          #_=> Parsing slow3:  (sentence (subject spot) (verb runs))
  ;;  [end]

  ;;-------------------

  ;; o/SENT 不要版

  (c/defnode-slow4 s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode-slow4 s2
    (o/category3 verb s3 (r/setr v ***)))

  (c/defnode-slow4 s
    (o/category3 noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses-slow4 s
    '(spot runs) (println "Parsing slow4: " parse))

  ;;  #_=> #'onlisp.core/=s3
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s2
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s
  ;;  onlisp.core=>          #_=> Parsing slow4:  (sentence (subject spot) (verb runs))
  ;;  [end]

  ;;-------------------

  ;; u2/PATHS 不要版

  (c/defnode-slow5 s3
    (o/up `(~'sentence
            (~'subject ~(r/getr subj))
            (~'verb ~(r/getr v)))))

  ;; 「***」は固定値で。o/category 内部で変数として値が束縛される。
  (c/defnode-slow5 s2
    (o/category4 verb s3 (r/setr v ***)))

  (c/defnode-slow5 s
    (o/category4 noun s2 (r/setr subj ***)))

  ;; 「parse」は固定値で。p/with-parses 内部で変数として値が束縛される。
  (p/with-parses-slow5 s
    '(spot runs) (println "Parsing slow5: " parse))


  ;;  #_=> #'onlisp.core/=s3
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s2
  ;;  onlisp.core=>          #_=> #'onlisp.core/=s
  ;;  onlisp.core=>          #_=> Parsing slow5:  (sentence (subject spot) (verb runs))
  ;;  [end]
  )

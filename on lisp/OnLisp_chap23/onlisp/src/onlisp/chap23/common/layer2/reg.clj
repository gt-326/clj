(ns onlisp.chap23.common.layer2.reg
  (:require
    [onlisp.chap23.common.layer1.core :as c]))


;; [ P318 chap23.4 ]


(defn search-types
  [k r]
  (rest
    (some
      (fn [x#] (and (= k (first x#)) x#))
      r)))


(comment

  ;;  onlisp.core=> (some
  ;;                 (fn [x#] (if (= 'spot (first x#)) x#))
  ;;                 '((spot noun) (runs verb)))
  ;;  #_=>          #_=> (spot noun)

  ;;  onlisp.core=> (some
  ;;                 (fn [x#] (if (= 'runs (first x#)) x#))
  ;;                 '((spot noun) (runs verb)))
  ;;  #_=>          #_=> (runs verb)

  ;;  onlisp.core=> (some
  ;;                 (fn [x#] (if (= 'aaa (first x#)) x#))
  ;;                 '((spot noun) (runs verb)))
  ;;  #_=>          #_=> nil

  ;;  onlisp.core=> (rest nil)
  ;;  ()

  )


(defmacro getr_
  [k & [regs]]
  (let [r (or regs 'regs)]
    `(let [~'result
           (rest
             (some
               (fn [x#] (and (= '~k (first x#)) x#))
               (first ~r)))]
       (if (seq (rest ~'result))
         ~'result
         (first ~'result)))))


(defmacro getr
  [k & [regs]]
  (let [r (or regs
              ;; このデフォルト値を c/defnode 内部の引数名と一致させる必要がある
              'arg_regs)]
    `(let [~'result (search-types '~k (first ~r))]
       (if (seq (rest ~'result))
         ~'result
         (first ~'result)))))


(comment

;;  onlisp.core=> (r/getr v '(((subj runs spot) (subj spot))))
;;  nil

;;  onlisp.core=> (r/getr subj '(((subj runs spot) (subj spot))))
;;  (runs spot)

  )


(defmacro setr
  [k v regs]
  `(c/set-register '~k (list ~v) ~regs))


(defmacro pushr_
  [k v regs]
  `(c/set-register '~k
                   (cons '~v (rest
                               ;; (get (first ~regs) '~k)
                               (some
                                 (fn [x#] (and (= '~k (first x#)) x#))
                                 (first ~regs))))
                   ~regs))


(defmacro pushr
  [k v regs]
  `(c/set-register '~k
                   (cons ~v (search-types '~k (first ~regs)))
                   ~regs))


(comment

;;  onlisp.core=> (r/setr subj spot '())
;;  (((subj spot)))

;;  onlisp.core=> (r/setr v runs '(((subj spot))))
;;  (((v runs) (subj spot)))

;;  onlisp.core=> (r/pushr v runs '(((subj spot))))
;;  (((v runs) (subj spot)))


;;  すでに regs に同じキーのレコードがある場合に、「setr」と「pushr」の違いが出る。

;;  onlisp.core=> (r/setr subj runs '(((subj spot))))
;;  (((subj runs) (subj spot)))

;;  onlisp.core=> (r/pushr subj runs '(((subj spot))))
;;  (((subj runs spot) (subj spot)))

  )

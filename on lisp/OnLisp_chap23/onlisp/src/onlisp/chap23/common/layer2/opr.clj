(ns onlisp.chap23.common.layer2.opr
  (:require
    [onlisp.chap23.common.layer1.core :as c]
    [onlisp.common.util :as u]
    [onlisp.common.util2 :as u2]
    [onlisp.common.util3 :as u3]))


;; [ P318 chap23.4 ]

;; ^:dynamic を付与する必要はなかった。
;; また、この SENT を使用しない実装が実現可能だとわかった。

(def SENT (atom []))


(def DICTIONARY
  (atom
    {'do    '(aux v)
     'does  '(aux v)
     'did   '(aux v)
     'time  '(n v)
     'times '(n v)
     'fly   '(n v)
     'flies '(n v)
     'like  '(v prep)
     'liked '(v)
     'likes '(v)
     'a     '(det)
     'an    '(det)
     'the   '(det)
     'arrow '(n)
     'arrows '(n)
     'i     '(pron)
     'you   '(pron)
     'he    '(pron)
     'she   '(pron)
     'him   '(pron)
     'her   '(pron)
     'it    '(pron)
     'spot  '(noun)
     'runs  '(verb)}))


(defn dictionary-word
  [word]
  (get @DICTIONARY word '()))


(defn register-word!
  [word categories]
  (swap! DICTIONARY assoc word categories))


(defmacro category
  [cat next & cmds]
  `(if (= (count @SENT) ~'pos)
     (u2/fail)

     (let [~'*** (nth @SENT ~'pos "nothing found")]
       (if (some (fn [x#] (= x# '~cat)) (dictionary-word ~'***))
         (~next
          (inc ~'pos)
          ~(c/compile-cmds cmds))
         (u2/fail)))))


;; atom、set 版

(defmacro category2
  [cat next & cmds]
  `(if (= (count @SENT) ~'pos)
     (u2/fail)

     (let [~'*** (nth @SENT ~'pos "nothing found")]
       (if (some (fn [x#] (= x# '~cat)) (dictionary-word ~'***))
         (~next
          (inc ~'pos)
          ~(c/compile-cmds cmds)
          ;; c/defnode-slow2、c/defnode-slow3 にて =defn で生成する関数の
          ;; 引数 arg_visited を指定する
          ~'arg_visited)
         (u2/fail)))))


;; o/SENT 不要版

(defmacro category3
  [cat next & cmds]
  `(if (= (count ~'arg_sent) ~'pos)
     (u2/fail)

     (let [~'*** (nth ~'arg_sent ~'pos "nothing found")]
       (if (some (fn [x#] (= x# '~cat)) (dictionary-word ~'***))
         (~next
          (inc ~'pos)
          ~(c/compile-cmds cmds)
          ;; c/defnode-slow4 にて =defn で生成する関数の
          ;; 引数 arg_visited を指定する
          ~'arg_visited
          ;; 引数 arg_sent を指定する
          ~'arg_sent)
         (u2/fail)))))


;; u2/PATHS 不要版

(defmacro category4_
  [cat next & cmds]
  `(if (= (count ~'arg_sent) ~'pos)
     (u3/fail)

     (let [~'*** (nth ~'arg_sent ~'pos "nothing found")]
       (if (some (fn [x#] (= x# '~cat)) (dictionary-word ~'***))
         (~next
          (inc ~'pos)
          ~(c/compile-cmds cmds)
          ;; c/defnode-slow5 にて =defn で生成する関数の
          ;; 引数 arg_visited を指定する
          ~'arg_visited
          ;; 引数 arg_sent を指定する
          ~'arg_sent)
         (u3/fail)))))


(defmacro category4
  [cat next & cmds]
  `(if (= (count ~'arg_sent) ~'pos)
     (u3/fail)

     (let [~'*** (nth ~'arg_sent ~'pos "nothing found")]
       (if-not (some (fn [x#] (= x# '~cat)) (dictionary-word ~'***))
         (u3/fail)

         (~next
          (inc ~'pos)
          ~(c/compile-cmds cmds)
          ;; c/defnode-slow5 にて =defn で生成する関数の
          ;; 引数 arg_visited を指定する
          ~'arg_visited
          ;; 引数 arg_sent を指定する
          ~'arg_sent)))))


(defmacro down
  [sub next & cmds]
  `(u/=bind [~'*** ~'pos ~'arg_regs]
            (~sub ~'pos (cons nil ~'arg_regs))
            (~next ~'pos ~(c/compile-cmds cmds))))


(defmacro jump
  [next & cmds]
  `(~next ~'pos ~(c/compile-cmds cmds)))


(defmacro up
  [expr]
  `(u/=values ~expr ~'pos
              (rest
                ;; このシンボルを c/defnode 内部の引数名と一致させる必要がある
                ~'arg_regs)))

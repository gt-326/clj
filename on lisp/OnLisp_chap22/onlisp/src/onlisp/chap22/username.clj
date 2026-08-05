(ns onlisp.chap22.username
  (:require
    [onlisp.chap22.common :as c]
    [onlisp.common.util :as u]))


;; サインアップ（20章）で「名前が登録済みだったら最初からやり直す」ではなく、
;; 「システム側が taro, taro1, taro2, ... のように自動で空いている候補を
;; 提案する」場合の実装。候補を1つずつ試す(choose-bind)→登録済みならfail
;; →空いていたらcut、という構造は find-boxes5(nondetermin.clj)と全く同じ。


(def registered-names
  "登録済みのユーザー名（例として固定データ）"
  #{"taro" "hanako" "taro1" "taro2" "taro3"})


(defn candidates
  "base, base1, base2, ..., base(limit-1) という候補列を生成する"
  [base limit]
  (cons base (map #(str base %) (range 1 limit))))


(u/=defn find-available-username [base]
         (reset! c/PATHS [])

         ;; mark: この時点をチェックポイントとして記録する。
         ;; ※ 今回はchoose-bindが1段しかないフラットな探索なので、
         ;;   実はmarkなしのcutだけでもPATHSは正しく空になる
         ;;  （下に守るべき他の選択肢が積まれていないため）。
         ;;   それでもmarkを置くのは、find-boxes5と同じ書き方に揃えておけば、
         ;;   将来これが「もっと外側のchoose-bindの内側」に組み込まれても
         ;;   （＝下に他の選択肢が積まれる状況になっても）安全なようにするため。
         (c/mark)

         (c/choose-bind name (candidates base 20)
                        (if (contains? registered-names name)
                          (do
                            (println name "は登録済みです。次の候補を試します。")
                            (c/fail))
                          (do
                            (println name "は使用可能です。")
                            ;; 見つかったので、残りの未試行候補（PATHSに積まれている
                            ;; 「次の候補を試す」継続）を捨てる
                            (c/cut)
                            (u/=values name)))))


(comment

  ;; onlisp.core=> (u/find-available-username "taro")
  ;; taro は登録済みです。次の候補を試します。
  ;; taro1 は登録済みです。次の候補を試します。
  ;; taro2 は登録済みです。次の候補を試します。
  ;; taro3 は登録済みです。次の候補を試します。
  ;; taro4 は使用可能です。
  ;; taro4

  ;; ↑ cutが効いているか確認するため、この後さらにfailを呼んでみる。
  ;;   cutが正しく効いていれば、taro5以降を試すことなく即座に[end]になるはず。

  ;; onlisp.core=> (c/fail)
  ;; [end]

  )

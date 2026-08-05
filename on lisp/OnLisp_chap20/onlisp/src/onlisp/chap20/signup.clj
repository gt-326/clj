(ns onlisp.chap20.signup
  (:require
    [onlisp.chap20.continuations :as c]))


;; 20章の *cont*/=defn/=bind をそのまま使い、
;; 「次のHTTPリクエストを待つ」ことを
;; 「pending に継続を保存していったん呼び出し元(REPL)へ戻る」ことで
;; シミュレートする、多ページのサインアップフォーム。
;;
;; 本物のWebサーバーは使わない。代わりに
;;   (signup)       … 1問目を表示し、pendingに継続を保存して戻る
;;   (submit! val)  … 次のリクエストが来た、という想定でpendingの継続を呼ぶ
;; をREPLから交互に呼ぶことで「ページ送信 → 次のページ表示」を模擬する。
;;
;; core.clj の saved/restart（dft-node による木の巡回）と同じ発想で、
;; 今回は分岐せず1本道を進むだけなので、スタックではなく単一の atom で足りる。


(def pending
  "次の入力を待っている継続を1つだけ保持する"
  (atom nil))


(defn get-input
  "プロンプトを表示し、現在の *cont*
  （=bindが用意した「この入力を受け取ったら次に何をするか」という継続）を
  pending に保存して、呼び出し元へいったん戻る。
  戻り値そのものは使わない（『次のページが表示されている』という状態を表すだけ）。"
  [prompt]
  (println prompt)
  (reset! pending c/*cont*)
  '[waiting])


(defn submit!
  "次のリクエストが届いた、という想定で、pending に保存されている継続に
  value を渡して処理を再開する。"
  [value]
  (if-let [cont @pending]
    (do
      (reset! pending nil)
      (cont value))
    (println "入力待ちの状態ではありません。(signup) から始めてください。")))


;; パスワード + 確認用パスワードの2ステップ。
;; 不一致なら、name/emailはそのまま引数として持ち回りつつ、
;; 自分自身(ask-password)を呼び直す = 「メールアドレス入力後の状態」から
;; やり直すことになる。22章のrestart（保存しておいた継続に戻る）と同じ発想を、
;; 「あの時点の引数を持ったまま自分自身を再度呼ぶ」という単純な形で実現している。

(c/=defn ask-password [name email]
         (c/=bind [password] (get-input "パスワードを入力してください:")
                  (c/=bind [password-re] (get-input "確認のため、もう一度パスワードを入力してください:")
                           (if (= password password-re)
                             (do
                               (println "登録完了:" name email password)
                               ;; *cont* のデフォルトはidentityで1引数しか
                               ;; 受け取れない（22章のtwo-numbersと同じ理由）
                               ;; ので、リストにまとめて渡す。
                               (c/=values (list name email password)))
                             (do
                               (println "パスワードが一致しませんでした。もう一度お試しください。")
                               (ask-password name email))))))


(c/=defn signup []
         (c/=bind [name] (get-input "名前を入力してください:")
                  (c/=bind [email] (get-input "メールアドレスを入力してください:")
                           (ask-password name email))))


;; 注意: =defn が生成するマクロ(signup)は、定義したのと同じ名前空間からしか
;; 正しく呼び出せない（=defnのf（内部関数名 =signup）が名前空間非依存の
;; 裸のシンボルとして展開されるため）。onlisp.core からは
;; (signup/signup) のように別名前空間経由で呼んでもエラーになる。
;; message/baz/dft2など、core.clj内の既存の=defn例が全部core.clj自身に
;; 書かれているのも、同じ理由でこの制約を踏んでいたためと考えられる。
;;
;; 試すときは、まずこの名前空間に切り替えてから呼ぶこと:
;;   onlisp.core=> (in-ns 'onlisp.chap20.signup)
;;   onlisp.chap20.signup=> (signup)
;;   onlisp.chap20.signup=> (submit! ...)

(comment

  ;; onlisp.chap20.signup=> (signup)
  ;; 名前を入力してください:
  ;; [waiting]

  ;; onlisp.chap20.signup=> (submit! "Taro")
  ;; メールアドレスを入力してください:
  ;; [waiting]

  ;; onlisp.chap20.signup=> (submit! "taro@example.com")
  ;; パスワードを入力してください:
  ;; [waiting]

  ;; onlisp.chap20.signup=> (submit! "hunter2")
  ;; 確認のため、もう一度パスワードを入力してください:
  ;; [waiting]

  ;; パスワードが不一致の場合 → メールアドレス入力後の状態(パスワードを聞く直前)からやり直す

  ;; onlisp.chap20.signup=> (submit! "wrongpass")
  ;; パスワードが一致しませんでした。もう一度お試しください。
  ;; パスワードを入力してください:
  ;; [waiting]

  ;; onlisp.chap20.signup=> (submit! "hunter2")
  ;; 確認のため、もう一度パスワードを入力してください:
  ;; [waiting]

  ;; onlisp.chap20.signup=> (submit! "hunter2")
  ;; 登録完了: Taro taro@example.com hunter2
  ;; ("Taro" "taro@example.com" "hunter2")

  ;; ↑ 実際にlein replで実行して確認済み（不一致→やり直し→成功、まで通しで確認）

  )

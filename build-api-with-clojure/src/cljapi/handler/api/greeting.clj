;; ./src/cljapi/handler/api/greeting.clj
(ns cljapi.handler.api.greeting
  (:require
    ;; 10_Clojureで作るAPI ルーターを追加する　その３：Systemの再起動を減らす
    [cljapi.handler :as h]
    [cljapi.router :as r]
    [ring.util.http-response :as res]))


(defn hello
  [_]
  (res/ok "greeting: Hello! \n"))


(defn bye
  [_]
  (res/ok "greeting: Goodbye!? \n"))


;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
;; その３−１：JSONの入出力に対応する（エラーのレスポンスをもらう）

;; (defmethod h/handler [::r/hello :get]
;;   [_]
;;   (res/ok "Hello \n"))


;; (defmethod h/handler [::r/bye :get]
;;   [_]
;;   (res/ok "Bye \n"))


(defmethod h/handler [::r/hello :get]
  [_]
  (res/ok {:greeting "Hello, hello \n"}))


(defmethod h/handler [::r/bye :get]
  [_]
  (res/ok
   ;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
   ;; その３−２：JSONの入出力に対応する

   ;; cljapi.router/memoized->camelCaseString により、
   ;; 以下のように key が変換される。
   ;; [ ":greeting-bye-bye" -> "greetingByeBye" ]

   ;;{:greeting "Bye, bye \n"}
   {:greeting-bye-bye "Bye, bye \n"}
   ))

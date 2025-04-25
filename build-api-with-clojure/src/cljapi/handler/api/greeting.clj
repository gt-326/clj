;; ./src/cljapi/handler/api/greeting.clj
(ns cljapi.handler.api.greeting
  (:require
   [ring.util.http-response :as res]

   ;; 10_Clojureで作るAPI ルーターを追加する　その３：Systemの再起動を減らす
   [cljapi.handler :as h]
   [cljapi.router :as r]))


(defn hello
  [_]
  (res/ok "greeting: Hello! \n"))


(defn bye
  [_]
  (res/ok "greeting: Goodbye!? \n"))


(defmethod h/handler [::r/hello :get]
  [_]
  (res/ok "Hello \n"))


(defmethod h/handler [::r/bye :get]
  [_]
  (res/ok "Bye \n"))

;; ./src/cljapi/handler/api/greeting.clj
(ns cljapi.handler.api.greeting
  (:require
   [ring.util.http-response :as res]))


(defn hello
  [_]
  (res/ok "greeting: Hello! \n"))


(defn bye
  [_]
  (res/ok "greeting: Goodbye! \n"))

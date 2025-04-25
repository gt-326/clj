;; ./src/cljapi/handler/health.clj
(ns cljapi.handler.health
  (:require
   [ring.util.http-response :as res]

   ;; 10_Clojureで作るAPI ルーターを追加する　その３：Systemの再起動を減らす
   [cljapi.handler :as h]
   [cljapi.router :as r]))


(defn health
  "ヘルスチェックに対応するためのHandlerとして意図しています"
  [_]
  (res/ok "health: Application is running \n"))


(defmethod h/handler [::r/health :get]
  [_]
  (res/ok "Application is running \n"))

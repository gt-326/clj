;; ./src/cljapi/handler/health.clj
(ns cljapi.handler.health
  (:require
    [ring.util.http-response :as res]))


(defn health
  "ヘルスチェックに対応するためのHandlerとして意図しています"
  [_]
  (res/ok "health: Application is running \n"))

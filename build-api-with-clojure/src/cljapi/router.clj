;; ./src/cljapi/router.clj
(ns cljapi.router
  (:require
    ;; [cljapi.handler.api.greeting :as api.greeting]
    ;; [cljapi.handler.health :as health]
    ;; 具体的なhandlerへの依存が消えている
    [cljapi.handler :as h]
    [reitit.ring :as ring]
    ;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
    ;; その２：定番のMiddlewareをまとめて入れる
    [ring.middleware.defaults :as m.defautls]))


;; (def router
;;   (ring/router
;;    [["/health" health/health]
;;     ["/api"
;;      ["/hello" api.greeting/hello]
;;      ["/bye" api.greeting/bye]]]))

(def router_
  (ring/router
    [["/health" {:name ::health
                 :handler h/handler}]
     ["/api"
      ["/hello" {:name ::hello
                 :handler h/handler}]
      ["/bye" {:name ::bye
               :handler h/handler}]]]))


;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
;; その２：定番のMiddlewareをまとめて入れる
(def ^:private ring-defaults-config
  (-> m.defautls/api-defaults
      ;; ロードバランサーの後ろで動いていると想定して、
      ;; X-Forwarded-For と X-Forwarded-Proto に対応させる
      (assoc :proxy true)))


(def router
  (ring/router
    [["/health" {:name ::health
                 :handler h/handler}]

     ;; Ring-Defaults で入る機能はAPIとしての動作にだけ働けばいい。
     ;;  /api 配下で機能するように適用する。
     ["/api" {:middleware [[m.defautls/wrap-defaults ring-defaults-config]]}
      ["/hello" {:name ::hello
                 :handler h/handler}]
      ["/bye" {:name ::bye
               :handler h/handler}]]]))

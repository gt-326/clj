;; ./src/cljapi/router.clj
(ns cljapi.router
  (:require
    ;; [cljapi.handler.api.greeting :as api.greeting]
    ;; [cljapi.handler.health :as health]
    ;; 具体的なhandlerへの依存が消えている
    [cljapi.handler :as h]
    [reitit.ring :as ring]))


;; (def router
;;   (ring/router
;;    [["/health" health/health]
;;     ["/api"
;;      ["/hello" api.greeting/hello]
;;      ["/bye" api.greeting/bye]]]))

(def router
  (ring/router
    [["/health" {:name ::health
                 :handler h/handler}]
     ["/api"
      ["/hello" {:name ::hello
                 :handler h/handler}]
      ["/bye" {:name ::bye
               :handler h/handler}]]]))

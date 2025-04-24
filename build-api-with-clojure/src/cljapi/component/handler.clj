(ns cljapi.component.handler
  (:require
    [com.stuartsierra.component :as component]

    [cljapi.router :as router]
    [reitit.ring :as ring]))


;; (defn- ring-handler
;;   [_req]
;;   {:status 200
;;    :body "Hello, Clojure API !? \n"})


(defn- build-handler
  []
  (ring/ring-handler router/router))


(defrecord Handler
  [handler]

  component/Lifecycle

  (start
    [this]
;;  (assoc this :handler ring-handler)
    (assoc this :handler (build-handler)))


  (stop
    [this]
    (assoc this :handler nil)))

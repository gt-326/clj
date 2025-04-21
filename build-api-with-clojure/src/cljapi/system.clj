(ns cljapi.system
  (:require
    [cljapi.component.handler :as c.handler]
    [cljapi.component.server :as c.server]
    [com.stuartsierra.component :as component]))


(defn- new-system
  []
  (component/system-map
    ;; ①
    :handler
    ;; cljapi.component.handler/Handler を生成
    (c.handler/map->Handler {})

    ;; ②
    :server (component/using
              ;; cljapi.component.server/Jetty9Server を生成
              ;; J9S {:handler nil, :opts {:join? false, :port 8000}, :server nil}
              (c.server/map->Jetty9Server {:opts {:join? false
                                                  :port 8000}})
              ;; component/usingの第二引数で依存しているコンポーネントを宣言している
              ;; ①
              [:handler])))


(defn start
  []
  (let [system (new-system)]
    (component/start system)))


(defn stop
  [system]
  (component/stop system))


;; (defonce system (atom nil))


;; (defn go
;;   []
;;   (when @system
;;     (stop @system)
;;     (reset! system nil))
;;   (reset! system (start)))

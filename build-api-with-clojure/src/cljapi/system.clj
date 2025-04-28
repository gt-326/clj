(ns cljapi.system
  (:require
    [cljapi.component.handler :as c.handler]
    [cljapi.component.server :as c.server]
    [cljapi.config :as config]
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [unilog.config :as unilog]))


(defn- new-system
  ;; [conf]
  [{:as conf ; :keys [:profile]
    }]
  (component/system-map
    ;; ①
    :handler
    ;; cljapi.component.handler/Handler を生成

    ;; config内のprofileをHandler Componentに渡すように変更している
    ;;  (c.handler/map->Handler {})
    (c.handler/map->Handler {:profile conf})

    ;; ②
    :server (component/using
              ;; cljapi.component.server/Jetty9Server を生成
              ;; J9S {:handler nil, :opts {:join? false, :port 8000}, :server nil}
              (c.server/map->Jetty9Server (:server conf))
              ;; component/usingの第二引数で依存しているコンポーネントを宣言している
              ;; ①
              [:handler])))


(defn- init-logging!
  [conf]
  (unilog/start-logging! (:logging conf)))


;; (defn start
;;   [prof]
;;   (let [conf (config/read-config prof)
;;         system (new-system conf)]
;;     (component/start system)))


(defn start
  [prof]
  (let [config (config/read-config prof)
        system (new-system config)

        _ (init-logging! config)
        _ (log/info "-- system is ready to start --")

        started-system (component/start system)]

    (log/info "-- system is started --")
    started-system))


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

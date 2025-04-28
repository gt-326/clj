;; ./dev/user.clj
(ns user
  (:require
    [cljapi.system :as system]))


(defonce system (atom nil))


(defn start
  []
  ;; (reset! system (system/start :test))
  (reset! system (system/start :dev)))


(defn stop
  []
  (when @system
    (reset! system (system/stop @system))))


(defn go
  []
  (stop)
  (start))


(defn hw
  []
  (println "Hello, world!!"))

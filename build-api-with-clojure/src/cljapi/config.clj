;; ./src/cljapi/config.clj
(ns cljapi.config
  (:require
    [aero.core :as aero]
    [clojure.java.io :as io]))


(defn read-config
  [prof]
  ;; 引数 prof が :dev :prod :test のいずれかでなければ、
  ;; AssertionError 例外が投げられます。
  {:pre [(contains? #{:dev :prod :test} prof)]}

  ;;  (let[edn (io/resource "config.edn")]
  ;;    (assoc
  ;;     (aero/read-config edn {:profile prof})
  ;;     :profile prof))

  (-> (io/resource "config.edn")
      (aero/read-config {:profile prof})
      (assoc :profile prof)))

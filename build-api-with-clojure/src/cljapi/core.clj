;; ./src/cljapi/core.clj
(ns cljapi.core
  (:require
    [ring.adapter.jetty9 :as jetty]))


(defn ring-handler
  [_req]
  {:status 200
   :body "Hello, Clojure API! \n"})


(defn -main
  [& _args]
  (jetty/run-jetty ring-handler {:port 8000}))


;; ファイル全体を評価した後で、[1]の式を評価します。
;; そうすると大量のログがREPLに出力されつつ、Webサーバーが立ち上がります。

(comment
  (-main) ;; [1]
  )

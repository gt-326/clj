(ns cljapi.component.handler
  (:require
    ;; 10_Clojureで作るAPI ルーターを追加する　その３：Systemの再起動を減らす
    ;; 記載しないと「not found」になってしまう
    [cljapi.handler.api.greeting]
    [cljapi.handler.health]
    [cljapi.router :as router]
    [com.stuartsierra.component :as component]
    [reitit.ring :as ring]
    ;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
    ;; その１：APIリクエストをロギングする
    [ring.logger :as m.logger]
    ;; 10_Clojureで作るAPI ルーターを追加する　その２：評価のし忘れを防ぐ
    [ring.middleware.lint :as m.lint]
    [ring.middleware.reload :as m.reload]
    [ring.middleware.stacktrace :as m.stacktrace]))


;; (defn- ring-handler
;;   [_req]
;;   {:status 200
;;    :body "Hello, Clojure API !? \n"})


;; 10_Clojureで作るAPI ルーターを追加する　その２：評価のし忘れを防ぐ

;; (defn- build-handler
;;   []
;;   (ring/ring-handler router/router))

(defn- build-handler_
  [prof]

  (ring/ring-handler
    router/router
    nil
    {:middleware
     (if (= prof :prod)
       []

       ;; 開発時に適用する Ring Middleware です。
       ;; 今回は wrap-reload だけでなく、
       ;; ring-devel の提供する他の開発用の Middleware もついでに適用しています。

       [;; Middleware ①
        ;; 指定したディレクトリ以下の変更を検知してリロードさせる
        ;; m.reload/wrap-reload
        [m.reload/wrap-reload {:dirs ["src"]
                               ;; オプションはデフォルト
                               ;; そのままをあえて可視化するために書いています
                               :reload-compile-errors? true}]

        ;; Middleware ②
        ;; リクエストマップとレスポンスマップが Ring の仕様を満たしているかをチェックする
        m.lint/wrap-lint

        ;; Middleware ③
        ;; 例外をわかりやすく表示してくれる
        [m.stacktrace/wrap-stacktrace {:color? true}]])}))


;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
;; その１：APIリクエストをロギングする
(def ^:private dev-middlewares
  "開発時だけ有効化する"
  [[m.reload/wrap-reload {:dirs ["src"]
                          :reload-compile-errors? true}]
   m.lint/wrap-lint
   [m.stacktrace/wrap-stacktrace {:color? true}]])


(defn- build-handler
  [prof]
  (let [common-middlewares [m.logger/wrap-with-logger]
        middlewares (if (= prof :prod)
                      common-middlewares
                      ;; 開発用のMiddlewareは先に適用する
                      (apply conj dev-middlewares common-middlewares))]
    (ring/ring-handler
      router/router
      nil
      {:middleware middlewares})))


(defrecord Handler
  ;; 10_Clojureで作るAPI ルーターを追加する　その２：評価のし忘れを防ぐ
  ;; [handler]

  [handler prof]

  component/Lifecycle

  (start
    [this]
    ;;  (assoc this :handler ring-handler)

    ;; 10_Clojureで作るAPI ルーターを追加する　その２：評価のし忘れを防ぐ
    ;;  (assoc this :handler (build-handler))
    (assoc this :handler (build-handler prof)))


  (stop
    [this]
    (assoc this :handler nil)))

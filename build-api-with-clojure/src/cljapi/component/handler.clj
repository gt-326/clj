(ns cljapi.component.handler
  (:require
    [com.stuartsierra.component :as component]

    [cljapi.router :as router]
    [reitit.ring :as ring]

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

(defn- build-handler
  [prof]
  (println (str "aaa: " (empty? prof)))

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
       [m.stacktrace/wrap-stacktrace {:color? true}]
       ])
    }
   ))


(defrecord Handler
  ;; 10_Clojureで作るAPI ルーターを追加する　その２：評価のし忘れを防ぐ
  ;;[handler]
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

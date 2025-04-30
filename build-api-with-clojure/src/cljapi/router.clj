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
    [ring.middleware.defaults :as m.defautls]

    ;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
    ;; その３−２：JSONの入出力に対応する
    [camel-snake-kebab.core :as csk]
    [clojure.core.memoize :as memo]
    [muuntaja.core :as muu]
    [muuntaja.middleware :as muu.middleware]))


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


(def router__
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


;; 11_Clojureで作るAPI RingMiddlewareを追加してAPIらしくする
;; その３−２：JSONの入出力に対応する
(def ^:private memoized->camelCaseString
  "実装上 kebab-case keyword でやっているものを JSON にするときに camelCase にしたい。
   バリエーションはそれほどないはずなのでキャッシュする"
  (memo/lru csk/->camelCaseString {} :lru/threshold 1024))


(def ^:private muuntaja-config
  "https://cljdoc.org/d/metosin/muuntaja/0.6.8/doc/configuration"
  (-> muu/default-options
      ;; JSON に encode する時にキーを camelCase にする
      (assoc-in [:formats "application/json" :encoder-opts]
                {:encode-key-fn memoized->camelCaseString})

      ;; JSON 以外の accept でリクエストされたときに返らないように制限する
      (update :formats #(select-keys % ["application/json"]))
      muu/create))


(def router
  (ring/router
   [["/health" {:name ::health
                :handler h/handler}]

    ;; Ring-Defaults で入る機能はAPIとしての動作にだけ働けばいい。
    ;;  /api 配下で機能するように適用する。
    ["/api" {:middleware [[m.defautls/wrap-defaults ring-defaults-config]
                          [muu.middleware/wrap-format muuntaja-config]
                          muu.middleware/wrap-params]}
     ["/hello" {:name ::hello
                :handler h/handler}]
     ["/bye" {:name ::bye
              :handler h/handler}]]]))

(ns cljapi.handler.api.account
  (:require
   [cljapi.handler :as h]
   [cljapi.router :as r]
   [ring.util.http-response :as res]

   ;; 12_Clojureで作るAPI RESTful APIを追加する
   ;; その２：バリデーション
   [cljapi.validation.account :as v.account]))


;; PUT
(defmethod h/handler [::r/account-by-id :put]
  [req]
  (res/ok {:method :put
           :path-params (:path-params req)}))


;; DELETE
(defmethod h/handler [::r/account-by-id :delete]
  [req]
  (res/ok {:method :delete
           :path-params (:path-params req)}))


;; 12_Clojureで作るAPI RESTful APIを追加する
;; その２：バリデーション

;; GET
(defmethod h/handler [::r/account :get]
  [req]
  (let [[error values] (v.account/validate-get-account req)]
    (if error
      (res/bad-request error)
      (res/ok {:validated-values values}))))


;; POST
(defmethod h/handler [::r/account :post]
  [req]
  (let [[error values] (v.account/validate-post-account req)]
    (if error
      (res/bad-request error)
      (res/ok {:validated-values values}))))

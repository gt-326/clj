(ns cljapi.handler.api.account
  (:require
   [cljapi.handler :as h]
   [cljapi.router :as r]
   [ring.util.http-response :as res]))


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

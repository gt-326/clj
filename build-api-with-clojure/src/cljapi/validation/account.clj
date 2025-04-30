(ns cljapi.validation.account
  (:require
   [cljapi.validator :as v]
   [struct.core :as st]))


(def ^:private get-account-schema
  {:search [v/string v/string-shorter-than-256]})


(def ^:private post-account-schema
  {:name [v/required v/string v/string-shorter-than-256]
   :email [v/required v/string v/email]})


(defn validate-get-account
  [{:keys [:params]}]
  (st/validate params get-account-schema {:strip true}))


(defn validate-post-account
  [{:keys [:params]}]
  (st/validate params post-account-schema {:strip true}))

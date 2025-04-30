(ns cljapi.validator
  (:require
   [clojure.string :as str]
   [struct.core :as st]))


(def required
  "structのrequiredに加えて、文字列の場合に空白のみの文字列を許容しないvalidator"
  {:message "必須入力項目です"
   :optional false
   :validate #(if (string? %)
                (boolean (seq (str/trim %)))
                (some? %))})


(def string
  [st/string
   :message "文字列である必要があります"
   :coerce str/trim])


(def string-shorter-than-256
  [st/max-count 255 :message "255文字以内で入力してください"])


(def email
  [st/email :message "メールアドレスの形式が正しくありません"])

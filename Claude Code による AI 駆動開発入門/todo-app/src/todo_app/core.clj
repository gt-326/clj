(ns todo-app.core
  (:gen-class)
  (:require
    [clojure.string :as str]
    [todo-app.cui :as cui]
    [todo-app.gui :as gui]
    [todo-app.store :as store]
    [todo-app.util :as util]))


(defn help
  []
  (println
    (str/join "\n"
              [""
               "TODO Mode - 使い方:"
               "  0:Simple CUI / 1:Repl CUI / 2:GUI"
               ""])))


(defn -main
  [mode & args]
  ;; ファイルの存在チェック（なければ生成する）
  (store/initialize-store!)
  ;; 起動時に初期化・1回だけ読み込む
  (let [data-atom (atom (store/load-todos))]
    (case (some-> mode util/parse-num)
      0 (cui/cui-simple! data-atom args)
      1 (cui/cui-repl! data-atom)
      2 (gui/run data-atom)
      (help))))

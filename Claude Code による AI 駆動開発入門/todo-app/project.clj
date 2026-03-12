(defproject todo-app "1.0.0"
  :description "A simple standalone TODO app [ CUI / GUI / REST ]"
  :url "https://example.com"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [seesaw "1.5.0"]

                 [ring/ring-core          "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]   ; 組み込み Jetty
                 [compojure               "1.7.1"]    ; ルーティング
                 [ring/ring-json          "0.5.1"]    ; JSON ミドルウェア
                 [cheshire                "5.12.0"]   ; JSON 変換
                 ]

  :source-paths ["src/clj"]
  :test-paths   ["test/clj"]

  :main todo-app.core
  :aot [todo-app.core]
  :profiles {:dev     {:dependencies [[ring/ring-mock "0.4.0"]]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :target-path "target/%s")

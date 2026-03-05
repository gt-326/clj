(defproject todo-app "1.0.0"
  :description "A simple standalone CUI TODO app"
  :url "https://example.com"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :main todo-app.core
  :aot [todo-app.core]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :target-path "target/%s")

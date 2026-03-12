(defproject todo-app "1.0.0"
  :description "A simple standalone TODO app [ CUI / GUI ]"
  :url "https://example.com"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [seesaw "1.5.0"]]
  :main todo-app.core
  :aot [todo-app.core]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :target-path "target/%s")

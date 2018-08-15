(defproject expaths "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/clojurescript "1.9.946"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [reagent "0.7.0" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server cljsjs/create-react-class]]
                 [re-frame "0.10.4"]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.14"]]
  :clean-targets ^{:protect false} ["test/target" "test/runTests.js" "target/" "index.ios.js" "index.android.js" #_($PLATFORM_CLEAN$)]
  :aliases {"prod-build"     ^{:doc "Recompile code with prod profile."}
                             ["do"
                              "clean"
                              ["with-profile" "test" "cljsbuild" "test"]
                              "clean"
                              ["with-profile" "prod" "cljsbuild" "once"]]
            "cljstest"       ^{:doc "Recompile tests"}
                             ["do"
                              "clean"
                              ["with-profile" "test" "cljsbuild" "test"]]
            "advanced-build" ^{:doc "Recompile code for production using :advanced compilation."}
                             ["do" "clean"
                              ["with-profile" "advanced" "cljsbuild" "once"]]}
  :jvm-opts ["-XX:+IgnoreUnrecognizedVMOptions" "--add-modules java.xml.bind"]
  :profiles {:test {:dependencies [[figwheel-sidecar "0.5.14"]
                                   [com.cemerick/piggieback "0.2.1"]]
                    :source-paths ["src" "test"]
                    :cljsbuild    {:builds
                                   [{:source-paths ["src" "test"],
                                     :compiler
                                                   {:output-to  "test/runTests.js",
                                                    :main       "tourme.run-tests",
                                                    :target     :nodejs,
                                                    :output-dir "target/android"
                                                    :libs       ["src/js/polyline.js"
                                                                 "src/js/position-helper.js"]},
                                     :figwheel     false,
                                     :builds       nil}]
                                   :test-commands
                                   {"node tests" ["node" "test/runTests.js" "..."]}}}

             :dev  {:dependencies [[figwheel-sidecar "0.5.14"]
                                   [com.cemerick/piggieback "0.2.1"]]
                    :source-paths ["src" "env/dev"]
                    :cljsbuild    {:builds [{:id           "ios"
                                             :source-paths ["src" "env/dev"]
                                             :figwheel     true
                                             :compiler     {:output-to     "target/ios/not-used.js"
                                                            :main          "env.ios.main"
                                                            :output-dir    "target/ios"
                                                            :optimizations :none
                                                            :libs          ["src/js/polyline.js"
                                                                            "src/js/position-helper.js"]}}
                                            {:id           "android"
                                             :source-paths ["src" "env/dev"]
                                             :figwheel     true
                                             :compiler     {:output-to     "target/android/not-used.js"
                                                            :main          "env.android.main"
                                                            :output-dir    "target/android"
                                                            :optimizations :none
                                                            :libs          ["src/js/polyline.js"
                                                                            "src/js/position-helper.js"]}}
                                            #_($DEV_PROFILES$)]}
                    :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}

             :prod {:cljsbuild {:builds [{:id           "android"
                                          :source-paths ["src" "env/prod"]
                                          :compiler     {:output-to          "index.android.js"
                                                         :main               "env.android.main"
                                                         :output-dir         "target/android"
                                                         :static-fns         true
                                                         :optimize-constants true
                                                         :optimizations      :simple
                                                         :libs               ["src/js/polyline.js"
                                                                              "src/js/position-helper.js"]
                                                         :closure-defines    {"goog.DEBUG"                 false
                                                                              "tourme.config.INTERNALTEST" true}}}
                                         #_($PROD_PROFILES$)]}}})
                                                  
                      

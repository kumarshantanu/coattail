(defproject coattail "0.0.2-SNAPSHOT"
  :description "Clojure/Script OpenAPI tooling to afford instant leverage"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                *assert* true
                *unchecked-math* :warn-on-boxed}
  :dependencies []
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :dev {:dependencies [[cheshire "5.10.0"]]}
             :cljs {:plugins   [[lein-cljsbuild "1.1.7"]
                                [lein-doo "0.1.10"]]
                    :doo       {:build "test"}
                    :cljsbuild {:builds {:test {:source-paths ["src" "test" "test-doo"]
                                                :compiler {_ ~(when-not (= "UTC" (System/getenv "TZ"))
                                                                (println "ERROR: Timezone not set to UTC. Rerun with `TZ=UTC` env var.")
                                                                (System/exit -1))
                                                           :main          coattail.runner
                                                           :output-dir    "target/out"
                                                           :output-to     "target/test/core.js"
                                                           :target        :nodejs
                                                           :optimizations :none
                                                           :source-map    true
                                                           :pretty-print  true}}}}
                    :prep-tasks [["cljsbuild" "once"]]
                    :hooks      [leiningen.cljsbuild]}
             :c09  {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :c10  {:dependencies [[org.clojure/clojure "1.10.3-rc1"]]}
             :s10  {:dependencies [[org.clojure/clojure "1.10.3-rc1"]
                                   [org.clojure/clojurescript "1.10.758"]]}}
  :aliases {"clj-test"  ["with-profile" "c09,dev:c10,dev" "test"]
            "cljs-test" ["with-profile" "cljs,s10" "doo" "node" "once"]}
  :deploy-repositories [["releases" {:url "https://clojars.org" :creds :gpg}]])

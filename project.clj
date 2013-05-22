(defproject panoptic "0.1.0-SNAPSHOT"
  :description "File/Directory Monitoring Library"
  :url "https://github.com/xsc/panoptic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [potemkin "0.2.2"]
                 [pandect "0.2.2"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [log4j "1.2.17"]]  
                   :plugins [[lein-midje "3.0.1"]]}
             :examples {:source-paths ["examples"]}}
  :aliases {"run-example" ["with-profile" "dev,examples" "run" "-m"]})

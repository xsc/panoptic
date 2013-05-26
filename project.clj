(defproject panoptic "0.1.0"
  :description "File & Directory Monitoring for Clojure."
  :url "https://github.com/xsc/panoptic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "1.6.0"]
                 [potemkin "0.2.2"]
                 [pandect "0.2.3"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.0.1"]]}
             :examples {:source-paths ["examples"]}}
  :aliases {"run-example" ["with-profile" "dev,examples" "run" "-m"]})

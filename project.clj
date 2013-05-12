(defproject panoptic "0.1.0-SNAPSHOT"
  :description "File/Directory Monitoring Library"
  :url "https://github.com/xsc/panoptic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [potemkin "0.2.2"]
                 [me.raynes/fs "1.4.0"]
                 [digest "1.4.3"]
                 [clj-time "0.5.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [log4j "1.2.17"]]  
                   :plugins [[lein-midje "3.0.1"]]}})

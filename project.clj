(defproject panoptic "0.2.1-SNAPSHOT"
  :description "File & Directory Monitoring for Clojure."
  :url "https://github.com/xsc/panoptic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "1.6.0"]
                 [potemkin "0.2.2"]
                 [pandect "0.2.3"]]
  :repositories  {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :exclusions [org.clojure/clojure]
  :profiles {:test {:dependencies [[midje "1.5.1"]]
                    :plugins [[lein-midje "3.0.1"]]}
             :1.3  {:dependencies  [[org.clojure/clojure "1.3.0"]]}
             :1.4  {:dependencies  [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies  [[org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies  [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :examples {:source-paths ["examples"]}}
  :aliases {"run-example" ["with-profile" "dev,examples" "run" "-m"]
            "midje-all" ["with-profile" "1.3,test:1.4,test:1.5,test:1.6,test" "do" "clean," "midje"]})

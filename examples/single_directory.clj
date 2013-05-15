(ns single-directory
  (:use panoptic.core))

(defn -main
  [& [path & _]]
  (if path
    (do
      (println "Watching" path "...")
      (let [w (-> (directory-watcher)
                (on-file-create #(println "   File" (:path %3) "created."))
                (on-file-delete #(println "   File" (:path %3) "deleted."))
                (on-subdirectory-create #(println "   Directory" (:path %3) "created."))
                (on-subdirectory-delete #(println "   Directory" (:path %3) "deleted."))
                (on-create #(println "   Directory" (:path %3) "created."))
                (on-delete #(println "   Directory" (:path %3) "deleted.")))]
        @(start-simple-watcher! w [path] :interval 200))) 
    (println "Usage: lein run-example single-directory <path to directory>")))

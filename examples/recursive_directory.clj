(ns recursive-directory
  (:use panoptic.core))

(defn -main
  [& [path & _]]
  (if path
    (do
      (println "Watching" path "...")
      (let [w (-> (directory-watcher :recursive true)
                (on-directory-file-create #(println "   File" (:path %3) "created."))
                (on-directory-file-delete #(println "   File" (:path %3) "deleted."))
                (on-directory-create #(println "   Directory" (:path %3) "created."))
                (on-directory-delete #(println "   Directory" (:path %3) "deleted.")))]
        @(start-simple-watcher! w [path] :interval 200))) 
    (println "Usage: lein run-example single-directory <path to directory>")))

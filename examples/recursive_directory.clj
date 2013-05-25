(ns recursive-directory
  (:use panoptic.core))

(set-log-level! :warn)

(defn -main
  [& [path & _]]
  (if path
    (do
      (println "Watching" path "...")
      (let [w (-> (directory-watcher :recursive true)
                (on-file-create #(println "   File" (:path %3) "created."))
                (on-file-delete #(println "   File" (:path %3) "deleted."))
                (on-directory-create #(println "   Directory" (:path %3) "created."))
                (on-directory-delete #(println "   Directory" (:path %3) "deleted.")))]
        @(start-multi-watcher! w [path] :interval 2000 :threads 4))) 
    (println "Usage: lein run-example single-directory <path to directory>")))

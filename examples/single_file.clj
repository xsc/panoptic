(ns single-file
  (:use panoptic.core))

(defn -main
  [& [path & _]]
  (if path
    (do
      (println "Watching" path "...")
      (let [w (-> (file-watcher :checksum :md5)
                (on-file-create #(println "  " (:path %3) "created." (str "(checksum: " (checksum %3) ")")))
                (on-file-delete #(println "  " (:path %3) "deleted."))
                (on-file-modify #(println "  " (:path %3) "modified." (str "(checksum: " (checksum %3) ")"))))]
        @(run! w [path] :interval 200))) 
    (println "Usage: lein run-example single-file <path to file>")))

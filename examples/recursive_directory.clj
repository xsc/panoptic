(ns recursive-directory
  (:use panoptic.core))

(defn -main
  [& [path & _]]
  (if path
    (do
      (println "Watching" path "...")
      (let [w (-> (simple-directory-watcher path :recursive true :interval 200)
                (on-directory-file-create #(println "   File" %3 "created."))
                (on-directory-file-delete #(println "   File" %3 "deleted."))
                (on-directory-create #(println "   Directory" %3 "created."))
                (on-directory-delete #(println "   Directory" %3 "deleted.")))]
        @(start-watcher! w))) 
    (println "Usage: lein run-example single-directory <path to directory>")))

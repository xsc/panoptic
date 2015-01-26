(ns directory-files
  (:use panoptic.core))

;; TODO: This has to be easier.

(defn -main
  [& [path & _]]
  (if path
    (let [fw  (-> (file-watcher :checksum :adler32)
                  (on-file-create #(println "  " (:path %3) "created." (str "(checksum: " (checksum %3) ")")))
                  (on-file-delete #(println "  " (:path %3) "deleted."))
                  (on-file-modify #(println "  " (:path %3) "modified." (str "(checksum: " (checksum %3) ")")))
                  (unwatch-on-delete)
                  (run! :id :files :threads 4 :distribute :frequency :interval 200))
          dw  (-> (directory-watcher :recursive true :include-hidden false)
                  (on-directory-create (fn [_1 _2 dir]
                    (doseq [child (:files (:panoptic.data.core/children dir))]
                      (watch-entity! fw (str (:path dir) "/" child) :created))))
                  (on-file-create #(watch-entity! fw (:path %3) :created))
                  (run! :id :dirs :threads 2 :distribute :fair :interval 500))]
      (watch-entity! dw path :created)
     @dw)
    (println "Usage: lein run-example directory-files <Path>")))

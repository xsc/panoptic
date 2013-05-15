(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file
  (:use panoptic.watchers.core
        [clojure.tools.logging :only [error]])
  (:require [panoptic.checkers :as c]
            [panoptic.data.file :as f]
            [panoptic.utils :as u]))

;; ## Observation Logic

(defn- update-file!
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map."
  [checker {:keys [checked path checksum] :as f}]
  (try
    (let [chk (c/file-checksum checker path)]
      (condp = [checksum chk]
        [nil nil] (f/set-file-missing f)
        [chk chk] (f/set-file-untouched f chk)
        [nil chk] (if (or (:deleted f) (:missing f)) ;; this prevents creation notifications on startup
                    (f/set-file-created f chk) 
                    (f/set-file-untouched f chk)) 
        [checksum nil] (f/set-file-deleted f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (error ex "in file checker for file" path)
      (f/set-file-untouched f checksum))))

;; ## File Watcher

(defn file-watcher
  "Create WatchFn for Files."
  ([] (file-watcher nil))
  ([& {:keys [checker]}] 
   (watch-fn
     (partial update-file! (or checker c/md5))
     (fn [m path]
       (when-let [f (f/file path)]
         (assoc m (:path f) f)))
     (fn [m path]
       (when-let [f (f/file path)]
         (dissoc m (:path f)))))))

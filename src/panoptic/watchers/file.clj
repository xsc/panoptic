(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file
  (:use panoptic.watchers.core
        [clojure.tools.logging :only [error]])
  (:require [panoptic.checkers :as c]
            [panoptic.data.file :as f]
            [panoptic.utils.core :as u]))

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

(defprotocol FileEntityHandlers
  (on-file-create [this f])
  (on-file-modify [this f])
  (on-file-delete [this f]))

(defn- on-flag [flag watch-fn f]
  (after-entity-handler
    watch-fn
    #(when (get %3 flag)
       (f %1 %2 %3))))

(defwatch FileWatcher
  FileEntityHandlers
  (on-file-create [this f] (on-flag :created this f))
  (on-file-modify [this f] (on-flag :modified this f))
  (on-file-delete [this f] (on-flag :deleted this f)))

(defn file-watcher
  "Create WatchFn for Files."
  [& {:keys [checker]}] 
  (FileWatcher.
    (partial update-file! (or checker c/crc32))
    (fn [m path]
      (when-let [f (f/file path)]
        (assoc m (:path f) f)))
    (fn [m path]
      (when-let [f (f/file path)]
        (dissoc m (:path f))))
    nil))

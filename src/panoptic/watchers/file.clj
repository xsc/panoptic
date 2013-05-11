(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file
  (:use panoptic.watchers.core)
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]))

;; ## File Handlers

(defn- on-flag-set
  "Add entity handler to watcher that fires if a given flag is set
   in the file map."
  [flag watcher f]
  (wrap-entity-handler!
    watcher
    (fn [h]
      (fn [file]
        (when h (h file)) 
        (when (get file flag)
          (f file))))))

(def on-file-create (partial on-flag-set :created))
(def on-file-delete (partial on-flag-set :deleted))
(def on-file-modify (partial on-flag-set :modified))

;; ## Observation Logic

(defn- watch-file
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map."
  [checker {:keys [checked path checksum] :as f}]
  (try
    (let [chk (c/file-checksum checker path)]
      (condp = [checksum chk]
        [nil nil] (f/set-file-missing f)
        [chk chk] (f/set-file-untouched f chk)
        [nil chk] (if (:missing f) ;; this prevents creation notifications on startup
                    (f/set-file-created f chk) 
                    (f/set-file-untouched f chk)) 
        [checksum nil] (f/set-file-deleted f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (f/set-file-untouched f checksum))))

;; ## Simple File Watcher

(defn simple-file-watcher
  "Create single-threaded file watcher."
  [files & {:keys [interval checker]}]
  (let [files (if (string? files) [files] files)]
    (-> (generic-watcher 
          (partial watch-file (or checker c/last-modified))
          f/file
          (or interval 1000))
      (watch-entities! files))))

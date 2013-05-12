(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file
  (:use panoptic.watchers.core
        [clojure.tools.logging :only [error]])
  (:require [panoptic.checkers :as c]
            [panoptic.data.file :as f]
            [panoptic.utils :as u]))

;; ## FileWatcher Protocol

(defprotocol FileWatcher
  "Protocol for File Watchers"
  (on-file-create [this f]
    "Add Handler for File Creation. Function must take three parameters:
     - the watcher that issues the event
     - the path of the file
     - the file map")
  (on-file-delete [this f]
    "Add Handler for File Deletion. Function must take three parameters:
     - the watcher that issues the event
     - the path of the file
     - the file map")
  (on-file-modify [this f]
    "Add Handler for File Modification. Function must take three parameters:
     - the watcher that issues the event
     - the path of the file
     - the file map"))

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
      (error ex "in file checker for file" path)
      (f/set-file-untouched f checksum))))

;; ## Simple File Watcher

(defn- wrap-file-handler!
  "Add entity handler to watcher that fires if a given flag is set
   in the file map."
  [watcher flag f]
  (wrap-entity-handler!
    watcher
    (fn [h]
      (fn [& [_ _ file :as args]]
        (when h (apply h args)) 
        (when (get file flag)
          (apply f args))))))

(deftype SimpleFileWatcher [internal-watcher]
  Watcher
  (wrap-entity-handler! [this f] 
    (wrap-entity-handler! internal-watcher f)
    this)
  (start-watcher! [this] 
    (start-watcher! internal-watcher)
    this)
  (stop-watcher! [this] 
    (stop-watcher! internal-watcher)
    this)
  (watch-entities! [this es] 
    (watch-entities! internal-watcher es)
    this)
  (unwatch-entities! [this es] 
    (unwatch-entities! internal-watcher es)
    this)

  FileWatcher
  (on-file-create [this f]
    (wrap-file-handler! internal-watcher :created f)
    this)
  (on-file-delete [this f]
    (wrap-file-handler! internal-watcher :deleted f)
    this)
  (on-file-modify [this f]
    (wrap-file-handler! internal-watcher :modified f)
    this)
  
  Object
  (toString [_]
    (.toString internal-watcher)))

(defn simple-file-watcher
  "Create single-threaded file watcher."
  [files & {:keys [interval checker]}]
  (let [files (if (string? files) [files] files)]
    (SimpleFileWatcher.
      (-> (simple-watcher 
            (partial watch-file (or checker c/last-modified))
            (fn [m path]
              (when-let [f (f/file path)]
                (assoc m (:path f) f)))
            (or interval 1000))
        (watch-entities! files)))))

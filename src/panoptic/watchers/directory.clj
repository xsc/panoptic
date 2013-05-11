(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]))

;; ## Protocol

(defprotocol DirectoryWatcher
  "Protocol for Directory Watchers."
  (wrap-directory-handler [this f]))

;; ## Watching Directories

(defn check-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [d0]
  (if-let [d (f/refresh-directory d0)]
    (let [new-files (s/difference (:files d) (:files d0))
          new-directories (s/difference (:directories d) (:directories d0))
          deleted-files (s/difference (:files d0) (:files d))
          deleted-directories (s/difference (:directories d0) (:directories d))]
      (prn deleted-files) 
      d)
    (f/set-directory-deleted d0)))

(defn run-directory-watcher!
  "Create Watcher that checks the directories contained in the given
   atom in a periodic fashion, using the given polling interval. Returns
   a function that can be called to shutdown the observer."
  [directory-seq-atom interval]

  )

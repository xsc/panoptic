(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]
            [panoptic.observable :as o]))

;; ## Watching Directories

(defn- check-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [{:keys [path files include-hidden extensions directories] :as dir}]
  (if-let [d (f/directory path :extensions extensions :include-hidden include-hidden)]
    nil
    (f/set-directory-deleted dir)))

(defn run-directory-watcher!
  "Create Watcher that checks the directories contained in the given
   atom in a periodic fashion, using the given polling interval. Returns
   a function that can be called to shutdown the observer."
  [directory-seq-atom interval]

  )

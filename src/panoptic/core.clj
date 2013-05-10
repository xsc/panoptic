(ns 
  panoptic.core
  (:require [panoptic.watcher :as w]
            [panoptic.file :as f]
            [panoptic.checkers :as check]))

;; ## Concept
;;
;; There are different "file watchers" that read the files they have to check
;; from an atom, writing updated values back to it. The atom has a common watcher
;; attached that will call corresponding handlers for file creation/deletion/modification.
;;
;; There are "directory watchers" that read the directories they have to check from
;; an atom, writing updated values back to it. New files will then be distributed
;; to the existing "file watchers" (whilst deleted files will automatically disappear 
;; from the system, since the file watchers will invalidate them.
;;
;; The file watchers have different polling intervals. Files that have been freshly changed are
;; polled more often than those that have not been changed for a long time. However, 
;; files untouched for a longer span of time are distributed randomly to watchers with capacity
;; to anticipate potential changes and detect them quickly.
;;
;; The initial interval given is the maximum time a file can go without being polled.

;; ## Wrappers

(defn start-watcher!
  "Start the given Watcher."
  [watcher]
  (w/start-watcher! watcher))

(defn stop-watcher!
  "Stop the given Watcher."
  [watcher]
  (w/stop-watcher! watcher))

(defn on-create
  "Add creation handler to Watcher."
  [watcher f]
  (w/on-create watcher f))

(defn on-delete
  "Add deletion handler to Watcher."
  [watcher f]
  (w/on-delete watcher f))

(defn on-modify
  "Add modification handler to Watcher."
  [watcher f]
  (w/on-modify watcher f))

;; ## Simple File Watcher

(defn simple-file-watcher
  "Create a simple, single-threaded file watcher observing the given files 
   (which may not exist yet)."
  [files & {:keys [check interval]}]
  (let [files (if (string? files) [files] files)
        file-atom (atom (map f/file files))]
    (w/file-watcher 
      file-atom 
      (or check check/by-modification-time) 
      (or interval 1000))))

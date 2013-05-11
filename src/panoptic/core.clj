(ns 
  panoptic.core
  (:use [potemkin :only [import-vars]])
  (:require [panoptic.watchers.file :as wf]
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

;; ## Import

(import-vars
  [panoptic.watchers.core
   
   start-watcher!
   stop-watcher!]

  [panoptic.watchers.file
   
   simple-file-watcher

   on-create
   on-delete
   on-modify]
  
  [panoptic.checkers
   
   last-modified
   md5
   sha1
   sha256])

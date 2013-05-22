(ns ^{:doc "Panoptic API Facade."
      :author "Yannick Scherer"}
  panoptic.core
  (:use [potemkin :only [import-vars]])
  (:require [taoensso.timbre :as timbre :only [set-level]]
            [panoptic.watchers core file-watcher directory-watcher]
            [panoptic.runners simple multi-threaded]
            [panoptic.data file directory]))

;; ## Import

(import-vars
  [panoptic.runners.core
   
   watch-entities!
   watch-entity!
   unwatch-entity!
   unwatch-entities!
   start-watcher!
   stop-watcher!]

  [panoptic.runners.simple
   
   simple-watcher
   start-simple-watcher!]

  [panoptic.runners.multi-threaded
   
   multi-watcher
   start-multi-watcher!]

  [panoptic.watchers.core
   
   wrap-entity-handler
   after-entity-handler
   before-entity-handler
   wrap-watch-fn]

  [panoptic.watchers.file-watcher
   
   file-watcher
   
   on-file-create
   on-file-modify
   on-file-delete]
  
  [panoptic.watchers.directory-watcher
   
   directory-watcher

   on-directory-create
   on-directory-delete])

;; ## Logging Access

(timbre/set-level! :warn)

(defn set-log-level!
  "Set Panoptic/Timbre Log Level."
  [l]
  (timbre/set-level! l))

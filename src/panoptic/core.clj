(ns ^{:doc "Panoptic API Facade."
      :author "Yannick Scherer"}
  panoptic.core
  (:use [potemkin :only [import-vars]])
  (:require [taoensso.timbre :as timbre :only [set-level warn]]
            [panoptic.watchers core file-watcher directory-watcher]
            [panoptic.runners simple-runner multi-runner]
            [panoptic.data.core :as data]))

;; ## Import

(import-vars
  [panoptic.watchers.core
   
   watch-entities!
   watch-entity!
   unwatch-entity!
   unwatch-entities!
   start-watcher!
   stop-watcher! 
   wrap-entity-handler
   after-entity-handler
   before-entity-handler
   wrap-watch-fn
   
   on-entity-matches
   on-entity-create
   on-entity-modify
   on-entity-delete
   on-child-create
   on-child-delete
   unwatch-on-delete]

  [panoptic.watchers.file-watcher
   
   file-watcher
   
   on-file-create
   on-file-modify
   on-file-delete]
  
  [panoptic.watchers.directory-watcher
   
   directory-watcher

   on-directory-create
   on-directory-delete]
  
  [panoptic.runners.simple-runner
   
   simple-runner]

  [panoptic.runners.multi-runner
   
   multi-runner]
  
  [panoptic.data.core
   
   last-changed
   timestamp
   checksum])

;; ## Run Wrapper

(defn- run!*
  [watch-fn initial-entities & {:keys [id interval threads distribute]}]
  (let [interval (or interval 1000)
        threads (or threads 1)
        id (keyword (or id (panoptic.runners.core/generate-watcher-id)))]
    (when (and (= threads 1) distribute)
      (timbre/warn "[panoptic] distribution strategy given but only one thread specfied!"))
    (->
      (if (= threads 1)
        (simple-runner watch-fn id interval)
        (multi-runner watch-fn id distribute threads interval))
      (watch-entities! initial-entities)
      (start-watcher!))))

(defn run!
  "Run the given Watcher. An optional vector of initial entities to watch can be
   specified, followed by the following options:
  
   - `:interval`: the base interval to use for the polling threads,
   - `:threads`: the number of watcher threads to create,
   - `:distribute`: the distribution strategy to use with multiple threads.

  Examples:

     (run! watcher)
     (run! watcher [\"file.txt\"])
     (run! watcher :interval 200)
     (run! watcher [\"file.txt\"] :threads 4)

  This returns a watch runner stoppable by `stop!` or `stop-watcher!`.
  "
  [watch-fn & args]
  (if (or (not (seq args)) (keyword? (first args)))
    (apply run!* watch-fn nil args)
    (apply run!* watch-fn args)))

(defn run-blocking!
  "Run the given watcher and block indefinitely."
  [watch-fn & args]
  (when-let [fut (apply run! watch-fn args)]
    @fut))

(defn stop!
  "Stop the given watcher!"
  [w]
  (stop-watcher! w))

;; ## Logging Access

(timbre/set-level! :warn)

(defn set-log-level!
  "Set Panoptic/Timbre Log Level."
  [l]
  (timbre/set-level! l))

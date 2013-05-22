(ns ^{:doc "Simple, single-threaded Watch Runners."
      :author "Yannick Scherer"}
  panoptic.runners.simple
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core
        panoptic.runners.core)
  (:require [panoptic.utils.core :as u])) 

;; ## Watcher Type

(deftype SimpleWatcher [watch-fn interval entities thread-data]
  WatchRunner
  (watch-entities! [this es]
    (swap! entities #(add-entities watch-fn % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities #(remove-entities watch-fn % es))
    this)
  (watched-entities [this]
    @entities)
  (start-watcher! [this]
    (swap! thread-data #(or % (run-watcher-thread! this watch-fn interval entities)))
    this)
  (stop-watcher! [this]
    (when-let [[ft f] @thread-data]
      (reset! thread-data nil)
      (f)
      ft))

  clojure.lang.IDeref
  (deref [_]
    (let [[ft _] @thread-data]
      @ft))
  
  Object
  (toString [this]
    (pr-str @entities)))

(defn simple-watcher
  "Create and generic, single-threaded Watcher."
  [watch-fn interval] 
  (SimpleWatcher. watch-fn (or interval 1000) (atom {}) (atom nil)))

(defn start-simple-watcher!*
  "Create and start generic, single-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn initial-entities & {:keys [interval]}]
  (->
    (simple-watcher watch-fn interval)
    (watch-entities! initial-entities)
    (start-watcher!)))

(defn start-simple-watcher!
  "Create and start generic, single-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn & args]
  (if (or (not (seq args)) (keyword? (first args))) 
    (apply start-simple-watcher!* watch-fn nil args)
    (apply start-simple-watcher!* watch-fn args)))

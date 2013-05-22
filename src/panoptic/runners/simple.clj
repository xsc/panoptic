(ns ^{:doc "Simple, single-threaded Watch Runners."
      :author "Yannick Scherer"}
  panoptic.runners.simple
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core
        panoptic.runners.core)
  (:require [panoptic.utils.core :as u])) 

;; ## Watcher Type

(deftype SimpleWatcher [id watch-fn interval entities thread-data]
  WatchRunner
  (watch-entities! [this es]
    (dosync (alter entities #(add-entities watch-fn % es))) 
    this)
  (unwatch-entities! [this es]
    (dosync (alter entities #(remove-entities watch-fn % es))) 
    this)
  (watched-entities [this]
    @entities)
  (start-watcher! [this]
    (swap! thread-data #(or % (run-watcher-thread! id this watch-fn interval entities)))
    this)
  (stop-watcher! [this]
    (when-let [[ft f] @thread-data]
      (reset! thread-data nil)
      (f)
      ft))

  clojure.lang.IDeref
  (deref [_]
    (let [[ft _] @thread-data]
      (when ft @ft)))
  
  Object
  (toString [this]
    (pr-str @entities)))

(defmethod print-method SimpleWatcher
  [o w]
  (print-simple 
    (str "#<SimpleWatcher: " (.toString o) ">")
    w))

(defn simple-watcher
  "Create a generic, single-threaded Watcher."
  [watch-fn interval] 
  (SimpleWatcher. 
    (keyword (gensym "watcher-")) 
    watch-fn 
    (or interval 1000) 
    (ref {}) 
    (atom nil)))

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

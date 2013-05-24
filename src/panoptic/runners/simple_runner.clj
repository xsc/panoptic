(ns ^{:doc "Simple, single-threaded Watch Runners."
      :author "Yannick Scherer"}
  panoptic.runners.simple-runner
  (:use [taoensso.timbre :only [debug info warn error]]
        panoptic.watchers.core
        panoptic.runners.core)
  (:require [panoptic.utils.core :as u])) 

;; ## Simple Runner
;;
;; The simple runner has its entities in a single atom. It runs a
;; standlone watcher as defined in `panoptic.runners.core`.

(deftype SimpleRunner [id watch-fn interval entities-atom stop-atom threads-atom]
  WatchRunner
  (watch-entities! [this es]
    (swap! entities-atom #(add-entities watch-fn % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities-atom #(remove-entities watch-fn % es))
    this) 
  (watched-entities [this]
    (read-watched-entities entities-atom))
  (start-watcher! [this]
    (swap! threads-atom #(or % (run-standalone-watcher! id this watch-fn nil interval entities-atom stop-atom)))
    (reset! stop-atom false)
    this)
  (stop-watcher! [this]
    (reset! stop-atom true)
    (let [ts (when-let [threads @threads-atom]
               (reset! threads-atom nil)
               threads)]
      (future (doseq [t ts] @t))))
  
  clojure.lang.IDeref
  (deref [_]
    (doseq [t @threads-atom] @t)))

(defmethod print-method SimpleRunner
  [o w]
  (print-simple 
    (str "#<SimpleRunner: " (watched-entities o) ">")
    w))

;; ## Create Functions

(defn simple-runner
  "Create a generic, single-threaded WatchRunner."
  [watch-fn interval] 
  (SimpleRunner. (generate-watcher-id) watch-fn (or interval 1000) (atom {}) (atom nil) (atom nil)))

(defn start-simple-watcher!*
  "Create and start generic, single-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn initial-entities & {:keys [interval]}]
  (->
    (simple-runner watch-fn interval)
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

(ns ^{:doc "Simple, single-threaded Watchers."
      :author "Yannick Scherer"}
  panoptic.watchers.simple-watchers
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u])) 

;; ## Watcher Loop

(defn- run-entity-watcher!
  "Run the given watcher function over the given entity map, using the given handler
   function."
  [w watch-fn entities]
  (try 
    (->> entities
      (keep 
        (fn [[k x]]
          (when x
            (when-let [f (update-entity! watch-fn w k x)]
              [k f]))))
      (into {}))
    (catch Exception ex
      (error ex "in `run-entity-watcher!'"))))

(defn- run-watcher!
  "Run Watcher Loop"
  [w watch-fn interval entities]
  (let [stop? (atom false)
        watch-future (future
                       (loop []
                         (when-not @stop?
                           (when-let [es (swap! entities #(run-entity-watcher! w watch-fn %))] 
                             (doseq [[k x] es]
                               (run-entity-handler! watch-fn w k x)) 
                             (u/sleep interval) 
                             (recur)))))]
    (vector watch-future #(reset! stop? true)))) 

;; ## Watcher Type

(deftype SimpleWatcher [watch-fn interval entities thread-data]
  Watcher
  (watch-entities! [this es]
    (swap! entities #(add-entities watch-fn % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities #(remove-entities watch-fn % es))
    this)
  (watched-entities [this]
    @entities)
  (start-watcher! [this]
    (swap! thread-data #(or % (run-watcher! this watch-fn interval entities)))
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

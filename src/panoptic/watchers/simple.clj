(ns ^{:doc "Simple, single-threaded Watchers."
      :author "Yannick Scherer"}
  panoptic.watchers.simple
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core)
  (:require [panoptic.utils :as u])) 

;; ## Watcher Loop

(defn- run-entity-watcher!
  "Run the given watcher function over the given entity map, using the given handler
   function."
  [w watch-fn entities]
  (->> entities
    (keep 
      (fn [[k x]]
        (when x
          (when-let [f (update-entity! watch-fn w k x)]
            (run-entity-handler! watch-fn w k x)
            [k f]))))
    (into {})))

(defn- run-watcher!
  "Run Watcher Loop"
  [w watch-fn interval entities]
  (let [stop? (atom false)
        watch-future (future
                       (loop []
                         (when-not @stop?
                           (swap! entities #(run-entity-watcher! w watch-fn %))
                           (u/sleep interval)
                           (recur))))]
    (vector watch-future #(reset! stop? true)))) 

;; ## Watcher Type

(deftype SimpleWatcher [watch-fn interval entities thread-data]
  PWatchFn
  (wrap-entity-handler [this f]
    (when @thread-data
      (throw (Exception. "Cannot wrap Entity Handler if Watcher is running.")))
    (SimpleWatcher. 
      (wrap-entity-handler watch-fn f)
      interval 
      (atom @entities) 
      (atom nil)))
  (wrap-watch-fn [this f]
    (when @thread-data
      (throw (Exception. "Cannot wrap watch function if Watcher is running.")))
    (SimpleWatcher. 
      (wrap-watch-fn watch-fn f)
      interval
      (atom @entities)
      (atom nil)))

  Watcher
  (watch-entities! [this es]
    (swap! entities #(reduce 
                       (fn [m e] 
                         (if-let [f (:add-fn watch-fn assoc)]
                           (or (f m e) m)
                           m))
                       % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities #(reduce 
                       (fn [m e] 
                         (if-let [f (:remove-fn watch-fn assoc)]
                           (or (f m e) m)
                           m))
                       % es))
    this)
  (watched-entities [this]
    @entities)
  (start-watcher! [this]
    (when-let [[ft _] (swap! thread-data #(or % (run-watcher! this watch-fn interval entities)))]
      ft))
  (stop-watcher! [this]
    (when-let [[ft f] @thread-data]
      (reset! thread-data nil)
      (f)
      ft))
  
  Object
  (toString [this]
    (pr-str @entities)))

(defn simple-watcher
  "Create generic, single-threaded Watcher using: 
   - a WatchFn instance
   - the watch loop interval in milliseconds."
  [watch-fn interval]
  (SimpleWatcher. watch-fn (or interval 1000) (atom {}) (atom nil)))

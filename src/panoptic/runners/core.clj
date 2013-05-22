(ns ^{:doc "Common Runner Logic"
      :author "Yannick Scherer"}
  panoptic.runners.core
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]))

;; ## Run Loop

(defn- run-entity-watcher!
  "Run the given watcher function over the given entity map, using the given handler
   function."
  [tag w watch-fn entities]
  (try 
    (->> entities
      (keep 
        (fn [[k x]]
          (when x
            (debug tag "* Updating Entity:" k)
            (when-let [f (update-entity! watch-fn w k x)]
              [k f]))))
      (into {}))
    (catch Exception ex
      (error ex "in `run-entity-watcher!'"))))

(defn run-watcher-thread!
  "Run Watcher Loop"
  [id w watch-fn interval entities]
  (let [tag (str "[" id "]")]
    (info tag "Starting Watcher ...")
    (let [stop? (atom false)
          watch-future (future
                         (info tag "Watcher started.")
                         (loop []
                           (when-not @stop?
                             (debug tag "Running Entity Updaters ...")
                             (when-let [es (swap! entities #(run-entity-watcher! tag w watch-fn %))] 
                               (debug tag "Running Entity Handlers ...")
                               (doseq [[k x] es]
                                 (debug tag "* Running Entity Handler on:" k)
                                 (run-entity-handler! watch-fn w k x)) 
                               (debug tag "Sleeping" interval "milliseconds ...")
                               (u/sleep interval) 
                               (recur))))
                         (info tag "Watcher stopped."))]
      (vector watch-future 
              (fn []
                (info tag "Stopping Watcher ...")
                (reset! stop? true)))))) 

;; ## WatchRunner Protocol

(defprotocol WatchRunner
  "Protocol for Watchers. Watchers should also implement clojure.lang.IDeref"
  (watch-entities! [this es]
    "Add Entities to Watch List.")
  (unwatch-entities! [this es]
    "Remove Entities from Watch List.")
  (watched-entities [this]
    "Get current entity map.")
  (start-watcher! [this]
    "Start Watcher Loop.")
  (stop-watcher! [this]
    "Stop Watcher Loop. Returns a future that can be used to wait for
     shutdown completion."))

(defn watch-entity!
  "Add single Entity to Watch List."
  [w e]
  (watch-entities! w [e]))

(defn unwatch-entity!
  "Remove single Entity from Watch List."
  [w e]
  (unwatch-entities! w [e]))

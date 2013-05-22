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

(defn run-watcher-thread!
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

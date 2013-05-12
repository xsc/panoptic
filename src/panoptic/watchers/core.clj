(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:use [clojure.tools.logging :only [debug info warn error]])
  (:require [panoptic.utils :as u]))

;; ## Premises
;;
;; A watcher shall be able to handle a new set of entities on demand. It
;; should be able to attach entity watchers to it dynamically, e.g. when
;; it is already running. 

(defprotocol Watcher
  "Protocol for Watchers."
  (wrap-entity-handler! [this f]
    "Wrap Entity Handler Function using the given one.")
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

;; ## Watcher Logic

(defn- run-entity-watcher!
  "Run the given watcher function over the given entity map, using the given handler
   function."
  [w watch-fn entities handler]
  (->> entities
    (keep 
      (fn [[k x]]
        (when x
          (when-let [f (try
                         (watch-fn x)
                         (catch Exception ex
                           (error ex "when calling watch function for entity" k)))]
            (when handler 
              (future 
                (try
                  (handler w k f)
                  (catch Exception ex 
                    (error ex "when calling handler for entity" k)))))
            [k f]))))
    (into {})))

(defn- run-watcher!
  "Run Watcher Loop"
  [w watch-fn interval entities handler]
  (let [stop? (atom false)
        watch-future (future
                       (loop []
                         (when-not @stop?
                           (swap! entities #(run-entity-watcher! w watch-fn % @handler))
                           (u/sleep interval)
                           (recur))))]
    (fn []
      (reset! stop? true)
      watch-future)))

(deftype SimpleWatcher [watch-fn add-entity-fn interval entities handler stop-fn]
  Watcher
  (wrap-entity-handler! [this f]
    (when f (swap! handler f))
    this)
  (watch-entities! [this es]
    (swap! entities #(reduce (fn [m e] (or (add-entity-fn m e) m)) % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities #(reduce dissoc % es))
    this)
  (watched-entities [this]
    @entities)
  (start-watcher! [this]
    (swap! stop-fn #(or % (run-watcher! this watch-fn interval entities handler)))
    this)
  (stop-watcher! [this]
    (when-let [f @stop-fn]
      (reset! stop-fn nil)
      (f)))
  
  Object
  (toString [this]
    (pr-str @entities)))

(defn simple-watcher
  "Create generic, single-threaded Watcher using: 
   - a watch function (transform an input entity to create the new state of the entity)
   - an entity-add function (gets a map and an entity and updates the entity map)
   - the watch loop interval in milliseconds."
  [watch-fn add-entity-fn interval]
  (SimpleWatcher. watch-fn add-entity-fn interval (atom {}) (atom nil) (atom nil)))

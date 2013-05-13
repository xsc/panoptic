(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:use [clojure.tools.logging :only [debug info warn error]])
  (:require [panoptic.utils :as u]))

;; ## Protocol for WatchFn

(defprotocol PWatchFn
  (wrap-entity-handler [this f]
    "Wrap a WatchFn's entity handler function using the given one.")
  (wrap-watch-fn [this f]
    "Wrap a WatchFn's update function using the given one."))

;; ## Watch Logic
;;
;; The watcher logic consists of a map of:
;; - `:update-fn`: a function that takes an entity, performs checks, updates, ...
;;   and returns the updated entity
;; - `:add-fn`: a function that takes a map and an entity description (e.g. a
;;   file path) and returns a new map with the additional entities.
;; - `:remove-fn`: a function that takes a map and an entity description and returns
;;   a new map without the desired entities.
;; - `:handle-fn`: a function that takes an entity and performs any tasks changes to
;;   the entity require
;;

(defrecord WatchFn [update-fn add-fn remove-fn handle-fn]
  PWatchFn
  (wrap-entity-handler [{:keys [handle-fn] :as w} f]
    (when-not (fn? f)
      (throw (Exception. "expects a function as second parameter.")))
    (assoc w :handle-fn (f handle-fn)))
  (wrap-watch-fn [{:keys [update-fn] :as w} f]
    (when-not (fn? f)
      (throw (Exception. "expects a function as second parameter.")))
    (assoc w :update-fn (f update-fn))))

(defn watch-fn
  "Create new WatchFn."
  ([update-fn] (watch-fn update-fn nil nil))
  ([update-fn add-fn remove-fn] 
   (WatchFn. update-fn (or add-fn #(assoc %1 %2 %2)) (or remove-fn dissoc) (constantly nil))))

(defn run-entity-handler!
  "Run a WatchFn's entity handler on a given entity."
  [^WatchFn {:keys [handle-fn]} watcher entity-key entity]
  (when (and entity-key entity handle-fn) 
    (try
      (handle-fn watcher entity-key entity)
      (catch Exception ex
        (error ex "in entity handler for:" entity-key)))
    nil))

(defn update-entity!
  "Run a WatchFn's entity update function on a given entity."
  [^WatchFn {:keys [update-fn]} watcher entity-key entity]
  (when (and update-fn entity)
    (try 
      (update-fn entity)
      (catch Exception ex
        (error ex "in update function for: " entity-key)))))

(defn add-entities
  "Add entities to a map using the given WatchFn's add function."
  [^WatchFn {:keys [add-fn]} m es]
  (let [f (or add-fn #(assoc %1 %2 %2))]
    (reduce
      (fn [m e]
        (or (f m e) m)) 
      m es)))

(defn remove-entities
  "Remove entities from a map using the given WatchFn's remove function."
  [^WatchFn {:keys [remove-fn]} m es]
  (let [f (or remove-fn dissoc)]
    (reduce
      (fn [m e]
        (or (f m e) m)) 
      m es)))

;; ## Watcher Protocol

(defprotocol Watcher
  "Protocol for Watchers."
  (watch-entities! [this es]
    "Add Entities to Watch List.")
  (unwatch-entities! [this es]
    "Remove Entities from Watch List.")
  (watched-entities [this]
    "Get current entity map.")
  (start-watcher! [this]
    "Start Watcher Loop. Should return a future containing the watcher
     thread.")
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

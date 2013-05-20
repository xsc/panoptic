(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:use [clojure.tools.logging :only [debug info warn error]])
  (:require [panoptic.utils.core :as u]))

;; ## Protocol for WatchFn

(defprotocol WatchFunction
  (update-function [this]
    "Get WatchFn's update function.")
  (entity-handler [this]
    "Get WatchFn's entity handler")
  (add-entities [this m es] 
    "Add entities to a map using the given WatchFn's add function.")
  (remove-entities [this m es]
    "Add entities to a map using the given WatchFn's add function.") 
  (wrap-entity-handler [this f]
    "Wrap a WatchFn's entity handler function using the given one.")
  (wrap-watch-fn [this f]
    "Wrap a WatchFn's update function using the given one."))

(defn before-entity-handler
  "Run function before the given WatchFn's entity handler."
  [watch-fn f]
  (wrap-entity-handler
    watch-fn
    (fn [h]
      (fn [& args]
        (apply f args)
        (when h (apply h args))))))

(defn after-entity-handler
  "Run function after the given WatchFn's entity handler."
  [watch-fn f]
  (wrap-entity-handler
    watch-fn
    (fn [h]
      (fn [& args]
        (when h (apply h args))
        (apply f args)))))

(defn run-entity-handler!
  "Run a WatchFn's entity handler on a given entity."
  [watch-fn watcher entity-key entity]
  (let [handle-fn (entity-handler watch-fn)]
    (when (and entity-key entity handle-fn) 
      (try
        (handle-fn watcher entity-key entity)
        (catch Exception ex
          (error ex "in entity handler for:" entity-key)))
      nil)))

(defn update-entity!
  "Run a WatchFn's entity update function on a given entity."
  [watch-fn watcher entity-key entity]
  (let [update-fn (update-function watch-fn)]
    (when (and update-fn entity)
      (try 
        (update-fn entity)
        (catch Exception ex
          (error ex "in update function for: " entity-key))))))

;; ## Watch Logic
;;
;; The watcher logic is a type with the following keys/fields: 
;; - `update-fn`: a function that takes an entity, performs checks, updates, ...
;;   and returns the updated entity
;; - `add-fn`: a function that takes a map and an entity description (e.g. a
;;   file path) and returns a new map with the additional entities.
;; - `remove-fn`: a function that takes a map and an entity description and returns
;;   a new map without the desired entities.
;; - `handle-fn`: a function that takes a watcher, an entity key and the entity and 
;;   performs any tasks changes to the entity require

(defmacro defwatch
  [id & entity-handlers]
  `(deftype ~id [update-fn# add-fn# remove-fn# handle-fn#]
     WatchFunction
     (update-function [this#] update-fn#)
     (entity-handler [this#] handle-fn#)
     (add-entities [this# m# es#]
       (let [f# (or add-fn# #(assoc %1 %2 %2))]
         (reduce
           (fn [m# e#]
             (or (f# m# e#) m#)) 
           m# es#)))
     (remove-entities [this# m# es#]
       (let [f# (or remove-fn# dissoc)]
         (reduce
           (fn [m# e#]
             (or (f# m# e#) m#)) 
           m# es#)))
     (wrap-entity-handler [this# f#]
       (when-not (fn? f#)
         (throw (Exception. "expects a function as second parameter.")))
       (new ~id update-fn# add-fn# remove-fn# (f# handle-fn#)))
     (wrap-watch-fn [this# f#]
       (when-not (fn? f#)
         (throw (Exception. "expects a function as second parameter.")))
       (new ~id (f# update-fn#) add-fn# remove-fn# handle-fn#))
     
     ~@entity-handlers))

;; ## Generic WatchFn

(defwatch WatchFn)

(defn watch-fn
  "Create new WatchFn."
  ([update-fn] (watch-fn update-fn nil nil))
  ([update-fn add-fn remove-fn] 
   (WatchFn. update-fn (or add-fn #(assoc %1 %2 %2)) (or remove-fn dissoc) (constantly nil))))

;; ## Watcher Protocol

(defprotocol Watcher
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

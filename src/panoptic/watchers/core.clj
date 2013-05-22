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

(defn add-entities*
  [f m es]
  (let [f (or f #(assoc %1 %2 %2))]
    (reduce
      (fn [m e]
        (or (f m e) m))
      m es)))

(defn remove-entities*
  [f m es]
  (let [f (or f dissoc)]
    (reduce
      (fn [m e]
        (or (f m e) m))
      m es)))

(defmacro defwatch
  [id & entity-handlers]
  (let [T (gensym "T")]
    `(do 
       (deftype ~T [update-fn# add-fn# remove-fn# handle-fn#]
         WatchFunction
         (update-function [this#] update-fn#)
         (entity-handler [this#] handle-fn#)
         (add-entities [this# m# es#] (add-entities* add-fn# m# es#))
         (remove-entities [this# m# es#] (remove-entities* remove-fn# m# es#))
         (wrap-entity-handler [this# f#]
           (when-not (fn? f#)
             (throw (Exception. "expects a function as second parameter.")))
           (new ~T update-fn# add-fn# remove-fn# (f# handle-fn#)))
         (wrap-watch-fn [this# f#]
           (when-not (fn? f#)
             (throw (Exception. "expects a function as second parameter.")))
           (new ~T (f# update-fn#) add-fn# remove-fn# handle-fn#))
         ~@entity-handlers)
       (defn ~id
         ([u#] (~id u# nil nil))
         ([u# a# r#] (new ~T u# (or a# #(assoc %1 %2 %2)) (or r# dissoc) nil))))))

;; ## Generic Watching Logic

(defwatch watch-fn)

(defn on-flag-set
  "Run function if an entity map's field is set to true."
  [watch-fn flag f]
  (after-entity-handler
    watch-fn
    #(when (get %3 flag)
       (f %1 %2 %3))))

(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:use [taoensso.timbre :only [debug info warn error]])
  (:require [panoptic.utils.core :as u]
            [panoptic.data.core :as data]))

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

;; ## Protocol for WatchFn

(defprotocol WatchFunction
  (update-function [this]
    "Get WatchFn's update function.")
  (entity-handler [this]
    "Get WatchFn's entity handler")
  (create-entity-keys [this e] 
    "Create keys to be added to the entity map bsaed on a root entity.")
  (create-entity-value [this k e]
    "Create initial value for entity.")
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

(defn add-entities
  [watch-fn m es]
  (let [create-keys (partial create-entity-keys watch-fn)
        create-value (partial create-entity-value watch-fn)]
    (reduce
      (fn [m e]
        (let [ks (create-keys e)]
          (reduce
            (fn [m k]
              (if (contains? m k) m (assoc m k (ref (create-value k e)))))
            m ks)))
      m es)))

(defn remove-entities
  [watch-fn m es]
  (let [create-keys (partial create-entity-keys watch-fn)]
    (reduce
      (fn [m e]
        (let [ks (create-keys e)]
          (reduce dissoc m ks)))
      m es)))

(defn read-watched-entities
  [entities-ref]
  (->> @entities-ref
    (map (fn [[k v]] [k @v]))
    (into {})))

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
  (let [T (gensym "T")]
    `(do 
       (deftype ~T [update-fn# key-fn# value-fn# handle-fn#]
         WatchFunction
         (update-function [this#] update-fn#)
         (entity-handler [this#] handle-fn#)
         (create-entity-keys [this# e#] (key-fn# e#))
         (create-entity-value [this# k# e#] (value-fn# k# e#))
         (wrap-entity-handler [this# f#] (new ~T update-fn# key-fn# value-fn# (f# handle-fn#)))
         (wrap-watch-fn [this# f#] (new ~T (f# update-fn#) key-fn# value-fn# handle-fn#))
         ~@entity-handlers)
       (defn ~id
         ([u#] (~id u# nil nil))
         ([u# a# r#] (new ~T u# (or a# vector) (or r# #(identity %2)) nil))))))

(defwatch watch-fn)

;; ## Generic Handlers

(defn on-entity-matches
  "Handler that runs if an entity matches the given predicate."
  [watch-fn p? f]
  (after-entity-handler 
    watch-fn
    #(when (p? %3)
       (f %1 %2 %3))))

(def on-entity-create 
  "Handler that runs if an entity has the created-flag set."
  #(on-entity-matches %1 data/created? %2))

(def on-entity-modify
  "Handler that runs if an entity has the modified-flag set."
  #(on-entity-matches %1 data/modified? %2))

(def on-entity-delete 
  "Handler that runs if an entity has the deleted-flag set."
  #(on-entity-matches %1 data/deleted? %2))

(defn on-child-create
  "Handler that runs if a given entity contains a children diff map
   which in turn contains a field `:created` under the given key. "
  [watch-fn child-key f]
  (after-entity-handler
    watch-fn
    (fn [w k entity]
      (when-let [diff (data/children-diff entity)]
        (when-let [created (get-in diff [child-key :created])]
          (doseq [e created]
            (f w entity e)))))))

(defn on-child-delete
  "Handler that runs if a given entity contains a children diff map
   which in turn contains a field `:deleted` under the given key. "
  [watch-fn child-key f]
  (after-entity-handler
    watch-fn
    (fn [w k entity]
      (when-let [diff (data/children-diff entity)]
        (when-let [deleted (get-in diff [child-key :deleted])]
          (doseq [e deleted]
            (f w entity e)))))))

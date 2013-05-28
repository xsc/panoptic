(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:use [taoensso.timbre :only [debug info warn error]]
        [potemkin :only [definterface+ deftype+]])
  (:require [panoptic.utils.core :as u]
            [panoptic.data.core :as data]))

;; ## WatchRunner Protocol

(definterface+ WatchRunner
;;"Protocol for Watchers. Watchers should also implement clojure.lang.IDeref"
  (watch-entities!* [this es metadata]
    "Add Entities to Watch List.")
  (unwatch-entities!* [this es metadata]
    "Remove Entities from Watch List.")
  (watched-entities [this]
    "Get current entity map.")
  (start-watcher! [this]
    "Start Watcher Loop.")
  (stop-watcher! [this]
    "Stop Watcher Loop. Returns a future that can be used to wait for
     shutdown completion."))

(defn watch-entities!
  ([w es] (watch-entities!* w es nil)) 
  ([w es m] (watch-entities!* w es m)))

(defn unwatch-entities!
  ([w es] (unwatch-entities!* w es nil)) 
  ([w es m] (unwatch-entities!* w es m)))

(defn watch-entity!
  "Add single Entity to Watch List."
  ([w e m] (watch-entities!* w [e] m))
  ([w e] (watch-entity! w e nil)))

(defn unwatch-entity!
  "Remove single Entity from Watch List."
  ([w e m] (unwatch-entities!* w [e] m))
  ([w e] (unwatch-entity! w e nil)))

(defmacro defrunner 
  "Create new Runner type. Expects the implementation of the
   WatchRunner interface first and any other interface/protocol
   implementations after that. Will create a type with the given
   ID and implement `print-method` to print the watched entities."
  [id fields & impl]
  `(do
     (deftype+ ~id [~@fields]
       WatchRunner
       ~@impl)
     (defmethod print-method ~id
       [o# w#]
       (print-simple
         (str 
           ~(str "#<" (name id) " ")
           (pr-str (watched-entities o#))
           ">")
         w#))
     ~id))

;; ## Protocol for WatchFn

(definterface+ WatchFunction
  (update-function [this]
    "Get WatchFn's update function.")
  (entity-handler [this]
    "Get WatchFn's entity handler")
  (create-entity-keys [this e metadata] 
    "Create keys to be added to the entity map based on a root entity.
     Result elements may be a single values or vectors of [<key> <metadata>].")
  (create-entity-value [this k e metadata]
    "Create initial value for entity.")
  (wrap-entity-handler [this f]
    "Wrap a WatchFn's entity handler function using the given one. The handler function
     shall take the Watcher handling an entity, the entity key and the actual entity map. ")
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
  [watch-fn m es metadata]
  (let [create-keys #(create-entity-keys watch-fn % metadata)
        create-value (partial create-entity-value watch-fn)]
    (reduce
      (fn [m e]
        (let [ks (create-keys e)]
          (reduce
            (fn [m k]
              (let [[k metadata] (if (vector? k) k [k nil])]
                (if (contains? m k) m (assoc m k (ref (create-value k e metadata))))))
            m ks)))
      m es)))

(defn remove-entities
  [watch-fn m es metadata]
  (let [create-keys #(create-entity-keys watch-fn % metadata)]
    (reduce
      (fn [m e]
        (let [ks (->> (create-keys e) (map (fn [x] (if (vector? x) (first x) x))))]
          (reduce dissoc m ks)))
      m es)))

(defn read-watched-entities
  [entities-ref]
  (->> @entities-ref
    (map (fn [[k v]] [k @v]))
    (into {})))

;; ## Watch Logic

(defmacro defwatcher
  "Create new Watcher type and constructor function."
  [id docstring params & args]
  (let [T (gensym "T")
        [data entity-handlers] (let [p (partition 2 2 nil args)
                                     [d e] (split-with (comp keyword? first) p)]
                                 (vector
                                   (into {} (map vec d))
                                   (apply concat e)))
        let-bindings (:let data)
        update-fn (or (:update data) `identity)
        key-fn (cond (:keys data) (:keys data)
                     (:key data) `(let [f# ~(:key data)] (fn [k# m#] (vector (f# k# m#))))
                     :else `(fn [k# _#] k#))
        value-fn (or (:values data) `(fn [k# & _#] k#))
        init-fn (or (:init data) `identity)]
    `(do 
       (deftype+ ~T [update-fn# key-fn# value-fn# handle-fn# params#]
         WatchFunction
         (update-function [this#] update-fn#)
         (entity-handler [this#] handle-fn#)
         (create-entity-keys [this# e# m#] (key-fn# e# m#))
         (create-entity-value [this# k# e# m#] (value-fn# k# e# m#))
         (wrap-entity-handler [this# f#] (new ~T update-fn# key-fn# value-fn# (f# handle-fn#) params#))
         (wrap-watch-fn [this# f#] (new ~T (f# update-fn#) key-fn# value-fn# handle-fn# params#))
         ~@entity-handlers)
       (defn ~id
         ~docstring
         [& args#]
         (let [[~@params] args#
               ~@let-bindings
               init# ~init-fn]
           (init#
             (new ~T 
                  (or ~update-fn identity) 
                  (or ~key-fn (fn [k# & _#] [k#])) 
                  (or ~value-fn (fn [k# & _#] k#)) 
                  nil args#)))))))

(defwatcher watch-fn
  "Generic Watch Function."
  [u k v]
  :update u
  :keys   k
  :values v)

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

;; ## Special Handlers

(defn unwatch-on-delete
  "Remove entities from watch pool when they are deleted."
  [watch-fn]
  (on-entity-delete watch-fn (fn [w k _] (unwatch-entity! w k))))

(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core
  (:require [panoptic.utils :as u]))

;; ## Concept
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
  [watch-fn entities handler]
  (->> entities
    (keep 
      (fn [[k x]]
        (when x
          (when-let [f (watch-fn x)]
            (when handler (handler f))
            [k f]))))
    (into {})))

(defn- run-watcher!
  "Run Watcher Loop"
  [watch-fn interval entities handler]
  (let [stop? (atom false)
        watch-future (future
                       (loop []
                         (when-not @stop?
                           (swap! entities #(run-entity-watcher! watch-fn % @handler))
                           (u/sleep interval)
                           (recur))))]
    (fn []
      (reset! stop? true)
      watch-future)))

(deftype GenericWatcher [watch-fn entity-fn interval entities handler stop-fn]
  Watcher
  (wrap-entity-handler! [this f]
    (when f (swap! handler f))
    this)
  (watch-entities! [this es]
    (swap! entities #(reduce (fn [m e] (assoc m e (entity-fn e))) % es))
    this)
  (unwatch-entities! [this es]
    (swap! entities #(reduce dissoc % es))
    this)
  (start-watcher! [this]
    (swap! stop-fn #(or % (run-watcher! watch-fn interval entities handler)))
    this)
  (stop-watcher! [this]
    (when-let [f @stop-fn]
      (reset! stop-fn nil)
      (f)))
  
  Object
  (toString [this]
    (pr-str @entities)))

(defn generic-watcher
  "Create generic, single-threaded Watcher using a watch function and an entity 
   creation function."
  [watch-fn entity-fn interval]
  (GenericWatcher. watch-fn entity-fn interval (atom {}) (atom nil) (atom nil)))

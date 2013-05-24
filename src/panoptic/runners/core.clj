(ns ^{:doc "Common Runner Logic"
      :author "Yannick Scherer"}
  panoptic.runners.core
  (:use [taoensso.timbre :only [trace debug info warn error]]
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.utils.queue :as q]
            [panoptic.data.core :as data]))

;; ## Concept
;;
;; Each entity resides inside a ref that can be updated by watcher threads. These
;; threads maintain a map of such atoms inside a ref, so redistribution of entities
;; can happen consistently.
;;
;; Additionally, there is a handler thread that listens to a queue providing entities
;; that have changed (ideally) and runs a user-defined handling function on each one
;; of them. This means, that handling is done sequentially, but entity updates can 
;; occur in parallel.

;; ## Helper

(defmacro future-with-errors
  "Create future that handles and prints errors gracefully."
  [& body]
  `(future
     (try
       (do ~@body)
       (catch Exception ex#
         (error ex# "in future")))))

(defn generate-watcher-id
  "Generate ID for watcher."
  ([] (keyword (gensym "watcher-")))
  ([base n] (keyword (str (name base) "-" n))))

;; ## Thread Logic

(defn- run-entity-updates!
  "Run the given WatchFn's update function over all entities contained
   in the given entity map. Entities that have changed will be conj'ed
   to the vector contained in `changes-ref`."
  [watcher watch-fn entities changes-ref]
  (doseq [[k entity-ref] entities]
    (dosync
      (when-let [e (update-entity! watch-fn watcher k @entity-ref)]
        (ref-set entity-ref e)
        (when (data/changed? e) 
          (alter changes-ref conj [k e]))))))

(defn- process-changes!
  "Push elements in the given ref to the given Queue."
  [changes-queue changes-ref]
  (doseq [e (dosync
              (let [c @changes-ref]
                (when-not (empty? c)
                  (ref-set changes-ref []) 
                  c)))] 
    (q/push! changes-queue e)))

(defn run-update-thread!
  "Run thread that updates the entities contained in the given ref/atom/...
   periodically, until the value in the given stop-atom is set to true.
   An optional offset in milliseconds can be given that is used to let
   the thread sleep before being operational."
  ([tag watcher watch-fn start-offset update-interval stop-atom entities-ref changes-queue]
   (run-update-thread! nil tag watcher watch-fn start-offset update-interval stop-atom entities-ref changes-queue))
  ([go-promise tag watcher watch-fn start-offset update-interval stop-atom entities-ref changes-queue]
   (let [changes-ref (ref [])]
     (future-with-errors
       (when go-promise @go-promise) 
       (when (and start-offset (pos? start-offset)) (u/sleep start-offset))
       (info tag "Watch Thread running" 
             (str "(poll interval: " update-interval "ms, offset: " (or start-offset 0) "ms)") 
             "...")
       (while (not @stop-atom)
         (let [entities @entities-ref]
           (when-not (empty? entities)
             (debug tag "Updating" (count entities) "Entities ...") 
             (run-entity-updates! watcher watch-fn entities changes-ref)
             (process-changes! changes-queue changes-ref)))
         (u/sleep update-interval))
       (info tag "Watch Thread stopped.")))))

(defn run-handler-thread!
  "Run thread that polls from the given Queue and run's the given WatchFn's
   handler function on the entities it receives (expects [entity-key entity] 
   pairs) until the given stop-atom is set to true."
  ([tag watcher watch-fn update-interval stop-atom changes-queue]
   (run-handler-thread! nil tag watcher watch-fn update-interval stop-atom changes-queue))
  ([go-promise tag watcher watch-fn update-interval stop-atom changes-queue]
   (future-with-errors
     (when go-promise @go-promise)
     (info tag "Handler Thread running (poll timeout: " (str update-interval "ms)") "...")
     (while (not @stop-atom)
       (when-let [[k e] (q/poll! changes-queue update-interval nil)]
         (debug tag "Running Handler on:" k)
         (run-entity-handler! watch-fn watcher k e) ))
     (info tag "Handler Thread stopped."))))

;; ## Standalone Watcher Thread

(defn run-standalone-watcher!
  "Run standalone watcher, consisting of a single watcher thread and a single
   handler thread. Returns a vector of those both threads (`[update handle]`)."
  [id watcher watch-fn start-offset update-interval entities-ref stop-atom]
  (let [tag (str "[" id "]")
        changes-queue (q/queue)
        updater-thread (run-update-thread! 
                        tag watcher watch-fn start-offset update-interval 
                        stop-atom entities-ref changes-queue)
        handler-thread (run-handler-thread!
                        tag watcher watch-fn update-interval
                        stop-atom changes-queue)]
    [updater-thread handler-thread]))

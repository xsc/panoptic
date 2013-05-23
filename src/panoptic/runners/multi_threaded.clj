(ns ^{:doc "Multi-Threaded Watch Runners"
      :author "Yannick Scherer"}
  panoptic.runners.multi-threaded
  (:use [taoensso.timbre :only [debug info warn error]]
        panoptic.runners.core
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.runners.simple :as sw])) 

;; ## Concept
;;
;; The multi-threaded Watcher distributes entities it is supposed to watch evenly between
;; its threads.

;; ## Logic

(defn- run-multi-watcher!
  [id w watch-fn interval entities]
  (let [tag (str "[" id "]")
        thread-count (count entities)
        offset (let [o (int (/ interval thread-count))]
                 (if (< o 1) 1 o)) 
        _ (info tag "Starting" thread-count "Watcher Threads" (str "(Offset: " offset "ms)") "...")
        threads (doall
                  (for [[n e] (map vector (range) entities)]
                    (let [thread-id (keyword (str (name id) "-" n))
                          [ft f] (run-watcher-thread! thread-id w watch-fn interval e (* n offset))]
                      (-> {}
                        (assoc :thread ft)
                        (assoc :entities e)
                        (assoc :stop-fn f)))))
        stop-future (future
                      (doseq [{:keys [thread]} threads]
                        @thread)
                      (info tag thread-count "Watcher Threads stopped."))
        stop-fn (fn []
                  (info tag "Stopping" thread-count "Watcher Threads ...")
                  (doseq [{:keys [stop-fn]} threads]
                    (try (stop-fn) (catch Exception _ nil)))
                  stop-future)]
    [stop-future stop-fn]))

(defn- update-entity-refs!
  [watch-fn thread-count update-fn entities es]
  (dosync
    (let [new-entities (let [m (reduce #(merge %1 (deref %2)) {} entities)]
                         (update-fn watch-fn m es))
          c (inc (int (/ (count new-entities) thread-count))) 
          groups (->> new-entities
                   (partition c c nil)
                   (map #(into {} %)))]
      (doseq [[e g] (map vector entities (concat groups (repeat {})))]
        (ref-set e g))))) 

;; ## MultiWatcher

(deftype MultiWatcher [id watch-fn thread-count interval thread-data entities]
  WatchRunner
  (watch-entities! [this es]
    (update-entity-refs! watch-fn thread-count add-entities entities es)
    this)
  (unwatch-entities! [this es]
    (future
      (u/sleep interval) ;; all entities should be processed at least once more (TODO: seems like a hack)
      (update-entity-refs! watch-fn thread-count remove-entities entities es)) 
    this)
  (watched-entities [this]
    (reduce #(merge %1 (deref %2)) {} entities))
  (start-watcher! [this]
    (dosync
      (when-not @thread-data
        (ref-set thread-data (run-multi-watcher! id this watch-fn interval entities))))
    this)
  (stop-watcher! [this]
    (dosync
      (when-let [[ft f] @thread-data]
        (ref-set thread-data nil)
        (f)
        ft)))

  clojure.lang.IDeref
  (deref [_]
    (dosync
      (let [[ft _] @thread-data]
        (when ft @ft))))
  
  Object
  (toString [this]
    (pr-str (watched-entities this))))

(defmethod print-method MultiWatcher
  [o w]
  (print-simple 
    (str "#<MultiWatcher: " (.toString o) ">")
    w))

(defn multi-watcher
  "Create a generic, multi-threaded Watcher."
  [watch-fn thread-count interval] 
  (let [thread-count (or thread-count 2)]
    (MultiWatcher. 
      (keyword (gensym "multi-watcher-")) 
      watch-fn
      thread-count
      (or interval 1000) 
      (ref nil)
      (doall (take thread-count (repeatedly #(ref {})))))))

;; ## Start MultiWatcher

(defn start-multi-watcher!*
  "Create and start generic, multi-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn initial-entities & {:keys [interval threads]}]
  (->
    (multi-watcher watch-fn threads interval)
    (watch-entities! initial-entities)
    (start-watcher!)))

(defn start-multi-watcher!
  "Create and start generic, multi-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn & args]
  (if (or (not (seq args)) (keyword? (first args))) 
    (apply start-multi-watcher!* watch-fn nil args)
    (apply start-multi-watcher!* watch-fn args)))

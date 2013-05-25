(ns ^{:doc "Multi-Threaded Watch Runners"
      :author "Yannick Scherer"}
  panoptic.runners.multi-runner
  (:use [taoensso.timbre :only [debug info warn error]]
        panoptic.runners.core
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.utils.queue :as q]))

;; ## Concept
;;
;; In addition to watcher and handler threads the multi-threaded runner uses a scheduling/
;; distribution thread to move entities between watchers and to handle added/removed entities.
;; The distribution strategy can be user-defined, e.g.:
;;
;; - let multiple threads watch all entities simulateneously (same as multiple simple watchers);
;; - let multiple threads watch equally big portions of the entity pool;
;; - let threads watch entities by change frequency (e.g. more changes mean more checking);
;; - ...
;;
;; Redistribution can happen periodically.

;; ## Logic

(def ^:private ENTITY_MAP_ADD (Object.))
(def ^:private ENTITY_MAP_REMOVE (Object.))

(defn- run-multi-watcher-threads!
  "Create map of [index {:thread <future> :entities <ref>}] pairs, representing
   the desired number of threads availale for watching."
  [go? id watcher watch-fn thread-count update-interval stop-atom changes-queue]
  (let [tag (str "[" id "]")
        offset (inc (int (/ update-interval thread-count)))]
    (reduce
      (fn [m n]
        (let [thread-tag (str "[" (generate-watcher-id id n) "]") 
              thread-entities (ref {})
              thread (run-update-thread! 
                       go? thread-tag watcher watch-fn 
                       (* n offset) update-interval 
                       stop-atom thread-entities changes-queue)]
          (assoc m n { :thread thread :entities thread-entities })))
      {} (range thread-count))))

(defn- run-distribution-thread!
  "Create future containing a distribution thread that redestributes the entities contained in 
   `entities-atom` everytime an entity is delivered via `notify-queue` (including the special entities
    ENTITY_MAP_ADD/ENTITY_MAP_REMOVE).
 
   `distribute-fn` is a function taking four arguments:
 
   - the thread map created by `run-multi-watcher-threads!`
   - the update interval in milliseconds
   - the complete entity map
   - the entity that prompted the change (or :add/:remove(:init)
  "
  [go? tag distribute-fn updaters update-interval entities-atom stop-atom notify-queue changes-queue]
  (let [distribute! (partial distribute-fn updaters update-interval)
        distribute-interval (/ update-interval 2)]
    (future-with-errors
      (info tag "Distribution Thread started ...")
      (q/clear! notify-queue)
      (distribute! @entities-atom :init)
      (deliver go? true)
      (while (not @stop-atom)
        (when-let [e (q/poll! notify-queue distribute-interval)]
          (condp = e
            ENTITY_MAP_ADD    (distribute! @entities-atom :add)
            ENTITY_MAP_REMOVE (distribute! @entities-atom :remove)
            (do 
              (q/push! changes-queue e)
              (distribute! @entities-atom e))))))))

(defn- run-multi-watcher!
  "Create and start watcher/handler/distributor threads. Return a map with the respecitive keys
   `:updaters`/`:handler`/`:distributor`."
  [id watcher watch-fn distribute-fn thread-count update-interval entities-atom stop-atom notify-queue]
  (let [tag (str "[" id "]")
        changes-queue (q/queue)
        go? (promise)
        updater-threads (run-multi-watcher-threads!
                          go? id watcher watch-fn 
                          thread-count update-interval 
                          stop-atom notify-queue)
        distr-thread (run-distribution-thread! 
                       go? tag distribute-fn updater-threads update-interval
                       entities-atom stop-atom 
                       notify-queue changes-queue)
        handler-thread (run-handler-thread!
                         go? tag watcher watch-fn
                         update-interval 
                         stop-atom changes-queue)]
    @go?
    (-> {}
      (assoc :updaters updater-threads) 
      (assoc :distributor distr-thread)
      (assoc :handler handler-thread))))

(defn- collect-threads
  "Based on a Thread Map, create a seq of all threads contained therein."
  [thread-map]
  (concat
    [(:handler thread-map)
     (:distributor thread-map)]
    (map :thread (:threads thread-map))))

;; ## Multi Runner

(deftype MultiRunner [id watch-fn distribute-fn 
                      thread-count interval 
                      entities-atom stop-atom threads-atom
                      notify-queue]
  WatchRunner
  (watch-entities!* [this es metadata]
    (swap! entities-atom #(add-entities watch-fn % es metadata))
    (q/push! notify-queue ENTITY_MAP_ADD)
    this)
  (unwatch-entities!* [this es metadata]
    (swap! entities-atom #(remove-entities watch-fn % es metadata))
    (q/push! notify-queue ENTITY_MAP_REMOVE)
    this)
  (watched-entities [this]
    (read-watched-entities entities-atom))
  (start-watcher! [this]
    (reset! stop-atom false)
    (swap! threads-atom #(or % (run-multi-watcher! 
                                 id this watch-fn distribute-fn
                                 thread-count interval 
                                 entities-atom stop-atom
                                 notify-queue)))
    this)
  (stop-watcher! [this]
    (reset! stop-atom true)
    (let [ts (when-let [threads @threads-atom]
               (reset! threads-atom nil)
               (collect-threads threads))]
      (future 
        (doseq [t ts] @t)
        (q/clear! notify-queue)
        nil)))

  clojure.lang.IDeref
  (deref [_]
    (try
      (doseq [thread (collect-threads @threads-atom)]
        @thread)
      (catch Exception ex
        (error ex "when dereferencing MultiRunner threads.")))))

(defmethod print-method MultiRunner
  [o w]
  (print-simple 
    (str "#<MultiRunner: " (watched-entities o) ">")
    w))

;; ## Distribution Logic

(defmulti create-distributor 
  (fn [x] (if (fn? x) ::fn x))
  :default :none)

(defmethod create-distributor ::fn [f] f)

(defmethod create-distributor :none
  [_]
  ;; - let all threads handle all entities
  ;; - do not redistribute on entity modification
  (fn [updaters interval current-entities e]
    (when (keyword? e)
      (dosync
        (doseq [{:keys [entities]} (vals updaters)]
          (ref-set entities current-entities))))))

(defmethod create-distributor :fair
  [_]
  ;; - let threads handle equal portions
  ;; - do not redistribute on entity modification
  (fn [updaters interval current-entities e]
    (when (keyword? e)
      ;; TODO
      )
    )
  )

;; ## Creation/Start Functions

(defn multi-runner
  "Create a generic, multi-threaded Watcher."
  [watch-fn distribute thread-count interval] 
  (let [thread-count (or thread-count 1)]
    (MultiRunner. 
      (generate-watcher-id) watch-fn 
      (create-distributor distribute) 
      thread-count interval 
      (atom {}) (atom false) (atom nil)
      (q/queue))))

(defn start-multi-watcher!*
  "Create and start generic, multi-threaded Watcher using: 
   - a WatchFn instance
   - the initial entities to watch
   - additional options (e.g. the watch loop interval in milliseconds)."
  [watch-fn initial-entities & {:keys [interval threads distribute]}]
  (->
    (multi-runner watch-fn distribute threads interval)
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

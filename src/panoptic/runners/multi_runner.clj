(ns ^{:doc "Multi-Threaded Watch Runners"
      :author "Yannick Scherer"}
  panoptic.runners.multi-runner
  (:use [taoensso.timbre :only [debug info warn error trace]]
        panoptic.runners.core
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.utils.queue :as q]
            [panoptic.runners.distributors :as d]))

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

(defn- run-multi-watcher-threads!
  "Create map of [index {:thread <future> :entities <ref>}] pairs, representing
   the desired number of threads availale for watching."
  [go? id watcher watch-fn distributor thread-count stop-atom changes-queue]
  (let [tag (str "[" id "]")]
    (reduce
      (fn [m n]
        (let [thread-id (generate-watcher-id id n) 
              thread-entities (ref {})
              thread (run-update-thread! 
                       go? thread-id watcher watch-fn 
                       (d/thread-offset distributor n)
                       (d/thread-interval distributor n)
                       stop-atom thread-entities changes-queue)]
          (assoc m n { :thread thread :entities thread-entities })))
      {} (range thread-count))))

(defn- run-multi-watcher!
  "Create and start watcher/handler/distributor threads. Return a map with the respecitive keys
   `:updaters`/`:handler`/`:distributor`."
  [id watcher watch-fn distributor thread-count entities-atom stop-atom notify-queue]
  (let [changes-queue (q/queue)
        go? (promise)
        updater-threads (run-multi-watcher-threads!
                          go? id watcher watch-fn 
                          distributor thread-count
                          stop-atom notify-queue)
        distr-thread (d/run-distribution-thread! 
                       go? id distributor updater-threads
                       entities-atom stop-atom 
                       notify-queue changes-queue)
        handler-thread (run-handler-thread!
                         go? id watcher watch-fn
                         (d/thread-interval distributor 0) 
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

(deftype MultiRunner [id watch-fn distributor thread-count entities-atom stop-atom threads-atom notify-queue]
  WatchRunner
  (watch-entities!* [this es metadata]
    (swap! entities-atom #(add-entities watch-fn % es metadata))
    (d/notify-entities-added! notify-queue es)
    this)
  (unwatch-entities!* [this es metadata]
    (swap! entities-atom #(remove-entities watch-fn % es metadata))
    (d/notify-entities-removed! notify-queue es)
    this)
  (watched-entities [this]
    (read-watched-entities entities-atom))
  (start-watcher! [this]
    (reset! stop-atom false)
    (swap! threads-atom #(or % (run-multi-watcher! id this watch-fn distributor thread-count
                                                   entities-atom stop-atom notify-queue)))
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

;; ## Creation/Start Functions

(defn multi-runner
  "Create a generic, multi-threaded Watcher."
  [watch-fn distribute thread-count interval] 
  (let [thread-count (or thread-count 1)]
    (MultiRunner. 
      (generate-watcher-id) watch-fn 
      (d/create-distributor distribute interval thread-count) 
      thread-count
      (atom {}) (atom false) (atom nil)
      (q/queue))))

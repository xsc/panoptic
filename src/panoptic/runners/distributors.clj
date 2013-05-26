(ns ^ {:doc "Distribution Strategies" 
       :author "Yannick Scherer"}
  panoptic.runners.distributors
  (:use [taoensso.timbre :only [debug info error trace]]
        panoptic.runners.core
        panoptic.watchers.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.utils.queue :as q]
            [panoptic.data.core :as data]))

;; ## Distributor Logic
;;
;; Distributors handle a watch-thread's activity (offset, update interval) 
;; and the amount of data it has to process everytime it gets active.

(defprotocol Distributor
  (thread-offset [this thread-index]
    "Get offset for the given thread based on the total thread
     count and the base update interval.")
  (thread-interval [this thread-index]
    "Get update interval for the given thread base on the total
     thread count and the base update interval.")
  (distribute [this entities modification]
    "Create new distribution of Entities. Should create a 
     seq of entity maps to be assigned to the given number
     of threads. If no redistribution is necessary, return
     `nil`"))

(defmulti create-distributor 
  "Get Distributor by ID."
  (fn [x base-interval thread-count] x)
  :default :simple)

;; ## Simple Distributor
;;
;; - all threads handle all entities
;; - entities are updated on add/remove
;; - threads are not run at the same time but using a fixed offset

(deftype SimpleDistributor [base-interval thread-count]
  Distributor
  (thread-offset [_ n]
    (when (pos? n)
      (let [o (long (Math/ceil (/ base-interval thread-count)))]
        (* n o))))
  (thread-interval [_ _] base-interval)
  (distribute [_ entities {:keys [type]}]
    (when (contains? #{:add :remove :init} type)
      (repeat thread-count entities))))

(defmethod create-distributor :simple
  [_ i c]
  (SimpleDistributor. i c))

;; ## Fair Distributor
;;
;; - threads handle (ideally equally sized) portions of the entity pool
;; - entities are updated on add/remove
;; - threads are run at the same time

(deftype FairDistributor [base-interval thread-count]
  Distributor
  (thread-offset [& _] nil)
  (thread-interval [& _] base-interval)
  (distribute [_ entities {:keys [type]}]
    (when (= type :remove) (u/sleep base-interval))
    (when (contains? #{:add :remove :init} type)
      (let [c (count entities)
            entities-per-updater (long (Math/ceil (/ c thread-count))) 
            parts (->> entities 
                    (partition entities-per-updater entities-per-updater nil)
                    (map #(into {} %)))
            parts (concat parts (repeat {}))]
        (take thread-count parts)))))

(defmethod create-distributor :fair
  [_ i c]
  (FairDistributor. i c))

;; ## Frequency Distributor
;;
;; - threads have different update intervals (I*(2^n))
;; - entities are distributed on add/remove/modify and periodically
;; - entities are distributed by last modification time
;; - entities that have not been updated for a long time are distributed between
;;   slower updating threads.

(defn- group-by-last-update
  "Get frequency group number of the given entity based on a base interval and
   the total number of threads (each having a different update interval).

   We will assume a grouping factor of 20, i.e. if the fastest thread updates
   every X milliseconds, it will handle everything that changed in the last
   20*X milliseconds. This propagates exponentially: the second-fastest thread
   will handle everything that changed between 20*X and 60*X milliseconds (a
   40*X timespan), the next one handles a 80*X timespan, and so on...
  
   If no change information is attached, it will be set to the current timestamp,
   the entity thus assigned to the fastest thread, decaying over time."
  [now base-interval thread-count entities]
  (let [I (* base-interval 20)]
    (letfn [(g [[_ entity]]
              (let [t (dosync 
                        (if-let [c (data/last-changed @entity)]
                          c
                          (let [t (u/unix-timestamp)]
                            (alter entity data/set-changed)
                            t)))
                    T (- now t)] 
                (if (<= T I) 
                  0
                  (let [n (long (dec (Math/ceil (u/log2 (inc (/ T I))))))]
                    (if (< n thread-count) n -1)))))]
      (group-by g entities))))

(defn- distribute-by-last-update
  [now base-interval thread-count entities ratio]
  (let [groups (group-by-last-update now base-interval thread-count entities)
        assigned (dissoc groups -1)
        unassigned (get groups -1)
        unassigned-per-thread (long (Math/ceil (/ (count unassigned) thread-count ratio)))]
    (loop [n (dec thread-count)
           u unassigned
           r []]
      (if (neg? n) r
        (recur 
          (dec n)
          (drop unassigned-per-thread u)
          (cons 
            (->> (get assigned n)
              (concat (take unassigned-per-thread u))
              (into {})) 
            r))))))

(deftype FrequencyDistributor [ratio base-interval thread-count]
  Distributor
  (thread-offset [& _] nil)
  (thread-interval [_ n]
    (let [p (long (Math/pow 2 n))]
      (long (* base-interval p))))
  (distribute [_ entities {:keys [type]}]
    (when (= type :remove) (u/sleep base-interval))
    (let [now (u/unix-timestamp)]
      (distribute-by-last-update now base-interval thread-count entities ratio))))

(defmethod create-distributor :frequency
  [_ i c]
  (FrequencyDistributor. 0.5 i c))

;; ## Distribution Thread
;;
;; The distribution thread maintains two queues: the notifcation queue
;; where it can poll changes to the entity pool (modifications, new
;; entities, ...) and the handler queue where it will push all
;; modified entities.

;; ### Data

(def ^:dynamic *distribute-poll-factor* 2)
(def ^:dynamic *distribute-periodic-cycles* 5)

;; ### Notifications

(def ^:private ENTITIES_ADDED (Object.))
(def ^:private ENTITIES_REMOVED (Object.))

(defn notify-entities-added!
  "Send notification on the given Queue."
  [q entities]
  (q/push! q { ::notify ENTITIES_ADDED :data entities }))

(defn notify-entities-removed!
  "Send notification on the given Queue."
  [q entities]
  (q/push! q { ::notify ENTITIES_REMOVED ::data entities }))

;; ### Actual Distribution

(defn- distribute!
  "Perform actual, STM-based distribution."
  [tag distributor updaters entities modification]
  (when-let [entities-per-thread (distribute distributor entities modification)] 
    (let [entity-refs (map :entities (map second (sort-by first updaters)))] 
      (debug tag "Distribution:" (vec (map count entities-per-thread)))
      (dosync
        (doseq [[r e] (map vector entity-refs (concat entities-per-thread (repeat {})))]
          (ref-set r e))))))

(def ^:private distribute-init! 
  #(distribute! %1 %2 %3 %4 {:type :init}))

(def ^:private distribute-add! 
  #(distribute! %1 %2 %3 %4 {:type :add :data %5}))

(def ^:private distribute-remove! 
  #(distribute! %1 %2 %3 %4 {:type :remove :data %5}))

(def ^:private distribute-modify! 
  #(distribute! %1 %2 %3 %4 {:type :modify :data %5}))

(def ^:private distribute-periodic!
  #(distribute! %1 %2 %3 %4 {:type :periodic}))

;; ### Polling & Handliner

(defn- poll-notification!
  "Poll Notifications and try to merge similar notifications into one. Will return
   either a map describing the changes or a vector containing modified elements."
  [q timeout]
  (when-let [h (q/poll! q timeout)]
    (let [type (::notify h)]
      (loop [h (if type h [h])]
        (if-let [nxt (first q)]
          (let [nxt-type (::notify nxt)]
            (if (= nxt-type type)
              (let [h (if type
                        (update-in h [::data] concat (::data nxt))
                        (conj h nxt))]
                (q/poll! q)
                (recur h))
              h))
          h)))))

;; ### Thread

(defn run-distribution-thread!
  "Create future containing a distribution thread that redestributes the entities contained in 
   `entities-atom` everytime an entity is delivered via `notify-queue`."
  [go? id distributor updaters entities-atom stop-atom notify-queue changes-queue]
  (let [tag (str "[" (generate-watcher-id id "distribute") "]")
        distribute-interval (long (* (thread-interval distributor 0) *distribute-poll-factor*))]
    (future-with-errors
      (info tag "Distribution Thread running (poll timeout:" (str distribute-interval "ms)") "...")
      (q/clear! notify-queue)
      (distribute-init! tag distributor updaters @entities-atom)
      (deliver go? true)
      (loop [n 0]
        (when (not @stop-atom)
          (let [e (poll-notification! notify-queue distribute-interval)]
            (cond (not e) (do 
                            (when (= n *distribute-periodic-cycles*)
                              (distribute-periodic! tag distributor updaters @entities-atom))
                            (recur (mod (inc n) (inc *distribute-periodic-cycles*))))
                  (vector? e) (do
                                (doseq [e e]
                                  (q/push! changes-queue e))
                                (recur 0))
                  (and (map? e) (::notify e)) (do
                                                (condp = (::notify e)
                                                  ENTITIES_ADDED (distribute-add! tag distributor updaters @entities-atom (::data e))
                                                  ENTITIES_REMOVED (distribute-remove! tag distributor updaters @entities-atom (::data e))
                                                  nil)
                                                (recur 0))
                  :else (recur n))))))))

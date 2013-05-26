(ns ^ {:doc "Distribution Strategies" 
       :author "Yannick Scherer"}
  panoptic.runners.distributors
  (:require [panoptic.utils.core :as u]
            [panoptic.data.core :as data]))

;; ## Distributor Logic
;;
;; Distributors handle a watch-thread's activity (offset, update interval) 
;; and the amount of data it has to process everytime it gets active.

(defprotocol Distributor
  (thread-offset [this base-interval thread-count thread-index]
    "Get offset for the given thread based on the total thread
     count and the base update interval.")
  (thread-interval [this base-interval thread-count thread-index]
    "Get update interval for the given thread base on the total
     thread count and the base update interval.")
  (distribute [this base-interval thread-counti entities modification]
    "Create new distribution of Entities. Should create a 
     seq of entity maps to be assigned to the given number
     of threads. If no redistribution is necessary, return
     `nil`"))

(extend-type clojure.lang.AFunction
  Distributor
  (thread-offset [_ base-interval _ _] nil)
  (thread-interval [_ base-interval _ _] base-interval)
  (distribute [f & args]
    (apply f args)))

;; ## Simple Distributor
;;
;; - all threads handle all entities
;; - entities are updated on add/remove
;; - threads are not run at the same time but using a fixed offset

(deftype SimpleDistributor [offset?]
  Distributor
  (thread-offset [_ base-interval c n]
    (when (and offset? (pos? n))
      (let [o (long (Math/ceil (/ base-interval c)))]
        (* n o))))
  (thread-interval [_ base-interval _ _] base-interval)
  (distribute [_ _ n entities {:keys [type]}]
    (when (contains? #{:add :remove :init} type)
      (repeat n entities))))

;; ## Fair Distributor
;;
;; - threads handle (ideally equally sized) portions of the entity pool
;; - entities are updated on add/remove
;; - threads are run at the same time

(deftype FairDistributor []
  Distributor
  (thread-offset [_ _ _ _] nil)
  (thread-interval [_ base-interval _ _] base-interval)
  (distribute [_ _ n entities {:keys [type]}]
    (when (contains? #{:add :remove :init} type)
      (let [c (count entities)
            entities-per-updater (long (Math/ceil (/ c n))) 
            parts (->> entities 
                    (partition entities-per-updater entities-per-updater nil)
                    (map #(into {} %)))
            parts (concat parts (repeat {}))]
        (take n parts)))))

;; ## Frequency Distributor
;;
;; - threads have different update intervals
;; - thread X => interval I/(2^(n-1-X)); X in [0;n[
;; - entities are distributed on add/remove/modify and periodically
;; - entities are distributed by last modification time
;; - entities that have not been updated for a long time are distributed between
;;   slower updating threads.

(defn- group-by-last-update
  "Get frequency group number of the given entity based on a base interval and
   the total number of threads (each having a different update interval).
  
   Group n is everything that changed at most (I/2)*(2^(n+1)-1) milliseconds
   ago, and n is less than `thread-count`. Things that have been unchanged for
   more than the maximum group's treshold are assigned group -1."
  [now base-interval thread-count entities]
  (let [I (/ base-interval 2)]
    (letfn [(g [[_ entity]]
              (if-let [t (data/last-changed entity)] 
                (let [T (- now t)]
                  (if (<= T I) 0
                    (let [n (long (dec (Math/ceil (u/log2 (inc (/ T I))))))]
                      (if (< n thread-count) n -1))))
                -1))]
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

(deftype FrequencyDistributor [ratio]
  Distributor
  (thread-offset [_ _ _ _] nil)
  (thread-interval [_ base-interval c n]
    (let [p (long (Math/pow 2 (- c 1 n)))]
      (long (Math/ceil (/ base-interval p)))))
  (distribute [_ base-interval n entities _]
    (let [now (u/unix-timestamp)]
      (distribute-by-last-update now base-interval n entities ratio))))

;; ## Multimethod for Distributor Access

(defmulti create-distributor 
  "Get Distributor using the given key (or a function)."
  (fn [x] (if (fn? x) ::fn x))
  :default :simple)

(defmethod create-distributor ::fn 
  [f] 
  f)

(defmethod create-distributor :simple
  [_]
  (SimpleDistributor.))

(defmethod create-distributor :fair
  [_]
  (FairDistributor.))

(defmethod create-distributor :frequency
  [_]
  (FrequencyDistributor. 0.7))

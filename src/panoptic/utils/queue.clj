(ns ^ {:doc "Blocking Queue for Panoptic"
       :author "Yannick Scherer"}
  panoptic.utils.queue 
  (:import  [java.util.concurrent LinkedBlockingQueue TimeUnit]) )

(set! *warn-on-reflection* true)

(def ^:private NIL
  "Object representing `nil` values, since they can not be directly
   stored in a LinkedBlockingQueue."
  (Object.))

(defn queue
  "Create new blocking Queue."
  ([] (queue nil))
  ([sq] (if sq
          (LinkedBlockingQueue. ^java.util.List sq)
          (LinkedBlockingQueue.))))

(defn push!
  "Push Element to blocking Queue."
  [^LinkedBlockingQueue q v]
  (let [v (if (nil? v) NIL v)]
    (try
      (.offer q v)
      (catch Exception _ nil))
    q))

(defn poll!
  "Poll Element from blocking Queue. If `timeout` is `nil`, blocking may be
   infinitely."
  ([^LinkedBlockingQueue q] (poll! q nil nil))
  ([^LinkedBlockingQueue q timeout] (poll! q timeout nil))
  ([^LinkedBlockingQueue q timeout default-value]
   (if-let [v (if timeout 
                (.poll q (long timeout) TimeUnit/MILLISECONDS)
                (.take q))]
     (if (= v NIL)
       nil
       v)
     default-value)))

(ns ^ {:doc "Generic Checksum Watcher"
       :author "Yannick Scherer"}
  panoptic.watchers.checksum-watcher
  (:require [panoptic.data.core :as data])
  (:use [taoensso.timbre :only [error]]
        panoptic.watchers.core))

(defn update-checksum
  "Update Checksum of the given Entity."
  [checksum-fn data-key entity]
  (try
    (let [chk0 (data/checksum entity)
          chk1 (checksum-fn (get entity data-key))]
      (condp = [chk0 chk1]
        [nil  nil]  (data/set-missing entity)
        [chk0 chk0] (data/set-untouched entity)
        [nil  chk1] (let [entity-new (data/set-created entity chk1)]
                      (if-not (data/exists? entity)
                        entity-new
                        (data/set-untouched entity-new)))
        [chk0 nil]  (data/set-deleted entity)
        (data/set-modified entity chk1))) 
    (catch Exception ex
      (error ex "in checksum update function")
      (data/set-untouched entity))))

(defwatcher checksum-watcher
  "A generic checksum watcher that calculates the checksum 
   by applying `checksum-fn` to the entity map's `data-key`
   element, storing it in `checksum-key`. A checksum of `nil`
   means that the element does not exist.
  
   Triggers `on-entity-create`, `on-entity-modify` and 
   `on-entity-delete`."
  [key-fn value-fn checksum-key checksum-fn data-key]
  :update #(update-checksum checksum-fn data-key %)
  :keys   key-fn
  :values value-fn)

(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file-watcher
  (:use panoptic.watchers.core
        [taoensso.timbre :only [error info]])
  (:require [panoptic.data.file :as f]
            [panoptic.data.core :as data]
            [panoptic.utils.fs :as fs :only [file-exists? last-modified]]
            [panoptic.watchers.checksums :as cs]))

;; ## Protocol

(defprotocol FileEntityHandlers
  (on-file-create [this f])
  (on-file-modify [this f])
  (on-file-delete [this f]))

;; ## File Watcher

(defwatcher file-watcher 
  "Create WatchFn for Files."
  [& {:keys [checksum]}]
  :let [checker (cs/file-checksum-fn checksum)]
  :update #(cs/update-checksum checker :path %)
  :keys   (fn [k created?] (vector [(fs/absolute-path k) created?]))
  :values (fn [p _ created?] 
            (let [f (f/file p)]
              (if created? (data/set-missing f) f)))

  FileEntityHandlers 
  (on-file-create [this f] (on-entity-create this f)) 
  (on-file-modify [this f] (on-entity-modify this f)) 
  (on-file-delete [this f] (on-entity-delete this f)))

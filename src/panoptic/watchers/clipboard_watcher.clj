(ns ^ {:doc "Clipboard Watcher"
       :author "Yannick Scherer"}
  panoptic.watchers.clipboard-watcher
  (:use panoptic.watchers.core)
  (:require [panoptic.utils.clipboard :as clip]
            [panoptic.watchers.checksums :as cs]))

;; ## Protocol

(defprotocol ClipboardEntityHandlers
  (on-content-set [this f])
  (on-content-clear [this f]))

;; ## Clipboard Data

(defmulti clipboard-data-fn
  (fn [k] (if (fn? k) ::fn k))
  :default :string)

(defmethod clipboard-data-fn :string [_] clip/string-contents)
(defmethod clipboard-data-fn :image [_] clip/image-contents)
(defmethod clipboard-data-fn :files [_] clip/file-list-contents)

;; ## Clipboard Watcher

(defwatcher clipboard-watcher
  "Clipboard Watcher."
  [& {:keys [data-type]}]
  :let [data-fn (clipboard-data-fn data-type)]
  :update (fn [e]
            (let [e (assoc e :data (data-fn))] 
              (cs/update-checksum #(when % (.hashCode %)) :data e)))
  :key (constantly ::c)
  :values (constantly {})
  :initial [:go]

  ClipboardEntityHandlers
  (on-content-set [this f]
    (-> this
      (on-entity-modify #(f %1 (:data %3)))
      (on-entity-create #(f %1 (:data %3)))))
  (on-content-clear [this f]
    (on-entity-delete this #(f %1))))

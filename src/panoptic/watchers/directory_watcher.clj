(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory-watcher
  (:use panoptic.watchers.core
        panoptic.watchers.file-watcher)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.data.core :as data]
            [panoptic.data.file :as f]
            [panoptic.data.directory :as d]
            [panoptic.utils.fs :as fs]
            [panoptic.utils.core :as u]))

;; ## Protocol

(defprotocol DirectoryEntityHandlers
  (^:private on-subdirectory-create [this f])
  (^:private on-subdirectory-delete [this f])
  (on-directory-create [this f])
  (on-directory-delete [this f]))

;; ## Logic

(defn- update-directory!
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [refresh-fn d0]
  (let [missing? (not (data/exists? d0))
        d (refresh-fn d0)]
    (cond (not (or missing? d))  (-> d0 (data/set-children-diff d) (data/set-deleted)) 
          (and missing? (not d)) (data/set-missing d0)
          (and missing? d)       (-> d0 (data/set-children-diff d) (data/set-created)) 
          :else (-> d0 (data/set-children-diff d) (data/set-untouched)))))

;; ## Recursive Directory Watcher

(defwatch recursive-directory-watcher*
  DirectoryEntityHandlers
  (on-directory-create [this f]
    (on-entity-create this f))
  (on-directory-delete [this f]
    (on-entity-delete this f))
  
  FileEntityHandlers
  (on-file-create [this f]
    (on-child-create this :files #(f %1 %2 (f/file (str (:path %2) "/" %3)))))
  (on-file-delete [this f]
    (on-child-delete this :files #(f %1 %2 (f/file (str (:path %2) "/" %3)))))
  (on-file-modify [this f] this))

(defn- recursive-directory-watcher
  [refresh-fn opts]
  (->
    (recursive-directory-watcher*
      (partial update-directory! refresh-fn) 
      (fn [k] (vector (fs/absolute-path k)))
      (fn [p _] (apply d/directory p opts)))
    (on-child-create :directories #(watch-entity! %1 [%3])) 
    (on-child-delete :directories #(unwatch-entity! %1 %3))))

;; ## Normal Directory Watcher

(defwatch normal-directory-watcher*
  DirectoryEntityHandlers
  (on-directory-create [this f]
    (-> this
      (on-entity-create f) 
      (on-child-create :directories #(f %1 %2 (d/directory (str (:path %2) "/" %3))))))
  (on-directory-delete [this f]
    (-> this
      (on-entity-delete f) 
      (on-child-delete :directories #(f %1 %2 (d/directory (str (:path %2) "/" %3))))))
  
  FileEntityHandlers
  (on-file-create [this f]
    (on-child-create this :files #(f %1 %2 (f/file (str (:path %2) "/" %3)))))
  (on-file-delete [this f]
    (on-child-delete this :files #(f %1 %2 (f/file (str (:path %2) "/" %3)))))
  (on-file-modify [this f] this))

(defn- normal-directory-watcher
  [refresh-fn opts]
  (normal-directory-watcher*
    #(update-directory! refresh-fn %) 
    #(vector (fs/absolute-path %))
    (fn [p _] (apply d/directory p opts))))

;; ## Putting it together

(defn directory-watcher
  "Create directory watch function using the given options."
  [& {:keys [recursive refresh] :as opts}]
  (let [opts (apply concat opts)
        refresh (or refresh d/refresh-directory)]
    (if recursive
      (recursive-directory-watcher refresh opts)
      (normal-directory-watcher refresh opts))))  

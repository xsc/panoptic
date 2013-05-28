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
  (on-directory-create [this f])
  (on-directory-delete [this f]))

;; ## Logic

(defn- update-directory!
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [refresh-fn d0]
  (let [missing? (not (data/exists? d0))
        d0 (data/clear-children-diff d0)
        d (refresh-fn d0)]
    (cond (not (or missing? d))  (-> d0 (data/set-children-diff d) (data/set-deleted) (data/clear-children)) 
          (and missing? (not d)) (data/set-missing d0)
          (and missing? d)       (-> d0 (data/set-children-diff d) (data/set-created)) 
          :else (-> d0 (data/set-children-diff d) (data/set-untouched)))))

;; ## Recursive Directory Watcher

(defwatcher recursive-directory-watcher
  ""
  [refresh-fn opts]
  :update #(update-directory! refresh-fn %)
  :keys  (fn [k created?]
           (let [paths (cons (fs/absolute-path k) (fs/list-directories-recursive k))]
             (if-not created?
               paths
               (map #(vector % created?) paths))))
  :values (fn [p _ created?] 
            (let [dir (apply d/directory p opts)]
              (if-not created? dir (data/set-missing dir))))
  :init   (fn [this]
            (-> this
              (on-child-create :directories #(watch-entity! %1 (str (:path %2) "/" %3) :created)) 
              (on-child-delete :directories #(unwatch-entity! %1 (str (:path %2) "/" %3)))))

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

;; ## Normal Directory Watcher

(defwatcher normal-directory-watcher
  ""
  [refresh-fn opts]
  :update #(update-directory! refresh-fn %)
  :key    (fn [k _] (fs/absolute-path k))
  :values (fn [k _ _] (apply d/directory k opts)) 

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

;; ## Putting it together

(defn directory-watcher
  "Create directory watch function using the given options."
  [& {:keys [recursive refresh] :as opts}]
  (let [opts (apply concat opts)
        refresh (or refresh d/refresh-directory)]
    (if recursive
      (recursive-directory-watcher refresh opts)
      (normal-directory-watcher refresh opts))))  

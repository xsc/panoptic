(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.data.file :as fs]
            [panoptic.data.directory :as f]
            [panoptic.utils.core :as u]))

;; ## Handlers for Directories

(defprotocol DirectoryEntityHandlers
  (on-directory-create [this f])
  (on-directory-delete [this f])
  (on-subfile-create [this f])
  (on-subfile-delete [this f])
  (on-subdirectory-create [this f]) 
  (on-subdirectory-delete [this f])) 

(defn- on-directory-change
  [set-key create-fn watch-fn f]
  (after-entity-handler 
    watch-fn 
    #(when-let [s (get %3 set-key)] 
       (doseq [e s]
         (f %1 %3 (create-fn (str (:path %3) "/" e)))))))

(defn- on-flag [flag watch-fn f]
  (after-entity-handler
    watch-fn
    #(when (get %3 flag)
       (f %1 %2 %3))))

;; ## Directory Watcher

(defwatch DirectoryWatcher
  DirectoryEntityHandlers
  (on-directory-create [this f] (on-flag :created this f))
  (on-directory-delete [this f] (on-flag :deleted this f))
  (on-subfile-create [this f] (on-directory-change :created-files fs/file this f))
  (on-subfile-delete [this f] (on-directory-change :deleted-files fs/file this f) )
  (on-subdirectory-create [this f] (on-directory-change :created-dirs f/directory this f) ) 
  (on-subdirectory-delete [this f] (on-directory-change :deleted-dirs f/directory this f) ))

;; ## Watching Directories

(defn- update-directory!
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [{:keys [missing deleted] :as d0}]
  (let [missing? (or missing deleted)
        d (f/refresh-directory d0)]
    (cond (not (or missing? d))  (f/set-directory-deleted d0)
          (and missing? (not d)) (f/set-directory-missing d0)
          (and missing? d)       (-> d0 (f/set-directory-diff d) (f/set-directory-created)) 
          :else (-> d0 (f/set-directory-diff d) (f/set-directory-untouched)))))

;; ## Directory Watcher

(defn- recursive-directory-watcher
  [opts]
  (->
    (DirectoryWatcher.
      update-directory!
      (fn [m f]
        (let [[path created?] (if (string? f) [f nil] [(first f) true])]
          (when-let [ds (apply f/directories path opts)] 
            (let [ds (if created? (map f/set-directory-missing ds) ds)] 
              (reduce #(assoc %1 (:path %2) %2) m ds)))))
      (fn [m path]
        (when-let [ds (apply f/directories path opts)]
          (reduce #(dissoc %1 (:path %2)) m ds)))
      nil)
    (on-subdirectory-create #(watch-entity! %1 [(:path %3)])) 
    (on-subdirectory-delete #(unwatch-entity! %1 (:path %3)))))

(defn- normal-directory-watcher
  [opts]
  (DirectoryWatcher.
    update-directory!
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (assoc m (:path d) d)))
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (dissoc m (:path d) d)))
    nil))

(defn directory-watcher
  "Create directory watch function using the given options."
  [& {:keys [recursive] :as opts}]
  (let [opts (apply concat opts)]
    (if recursive
      (recursive-directory-watcher opts)
      (normal-directory-watcher opts))))  

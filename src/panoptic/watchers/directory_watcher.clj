(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory-watcher
  (:use panoptic.watchers.core
        panoptic.watchers.file-watcher)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.runners.core :as r]
            [panoptic.data.file :as fs]
            [panoptic.data.directory :as f]
            [panoptic.utils.core :as u]))

;; ## Protocol

(defprotocol DirectoryEntityHandlers
  (^:private on-subdirectory-create [this f])
  (^:private on-subdirectory-delete [this f])
  (on-directory-create [this f])
  (on-directory-delete [this f]))

;; ## Logic

(defn- on-directory-change
  [watch-fn set-key create-fn f]
  (after-entity-handler 
    watch-fn 
    #(when-let [s (get %3 set-key)] 
       (doseq [e s]
         (f %1 %3 (create-fn (str (:path %3) "/" e)))))))

(defn- update-directory!
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [{:keys [missing deleted] :as d0}]
  (let [missing? (or missing deleted)
        d (f/refresh-directory d0)]
    (cond (not (or missing? d))  (-> d0 (f/set-directory-diff d) (f/set-directory-deleted)) 
          (and missing? (not d)) (f/set-directory-missing d0)
          (and missing? d)       (-> d0 (f/set-directory-diff d) (f/set-directory-created)) 
          :else (-> d0 (f/set-directory-diff d) (f/set-directory-untouched)))))

;; ## Recursive Directory Watcher

(defwatch recursive-directory-watcher*
  DirectoryEntityHandlers
  (on-directory-create [this f]
    (on-flag-set this :created f))
  (on-directory-delete [this f]
    (on-flag-set this :deleted f))
  
  FileEntityHandlers
  (on-file-create [this f]
    (on-directory-change this :created-files fs/file f))
  (on-file-delete [this f]
    (on-directory-change this :deleted-files fs/file f))
  (on-file-modify [this f] nil))

(defn- recursive-directory-watcher
  [opts]
  (->
    (recursive-directory-watcher*
      update-directory!
      (fn [m f]
        (let [[path created?] (if (string? f) [f nil] [(first f) true])]
          (when-let [ds (apply f/directories path opts)] 
            (let [ds (if created? (map f/set-directory-missing ds) ds)] 
              (reduce #(assoc %1 (:path %2) %2) m ds)))))
      (fn [m path]
        (when-let [ds (apply f/directories path opts)]
          (reduce #(dissoc %1 (:path %2)) m ds))))
    (on-directory-change :created-dirs identity #(r/watch-entity! %1 [%3])) 
    (on-directory-change :deleted-dirs identity #(r/unwatch-entity! %1 %3))))

;; ## Normal Directory Watcher

(defwatch normal-directory-watcher*
  DirectoryEntityHandlers
  (on-directory-create [this f]
    (-> this
      (on-flag-set :created f)
      (on-directory-change :created-dirs f/directory f)))
  (on-directory-delete [this f]
    (-> this
      (on-directory-change :deleted-dirs f/directory f) 
      (on-flag-set :deleted f)))
  
  FileEntityHandlers
  (on-file-create [this f]
    (on-directory-change this :created-files fs/file f))
  (on-file-delete [this f]
    (on-directory-change this :deleted-files fs/file f))
  (on-file-modify [this f] nil))

(defn- normal-directory-watcher
  [opts]
  (normal-directory-watcher*
    update-directory!
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (assoc m (:path d) d)))
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (dissoc m (:path d) d)))))

;; ## Putting it together

(defn directory-watcher
  "Create directory watch function using the given options."
  [& {:keys [recursive] :as opts}]
  (let [opts (apply concat opts)]
    (if recursive
      (recursive-directory-watcher opts)
      (normal-directory-watcher opts))))  

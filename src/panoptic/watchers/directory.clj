(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.data.file :as fs]
            [panoptic.data.directory :as f]
            [panoptic.watchers.simple :as sm :only [simple-watcher]]
            [panoptic.utils :as u]))

;; ## Handlers for Directories

(defn- on-directory-change
  [set-key create-fn watch-fn f]
  (after-entity-handler 
    watch-fn 
    #(when-let [s (get %3 set-key)] 
       (doseq [e s]
         (f %1 %3 (create-fn (str (:path %3) "/" e)))))))

(defn- on-directory-flag
  [flag watch-fn f]
  (after-entity-handler
    watch-fn
    #(when (get %3 flag)
       (f %1 %2 %3))))

(def on-directory-create 
  "Run function when a watched directory is created. Parameters:
   - the watcher
   - the path to the created directory
   - the directory map"
  (partial on-directory-flag :created))

(def on-directory-delete 
  "Run function when a watched directory is deleted. Parameters:
   - the watcher
   - the path to the created directory
   - the directory map"
  (partial on-directory-flag :deleted))

(def on-subdirectory-create 
  "Run function when a watched directory has a new subdirectory created. Parameters:
   - the watcher
   - the parent directory map
   - the absolute path of the new directory"
  (partial on-directory-change :created-dirs identity))

(def on-subdirectory-delete 
  "Run function when a watched directory has a subdirectory deleted. Parameters:
   - the watcher
   - the parent directory map
   - the absolute path of the new directory"
  (partial on-directory-change :deleted-dirs identity))

(def on-directory-file-create 
  "Run function when a watched directory has a file created. Parameters:
   - the watcher
   - the parent directory map
   - the file map"
  (partial on-directory-change :created-files fs/file))

(def on-directory-file-delete 
  "Run function when a watched directory has a file deleted. Parameters:
   - the watcher
   - the parent directory map
   - the file map"
  (partial on-directory-change :deleted-files fs/file))

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
    (watch-fn 
      update-directory!
      (fn [m f]
        (let [[path created?] (if (string? f) [f nil] [(first f) true])]
          (when-let [ds (apply f/directories path opts)] 
            (let [ds (if created? (map f/set-directory-missing ds) ds)] 
              (reduce #(assoc %1 (:path %2) %2) m ds)))))
      (fn [m path]
        (when-let [ds (apply f/directories path opts)]
          (reduce #(dissoc %1 (:path %2)) m ds))))
    (on-directory-delete #(unwatch-entity! %1 (:path %3)))
    (on-subdirectory-create #(watch-entity! %1 [%3])) 
    (on-subdirectory-delete #(unwatch-entity! %1 %3))))

(defn- normal-directory-watcher
  [opts]
  (watch-fn
    update-directory!
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (assoc m (:path d) d)))
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (dissoc m (:path d) d)))))

(defn directory-watcher
  "Create directory watch function using the given options."
  [& {:keys [recursive] :as opts}]
  (let [opts (apply concat opts)]
    (if recursive
      (recursive-directory-watcher opts)
      (normal-directory-watcher opts))))  

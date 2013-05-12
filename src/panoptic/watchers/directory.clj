(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.data.directory :as f]
            [panoptic.utils :as u]))

;; ## DirectoryWatcher Protocol

(defprotocol DirectoryWatcher
  (on-directory-create [this f])
  (on-directory-delete [this f])
  (on-directory-file-create [this f])
  (on-directory-file-delete [this f])) 

;; ## Watching Directories

(defn- watch-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [d0]
  (if-let [d (f/refresh-directory d0)]
    (f/set-directory-diff d0 d)
    (f/set-directory-deleted d0)))

;; ## Simple Directory Watcher

(defn- wrap-directory-handler!
  "Add Handler that observes sets at the given map key in a directory map."
  [watcher k f]
  (wrap-entity-handler!
    watcher
    (fn [h]
      (fn [& [w _ {:keys [path] :as d} :as args]]
        (when h (apply h args))
        (when-let [s (get d k)]
          (when (seq s)
            (doseq [entity s]
              (f w d (str path "/" entity)))))))))

(deftype SimpleDirectoryWatcher [internal-watcher]
  Watcher
  (wrap-entity-handler! [this f] 
    (wrap-entity-handler! internal-watcher f)
    this)
  (start-watcher! [this] 
    (start-watcher! internal-watcher)
    this)
  (stop-watcher! [this] 
    (stop-watcher! internal-watcher))
  (watch-entities! [this es]
    (watch-entities! internal-watcher es)
    this)
  (unwatch-entities! [this es] 
    (unwatch-entities! internal-watcher es)
    this)
  (watched-entities [this]
    (watched-entities internal-watcher))

  DirectoryWatcher
  (on-directory-create [this f]
    (wrap-directory-handler! internal-watcher :created-dirs f)
    this)
  (on-directory-delete [this f]
    (wrap-directory-handler! internal-watcher :deleted-dirs f)
    this)
  (on-directory-file-create [this f]
    (wrap-directory-handler! internal-watcher :created-files f)
    this)
  (on-directory-file-delete [this f]
    (wrap-directory-handler! internal-watcher :deleted-files f)
    this)
  
  Object
  (toString [_]
    (.toString internal-watcher)))

(defn- create-recursive-directory-watcher
  [interval opts]
  "Create recursive directory watcher."
  (->
    (SimpleDirectoryWatcher.
      (simple-watcher 
        watch-directory 
        (fn [m path]
          (when-let [ds (apply f/directories path opts)]
            (reduce #(assoc %1 (:path %2) %2) m ds)))
        (or interval 1000)))
    (on-directory-create 
      (fn [w _ path]
        (watch-entity! w path))) 
    (on-directory-delete
      (fn [w _ path]
        (unwatch-entity! w path)))))

(defn- create-normal-directory-watcher
  "Create single-level directory watcher."
  [interval opts]
  (SimpleDirectoryWatcher.
    (simple-watcher 
      watch-directory 
      (fn [m path]
        (when-let [d (apply f/directory path opts)]
          (assoc m (:path d) d)))
      (or interval 1000))))

(defn simple-directory-watcher
  "Create single-threaded directory watcher."
  [directories & opts]
  (let [{:keys [interval recursive]} (apply hash-map opts)
        directories (if (string? directories) [directories] directories) 
        w (if recursive
            (create-recursive-directory-watcher interval opts)
            (create-normal-directory-watcher interval opts))]
    (watch-entities! w directories)))

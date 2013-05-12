(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.data.directory :as f]
            [panoptic.utils :as u]))

;; ## Handlers

(defn- on-changes
  "Add Handler that observes sets at the given map key in a directory map."
  [k watcher f]
  (wrap-entity-handler!
    watcher
    (fn [h]
      (fn [& [w _ {:keys [path] :as d} :as args]]
        (when h (apply h args))
        (when-let [s (get d k)]
          (when (seq s)
            (doseq [entity s]
              (f w d (str path "/" entity)))))))))

(def on-directory-create (partial on-changes :created-dirs))
(def on-directory-delete (partial on-changes :deleted-dirs))
(def on-directory-file-create (partial on-changes :created-files))
(def on-directory-file-delete (partial on-changes :deleted-files))

;; ## Watching Directories

(defn- watch-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [d0]
  (if-let [d (f/refresh-directory d0)]
    (f/set-directory-diff d0 d)
    (f/set-directory-deleted d0)))

(defn simple-directory-watcher
  "Create single-threaded directory watcher."
  [directories & {:keys [interval] :as opts}]
  (let [directories (if (string? directories) [directories] directories)]
    (-> (simple-watcher 
          watch-directory 
          (fn [m path]
            (when-let [d (apply f/directory path opts)]
              (assoc m (:path d) d)))
          (or interval 1000))
      (watch-entities! directories))))

(defn recursive-directory-watcher
  "Create recurisve directory watcher."
  [directories & opts]
  (let [directories (if (string? directories) [directories] directories)
        {:keys [interval]} (apply hash-map opts)]
    (-> (simple-watcher 
          watch-directory 
          (fn [m path]
            (when-let [ds (apply f/directories path opts)]
              (reduce #(assoc %1 (:path %2) %2) m ds)))
          (or interval 1000))
      (watch-entities! directories)
      (on-directory-create 
        (fn [w _ path]
          (watch-entity! w path)))
      (on-directory-delete
        (fn [w _ path]
          (unwatch-entity! w path))))))

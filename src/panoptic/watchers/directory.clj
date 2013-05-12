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

(defn watch-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [d0]
  (if-let [d (f/refresh-directory d0)]
    (f/set-directory-diff d0 d)
    (f/set-directory-deleted d0)))

(defn simple-directory-watcher
  "Create single-threaded directory watcher."
  [directories & {:keys [interval] :as opts}]
  (let [directories (if (string? directories) [directories] directories)]
    (-> (generic-watcher watch-directory #(apply f/directory % opts) (or interval 1000))
      (watch-entities! directories))))

(ns ^{:doc "Directory Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.directory
  (:use panoptic.watchers.core)
  (:require [clojure.set :as s :only [difference]]
            [panoptic.checkers :as c]
            [panoptic.data.directory :as f]
            [panoptic.watchers.simple :as sm :only [simple-watcher]]
            [panoptic.utils :as u]))

;; ## Handlers for Directories

(defn- wrap-directory-handler
  "Add Handler that observes sets at the given map key in a directory map."
  [k watch-fn f]
  (wrap-entity-handler
    watch-fn
    (fn [h]
      (fn [& [w _ {:keys [path] :as d} :as args]]
        (when h (apply h args))
        (when-let [s (get d k)]
          (when (seq s)
            (doseq [entity s]
              (f w d (str path "/" entity)))))))))

(def on-directory-create (partial wrap-directory-handler :created-dirs))
(def on-directory-delete (partial wrap-directory-handler :deleted-dirs))
(def on-directory-file-create (partial wrap-directory-handler :created-files))
(def on-directory-file-delete (partial wrap-directory-handler :deleted-files))

;; ## Watching Directories

(defn- update-directory!
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [d0]
  (if-let [d (f/refresh-directory d0)]
    (f/set-directory-diff d0 d)
    (f/set-directory-deleted d0)))

;; ## Directory Watcher

(defn recursive-directory-watch-fn
  "Create recursive directory watch function using the given options."
  [opts]
  (->
    (watch-fn 
      update-directory!
      (fn [m path]
        (when-let [ds (apply f/directories path opts)]
          (reduce #(assoc %1 (:path %2) %2) m ds)))
      (fn [m path]
        (when-let [ds (apply f/directories path opts)]
          (reduce #(dissoc %1 (:path %2)) m ds))))
    (on-directory-create 
      (fn [w _ path]
        (watch-entity! w path))) 
    (on-directory-delete
      (fn [w _ path]
        (unwatch-entity! w path)))))  

(defn directory-watch-fn
  "Create single-level directory watch function using the given options."
  [opts]
  (watch-fn
    update-directory!
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (assoc m (:path d) d)))
    (fn [m path]
      (when-let [d (apply f/directory path opts)]
        (dissoc m (:path d) d)))))

;; ## Simple Directory Watcher

(defn simple-directory-watcher*
  "Create single-threaded directory watcher."
  [directories & opts]
  (let [{:keys [interval recursive]} (apply hash-map opts)
        directories (if (string? directories) [directories] directories) 
        w (if recursive
            (recursive-directory-watch-fn opts)
            (directory-watch-fn opts))]
    (-> (sm/simple-watcher w interval)
      (watch-entities! directories))))

(defn simple-directory-watcher
  "Create simple, single-threaded directory watcher. Multiple directories may be given as a Seq,
   a single directory as a string. For options, see `simple-directory-watcher*`"
  [& args]
  (if (keyword? (first args))
    (apply simple-directory-watcher* nil args)
    (apply simple-directory-watcher* args)))

(ns ^{:doc "Simple, single-threaded Watch Runners."
      :author "Yannick Scherer"}
  panoptic.runners.simple-runner
  (:use [taoensso.timbre :only [debug info warn error]]
        panoptic.watchers.core
        panoptic.runners.core)
  (:require [panoptic.utils.core :as u])) 

;; ## Simple Runner
;;
;; The simple runner has its entities in a single atom. It runs a
;; standlone watcher as defined in `panoptic.runners.core`.

(defrunner SimpleRunner [id watch-fn interval entities-atom stop-atom threads-atom]
  (watch-entities!* [this es metadata]
    (swap! entities-atom #(add-entities watch-fn % es metadata))
    this)
  (unwatch-entities!* [this es metadata]
    (swap! entities-atom #(remove-entities watch-fn % es metadata))
    this) 
  (watched-entities [this]
    (read-watched-entities entities-atom))
  (start-watcher! [this]
    (swap! threads-atom #(or % (run-standalone-watcher! id this watch-fn nil interval entities-atom stop-atom)))
    (reset! stop-atom false)
    this)
  (stop-watcher! [this]
    (reset! stop-atom true)
    (let [ts (when-let [threads @threads-atom]
               (reset! threads-atom nil)
               threads)]
      (future (doseq [t ts] @t))))
  
  clojure.lang.IDeref
  (deref [_]
    (doseq [t @threads-atom] @t)))

;; ## Create Functions

(defn simple-runner
  "Create a generic, single-threaded WatchRunner."
  [watch-fn id interval] 
  (SimpleRunner. 
    (or id (generate-watcher-id)) watch-fn (or interval 1000) 
    (atom {}) (atom nil) (atom nil)))

(ns ^{:doc "Multi-Threaded Watch Runners"
      :author "Yannick Scherer"}
  panoptic.runners.multi-threaded
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.runners.core)
  (:require [panoptic.utils.core :as u]
            [panoptic.runners.simple :as sw])) 

(deftype MultiWatcher [watch-fn n interval]
  WatchRunner
  (watch-entities! [this es]
    ;; TODO 
    )
  (unwatch-entities! [this es]
    ;; TODO 
    )
  (watched-entities [this]
    ;; TODO 
    )
  (start-watcher! [this]
    ;; TODO 
    )
  (stop-watcher! [this]
    ;; TODO 
    )

  clojure.lang.IDeref
  (deref [_]
    ;; TODO
    )
  )

(ns ^{:doc "Multi-Threaded Watchers."
      :author "Yannick Scherer"}
  panoptic.watchers.multi-threaded-watchers
  (:use [clojure.tools.logging :only [debug info warn error]]
        panoptic.watchers.core)
  (:require [panoptic.utils :as u]
            [panoptic.watchers.simple-watchers :as sw])) 

(deftype MultiWatcher [watch-fn n interval]
  Watcher
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

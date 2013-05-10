(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core)

;; ## Watcher Protocol

(defprotocol Watcher
  "Protocol for Watcher."
  (start-watcher!* [this opts] "Start Watcher.")
  (stop-watcher! [this] "Stop Watcher."))

(defn start-watcher!
  [w & opts]
  (start-watcher!* w (apply hash-map opts)))

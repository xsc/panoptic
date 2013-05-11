(ns ^{:doc "Watcher Basics"
      :author "Yannick Scherer"}
  panoptic.watchers.core)

;; ## Watcher Protocol

(defprotocol Watcher
  "Protocol for Watcher."
  (start-watcher!* [this]
    "Start Watcher. Should return a function that stops the watcher."))

(defn start-watcher!
  [w]
  (start-watcher!* w))

(ns ^{:doc "Tests for generic Watchers."
      :author "Yannick Scherer"}
  panoptic.runners.generic-runner-test
  (:require [taoensso.timbre :as timbre])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.runners.core
        panoptic.runners.simple
        panoptic.runners.multi-threaded
        panoptic.utils.core))

(timbre/set-level! :warn)

(tabular 
  (fact "about generic runners"
    (let [a (atom 0)
          f (watch-fn (fn [_] @a) nil (constantly 0))
          w (?runner f 10)]
      (watch-entities! w [:a :b]) => w
      (watched-entities w) => { :a 0 :b 0 }
      (start-watcher! w) => w
      (reset! a 1) => 1
      (sleep 20) => anything
      (watched-entities w) => { :a 1 :b 1 }
      (reset! a 2) => 2
      (sleep 20) => anything
      (watch-entity! w :c) => w
      (sleep 20) => anything
      (watched-entities w) => { :a 2 :b 2 :c 2 }
      (let [stop  (stop-watcher! w)]
        stop => future?
        (deref stop) => falsey)))
  ?runner
  simple-watcher
  #_(multi-watcher %1 2 %2))

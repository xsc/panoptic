(ns ^{:doc "Tests for Generic Watchers."
      :author "Yannick Scherer"}
  panoptic.watchers.core-test
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.utils))

(fact "about generic watchers"
  (let [a (atom 0)
        w (generic-watcher (fn [_] @a) (constantly 0) 10)]
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

(ns ^{:doc "Tests for simple, generic Watchers."
      :author "Yannick Scherer"}
  panoptic.watchers.simple-test
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.watchers.simple
        panoptic.utils))

(fact "about generic watchers"
  (let [a (atom 0)
        f (watch-fn (fn [_] @a) #(assoc %1 %2 0) dissoc)
        w (simple-watcher f 10)]
    (watch-entities! w [:a :b]) => w
    (watched-entities w) => { :a 0 :b 0 }
    (start-watcher! w) => future?
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

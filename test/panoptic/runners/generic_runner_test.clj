(ns ^{:doc "Tests for generic Watchers."
      :author "Yannick Scherer"}
  panoptic.runners.generic-runner-test
  (:require [taoensso.timbre :as timbre])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.runners.core
        panoptic.runners.simple-runner
        panoptic.runners.multi-runner
        panoptic.utils.core))

(timbre/set-level! :warn)

          
(let [a (atom 0)
      f (watch-fn (fn [_] @a) nil (constantly 0))
      update-interval 10] 
  (tabular 
    (fact "about generic runners"
      (let [w ?runner]
        (reset! a 0)
        (watch-entities! w [:a :b]) => w
        (watched-entities w) => { :a 0 :b 0 }
        (start-watcher! w) => w
        (reset! a 1) => 1
        (sleep (* 2 update-interval))
        (watched-entities w) => { :a 1 :b 1 }
        (reset! a 2) => 2
        (sleep (* 2 update-interval))
        (watch-entity! w :c) => w
        (sleep (* 2 update-interval))
        (watched-entities w) => { :a 2 :b 2 :c 2 }
        (let [stop  (stop-watcher! w)]
          stop => future?
          (deref stop) => falsey)))
    ?runner
    (simple-runner f update-interval) 
    (multi-runner f :none 1 update-interval)))

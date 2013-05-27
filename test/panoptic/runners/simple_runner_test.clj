(ns ^{:doc "Tests for Simple Runners"
      :author "Yannick Scherer"}
  panoptic.runners.simple-runner-test
  (:require [taoensso.timbre :as timbre :only [set-level!]])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.runners.core
        panoptic.runners.simple-runner
        panoptic.utils.core))

(timbre/set-level! :warn)
          
(tabular
  (let [a (atom 0)
        f (watch-fn (fn [_] @a) nil (constantly 0))
        reset-a! #(do (reset! a %) (sleep (* 2 ?u)))]
    (fact "about simple runners"
      (let [w (simple-runner f :id ?u)]
        (reset-a! 0)
        (watch-entities! w [:a :b]) => truthy
        (watched-entities w) => { :a 0 :b 0 }
        (start-watcher! w)
        (reset-a! 1)
        (watched-entities w) => { :a 1 :b 1 }
        (watch-entity! w :c) => truthy
        (reset-a! 2)
        (watched-entities w) => { :a 2 :b 2 :c 2 }
        (unwatch-entities! w [:a :b :c]) => truthy
        (watched-entities w) => {}
        (let [stop (stop-watcher! w)]
          stop => future?
          @stop => falsey)))) 
    ?u 10)

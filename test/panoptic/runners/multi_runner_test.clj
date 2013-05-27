(ns ^{:doc "Tests for Multi Runners"
      :author "Yannick Scherer"}
  panoptic.runners.multi-runner-test
  (:require [taoensso.timbre :as timbre :only [set-level!]])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.runners.core
        panoptic.runners.multi-runner
        panoptic.utils.core))

(timbre/set-level! :warn)

(tabular
  (tabular
    (tabular
      (let [a (atom 0)
            f (watch-fn #(assoc % :v @a) nil (constantly {})) 
            w (multi-runner f :id ?distribute ?threads ?u)
            reset-a! #(do (reset! a %) (sleep (* 2 ?u)))
            es (take (* 2 ?threads) (map keyword (repeatedly gensym)))]
        (fact "about multi-runner"
          w => #(satisfies? WatchRunner %)
          (watched-entities w) => {}

          (watch-entities! w es) => truthy
          (let [ws (watched-entities w)]
            (count ws) => (count es)
            (vals ws) => #(every? empty? %))

          (start-watcher! w)

          (reset-a! 0)
          (let [ws (watched-entities w)]
            (count ws) => (count es)
            (map :v (vals ws)) => #(every? zero? %))

          (reset-a! 1)
          (let [ws (watched-entities w)]
            (count ws) => (count es)
            (map :v (vals ws)) => #(every? (fn [x] (= x 1)) %))

          (sleep (* ?threads ?u))
          (reset-a! 2)
          (let [ws (watched-entities w)]
            (count ws) => (count es)
            (map :v (vals ws)) => #(every? (fn [x] (= x 2)) %))

          (unwatch-entities! w es) => truthy
          (watched-entities w) => {}

          (let [stop (stop-watcher! w)]
            stop => future?
            @stop => falsey)))
      ?distribute :simple :fair :frequency)
    ?threads 1 2 4 8)
  ?u 10)

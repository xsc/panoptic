(ns ^ {:doc "Tests for File Watchers"
       :author "Yannick Scherer"}
  panoptic.watchers.file-watcher-test
  (:require [panoptic.utils.fs :as fs])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.watchers.file-watcher))

;; ## Mock Checksum

(def cs (atom nil))

;; ## Testing

(let [fw (file-watcher :checksum (fn [_] @cs))]
  
  (fact "about file-watcher's add/remove entity logic"
    (let [p (fs/absolute-path "some-file")]
      fw => #(satisfies? WatchFunction %)
      (add-entities fw {} ["some-file"]) => { p {:path p}}
      (remove-entities fw {p {:path p}} ["some-file"]) => {})) 

  (tabular 
    (fact "about file-watcher's updating logic"
      (reset! cs ?new-cs)
      (let [u (update-entity! fw nil "x" {:path "x" :checksum ?initial-cs :missing ?missing})]
        (:path u) => "x"
        (:checked u) => pos?
        (:checksum u) => ?new-cs
        (map #(get u %) ?flags-set) => #(every? truthy %)
        (map #(get u %) ?flags-unset) => #(every? falsey %)))
    ?initial-cs      ?new-cs     ?missing   ?flags-set       ?flags-unset
    nil              nil         nil        [:missing]      [:created :deleted :modified] 
    nil              "abc"       nil        []              [:created :deleted :modified :missing] 
    nil              "abc"       true       [:created]      [:missing :deleted :modified] 
    "abc"            nil         nil        [:deleted]      [:created :missing :modified] 
    "abc"            "def"       nil        [:modified]     [:created :missing :deleted] 
    "abc"            "abc"       nil        []              [:created :missing :deleted :deleted]) 

  (tabular
    (fact "about file-watcher's handler logic"
      (let [a (atom nil)
            fw (-> fw
                 (on-file-create (fn [& _] (reset! a :create)))
                 (on-file-modify (fn [& _] (reset! a :modify)))
                 (on-file-delete (fn [& _] (reset! a :delete))))]
        (reset! cs ?new-cs)
        (let [u (update-entity! fw nil "x" {:path "x" :checksum ?initial-cs :missing ?missing})]
          (run-entity-handler! fw nil "x" u) => anything
          @a => ?a)))
    ?initial-cs      ?new-cs     ?missing   ?a
    nil              nil         nil        nil
    nil              "abc"       nil        nil
    nil              "abc"       true       :create
    "abc"            nil         nil        :delete
    "abc"            "def"       nil        :modify
    "abc"            "abc"       nil        nil)) 

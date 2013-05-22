(ns ^ {:doc "Tests for Directory Watchers"
       :author "Yannick Scherer"}
  panoptic.watchers.directory-watcher-test
  (:require [panoptic.utils.fs :as fs]
            [clojure.set :as set :only [difference]])
  (:use midje.sweet
        panoptic.watchers.core
        panoptic.watchers.file-watcher
        panoptic.watchers.directory-watcher))

(let [refreshment (atom nil)
      dw (directory-watcher :refresh #(when-let [r @refreshment] (merge %1 r)))]
  
  (fact "about directory-watcher's add/remove logic"
    (let [p (fs/absolute-path "some-file")]
      dw => #(satisfies? WatchFunction %)
      (let [r (add-entities dw {} ["some-file"])]
        (count r) => 1
        (:path (get r p)) => p
        (:missing (get r p)) => true
        (:opts (get r p)) => (contains [:refresh fn?]))
      (remove-entities dw {p {:path p}} ["some-file"]) => {}))

  (let [c (+ 3 (rand-int 10))
        s (repeatedly #(str (gensym "f")))
        f (take c s)]
    (tabular 
      (tabular
        (fact "about directory-watcher's updating logic (child entities)"
          (reset! refreshment 
                  {:directories (set ?new-directories) 
                   :files (set ?new-files)})
          (let [u (update-entity! dw nil "x" {:path "x" :directories (set ?old-directories) :files (set ?old-files)})]
            (:path u) => "x"
            (set (:created-dirs u))  => (set/difference (set ?new-directories) (set ?old-directories))
            (set (:deleted-dirs u))  => (set/difference (set ?old-directories) (set ?new-directories))
            (set (:created-files u)) => (set/difference (set ?new-files) (set ?old-files))
            (set (:deleted-files u)) => (set/difference (set ?old-files) (set ?new-files))))
        ?old-directories ?old-files ?new-directories ?new-files
        nil              nil        nil              nil
        ?x               nil        nil              nil
        nil              ?x         nil              nil
        nil              nil        ?x               nil
        nil              nil        nil              ?x
        ?x               ?x         nil              nil
        nil              ?x         ?x               nil
        nil              nil        ?x               ?x
        ?x               nil        nil              ?x
        ?x               nil        ?x               nil
        nil              ?x         nil              ?x
        nil              ?x         ?x               ?x
        ?x               nil        ?x               ?x
        ?x               ?x         nil              ?x
        ?x               ?x         ?x               nil
        ?x               ?x         ?x               ?x)
      ?x f))
  
  (tabular
    (fact "about directory-watcher's updating logic (root entities)"
      (reset! refreshment (when ?exists {}))
      (let [u (update-entity! dw nil "x" {:path "x" ?k true})]
        (:path u) => "x"
        (:missing u) => ?missing
        (:created u) => ?created
        (:deleted u) => ?deleted))
    ?k         ?exists     ?missing ?created ?deleted
    nil        false       falsey   falsey    truthy
    :created   false       falsey   falsey    truthy
    :deleted   false       truthy   falsey    falsey
    :missing   false       truthy   falsey    falsey

    nil        true        falsey   falsey    falsey
    :created   true        falsey   falsey    falsey
    :missing   true        falsey   truthy    falsey
    :deleted   true        falsey   truthy    falsey)
  
  (let [a (atom [])
        dw (-> dw
             (on-file-create (fn [& _] (swap! a conj :file-created)))
             (on-file-delete (fn [& _] (swap! a conj :file-deleted)))
             (on-directory-create (fn [& _] (swap! a conj :dir-created)))
             (on-directory-delete (fn [& _] (swap! a conj :dir-deleted))))]
    (tabular
      (tabular
        (fact "about directory-watcher's handler logic"
          (reset! a [])
          (reset! refreshment 
                  {:directories (set ?new-directories) 
                   :files (set ?new-files)})
          (let [u (update-entity! dw nil "x" {:path "x" :directories (set ?old-directories) :files (set ?old-files)})] 
            (run-entity-handler! dw nil "x" u) => anything
            (:path u) => "x"
            (set @a) => #(every? (partial contains? %) ?a)))
        ?old-directories ?old-files ?new-directories ?new-files ?a
        nil              nil        nil              nil        []
        ?x               nil        nil              nil        [:dir-deleted]
        nil              ?x         nil              nil        [:file-deleted]        
        nil              nil        ?x               nil        [:dir-created]
        nil              nil        nil              ?x         [:file-created]
        ?x               ?x         nil              nil        [:file-deleted :dir-deleted]
        nil              ?x         ?x               nil        [:file-deleted :dir-created]
        nil              nil        ?x               ?x         [:file-created :dir-created]
        ?x               nil        nil              ?x         [:file-created :dir-deleted]
        ?x               nil        ?x               nil        []
        nil              ?x         nil              ?x         []
        nil              ?x         ?x               ?x         [:dir-created]
        ?x               nil        ?x               ?x         [:file-created]
        ?x               ?x         nil              ?x         [:dir-deleted]
        ?x               ?x         ?x               nil        [:file-deleted]
        ?x               ?x         ?x               ?x         []) 
      ?x ["y"])))

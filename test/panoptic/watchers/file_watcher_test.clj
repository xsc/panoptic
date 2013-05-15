(ns ^{:doc "Tests for File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file-watcher-test
  (:require [me.raynes.fs :as fs])
  (:use midje.sweet
        panoptic.watchers.simple-watchers
        panoptic.watchers.multi-threaded-watchers
        panoptic.watchers.file
        panoptic.watchers.core
        panoptic.checkers
        panoptic.utils))

;; ## Data

(def f1 (fs/temp-file "panoptic-"))
(def f2 (fs/temp-file "panoptic-"))
(def p1 (fs/absolute-path f1))
(def p2 (fs/absolute-path f2))

;; ## Helper Macro

(defmacro wh
  "Run forms, wait until condition is true, then dereference the atom at the var `changes`."
  [until? & forms]
  `(do 
     ~@forms 
     (loop [] (when-not ~until? (recur)))
     (sleep 400)
     (deref ~'changes)))

(defn slp 
  [n]
  (sleep n)
  true)

;; ## Tests

(tabular
  (tabular
    (with-state-changes [(before :facts (do (fs/delete f1) (fs/delete f2)))
                         (after :facts (do (fs/delete f1) (fs/delete f2)))]
      (fact :slow "about checksum file watchers" 
        (let [changes (atom [])
              fw (-> (file-watcher :checker ?checksum)
                   (on-create #(swap! changes conj [:create (:path %3)]))
                   (on-modify #(swap! changes conj [:modify (:path %3)]))
                   (on-delete #(swap! changes conj [:delete (:path %3)]))
                   (?start [p1 p2] :interval 30))]
          fw => #(satisfies? Watcher %)
          @changes => []
          (wh (fs/exists? f1) (fs/touch f1)) => (just [[:create p1]])
          (wh (fs/exists? f2) (fs/touch f2)) => (just [[:create p1] [:create p2]])
          (reset! changes [])
          (wh (slp 50) (fs/touch f1)) => (just [])
          (wh (slp 50) (fs/touch f2)) => (just [])
          (wh (= (slurp p1) "text") (spit p1 "text")) => (just [[:modify p1]])
          (wh (= (slurp p2) "text") (spit p2 "text")) => (just [[:modify p1] [:modify p2]])
          (reset! changes [])
          (wh (slp 50) (spit p1 "text")) => (just [])
          (wh (slp 50) (spit p2 "text")) => (just [])
          (wh (= (slurp p1) "text2") (spit p1 "text2")) => (just [[:modify p1]])
          (wh (= (slurp p2) "text2") (spit p2 "text2")) => (just [[:modify p1] [:modify p2]])
          (reset! changes [])
          (wh (not (fs/exists? f1)) (fs/delete f1)) => (just [[:delete p1]])
          (wh (not (fs/exists? f2)) (fs/delete f2)) => (just [[:delete p1] [:delete p2]])
          @(stop-watcher! fw) => anything))) 
    ?checksum md5 sha1 sha256)
  ?start 
  start-simple-watcher!)

(tabular
  (with-state-changes [(before :facts (do (fs/delete f1) (fs/delete f2)))
                       (after :facts (do (fs/delete f1) (fs/delete f2)))]
    (fact :slow "about timestamp file watchers"
      (let [changes (atom [])
            fw (-> (file-watcher :checker last-modified)
                 (on-create #(swap! changes conj [:create (:path %3)]))
                 (on-modify #(swap! changes conj [:modify (:path %3)]))
                 (on-delete #(swap! changes conj [:delete (:path %3)]))
                 (?start [p1 p2] :interval 30))]
        fw => #(satisfies? Watcher %)
        @changes => []
        (wh (fs/exists? p1) (fs/touch p1)) => (just [[:create p1]])
        (wh (fs/exists? p2) (fs/touch p2)) => (just [[:create p1] [:create p2]])
        (reset! changes [])
        (sleep 1500) ;; because last-modified only gives seconds
        (wh (slp 50) (fs/touch p1)) => (just [[:modify p1]])
        (wh (slp 50) (fs/touch p2)) => (just [[:modify p1] [:modify p2]])
        (reset! changes [])
        (sleep 1500)
        (wh (slp 50) (fs/touch p1)) => (just [[:modify p1]])
        (wh (slp 50) (fs/touch p2)) => (just [[:modify p1] [:modify p2]])
        @(stop-watcher! fw) => anything))) 
  ?start 
  start-simple-watcher!)

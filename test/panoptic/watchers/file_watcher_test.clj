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

(defmacro w-sleep
  "Run forms, sleep 500ms, then dereference the atom given as first parameter."
  [a & forms]
  `(do ~@forms (sleep 500) (deref ~a)))

;; ## Tests

(tabular
  (tabular
    (with-state-changes [(before :facts (do (fs/delete f1) (fs/delete f2)))
                         (after :facts (do (fs/delete f1) (fs/delete f2)))]
      (fact "about checksum file watchers"
        (let [changes (atom [])
              fw (-> (file-watcher :checker ?checksum)
                   (on-create #(swap! changes conj [:create (:path %3)]))
                   (on-modify #(swap! changes conj [:modify (:path %3)]))
                   (on-delete #(swap! changes conj [:delete (:path %3)]))
                   (?start [p1 p2] :interval 50))]
          fw => #(satisfies? Watcher %)
          @changes => []
          (w-sleep changes (fs/touch f1)) => (just [[:create p1]])
          (w-sleep changes (fs/touch f2)) => (just [[:create p1] [:create p2]])
          (reset! changes [])
          (w-sleep changes (fs/touch f1)) => (just [])
          (w-sleep changes (fs/touch f2)) => (just [])
          (w-sleep changes (spit p1 "text")) => (just [[:modify p1]])
          (w-sleep changes (spit p2 "text")) => (just [[:modify p1] [:modify p2]])
          (reset! changes [])
          (w-sleep changes (spit p1 "text")) => (just [])
          (w-sleep changes (spit p2 "text")) => (just [])
          (w-sleep changes (spit p1 "text2")) => (just [[:modify p1]])
          (w-sleep changes (spit p2 "text2")) => (just [[:modify p1] [:modify p2]])
          (reset! changes [])
          (w-sleep changes (fs/delete f1)) => (just [[:delete p1]])
          (w-sleep changes (fs/delete f2)) => (just [[:delete p1] [:delete p2]])
          @(stop-watcher! fw) => anything))) 
    ?checksum md5 sha1 sha256)
  ?start 
  start-simple-watcher!)

(tabular
  (with-state-changes [(before :facts (do (fs/delete f1) (fs/delete f2)))
                       (after :facts (do (fs/delete f1) (fs/delete f2)))]
    (fact "about timestamp file watchers"
      (let [changes (atom [])
            fw (-> (file-watcher :checker last-modified)
                 (on-create #(swap! changes conj [:create (:path %3)]))
                 (on-modify #(swap! changes conj [:modify (:path %3)]))
                 (on-delete #(swap! changes conj [:delete (:path %3)]))
                 (?start [p1 p2] :interval 50))]
        fw => #(satisfies? Watcher %)
        @changes => []
        (w-sleep changes (fs/touch p1)) => (just [[:create p1]])
        (w-sleep changes (fs/touch p2)) => (just [[:create p1] [:create p2]])
        (reset! changes [])
        (sleep 1500) ;; because last-modified only gives seconds
        (w-sleep changes (fs/touch p1)) => (just [[:modify p1]])
        (w-sleep changes (fs/touch p2)) => (just [[:modify p1] [:modify p2]])
        (reset! changes [])
        (sleep 1500)
        (w-sleep changes (fs/touch p1)) => (just [[:modify p1]])
        (w-sleep changes (fs/touch p2)) => (just [[:modify p1] [:modify p2]])
        @(stop-watcher! fw) => anything))) 
  ?start 
  start-simple-watcher!)

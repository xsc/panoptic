(ns ^ {:doc "Tests for Distributors"
       :author "Yannick Scherer"}
  panoptic.runners.distributors-test
  (:use midje.sweet
        panoptic.data.core
        panoptic.runners.distributors))

(def data-to-distribute
  {:a (ref { :data 0 })
   :b (ref { :data 1 })
   :c (ref { :data 2 })
   :d (ref { :data 3 })})

(let [sd (create-distributor :simple 200 4)]
  (fact "about simple distributor"
    sd => #(satisfies? Distributor %)
    (map #(thread-offset sd %) (range 4)) => [nil 50 100 150]
    (map #(thread-interval sd %) (range 4)) => [200 200 200 200]
    (distribute sd data-to-distribute {:type :periodic}) => nil
    (distribute sd data-to-distribute {:type :modify}) => nil
    (distribute sd data-to-distribute {:type :init}) => (repeat 4 data-to-distribute)
    (distribute sd data-to-distribute {:type :add}) => (repeat 4 data-to-distribute)
    (distribute sd data-to-distribute {:type :remove}) => (repeat 4 data-to-distribute)))

(let [sd (create-distributor :fair 200 4)
      ex (map #(into {} %) (partition 1 data-to-distribute))]
  (fact "about fair distributor"
    sd => #(satisfies? Distributor %)
    (map #(thread-offset sd %) (range 4)) => [nil nil nil nil]
    (map #(thread-interval sd %) (range 4)) => [200 200 200 200]
    (distribute sd data-to-distribute {:type :periodic}) => nil
    (distribute sd data-to-distribute {:type :modify}) => nil
    (distribute sd data-to-distribute {:type :init}) => ex
    (distribute sd data-to-distribute {:type :add}) => ex
    (distribute sd data-to-distribute {:type :remove}) => ex))

(let [sd (create-distributor :frequency 20 4)
      ex [data-to-distribute {} {} {}]]
  (fact "about frequency distributor"
    sd => #(satisfies? Distributor %)
    (map #(thread-offset sd %) (range 4)) => [nil nil nil nil]
    (map #(thread-interval sd %) (range 4)) => [20 40 80 160]
    (distribute sd data-to-distribute {:type :periodic}) => ex
    (distribute sd data-to-distribute {:type :modify}) => ex
    (distribute sd data-to-distribute {:type :init}) => ex
    (distribute sd data-to-distribute {:type :add}) => ex
    (distribute sd data-to-distribute {:type :remove}) => ex))

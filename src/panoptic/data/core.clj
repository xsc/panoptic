(ns ^ {:doc "Basics for Panoptic Data"
       :author "Yannick Scherer"}
  panoptic.data.core
  (:require [panoptic.utils.core :as u]
            [clojure.set :as s :only [difference]]))

;; ## Concept
;;
;; Panoptic operates on simple Clojure maps. It can thus set
;; change information easily.

;; ## Generic Data

(defn unix-timestamp
  "Get Unix Timestamp in Milliseconds"
  []
  (System/currentTimeMillis))

(defn update-timestamp!
  "Update timestamp in map."
  [m] 
  (assoc m ::timestamp (unix-timestamp)))

(defn set-checksum
  "Set Checksum in Entity Map."
  [m cs]
  (-> m
    (assoc ::checksum cs)
    (update-timestamp!)))

(defn checksum
  [m]
  (::checksum m))

(defn clear-checksum
  [m]
  (-> m
    (dissoc ::checksum)
    (update-timestamp!)))

;; ## Change Types

(defmacro ^:private def-change
  [id]
  (let [k (keyword id)
        get-fn (symbol (str id "?"))
        set-fn (symbol (str "set-" id))]
    `(do
       (defn ~get-fn
         [m#]
         (= ~k (get m# ::state)))
       (defn ~set-fn
         ([m#] (~set-fn m# nil))
         ([m# cs#]
          (let [m# (assoc m# ::state ~k)]
            (if cs#
              (set-checksum m# cs#)
              (clear-checksum m#))))))))

(def-change created)
(def-change modified)
(def-change deleted)
(def-change missing)

(defn set-untouched
  "Clear state in given Entity Map."
  [m]
  (-> m
    (dissoc m ::state)
    (update-timestamp!)))

(defn untouched?
  "Check if the given Entity has no Change Information attached to it."
  [m]
  (boolean (not (get m ::state))))

(defn exists?
  [m]
  (not (or (missing? m) (deleted? m))))

;; ## Child Data

(defn add-children
  "Add Children to Entity Map."
  [m k cs]
  (update-in m [::children k] (comp set concat) cs))

(defn add-child
  "Add single Child to Entity Map."
  [m k c]
  (add-children m k [c]))

(defn children
  "Get children from Entity Map."
  [m]
  (get m ::children))

(defn set-children-diff
  "Create map describing the difference between an old and a new entity state's children."
  [m0 m1]
  (assoc-in (or m1 m0) [::children ::diff]
            (let [c0 (children m0)
                  c1 (children m1)
                  ks (distinct (concat (keys c0) (keys c1)))]
              (reduce
                (fn [r k]
                  (let [old-children (set (get c0 k))
                        new-children (set (get c1 k))]
                    (assoc r k {:created (s/difference new-children old-children)
                                :deleted (s/difference old-children new-children)})))
                {} ks))))

(defn children-diff
  "Get diff map from entity."
  [m]
  (get-in m [::children ::diff]))

(defn clear-children-diff
  "Remove diff map from entity."
  [m]
  (update-in m [::children] dissoc ::diff))

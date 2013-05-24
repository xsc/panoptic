(ns ^{:doc "Utility Functions"
      :author "Yannick Scherer"}
  panoptic.utils.core)

(defn unix-timestamp
  "Get Unix Timestamp in Milliseconds"
  []
  (System/currentTimeMillis))

(defn match?
  "Check if the given String matches the given Pattern."
  [p s]
  (cond (instance? java.util.regex.Pattern p) (re-matches p s)
        (keyword? p) (= (name p) s)
        :else (= p s)))

(defn match-some?
  "Check if the given String matches one of the given patterns."
  [ps s]
  (some (fn [i] (match? i s)) ps))

(defn sleep
  "Sleep the given number of Milliseconds."
  [interval]
  (try
    (Thread/sleep interval)
    (catch Exception _ nil)))


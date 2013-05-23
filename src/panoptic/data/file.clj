(ns ^{:doc "File Representation"
      :author "Yannick Scherer"}
  panoptic.data.file
  (:require [panoptic.utils.fs :as fs :only [absolute-path]]
            [panoptic.utils.core :as u]))

;; ## File Map
;;
;; A file is represented as a map with only one mandatory field, namely `:path` containing
;; a fully-qualified path to a file. 

(defn file
  "Create new File Map using the given Path."
  [path]
  (-> {}
    (assoc :path (fs/absolute-path path))))

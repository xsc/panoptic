(ns ^{:doc "Directory Representation"
      :author "Yannick Scherer"}
  panoptic.data.directory
  (:require [panoptic.utils.fs :as fs]
            [panoptic.utils.core :as u]
            [panoptic.data.core :as data]
            [clojure.set :as s :only [difference]]))

;; ## Directory Map

(defn- create-include-function
  [exclude precondition]
  (let [patterns (seq exclude)]
    (cond (not (or patterns precondition)) (constantly true)
          (and patterns precondition) #(and (precondition %) (not (u/match-some? patterns %)))
          (and (not patterns) precondition) precondition
          :else #(not (u/match-some? patterns %)))) )

(defn directory
  "Create directory map from directory path and additional options."
  [path & opts ] 
  (let [f (fs/file path) 
        {:keys [extensions include-hidden exclude exclude-dirs exclude-files]} (apply hash-map opts)
        include? (create-include-function exclude nil)
        include-file? (create-include-function exclude-files include?)
        include-dir? (create-include-function exclude-dirs include?)
        valid-extension? (if-not (seq extensions)
                           (constantly true)
                           (let [ext (set (map #(if (keyword? %) (name %) (str %)) extensions))]
                             #(contains? ext (fs/extension %))))]
    (when-not (fs/file? f) 
      (let [path (fs/absolute-path f)]
        (-> (if (fs/exists? f) {} (-> {} (data/set-missing)))
          (assoc :path path)
          (assoc :opts opts)
          (data/add-children :files 
                             (->> (fs/list-files f)
                               (filter #(or include-hidden (not (fs/hidden? %))))
                               (filter valid-extension?)
                               (filter include-file?)))
          (data/add-children :directories 
                 (->> (fs/list-directories f)
                   (filter #(or include-hidden (not (fs/hidden? %))))
                   (filter include-dir?))))))))

(defn refresh-directory
  "Create current state of given Directory."
  [{:keys [path opts]}]
  (when (fs/directory-exists? path)
    (apply directory path opts)))

(defn directories
  "Create seq of directories by recursively traversing the directory tree starting at
   the given root path."
  [root-path & opts]
  (when-let [{:keys [path] :as d} (apply directory root-path opts)] 
    (let [dirs (:directories d)] 
      (if-not (seq dirs)
        [d]
        (cons d (mapcat #(apply directories (str path "/" %) opts) dirs))))))

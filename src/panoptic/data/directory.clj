(ns ^{:doc "Directory Representation"
      :author "Yannick Scherer"}
  panoptic.data.directory
  (:require [me.raynes.fs :as fs]
            [clojure.set :as s :only [difference]]
            [panoptic.utils :as u]))

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
                           (let [ext (set (map #(str "." (if (keyword? %) (name %) (str %))) extensions))]
                             #(contains? ext (fs/extension %))))]
    (when-not (fs/file? f) 
      (let [path (fs/absolute-path f)
            children (when (fs/exists? f) (fs/list-dir f))]
        (-> (if (fs/exists? f) {} {:missing true})
          (assoc :path path)
          (assoc :opts opts)
          (assoc :files 
                 (->> children
                   (filter #(fs/file? (str path "/" %)))
                   (filter valid-extension?)
                   (filter include-file?)
                   (set)))
          (assoc :directories 
                 (->> children
                   (filter #(fs/directory? (str path "/" %)))
                   (filter #(or include-hidden (not (.startsWith ^String % "."))))
                   (filter include-dir?)
                   (set))))))))

(defn refresh-directory
  "Create current state of given Directory."
  [{:keys [path opts]}]
  (when (and (fs/directory? path) (fs/exists? path))
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

;; ## Directory Checking

(defn set-directory-created
  [dir]
  (-> dir
    (dissoc :deleted :missing)
    (assoc :created true)
    (u/update-timestamp :checked)))

(defn set-directory-deleted
  "Set `:deleted` data in directory map."
  [dir]
  (-> dir
    (dissoc :created :missing)
    (assoc :deleted true)
    (u/update-timestamp :checked)))

(defn set-directory-missing
  [dir]
  (-> dir
    (dissoc :created :deleted)
    (assoc :missing true)
    (u/update-timestamp :checked)))

(defn set-directory-untouched
  [dir]
  (-> dir
    (dissoc :created :deleted :missing)
    (u/update-timestamp :checked)))

(defn set-directory-diff
  [old-dir new-dir]
  (let [old-files (:files old-dir)
        old-directories (:directories old-dir)
        new-files (:files new-dir)
        new-directories (:directories new-dir)
        created-files (s/difference new-files old-files)
        created-directories (s/difference new-directories old-directories)
        deleted-files (s/difference old-files new-files)
        deleted-directories (s/difference old-directories new-directories)]
    (-> new-dir
      (assoc :created-files created-files)
      (assoc :deleted-files deleted-files)
      (assoc :created-dirs created-directories)
      (assoc :deleted-dirs deleted-directories)
      (u/update-timestamp :checked))))

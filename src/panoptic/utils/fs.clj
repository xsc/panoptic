(ns ^ {:doc "File System Utilities"
       :author "Yannick Scherer"}
  panoptic.utils.fs
  (:refer-clojure :exclude [name file-seq])
  (:require [clojure.java.io :as io])
  (:import [java.io File]))

;; ## Base

(defn file
  "Create `java.io.File` object from the given entity."
  ^File
  [path]
  (io/file path))

;; ## Macro Helper

(defmacro ^:private def-file-fn
  "Define Function that calls the method given as last parameter, then
   applying the functions given before the method in reverse order. E.g.:
 
     (def-file-fn path-vec vec seq listFiles)
 
   This creates a wrapper for the call: `(vec (seq (.listFiles (file path))))`"
  [id & calls]
  (when-not (seq calls)
    (throw (Exception. "expects at least the method to be called")))
  `(defn ~id
     ~(str "Wrapper around `java.io.File/" (last calls) "`.")
     [~'path]
     (->
       (. (file ~'path) ~(last calls))
       ~@(reverse (butlast calls)))))

;; ## Simple Wrappers

(def-file-fn exists? exists)
(def-file-fn file? isFile)
(def-file-fn directory? isDirectory)
(def-file-fn absolute-path getCanonicalPath)
(def-file-fn path-seq seq list) 
(def-file-fn file-seq seq listFiles) 

;; ## Compound Functions

(defn file-exists?
  "Check if the given path points to an existing file."
  [path]
  (let [f (file path)]
    (and (exists? f) (file? f)))) 

(defn directory-exists?
  "Check if the given path points to an existing directory."
  [path]
  (let [f (file path)]
    (and (exists? f) (directory? f))))

(defn list-files
  "List all files in the directory denoted by the given path."
  [path]
  (->>
    (file-seq path)
    (keep
      (fn [^File f]
        (when (.isFile f)
          (.getName f))))))

(defn list-directories
  "List all directories in the directory denoted by the given path."
  [path]
   (->>
    (file-seq path)
    (keep
      (fn [^File f]
        (when (.isDirectory f)
          (.getName f))))))

(defn list-files-absolute
  "List files in the directory denoted by the given path, using their absolute paths."
  [path]
  (->>
    (file-seq path)
    (keep
      (fn [^File f]
        (when (.isFile f)
          (.getCanonicalPath f))))))

(defn list-directories-absolute
  "List files in the directory denoted by the given path, using their absolute paths."
  [path]
  (->>
    (file-seq path)
    (keep
      (fn [^File f]
        (when (.isDirectory f)
          (.getCanonicalPath f))))))

(defn list-directories-recursive
  [path]
  (let [child-dirs (list-directories-absolute path)]
    (concat child-dirs (mapcat list-directories-recursive child-dirs))))

(defn last-modified
  "Get time of last modification in milliseconds since epoch."
  [path]
  (let [f (file path)]
    (when (.exists f)
      (.lastModified f))))

;; ## Additional Functionality

(defn extension
  "Get extension part of the given file path."
  ^String
  [^String path]
  (let [n (.getName (file path))
        i (.lastIndexOf n ".")]
    (when (and (> i 0) (< (inc i) (count n))) 
      (subs n (inc i)))))

(defn name
  "Get name part of the given file path."
  ^String
  [^String path]
  (let [n (.getName (file path))
        i (.lastIndexOf n ".")]
    (cond (zero? i) n
          (pos? i) (subs n 0 i)
          :else nil)))

(defn hidden?
  "Does the given path denote a hidden (prefixed with `.`) file?"
  [^String path]
  (.startsWith (.getName (file path)) "."))

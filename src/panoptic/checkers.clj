(ns ^{:doc "File Checkers"
      :author "Yannick Scherer"}
  panoptic.checkers
  (:require [me.raynes.fs :as fs :only [file mod-time exists?]]
            [pandect.core :as cs :only [md5 sha1 sha256]]))

;; ## Protocol

(defprotocol FileChecker
  "Protocol for File Checking Strategies."
  (file-checksum [this file]
    "Create Checksum of File. This can be any value comparable using `=`. If the file
     does not exist (any more), return `nil`."))

(extend-type clojure.lang.AFunction
  FileChecker
  (file-checksum [f file]
    (f file)))

;; ## Last-Modified Checker

(defn last-modified
  "Checker that returns the time of the last modification for a file."
  [path]
  (when (and (fs/exists? path) (fs/file? path)) 
    (fs/mod-time path)))

;; ## Checksum Checkers

(defn- create-digest-fn
  "Create Checker that applies the given function on a File object
   created from the given path."
  [f]
  (fn [path]
    (when (fs/exists? path)
      (f (fs/file path)))))

(def md5 
  "Checker that returns the MD5 checksum of a file."
  (create-digest-fn cs/md5))

(def sha1 
  "Checker that returns the SHA-1 checksum of a file."
  (create-digest-fn cs/sha1))

(def sha256 
  "Checker that returns the SHA-265 checksum of a file."
  (create-digest-fn cs/sha256))

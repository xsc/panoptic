(ns ^{:doc "File Checkers"
      :author "Yannick Scherer"}
  panoptic.checkers
  (:require [me.raynes.fs :as fs :only [file mod-time exists?]]
            [digest :as cs :only [md5 sha-1 sha-265]]))

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

(defn by-modification-time
  "Checker that returns the time of the last modification for a file."
  [path]
  (when (fs/exists? path)
    (fs/mod-time path)))

;; ## Checksum Checkers

(defn- create-digest-fn
  "Create Checker that applies the given function on a File object
   created from the given path."
  [f]
  (fn [path]
    (f (fs/file path))))

(def by-md5-checksum 
  "Checker that returns the MD5 checksum of a file."
  (create-digest-fn cs/md5))

(def by-sha1-checksum 
  "Checker that returns the SHA-1 checksum of a file."
  (create-digest-fn cs/sha-1))

(def by-sha256-checksum 
  "Checker that returns the SHA-265 checksum of a file."
  (create-digest-fn cs/sha-256))

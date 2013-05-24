(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file-watcher
  (:use panoptic.watchers.core
        [taoensso.timbre :only [error info]])
  (:require [panoptic.data.file :as f]
            [panoptic.data.core :as data]
            [panoptic.utils.fs :as fs :only [file-exists? last-modified]]
            [pandect.core :as cs]))

;; ## Protocol

(defprotocol FileEntityHandlers
  (on-file-create [this f])
  (on-file-modify [this f])
  (on-file-delete [this f]))

;; ## Checksums

(defmulti checksum-fn 
  "Get function used to compute file checksum."
  (fn [k] 
    (if (fn? k)
      ::fn
      k))
  :default :crc32)

(defmethod checksum-fn ::fn
  [f]
  f)

(defn- wrap-checksum
  [f]
  #(when (fs/exists? %) (f %)))

(defmethod checksum-fn :last-modified [_] (wrap-checksum fs/last-modified))
(defmethod checksum-fn :crc32 [_] (wrap-checksum cs/crc32-file))
(defmethod checksum-fn :adler32 [_] (wrap-checksum cs/adler32-file))
(defmethod checksum-fn :md5 [_] (wrap-checksum cs/md5-file))
(defmethod checksum-fn :sha1 [_] (wrap-checksum cs/sha1-file))
(defmethod checksum-fn :sha256 [_] (wrap-checksum cs/sha256-file))
(defmethod checksum-fn :sha512 [_] (wrap-checksum cs/sha512-file))

;; ## Logic

(defn- update-file!
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map."
  [checker {:keys [path] :as f}]
  (try
    (let [chk0 (data/checksum f)
          chk1 (checker path)]
      (condp = [chk0 chk1]
        [nil  nil]  (data/set-missing f)
        [chk0 chk0] (data/set-untouched f)
        [nil  chk1] (let [f-new (data/set-created f chk1)]
                      (if (not (data/exists? f)) 
                        f-new
                        (data/set-untouched f-new)))
        [chk0 nil]  (data/set-deleted f)
        (data/set-modified f chk1)))
    (catch Exception ex
      (error ex "in file checker for file" path)
      (data/set-untouched f))))

;; ## File Watcher

(defwatch file-watcher*
  FileEntityHandlers
  (on-file-create [this f] (on-entity-create this f))
  (on-file-modify [this f] (on-entity-modify this f))
  (on-file-delete [this f] (on-entity-delete this f)))

(defn file-watcher
  "Create WatchFn for Files."
  [& {:keys [checksum]}] 
  (let [checker (or (checksum-fn checksum) cs/crc32)]
    (file-watcher*
      #(update-file! checker %)
      #(vector (fs/absolute-path %))
      (fn [p _] (f/file p)))))

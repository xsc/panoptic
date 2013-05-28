(ns ^ {:doc "Checksum Functions"
       :author "Yannick Scherer"}
  panoptic.watchers.checksums
  (:require [panoptic.data.core :as data]
            [panoptic.utils.fs :as fs :only [exists? last-modified]]
            [pandect.core :as cs])
  (:use [taoensso.timbre :only [error]]))

;; ## Updating Checksums

(defn update-checksum
  "Update Checksum of the given Entity."
  [checksum-fn data-key entity]
  (try
    (let [chk0 (data/checksum entity)
          chk1 (checksum-fn (get entity data-key))]
      (condp = [chk0 chk1]
        [nil  nil]  (data/set-missing entity)
        [chk0 chk0] (data/set-untouched entity)
        [nil  chk1] (let [entity-new (data/set-created entity chk1)]
                      (if-not (data/exists? entity)
                        entity-new
                        (data/set-untouched entity-new)))
        [chk0 nil]  (data/set-deleted entity)
        (data/set-modified entity chk1))) 
    (catch Exception ex
      (error ex "in checksum update function")
      (data/set-untouched entity))))

;; ## Calculating File Checksums

(defmulti file-checksum-fn 
  "Get function used to compute file checksum."
  (fn [k] (if (fn? k) ::fn k))
  :default :crc32)

(defmethod file-checksum-fn ::fn
  [f]
  f)

(defn- wrap-file-checksum
  [f]
  #(when (fs/exists? %) (f %)))

(defmethod file-checksum-fn :last-modified [_] (wrap-file-checksum fs/last-modified))
(defmethod file-checksum-fn :crc32 [_] (wrap-file-checksum cs/crc32-file))
(defmethod file-checksum-fn :adler32 [_] (wrap-file-checksum cs/adler32-file))
(defmethod file-checksum-fn :md5 [_] (wrap-file-checksum cs/md5-file))
(defmethod file-checksum-fn :sha1 [_] (wrap-file-checksum cs/sha1-file))
(defmethod file-checksum-fn :sha256 [_] (wrap-file-checksum cs/sha256-file))
(defmethod file-checksum-fn :sha512 [_] (wrap-file-checksum cs/sha512-file))

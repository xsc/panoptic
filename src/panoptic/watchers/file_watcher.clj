(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file-watcher
  (:use panoptic.watchers.core
        [taoensso.timbre :only [error]])
  (:require [panoptic.data.file :as f]
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
  [checker {:keys [checked path checksum] :as f}]
  (try
    (let [chk (checker path)]
      (condp = [checksum chk]
        [nil nil] (f/set-file-missing f)
        [chk chk] (f/set-file-untouched f chk)
        [nil chk] (if (or (:deleted f) (:missing f)) ;; this prevents creation notifications on startup
                    (f/set-file-created f chk) 
                    (f/set-file-untouched f chk)) 
        [checksum nil] (f/set-file-deleted f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (error ex "in file checker for file" path)
      (f/set-file-untouched f checksum))))

;; ## File Watcher

(defwatch file-watcher*
  FileEntityHandlers
  (on-file-create [this f] (on-flag-set this :created f))
  (on-file-modify [this f] (on-flag-set this :modified f))
  (on-file-delete [this f] (on-flag-set this :deleted f)))

(defn file-watcher
  "Create WatchFn for Files."
  [& {:keys [checksum]}] 
  (let [checker (checksum-fn checksum)]
    (file-watcher*
      (partial update-file! (or checker cs/crc32-file))
      (fn [m path]
        (when-let [f (f/file path)]
          (assoc m (:path f) f)))
      (fn [m path]
        (when-let [f (f/file path)]
          (dissoc m (:path f)))))))

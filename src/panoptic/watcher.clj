(ns ^{:doc "Watcher Implementation for Panoptic"
      :author "Yannick Scherer"}
  panoptic.watcher
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]))

;; ## Concept
;;
;; Observing Files is done using the premise that changes occur in batches not
;; distributed over a large amount of time. We will thus have multiple threads
;; watching different sets of files, running their update mechanisms in different
;; intervals. 
;;
;; Files will be upgraded and downgraded according to the frequency changes were
;; observed. Every observer should have the same number of files to watch, meaning
;; that open spaces will be filled randomly with files of other priorities, having
;; them shuffled periodically between observers.

;; ## Check Files for Changes

(defn- check-file
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map or nil (if the checker returned `nil` and the file did not
   have a checksum before)"
  [checker {:keys [path checksum] :as f}]
  (try
    (let [chk (c/file-checksum checker path)]
      (condp = [checksum chk]
        [nil nil] nil
        [nil chk] (f/set-file-created f chk)
        [checksum nil] (f/set-file-deleted f)
        [chk chk] (f/set-file-untouched f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (f/set-file-untouched f))))

(defn check-files
  "Check seq of file maps for changes, returns an updated seq."
  [checker files]
  (let [check! (partial check-file checker)]
    (doall (keep #(when % (check! %)) files))))

;; ## Watching Files

(defn- sleep
  "Sleep the given number of Milliseconds."
  [interval]
  (try
    (Thread/sleep interval)
    (catch Exception _ nil)))

(defn run-file-watcher!
  "Create Watcher that checks the files contained in the given atom
   in a periodic fashion using the given checker and polling interval
   in milliseconds. Returns a function that can be called to shutdown
   the observer."
  [file-seq-atom checker interval]
  (let [stop? (atom nil)
        check! (partial check-files checker)
        observer-thread (future
                          (loop []
                            (when-not @stop?
                              (swap! file-seq-atom check!) 
                              (sleep interval) 
                              (recur))))]
    (fn 
      ([] 
       (reset! stop? true) 
       @observer-thread)
      ([cancel-after-milliseconds]
       (reset! stop? true)
       (when (= ::timeout (deref observer-thread cancel-after-milliseconds ::timeout))
         (future-cancel observer-thread))))))

;; ## Watching Directories

(defn- check-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [{:keys [path files include-hidden extensions directories] :as dir}]
  (if-let [d (f/directory path :extensions extensions :include-hidden include-hidden)]
    nil
    (f/set-file-deleted dir)))

(defn run-directory-watcher!
  "Create Watcher that checks the directories contained in the given
   atom in a periodic fashion, using the given polling interval. Returns
   a function that can be called to shutdown the observer."
  [directory-seq-atom interval]

  )

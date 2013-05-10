(ns ^{:doc "Watcher Implementation for Panoptic"
      :author "Yannick Scherer"}
  panoptic.watcher
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]))

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

;; ## Protocol

(defprotocol Watcher
  "Protocol for Watchers."
  (start-watcher! [this]
    "Start the Watcher.")
  (stop-watcher! [this]
    "Stop the Watcher.")
  (on-create [this f]
    "Add handler for entity creation.")
  (on-delete [this f]
    "Add handler for entity deletion.") 
  (on-modify [this f]
    "Add handler for entity modification.")) 

;; ## FileWatcher

;; ### Helpers

(defn- check-file
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map or nil (if the checker returned `nil` and the file did not
   have a checksum before)"
  [checker {:keys [checked path checksum] :as f}]
  (try
    (let [chk (c/file-checksum checker path)]
      (condp = [checksum chk]
        [nil nil] (f/set-file-missing f)
        [chk chk] (f/set-file-untouched f chk)
        [nil chk] (if (:missing f) (f/set-file-created f chk) (f/set-file-untouched f chk)) 
        [checksum nil] (f/set-file-deleted f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (f/set-file-untouched f checksum))))

(defn check-files
  "Check seq of file maps for changes, returns an updated seq."
  [checker files]
  (let [check! (partial check-file checker)]
    (doall (keep #(when % (check! %)) files))))

;; ### Run File Watcher

(defn- run-file-watcher!
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
                              (u/sleep interval) 
                              (recur))))]
    (fn 
      ([] 
       (reset! stop? true) 
       @observer-thread)
      ([cancel-after-milliseconds]
       (reset! stop? true)
       (when (= ::timeout (deref observer-thread cancel-after-milliseconds ::timeout))
         (future-cancel observer-thread))))))

;; ### File Watcher Type

(defn- attach-file-handlers
  "Attach Handlers to an Atom containing a seq of file maps."
  [file-atom k {:keys [on-create on-modify on-delete] :as h}]
  (add-watch file-atom k
             (fn [_ _ _ files]
               (doseq [{:keys [created modified deleted] :as f} files]
                 (try
                   (cond (and on-create created) (doseq [c on-create] (c f))
                         (and on-delete deleted) (doseq [c on-delete] (c f))
                         (and on-modify modified) (doseq [c on-modify] (c f))
                         :else nil)
                   (catch Exception _ nil))))))

(deftype FileWatcher [file-seq-atom checker interval handlers stop-fn]
  Watcher
  (start-watcher! [this]
    (attach-file-handlers file-seq-atom ::watcher handlers)
    (FileWatcher. 
      file-seq-atom checker interval handlers
      (run-file-watcher! file-seq-atom checker interval)))
  (stop-watcher! [this]
    (or
      (when stop-fn 
        (stop-fn)
        (remove-watch file-seq-atom ::watcher)
        (FileWatcher.  file-seq-atom checker interval handlers nil))
      this))
  (on-create [this f]
    (FileWatcher. 
      file-seq-atom checker interval
      (update-in handlers [:on-create] #(conj (vec %1) %2) f)
      stop-fn))
  (on-delete [this f]
    (FileWatcher. 
      file-seq-atom checker interval
      (update-in handlers [:on-delete] #(conj (vec %1) %2) f)
      stop-fn))
  (on-modify [this f]
    (FileWatcher. 
      file-seq-atom checker interval
      (update-in handlers [:on-modify] #(conj (vec %1) %2) f)
      stop-fn)))

(defn file-watcher
  "Create new File Watcher."
  [file-seq-atom checker interval]
  (FileWatcher. file-seq-atom checker interval {} nil))

;; ## Watching Directories

(defn- check-directory
  "Check the given directory for new files/subdirectories, returning `nil` if it was deleted."
  [{:keys [path files include-hidden extensions directories] :as dir}]
  (if-let [d (f/directory path :extensions extensions :include-hidden include-hidden)]
    nil
    (f/set-directory-deleted dir)))

(defn run-directory-watcher!
  "Create Watcher that checks the directories contained in the given
   atom in a periodic fashion, using the given polling interval. Returns
   a function that can be called to shutdown the observer."
  [directory-seq-atom interval]

  )

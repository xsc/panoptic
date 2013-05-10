(ns ^{:doc "Watcher Implementation for Panoptic"
      :author "Yannick Scherer"}
  panoptic.watcher
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]
            [panoptic.observable :as o]))

;; ## Observable Entities

;; ### Container

(defn observable-files
  "Create new Observable container containing the given files."
  ([] (observable-files []))
  ([paths] 
   (o/observable-atom (vec (map f/file paths)))))

(defn add-observable-file
  [file-observable path]
  (o/observable-swap! file-observable #(conj (vec %1) (f/file path))))

;; ### Handlers

(defn- on-flag-set
  "Attach Handler to File Observable that is called when the given map entry is set."
  [flag file-observable f]
  (o/add-result-handler 
    file-observable
    (fn [sq]
      (doseq [file (filter flag sq)]
        (f file)))))

(def on-create (partial on-flag-set :created))
(def on-delete (partial on-flag-set :deleted))
(def on-modify (partial on-flag-set :modified))

;; ## Watcher Protocol

(defprotocol Watcher
  "Protocol for Watcher."
  (start-watcher!* [this opts] "Start Watcher.")
  (stop-watcher! [this] "Stop Watcher."))

(defn start-watcher!
  [w & opts]
  (start-watcher!* w (apply hash-map opts)))

;; ## FileWatcher

;; ### Observation Logic

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
        [nil chk] (if (:missing f) ;; this prevents creation notifications on startup
                    (f/set-file-created f chk) 
                    (f/set-file-untouched f chk)) 
        [checksum nil] (f/set-file-deleted f)
        (f/set-file-modified f chk)))
    (catch Exception ex
      (f/set-file-untouched f checksum))))

(defn run-file-watcher!
  "Create Watcher that checks the files contained in the given atom
   in a periodic fashion using the given checker and polling interval
   in milliseconds. Returns a function that can be called to shutdown
   the observer."
  [file-observable checker interval]
  (let [stop? (atom nil)
        check! (partial check-file checker)
        observer-thread (future
                          (loop []
                            (when-not @stop?
                              (o/observe! file-observable check!) 
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

;; ### FileWatcher Type

(deftype FileWatcher [file-observable stop-fn-atom]
  Watcher
  (start-watcher!* [this {:keys [interval checker]}]
    (swap! stop-fn-atom
           (fn [stop]
             (or stop (run-file-watcher! file-observable (or checker c/last-modified) (or interval 1000)))))
    this)
  (stop-watcher! [this]
    (swap! stop-fn-atom
           (fn [stop]
             (when stop (stop))
             nil))
    this)

  Object 
  (toString [this]
    (.toString file-observable)))

(defn file-watcher
  "Create new FileWatcher."
  [file-observable]
  (FileWatcher. file-observable (atom nil)))

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

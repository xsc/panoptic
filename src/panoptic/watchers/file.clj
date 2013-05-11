(ns ^{:doc "File Watchers"
      :author "Yannick Scherer"}
  panoptic.watchers.file
  (:use panoptic.watchers.core)
  (:require [panoptic.checkers :as c]
            [panoptic.file :as f]
            [panoptic.utils :as u]
            [panoptic.observable :as o]))

;; ## Protocol

(defprotocol FileWatcher
  "Protocol for File Watchers."
  (wrap-file-handler [this f]))

(defn- on-flag-set
  [flag watcher f]
  (wrap-file-handler 
    watcher
    (fn [h]
      (fn [file]
        (when h (h file)) 
        (when (get file flag)
          (f file))))))

(def on-file-create (partial on-flag-set :created))
(def on-file-delete (partial on-flag-set :deleted))
(def on-file-modify (partial on-flag-set :modified))

;; ## Observation Logic

(defn- check-file
  "Check a file (given as a file map) for changes using the given checker. Returns
   an updated file map."
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

(defn- run-file-watcher!
  "Create Watcher that checks the files contained in the given atom
  in a periodic fashion using the given checker and polling interval
  in milliseconds. Returns a function that can be called to shutdown
  the observer (producing a future that can be waited for)."
  ([file-seq-atom checker interval]
   (run-file-watcher! file-seq-atom checker interval nil))
  ([file-seq-atom checker interval handler] 
   (let [stop? (atom nil)
         check! (partial check-file checker)
         observer-thread (future
                           (loop []
                             (when-not @stop?
                               (swap! file-seq-atom 
                                      #(doall
                                         (keep 
                                           (fn [x] 
                                             (when x 
                                               (when-let [f (check! x)]
                                                 (when handler (handler f))
                                                 f))) %))) 
                               (u/sleep interval) 
                               (recur))))]
     (fn 
       ([] 
        (reset! stop? true) 
        observer-thread)
       ([cancel-after-milliseconds]
        (reset! stop? true)
        (future
          (when (= ::timeout (deref observer-thread cancel-after-milliseconds ::timeout))
            (future-cancel observer-thread))))))))

;; ## Simple File Watcher

(deftype SimpleFileWatcher [file-seq-atom checker interval handler]
  FileWatcher
  (wrap-file-handler [this f]
    (SimpleFileWatcher. file-seq-atom checker interval (f handler)))
  Watcher
  (start-watcher!* [this]
    (run-file-watcher! file-seq-atom checker interval handler)))

(defn simple-file-watcher
  "Create single-threaded file watcher."
  [files & {:keys [interval checker]}]
  (SimpleFileWatcher.
    (atom (map f/file files))
    (or checker c/last-modified)
    (or interval 1000)
    nil))

;; ## Multi-Threaded File Watcher

(defn- distribute-files
  [src-seq dest-atoms]
  (let [l (count src-seq)
        n (count dest-atoms)
        c (int (Math/ceil (/ l n)))
        p (concat (partition c c nil src-seq) (repeat nil))]
    (doseq [[a s] (map vector dest-atoms p)]
      (reset! a s)))
  src-seq)

(deftype MultiThreadedFileWatcher [n file-seq-atom checker interval handler]
  FileWatcher
  (wrap-file-handler [this f]
    (MultiThreadedFileWatcher. n file-seq-atom checker interval (f handler)))
  Watcher
  (start-watcher!* [this]
    (let [child-atoms (take n (repeatedly #(atom [])))
          child-watchers (map #(SimpleFileWatcher. % checker interval handler) child-atoms)
          stop-promise (promise)]
      (add-watch file-seq-atom ::watch (fn [_ _ _ v] (distribute-files v child-atoms)))
      (distribute-files @file-seq-atom child-atoms)
      (let [stop-fns (doall (map start-watcher! child-watchers))]
        (fn 
          ([] 
           (remove-watch file-seq-atom ::watch)
           (future
             (->> stop-fns
               (map (fn [f] (try (f) (catch Exception _ nil))))
               (doall)
               (map deref)
               (doall))))
          ([cancel-after-milliseconds] 
           (remove-watch file-seq-atom ::watch)
           (future
             (->> stop-fns
               (map (fn [f] (try (f cancel-after-milliseconds) (catch Exception _ nil))))
               (map deref)
               (doall)))))))))

(defn multi-threaded-file-watcher
  "Create multi-threaded File Watcher."
  [n files & {:keys [interval checker]}]
  (MultiThreadedFileWatcher.
    n
    (atom (map f/file files))
    (or checker c/last-modified)
    (or interval 1000)
    nil))

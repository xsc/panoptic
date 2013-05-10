(ns ^{:doc "Container for Observable Entities"
      :author "Yannick Scherer"}
  panoptic.observable)

;; ## Observable
;;
;; Since 'add-watch' is marked 'Alpha' this should replicate its behaviour.
;; Additionally, this is made for atoms containing seqs of which single elements
;; can be observed independently.

(defprotocol _Observable
  (observable-deref [this])
  (observable-swap! [this f])
  (observe! [this f])
  (add-result-handler [this f]))

(deftype ObservableAtom [a handlers]
  _Observable
  (observable-deref [this]
    @a)
  (observable-swap! [this f]
    (swap! a f)
    this)
  (observe! [this f]
    (let [new-value (swap! a #(doall (keep (fn [x] (when x (f x))) %)))]
      (doseq [h handlers]
        (future (h new-value))))
    this)
  (add-result-handler [this f]
    (ObservableAtom. a (conj handlers f)))
  Object
  (toString [this]
    (pr-str @a)))

(defn observable-atom
  "Create new observable Atom."
  [initial-value]
  (ObservableAtom. (atom initial-value) []))

;; ## Atom Fallback

(extend-type clojure.lang.Atom
  _Observable
  (observable-deref [this]
    @this)
  (observable-swap! [this f]
    (swap! this f))
  (observe! [this f]
    (swap! this #(doall (keep (fn [x] (when x (f x))) %))))
  (add-result-handler [this f]
    (add-watch this (keyword (gensym)) f)))
